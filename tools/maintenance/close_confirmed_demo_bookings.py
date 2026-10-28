"""Close only the three unused DEMO stays explicitly confirmed by the user.

Uses Firebase Auth and deployed Rules, never Admin SDK bypass. Default is read-only.
NO_SHOW is the existing domain state for an ended CONFIRMED stay without use.
Preserves money, rental dates, snapshots, reviews and unrelated calendar owners.
"""
from pathlib import Path
import argparse
import copy
import datetime as dt
import importlib.util
import json

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/verification/final-qa-2026-10-28'
spec = importlib.util.spec_from_file_location('counter', ROOT / 'tools/maintenance/reconcile_room_counters.py')
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)

TARGETS = {
    '5132e2aae3cf6ca7eff29365eed4df5a1d38a7c26d13ca7c8dbfd4d674ae69af': 'dataset_hcm_v1_room_02',
    'ce0436c3dce49ddd81a4998bcc324335a3d85720ee49305b70c4ff1eb8d93fc4': 'dataset_hcm_v1_room_05',
    '78cb225b84f342b8d0eb2b6dc88547bd6e48071c24e2366f907a257074419fa7': 'payos_demo_20261026_room',
}
REVIEW = 'e38f82e850d7e81d996ea1aa3be4f8d62d896e6af8d9cac5acdc1f2c20b6f286'
REASON = 'Kết thúc thử nghiệm DEMO: người phụ trách xác nhận không sử dụng phòng thực tế; nhả lịch, giữ lịch sử, không ghi nhận thanh toán hay bằng chứng sử dụng.'


def save(name, value):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def operator(admin, owner):
    """Choose the real Host session, or the existing Admin route for a blocked Host."""
    profile = source.fields(admin.get('users/' + owner))
    if profile.get('status') == 'BLOCKED':
        return admin
    source.require(profile.get('role') == 'HOST' and profile.get('status') == 'ACTIVE', 'Owner must be an active Host')
    wanted = profile.get('email', '').strip().lower()
    source.require(bool(wanted), 'Host email missing; cannot select an authorized session')
    pair = None
    for path in (ROOT / 'data/demo').glob('*.local.properties'):
        values = dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
        for key, value in values.items():
            if key.endswith('.email') and value.strip().lower() == wanted:
                password = values.get(key[:-6] + '.password')
                if password:
                    pair = (value.strip(), password)
    source.require(pair is not None, 'No local credential for the owning Host; no bypass attempted')
    config = json.loads((ROOT / 'app/google-services.json').read_text(encoding='utf-8'))
    app = next(c for c in config['client'] if c['client_info']['android_client_info']['package_name'] == 'vn.edu.ueh.phuclu.broomsharing')
    result = admin.http('https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=' + app['api_key'][0]['current_key'],
                        {'email': pair[0], 'password': pair[1], 'returnSecureToken': True})
    source.require(result['localId'] == owner, 'Auth UID does not match the owning Host')
    client = copy.copy(admin)
    client.actor, client.token = owner, result['idToken']
    return client


def update(client, path, raw, values, times):
    return {'update': {'name': client.db + '/documents/' + path,
                       'fields': {k: source.encode(v) for k, v in values.items()}},
            'updateMask': {'fieldPaths': list(values)},
            'currentDocument': {'updateTime': raw['updateTime']},
            'updateTransforms': [{'fieldPath': k, 'setToServerValue': 'REQUEST_TIME'} for k in times]}


def prepare(admin, snapshot, identity, expected_room):
    raw = snapshot['bookings'][identity]
    b = source.fields(raw)
    source.require(b['roomId'] == expected_room and b['id'] == identity, 'Target identity/room changed')
    source.require(b.get('usageConfirmedAt') is None and not b.get('usageConfirmedBy'), 'Usage evidence exists; stop for review')
    source.require(b.get('paidAmount') in (None, 0) and b.get('paymentStatus') in ('DEMO_PENDING', 'UNPAID'), 'Payment changed; stop for review')
    source.require(dt.datetime.fromisoformat(b['endAt'].replace('Z', '+00:00')) <= dt.datetime.now(dt.timezone.utc), 'Stay has not ended')
    if b['status'] == 'NO_SHOW':
        source.require(b.get('rejectionReason') == REASON and b.get('closedAt'), 'Unrecognized previous closure')
        return {'bookingId': identity, 'alreadyClosed': True, 'writes': [], 'operator': None}
    source.require(b['status'] == 'CONFIRMED' and b.get('schemaVersion') == 2, 'Expected an unchanged confirmed legacy booking')
    room_raw = snapshot['rooms'][expected_room]
    room = source.fields(room_raw)
    source.require(room['ownerUid'] == b['ownerUid'], 'Owner mismatch')
    source.require(type(room.get('activeBookingCount')) is int and room['activeBookingCount'] > 0, 'Counter not reconciled')
    client = operator(admin, b['ownerUid'])
    writes = [update(client, 'bookings/' + identity, raw,
                     {'status': 'NO_SHOW', 'rejectionReason': REASON}, ['updatedAt', 'closedAt'])]
    source.require(len(b['monthKeys']) == len(set(b['monthKeys'])) and bool(b['monthKeys']), 'Invalid month keys')
    for key in b['monthKeys']:
        m_raw = snapshot['availabilityMonths'][key]
        m = source.fields(m_raw)
        source.require(m['roomId'] == expected_room, 'Wrong calendar room')
        hourly = copy.deepcopy(m['hourlyBookings'])
        busy, fixed = list(m['slotsBusy']), m['fixedBookingId']
        if b['activeMode'] == 'FIXED':
            source.require(fixed == identity, 'Fixed lock lost')
            fixed = ''
        else:
            source.require(b['activeMode'] == 'HOURLY' and hourly.get(identity) == b['slotKeys'], 'Hourly lock lost')
            source.require(set(b['slotKeys']) <= set(busy), 'Busy slots lost')
            del hourly[identity]
            busy = [slot for slot in busy if slot not in b['slotKeys']]
        values = {'fixedBookingId': fixed, 'hourlyBookings': hourly, 'slotsBusy': busy,
                  'pendingPaymentIds': [x for x in m.get('pendingPaymentIds', []) if x != identity], 'lastBookingId': identity}
        writes.append(update(client, 'availabilityMonths/' + key, m_raw, values, ['updatedAt']))
    writes.append(update(client, 'rooms/' + expected_room, room_raw,
                         {'activeBookingCount': room['activeBookingCount'] - 1,
                          'bookingRevision': room.get('bookingRevision', 0) + 1, 'lastBookingId': identity}, []))
    audit_id = 'booking_' + identity + '_NO_SHOW'
    audit = {'schemaVersion': 1, 'actorUid': client.actor, 'targetPath': 'bookings/' + identity,
             'action': 'NO_SHOW', 'before': {'status': 'CONFIRMED'}, 'after': {'status': 'NO_SHOW'}, 'reason': REASON}
    writes.append({'update': {'name': client.db + '/documents/auditLogs/' + audit_id,
                             'fields': {k: source.encode(v) for k, v in audit.items()}},
                   'currentDocument': {'exists': False},
                   'updateTransforms': [{'fieldPath': 'createdAt', 'setToServerValue': 'REQUEST_TIME'}]})
    return {'bookingId': identity, 'alreadyClosed': False, 'operator': client,
            'writes': writes, 'auditId': audit_id, 'counterBefore': room['activeBookingCount']}


def verify(before, after, admin):
    source.inventory(after)
    source.require(set(before['bookings']) == set(after['bookings']), 'Booking set changed')
    source.require(set(before['rooms']) == set(after['rooms']), 'Room set changed')
    source.require(set(before['availabilityMonths']) == set(after['availabilityMonths']), 'Calendar set changed')
    touched_months = set()
    rows = []
    for identity, room_id in TARGETS.items():
        old, new = source.fields(before['bookings'][identity]), source.fields(after['bookings'][identity])
        allowed = {'status', 'rejectionReason', 'closedAt', 'updatedAt'}
        source.require({k: v for k, v in old.items() if k not in allowed} == {k: v for k, v in new.items() if k not in allowed}, 'Unrelated booking field changed')
        source.require(new['status'] == 'NO_SHOW' and new['rejectionReason'] == REASON and new.get('closedAt'), 'Closure readback failed')
        for key in old['monthKeys']:
            touched_months.add(key)
            m, n = source.fields(before['availabilityMonths'][key]), source.fields(after['availabilityMonths'][key])
            permitted = {'hourlyBookings', 'slotsBusy', 'fixedBookingId', 'pendingPaymentIds', 'lastBookingId', 'updatedAt'}
            source.require({k: v for k, v in m.items() if k not in permitted} == {k: v for k, v in n.items() if k not in permitted}, 'Unrelated calendar field changed')
            source.require({k: v for k, v in m['hourlyBookings'].items() if k != identity} == n['hourlyBookings'], 'Other hourly owner changed')
            source.require(identity not in n['hourlyBookings'] and identity not in n.get('pendingPaymentIds', []) and n['fixedBookingId'] != identity, 'Target lock remains')
        r, n = source.fields(before['rooms'][room_id]), source.fields(after['rooms'][room_id])
        permitted = {'activeBookingCount', 'bookingRevision', 'lastBookingId'}
        source.require({k: v for k, v in r.items() if k not in permitted} == {k: v for k, v in n.items() if k not in permitted}, 'Room fields changed')
        source.require(n['activeBookingCount'] == r['activeBookingCount'] - (old['status'] != 'NO_SHOW'), 'Counter decremented incorrectly')
        audit = source.fields(admin.get('auditLogs/booking_' + identity + '_NO_SHOW'))
        source.require(audit['reason'] == REASON and audit['after']['status'] == 'NO_SHOW', 'Audit missing')
        rows.append({'bookingId': identity, 'status': new['status'], 'closedAt': new['closedAt'],
                     'usageProofAbsent': not new.get('usageConfirmedAt'), 'moneyUnchanged': True,
                     'ownLocksReleased': True, 'roomCounter': n['activeBookingCount'], 'auditVerified': True})
    for collection, targets in [('bookings', set(TARGETS)), ('rooms', set(TARGETS.values())), ('availabilityMonths', touched_months)]:
        for identity in set(before[collection]) - targets:
            source.require(before[collection][identity] == after[collection][identity], 'Unrelated document changed')
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    admin = source.Firebase()
    before = admin.snapshot()
    source.inventory(before)
    review_before = admin.get('reviews/' + REVIEW)
    plans = [prepare(admin, before, identity, room) for identity, room in TARGETS.items()]
    if not args.apply:
        result = {'readOnly': True, 'documentWrites': 0, 'state': 'NO_SHOW', 'reason': REASON,
                  'rows': [{k: p[k] for k in ('bookingId', 'alreadyClosed')} | {'writeCount': len(p['writes'])} for p in plans]}
        save('demo-closure-plan.json', result)
        print(json.dumps(result, ensure_ascii=True))
        return
    # Snapshot saved before the first write; preconditions make any concurrent change fail closed.
    backup = {'bookings': {i: before['bookings'][i] for i in TARGETS},
              'rooms': {i: before['rooms'][i] for i in TARGETS.values()},
              'availabilityMonths': {k: before['availabilityMonths'][k] for i in TARGETS for k in source.fields(before['bookings'][i])['monthKeys']},
              'review': review_before}
    backup_name = dt.datetime.now(dt.timezone.utc).strftime('demo-closure-before-%Y%m%dT%H%M%SZ.json')
    save(backup_name, backup)
    progress = []
    for p in plans:
        if p['writes']:
            p['operator'].call(':commit', {'writes': p['writes']})
        progress.append({'bookingId': p['bookingId'], 'alreadyClosed': p['alreadyClosed'], 'writeCount': len(p['writes'])})
        save('demo-closure-progress.json', progress)
    after = admin.snapshot()
    rows = verify(before, after, admin)
    source.require(admin.get('reviews/' + REVIEW) == review_before, 'Historical review changed')
    result = {'passed': True, 'checkedAt': dt.datetime.now(dt.timezone.utc).isoformat(),
              'database': source.DATABASE, 'project': source.PROJECT, 'reason': REASON,
              'documentWrites': sum(p['writeCount'] for p in progress), 'rows': rows,
              'historicalReviewUnchanged': True, 'otherDocumentsUnchanged': True,
              'usageProofFabricated': False, 'backup': backup_name, 'deployedRulesEnforced': True}
    save('demo-closure-receipt.json', result)
    print(json.dumps(result, ensure_ascii=True))


if __name__ == '__main__':
    main()
