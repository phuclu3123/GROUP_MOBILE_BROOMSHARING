'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const R = require('../booking-rules');
const at = s => Date.parse(s + '+07:00');
const now = at('2026-10-01T00:00:00');
const hourly = {activeMode:'HOURLY', pricePerHour:100001, deposit:200000, openTime:'08:00', closeTime:'22:00',
  slotMinutes:30,minBookingMinutes:60,workingDays:[1,2,3,4,5,6,7]};
const input = {roomId:'room',startAt:at('2026-10-20T08:00:00'),endAt:at('2026-10-20T09:30:00')};
test('half-open intervals permit adjacent reservations', () => {
  assert.equal(R.overlaps(1,2,2,3),false); assert.equal(R.overlaps(1,3,2,4),true);
});
test('hourly uses integer VND server price and separates deposit', () => {
  const p=R.plan(hourly,{...input,totalAmount:1},now);
  assert.equal(p.priceSnapshot.rentalAmount,150002);assert.equal(p.priceSnapshot.depositAmount,200000);
  assert.equal(p.priceSnapshot.totalAmount,150002);assert.equal(p.priceSnapshot.paymentDueAmount,350002);
  assert.deepEqual(p.locks[0].slots,['480','510','540']);
});
test('rejects misalignment, too short, closed weekday, past, closing boundary', () => {
  for(const changes of [{startAt:input.startAt+60000},{endAt:input.startAt+1800000},{startAt:now-1},{endAt:at('2026-10-20T22:30:00')}])
    assert.throws(()=>R.plan(hourly,{...input,...changes},now));
  assert.throws(()=>R.plan({...hourly,workingDays:[1]},input,now));
});
test('blackouts include partial intervals, legacy dates and full fixed term', () => {
  assert.throws(()=>R.plan({...hourly,blackoutDates:['20/10/2026']},input,now));
  assert.throws(()=>R.plan({...hourly,blackoutRanges:[{startAt:input.startAt+1,endAt:input.endAt,reason:'Maintenance'}]},input,now));
  assert.doesNotThrow(()=>R.plan({...hourly,blackoutRanges:[{startAt:input.endAt,endAt:input.endAt+1000,reason:'Adjacent'}]},input,now));
});
test('calendar quarter crosses year and February leap day', () => {
  const r={activeMode:'FIXED',fixedStartMonth:'2026-01',pricePerMonth:3000000,pricePerQuarter:8000000,deposit:0};
  let p=R.plan(r,{roomId:'r',startAt:at('2026-12-01T00:00:00'),months:3},now);
  assert.equal(p.end,at('2027-03-01T00:00:00'));assert.equal(p.locks.length,3);
  p=R.plan({...r,fixedStartMonth:'2028-01'},{roomId:'r',startAt:at('2028-02-01T00:00:00'),months:1},at('2028-01-01T00:00:00'));
  assert.equal(p.end,at('2028-03-01T00:00:00'));assert.equal((p.end-p.start)/86400000,29);
});
test('fixed requires supported period, month boundary, permitted month, no blackout', () => {
  const r={activeMode:'FIXED',fixedStartMonth:'2026-11',pricePerMonth:3000000,pricePerQuarter:0,deposit:0};
  for(const x of [{startAt:at('2026-10-01T00:00:00'),months:1},{startAt:at('2026-11-02T00:00:00'),months:1},
    {startAt:at('2026-11-01T00:00:00'),months:3},{startAt:at('2026-11-01T00:00:00'),months:2}])
    assert.throws(()=>R.plan(r,{roomId:'r',...x},now));
  assert.throws(()=>R.plan({...r,blackoutDates:['2026-11-30']},{roomId:'r',startAt:at('2026-11-01T00:00:00'),months:1},now));
});
