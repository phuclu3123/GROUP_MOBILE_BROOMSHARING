"""Isolated Firestore emulator checks for avatar and two-sided DEMO Host evidence."""
import base64, hashlib, json, pathlib, time, urllib.request, urllib.error
ROOT=pathlib.Path(__file__).resolve().parents[2]
DB='projects/broom-sharing-firebase-mobile/databases/profile_identity_qa'
BASE='http://127.0.0.1:8085'
OUT=ROOT/'output/verification/profile-identity-2026-10-26';OUT.mkdir(parents=True,exist_ok=True)
results=[]
def jwt(uid):
 def part(v):return base64.urlsafe_b64encode(json.dumps(v).encode()).decode().rstrip('=')
 return part({'alg':'none','typ':'JWT'})+'.'+part({'sub':uid,'user_id':uid,'aud':'broom-sharing-firebase-mobile','iss':'https://securetoken.google.com/broom-sharing-firebase-mobile','iat':int(time.time()),'exp':int(time.time())+3600,'firebase':{'sign_in_provider':'password'}})+'.'
def call(path,body=None,actor=None,method=None):
 req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),method=method,headers={'Content-Type':'application/json','Authorization':'Bearer '+('owner' if actor is None else jwt(actor))})
 try:
  with urllib.request.urlopen(req,timeout=30) as response:return response.status,json.load(response)
 except urllib.error.HTTPError as error:return error.code,json.loads(error.read())
def v(x):
 if x is None:return {'nullValue':None}
 if isinstance(x,bool):return {'booleanValue':x}
 if isinstance(x,int):return {'integerValue':str(x)}
 if isinstance(x,list):return {'arrayValue':{'values':[v(a) for a in x]}}
 return {'stringValue':x}
def seed(path,data):
 code,body=call('/v1/'+DB+'/documents/'+path,{'fields':{k:v(x) for k,x in data.items()}},method='PATCH');assert code==200,body
def profile(uid,role='GUEST',status='ACTIVE'):
 seed('users/'+uid,dict(uid=uid,role=role,status=status,name='Demo',phone='+84900000900',phoneVerified=False))
def write(path,data,mask=None,transforms=('updatedAt',)):
 w={'update':{'name':DB+'/documents/'+path,'fields':{k:v(x) for k,x in data.items()}}}
 if mask is not None:w['updateMask']={'fieldPaths':mask}
 if transforms:w['updateTransforms']=[{'fieldPath':p,'setToServerValue':'REQUEST_TIME'} for p in transforms]
 return w
def check(label,writes,actor,expected=200):
 code,body=call('/v1/'+DB+'/documents:commit',{'writes':writes},actor,'POST')
 results.append(dict(case=label,passed=code==expected,actual=code,expected=expected))
 print(label,'PASS' if code==expected else 'FAIL',flush=True)
 if code!=expected:print(str(body)[:900],flush=True)
def avatar(uid,leaf='face'):return 'https://res.cloudinary.com/demo_cloud/image/upload/v123/broomsharing/avatarMedia/'+uid+'/avatar/'+leaf+'.jpg'
def identity(uid,leaf='front'):return 'https://res.cloudinary.com/demo_cloud/image/upload/v123/broomsharing/hostIdentity/'+uid+'/'+uid+'/'+leaf+'.jpg'
def application(uid):return dict(id=uid,schemaVersion=2,applicantUid=uid,fullName='Demo Applicant',phone='0900000900',email='demo@example.com',organizationType='INDIVIDUAL',identityDoc='DEMO fictitious identity only',identityImageUrls=[identity(uid),identity(uid,'back')],evidenceMode='DEMO_PUBLIC',identityReviewedAt=None,status='PENDING',rejectionReason='')
source=(ROOT/'firestore.rules').read_text(encoding='utf-8')
code,body=call('/emulator/v1/'+DB+':securityRules',{'rules':{'files':[{'name':'firestore.rules','content':source}]}},method='PUT');assert code==200 and not any(x.get('severity')=='ERROR' for x in body.get('issues',[])),body
for uid,role,status in [('avatar_guest','GUEST','ACTIVE'),('avatar_host','HOST','ACTIVE'),('profile_admin','ADMIN','ACTIVE'),('avatar_blocked','GUEST','BLOCKED'),('avatar_peer','GUEST','ACTIVE')]:profile(uid,role,status)
for label,data,actor,expected in [
 ('Guest saves own avatar',{'photoUrl':avatar('avatar_guest')},'avatar_guest',200),
 ('Guest removes avatar',{'photoUrl':''},'avatar_guest',200),
 ('Foreign UID URL denied',{'photoUrl':avatar('avatar_peer')},'avatar_guest',403),
 ('Foreign user write denied',{'photoUrl':avatar('avatar_guest')},'avatar_peer',403),
 ('HTTP avatar denied',{'photoUrl':avatar('avatar_guest').replace('https:','http:')},'avatar_guest',403),
 ('PDF avatar denied',{'photoUrl':avatar('avatar_guest').replace('.jpg','.pdf')},'avatar_guest',403),
 ('Raw avatar denied',{'photoUrl':avatar('avatar_guest').replace('/image/','/raw/')},'avatar_guest',403),
 ('Untrusted hostname denied',{'photoUrl':avatar('avatar_guest').replace('res.cloudinary.com','example.com')},'avatar_guest',403),
 ('Query avatar denied',{'photoUrl':avatar('avatar_guest')+'?x=1'},'avatar_guest',403),
 ('Non-string avatar denied',{'photoUrl':123},'avatar_guest',403),
 ('Avatar write cannot promote role',{'photoUrl':avatar('avatar_guest'),'role':'HOST'},'avatar_guest',403),
]:check(label,[write('users/avatar_guest',data,list(data))],actor,expected)
check('Blocked user avatar denied',[write('users/avatar_blocked',{'photoUrl':avatar('avatar_blocked')},['photoUrl'])],'avatar_blocked',403)
check('Host saves user and public avatar atomically',[
 write('users/avatar_host',{'photoUrl':avatar('avatar_host')},['photoUrl']),
 write('publicHosts/avatar_host',dict(uid='avatar_host',name='Demo',status='ACTIVE',photoUrl=avatar('avatar_host')),transforms=())],'avatar_host')
check('Host cannot forge public projection avatar',[write('publicHosts/avatar_host',dict(uid='avatar_host',name='Demo',status='ACTIVE',photoUrl=avatar('avatar_peer')),transforms=())],'avatar_host',403)

cases=[('Two-sided application accepted',{},200),('Missing front denied',{'identityImageUrls':[]},403),('Single side denied',{'identityImageUrls':['OWN_FRONT']},403),('Duplicate sides denied',{'identityImageUrls':['OWN_FRONT','OWN_FRONT']},403),('Foreign side denied',{'identityImageUrls':[identity('another'),identity('another','back')]},403),('Wrong folder denied',{'identityImageUrls':[avatar('another'),avatar('another','back')]},403),('Non-demo public mode denied',{'evidenceMode':'REAL_IDENTITY'},403),('Pre-approved application denied',{'status':'APPROVED'},403),('Forged reviewer denied',{'reviewedBy':'profile_admin'},403),('Legacy new application without images denied',{'schemaVersion':1,'identityImageUrls':[]},403)]
accepted=None
for i,(label,change,expected) in enumerate(cases):
 uid='application_'+str(time.time_ns())+'_'+str(i);profile(uid);data=application(uid)
 data.update({k:([identity(uid) if x=='OWN_FRONT' else x for x in xval] if isinstance(xval,list) else xval) for k,xval in change.items()})
 check(label,[write('hostApplications/'+uid,data,transforms=('createdAt','updatedAt'))],uid,expected)
 if expected==200:accepted=uid

for actor,expected,label in [(accepted,200,'Applicant reads own application'),('profile_admin',200,'Admin reads application'),('avatar_peer',403,'Peer cannot read application')]:
 code,_=call('/v1/'+DB+'/documents/hostApplications/'+accepted,actor=actor);results.append(dict(case=label,passed=code==expected,actual=code,expected=expected));print(label,'PASS' if code==expected else 'FAIL',flush=True)
check('Applicant cannot self-approve',[write('hostApplications/'+accepted,{'status':'APPROVED'},['status'])],accepted,403)
check('Applicant cannot replace evidence while pending',[write('hostApplications/'+accepted,{'identityImageUrls':[identity(accepted,'other'),identity(accepted,'back')]},['identityImageUrls'])],accepted,403)
check('Admin cannot promote without application',[write('users/avatar_guest',{'role':'HOST'},['role'])],'profile_admin',403)
check('Admin cannot approve without user promotion',[write('hostApplications/'+accepted,{'status':'APPROVED','reviewedBy':'profile_admin','rejectionReason':''},['status','reviewedBy','rejectionReason'],('updatedAt','identityReviewedAt'))],'profile_admin',403)
check('Admin cannot promote without approval',[write('users/'+accepted,{'role':'HOST'},['role'])],'profile_admin',403)
check('Admin approves and promotes atomically',[
 write('hostApplications/'+accepted,{'status':'APPROVED','reviewedBy':'profile_admin','rejectionReason':''},['status','reviewedBy','rejectionReason'],('updatedAt','identityReviewedAt')),
 write('users/'+accepted,{'role':'HOST'},['role']),
 write('publicHosts/'+accepted,dict(uid=accepted,name='Demo',status='ACTIVE',photoUrl=''),transforms=())],'profile_admin')
check('Approved evidence cannot be replaced',[write('hostApplications/'+accepted,{'identityImageUrls':[identity(accepted,'new'),identity(accepted,'back')]},['identityImageUrls'])],accepted,403)

uid='resubmit_user';profile(uid);seed('hostApplications/'+uid,application(uid))
check('Whitespace rejection denied',[write('hostApplications/'+uid,dict(status='REJECTED',reviewedBy='profile_admin',rejectionReason='  '),['status','reviewedBy','rejectionReason'])],'profile_admin',403)
check('Reject with reason accepted',[write('hostApplications/'+uid,dict(status='REJECTED',reviewedBy='profile_admin',rejectionReason='Demo image not legible'),['status','reviewedBy','rejectionReason'])],'profile_admin')
check('Rejected applicant resubmits new pair',[write('hostApplications/'+uid,application(uid),transforms=('createdAt','updatedAt'))],uid)

(OUT/'rules-acceptance.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
(OUT/'rules-source.sha256').write_text(hashlib.sha256(source.encode()).hexdigest(),encoding='ascii')
assert all(r['passed'] for r in results),'Profile/identity Rules cases failed'
print('PASS',len(results),'Rules cases')
