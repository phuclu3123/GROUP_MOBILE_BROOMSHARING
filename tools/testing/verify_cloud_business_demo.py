"""Read back only the dedicated video cohort using its real Admin ID token."""
import collections
import argparse
import json
import pathlib

from prepare_cloud_business_demo import ROOT, PRIVATE, MANIFEST, OUTPUT, API, PROJECT, DATABASE, properties, request, fixture_admin_token


def decode(value):
    kind = next(iter(value))
    item = value[kind]
    if kind == 'integerValue':
        return int(item)
    if kind == 'mapValue':
        return {k: decode(v) for k, v in item.get('fields', {}).items()}
    if kind == 'arrayValue':
        return [decode(v) for v in item.get('values', [])]
    return item


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--normalize-historical-snapshots', action='store_true')
    args = parser.parse_args()
    private = properties(PRIVATE)
    manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
    state = json.loads(OUTPUT.joinpath('video-business-state.json').read_text(encoding='utf-8'))
    assert state['run'] == manifest['run']
    config = json.loads(ROOT.joinpath('app/google-services.json').read_text(encoding='utf-8'))
    assert config['project_info']['project_id'] == PROJECT
    key = next(c for c in config['client'] if c['client_info']['android_client_info']['package_name']
               == 'vn.edu.ueh.phuclu.broomsharing')['api_key'][0]['current_key']
    token = request('https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=' + key,
                    {'email': private['admin.email'], 'password': private['admin.password'],
                     'returnSecureToken': True}, method='POST')['idToken']

    def document(path):
        return {k: decode(v) for k, v in request(API + path, token=token)['fields'].items()}

    def query(collection, field, value):
        rows = request(API.rstrip('/') + ':runQuery', {'structuredQuery': {
            'from': [{'collectionId': collection}], 'where': {'fieldFilter': {
                'field': {'fieldPath': field}, 'op': 'EQUAL', 'value': {'stringValue': value}}}}}, token, 'POST')
        return {r['document']['name'].rsplit('/', 1)[1]: {k: decode(v) for k, v in r['document']['fields'].items()}
                for r in rows if 'document' in r}

    profiles = {}
    for actor in manifest['retainedAccounts']:
        profile = document('users/' + actor['uid'])
        assert profile['status'] == 'ACTIVE' and profile['role'] == actor['role']
        profiles[actor['uid']] = profile
    bookings = query('bookings', 'ownerUid', private['host.uid'])
    rooms = query('rooms', 'ownerUid', private['host.uid'])
    assert len(bookings) == len(state['bookings'])
    assert all(r.startswith(manifest['run']) for r in rooms)
    held_status = {'PENDING', 'CONFIRMED', 'CHECKED_IN'}
    all_calendars = {}
    for room_id, room in rooms.items():
        held = {i: b for i, b in bookings.items() if b['roomId'] == room_id and b['status'] in held_status}
        assert room['activeBookingCount'] == len(held), 'Room count differs from active bookings'
        calendars = query('availabilityMonths', 'roomId', room_id)
        all_calendars.update(calendars)
        for calendar_id, month in calendars.items():
            hourly = month.get('hourlyBookings', {})
            fixed = month.get('fixedBookingId', '')
            assert not fixed or fixed in held
            assert set(hourly) <= set(held)
            union = set()
            for booking_id, slots in hourly.items():
                assert calendar_id in held[booking_id]['monthKeys']
                assert slots == held[booking_id]['slotKeys']
                union.update(slots)
            assert set(month.get('slotsBusy', [])) == union
        for booking_id, booking in held.items():
            for month_id in booking['monthKeys']:
                month = calendars[month_id]
                if booking['activeMode'] == 'FIXED':
                    assert month['fixedBookingId'] == booking_id
                else:
                    assert month['hourlyBookings'][booking_id] == booking['slotKeys']
    for booking_id, booking in bookings.items():
        if booking['status'] not in held_status:
            for month_id in booking['monthKeys']:
                month = all_calendars.get(month_id, {})
                assert month.get('fixedBookingId') != booking_id
                assert booking_id not in month.get('hourlyBookings', {})
        audit_action = 'CREATE' if booking['status'] == 'PENDING' else booking['status']
        audit = document('auditLogs/booking_' + booking_id + '_' + audit_action)
        assert audit['targetPath'] == 'bookings/' + booking_id and audit['action'] == audit_action

    reviews = query('reviews', 'roomId', state['roomId'])
    reports = query('reports', 'roomId', state['roomId'])
    assert len(reviews) == 1 and len(reports) == 2
    for review in reviews.values():
        assert bookings[review['bookingId']]['status'] == 'COMPLETED' and review['rating'] == 5
        assert review['guestUid'] == bookings[review['bookingId']]['guestUid']
    assert rooms[state['roomId']]['approvalStatus'] == 'APPROVED'
    assert rooms[state['roomId']]['listingStatus'] == 'ACTIVE'
    assert bookings[state['pendingBookingId']]['status'] == 'PENDING'
    assert bookings[state['confirmedBookingId']]['status'] == 'CONFIRMED'
    fixture_bookings = {w['path'].split('/')[1] for w in manifest['historicalFixtureWrites'] if w['path'].startswith('bookings/')}
    snapshot_mismatches = [i for i, b in bookings.items() if b['guestName'] != profiles[b['guestUid']]['name']]
    assert set(snapshot_mismatches) <= fixture_bookings, 'New real booking has an inconsistent Guest snapshot'
    if args.normalize_historical_snapshots and snapshot_mismatches:
        corrections = []
        for booking_id in snapshot_mismatches:
            booking = bookings[booking_id]
            assert booking_id in fixture_bookings and booking['roomId'].startswith(manifest['run'])
            profile = profiles[booking['guestUid']]
            request(API + 'bookings/' + booking_id + '?updateMask.fieldPaths=guestName&updateMask.fieldPaths=guestPhone',
                    {'fields': {'guestName': {'stringValue': profile['name']}, 'guestPhone': {'stringValue': profile['phone']}}},
                    fixture_admin_token(), 'PATCH')
            corrections.append({'bookingId': booking_id, 'fields': ['guestName', 'guestPhone'],
                                'scope': 'Historical preparation only; real Repository bookings were already correct'})
        OUTPUT.joinpath('historical-snapshot-corrections.json').write_text(json.dumps(corrections, indent=2), encoding='utf-8')
        print('Historical fixture snapshots normalized:', len(corrections))
        print('Re-run without normalization to verify final state read-only.')
        return
    result = {'passed': True, 'project': PROJECT, 'database': DATABASE, 'run': state['run'],
              'authentication': 'Real retained demo Admin ID token; Security Rules applied',
              'cloudWritesDuringVerification': 0, 'accountsActive': len(profiles), 'rooms': len(rooms),
              'bookings': len(bookings), 'bookingStates': dict(collections.Counter(b['status'] for b in bookings.values())),
              'heldBookings': sum(b['status'] in held_status for b in bookings.values()),
              'calendarDocuments': len(all_calendars), 'reviewCount': len(reviews), 'reportCount': len(reports),
              'roomCountersMatch': True, 'calendarsMatch': True, 'terminalBookingHoldsAbsent': True,
              'currentStateAuditsExist': True, 'videoRoomPublic': True,
              'historicalGuestSnapshotCorrectionsNeeded': snapshot_mismatches}
    OUTPUT.joinpath('cloud-readback.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
