"""Local validation tests; optional emulator checks use a separate QA database only."""
import base64
import copy
import json
import os
import pathlib
import sys
import time
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "maintenance"))
import reconcile_room_counters as migration


class CounterContractTest(unittest.TestCase):
    def setUp(self):
        self.booking = {"roomId": "room", "schemaVersion": 2, "status": "CONFIRMED", "activeMode": "HOURLY", "rentalMonths": 0, "monthKeys": ["room_202611"], "slotKeys": [12, 13]}
        self.month = {"roomId": "room", "fixedBookingId": "", "hourlyBookings": {"booking": [12, 13]}, "slotsBusy": [12, 13]}

    def count(self, booking=None, month=None):
        return migration.validate_room("room", {"booking": booking or self.booking}, {"room_202611": month or self.month})

    def test_empty_room_is_zero(self):
        self.assertEqual(0, migration.validate_room("room", {}, {}))

    def test_all_three_held_states_count_even_after_end(self):
        for status in migration.HELD:
            self.booking.update(status=status, startAt="2020-01-01T00:00:00Z", endAt="2020-01-01T01:00:00Z")
            self.assertEqual(1, self.count())

    def test_terminal_booking_without_lock_is_zero(self):
        for status in migration.TERMINAL:
            self.booking["status"] = status
            self.assertEqual(0, migration.validate_room("room", {"booking": self.booking}, {}))

    def test_terminal_lock_is_rejected(self):
        self.booking["status"] = "CANCELLED"
        with self.assertRaisesRegex(ValueError, "Orphan"):
            self.count()

    def test_missing_month_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Missing/wrong"):
            migration.validate_room("room", {"booking": self.booking}, {})

    def test_wrong_slot_owner_is_rejected(self):
        self.month["hourlyBookings"]["booking"] = [14, 15]
        with self.assertRaises(ValueError):
            self.count()

    def test_busy_slots_must_equal_owned_slots(self):
        self.month["slotsBusy"].append(14)
        with self.assertRaisesRegex(ValueError, "Busy slots"):
            self.count()

    def test_overlapping_two_bookings_rejected(self):
        self.month["hourlyBookings"]["other"] = [12, 13]
        with self.assertRaisesRegex(ValueError, "same slot"):
            migration.validate_room("room", {"booking": self.booking, "other": copy.deepcopy(self.booking)}, {"room_202611": self.month})

    def test_quarter_counts_one_booking_not_three_months(self):
        self.booking.update(activeMode="FIXED", rentalMonths=3, slotKeys=[], monthKeys=["room_202611", "room_202612", "room_202701"])
        month = {"roomId": "room", "fixedBookingId": "booking", "hourlyBookings": {}, "slotsBusy": []}
        self.assertEqual(1, migration.validate_room("room", {"booking": self.booking}, {k: copy.deepcopy(month) for k in self.booking["monthKeys"]}))

    def test_unknown_status_and_active_legacy_rejected(self):
        for change in [{"status": "REQUESTED"}, {"schemaVersion": 1}]:
            with self.assertRaises(ValueError):
                self.count(dict(self.booking, **change))

    def test_foreign_room_rejected(self):
        with self.assertRaises(ValueError):
            self.count(dict(self.booking, roomId="other"))

    def test_duplicate_month_keys_rejected(self):
        with self.assertRaises(ValueError):
            self.count(dict(self.booking, monthKeys=["room_202611", "room_202611"]))

    def test_known_mismatch_is_not_silently_repaired(self):
        def doc(data):
            return {"fields": {k: migration.encode(v) for k, v in data.items()}}
        snapshot = {"rooms": {"room": doc({"activeBookingCount": 0})}, "bookings": {"booking": doc(self.booking)}, "availabilityMonths": {"room_202611": doc(self.month)}}
        with self.assertRaisesRegex(ValueError, "Known counter mismatch"):
            migration.inventory(snapshot)


@unittest.skipUnless(os.environ.get("RUN_COUNTER_EMULATOR") == "true", "Explicit emulator opt-in")
class CounterEmulatorTest(unittest.TestCase):
    def test_transaction_migration_and_host_permissions(self):
        db = migration.Firebase(emulator=True)
        root = db.base + db.db + "/documents/"
        def seed(path, values):
            db.http(root + path, {"fields": {k: migration.encode(v) for k, v in values.items()}}, "PATCH")
        for uid, role in [(db.actor, "ADMIN"), ("qa_counter_host", "HOST"), ("qa_counter_guest", "GUEST")]:
            seed("users/" + uid, {"role": role, "status": "ACTIVE"})
        for identity in ["qa_counter_empty", "qa_counter_occupied"]:
            seed("rooms/" + identity, {"id": identity, "schemaVersion": 1, "ownerUid": "qa_counter_host", "buildingId": "qa_counter_building",
                 "roomName": "Counter QA", "activeMode": "HOURLY", "pricePerHour": 1000, "pricePerMonth": 3000, "pricePerQuarter": 8000,
                 "deposit": 0, "capacity": 1, "area": 1, "imageUrls": [], "blackoutDates": [], "blackoutRanges": [],
                 "approvalStatus": "PENDING_REVIEW", "listingStatus": "ACTIVE", "bookingRevision": 0, "lastBookingId": "",
                 "locationVerification": "UNVERIFIED", "photosVerification": "UNVERIFIED"})
        seed("bookings/qa_counter_booking", {"roomId": "qa_counter_occupied", "schemaVersion": 2, "status": "PENDING", "activeMode": "HOURLY", "rentalMonths": 0, "monthKeys": ["qa_counter_occupied_202611"], "slotKeys": [12, 13]})
        seed("availabilityMonths/qa_counter_occupied_202611", {"roomId": "qa_counter_occupied", "fixedBookingId": "", "hourlyBookings": {"qa_counter_booking": [12, 13]}, "slotsBusy": [12, 13]})
        rules = (migration.ROOT / "firestore.rules").read_text(encoding="utf-8")
        db.http("http://127.0.0.1:8085/emulator/v1/" + db.db + ":securityRules", {"rules": {"files": [{"name": "firestore.rules", "content": rules}]}}, "PUT")
        def token(uid):
            def b64(v): return base64.urlsafe_b64encode(json.dumps(v).encode()).decode().rstrip("=")
            return b64({"alg": "none", "typ": "JWT"}) + "." + b64({"sub": uid, "user_id": uid, "aud": migration.PROJECT, "iss": "https://securetoken.google.com/" + migration.PROJECT, "iat": int(time.time()), "exp": int(time.time()) + 3600, "firebase": {"sign_in_provider": "password"}}) + "."
        results = []
        def patch(label, uid, identity, key, value, allowed):
            db.token = token(uid)
            try:
                db.http(root + "rooms/" + identity + "?updateMask.fieldPaths=" + key, {"fields": {key: migration.encode(value)}}, "PATCH")
                actual = True
            except RuntimeError as error:
                self.assertEqual("Firebase HTTP 403", str(error))
                actual = False
            self.assertEqual(allowed, actual, label)
            results.append({"case": label, "passed": actual == allowed})
        patch("Unknown counter blocks mode change", "qa_counter_host", "qa_counter_empty", "activeMode", "FIXED", False)
        patch("Guest cannot repair counter", "qa_counter_guest", "qa_counter_empty", "activeBookingCount", 0, False)
        patch("Host cannot self-reset counter", "qa_counter_host", "qa_counter_empty", "activeBookingCount", 0, False)
        db.token = token(db.actor)
        before = db.snapshot()
        self.assertTrue(all(r["needsRepair"] for r in migration.inventory(before)))
        run = str(int(time.time() * 1000))
        real_call = db.call
        concurrent = {"injected": False, "commits": 0}
        def conflict_once(suffix, body):
            if suffix == ":commit":
                concurrent["commits"] += 1
                if not concurrent["injected"]:
                    concurrent["injected"] = True
                    db.http(root + "rooms/qa_counter_empty?updateMask.fieldPaths=bookingRevision", {"fields": {"bookingRevision": migration.encode(1)}}, "PATCH")
            return real_call(suffix, body)
        db.call = conflict_once
        for identity, expected in [("qa_counter_empty", 0), ("qa_counter_occupied", 1)]:
            self.assertEqual(expected, db.repair(identity, run)["after"])
            self.assertFalse(db.repair(identity, run)["changed"])
        after = db.snapshot()
        self.assertEqual(before["bookings"], after["bookings"])
        self.assertEqual(before["availabilityMonths"], after["availabilityMonths"])
        self.assertTrue(concurrent["injected"])
        self.assertEqual(3, concurrent["commits"])
        self.assertFalse(any(r["needsRepair"] for r in migration.inventory(after)))
        patch("Reconciled empty room can change mode", "qa_counter_host", "qa_counter_empty", "activeMode", "FIXED", True)
        patch("Occupied room still cannot change mode", "qa_counter_host", "qa_counter_occupied", "activeMode", "FIXED", False)
        db.token = token(db.actor)
        audits = db.query("auditLogs")
        self.assertEqual(2, sum(i.startswith("room_counter_" + run) for i in audits))
        migration.OUTPUT.mkdir(parents=True, exist_ok=True)
        migration.save(migration.OUTPUT / "emulator-acceptance.json", {"passed": True, "database": db.database, "checks": results, "atomicRepair": True, "concurrentEditRetriesFreshSnapshot": True, "retryIsIdempotent": True, "bookingsAndCalendarUnchanged": True})


if __name__ == "__main__":
    unittest.main(verbosity=2)
