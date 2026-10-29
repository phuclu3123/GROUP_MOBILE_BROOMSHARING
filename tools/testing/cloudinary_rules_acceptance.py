"""Rules-only Cloudinary reference checks. Isolated local emulator database, never cloud."""
import base64
import hashlib
import json
import pathlib
import time
import urllib.error
import urllib.request

ROOT=pathlib.Path(__file__).resolve().parents[2]
BASE='http://127.0.0.1:8085'
DB='projects/broom-sharing-firebase-mobile/databases/media_rules_qa'
OUT=ROOT/'output/verification/cloudinary-2026-10-26'
OUT.mkdir(parents=True,exist_ok=True)
results=[]

def jwt(uid):
    def part(v):return base64.urlsafe_b64encode(json.dumps(v).encode()).decode().rstrip('=')
    return part({'alg':'none','typ':'JWT'})+'.'+part({'sub':uid,'user_id':uid,'aud':'broom-sharing-firebase-mobile',
        'iss':'https://securetoken.google.com/broom-sharing-firebase-mobile','iat':int(time.time()),'exp':int(time.time())+3600,
        'firebase':{'sign_in_provider':'password'}})+'.'

def call(path,body=None,actor=None,method=None):
    request=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),method=method,
        headers={'Authorization':'Bearer '+('owner' if actor is None else jwt(actor)),'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(request,timeout=30) as response:return response.status,json.load(response)
    except urllib.error.HTTPError as error:return error.code,json.loads(error.read())

def value(v):
    if isinstance(v,list):return {'arrayValue':{'values':[value(x) for x in v]}}
    if isinstance(v,dict):return {'mapValue':{'fields':{k:value(x) for k,x in v.items()}}}
    if isinstance(v,int):return {'integerValue':str(v)}
    return {'stringValue':v}

def seed(path,fields):
    code,body=call('/v1/'+DB+'/documents/'+path,{'fields':{k:value(v) for k,v in fields.items()}},method='PATCH')
    assert code==200,body

source=(ROOT/'firestore.rules').read_text(encoding='utf-8')
code,body=call('/emulator/v1/'+DB+':securityRules',{'rules':{'files':[{'name':'firestore.rules','content':source}]}},method='PUT')
assert code==200 and not any(i.get('severity')=='ERROR' for i in body.get('issues',[])),body
for uid,role,status in [('media_host','HOST','ACTIVE'),('media_guest','GUEST','ACTIVE'),('media_other','HOST','ACTIVE'),('media_admin','ADMIN','ACTIVE'),('media_blocked','HOST','BLOCKED'),('media.host','HOST','ACTIVE')]:
    seed('users/'+uid,dict(uid=uid,role=role,status=status))
seed('rooms/media_room',dict(ownerUid='media_host',buildingId='media_building'))
seed('rooms/media_foreign',dict(ownerUid='media_other',buildingId='media_other_building'))
seed('buildings/media_building',dict(ownerUid='media_host'))
for actor in ['media_guest','media_blocked','media.host']:
    seed('rooms/'+actor+'_room',dict(ownerUid=actor,buildingId=actor+'_building'))
    seed('buildings/'+actor+'_building',dict(ownerUid=actor))

def url(identity,owner='media_host',resource='image',extension='jpg'):
    return 'https://res.cloudinary.com/demo_test_cloud/'+resource+'/upload/v123/broomsharing/verification/'+owner+'/'+identity+'/file.'+extension

def check(label,change=None,expected=200,actor='media_host',doc_id=None):
    identity=doc_id or 'media_'+hashlib.sha256((label+str(time.time_ns())).encode()).hexdigest()[:24]
    fields=dict(id=identity,schemaVersion=1,ownerUid=actor,roomId='media_room',buildingId='media_building',roomName='Demo room',buildingName='Demo building',
        verificationType='LOCATION',description='Demo verification content, no personal documents.',evidenceRefs=[url(identity)],status='PENDING')
    fields.update(change(identity) if callable(change) else change or {})
    write={'update':{'name':DB+'/documents/propertyVerifications/'+identity,'fields':{k:value(v) for k,v in fields.items()}},
        'currentDocument':{'exists':False},'updateTransforms':[{'fieldPath':p,'setToServerValue':'REQUEST_TIME'} for p in ['createdAt','updatedAt']]}
    code,body=call('/v1/'+DB+'/documents:commit',{'writes':[write]},actor,'POST')
    passed=code==expected
    results.append(dict(case=label,passed=passed,expected=expected,actual=code,error=body.get('error',{}).get('status')))
    print(label,'PASS' if passed else 'FAIL',flush=True)
    return identity

own=check('LOCATION accepts matching Cloudinary image')
check('PHOTOS accepts matching Cloudinary PDF',lambda i:dict(verificationType='PHOTOS',evidenceRefs=[url(i,resource='raw',extension='pdf')]))
check('Own building accepts Cloudinary evidence',dict(roomId=''))
check('Legacy Firebase Storage path remains valid',dict(evidenceRefs=['verification/media_host/demo.jpg']))
check('Empty metadata-only application remains valid',dict(evidenceRefs=[]))
check('Six Cloudinary references accepted',lambda i:dict(evidenceRefs=[url(i).replace('/file.','/file'+str(n)+'.') for n in range(6)]))
check('Guest cannot submit',lambda i:dict(roomId='media_guest_room',buildingId='media_guest_building',evidenceRefs=[url(i,'media_guest')]),403,actor='media_guest')
check('Blocked Host cannot submit',lambda i:dict(roomId='media_blocked_room',buildingId='media_blocked_building',evidenceRefs=[url(i,'media_blocked')]),403,actor='media_blocked')
check('Unsafe document ID cannot inject regex',expected=403,doc_id='media.regex.*')
check('Unsafe UID cannot inject regex',lambda i:dict(roomId='media.host_room',buildingId='media.host_building',evidenceRefs=[url(i,'media.host')]),403,actor='media.host')
check('Foreign UID reference rejected',lambda i:dict(evidenceRefs=[url(i,'media_other')]),403)
check('Foreign verification ID rejected',dict(evidenceRefs=[url('another_case')]),403)
check('Foreign room rejected',dict(roomId='media_foreign',buildingId='media_other_building'),403)
check('Missing room rejected',dict(roomId='not_found'),403)
check('IDENTITY Cloudinary URL rejected',lambda i:dict(verificationType='IDENTITY',roomId='',buildingId='',evidenceRefs=[url(i)]),403)
check('Legacy IDENTITY metadata remains valid',dict(verificationType='IDENTITY',roomId='',buildingId='',evidenceRefs=[]))
check('Plain HTTP rejected',lambda i:dict(evidenceRefs=[url(i).replace('https:','http:')]),403)
check('Foreign domain rejected',lambda i:dict(evidenceRefs=[url(i).replace('res.cloudinary.com','other.example')]),403)
check('Cloudinary hostname suffix attack rejected',lambda i:dict(evidenceRefs=[url(i).replace('res.cloudinary.com','res.cloudinary.com.evil.example')]),403)
check('Query string rejected',lambda i:dict(evidenceRefs=[url(i)+'?x=y']),403)
check('Fragment rejected',lambda i:dict(evidenceRefs=[url(i)+'#file']),403)
check('Encoded traversal rejected',lambda i:dict(evidenceRefs=[url(i).replace('/file.','/%2e%2e%2ffile.')]),403)
check('Wrong media folder rejected',lambda i:dict(evidenceRefs=[url(i).replace('/verification/','/roomMedia/')]),403)
check('Unsupported extension rejected',lambda i:dict(evidenceRefs=[url(i).replace('.jpg','.html')]),403)
check('Non-string reference rejected',dict(evidenceRefs=[{'file':'not-a-string'}]),403)
check('Forged approval rejected',dict(status='VERIFIED'),403)
check('Injected reviewer rejected',dict(reviewedBy='media_admin'),403)
check('Over ten references rejected',lambda i:dict(evidenceRefs=[url(i)]*11),403)
check('Foreign reference at final position rejected',lambda i:dict(evidenceRefs=[url(i)]*9+[url(i,'media_other')]),403)

for label,actor,expected in [('Owner reads own document','media_host',200),('Admin reads document','media_admin',200),('Guest cannot read document','media_guest',403),('Peer Host cannot read document','media_other',403)]:
    code,_=call('/v1/'+DB+'/documents/propertyVerifications/'+own,actor=actor)
    results.append(dict(case=label,passed=code==expected,expected=expected,actual=code));print(label,'PASS' if code==expected else 'FAIL',flush=True)
write={'update':{'name':DB+'/documents/propertyVerifications/'+own,'fields':{'status':value('VERIFIED')}},'updateMask':{'fieldPaths':['status']}}
code,_=call('/v1/'+DB+'/documents:commit',{'writes':[write]},'media_host','POST')
results.append(dict(case='Host cannot self-approve',passed=code==403,expected=403,actual=code))
print('Host cannot self-approve','PASS' if code==403 else 'FAIL',flush=True)
(OUT/'rules-acceptance.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
assert all(row['passed'] for row in results),'Rules acceptance failed'
(OUT/'rules-source.sha256').write_text(hashlib.sha256(source.encode()).hexdigest(),encoding='ascii')
print('PASS',len(results),'Cloudinary reference Rules cases')
