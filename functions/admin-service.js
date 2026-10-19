'use strict';
const {FieldValue} = require('firebase-admin/firestore');
const R = require('./booking-rules');
const stamp = () => FieldValue.serverTimestamp();
function documentId(value) {
  R.requireThat(typeof value === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(value), 'Mã tài liệu không hợp lệ.', 'invalid-argument');
  return value;
}
function note(value, required = false) {
  const text = typeof value === 'string' ? value.trim() : '';
  R.requireThat(text.length <= 1000 && (!required || text.length > 0), 'Nhập lý do/ghi chú từ 1 đến 1.000 ký tự.', 'invalid-argument');
  return text;
}
function adminService(db) {
  async function admin(tx, uid) {
    const user = await tx.get(db.doc('users/' + documentId(uid)));
    R.requireThat(user.get('role') === 'ADMIN' && user.get('status') === 'ACTIVE', 'Chỉ Admin đang hoạt động được xử lý.', 'permission-denied');
  }
  function log(tx, actorUid, targetPath, action, before, after, reason) {
    tx.create(db.collection('auditLogs').doc(), {schemaVersion:1,actorUid,targetPath,action,before,after,reason,createdAt:stamp()});
  }
  async function review(uid, input) {
    const groups = {HOST:'hostApplications',ROOM:'rooms',VERIFICATION:'propertyVerifications',REPORT:'reports'};
    R.requireThat(groups[input.kind] && typeof input.approve === 'boolean', 'Nhóm/thao tác duyệt không hợp lệ.', 'invalid-argument');
    const targetId = documentId(input.id), approve = input.approve, reason = note(input.reason, !approve || input.kind === 'VERIFICATION');
    return db.runTransaction(async tx => {
      await admin(tx,uid);
      const ref = db.doc(groups[input.kind]+'/'+targetId), snap = await tx.get(ref), item = snap.data();
      R.requireThat(item, 'Hồ sơ không tồn tại.', 'not-found');
      const field = input.kind === 'ROOM' ? 'approvalStatus' : 'status';
      const pending = input.kind === 'ROOM' ? 'PENDING_REVIEW' : input.kind === 'REPORT' ? 'OPEN' : 'PENDING';
      R.requireThat(item[field] === pending, 'Hồ sơ đã được xử lý. Vui lòng tải lại.');
      let userRef, verificationRoom;
      if (input.kind === 'HOST') {
        userRef = db.doc('users/'+documentId(item.applicantUid));
        const user = await tx.get(userRef);
        R.requireThat(user.exists && user.get('status') === 'ACTIVE' && ['GUEST','HOST'].includes(user.get('role')), 'Tài khoản xin quyền Host không hợp lệ hoặc đã khóa.');
      }
      if (input.kind === 'ROOM' && approve) {
        const owner = await tx.get(db.doc('users/'+documentId(item.ownerUid)));
        const building = await tx.get(db.doc('buildings/'+documentId(item.buildingId)));
        R.requireThat(owner.get('role') === 'HOST' && owner.get('status') === 'ACTIVE', 'Chủ phòng không còn hoạt động.');
        R.requireThat(building.get('ownerUid') === item.ownerUid && building.get('status') === 'ACTIVE', 'Tòa nhà không thuộc chủ phòng hoặc đã ngừng hoạt động.');
        R.validateRoom(item,true);
      }
      if (input.kind === 'VERIFICATION') {
        R.requireThat(['IDENTITY','LOCATION','PHOTOS'].includes(item.verificationType), 'Chưa có loại xác minh danh tính/địa điểm/ảnh rõ ràng.');
        const owner = await tx.get(db.doc('users/'+documentId(item.ownerUid)));
        R.requireThat(owner.get('role') === 'HOST' && owner.get('status') === 'ACTIVE', 'Chủ hồ sơ không còn quyền Host hoạt động.');
        if (item.roomId) {
          verificationRoom = db.doc('rooms/'+documentId(item.roomId));
          const room = await tx.get(verificationRoom);
          R.requireThat(room.exists && room.get('ownerUid') === item.ownerUid, 'Phòng không thuộc chủ hồ sơ.');
        } else if (item.buildingId) {
          const building = await tx.get(db.doc('buildings/'+documentId(item.buildingId)));
          R.requireThat(building.exists && building.get('ownerUid') === item.ownerUid, 'Tòa nhà không thuộc chủ hồ sơ.');
        } else R.requireThat(item.verificationType === 'IDENTITY', 'Xác minh địa điểm/ảnh cần chỉ rõ phòng hoặc tòa nhà.');
        if (approve) {
          const refs = item.evidenceRefs || (item.evidencePath ? [item.evidencePath] : []);
          R.requireThat(Array.isArray(refs) && refs.length > 0 && refs.length <= 10
            && refs.every(path => typeof path === 'string' && path.startsWith('verification/'+item.ownerUid+'/')
              && !path.includes('..') && path.split('/').length === 3), 'Hồ sơ chưa có bằng chứng lưu riêng hợp lệ.');
          R.requireThat(input.evidenceReviewed === true, 'Cần xem và xác nhận bằng chứng trước khi duyệt.');
        }
      }
      const status = input.kind === 'REPORT' ? (approve?'RESOLVED':'DISMISSED')
        : input.kind === 'VERIFICATION' ? (approve?'VERIFIED':'REJECTED') : (approve?'APPROVED':'REJECTED');
      const changes = {[field]:status,reviewedBy:uid,updatedAt:stamp(),
        [input.kind === 'REPORT' ? 'resolution' : 'rejectionReason']:reason};
      if (input.kind === 'VERIFICATION') {
        changes.method = 'ADMIN_REVIEWED'; changes.reviewNote = reason; changes.reviewedAt = stamp();
        changes.partnerName = ''; // Manual review never produces a partner-verification badge.
      }
      if (input.kind === 'HOST' && approve) tx.update(userRef,{role:'HOST',updatedAt:stamp()});
      tx.update(ref,changes);
      // Identity approval does not claim the existence/accuracy of a room or its photographs.
      if (verificationRoom && input.kind === 'VERIFICATION' && approve && item.verificationType !== 'IDENTITY')
        tx.update(verificationRoom,{[item.verificationType==='LOCATION'?'locationVerification':'photosVerification']:'ADMIN_REVIEWED',
          [item.verificationType==='LOCATION'?'locationVerificationId':'photosVerificationId']:targetId});
      log(tx,uid,ref.path,approve?'APPROVE':'REJECT',{[field]:item[field]},
        {[field]:status,...(input.kind==='VERIFICATION'?{method:'ADMIN_REVIEWED',verificationType:item.verificationType}:{})},reason);
      return {id:targetId};
    });
  }
  async function moderateHost(uid,input) {
    const target = documentId(input.uid), reason = note(input.reason,true);
    R.requireThat(['ACTIVE','BLOCKED'].includes(input.status), 'Trạng thái chủ không hợp lệ.', 'invalid-argument');
    return db.runTransaction(async tx => {
      await admin(tx,uid);
      const ref=db.doc('users/'+target), user=await tx.get(ref);
      R.requireThat(user.get('role')==='HOST','Chỉ được khóa/mở tài khoản Host.');
      if(user.get('status')===input.status) return {id:target};
      const rooms=await tx.get(db.collection('rooms').where('ownerUid','==',target).limit(401));
      R.requireThat(rooms.size<=400,'Chủ có quá nhiều phòng; cần thao tác quản trị theo lô riêng.');
      tx.update(ref,{status:input.status,moderationReason:reason,moderatedBy:uid,updatedAt:stamp()});
      for(const room of rooms.docs) {
        if(input.status==='BLOCKED') {
          const suspended=room.get('listingStatus')==='SUSPENDED';
          tx.update(room.ref,{listingStatus:'SUSPENDED',suspendedByOwnerBlock:!suspended,
            listingStatusBeforeOwnerBlock:room.get('listingStatus')||'INACTIVE',updatedAt:stamp()});
        } else if(room.get('suspendedByOwnerBlock')===true) {
          tx.update(room.ref,{listingStatus:room.get('listingStatusBeforeOwnerBlock')==='ACTIVE'?'ACTIVE':'INACTIVE',
            suspendedByOwnerBlock:false,updatedAt:stamp()});
        }
      }
      log(tx,uid,ref.path,input.status==='BLOCKED'?'BLOCK_HOST':'UNBLOCK_HOST',
        {status:user.get('status')},{status:input.status,affectedRooms:rooms.size},reason);
      return {id:target};
    });
  }
  async function moderateRoom(uid,input) {
    const roomId=documentId(input.roomId),reason=note(input.reason,true);
    R.requireThat(['SUSPENDED','INACTIVE'].includes(input.status),'Admin chỉ khóa hoặc gỡ khóa về trạng thái ngừng rao.');
    return db.runTransaction(async tx=>{
      await admin(tx,uid);
      const ref=db.doc('rooms/'+roomId),room=await tx.get(ref);
      R.requireThat(room.exists,'Phòng không tồn tại.','not-found');
      if(input.status==='INACTIVE') {
        const owner=await tx.get(db.doc('users/'+documentId(room.get('ownerUid'))));
        R.requireThat(owner.get('role')==='HOST' && owner.get('status')==='ACTIVE','Cần mở khóa chủ trước khi gỡ khóa phòng.');
      }
      tx.update(ref,{listingStatus:input.status,suspendedByOwnerBlock:false,moderationReason:reason,moderatedBy:uid,updatedAt:stamp()});
      log(tx,uid,ref.path,input.status==='SUSPENDED'?'SUSPEND_ROOM':'UNSUSPEND_ROOM',
        {listingStatus:room.get('listingStatus')},{listingStatus:input.status},reason);
      return {id:roomId};
    });
  }
  async function evidence(uid,input) {
    const snap=await db.doc('users/'+documentId(uid)).get();
    R.requireThat(snap.get('role')==='ADMIN'&&snap.get('status')==='ACTIVE','Chỉ Admin được xem bằng chứng.','permission-denied');
    const item=await db.doc('propertyVerifications/'+documentId(input.id)).get();
    R.requireThat(item.exists,'Hồ sơ không tồn tại.','not-found');
    const refs=item.get('evidenceRefs')||(item.get('evidencePath')?[item.get('evidencePath')]:[]);
    R.requireThat(typeof input.path==='string' && refs.includes(input.path)
      && input.path.startsWith('verification/'+item.get('ownerUid')+'/') && !input.path.includes('..')
      && input.path.split('/').length===3,'Bằng chứng không thuộc hồ sơ.','permission-denied');
    return {path:input.path}; // Storage bytes are fetched only by the callable wrapper.
  }
  return {review,moderateHost,moderateRoom,evidence};
}
module.exports={adminService};
