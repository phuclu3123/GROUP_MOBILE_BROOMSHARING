"""Payment permissions and paid/unpaid cancellation. Emulator only; no cloud writes."""
import datetime as dt
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("resolution", ROOT / "tools/testing/booking_resolution_rules_acceptance.py")
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)  # Also runs the existing 44 lifecycle regression cases.
qa.DB = "projects/broom-sharing-firebase-mobile/databases/payment_rules_qa"
code, detail = qa.call("/emulator/v1/" + qa.DB + ":securityRules",
    {"rules": {"files": [{"name": "firestore.rules", "content": (ROOT / "firestore.rules").read_text(encoding="utf-8") }]}}, method="PUT")
assert code == 200, detail
results = []

def expect(label, code, expected, detail):
    results.append(dict(case=label, passed=code == expected, actual=code, expected=expected))
    print(label, "PASS" if code == expected else "FAIL", flush=True)
    if code != expected: print(str(detail)[:1200], flush=True)

for label, payment_status, hours, amount, refund_status, extra, expected in [
    ("Paid full-window cancellation records manual review", "PAID", 48, 40000, "MANUAL_REVIEW", {}, 200),
    ("Paid partial-window cancellation records manual review", "PAID", 20, 20000, "MANUAL_REVIEW", {}, 200),
    ("Paid late cancellation has no refund due", "PAID", 1, 0, "NOT_REQUIRED", {}, 200),
    ("Pending QR cancellation has zero refund", "PENDING", 48, 0, "NOT_REQUIRED", {}, 200),
    ("Unpaid cancellation has zero refund", "UNAVAILABLE", 48, 0, "NOT_REQUIRED", {}, 200),
    ("Cannot falsely confirm refund", "PAID", 48, 40000, "REFUNDED", {}, 403),
    ("Cannot forge refund amount", "PAID", 48, 99999, "MANUAL_REVIEW", {}, 403),
    ("Cannot erase paid amount on cancellation", "PAID", 48, 40000, "MANUAL_REVIEW", {"paidAmount":0}, 403),
    ("Review payment can release booking while retaining manual review", "REVIEW_REQUIRED", 48, 0, "MANUAL_REVIEW", {}, 200),
]:
    b, count = qa.fixture(label, ended=False)
    b.update(startAt=qa.NOW + dt.timedelta(hours=hours), endAt=qa.NOW + dt.timedelta(hours=hours+2),
        priceSnapshot={"cancellationPolicyVersion":1}, paymentStatus=payment_status,
        paidAmount=40000 if payment_status in ["PAID", "REVIEW_REQUIRED"] else 0)
    qa.seed("bookings/" + b["id"], b)
    rate = 100 if hours >= 24 else 50 if hours >= 6 else 0
    data = dict(status="CANCELLED", rejectionReason="", cancelledByUid="resolution_guest",
        cancellationRefundPercent=rate, refundAmount=amount, refundStatus=refund_status, **extra)
    writes = [qa.write("bookings/"+b["id"], data, ["updatedAt", "closedAt", "cancelledAt"])]
    for key in b["monthKeys"]:
        writes.append(qa.write("availabilityMonths/"+key, dict(roomId=b["roomId"], monthIndex=int(key.rsplit("_",1)[1]),
            fixedBookingId="", slotsBusy=[20], hourlyBookings={"other_booking":[20]}, lastBookingId=b["id"]), ["updatedAt"], False))
    writes.append(qa.write("rooms/"+b["roomId"],dict(activeBookingCount=count-1,bookingRevision=8,lastBookingId=b["id"])))
    writes.append(qa.write("auditLogs/booking_"+b["id"]+"_CANCELLED",dict(actorUid="resolution_guest",
        action="CANCELLED",targetPath="bookings/"+b["id"],reason="",before={"status":"CONFIRMED"},after={"status":"CANCELLED"}),["createdAt"],False))
    code, detail = qa.call("/v1/"+qa.DB+"/documents:commit", {"writes":writes}, "resolution_guest", "POST")
    expect(label, code, expected, detail)

b, _ = qa.fixture("payment permissions", ended=False)
path = "payments/"+b["id"]
for actor, expected in [("resolution_guest",404),("resolution_host",404),("resolution_other",403)]:
    code, detail = qa.call("/v1/"+qa.DB+"/documents/"+path,actor=actor)
    expect("Missing payment listener permitted only for participant: "+actor,code,expected,detail)
qa.seed(path,dict(bookingId=b["id"],guestUid="resolution_guest",ownerUid="resolution_host",status="PENDING",amount=40000))
for actor, expected in [("resolution_guest",200),("resolution_host",200),("resolution_admin",200),("resolution_other",403)]:
    code, detail = qa.call("/v1/"+qa.DB+"/documents/"+path,actor=actor)
    expect("Payment read: "+actor,code,expected,detail)
    code, detail = qa.call("/v1/"+qa.DB+"/documents:commit",{"writes":[qa.write(path,{"status":"PAID"})]},actor,"POST")
    expect("Client cannot mark payment paid: "+actor,code,403,detail)
    code, detail = qa.call("/v1/"+qa.DB+"/documents:commit",{"writes":[qa.write("bookings/"+b["id"],{"paymentStatus":"PAID","paidAmount":40000},["updatedAt"])]},actor,"POST")
    expect("Client cannot alter booking money: "+actor,code,403,detail)
qa.seed("users/resolution_guest",dict(status="BLOCKED",role="GUEST",uid="resolution_guest"))
code, detail = qa.call("/v1/"+qa.DB+"/documents/"+path,actor="resolution_guest")
expect("Blocked account cannot read payment",code,403,detail)
qa.seed("paymentOrders/123456",dict(bookingId=b["id"]))
code, detail = qa.call("/v1/"+qa.DB+"/documents/paymentOrders/123456",actor="resolution_admin")
expect("Order lookup is server only",code,403,detail)
out=ROOT/"output/verification/payos-2026-10-26";out.mkdir(parents=True,exist_ok=True)
(out/"rules-acceptance.json").write_text(json.dumps(results,indent=2),encoding="utf-8")
assert all(r["passed"] for r in results), "Payment Rules acceptance failed"
print("PASS",len(results),"payment Rules cases")
