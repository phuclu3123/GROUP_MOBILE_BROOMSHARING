'use strict';
const {test, before, after, beforeEach} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {initializeApp, deleteApp} = require('firebase-admin/app');
const {getFirestore} = require('firebase-admin/firestore');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {doc, setDoc, updateDoc, getDoc, deleteDoc, serverTimestamp, collection, query, where, getDocs} = require('firebase/firestore');
const {service} = require('../service');
const {adminService} = require('../admin-service');
let app, db, api, env;
const projectId = 'demo-broomsharing';
const at = s => Date.parse(s + '+07:00');
const base = new Date(Date.now() + 7 * 86400000).toISOString().slice(0,10);
const start = at(base + 'T08:00:00'), end = at(base + 'T09:00:00');
const room = () => ({id:'r',schemaVersion:1,ownerUid:'h',buildingId:'b',roomName:'Room',roomType:'Meeting',address:'Address',floor:'1',capacity:5,area:20,
  activeMode:'HOURLY',pricePerHour:100000,pricePerMonth:3000000,pricePerQuarter:8000000,deposit:200000,
  approvalStatus:'APPROVED',listingStatus:'ACTIVE',workingDays:[1,2,3,4,5,6,7],slotMinutes:30,minBookingMinutes:60,
  openTime:'08:00',closeTime:'22:00',amenities:[],rules:'Rules',imageUrls:['https://example.com/room.jpg'],
  blackoutDates:[],blackoutRanges:[],fixedStartMonth:base.slice(0,7),renewalPolicy:'Manual renewal',rentalPolicy:'Cancel before start; no money refund in demo'});
const request = key => ({roomId:'r',startAt:start,endAt:end,idempotencyKey:key});
before(async()=>{
  assert.match(process.env.FIRESTORE_EMULATOR_HOST || '', /^(127\.0\.0\.1|localhost):8085$/);
  app=initializeApp({projectId});db=getFirestore(app,'broomsharing');api=service(db);
  env=await initializeTestEnvironment({projectId,firestore:{host:'127.0.0.1',port:8085,rules:fs.readFileSync('../firestore.secure.rules','utf8')}});
});
beforeEach(async()=>{
  await Promise.all(['users','buildings','rooms','bookings','availabilityDays','fixedMonthLocks','bookingEvents','auditLogs','hostApplications','propertyVerifications','reports'].map(c=>db.recursiveDelete(db.collection(c))));
  for(const [uid,role] of [['h','HOST'],['g1','GUEST'],['g2','GUEST'],['a','ADMIN']])
    await db.doc('users/'+uid).set({uid,role,status:'ACTIVE',name:uid,phone:'+84901234567'});
  await db.doc('buildings/b').set({id:'b',ownerUid:'h',name:'Building',status:'ACTIVE'});
  await db.doc('rooms/r').set(room());
});
after(async()=>{await env?.cleanup();await deleteApp(app);});
test('two guests racing for one slot: one booking and one lock owner',async()=>{
  const results=await Promise.allSettled([api.create('g1',request('race1')),api.create('g2',request('race2'))]);
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
  assert.equal((await db.collection('bookings').get()).size,1);
  const locks=await db.collection('availabilityDays').get();assert.equal(locks.size,1);
  assert.equal(new Set(Object.values(locks.docs[0].get('slotsBusy'))).size,1);
});

test('guest availability returns only busy intervals and respects active room/owner',async()=>{
  await api.create('g1',request('availability'));
  const result=await api.availability('g2',{roomId:'r',startAt:start,endAt:end+3600000});
  assert.deepEqual(result,{intervals:[{startAt:start,endAt:end}]});
  assert.deepEqual(await api.availability('g2',{roomId:'r',startAt:end,endAt:end+3600000}),{intervals:[]});
  await assert.rejects(api.availability('g2',{roomId:'r',startAt:start,endAt:start+101*86400000}));
  await db.doc('users/h').update({status:'BLOCKED'});
  await assert.rejects(api.availability('g2',{roomId:'r',startAt:start,endAt:end}));
});

test('favorites rules isolate owners, validate fields and permit removal of unpublished rooms',async()=>{
  await env.withSecurityRulesDisabled(async context=>{
    const store=context.firestore();
    for(const uid of ['g1','g2'])await setDoc(doc(store,'users/'+uid),{uid,role:'GUEST',status:'ACTIVE'});
    await setDoc(doc(store,'rooms/r'),{approvalStatus:'APPROVED',listingStatus:'ACTIVE'});
  });
  const g1=env.authenticatedContext('g1').firestore(),g2=env.authenticatedContext('g2').firestore();
  const favorite=doc(g1,'users/g1/favorites/r');
  await assertSucceeds(setDoc(favorite,{roomId:'r',roomName:'Room',createdAt:serverTimestamp()}));
  await assertSucceeds(getDoc(favorite));
  await assertFails(getDoc(doc(g2,'users/g1/favorites/r')));
  await assertFails(setDoc(doc(g2,'users/g1/favorites/r'),{roomId:'r',roomName:'X',createdAt:serverTimestamp()}));
  await assertFails(setDoc(favorite,{roomId:'different',roomName:'X',createdAt:serverTimestamp()}));
  await assertFails(setDoc(favorite,{roomId:'r',roomName:'X',totalAmount:1,createdAt:serverTimestamp()}));
  await env.withSecurityRulesDisabled(async context=>{
    await setDoc(doc(context.firestore(),'rooms/r'),{approvalStatus:'DRAFT',listingStatus:'INACTIVE'});
  });
  await assertFails(setDoc(favorite,{roomId:'r',roomName:'X',createdAt:serverTimestamp()}));
  await assertSucceeds(deleteDoc(favorite));
});
test('idempotent retries return same booking; reused key with different input rejected',async()=>{
  const [a,b]=await Promise.all([api.create('g1',request('retry')),api.create('g1',request('retry'))]);
  assert.equal(a.bookingId,b.bookingId);assert.equal((await db.collection('bookings').get()).size,1);
  await assert.rejects(api.create('g1',{...request('retry'),endAt:end+1800000}));
});
test('server ignores forged price, rejects other host, requires reason and valid transition',async()=>{
  const result=await api.create('g1',{...request('price'),totalAmount:1,paymentStatus:'PAID'});
  const b=(await db.doc('bookings/'+result.bookingId).get()).data();
  assert.equal(b.totalAmount,100000);assert.equal(b.paymentStatus,'DEMO_PENDING');
  await assert.rejects(api.transition('g2',{bookingId:result.bookingId,status:'CONFIRMED'}));
  await assert.rejects(api.transition('h',{bookingId:result.bookingId,status:'REJECTED',reason:''}));
  await api.transition('h',{bookingId:result.bookingId,status:'CONFIRMED'});
  await api.transition('h',{bookingId:result.bookingId,status:'CONFIRMED'});
  await assert.rejects(api.transition('h',{bookingId:result.bookingId,status:'REJECTED',reason:'Late change'}));
});
test('cancel atomically releases only its slots; idempotent retry does not clear replacement',async()=>{
  const a=await api.create('g1',request('cancel'));
  await api.transition('g1',{bookingId:a.bookingId,status:'CANCELLED'});
  const b=await api.create('g2',request('replacement'));
  await api.transition('g1',{bookingId:a.bookingId,status:'CANCELLED'});
  await assert.rejects(api.create('g1',request('conflict')));
  const locks=await db.collection('availabilityDays').get();
  assert.deepEqual([...new Set(Object.values(locks.docs[0].get('slotsBusy')))],[b.bookingId]);
});
test('adjacent intervals succeed; config cannot invalidate existing booking; pause preserves history',async()=>{
  const b=await api.create('g1',request('first'));
  await api.create('g2',{...request('next'),startAt:end,endAt:end+3600000});
  await assert.rejects(api.saveRoom('h',{roomId:'r',room:{...room(),approvalStatus:'DRAFT',activeMode:'FIXED'}}));
  await api.listing('h',{roomId:'r',status:'INACTIVE'});
  assert.equal((await db.doc('bookings/'+b.bookingId).get()).get('status'),'PENDING');
  await assert.rejects(api.create('g1',{...request('paused'),startAt:end+3600000,endAt:end+7200000}));
  await db.doc('rooms/r').update({listingStatus:'SUSPENDED'});
  await assert.rejects(api.listing('h',{roomId:'r',status:'ACTIVE'}));
});
test('host config cannot forge approval/owner; submit stays private pending review',async()=>{
  const r={...room(),approvalStatus:'PENDING_REVIEW',ownerUid:'g1'};
  await api.saveRoom('h',{roomId:'r',room:r});
  const saved=await db.doc('rooms/r').get();assert.equal(saved.get('ownerUid'),'h');assert.equal(saved.get('approvalStatus'),'PENDING_REVIEW');
  await assert.rejects(api.create('g1',request('private')));
  await assert.rejects(api.saveRoom('h',{roomId:'r',room:{...r,approvalStatus:'APPROVED'}}));
});
test('blocked owner cannot accept or receive new bookings, existing history remains',async()=>{
  const b=await api.create('g1',request('blocked'));
  await db.doc('users/h').update({status:'BLOCKED'});
  await assert.rejects(api.transition('h',{bookingId:b.bookingId,status:'CONFIRMED'}));
  await assert.rejects(api.create('g2',{...request('new'),startAt:end,endAt:end+3600000}));
  assert.equal((await db.collection('bookings').get()).size,1);
});
test('month vs quarter race reserves calendar months exactly once',async()=>{
  const d=new Date(Date.now()+35*86400000), first=Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,1)-7*3600000;
  await db.doc('rooms/r').update({activeMode:'FIXED',fixedStartMonth:new Date(first+7*3600000).toISOString().slice(0,7)});
  const results=await Promise.allSettled([api.create('g1',{roomId:'r',startAt:first,months:1,idempotencyKey:'month'}),
    api.create('g2',{roomId:'r',startAt:first,months:3,idempotencyKey:'quarter'})]);
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
  assert.equal((await db.collection('bookings').get()).size,1);
  const winner=results.find(r=>r.status==='fulfilled').value;
  const booking=(await db.doc('bookings/'+winner.bookingId).get()).data();
  assert.equal((await db.collection('fixedMonthLocks').get()).size,booking.lockPlan.length);
});
test('price edits preserve existing snapshot; blackout overlapping held slot is rejected',async()=>{
  const b = await api.create('g1',request('snapshot'));
  await api.saveRoom('h',{roomId:'r',room:{...room(),approvalStatus:'DRAFT',pricePerHour:999999}});
  assert.equal((await db.doc('bookings/'+b.bookingId).get()).get('totalAmount'),100000);
  await assert.rejects(api.saveRoom('h',{roomId:'r',room:{...room(),approvalStatus:'DRAFT',
    blackoutRanges:[{startAt:start,endAt:end,reason:'Maintenance'}]}}));
});
test('config and booking race is serialized on room revision',async()=>{
  const results=await Promise.allSettled([api.create('g1',request('config-race')),
    api.saveRoom('h',{roomId:'r',room:{...room(),approvalStatus:'DRAFT',blackoutRanges:[{startAt:start,endAt:end,reason:'Maintenance'}]}})]);
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
});
test('quarter cancellation releases all month locks and accepts replacement month',async()=>{
  const d=new Date(Date.now()+35*86400000), first=Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,1)-7*3600000;
  await db.doc('rooms/r').update({activeMode:'FIXED',fixedStartMonth:new Date(first+7*3600000).toISOString().slice(0,7)});
  const b=await api.create('g1',{roomId:'r',startAt:first,months:3,idempotencyKey:'q-cancel'});
  await api.transition('g1',{bookingId:b.bookingId,status:'CANCELLED'});
  const locks=await db.collection('fixedMonthLocks').get();
  assert.equal(locks.size,3);for(const l of locks.docs) assert.deepEqual(l.get('slotsBusy'),{});
  await api.create('g2',{roomId:'r',startAt:first,months:1,idempotencyKey:'month-after-cancel'});
});
test('rules deny all direct booking/lock/config writes and unrelated reads',async()=>{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx=>{
    const f=ctx.firestore();
    for(const [uid,role] of [['h','HOST'],['g1','GUEST'],['g2','GUEST'],['a','ADMIN']]) await setDoc(doc(f,'users',uid),{uid,role,status:'ACTIVE'});
    await setDoc(doc(f,'rooms','r'),room());
    await setDoc(doc(f,'bookings','x'),{guestUid:'g1',ownerUid:'h',status:'PENDING'});
  });
  const g=env.authenticatedContext('g1').firestore(),h=env.authenticatedContext('h').firestore(), stranger=env.authenticatedContext('g2').firestore();
  await assertSucceeds(getDoc(doc(g,'bookings','x')));await assertSucceeds(getDoc(doc(h,'bookings','x')));
  await assertFails(getDoc(doc(stranger,'bookings','x')));
  await assertFails(updateDoc(doc(h,'bookings','x'),{status:'CONFIRMED'}));
  await assertFails(setDoc(doc(g,'bookings','forged'),{guestUid:'g1',ownerUid:'h',status:'PENDING'}));
  await assertFails(setDoc(doc(h,'availabilityDays','r_day'),{roomId:'r',slotsBusy:{}}));
  await assertFails(updateDoc(doc(h,'rooms','r'),{activeMode:'FIXED'}));
  await assertFails(updateDoc(doc(g,'users','g1'),{role:'ADMIN'}));
  const admin=env.authenticatedContext('a').firestore();
  await assertFails(updateDoc(doc(admin,'users','h'),{status:'BLOCKED'}));
  await assertFails(updateDoc(doc(admin,'rooms','r'),{approvalStatus:'APPROVED'}));
  await assertFails(setDoc(doc(admin,'auditLogs','fake'),{actorUid:'a',action:'APPROVE'}));
  await assertSucceeds(getDocs(query(collection(g,'rooms'),where('approvalStatus','==','APPROVED'),where('listingStatus','==','ACTIVE'))));
});

test('admin host approval assigns role and audit atomically; other roles cannot approve',async()=>{
  const admin=adminService(db);
  await db.doc('hostApplications/application').set({applicantUid:'g1',status:'PENDING'});
  await assert.rejects(admin.review('g2',{kind:'HOST',id:'application',approve:true}));
  await admin.review('a',{kind:'HOST',id:'application',approve:true});
  assert.equal((await db.doc('users/g1').get()).get('role'),'HOST');
  assert.equal((await db.doc('hostApplications/application').get()).get('status'),'APPROVED');
  const logs=await db.collection('auditLogs').get();assert.equal(logs.size,1);
  assert.equal(logs.docs[0].get('actorUid'),'a');assert.equal(logs.docs[0].get('before').status,'PENDING');
  await assert.rejects(admin.review('a',{kind:'HOST',id:'application',approve:false,reason:'Changed mind'}));
});
test('admin room approval validates pricing, owner and building instead of accepting client amounts',async()=>{
  const admin=adminService(db);
  await db.doc('rooms/r').update({approvalStatus:'PENDING_REVIEW'});
  await db.doc('users/h').update({status:'BLOCKED'});
  await assert.rejects(admin.review('a',{kind:'ROOM',id:'r',approve:true}));
  await db.doc('users/h').update({status:'ACTIVE'});
  await admin.review('a',{kind:'ROOM',id:'r',approve:true});
  assert.equal((await db.doc('rooms/r').get()).get('approvalStatus'),'APPROVED');
  assert.equal((await db.collection('auditLogs').get()).size,1);
});
test('blocking owner freezes listings, preserves bookings, restores only owner-blocked listings',async()=>{
  const admin=adminService(db),booking=await api.create('g1',request('admin-block'));
  await db.doc('rooms/inactive').set({...room(),id:'inactive',listingStatus:'INACTIVE'});
  await db.doc('rooms/frozen').set({...room(),id:'frozen',listingStatus:'SUSPENDED'});
  await assert.rejects(admin.moderateHost('g1',{uid:'h',status:'BLOCKED',reason:'Fraud'}));
  await assert.rejects(admin.moderateHost('a',{uid:'h',status:'BLOCKED',reason:''}));
  await admin.moderateHost('a',{uid:'h',status:'BLOCKED',reason:'Investigating report'});
  assert.equal((await db.doc('users/h').get()).get('status'),'BLOCKED');
  assert.equal((await db.doc('rooms/r').get()).get('listingStatus'),'SUSPENDED');
  assert.equal((await db.doc('bookings/'+booking.bookingId).get()).get('status'),'PENDING');
  await assert.rejects(api.create('g2',{...request('blocked2'),startAt:end,endAt:end+3600000}));
  await assert.rejects(api.listing('h',{roomId:'r',status:'ACTIVE'}));
  await admin.moderateHost('a',{uid:'h',status:'ACTIVE',reason:'Review resolved'});
  assert.equal((await db.doc('rooms/r').get()).get('listingStatus'),'ACTIVE');
  assert.equal((await db.doc('rooms/inactive').get()).get('listingStatus'),'INACTIVE');
  assert.equal((await db.doc('rooms/frozen').get()).get('listingStatus'),'SUSPENDED');
});
test('manual room freeze during owner block is preserved; unsuspend never auto-publishes',async()=>{
  const admin=adminService(db);
  await admin.moderateHost('a',{uid:'h',status:'BLOCKED',reason:'Owner review'});
  await admin.moderateRoom('a',{roomId:'r',status:'SUSPENDED',reason:'Independent room issue'});
  await assert.rejects(admin.moderateRoom('a',{roomId:'r',status:'INACTIVE',reason:'Too early'}));
  await admin.moderateHost('a',{uid:'h',status:'ACTIVE',reason:'Owner resolved'});
  assert.equal((await db.doc('rooms/r').get()).get('listingStatus'),'SUSPENDED');
  await admin.moderateRoom('a',{roomId:'r',status:'INACTIVE',reason:'Room resolved'});
  assert.equal((await db.doc('rooms/r').get()).get('listingStatus'),'INACTIVE');
});
test('verification requires evidence, note, matching ownership and explicit review; no partner badge',async()=>{
  const admin=adminService(db);
  await db.doc('propertyVerifications/v').set({ownerUid:'h',roomId:'r',verificationType:'LOCATION',status:'PENDING',evidenceRefs:[]});
  await assert.rejects(admin.review('a',{kind:'VERIFICATION',id:'v',approve:true,reason:'Checked',evidenceReviewed:true}));
  await db.doc('propertyVerifications/v').update({evidenceRefs:['verification/h/photo.png']});
  await assert.rejects(admin.review('a',{kind:'VERIFICATION',id:'v',approve:true,reason:'Checked',evidenceReviewed:false}));
  await assert.rejects(admin.review('a',{kind:'VERIFICATION',id:'v',approve:true,reason:'',evidenceReviewed:true}));
  await admin.review('a',{kind:'VERIFICATION',id:'v',approve:true,reason:'Reviewed location evidence',evidenceReviewed:true});
  const v=await db.doc('propertyVerifications/v').get();
  assert.equal(v.get('status'),'VERIFIED');assert.equal(v.get('method'),'ADMIN_REVIEWED');assert.equal(v.get('partnerName'),'');
  assert.equal((await db.doc('rooms/r').get()).get('locationVerification'),'ADMIN_REVIEWED');
  assert.equal((await db.doc('rooms/r').get()).get('photosVerification'),undefined);
});
test('private evidence denies non-admin, wrong request and another owners storage path',async()=>{
  const admin=adminService(db);
  await db.doc('propertyVerifications/v').set({ownerUid:'h',status:'PENDING',evidenceRefs:['verification/h/a.pdf','verification/g1/b.pdf']});
  await assert.rejects(admin.evidence('g1',{id:'v',path:'verification/h/a.pdf'}));
  await assert.rejects(admin.evidence('a',{id:'v',path:'verification/g1/b.pdf'}));
  await assert.rejects(admin.evidence('a',{id:'other',path:'verification/h/a.pdf'}));
  assert.equal((await admin.evidence('a',{id:'v',path:'verification/h/a.pdf'})).path,'verification/h/a.pdf');
});
test('booking state machine enforces check-in timing, completion timing and keeps payment separate',async()=>{
  let now=Date.now();const timed=service(db,()=>now);
  const b=await timed.create('g1',request('lifecycle'));
  await timed.transition('h',{bookingId:b.bookingId,status:'CONFIRMED'});
  await assert.rejects(timed.transition('h',{bookingId:b.bookingId,status:'CHECKED_IN'}));
  now=start+60000;
  await assert.rejects(timed.transition('g1',{bookingId:b.bookingId,status:'CHECKED_IN'}));
  await timed.transition('h',{bookingId:b.bookingId,status:'CHECKED_IN'});
  await assert.rejects(timed.transition('h',{bookingId:b.bookingId,status:'COMPLETED'}));
  let locks=await db.collection('availabilityDays').get();assert.ok(Object.keys(locks.docs[0].get('slotsBusy')).length>0);
  now=end;
  await timed.transition('h',{bookingId:b.bookingId,status:'COMPLETED'});
  const saved=await db.doc('bookings/'+b.bookingId).get();assert.equal(saved.get('paymentStatus'),'DEMO_PENDING');
  assert.equal(saved.get('status'),'COMPLETED');
  locks=await db.collection('availabilityDays').get();assert.deepEqual(locks.docs[0].get('slotsBusy'),{});
  await assert.rejects(timed.transition('h',{bookingId:b.bookingId,status:'CONFIRMED'}));
  assert.equal((await db.collection('auditLogs').where('targetPath','==','bookings/'+b.bookingId).get()).size,4);
});
