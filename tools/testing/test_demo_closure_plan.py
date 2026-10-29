"""Pure contract checks for the scoped maintenance script; never opens a network socket."""
import copy
import importlib.util
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('closure', 'tools/maintenance/close_confirmed_demo_bookings.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
s = m.source


class Admin:
    db = 'projects/test/databases/test'
    actor = 'owner'
    audits = {}

    def get(self, path):
        return self.audits[path]


def document(fields):
    return {'updateTime': '2020-01-01T00:00:00Z', 'fields': {k: s.encode(v) for k, v in fields.items()}}


def snapshot():
    out = {c: {} for c in ['rooms', 'bookings', 'availabilityMonths']}
    for identity, room in m.TARGETS.items():
        key, other = room + '_202010', identity + '_other'
        booking = {'id': identity, 'roomId': room, 'ownerUid': 'owner', 'status': 'CONFIRMED',
                   'schemaVersion': 2, 'activeMode': 'HOURLY', 'rentalMonths': 0, 'slotKeys': [9, 10],
                   'monthKeys': [key], 'endAt': '2020-10-20T00:00:00Z', 'paymentStatus': 'UNPAID',
                   'paidAmount': 0, 'totalAmount': 5000, 'priceSnapshot': {'depositAmount': 2000}}
        out['bookings'][identity] = document(booking)
        out['bookings'][other] = document(booking | {'id': other, 'slotKeys': [11]})
        out['rooms'][room] = document({'ownerUid': 'owner', 'activeBookingCount': 2, 'bookingRevision': 4, 'roomName': 'Keep name'})
        out['availabilityMonths'][key] = document({'roomId': room, 'monthIndex': 202010,
            'fixedBookingId': '', 'hourlyBookings': {identity: [9, 10], other: [11]},
            'slotsBusy': [9, 10, 11], 'pendingPaymentIds': [], 'lastBookingId': other})
    return out


class ClosureContract(unittest.TestCase):
    def setUp(self):
        self.before = snapshot()
        self.client = Admin()
        self.mock = patch.object(m, 'operator', return_value=self.client)
        self.mock.start()
        self.addCleanup(self.mock.stop)
        self.identity, self.room = next(iter(m.TARGETS.items()))

    def plan(self):
        return m.prepare(self.client, self.before, self.identity, self.room)

    def change(self, key, value):
        self.before['bookings'][self.identity]['fields'][key] = s.encode(value)

    def test_atomic_plan_preserves_input_and_guards_every_write(self):
        original = copy.deepcopy(self.before)
        plan = self.plan()
        self.assertEqual(len(plan['writes']), 4)
        self.assertEqual(self.before, original)
        self.assertTrue(all(w.get('currentDocument') for w in plan['writes']))
        self.assertEqual(plan['writes'][0]['updateMask']['fieldPaths'], ['status', 'rejectionReason'])

    def test_verified_closure_only_releases_owned_slots(self):
        after = copy.deepcopy(self.before)
        for identity, room in m.TARGETS.items():
            plan = m.prepare(self.client, self.before, identity, room)
            for write in plan['writes']:
                collection, key = write['update']['name'].split('/documents/')[1].split('/')
                if collection == 'auditLogs':
                    self.client.audits['auditLogs/' + key] = {'fields': write['update']['fields']}
                    continue
                after[collection][key]['fields'].update(write['update']['fields'])
                for transform in write['updateTransforms']:
                    after[collection][key]['fields'][transform['fieldPath']] = s.encode('2026-10-28T00:00:00Z')
        result = m.verify(self.before, after, self.client)
        self.assertEqual(len(result), 3)
        self.assertTrue(all(r['moneyUnchanged'] and r['usageProofAbsent'] and r['ownLocksReleased'] for r in result))

    def test_blocks_usage_proof(self):
        self.change('usageConfirmedAt', '2020-10-20T00:00:00Z')
        with self.assertRaises(ValueError): self.plan()

    def test_blocks_payment(self):
        self.change('paidAmount', 2000)
        with self.assertRaises(ValueError): self.plan()

    def test_blocks_future_stay(self):
        self.change('endAt', '2099-10-20T00:00:00Z')
        with self.assertRaises(ValueError): self.plan()

    def test_blocks_changed_status_or_room(self):
        self.change('status', 'CHECKED_IN')
        with self.assertRaises(ValueError): self.plan()
        self.change('status', 'CONFIRMED')
        self.change('roomId', 'different')
        with self.assertRaises(ValueError): self.plan()

    def test_known_closure_does_not_decrement_twice(self):
        self.change('status', 'NO_SHOW')
        self.change('rejectionReason', m.REASON)
        self.change('closedAt', '2026-10-28T00:00:00Z')
        self.assertEqual(self.plan()['writes'], [])


if __name__ == '__main__':
    unittest.main()
