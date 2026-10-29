"""Local emulator only: narrow a transaction failure before re-running Android SDK tests."""
import base64, datetime as dt, hashlib, json, urllib.request, urllib.error, pathlib, sys, argparse
parser=argparse.ArgumentParser();parser.add_argument('--months',type=int,default=0);parser.add_argument('--hours',type=int,default=1);parser.add_argument('--dates',type=int,default=0);parser.add_argument('--ranges',type=int,default=0);parser.add_argument('--blocked',choices=['none','date','legacy','range'],default='none');parser.add_argument('--forged-slots',action='store_true');parser.add_argument('--omit-room',action='store_true');parser.add_argument('--omit-audit',action='store_true');parser.add_argument('--tamper-price',action='store_true');parser.add_argument('--block-month-offset',type=int,default=0);parser.add_argument('--paid-amount',type=int,default=0);opts=parser.parse_args()
BASE='http://127.0.0.1:8085'
DB='projects/broom-sharing-firebase-mobile/databases/broomsharing'
def call(path,body=None,token='owner',method=None):
    try:
        r=urllib.request.urlopen(urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),method=method,headers={'Content-Type':'application/json','Authorization':'Bearer '+token}),timeout=30)
        return json.load(r)
    except urllib.error.HTTPError as e:
        raise RuntimeError(e.read().decode())
def decode(v):
    k=next(iter(v));x=v[k]
    if k=='mapValue':return {a:decode(b) for a,b in x.get('fields',{}).items()}
    if k=='arrayValue':return [decode(i) for i in x.get('values',[])]
    if k=='integerValue':return int(x)
    if k=='nullValue':return None
    return x
def encode(v):
    if v is None:return {'nullValue':None}
    if isinstance(v,bool):return {'booleanValue':v}
    if isinstance(v,int):return {'integerValue':str(v)}
    if isinstance(v,float):return {'doubleValue':v}
    if isinstance(v,dt.datetime):return {'timestampValue':v.isoformat().replace('+00:00','Z')}
    if isinstance(v,dict):return {'mapValue':{'fields':{k:encode(x) for k,x in v.items()}}}
    if isinstance(v,list):return {'arrayValue':{'values':[encode(x) for x in v]}}
    return {'stringValue':v}
def docs(c):return [{**{k:decode(v) for k,v in d['fields'].items()},'_name':d['name']} for d in call('/v1/'+DB+'/documents/'+c+'?pageSize=1000').get('documents',[])]
u={'uid':'qa_rules_guest','name':'Rules Guest','phone':'+84900000000','role':'GUEST','status':'ACTIVE','schemaVersion':1}
host={'uid':'qa_rules_host','name':'Rules Host','phone':'+84900000001','role':'HOST','status':'ACTIVE','schemaVersion':1}
for user in [u,host]:
    call('/v1/'+DB+'/documents/users/'+user['uid'],{'fields':{k:encode(v) for k,v in user.items()}},method='PATCH')
call('/v1/'+DB+'/documents/publicHosts/'+host['uid'],{'fields':{k:encode(v) for k,v in {'uid':host['uid'],'name':host['name'],'status':'ACTIVE'}.items()}},method='PATCH')
call('/v1/'+DB+'/documents/appConfig/payments',{'fields':{k:encode(v) for k,v in {'flow':'INSTANT_5M','demoHostUid':host['uid']}.items()}},method='PATCH')
call('/v1/'+DB+'/documents/buildings/probe_building',{'fields':{k:encode(v) for k,v in {'ownerUid':host['uid'],'status':'ACTIVE'}.items()}},method='PATCH')
r=dict(id='probe_room',schemaVersion=1,buildingId='probe_building',ownerUid=host['uid'],roomName='Rules acceptance',address='QA fixture',approvalStatus='APPROVED',listingStatus='ACTIVE',activeMode='HOURLY',pricePerHour=100000.0,pricePerMonth=3000000.0,pricePerQuarter=8000000.0,rentalPolicy='',renewalPolicy='',slotMinutes=30,minBookingMinutes=30,workingDays=[1,2,3,4,5,6,7])
def b64(x):return base64.urlsafe_b64encode(json.dumps(x).encode()).decode().rstrip('=')
token=b64({'alg':'none','typ':'JWT'})+'.'+b64({'sub':u['uid'],'user_id':u['uid'],'iat':int(dt.datetime.now().timestamp()),'exp':int(dt.datetime.now().timestamp())+3600,'aud':'broom-sharing-firebase-mobile','iss':'https://securetoken.google.com/broom-sharing-firebase-mobile','firebase':{'sign_in_provider':'password'}})+'.'
day=dt.datetime.now(dt.timezone(dt.timedelta(hours=7))).date()+dt.timedelta(days=3)
zone=dt.timezone(dt.timedelta(hours=7));mode='FIXED' if opts.months else 'HOURLY'
if opts.months:day=(day.replace(day=1)+dt.timedelta(days=32)).replace(day=1)
hour=0 if opts.months or opts.hours==24 else 8
start=dt.datetime.combine(day,dt.time(hour),zone).astimezone(dt.timezone.utc)
monthnum=day.year*12+day.month-1
if opts.months:
    n=monthnum+opts.months;end=dt.datetime(n//12,n%12+1,1,tzinfo=zone).astimezone(dt.timezone.utc)
else:end=start+dt.timedelta(hours=opts.hours)
r['id']='probe_'+mode;r.pop('_name',None);r['activeMode']=mode;r['openTime']='00:00';r['closeTime']='24:00';r['activeBookingCount']=0;r['bookingRevision']=0;r['lastBookingId']='';r['fixedStartMonth']=day.strftime('%Y-%m')
r['blackoutDates']=[(end.astimezone(zone).date()+dt.timedelta(days=i+1)).isoformat() for i in range(opts.dates)]
r['blackoutRanges']=[{'startAt':end+dt.timedelta(days=i+1),'endAt':end+dt.timedelta(days=i+1,hours=1),'reason':'QA'} for i in range(opts.ranges)]
monthnum=day.year*12+day.month-1
if opts.blocked in ('date','legacy'):r['blackoutDates'][-1:]=[((dt.date((monthnum+opts.block_month_offset)//12,(monthnum+opts.block_month_offset)%12+1,2)) if opts.months else day).strftime('%d/%m/%Y' if opts.blocked=='legacy' else '%Y-%m-%d')]
if opts.blocked=='range':r['blackoutRanges'][-1:]=[{'startAt':end-dt.timedelta(minutes=30),'endAt':end+dt.timedelta(minutes=30),'reason':'QA'}]
call('/v1/'+DB+'/documents/rooms/'+r['id'],{'fields':{k:encode(v) for k,v in r.items()}},method='PATCH')
id=hashlib.sha256((str(start)+r['id']+'probe').encode()).hexdigest();keys=[r['id']+'_'+str((monthnum+i)//12*100+(monthnum+i)%12+1) for i in range(opts.months or 1)];key=keys[0];slot=0 if opts.months else (day.day-1)*48+hour*2;slots=[] if opts.months else list(range(slot,slot+opts.hours*2))
if opts.forged_slots:slots[-1]+=1
price=r['pricePerMonth'] if opts.months==1 else r['pricePerQuarter'] if opts.months else r['pricePerHour']*opts.hours;advance=int(price*.4+.5)
b=dict(id=id,schemaVersion=2,roomId=r['id'],roomName=r['roomName'],roomAddress=r['address'],ownerUid=r['ownerUid'],guestUid=u['uid'],guestName=u['name'],guestPhone=u['phone'],status='PENDING',paymentStatus='UNAVAILABLE',activeMode='HOURLY',rejectionReason='',startAt=start,endAt=end,totalAmount=price,priceSnapshot=dict(currency='VND',rentalAmount=price,feeAmount=0,depositAmount=advance,advancePercent=40,cancellationPolicyVersion=1,totalAmount=price,paymentDueAmount=advance,rentalPolicy=r['rentalPolicy'],renewalPolicy=r['renewalPolicy']),rentalMonths=0,slotStart=slot,slotCount=2,monthKeys=[key],slotKeys=slots,requestFingerprint='a'*64,cancelledAt=None,cancelledByUid='',cancellationRefundPercent=0,refundAmount=0,refundStatus='NOT_REQUIRED')
b.update(activeMode=mode,rentalMonths=opts.months,slotCount=len(slots),monthKeys=keys)
b['paidAmount']=opts.paid_amount
b['paymentFlow']='INSTANT_5M'
if opts.tamper_price: b['totalAmount']=1;b['priceSnapshot']['rentalAmount']=1;b['priceSnapshot']['totalAmount']=1
def write(path,data,transforms=(),mask=None):
    w={'update':{'name':DB+'/documents/'+path,'fields':{k:encode(v) for k,v in data.items()}}}
    w['currentDocument']={'exists':mask is not None}
    if transforms:w['updateTransforms']=[{'fieldPath':k,'setToServerValue':'REQUEST_TIME'} for k in transforms]
    if mask is not None:w['updateMask']={'fieldPaths':mask}
    return w
# Reset this probe's fixtures with the emulator owner, leaving unrelated QA bookings untouched.
for path in ['bookings/'+id]+['availabilityMonths/'+k for k in keys]+['auditLogs/booking_'+id+'_CREATE']:
    try:call('/v1/'+DB+'/documents/'+path,method='DELETE')
    except RuntimeError:pass
roomchanges=dict(activeBookingCount=max(-1,r.get('activeBookingCount',-1)+1),bookingRevision=r.get('bookingRevision',0)+1,lastBookingId=id)
m=dict(roomId=r['id'],monthIndex=int(day.strftime('%Y%m')),fixedBookingId=id if opts.months else '',slotsBusy=slots,hourlyBookings={} if opts.months else {id:slots},lastBookingId=id)
m['pendingPaymentIds']=[id]
a=dict(schemaVersion=1,actorUid=u['uid'],targetPath='bookings/'+id,action='CREATE',before={'status':''},after={'status':'PENDING'},reason='')
w=[write('availabilityMonths/'+k,{**m,'monthIndex':int(k.rsplit('_',1)[1])},['updatedAt']) for k in keys]+[write('bookings/'+id,b,['createdAt','updatedAt']),write('rooms/'+r['id'],roomchanges,mask=list(roomchanges)),write('auditLogs/booking_'+id+'_CREATE',a,['createdAt'])]
if opts.omit_room:w=[x for x in w if '/rooms/' not in x['update']['name']]
if opts.omit_audit:w=[x for x in w if '/auditLogs/' not in x['update']['name']]
try:
    print(call('/v1/'+DB+'/documents:commit',{'writes':w},token))
except RuntimeError as e:
    print(str(e)[:2500]);sys.exit(1)
