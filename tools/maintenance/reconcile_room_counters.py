"""Plan/apply the missing room counters. Never change booking, calendar or rental mode.

Uses a real Firebase Auth Admin ID token, so deployed Security Rules still apply.
Default is read-only. Cloud writes require --apply and a previously saved plan.
"""
import argparse
import collections
import datetime as dt
import hashlib
import json
import pathlib
import time
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
PROJECT = "broom-sharing-firebase-mobile"
DATABASE = "broomsharing"
OUTPUT = ROOT / "output/verification/room-counter-compatibility-2026-10-25"
HELD = {"PENDING", "CONFIRMED", "CHECKED_IN"}
TERMINAL = {"CANCELLED", "REJECTED", "COMPLETED", "EXPIRED", "NO_SHOW", "TERMINATED"}


class FirebaseError(RuntimeError):
    def __init__(self, code, status):
        super().__init__("Firebase HTTP " + str(code))
        self.code, self.status = code, status


def decode(v):
    if "mapValue" in v:
        return {k: decode(x) for k, x in v["mapValue"].get("fields", {}).items()}
    if "arrayValue" in v:
        return [decode(x) for x in v["arrayValue"].get("values", [])]
    if "integerValue" in v:
        return int(v["integerValue"])
    return next(iter(v.values()))


def fields(document):
    return {k: decode(v) for k, v in document.get("fields", {}).items()}


def encode(v):
    if v is None:
        return {"nullValue": None}
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, dict):
        return {"mapValue": {"fields": {k: encode(x) for k, x in v.items()}}}
    if isinstance(v, list):
        return {"arrayValue": {"values": [encode(x) for x in v]}}
    return {"stringValue": v}


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_room(room_id, bookings, months):
    """Count by status, not wall clock; prove every held key and reverse calendar entry."""
    held = {}
    for identity, b in bookings.items():
        require(b.get("roomId") == room_id, "Booking belongs to another room")
        require(b.get("status") in HELD | TERMINAL, "Unknown booking status; review manually")
        if b["status"] in HELD:
            require(b.get("schemaVersion") == 2, "Active legacy booking; review manually")
            require(b.get("activeMode") in {"FIXED", "HOURLY"}, "Unknown rental mode")
            keys = b.get("monthKeys", [])
            require(bool(keys) and len(keys) == len(set(keys)), "Missing/duplicate month keys")
            require(len(keys) == (b.get("rentalMonths") if b["activeMode"] == "FIXED" else 1), "Wrong month count")
            if b["activeMode"] == "FIXED":
                require(b.get("rentalMonths") in {1, 3} and not b.get("slotKeys"), "Invalid fixed contract")
            else:
                slots = b.get("slotKeys", [])
                require(bool(slots) and len(slots) == len(set(slots)), "Missing/duplicate hourly slots")
            held[identity] = b
            for key in keys:
                m = months.get(key, {})
                require(m.get("roomId") == room_id, "Missing/wrong room calendar")
                if b["activeMode"] == "FIXED":
                    require(m.get("fixedBookingId") == identity, "Fixed calendar does not match booking")
                else:
                    require(m.get("hourlyBookings", {}).get(identity) == b["slotKeys"], "Hourly calendar does not match booking")
    for key, m in months.items():
        require(m.get("roomId") == room_id, "Calendar belongs to another room")
        fixed = m.get("fixedBookingId", "")
        hourly = m.get("hourlyBookings", {})
        busy = m.get("slotsBusy", [])
        require(not fixed or (not hourly and not busy), "Fixed and hourly locks overlap")
        if fixed:
            require(fixed in held and held[fixed]["activeMode"] == "FIXED" and key in held[fixed]["monthKeys"], "Orphan fixed lock")
        union = set()
        for identity, slots in hourly.items():
            require(identity in held and held[identity]["activeMode"] == "HOURLY" and key in held[identity]["monthKeys"], "Orphan hourly lock")
            require(slots == held[identity]["slotKeys"], "Wrong slot ownership")
            require(union.isdisjoint(slots), "Two bookings occupy the same slot")
            union.update(slots)
        require(len(busy) == len(set(busy)) and set(busy) == union, "Busy slots differ from their owners")
    return len(held)


def inventory(snapshot):
    bookings = {i: fields(d) for i, d in snapshot["bookings"].items()}
    months = {i: fields(d) for i, d in snapshot["availabilityMonths"].items()}
    room_bookings, room_months = collections.defaultdict(dict), collections.defaultdict(dict)
    for i, b in bookings.items():
        require(b.get("roomId") in snapshot["rooms"], "Booking room is missing")
        room_bookings[b["roomId"]][i] = b
    for i, m in months.items():
        require(m.get("roomId") in snapshot["rooms"], "Calendar room is missing")
        room_months[m["roomId"]][i] = m
    result = []
    for identity, document in sorted(snapshot["rooms"].items()):
        r = fields(document)
        count = validate_room(identity, room_bookings[identity], room_months[identity])
        previous = r.get("activeBookingCount")
        require(previous is None or (type(previous) is int and previous >= -1), "Invalid counter type")
        require(previous is None or previous < 0 or previous == count, "Known counter mismatch; outside missing-counter migration")
        result.append({"roomId": identity, "beforePresent": "activeBookingCount" in r,
                       "before": previous, "after": count, "needsRepair": previous is None or previous < 0})
    return result


class Firebase:
    def __init__(self, emulator=False):
        self.emulator = emulator
        self.database = "counter_compatibility_qa" if emulator else DATABASE
        self.base = "http://127.0.0.1:8085/v1/" if emulator else "https://firestore.googleapis.com/v1/"
        self.db = f"projects/{PROJECT}/databases/{self.database}"
        self.actor = "qa_counter_admin" if emulator else None
        self.token = "owner" if emulator else None
        if not emulator:
            private = dict(line.split("=", 1) for line in (ROOT / "data/demo/video-credentials.local.properties").read_text(encoding="utf-8").splitlines() if "=" in line and not line.startswith("#"))
            config = json.loads((ROOT / "app/google-services.json").read_text(encoding="utf-8"))
            require(config["project_info"]["project_id"] == PROJECT, "Wrong Firebase project")
            client = next(c for c in config["client"] if c["client_info"]["android_client_info"]["package_name"] == "vn.edu.ueh.phuclu.broomsharing")
            auth = self.http("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + client["api_key"][0]["current_key"],
                             {"email": private["admin.email"], "password": private["admin.password"], "returnSecureToken": True})
            self.token, self.actor = auth["idToken"], auth["localId"]
            profile = fields(self.get("users/" + self.actor))
            require(profile.get("role") == "ADMIN" and profile.get("status") == "ACTIVE", "Active Admin required")

    def http(self, url, body=None, method=None):
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method), timeout=45) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            # No URL, token, credentials or payload in exceptions/log output.
            try:
                status = json.loads(error.read()).get("error", {}).get("status", "UNKNOWN")
            except (ValueError, UnicodeError):
                status = "UNKNOWN"
            raise FirebaseError(error.code, status) from None

    def call(self, suffix, body):
        return self.http(self.base + self.db + "/documents" + suffix, body)

    def get(self, path, transaction=None):
        if transaction:
            rows = self.call(":batchGet", {"documents": [self.db + "/documents/" + path], "transaction": transaction})
            return next(r["found"] for r in rows if "found" in r)
        return self.http(self.base + self.db + "/documents/" + path)

    def query(self, collection, transaction=None, room_id=None):
        query = {"from": [{"collectionId": collection}]}
        if room_id:
            query["where"] = {"fieldFilter": {"field": {"fieldPath": "roomId"}, "op": "EQUAL", "value": {"stringValue": room_id}}}
        body = {"structuredQuery": query}
        if transaction:
            body["transaction"] = transaction
        return {r["document"]["name"].rsplit("/", 1)[-1]: r["document"] for r in self.call(":runQuery", body) if "document" in r}

    def snapshot(self):
        transaction = self.call(":beginTransaction", {"options": {"readOnly": {}}})["transaction"]
        try:
            return {c: self.query(c, transaction) for c in ("rooms", "bookings", "availabilityMonths")}
        finally:
            self.call(":rollback", {"transaction": transaction})

    def repair(self, identity, run):
        # Firebase Auth clients use read-only snapshots plus optimistic atomic commits.
        # Every app booking mutation also changes its room: updateTime guards even a new booking
        # appearing after the query. A conflict forces all reads/validation to run again.
        for attempt in range(4):
            transaction = self.call(":beginTransaction", {"options": {"readOnly": {}}})["transaction"]
            try:
                room = self.get("rooms/" + identity, transaction)
                raw = fields(room)
                bookings = {i: fields(d) for i, d in self.query("bookings", transaction, identity).items()}
                months = {i: fields(d) for i, d in self.query("availabilityMonths", transaction, identity).items()}
                count = validate_room(identity, bookings, months)
                previous = raw.get("activeBookingCount")
                if previous is not None and previous >= 0:
                    require(previous == count, "Room changed to a mismatched counter")
                    return {"roomId": identity, "after": count, "changed": False}
                audit_id = "room_counter_" + run + "_" + identity
                name = self.db + "/documents/rooms/" + identity
                audit = {"schemaVersion": 1, "actorUid": self.actor, "targetPath": "rooms/" + identity,
                         "action": "RECONCILE_ROOM", "before": {"activeBookingCount": previous, "fieldPresent": "activeBookingCount" in raw},
                         "after": {"activeBookingCount": count}, "reason": "Bổ sung bộ đếm từ booking và lịch đã đối soát; giữ nguyên trạng thái thuê."}
                writes = [{"update": {"name": name, "fields": {"activeBookingCount": encode(count)}},
                           "updateMask": {"fieldPaths": ["activeBookingCount"]},
                           "currentDocument": {"updateTime": room["updateTime"]},
                           "updateTransforms": [{"fieldPath": "updatedAt", "setToServerValue": "REQUEST_TIME"}]},
                          {"update": {"name": self.db + "/documents/auditLogs/" + audit_id, "fields": {k: encode(v) for k, v in audit.items()}},
                           "currentDocument": {"exists": False},
                           "updateTransforms": [{"fieldPath": "createdAt", "setToServerValue": "REQUEST_TIME"}]}]
                self.call(":commit", {"writes": writes})
                return {"roomId": identity, "before": previous, "after": count, "changed": True, "auditId": audit_id}
            except FirebaseError as error:
                if (error.code not in {409, 503, 504} and error.status not in {"ABORTED", "FAILED_PRECONDITION"}) or attempt == 3:
                    raise
                time.sleep(0.1 * (attempt + 1))
            finally:
                try:
                    self.call(":rollback", {"transaction": transaction})
                except RuntimeError:
                    pass  # A committed/aborted transaction no longer needs rollback.
        raise RuntimeError("Transaction retries exhausted")


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--plan", type=pathlib.Path)
    args = parser.parse_args()
    require(not args.apply or args.plan is not None, "--apply needs a reviewed --plan")
    firebase = Firebase()
    before = firebase.snapshot()
    rows = inventory(before)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    run = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ_") + uuid.uuid4().hex[:6]
    if not args.apply:
        plan = {"project": PROJECT, "database": DATABASE, "run": run, "snapshotSha256": digest(before), "rooms": rows}
        save(OUTPUT / "plan.json", plan)
        save(OUTPUT / (run + "_before.json"), before)
        print(json.dumps({"mode": "plan", "rooms": len(rows), "repair": sum(r["needsRepair"] for r in rows), "plan": str(OUTPUT / "plan.json"), "documentWrites": 0}))
        return
    plan = json.loads(args.plan.read_text(encoding="utf-8"))
    require(plan["project"] == PROJECT and plan["database"] == DATABASE, "Wrong plan target")
    targets = {r["roomId"] for r in plan["rooms"] if r["needsRepair"]}
    require(targets <= set(before["rooms"]), "Planned room missing")
    require({r["roomId"] for r in rows if r["needsRepair"]} <= targets, "New unknown rooms outside reviewed plan")
    # Persist the raw, typed backup before the first cloud mutation.
    backup = OUTPUT / (run + "_before.json")
    save(backup, before)
    results = []
    for identity in sorted(targets):
        results.append(firebase.repair(identity, run))
        save(OUTPUT / (run + "_progress.json"), results)
    after = firebase.snapshot()
    final = inventory(after)
    require(not any(r["needsRepair"] for r in final), "Unknown counters remain")
    require(before["bookings"] == after["bookings"] and before["availabilityMonths"] == after["availabilityMonths"], "Booking/calendar changed; inspect concurrent activity before declaring acceptance")
    require(set(before["rooms"]) == set(after["rooms"]), "Rooms added/removed concurrently")
    for identity, old in before["rooms"].items():
        allowed = {"activeBookingCount", "updatedAt"} if identity in targets else set()
        a, b = fields(old), fields(after["rooms"][identity])
        require({k: v for k, v in a.items() if k not in allowed} == {k: v for k, v in b.items() if k not in allowed}, "Unrelated room fields changed")
    for result in results:
        if result["changed"]:
            audit = fields(firebase.get("auditLogs/" + result["auditId"]))
            require(audit.get("actorUid") == firebase.actor and audit.get("after", {}).get("activeBookingCount") == result["after"], "Audit readback failed")
    save(OUTPUT / (run + "_after.json"), after)
    receipt = {"passed": True, "project": PROJECT, "database": DATABASE, "run": run,
               "rooms": len(final), "bookings": len(after["bookings"]), "calendarDocuments": len(after["availabilityMonths"]),
               "roomsRepaired": sum(r["changed"] for r in results), "remainingUnknownCounters": 0,
               "knownCountersMatch": True, "bookingAndCalendarUnchanged": True, "otherRoomFieldsUnchanged": True,
               "perRoomAtomicCommitWithAudit": True, "roomUpdateTimePrecondition": True, "beforeBackup": str(backup.relative_to(ROOT)),
               "beforeSha256": digest(before), "afterSha256": digest(after), "results": results}
    save(OUTPUT / "migration-receipt.json", receipt)
    print(json.dumps({k: receipt[k] for k in ["passed", "rooms", "bookings", "calendarDocuments", "roomsRepaired", "remainingUnknownCounters", "bookingAndCalendarUnchanged"]}))


if __name__ == "__main__":
    main()
