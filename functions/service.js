'use strict';
const {createHash} = require('node:crypto');
const {Timestamp, FieldValue} = require('firebase-admin/firestore');
const R = require('./booking-rules');
const stamp = () => FieldValue.serverTimestamp();
const id = s => { R.requireThat(typeof s === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(s), 'Mã tài liệu không hợp lệ.', 'invalid-argument'); return s; };
const hash = s => createHash('sha256').update(s).digest('hex');
function service(db, clock = Date.now) {
  async function user(tx, uid, roles) {
    const snap = await tx.get(db.doc('users/' + uid));
    const u = snap.data();
    R.requireThat(u?.status === 'ACTIVE' && roles.includes(u.role), 'Tài khoản không có quyền hoặc đã bị khóa.', 'permission-denied');
    return u;
  }
  async function future(tx, roomId, now) {
    const rows = await tx.get(db.collection('bookings').where('roomId', '==', roomId).where('endAt', '>', Timestamp.fromMillis(now)).limit(501));
    R.requireThat(rows.size <= 500, 'Lịch phòng quá lớn, cần quản trị viên hỗ trợ.');
    return rows.docs.map(d => ({...d.data(), id: d.id})).filter(R.occupies);
  }
  function event(tx, bookingId, actorUid, action, from, to, reason = '') {
    tx.create(db.collection('bookingEvents').doc(), {bookingId, actorUid, action, from, to, reason, createdAt: stamp()});
    tx.create(db.collection('auditLogs').doc(), {schemaVersion:1,actorUid,targetPath:'bookings/'+bookingId,action,
      before:{status:from},after:{status:to},reason,createdAt:stamp()});
  }
  async function create(uid, input) {
    id(input.roomId); id(input.idempotencyKey);
    const bookingId = hash(uid + ':' + input.idempotencyKey);
    const fingerprint = hash(JSON.stringify([input.roomId, input.startAt, input.endAt || null, input.months || null]));
    return db.runTransaction(async tx => {
      const guest = await user(tx, uid, ['GUEST', 'HOST']);
      const ref = db.doc('bookings/' + bookingId), previous = await tx.get(ref);
      if (previous.exists) {
        R.requireThat(previous.get('requestFingerprint') === fingerprint, 'Mã gửi lại đã dùng cho yêu cầu khác.', 'already-exists');
        return {bookingId, status: previous.get('status'), priceSnapshot: previous.get('priceSnapshot')};
      }
      const roomRef = db.doc('rooms/' + input.roomId), roomSnap = await tx.get(roomRef), room = roomSnap.data();
      R.requireThat(room && room.approvalStatus === 'APPROVED' && room.listingStatus === 'ACTIVE', 'Phòng chưa được duyệt hoặc đã ngừng nhận đặt.');
      R.requireThat(uid !== room.ownerUid, 'Không thể tự đặt phòng của mình.');
      await user(tx, id(room.ownerUid), ['HOST']);
      const building = await tx.get(db.doc('buildings/' + id(room.buildingId)));
      R.requireThat(building.exists && building.get('ownerUid') === room.ownerUid && building.get('status') === 'ACTIVE', 'Tòa nhà không còn hoạt động.');
      const now = clock(), p = R.plan(room, input, now), live = await future(tx, input.roomId, now);
      R.requireThat(!live.some(b => R.overlaps(p.start, p.end, R.millis(b.startAt), R.millis(b.endAt))), 'Khung giờ/kỳ thuê đã có người giữ chỗ.', 'already-exists');
      const lockDocs = [];
      for (const lock of p.locks) {
        const lref = db.doc(lock.path), snap = await tx.get(lref), slotsBusy = snap.get('slotsBusy') || {};
        R.requireThat(lock.slots.every(s => !slotsBusy[s]), 'Khung giờ/kỳ thuê đã được giữ chỗ.', 'already-exists');
        lockDocs.push({lref, slotsBusy, lock});
      }
      for (const {lref, slotsBusy, lock} of lockDocs) {
        for (const slot of lock.slots) slotsBusy[slot] = bookingId;
        tx.set(lref, {roomId: input.roomId, slotsBusy, updatedAt: stamp()});
      }
      const booking = {id: bookingId, schemaVersion: 1, roomId: input.roomId, roomName: room.roomName,
        ownerUid: room.ownerUid, guestUid: uid, guestName: guest.name || '', guestPhone: guest.phone || '',
        status: 'PENDING', paymentStatus: 'DEMO_PENDING', activeMode: p.mode, startAt: Timestamp.fromMillis(p.start),
        endAt: Timestamp.fromMillis(p.end), totalAmount: p.priceSnapshot.totalAmount, priceSnapshot: p.priceSnapshot,
        lockPlan: p.locks, requestFingerprint: fingerprint, rejectionReason: '', createdAt: stamp(), updatedAt: stamp()};
      tx.create(ref, booking);
      tx.update(roomRef, {bookingRevision: FieldValue.increment(1)});
      event(tx, bookingId, uid, 'CREATE', '', 'PENDING');
      return {bookingId, status: 'PENDING', priceSnapshot: p.priceSnapshot};
    });
  }
  async function transition(uid, input) {
    const bookingId = id(input.bookingId), target = input.status;
    R.requireThat(['CONFIRMED', 'REJECTED', 'CANCELLED','CHECKED_IN','COMPLETED'].includes(target), 'Thao tác booking không hợp lệ.', 'invalid-argument');
    const reason = typeof input.reason === 'string' ? input.reason.trim() : '';
    R.requireThat(reason.length <= 500 && (target !== 'REJECTED' || reason.length > 0), 'Từ chối cần lý do (tối đa 500 ký tự).', 'invalid-argument');
    return db.runTransaction(async tx => {
      await user(tx, uid, target === 'CANCELLED' ? ['GUEST', 'HOST'] : ['HOST']);
      const ref = db.doc('bookings/' + bookingId), snap = await tx.get(ref), b = snap.data();
      R.requireThat(b, 'Booking không tồn tại.', 'not-found');
      R.requireThat(target === 'CANCELLED' ? b.guestUid === uid : b.ownerUid === uid, 'Không được xử lý booking này.', 'permission-denied');
      if (b.status === target) return {bookingId, status: target};
      const allowed = target === 'CANCELLED' ? ['PENDING','CONFIRMED']
        : target === 'CHECKED_IN' ? ['CONFIRMED'] : target === 'COMPLETED' ? ['CONFIRMED','CHECKED_IN'] : ['PENDING'];
      R.requireThat(allowed.includes(b.status), 'Booking đã thay đổi trạng thái.');
      const now = clock();
      if (target === 'CHECKED_IN')
        R.requireThat(R.millis(b.startAt)<=now && now<R.millis(b.endAt),'Chỉ ghi nhận vào phòng trong thời gian thuê.');
      else if (target === 'COMPLETED') R.requireThat(R.millis(b.endAt)<=now,'Chưa kết thúc thời gian thuê.');
      else R.requireThat(R.millis(b.startAt) > now, 'Không được đổi yêu cầu đã bắt đầu.');
      const roomRef = db.doc('rooms/' + id(b.roomId)), roomSnap = await tx.get(roomRef), room = roomSnap.data();
      R.requireThat(room && room.ownerUid === b.ownerUid, 'Phòng/owner của booking không còn khớp.');
      if (target === 'CONFIRMED') {
        R.requireThat(room.approvalStatus === 'APPROVED' && room.listingStatus === 'ACTIVE', 'Phòng không còn nhận đặt mới.');
        const live = await future(tx, b.roomId, now);
        R.requireThat(!live.some(other => other.id !== bookingId
          && R.overlaps(R.millis(b.startAt), R.millis(b.endAt), R.millis(other.startAt), R.millis(other.endAt))), 'Booking xung đột với yêu cầu khác.', 'already-exists');
      }
      R.requireThat(Array.isArray(b.lockPlan) && b.lockPlan.length > 0, 'Booking cũ chưa có giữ chỗ an toàn; cần quản trị viên xử lý.');
      const locks = [];
      for (const lock of b.lockPlan) {
        R.requireThat(typeof lock.path === 'string' && /^(availabilityDays|fixedMonthLocks)\/[a-zA-Z0-9_-]+$/.test(lock.path), 'Dữ liệu khóa không hợp lệ.');
        const lref = db.doc(lock.path), l = await tx.get(lref), slots = l.get('slotsBusy') || {};
        R.requireThat(lock.slots.every(s => slots[s] === bookingId), 'Giữ chỗ không còn khớp.');
        locks.push({lref, slots, lock});
      }
      if (['REJECTED','CANCELLED','COMPLETED'].includes(target)) for (const {lref, slots, lock} of locks) {
        for (const s of lock.slots) if (slots[s] === bookingId) delete slots[s];
        tx.set(lref, {roomId: b.roomId, slotsBusy: slots, updatedAt: stamp()});
      }
      tx.update(ref, {status: target, rejectionReason: target === 'REJECTED' ? reason : '', updatedAt: stamp()});
      tx.update(roomRef, {bookingRevision: FieldValue.increment(1)});
      event(tx, bookingId, uid, target, b.status, target, reason);
      return {bookingId, status: target};
    });
  }
  async function saveRoom(uid, input) {
    const roomId = id(input.roomId), data = input.room;
    R.requireThat(data && ['DRAFT', 'PENDING_REVIEW'].includes(data.approvalStatus), 'Chủ phòng chỉ được lưu nháp/gửi duyệt.');
    R.validateRoom(data, data.approvalStatus === 'PENDING_REVIEW');
    return db.runTransaction(async tx => {
      await user(tx, uid, ['HOST']);
      const ref = db.doc('rooms/' + roomId), old = await tx.get(ref);
      R.requireThat(!old.exists || old.get('ownerUid') === uid, 'Phòng không thuộc tài khoản này.', 'permission-denied');
      const building = await tx.get(db.doc('buildings/' + id(data.buildingId)));
      R.requireThat(building.exists && building.get('ownerUid') === uid && building.get('status') === 'ACTIVE', 'Chọn tòa nhà đang hoạt động thuộc tài khoản của bạn.');
      // Every booking operation also writes the room revision, serializing config and booking.
      const live = await future(tx, roomId, clock());
      if (live.length > 0) {
        const defaults = {workingDays:[1,2,3,4,5,6,7],slotMinutes:60,minBookingMinutes:60,fixedStartMonth:''};
        for (const key of ['activeMode','buildingId','openTime','closeTime','workingDays','slotMinutes','minBookingMinutes','fixedStartMonth']) {
          let before = old.get(key) ?? defaults[key], after = data[key] ?? defaults[key];
          if (key === 'workingDays') { before = [...before].sort(); after = [...after].sort(); }
          R.requireThat(JSON.stringify(before) === JSON.stringify(after), 'Phòng còn booking giữ chỗ, không được đổi hình thức thuê/tòa nhà/lịch hoạt động.');
        }
        for (const booking of live) {
          const start = R.millis(booking.startAt), end = R.millis(booking.endAt);
          for (const b of data.blackoutRanges || [])
            R.requireThat(!R.overlaps(start,end,b.startAt,b.endAt), 'Khung khóa lịch trùng booking đang giữ chỗ.');
          for (const date of data.blackoutDates || []) {
            const at = Date.parse(R.legacyDate(date) + 'T00:00:00+07:00');
            R.requireThat(!R.overlaps(start,end,at,at+86400000), 'Ngày khóa lịch trùng booking đang giữ chỗ.');
          }
        }
      }
      const keys = ['buildingId','roomName','roomType','capacity','area','floor','address','activeMode',
        'pricePerHour','pricePerMonth','pricePerQuarter','deposit','openTime','closeTime','amenities','rules',
        'imageUrls','blackoutDates','blackoutRanges','workingDays','slotMinutes','minBookingMinutes',
        'fixedStartMonth','renewalPolicy','rentalPolicy','approvalStatus'];
      const clean = {};
      for (const key of keys) if (data[key] !== undefined) clean[key] = data[key];
      clean.blackoutRanges = (clean.blackoutRanges || []).map(b => ({startAt: Timestamp.fromMillis(b.startAt), endAt: Timestamp.fromMillis(b.endAt), reason: b.reason.trim()}));
      clean.pricePerDay = 0;
      clean.listingStatus = old.get('listingStatus') === 'SUSPENDED' ? 'SUSPENDED' : (data.listingStatus === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE');
      Object.assign(clean, {id: roomId, schemaVersion: 1, ownerUid: uid, buildingName: building.get('name'),
        rejectionReason: '', bookingRevision: (old.get('bookingRevision') || 0) + 1,
        createdAt: old.get('createdAt') || stamp(), updatedAt: stamp()});
      tx.set(ref, clean);
      tx.create(db.collection('auditLogs').doc(),{schemaVersion:1,actorUid:uid,targetPath:ref.path,action:'SAVE_ROOM',
        before:old.exists?{approvalStatus:old.get('approvalStatus')||'',activeMode:old.get('activeMode')||''}:{},
        after:{approvalStatus:clean.approvalStatus,activeMode:clean.activeMode},reason:'',createdAt:stamp()});
      return {roomId};
    });
  }
  async function listing(uid, input) {
    const roomId = id(input.roomId);
    R.requireThat(['ACTIVE', 'INACTIVE'].includes(input.status), 'Chủ phòng chỉ được mở/tạm ngừng nhận đặt.');
    return db.runTransaction(async tx => {
      await user(tx, uid, ['HOST']);
      const ref = db.doc('rooms/' + roomId), room = await tx.get(ref);
      R.requireThat(room.exists && room.get('ownerUid') === uid, 'Phòng không thuộc tài khoản này.', 'permission-denied');
      R.requireThat(room.get('listingStatus') !== 'SUSPENDED', 'Phòng bị Admin khóa, không thể tự mở lại.');
      tx.update(ref, {listingStatus: input.status, bookingRevision: FieldValue.increment(1), updatedAt: stamp()});
      tx.create(db.collection('auditLogs').doc(),{schemaVersion:1,actorUid:uid,targetPath:ref.path,action:'SET_LISTING',
        before:{listingStatus:room.get('listingStatus')||''},after:{listingStatus:input.status},reason:'',createdAt:stamp()});
      return {roomId};
    });
  }
  async function quote(uid, input) {
    id(input.roomId);
    return db.runTransaction(async tx => {
      await user(tx, uid, ['GUEST', 'HOST']);
      const snap = await tx.get(db.doc('rooms/' + input.roomId)), r = snap.data();
      R.requireThat(r && r.approvalStatus === 'APPROVED' && r.listingStatus === 'ACTIVE', 'Phòng hiện không nhận đặt.');
      await user(tx, id(r.ownerUid), ['HOST']);
      const now = clock(), p = R.plan(r, input, now), live = await future(tx, input.roomId, now);
      R.requireThat(!live.some(b => R.overlaps(p.start, p.end, R.millis(b.startAt), R.millis(b.endAt))), 'Khung giờ/kỳ thuê đã có người giữ chỗ.', 'already-exists');
      return {priceSnapshot: p.priceSnapshot, startAt: p.start, endAt: p.end, activeMode: p.mode};
    });
  }
  return {create, transition, saveRoom, listing, quote};
}
module.exports = {service};
