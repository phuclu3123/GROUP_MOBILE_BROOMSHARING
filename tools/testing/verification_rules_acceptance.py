"""Local-only Rules checks for the integrated Host verification contract."""
import base64, datetime, json, pathlib, time, urllib.request, urllib.error

ROOT = pathlib.Path(__file__).resolve().parents[2]
DB = 'projects/broom-sharing-firebase-mobile/databases/broomsharing'
BASE = 'http://127.0.0.1:8085'
OUTPUT = ROOT / 'output/verification/member-integration-2026-10-25'
OUTPUT.mkdir(parents=True, exist_ok=True)

def call(path, body=None, token='owner', method=None):
    try:
        with urllib.request.urlopen(urllib.request.Request(BASE+path, data=None if body is None else json.dumps(body).encode(),
                method=method, headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'}), timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())

def value(v):
    if isinstance(v,list): return {'arrayValue':{'values':[value(x) for x in v]}}
    if isinstance(v,dict): return {'mapValue':{'fields':{k:value(x) for k,x in v.items()}}}
    if isinstance(v,int): return {'integerValue':str(v)}
    return {'stringValue':v}

def jwt(uid):
    def part(v): return base64.urlsafe_b64encode(json.dumps(v).encode()).decode().rstrip('=')
    return part({'alg':'none','typ':'JWT'})+'.'+part({'sub':uid,'user_id':uid,'aud':'broom-sharing-firebase-mobile',
        'iss':'https://securetoken.google.com/broom-sharing-firebase-mobile','iat':int(time.time()),'exp':int(time.time())+3600,
        'firebase':{'sign_in_provider':'password'}})+'.'

rule_body = {'rules':{'files':[{'name':'firestore.rules','content':(ROOT/'firestore.rules').read_text(encoding='utf-8')}]}}
assert call('/emulator/v1/'+DB+':securityRules',rule_body,method='PUT')[0]==200
for path, fields in {
    'users/qa_meta_host':{'role':'HOST','status':'ACTIVE'},
    'users/qa_meta_guest':{'role':'GUEST','status':'ACTIVE'},
    'rooms/qa_meta_room':{'ownerUid':'qa_meta_host','buildingId':'qa_meta_building'},
    'rooms/qa_meta_foreign':{'ownerUid':'someone_else','buildingId':'qa_meta_foreign_building'},
    'buildings/qa_meta_building':{'ownerUid':'qa_meta_host'},
}.items():
    assert call('/v1/'+DB+'/documents/'+path, {'fields':{k:value(v) for k,v in fields.items()}},method='PATCH')[0]==200

results=[]
prefix='meta_'+str(int(time.time()*1000))
def check(label, changes=None, expected=200, actor='qa_meta_host'):
    identity=prefix+'_'+str(len(results))
    fields=dict(id=identity,schemaVersion=1,ownerUid=actor,roomId='qa_meta_room',buildingId='qa_meta_building',
                roomName='QA room',buildingName='QA building',verificationType='LOCATION',description='A valid verification description for Rules tests.',evidenceRefs=[],status='PENDING')
    fields.update(changes or {})
    write={'update':{'name':DB+'/documents/propertyVerifications/'+identity,'fields':{k:value(v) for k,v in fields.items()}},
           'currentDocument':{'exists':False},'updateTransforms':[{'fieldPath':k,'setToServerValue':'REQUEST_TIME'} for k in ['createdAt','updatedAt']]}
    code, detail=call('/v1/'+DB+'/documents:commit', {'writes':[write]},jwt(actor),'POST')
    passed=code==expected
    results.append({'case':label,'expected':expected,'actual':code,'passed':passed,
                    'errorStatus':detail.get('error',{}).get('status')})
    print(label,'PASS' if passed else 'FAIL',flush=True)
    return identity

own=check('Host own room')
check('Host own building',{'roomId':''})
check('Identity metadata',{'verificationType':'IDENTITY','roomId':'','buildingId':''})
check('Guest cannot submit',expected=403,actor='qa_meta_guest')
check('Foreign room denied',{'roomId':'qa_meta_foreign','buildingId':'qa_meta_foreign_building'},403)
check('Missing room denied',{'roomId':'missing_room'},403)
check('Client verified denied',{'status':'VERIFIED'},403)
check('Short description denied',{'description':'short'},403)
check('Injected reviewer denied',{'reviewedBy':'admin'},403)
check('Legacy field denied',{'evidencePaths':[]},403)
check('Non-string evidence denied',{'evidenceRefs':[{'file':'x'}]},403)
check('Other owner evidence denied',{'evidenceRefs':['verification/someone_else/x.jpg']},403)
for label, actor, expected in [('Owner reads own','qa_meta_host',200),('Guest cannot read Host verification','qa_meta_guest',403)]:
    code,_=call('/v1/'+DB+'/documents/propertyVerifications/'+own,token=jwt(actor))
    results.append({'case':label,'expected':expected,'actual':code,'passed':code==expected})
    print(label,'PASS' if code==expected else 'FAIL',flush=True)
(OUTPUT/'verification-rules.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
raise SystemExit(0 if all(r['passed'] for r in results) else 1)
