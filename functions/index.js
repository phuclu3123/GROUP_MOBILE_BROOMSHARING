'use strict';
const {initializeApp} = require('firebase-admin/app');
const {getFirestore} = require('firebase-admin/firestore');
const {onCall, HttpsError} = require('firebase-functions/v2/https');
const {BookingError} = require('./booking-rules');
initializeApp();
const api = require('./service').service(getFirestore('broomsharing'));
const adminApi = require('./admin-service').adminService(getFirestore('broomsharing'));
const callable = method => onCall({region: 'asia-southeast1', maxInstances: 5, timeoutSeconds: 30}, async req => {
  if (!req.auth) throw new HttpsError('unauthenticated', 'Vui lòng đăng nhập.');
  try { return await method(req.auth.uid, req.data || {}); }
  catch (e) {
    if (e instanceof BookingError) throw new HttpsError(e.code, e.message);
    console.error('Booking operation failed', {code: e.code});
    throw new HttpsError('internal', 'Chưa xử lý được yêu cầu. Vui lòng thử lại với cùng mã yêu cầu.');
  }
});
exports.createBooking = callable(api.create);
exports.transitionBooking = callable(api.transition);
exports.saveHostRoom = callable(api.saveRoom);
exports.setHostListing = callable(api.listing);
exports.quoteBooking = callable(api.quote);
exports.reviewAdminItem = callable(adminApi.review);
exports.moderateHost = callable(adminApi.moderateHost);
exports.moderateRoom = callable(adminApi.moderateRoom);
exports.getVerificationEvidence = callable(async (uid,input) => {
  const {path} = await adminApi.evidence(uid,input);
  const {getStorage} = require('firebase-admin/storage');
  const file = getStorage().bucket().file(path);
  const [metadata] = await file.getMetadata();
  const size = Number(metadata.size);
  if (!(size>0 && size<=5*1024*1024) || !['image/jpeg','image/png','image/webp','application/pdf'].includes(metadata.contentType))
    throw new BookingError('failed-precondition','Bằng chứng phải là ảnh/PDF tối đa 5 MiB.');
  const [bytes] = await file.download({validation:'crc32c'});
  return {base64:bytes.toString('base64')};
});
exports.getPublicHost = onCall({region: 'asia-southeast1', maxInstances: 5}, async req => {
  if (!req.auth) throw new HttpsError('unauthenticated', 'Vui lòng đăng nhập.');
  const uid = req.data?.ownerUid;
  if (typeof uid !== 'string' || !/^[a-zA-Z0-9_-]{1,128}$/.test(uid)) throw new HttpsError('invalid-argument', 'Mã chủ phòng không hợp lệ.');
  const user = await getFirestore('broomsharing').doc('users/' + uid).get();
  const active = user.get('status') === 'ACTIVE' && user.get('role') === 'HOST';
  return {active, name: active ? user.get('name') || '' : ''};
});
