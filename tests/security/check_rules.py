"""Opt-in local emulator checks. Never connects to the real Firebase project."""
import json
import uuid
from urllib.request import Request, urlopen
from urllib.error import HTTPError

PROJECT = "demo-broom-sharing"
FIRESTORE = f"http://127.0.0.1:8080/v1/projects/{PROJECT}/databases/broomsharing/documents/"
AUTH = "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake"


def request(url, data=None, token=None, method=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = Request(url, None if data is None else json.dumps(data).encode(), headers, method=method)
    try:
        with urlopen(req, timeout=10) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.load(error)


def value(item):
    if item is None:
        return {"nullValue": None}
    if isinstance(item, bool):
        return {"booleanValue": item}
    if isinstance(item, int):
        return {"integerValue": str(item)}
    if isinstance(item, float):
        return {"doubleValue": item}
    if isinstance(item, list):
        return {"arrayValue": {"values": [value(x) for x in item]}}
    return {"stringValue": item}


def write(path, fields, token="owner", mask=False):
    url = FIRESTORE + path
    if mask:
        url += "?" + "&".join("updateMask.fieldPaths=" + key for key in fields)
    return request(url, {"fields": {key: value(item) for key, item in fields.items()}}, token, "PATCH")[0]


def account(role):
    email = "rules_" + uuid.uuid4().hex + "@example.com"
    code, data = request(AUTH, {"email": email, "password": "RulesTest_123456!", "returnSecureToken": True}, method="POST")
    assert code == 200, "Auth emulator unavailable"
    uid, token = data["localId"], data["idToken"]
    assert write("users/" + uid, {"uid": uid, "role": role, "status": "ACTIVE", "schemaVersion": 1}) == 200
    return uid, token


def main():
    guest, guest_token = account("GUEST")
    host, host_token = account("HOST")
    other, other_token = account("HOST")
    suffix = uuid.uuid4().hex
    building, room = "buildings/" + suffix, "rooms/" + suffix
    assert write(building, {"id": suffix, "ownerUid": host, "schemaVersion": 1, "status": "ACTIVE"}) == 200
    fields = {"id": suffix, "schemaVersion": 1, "ownerUid": host, "buildingId": suffix,
              "buildingName": "Test", "roomName": "Test", "roomType": "Meeting", "capacity": 10,
              "area": 25, "floor": "3", "address": "Test address", "activeMode": "HOURLY",
              "pricePerHour": 100000, "pricePerDay": 0, "pricePerMonth": 0, "pricePerQuarter": 0,
              "deposit": 0, "openTime": "08:00", "closeTime": "22:00", "amenities": [], "rules": "",
              "approvalStatus": "DRAFT", "listingStatus": "ACTIVE", "imageUrls": [],
              "rejectionReason": "", "createdAt": None, "updatedAt": None}
    assert write(room, fields) == 200
    booking = "bookings/" + suffix
    assert write(booking, {"guestUid": guest, "ownerUid": host, "status": "PENDING", "paymentStatus": "UNPAID"}) == 200
    checks = [
        ("guest cannot become ADMIN", write("users/" + guest, {"role": "ADMIN"}, guest_token, True), 403),
        ("another host cannot edit room", write(room, {"roomName": "Changed"}, other_token, True), 403),
        ("owner can edit draft", write(room, {"roomName": "Changed"}, host_token, True), 200),
        ("host cannot self approve", write(room, {"approvalStatus": "APPROVED"}, host_token, True), 403),
        ("host cannot self verify", write(room, {"verified": True}, host_token, True), 403),
        ("guest cannot set PAID", write(booking, {"paymentStatus": "PAID"}, guest_token, True), 403),
        ("guest cannot forge audit", write("auditLogs/" + suffix, {"action": "APPROVE"}, guest_token), 403),
        ("anonymous cannot read draft", request(FIRESTORE + room)[0], 403),
        ("other host cannot read draft", request(FIRESTORE + room, token=other_token)[0], 403),
        ("anonymous cannot read profile", request(FIRESTORE + "users/" + guest)[0], 403),
        ("guest cannot write payment", write("payments/" + suffix, {"status": "PAID"}, guest_token), 403),
    ]
    fields["approvalStatus"] = "APPROVED"
    assert write(room, fields) == 200  # Seed through emulator-only admin bypass.
    checks.append(("anonymous can read approved active room", request(FIRESTORE + room)[0], 200))
    for label, actual, expected in checks:
        print(("PASS" if actual == expected else "FAIL"), label, actual)
    assert all(actual == expected for _, actual, expected in checks), "Rules checks failed"


if __name__ == "__main__":
    main()
