"""Instant checkout invariants on localhost emulator only. No cloud calls or bank transfer."""
import concurrent.futures
import contextlib
import copy
import hashlib
import io
import json
from pathlib import Path
import runpy
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.argv = [str(ROOT / 'tools/testing/firestore_rules_probe.py')]
with contextlib.redirect_stdout(io.StringIO()):
    p = runpy.run_path(sys.argv[0])
call, encode, write = p['call'], p['encode'], p['write']
db = p['DB']
results = []


def expect(label, function, allowed):
    try:
        function()
        accepted = True
    except RuntimeError as error:
        assert any(code in str(error) for code in ['PERMISSION_DENIED', 'ALREADY_EXISTS', 'FAILED_PRECONDITION']), str(error)
        accepted = False
    results.append(dict(case=label, passed=accepted == allowed))
    print(label, 'PASS' if accepted == allowed else 'FAIL', flush=True)


def seed(path, fields):
    return call('/v1/' + db + '/documents/' + path,
                {'fields': {k: encode(v) for k, v in fields.items()}}, method='PATCH')


def reset_calendar():
    seed('rooms/' + p['r']['id'], p['r'])
    for key in p['keys']:
        try:
            call('/v1/' + db + '/documents/availabilityMonths/' + key, method='DELETE')
        except RuntimeError:
            pass


def creation(label, edit=None):
    identity = hashlib.sha256(label.encode()).hexdigest()
    payload = json.loads(json.dumps(p['w']).replace(p['id'], identity))
    if edit:
        edit(payload)
    return identity, payload


def commit(writes, token=None):
    return call('/v1/' + db + '/documents:commit', {'writes': writes}, token or p['token'])


reset_calendar()
_, payload = creation('instant valid hold')
expect('Supported Host can create temporary hold', lambda: commit(payload), True)
expect('Client cannot modify payment channel', lambda: commit([
    write('appConfig/payments', {'flow': 'INSTANT_5M', 'demoHostUid': 'attacker'}, mask=['flow', 'demoHostUid'])]), False)

for label, change in [
    ('Legacy create cannot bypass payment flow', lambda w: [x['update']['fields'].pop('paymentFlow', None)
        for x in w if '/bookings/' in x['update']['name']]),
    ('Client cannot fake instant confirmation', lambda w: [x['update']['fields'].update(status=encode('CONFIRMED'))
        for x in w if '/bookings/' in x['update']['name']]),
    ('Calendar must identify temporary hold', lambda w: [x['update']['fields'].update(pendingPaymentIds=encode([]))
        for x in w if '/availabilityMonths/' in x['update']['name']]),
]:
    reset_calendar()
    _, payload = creation(label, change)
    expect(label, lambda: commit(payload), False)

reset_calendar()
seed('appConfig/payments', {'flow': 'INSTANT_5M', 'demoHostUid': 'different_host'})
_, payload = creation('unsupported host')
expect('Other Host cannot create an unpayable hold', lambda: commit(payload), False)
seed('appConfig/payments', {'flow': 'INSTANT_5M', 'demoHostUid': p['host']['uid']})

# Cả hai khách cùng nhìn thấy tài liệu tháng chưa tồn tại. Chỉ một commit được sở hữu slot.
reset_calendar()
candidates = [creation('simultaneous hold ' + str(i)) for i in range(2)]
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    futures = [pool.submit(commit, payload) for _, payload in candidates]
    accepted = []
    for (identity, _), future in zip(candidates, futures):
        try:
            future.result()
            accepted.append(identity)
        except RuntimeError as error:
            assert any(code in str(error) for code in ['PERMISSION_DENIED', 'ALREADY_EXISTS', 'FAILED_PRECONDITION'])
results.append(dict(case='Concurrent reservations have exactly one winner', passed=len(accepted) == 1))
print('Concurrent reservations:', len(accepted), 'winner', flush=True)

winner = accepted[0]
import base64
import time
def part(x): return base64.urlsafe_b64encode(json.dumps(x).encode()).decode().rstrip('=')
host_token = part({'alg': 'none', 'typ': 'JWT'}) + '.' + part({'sub': p['host']['uid'], 'user_id': p['host']['uid'],
    'iat': int(time.time()), 'exp': int(time.time()) + 3600, 'aud': 'broom-sharing-firebase-mobile',
    'iss': 'https://securetoken.google.com/broom-sharing-firebase-mobile', 'firebase': {'sign_in_provider': 'password'}}) + '.'
updates = [write('bookings/' + winner, {'status': 'CONFIRMED', 'rejectionReason': ''}, ['updatedAt'], ['status','rejectionReason']),
    write('rooms/' + p['r']['id'], {'activeBookingCount': 1, 'bookingRevision': 2, 'lastBookingId': winner}, mask=['activeBookingCount','bookingRevision','lastBookingId']),
    write('auditLogs/booking_' + winner + '_CONFIRMED', {'schemaVersion':1,'actorUid':p['host']['uid'],
        'targetPath':'bookings/'+winner,'action':'CONFIRMED','before':{'status':'PENDING'},'after':{'status':'CONFIRMED'},'reason':''}, ['createdAt'])]
expect('Host cannot approve instant booking without payment backend', lambda: commit(updates, host_token), False)
expect('Guest cannot fake a paid booking', lambda: commit([
    write('bookings/' + winner, {'paymentStatus':'PAID','paidAmount':40000}, ['updatedAt'], ['paymentStatus','paidAmount'])]), False)

out = ROOT / 'output/verification/instant-payment-2026-10-27'
out.mkdir(parents=True, exist_ok=True)
(out / 'rules-acceptance.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
assert all(row['passed'] for row in results), results
print('PASS', len(results), 'instant payment Rules cases')
