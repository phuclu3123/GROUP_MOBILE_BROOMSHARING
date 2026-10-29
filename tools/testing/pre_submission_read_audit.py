"""Read-only release audit. Writes sanitized local evidence, never cloud documents."""
from pathlib import Path
import collections
import datetime as dt
import importlib.util
import json
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/verification/pre-submission-2026-10-27'
spec = importlib.util.spec_from_file_location('counter_audit', ROOT / 'tools/maintenance/reconcile_room_counters.py')
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    client = source.Firebase()
    transaction = client.call(':beginTransaction', {'options': {'readOnly': {}}})['transaction']
    try:
        raw = {c: client.query(c, transaction) for c in
               ['users', 'publicHosts', 'buildings', 'rooms', 'bookings', 'availabilityMonths',
                'reviews', 'reports', 'propertyVerifications', 'hostApplications', 'payments']}
    finally:
        client.call(':rollback', {'transaction': transaction})
    data = {c: {i: source.fields(v) for i, v in rows.items()} for c, rows in raw.items()}
    now = dt.datetime.now(dt.timezone.utc)

    def date(value):
        try:
            return dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
        except (AttributeError, ValueError):
            return None

    def elapsed(b, key):
        value = date(b.get(key))
        return value is not None and value <= now

    findings = collections.defaultdict(list)
    for identity, room in data['rooms'].items():
        building = data['buildings'].get(room.get('buildingId'))
        owner = data['users'].get(room.get('ownerUid'))
        if building is None:
            findings['room_missing_building'].append(identity)
        elif building.get('ownerUid') != room.get('ownerUid'):
            findings['room_building_owner_mismatch'].append(identity)
        if not owner or owner.get('role') != 'HOST':
            findings['room_missing_host_profile'].append(identity)
        if room.get('activeMode') not in ['HOURLY', 'FIXED']:
            findings['room_unknown_mode'].append(identity)
        if room.get('approvalStatus') == 'APPROVED' and room.get('listingStatus') == 'ACTIVE':
            if not owner or owner.get('status') != 'ACTIVE':
                findings['public_room_inactive_host'].append(identity)
            if not room.get('imageUrls'):
                findings['public_room_without_photos'].append(identity)
            projection = data['publicHosts'].get(room.get('ownerUid'))
            if not projection or projection.get('status') != 'ACTIVE':
                findings['public_room_missing_active_host_projection'].append(identity)
    for identity, b in data['bookings'].items():
        if b.get('status') == 'PENDING' and elapsed(b, 'startAt'):
            findings['pending_past_start_stored'].append(identity)
        if b.get('status') in ['CONFIRMED', 'CHECKED_IN'] and elapsed(b, 'endAt'):
            findings['unclosed_past_end'].append(identity)
        if b.get('schemaVersion') != 2:
            findings['legacy_booking'].append(identity)
        if b.get('guestUid') == b.get('ownerUid'):
            findings['self_booking'].append(identity)
        for key in ['guestUid', 'ownerUid']:
            if b.get(key) not in data['users']:
                findings['booking_missing_participant'].append(identity)
        if b.get('status') in source.HELD:
            room = data['rooms'].get(b.get('roomId'), {})
            if room.get('activeMode') != b.get('activeMode'):
                findings['held_booking_mode_mismatch'].append(identity)
    for identity, review in data['reviews'].items():
        b = data['bookings'].get(review.get('bookingId'))
        if not b:
            findings['review_missing_booking'].append(identity)
        elif not b.get('usageConfirmedAt') or b.get('status') not in ['COMPLETED', 'TERMINATED']:
            findings['review_without_usage_proof'].append(identity)
    validations = source.inventory({c: raw[c] for c in ['rooms', 'bookings', 'availabilityMonths']})
    result = {
        'checkedAt': now.isoformat(), 'documentWrites': 0,
        'collectionCounts': {c: len(v) for c, v in data.items()},
        'bookingStatuses': dict(collections.Counter(b.get('status') for b in data['bookings'].values())),
        'counterCalendarIntegrity': {'roomsChecked': len(validations), 'repairsNeeded': sum(r['needsRepair'] for r in validations)},
        'findings': {k: {'count': len(v), 'documentIds': v} for k, v in findings.items()},
        'legacyReviewDetails': [{'reviewId': i, 'bookingId': r.get('bookingId'),
             'reviewCreatedAt': r.get('createdAt'),
             'bookingStatus': data['bookings'].get(r.get('bookingId'), {}).get('status'),
             'bookingSchema': data['bookings'].get(r.get('bookingId'), {}).get('schemaVersion'),
             'usageConfirmedAt': data['bookings'].get(r.get('bookingId'), {}).get('usageConfirmedAt')}
            for i, r in data['reviews'].items() if i in findings['review_without_usage_proof']],
        'scope': 'Single read-only transaction; no cloud document mutation. Overdue stored states require interpretation, not automatic corruption.'}
    (OUT / 'cloud-data-read-audit.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in result.items() if k != 'findings'}, ensure_ascii=False))
    print(json.dumps({'findingCounts': {k: len(v) for k, v in findings.items()}}))


if __name__ == '__main__':
    main()
