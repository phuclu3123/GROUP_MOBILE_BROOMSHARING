'use strict';
// Deliberate live integration harness. Never uses recursiveDelete or collection-wide deletion.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const {Firestore} = require('@google-cloud/firestore');
const projectId = 'broom-sharing-firebase-mobile';
const root = path.resolve(__dirname, '../..');
const output = path.join(root, 'build/acceptance');
fs.mkdirSync(output, {recursive:true});
const manifestPath = path.join(output, 'run.json');
const {getGlobalDefaultAccount} = require('firebase-tools/lib/auth');
const cliApi = require('firebase-tools/lib/api');
const account = getGlobalDefaultAccount();
assert(account?.tokens?.refresh_token, 'Firebase CLI login required');
const credentials = {type:'authorized_user', client_id:cliApi.clientId(), client_secret:cliApi.clientSecret(), refresh_token:account.tokens.refresh_token};
const db = new Firestore({projectId, databaseId:'broomsharing', credentials});
const config = JSON.parse(fs.readFileSync(path.join(root,'app/google-services.json'),'utf8'));
assert.equal(config.project_info.project_id, projectId);
const apiKey = config.client[0].api_key[0].current_key;
const groups = ['users','buildings','rooms','hostApplications','propertyVerifications','reports','bookings','availabilityDays','fixedMonthLocks','bookingEvents','auditLogs'];
let state;
const persist = () => fs.writeFileSync(manifestPath,JSON.stringify(state,null,2));
async function auth(method, body) {
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:${method}?key=${apiKey}`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  const data = await res.json();
  if (!res.ok) throw new Error('Auth '+method+': '+data.error?.message);
  return data;
}
async function baseline() {
  const result = {};
  for(const group of groups) {
    const snaps = await db.collection(group).select().get();
    for(const s of snaps.docs) result[s.ref.path] = s.updateTime.toMillis();
  }
  return result;
}
async function createDoc(p,data) {
  state.paths.push(p);persist();
  await db.doc(p).create(data);
}
async function call(name,who,data) {
  const res = await fetch(`http://127.0.0.1:5001/${projectId}/asia-southeast1/${name}`, {method:'POST',headers:{'Content-Type':'application/json',...(who?{Authorization:'Bearer '+state.accounts[who].idToken}:{})},body:JSON.stringify({data})});
  const body = await res.json();
  if(body.error) { const e = new Error(body.error.message);e.code=body.error.status;throw e; }
  assert(res.ok);return body.result;
}
async function check(name,fn) {
  try { await fn();state.results.push({name,status:'PASS'});console.log('PASS '+name); }
  catch(e) {state.results.push({name,status:'FAIL',code:e.code||'',message:e.message});console.log('FAIL '+name+': '+e.message);throw e;}
  finally {persist();}
}
async function denied(fn,code) {await assert.rejects(fn,e=>e.code===code);}
async function discover() {
  const actors = new Set(Object.values(state.accounts).map(a=>a.localId));
  for(const group of ['bookings','bookingEvents','auditLogs','availabilityDays','fixedMonthLocks']) {
    const rows=await db.collection(group).get();
    for(const doc of rows.docs) {
      const d=doc.data();
      if(actors.has(d.actorUid)||actors.has(d.guestUid)||actors.has(d.ownerUid)||d.roomId===state.roomId||d.roomId===state.fixedId) {
        assert(!Object.hasOwn(state.baseline,doc.ref.path),'Refuse to clean pre-existing record');
        if(!state.paths.includes(doc.ref.path)) state.paths.push(doc.ref.path);
      }
    }
  }
  persist();
}
async function prepare() {
  if(fs.existsSync(manifestPath)) {const old=JSON.parse(fs.readFileSync(manifestPath));assert(old.cleaned,'Uncleaned acceptance run exists; clean it first');}
  const run='acceptance_'+Date.now();
  state={run,projectId,databaseId:'broomsharing',createdAt:new Date().toISOString(),paths:[],accounts:{},results:[],baseline:await baseline(),roomId:run+'_room',fixedId:run+'_fixed',buildingId:run+'_building'};
  persist();
  for(const [label,role] of [['host','HOST'],['guest','GUEST'],['guest2','GUEST'],['admin','ADMIN']]) {
    const password=crypto.randomBytes(18).toString('base64url')+'aA1!';
    const email=run+'_'+label+'@example.com';
    const a=await auth('signUp',{email,password,returnSecureToken:true});
    state.accounts[label]={...a,password};persist();
    await createDoc('users/'+a.localId,{uid:a.localId,name:'NGHIEM THU '+label,email,role,status:'ACTIVE',phone:'+84900000000',schemaVersion:1});
  }
  const ownerUid=state.accounts.host.localId;
  await createDoc('buildings/'+state.buildingId,{id:state.buildingId,ownerUid,name:'NGHIEM THU - temporary',status:'ACTIVE',address:'Temporary acceptance fixture'});
  const base=new Date(Date.now()+7*86400000).toISOString().slice(0,10);
  state.start=Date.parse(base+'T08:00:00+07:00');state.end=state.start+3600000;persist();
  const room={id:state.roomId,schemaVersion:1,ownerUid,buildingId:state.buildingId,roomName:'NGHIEM THU - temporary room',roomType:'Meeting',address:'Temporary acceptance fixture',floor:'1',capacity:5,area:20,activeMode:'HOURLY',pricePerHour:100000,pricePerMonth:3000000,pricePerQuarter:8000000,deposit:200000,approvalStatus:'PENDING_REVIEW',listingStatus:'ACTIVE',workingDays:[1,2,3,4,5,6,7],slotMinutes:30,minBookingMinutes:60,openTime:'08:00',closeTime:'22:00',amenities:[],rules:'Acceptance only',imageUrls:['https://example.com/acceptance.jpg'],blackoutDates:[],blackoutRanges:[],fixedStartMonth:base.slice(0,7),renewalPolicy:'Manual renewal',rentalPolicy:'Demo payment only'};
  state.room=room;persist();
  await createDoc('rooms/'+state.roomId,room);
  await createDoc('rooms/'+state.fixedId,{...room,id:state.fixedId,roomName:'NGHIEM THU fixed',activeMode:'FIXED',approvalStatus:'APPROVED'});
  console.log('Prepared isolated real Auth accounts and named Firestore fixtures.');
}
async function verify() {
  state=JSON.parse(fs.readFileSync(manifestPath));
  const req=key=>({roomId:state.roomId,startAt:state.start,endAt:state.end,idempotencyKey:state.run+'_'+key});
  await check('unauthenticated callable rejected',()=>denied(()=>call('quoteBooking',null,req('anon')),'UNAUTHENTICATED'));
  await check('guest cannot review room',()=>denied(()=>call('reviewAdminItem','guest',{kind:'ROOM',id:state.roomId,approve:true}),'PERMISSION_DENIED'));
  await check('Admin approves pending room',async()=>{await call('reviewAdminItem','admin',{kind:'ROOM',id:state.roomId,approve:true});assert.equal((await db.doc('rooms/'+state.roomId).get()).get('approvalStatus'),'APPROVED');});
  await check('public host projection',async()=>{const h=await call('getPublicHost','guest',{ownerUid:state.accounts.host.localId});assert.equal(h.active,true);assert(!Object.hasOwn(h,'email'));});
  await check('quote uses server price',async()=>{const q=await call('quoteBooking','guest',{...req('quote'),totalAmount:1});assert.equal(q.priceSnapshot.totalAmount,100000);});
  await check('two real guests race: exactly one wins',async()=>{
    const r=await Promise.allSettled([call('createBooking','guest',req('race1')),call('createBooking','guest2',req('race2'))]);
    assert.equal(r.filter(x=>x.status==='fulfilled').length,1);
    state.winner=r[0].status==='fulfilled'?'guest':'guest2';state.winnerKey=state.winner==='guest'?'race1':'race2';state.bookingId=r.find(x=>x.status==='fulfilled').value.bookingId;
    assert.equal(r.find(x=>x.status==='rejected').reason.code,'ALREADY_EXISTS');persist();
  });
  await check('retry returns same booking',async()=>assert.equal((await call('createBooking',state.winner,req(state.winnerKey))).bookingId,state.bookingId));
  await check('wrong role cannot confirm booking',()=>denied(()=>call('transitionBooking','guest',{bookingId:state.bookingId,status:'CONFIRMED'}),'PERMISSION_DENIED'));
  await check('Host confirms; Firestore persists payment demo',async()=>{await call('transitionBooking','host',{bookingId:state.bookingId,status:'CONFIRMED'});const d=(await db.doc('bookings/'+state.bookingId).get()).data();assert.equal(d.status,'CONFIRMED');assert.equal(d.paymentStatus,'DEMO_PENDING');assert.equal(d.totalAmount,100000);});
  await check('Guest cancels and releases slots',async()=>{await call('transitionBooking',state.winner,{bookingId:state.bookingId,status:'CANCELLED'});const r=await call('createBooking','guest',req('replacement'));state.replacementId=r.bookingId;persist();});
  await check('Host rejects with mandatory reason',async()=>{await denied(()=>call('transitionBooking','host',{bookingId:state.replacementId,status:'REJECTED'}),'INVALID_ARGUMENT');await call('transitionBooking','host',{bookingId:state.replacementId,status:'REJECTED',reason:'Acceptance test'});});
  await check('Admin suspends; Host cannot reopen',async()=>{await call('moderateRoom','admin',{roomId:state.roomId,status:'SUSPENDED',reason:'Acceptance test'});await denied(()=>call('setHostListing','host',{roomId:state.roomId,status:'ACTIVE'}),'FAILED_PRECONDITION');await call('moderateRoom','admin',{roomId:state.roomId,status:'INACTIVE',reason:'Acceptance finished'});await call('setHostListing','host',{roomId:state.roomId,status:'ACTIVE'});});
  await check('Host saves room; Admin reapproves',async()=>{await call('saveHostRoom','host',{roomId:state.roomId,room:{...state.room,approvalStatus:'PENDING_REVIEW'}});await call('reviewAdminItem','admin',{kind:'ROOM',id:state.roomId,approve:true});});
  await check('block and unblock Host preserves room lifecycle',async()=>{await call('moderateHost','admin',{uid:state.accounts.host.localId,status:'BLOCKED',reason:'Acceptance test'});assert.equal((await call('getPublicHost','guest',{ownerUid:state.accounts.host.localId})).active,false);await call('moderateHost','admin',{uid:state.accounts.host.localId,status:'ACTIVE',reason:'Acceptance finished'});});
  await check('month/quarter conflict on real Firestore',async()=>{const d=new Date(Date.now()+40*86400000);const startAt=Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,1)-7*3600000;const r=await Promise.allSettled([call('createBooking','guest',{roomId:state.fixedId,startAt,months:1,idempotencyKey:state.run+'_month'}),call('createBooking','guest2',{roomId:state.fixedId,startAt,months:3,idempotencyKey:state.run+'_quarter'})]);assert.equal(r.filter(x=>x.status==='fulfilled').length,1);assert.equal(r.find(x=>x.status==='rejected').reason.code,'ALREADY_EXISTS');});
  await check('Guest cannot request private evidence',()=>denied(()=>call('getVerificationEvidence','guest',{id:state.run,path:'verification/other/test.png'}),'PERMISSION_DENIED'));
  await discover();
  await check('all pre-existing documents unchanged',async()=>{const after=await baseline();for(const [p,t] of Object.entries(state.baseline))assert.equal(after[p],t,p);});
  console.log('Live callable checks complete; fixtures retained for Android checks.');
}
async function cleanup() {
  state=JSON.parse(fs.readFileSync(manifestPath));
  if(state.cleaned) {console.log('Run already cleaned');return;}
  await discover();
  if(state.storagePaths?.length) {
    const {Storage}=require('@google-cloud/storage');
    const bucket=new Storage({projectId,credentials}).bucket(config.project_info.storage_bucket);
    for(const p of state.storagePaths) {
      assert(p.startsWith('verification/'+state.accounts.host.localId+'/'+state.run));
      await bucket.file(p).delete({ignoreNotFound:true});
    }
    state.storagePaths=[];persist();
  }
  for(const p of state.paths) {assert(!Object.hasOwn(state.baseline,p));await db.doc(p).delete();}
  for(const a of Object.values(state.accounts)) {
    const fresh=await auth('signInWithPassword',{email:a.email,password:a.password,returnSecureToken:true});
    await auth('delete',{idToken:fresh.idToken});
  }
  const after=await baseline();
  assert.deepEqual(after,state.baseline,'Existing data changed or test documents remain');
  state.cleaned=true;state.cleanedAt=new Date().toISOString();
  state.accounts=Object.fromEntries(Object.entries(state.accounts).map(([k,a])=>[k,{localId:a.localId}]));persist();
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify({projectId,databaseId:'broomsharing',run:state.run,results:state.results,cleaned:true,preExistingDocuments:Object.keys(state.baseline).length,deletedTestDocuments:state.paths.length},null,2));
  console.log('Cleanup verified: exact test records/accounts deleted; existing records unchanged.');
}
async function evidence() {
  state=JSON.parse(fs.readFileSync(manifestPath));
  const {Storage}=require('@google-cloud/storage');
  const bucket=new Storage({projectId,credentials}).bucket(config.project_info.storage_bucket);
  try {await bucket.getMetadata();}
  catch(e) {
    state.results.push({name:'real Storage evidence read and preview',status:'BLOCKED',code:e.code||'',message:'Storage bucket unavailable to current project/account'});persist();
    console.log('BLOCKED real Storage evidence: bucket unavailable, code '+e.code);return;
  }
  const ownerUid=state.accounts.host.localId;
  const storagePath='verification/'+ownerUid+'/'+state.run+'.png';
  const file=bucket.file(storagePath);
  const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aG1cAAAAASUVORK5CYII=','base64');
  state.storagePaths=[storagePath];persist();
  try {
    await file.save(png,{resumable:false,contentType:'image/png',preconditionOpts:{ifGenerationMatch:0}});
    await createDoc('propertyVerifications/'+state.run,{ownerUid,roomId:state.roomId,verificationType:'PHOTOS',status:'PENDING',evidenceRefs:[storagePath]});
    await check('Admin reads real private Storage evidence',async()=>{
      const r=await call('getVerificationEvidence','admin',{id:state.run,path:storagePath});assert.equal(r.base64,png.toString('base64'));
    });
    await check('Admin approves reviewed photo evidence',async()=>{await call('reviewAdminItem','admin',{kind:'VERIFICATION',id:state.run,approve:true,evidenceReviewed:true,reason:'Synthetic acceptance image, not a real property certificate'});});
  } finally {await file.delete({ignoreNotFound:true});state.storagePaths=[];persist();}
}
(async()=>{assert(!process.env.FIRESTORE_EMULATOR_HOST,'This harness requires real Firestore');const mode=process.argv[2];if(mode==='prepare')await prepare();else if(mode==='verify')await verify();else if(mode==='evidence')await evidence();else if(mode==='cleanup')await cleanup();else throw new Error('Use prepare / verify / evidence / cleanup');await db.terminate();})().catch(e=>{console.error(e.code||'ERROR',e.message);process.exitCode=1;});
