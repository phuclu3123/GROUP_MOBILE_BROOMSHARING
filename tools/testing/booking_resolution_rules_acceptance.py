"""Isolated Firestore emulator acceptance. No cloud endpoint, credentials or production writes."""
import base64
import datetime as dt
import hashlib
import json
import pathlib
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = "http://127.0.0.1:8085"
DB = "projects/broom-sharing-firebase-mobile/databases/booking_resolution_qa"
OUT = ROOT / "output/verification/booking-resolution-2026-10-26"
OUT.mkdir(parents=True, exist_ok=True)
NOW = dt.datetime.now(dt.timezone.utc)
results = []


def call(path, body=None, actor=None, method=None):
    token = "owner" if actor is None else jwt(actor)
    request = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(),
        method=method, headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def jwt(uid):
    def part(x): return base64.urlsafe_b64encode(json.dumps(x).encode()).decode().rstrip("=")
    return part({"alg": "none", "typ": "JWT"}) + "." + part({"sub": uid, "user_id": uid,
        "aud": "broom-sharing-firebase-mobile", "iss": "https://securetoken.google.com/broom-sharing-firebase-mobile",
        "iat": int(time.time()), "exp": int(time.time()) + 3600, "firebase": {"sign_in_provider": "password"}}) + "."


def enc(v):
    if v is None: return {"nullValue": None}
    if isinstance(v, bool): return {"booleanValue": v}
    if isinstance(v, int): return {"integerValue": str(v)}
    if isinstance(v, dt.datetime): return {"timestampValue": v.isoformat()}
    if isinstance(v, list): return {"arrayValue": {"values": [enc(x) for x in v]}}
    if isinstance(v, dict): return {"mapValue": {"fields": {k: enc(x) for k, x in v.items()}}}
    return {"stringValue": v}


def seed(path, data):
    code, detail = call("/v1/" + DB + "/documents/" + path, {"fields": {k: enc(v) for k, v in data.items()}}, method="PATCH")
    assert code == 200, detail


def get(path):
    code, data = call("/v1/" + DB + "/documents/" + path)
    assert code == 200, data
    return data


def write(path, data, stamps=(), masked=True):
    w = {"update": {"name": DB + "/documents/" + path, "fields": {k: enc(v) for k, v in data.items()}}}
    if masked: w["updateMask"] = {"fieldPaths": list(data)}
    if stamps: w["updateTransforms"] = [{"fieldPath": k, "setToServerValue": "REQUEST_TIME"} for k in stamps]
    return w


rules = (ROOT / "firestore.rules").read_text(encoding="utf-8")
code, detail = call("/emulator/v1/" + DB + ":securityRules", {"rules": {"files": [{"name": "firestore.rules", "content": rules}]}}, method="PUT")
assert code == 200, detail
assert not any(i.get("severity") == "ERROR" for i in detail.get("issues", [])), detail


def fixture(label, status="CONFIRMED", ended=True, blocked=False, fixed=False, proof=False):
    identity = hashlib.sha256((str(time.time_ns()) + label).encode()).hexdigest()
    room = "resolution_" + identity[:16]
    for uid, role in [("resolution_host", "HOST"), ("resolution_guest", "GUEST"), ("resolution_admin", "ADMIN"), ("resolution_other", "HOST")]:
        seed("users/" + uid, dict(uid=uid, role=role, status="BLOCKED" if blocked and role == "HOST" and uid == "resolution_host" else "ACTIVE", name=uid))
    start = NOW - dt.timedelta(hours=2)
    end = NOW - dt.timedelta(minutes=5) if ended else NOW + dt.timedelta(hours=2)
    if fixed:
        local = NOW.astimezone(dt.timezone(dt.timedelta(hours=7)))
        month = local.year * 12 + local.month - 1 - (4 if ended else 0)
        start = dt.datetime(month // 12, month % 12 + 1, 1, tzinfo=local.tzinfo)
        end = dt.datetime((month + 3) // 12, (month + 3) % 12 + 1, 1, tzinfo=local.tzinfo)
    local = start.astimezone(dt.timezone(dt.timedelta(hours=7)))
    number = local.year * 12 + local.month - 1
    keys = [room + "_" + str((number + i) // 12 * 100 + (number + i) % 12 + 1) for i in range(3 if fixed else 1)]
    held = status in ["PENDING", "CONFIRMED", "CHECKED_IN"]
    b = dict(id=identity, roomId=room, schemaVersion=2, roomName="QA resolution", ownerUid="resolution_host", guestUid="resolution_guest",
        status=status, paymentStatus="UNAVAILABLE", startAt=start, endAt=end, activeMode="FIXED" if fixed else "HOURLY",
        rentalMonths=3 if fixed else 0, slotKeys=[] if fixed else [10, 11], monthKeys=keys, rejectionReason="",
        usageConfirmedAt=NOW-dt.timedelta(hours=1) if proof else None, usageConfirmedBy="resolution_host" if proof else "",
        totalAmount=300000, createdAt=NOW-dt.timedelta(days=3), updatedAt=NOW-dt.timedelta(hours=3), refundAmount=0, refundStatus="NOT_REQUIRED")
    seed("bookings/" + identity, b)
    # Another hourly booking's slot must survive every release and every Admin correction.
    for key in keys:
        seed("availabilityMonths/" + key, dict(roomId=room, monthIndex=int(key.rsplit("_", 1)[1]),
            fixedBookingId=identity if fixed and held else "", slotsBusy=[] if fixed else [10,11,20] if held else [20],
            hourlyBookings={} if fixed else {identity:[10,11], "other_booking":[20]} if held else {"other_booking":[20]}, lastBookingId=identity))
    count = (1 if held else 0) + (0 if fixed else 1)
    seed("rooms/" + room, dict(ownerUid="resolution_host", activeBookingCount=count, bookingRevision=7, lastBookingId=identity))
    return b, count


def transition(b, count, target, actor="resolution_host", reason="Verified with participants", omit=None, changes=None, room_count_override=None):
    held = b["status"] in ["PENDING", "CONFIRMED", "CHECKED_IN"]
    release = held and target in ["COMPLETED", "TERMINATED", "NO_SHOW"]
    data = dict(status=target, rejectionReason=reason)
    data.update(changes or {})
    stamps = ["updatedAt", "closedAt"]
    if target == "CHECKED_IN": stamps = ["updatedAt"]
    if target in ["CHECKED_IN", "COMPLETED", "TERMINATED"] and b.get("usageConfirmedAt") is None:
        stamps.append("usageConfirmedAt"); data["usageConfirmedBy"] = actor
    writes = [write("bookings/" + b["id"], data, stamps)]
    if release:
        for key in b["monthKeys"]:
            fields = dict(roomId=b["roomId"], monthIndex=int(key.rsplit("_",1)[1]), fixedBookingId="",
                slotsBusy=[] if b["activeMode"]=="FIXED" else [20], hourlyBookings={} if b["activeMode"]=="FIXED" else {"other_booking":[20]}, lastBookingId=b["id"])
            writes.append(write("availabilityMonths/"+key, fields, ["updatedAt"], False))
    writes.append(write("rooms/" + b["roomId"], dict(activeBookingCount=count-(1 if release else 0) if room_count_override is None else room_count_override, bookingRevision=8, lastBookingId=b["id"])))
    action = "ATTENDANCE_CONFIRMED" if b["status"] == target else target
    writes.append(write("auditLogs/booking_"+b["id"]+"_"+action,
        dict(actorUid=actor, targetPath="bookings/"+b["id"], action=action, reason=reason, before={"status":b["status"]}, after={"status":target}), ["createdAt"], False))
    if omit: writes = [w for w in writes if omit not in w["update"]["name"]]
    return call("/v1/"+DB+"/documents:commit", {"writes": writes}, actor, "POST")


def check(label, expected=200, target="COMPLETED", actor="resolution_host", reason="Verified with participants", omit=None, changes=None, room_count_override=None, **kwargs):
    b, count = fixture(label, **kwargs)
    before = get("bookings/" + b["id"])
    code, detail = transition(b, count, target, actor, reason, omit, changes, room_count_override)
    passed = code == expected
    if expected != 200: passed &= get("bookings/" + b["id"]) == before
    if code == 200:
        room = get("rooms/" + b["roomId"])["fields"]
        release = b["status"] in ["CONFIRMED", "CHECKED_IN"] and target != "CHECKED_IN"
        passed &= int(room["activeBookingCount"]["integerValue"]) == count - int(release)
        for key in b["monthKeys"]:
            m = get("availabilityMonths/" + key)["fields"]
            if b["activeMode"] == "HOURLY":
                slots = m["slotsBusy"]["arrayValue"].get("values", [])
                passed &= {int(v["integerValue"]) for v in slots} == ({20} if release or b["status"] in ["NO_SHOW", "COMPLETED"] else {10,11,20})
    results.append(dict(case=label, passed=bool(passed), expected=expected, actual=code, error=detail.get("error", {}).get("message")))
    print(label, "PASS" if passed else "FAIL", flush=True)
    return b


check("Host completes checked-in", status="CHECKED_IN", proof=True, reason="")
check("Host late use confirmation")
check("Cannot silently complete unrecorded use", expected=403, reason="")
check("Host confirms no-show", target="NO_SHOW")
check("Cannot no-show early", expected=403, target="NO_SHOW", ended=False)
check("No-show needs reason", expected=403, target="NO_SHOW", reason="")
check("No-show rejects whitespace-only reason", expected=403, target="NO_SHOW", reason=" \t\n ")
check("Late usage confirmation rejects whitespace-only reason", expected=403, reason=" \t\n ")
check("Admin incident closure rejects whitespace-only reason", expected=403, actor="resolution_admin", blocked=True, status="CHECKED_IN", proof=True, ended=False, target="TERMINATED", reason=" \t\n ")
check("Admin completion rejects whitespace-only reason", expected=403, actor="resolution_admin", blocked=True, status="CHECKED_IN", proof=True, reason=" \t\n ")
check("No-show rejects overlong reason", expected=403, target="NO_SHOW", reason="x"*501)
check("Host check-in records usage confirmation", target="CHECKED_IN", ended=False)
check("Cannot record ordinary check-in after end", expected=403, target="CHECKED_IN")
check("Checked-in cannot become no-show", expected=403, status="CHECKED_IN", target="NO_SHOW", proof=True)
check("Guest cannot confirm use", expected=403, actor="resolution_guest")
check("Foreign Host cannot resolve", expected=403, actor="resolution_other")
check("Blocked Host cannot complete", expected=403, blocked=True, status="CHECKED_IN", proof=True)
check("Admin completes for blocked Host", actor="resolution_admin", blocked=True, status="CHECKED_IN", proof=True)
check("Admin cannot silently complete", expected=403, actor="resolution_admin", blocked=True, status="CHECKED_IN", proof=True, reason="")
check("Admin cannot close for active Host", expected=403, actor="resolution_admin", status="CHECKED_IN", proof=True)
terminated = check("Admin terminates ongoing use", actor="resolution_admin", blocked=True, status="CHECKED_IN", proof=True, ended=False, target="TERMINATED")
check("Admin terminates legacy checked-in", actor="resolution_admin", blocked=True, status="CHECKED_IN", ended=False, target="TERMINATED")
check("Host cannot terminate", expected=403, status="CHECKED_IN", proof=True, ended=False, target="TERMINATED")
check("Admin cannot terminate active Host", expected=403, actor="resolution_admin", status="CHECKED_IN", proof=True, ended=False, target="TERMINATED")
check("Admin corrects no-show without second release", actor="resolution_admin", status="NO_SHOW")
check("Admin cannot decrement counter twice", expected=403, actor="resolution_admin", status="NO_SHOW", room_count_override=0)
check("Admin cannot erase another booking count", expected=403, actor="resolution_admin", status="CHECKED_IN", proof=True, blocked=True, room_count_override=0)
check("Admin verifies forgotten check-in after Guest report", actor="resolution_admin", status="CONFIRMED")
check("Host cannot correct no-show", expected=403, status="NO_SHOW")
check("Admin verifies legacy completed record", actor="resolution_admin", status="COMPLETED")
check("Cannot change money while resolving", expected=403, changes={"refundAmount":100})
check("Cannot change original end", expected=403, changes={"endAt": NOW-dt.timedelta(seconds=1)})
check("Cannot omit calendar", expected=403, omit="availabilityMonths/")
check("Cannot omit room revision", expected=403, omit="rooms/")
check("Cannot omit audit", expected=403, omit="auditLogs/")
check("Quarter terminates all three months", actor="resolution_admin", blocked=True, status="CHECKED_IN", proof=True, ended=False, fixed=True, target="TERMINATED")
check("Quarter no-show releases all three months", target="NO_SHOW", fixed=True)


def review(label, status, proof, expected, ended=True, closed=True):
    b, _ = fixture(label, status=status, proof=proof, ended=ended)
    if closed: seed("bookings/"+b["id"], {**b,"closedAt":NOW-dt.timedelta(seconds=1)})
    fields = dict(bookingId=b["id"], roomId=b["roomId"], guestUid="resolution_guest", guestName="resolution_guest", rating=4, comment="Used the room and reporting actual experience.")
    code, detail = call("/v1/"+DB+"/documents:commit", {"writes":[write("reviews/"+b["id"],fields,["createdAt"],False)]},"resolution_guest","POST")
    results.append(dict(case=label, passed=code==expected, expected=expected, actual=code, error=detail.get("error",{}).get("message")))
    print(label,"PASS" if code==expected else "FAIL",flush=True)


review("Used completed rental can review", "COMPLETED", True, 200)
review("Unverified legacy completion cannot review", "COMPLETED", False, 403)
review("No-show cannot review", "NO_SHOW", False, 403)
review("Incident ended early can review", "TERMINATED", True, 200, ended=False)
review("Unclosed incident cannot review", "TERMINATED", True, 403, ended=False, closed=False)
review("Still checked-in cannot review", "CHECKED_IN", True, 403)
review("Future completion cannot review", "COMPLETED", True, 403, ended=False)

(OUT/"rules-acceptance.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
assert all(r["passed"] for r in results), "Rules acceptance failed; inspect output receipt"
print("PASS", len(results), "isolated Rules cases")
