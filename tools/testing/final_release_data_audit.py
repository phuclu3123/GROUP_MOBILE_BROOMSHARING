"""Inspect the four legacy anomalies and repair only the approved stale room text."""
from pathlib import Path
import argparse
import datetime as dt
import importlib.util
import json

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/verification/final-qa-2026-10-28'
spec = importlib.util.spec_from_file_location('counter_source', ROOT / 'tools/maintenance/reconcile_room_counters.py')
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--fix-policy', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    client = source.Firebase()
    audit = json.loads((ROOT / 'output/verification/pre-submission-2026-10-27/cloud-data-read-audit.json').read_text(encoding='utf-8'))
    ids = audit['findings']['unclosed_past_end']['documentIds'] + audit['findings']['review_without_usage_proof']['documentIds']
    all_audits = {key: source.fields(doc) for key, doc in client.query('auditLogs').items()}
    selected = []
    for identity in ids:
        b = source.fields(client.get('bookings/' + identity))
        room = source.fields(client.get('rooms/' + b['roomId']))
        selected.append({
            'bookingId': identity,
            'booking': {key: b.get(key) for key in ['roomId', 'status', 'schemaVersion', 'paymentFlow', 'paymentStatus', 'paidAmount', 'startAt', 'endAt', 'createdAt', 'updatedAt', 'usageConfirmedAt', 'usageConfirmedBy', 'checkedInAt', 'closedAt', 'rejectionReason', 'requestFingerprint']},
            'room': {key: room.get(key) for key in ['id', 'roomName', 'ownerUid']},
            'audits': [{**value, 'id': key} for key, value in all_audits.items() if value.get('targetPath') == 'bookings/' + identity]
        })
    room_id = 'payos_demo_20261026_room'
    room_doc = client.get('rooms/' + room_id)
    before = source.fields(room_doc).get('rentalPolicy')
    after = ('Trả trước 40% qua QR khi đặt chỗ; lịch tạm giữ tối đa 5 phút, không vượt giờ bắt đầu thuê. '
             'Nhận đủ tiền đúng hạn mới xác nhận booking, không cần Host duyệt. '
             'Bản demo chưa có API hoàn tiền; trường hợp cần hoàn tiền được đối soát thủ công.')
    changed = False
    if args.fix_policy and before != after:
        source.require(before == 'Trả trước 40% sau xác nhận. Bản demo chưa có API hoàn tiền.', 'Unexpected policy: inspect before applying')
        name = client.db + '/documents/rooms/' + room_id
        log_id = 'release_policy_20261028_' + room_id
        log = {'schemaVersion': 1, 'actorUid': client.actor, 'targetPath': 'rooms/' + room_id,
               'action': 'UPDATE_ROOM_POLICY', 'before': {'rentalPolicy': before},
               'after': {'rentalPolicy': after}, 'reason': 'Sửa mô tả cũ cho khớp luồng INSTANT_5M; không đổi giá, lịch hay trạng thái.'}
        client.call(':commit', {'writes': [
            {'update': {'name': name, 'fields': {'rentalPolicy': source.encode(after)}},
             'updateMask': {'fieldPaths': ['rentalPolicy']}, 'currentDocument': {'updateTime': room_doc['updateTime']}},
            {'update': {'name': client.db + '/documents/auditLogs/' + log_id,
                        'fields': {key: source.encode(value) for key, value in log.items()}},
             'currentDocument': {'exists': False},
             'updateTransforms': [{'fieldPath': 'createdAt', 'setToServerValue': 'REQUEST_TIME'}]}
        ]})
        source.require(source.fields(client.get('rooms/' + room_id))['rentalPolicy'] == after, 'Policy readback mismatch')
        changed = True
    result = {'checkedAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'legacyCases': selected,
              'roomPolicy': {'roomId': room_id, 'before': before, 'after': after if changed else before, 'changed': changed},
              'legacyBookingsMutated': False, 'usageProofFabricated': False}
    (OUT / 'legacy-data-and-policy.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'policyChanged': changed, 'legacyCases': [{
        'id': x['bookingId'], 'room': x['room']['roomName'], 'status': x['booking']['status'],
        'usageProof': x['booking']['usageConfirmedAt'], 'audits': len(x['audits'])} for x in selected]}, ensure_ascii=True))


if __name__ == '__main__':
    main()
