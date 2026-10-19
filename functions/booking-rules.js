'use strict';
const DAY = 86400000, OFFSET = 7 * 3600000;
class BookingError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}
function requireThat(ok, message, code = 'failed-precondition') {
  if (!ok) throw new BookingError(code, message);
}
function millis(value) { return value?.toMillis ? value.toMillis() : value instanceof Date ? value.getTime() : value; }
function dateKey(ms) { return new Date(ms + OFFSET).toISOString().slice(0, 10); }
function local(ms) { return new Date(ms + OFFSET); }
function midnight(ms) { const d = local(ms); return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()) - OFFSET; }
function overlaps(a, b, c, d) { return a < d && c < b; }
function occupies(b) { return ['PENDING', 'REQUESTED', 'CONFIRMED', 'CHECKED_IN'].includes(b.status); }
function money(n) { return Number.isSafeInteger(n) && n >= 0 && n <= 1e12; }
function minutes(s) {
  requireThat(typeof s === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(s), 'Giờ hoạt động không hợp lệ.');
  return Number(s.slice(0, 2)) * 60 + Number(s.slice(3));
}
function legacyDate(value) {
  if (/^\d{2}\/\d{2}\/\d{4}$/.test(value)) return value.slice(6) + '-' + value.slice(3, 5) + '-' + value.slice(0, 2);
  return value;
}
function validateRoom(r, submit = false) {
  requireThat(['HOURLY', 'FIXED'].includes(r.activeMode), 'Hình thức thuê không hợp lệ.');
  for (const key of ['pricePerHour', 'pricePerMonth', 'pricePerQuarter', 'deposit'])
    requireThat(money(r[key]), 'Giá/cọc phải là số nguyên VND từ 0 đến 1.000 tỷ.');
  requireThat(typeof r.roomName === 'string' && r.roomName.trim().length > 0 && r.roomName.length <= 200, 'Nhập tên phòng hợp lệ.');
  requireThat(Number.isInteger(r.capacity) && r.capacity >= 0 && r.capacity <= 10000
    && Number.isFinite(r.area) && r.area >= 0 && r.area <= 1e6, 'Sức chứa/diện tích không hợp lệ.');
  requireThat(Array.isArray(r.imageUrls) && r.imageUrls.length <= 6
    && r.imageUrls.every(s => typeof s === 'string' && s.startsWith('https://') && s.length <= 2048), 'Ảnh phòng không hợp lệ.');
  for (const key of ['rules', 'renewalPolicy', 'rentalPolicy'])
    requireThat(r[key] == null || (typeof r[key] === 'string' && r[key].length <= 5000), 'Nội dung chính sách quá dài.');
  if (r.activeMode === 'HOURLY') {
    requireThat(Array.isArray(r.workingDays) && r.workingDays.length > 0 && r.workingDays.length <= 7
      && new Set(r.workingDays).size === r.workingDays.length
      && r.workingDays.every(d => Number.isInteger(d) && d >= 1 && d <= 7), 'Chọn ngày hoạt động.');
    const step = r.slotMinutes, min = r.minBookingMinutes, open = minutes(r.openTime), close = minutes(r.closeTime);
    requireThat([30, 60].includes(step) && Number.isInteger(min) && min > 0 && min <= 1440 && min % step === 0
      && open < close && open % step === 0 && close % step === 0 && close - open >= min, 'Bước giờ/thời lượng/giờ hoạt động không hợp lệ.');
  }
  requireThat(!r.fixedStartMonth || /^\d{4}-(0[1-9]|1[0-2])$/.test(r.fixedStartMonth), 'Tháng bắt đầu không hợp lệ.');
  requireThat(Array.isArray(r.blackoutDates || []) && (r.blackoutDates || []).length <= 100
    && (r.blackoutDates || []).every(d => {
      if (typeof d !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(legacyDate(d))) return false;
      const at = Date.parse(legacyDate(d) + 'T00:00:00+07:00');
      return Number.isFinite(at) && dateKey(at) === legacyDate(d);
    }), 'Ngày khóa lịch không hợp lệ.');
  requireThat(Array.isArray(r.blackoutRanges || []) && (r.blackoutRanges || []).length <= 100, 'Tối đa 100 khung khóa lịch.');
  for (const b of r.blackoutRanges || [])
    requireThat(Number.isSafeInteger(millis(b.startAt)) && Number.isSafeInteger(millis(b.endAt))
      && millis(b.startAt) < millis(b.endAt) && typeof b.reason === 'string' && b.reason.trim().length > 0
      && b.reason.length <= 500, 'Khóa lịch cần khoảng thời gian và lý do hợp lệ.');
  if (submit) {
    requireThat(r.capacity > 0 && r.area > 0 && r.address?.trim() && r.floor?.trim() && r.imageUrls.length > 0, 'Thiếu thông tin gửi duyệt.');
    requireThat(r.activeMode === 'HOURLY' ? r.pricePerHour > 0 : (r.pricePerMonth > 0 || r.pricePerQuarter > 0), 'Nhập giá thuê lớn hơn 0.');
    requireThat(r.activeMode !== 'FIXED' || (r.fixedStartMonth && r.renewalPolicy?.trim()), 'Nhập tháng bắt đầu và điều khoản gia hạn.');
  }
}
function plan(room, input, now = Date.now()) {
  const start = input.startAt;
  requireThat(Number.isSafeInteger(start) && start > now && start <= now + 366 * DAY, 'Ngày bắt đầu phải trong tương lai, tối đa một năm.');
  let end, rentalAmount, locks = [];
  const mode = room.activeMode;
  if (mode === 'HOURLY') {
    end = input.endAt;
    requireThat(Number.isSafeInteger(end) && end > start && end <= midnight(start) + DAY, 'Khung giờ phải nằm trong một ngày.');
    const step = room.slotMinutes || 60, min = room.minBookingMinutes || 60;
    requireThat([30, 60].includes(step), 'Bước giờ phòng không hợp lệ.');
    const from = (start - midnight(start)) / 60000, to = (end - midnight(start)) / 60000;
    requireThat(from % step === 0 && to % step === 0 && to - from >= min, 'Khung giờ phải khớp bước giờ và thời lượng tối thiểu.');
    const weekday = local(start).getUTCDay() || 7;
    requireThat((room.workingDays || [1, 2, 3, 4, 5, 6, 7]).includes(weekday)
      && from >= minutes(room.openTime) && to <= minutes(room.closeTime), 'Phòng đóng cửa trong khung giờ đã chọn.');
    requireThat(money(room.pricePerHour) && room.pricePerHour > 0, 'Giá giờ không hợp lệ.');
    rentalAmount = Math.round(room.pricePerHour * (to - from) / 60);
    const slots = [];
    for (let m = from; m < to; m += 30) slots.push(String(m));
    locks = [{path: 'availabilityDays/' + input.roomId + '_' + dateKey(start).replaceAll('-', ''), slots}];
  } else {
    requireThat(mode === 'FIXED' && [1, 3].includes(input.months), 'Chỉ hỗ trợ một tháng hoặc một quý.');
    const d = local(start), month = dateKey(start).slice(0, 7);
    requireThat(d.getUTCDate() === 1 && start === midnight(start), 'Thuê cố định bắt đầu lúc 00:00 ngày 1 (giờ Việt Nam).');
    requireThat(room.fixedStartMonth && month >= room.fixedStartMonth, 'Chưa đến tháng chủ phòng nhận thuê.');
    end = Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + input.months, 1) - OFFSET;
    rentalAmount = input.months === 1 ? room.pricePerMonth : room.pricePerQuarter;
    requireThat(money(rentalAmount) && rentalAmount > 0, 'Phòng không hỗ trợ kỳ thuê đã chọn.');
    for (let i = 0; i < input.months; i++) {
      const m = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + i, 1)).toISOString().slice(0, 7).replace('-', '');
      locks.push({path: 'fixedMonthLocks/' + input.roomId + '_' + m, slots: ['month']});
    }
  }
  for (const value of room.blackoutDates || []) {
    const at = Date.parse(legacyDate(value) + 'T00:00:00+07:00');
    requireThat(Number.isFinite(at) && !overlaps(start, end, at, at + DAY), 'Phòng đã khóa lịch trong kỳ đã chọn.');
  }
  for (const b of room.blackoutRanges || [])
    requireThat(!overlaps(start, end, millis(b.startAt), millis(b.endAt)), 'Phòng đã khóa lịch: ' + b.reason);
  const depositAmount = room.deposit || 0;
  requireThat(money(rentalAmount) && money(depositAmount), 'Giá/cọc không hợp lệ.');
  return {start, end, mode, locks, priceSnapshot: {currency: 'VND', rentalAmount, feeAmount: 0, depositAmount,
    totalAmount: rentalAmount, paymentDueAmount: rentalAmount + depositAmount,
    rentalPolicy: room.rentalPolicy || '', renewalPolicy: room.renewalPolicy || ''}};
}
module.exports = {BookingError, requireThat, millis, dateKey, overlaps, occupies, validateRoom, plan, money, legacyDate};
