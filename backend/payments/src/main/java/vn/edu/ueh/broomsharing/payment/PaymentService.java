package vn.edu.ueh.broomsharing.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.cloud.firestore.*;

import java.security.SecureRandom;
import java.util.*;

// Mỗi booking dùng một mã thanh toán bền vững. Gọi payOS ngoài transaction để tránh thu lặp khi
// retry.
public final class PaymentService {
    private final Firestore db;
    private final PaymentGateway provider;
    private final String host, base;
    private final SecureRandom random = new SecureRandom();

    public PaymentService(Firestore db, PaymentGateway provider, String host, String base) {
        this.db = db;
        this.provider = provider;
        this.host = host;
        this.base = base;
    }

    public Map<String, Object> checkout(String uid, String id) throws Exception {
        validId(id);
        DocumentReference booking = db.collection("bookings").document(id);
        DocumentReference payment = db.collection("payments").document(id);
        long candidate = 10_000_000_000_000L + random.nextLong(80_000_000_000_000L);
        Map<String, Object> reserved =
                db.runTransaction(
                                tx -> {
                                    DocumentSnapshot b = tx.get(booking).get(),
                                            p = tx.get(payment).get();
                                    DocumentSnapshot actor =
                                            tx.get(db.collection("users").document(uid)).get();
                                    active(actor);
                                    PaymentPolicy.require(b.exists(), "Không tìm thấy booking.");
                                    Map<String, Object> data = b.getData();
                                    PaymentPolicy.require(
                                            uid.equals(data.get("guestUid")),
                                            "Không có quyền xem giao dịch.");
                                    if (p.exists()
                                            && Set.of("PAID", "REVIEW_REQUIRED")
                                                    .contains(p.getString("status")))
                                        return p.getData();
                                    PaymentPolicy.canCreate(
                                            data, uid, host, System.currentTimeMillis());
                                    DocumentSnapshot owner =
                                            tx.get(db.collection("users").document(host)).get();
                                    PaymentPolicy.require(
                                            "ACTIVE".equals(owner.getString("status"))
                                                    && "HOST".equals(owner.getString("role")),
                                            "Chủ phòng không còn quyền cho thuê.");
                                    if (p.exists()) return p.getData();
                                    DocumentReference orderRef =
                                            db.collection("paymentOrders")
                                                    .document(Long.toString(candidate));
                                    PaymentPolicy.require(
                                            !tx.get(orderRef).get().exists(),
                                            "Trùng mã thanh toán; vui lòng thử lại.");
                                    Map<String, Object> n = new HashMap<>();
                                    n.put("bookingId", id);
                                    n.put("guestUid", uid);
                                    n.put("ownerUid", host);
                                    n.put("orderCode", candidate);
                                    n.put("amount", PaymentPolicy.amount(data));
                                    n.put("expiresAt", PaymentPolicy.startMillis(data) / 1000);
                                    n.put("status", "CREATING");
                                    n.put("provider", "PAYOS");
                                    n.put("advancePercent", 40);
                                    n.put("createdAt", FieldValue.serverTimestamp());
                                    n.put("updatedAt", FieldValue.serverTimestamp());
                                    tx.create(payment, n);
                                    tx.create(orderRef, Map.of("bookingId", id));
                                    tx.update(
                                            booking,
                                            Map.of(
                                                    "paymentStatus",
                                                    "PENDING",
                                                    "updatedAt",
                                                    FieldValue.serverTimestamp()));
                                    return n;
                                })
                        .get();
        String status = (String) reserved.get("status");
        if (!"CREATING".equals(status)) return publicView(reserved);
        long order = ((Number) reserved.get("orderCode")).longValue();
        // Mất phản hồi create có thể đã tạo QR. Khôi phục cùng orderCode, không sinh mã mới.
        JsonNode created;
        try {
            created =
                    provider.create(
                            order,
                            ((Number) reserved.get("amount")).longValue(),
                            ((Number) reserved.get("expiresAt")).longValue(),
                            base);
        } catch (Exception createError) {
            try {
                created = provider.get(order);
            } catch (Exception lookupError) {
                throw new IllegalStateException("Chưa tạo được QR. Vui lòng thử lại cùng booking.");
            }
        }
        final JsonNode result = created;
        PaymentPolicy.require(
                result.path("orderCode").asLong() == order
                        && result.path("amount").asLong()
                                == ((Number) reserved.get("amount")).longValue(),
                "Phản hồi payOS không khớp booking.");
        db.runTransaction(
                        tx -> {
                            DocumentSnapshot current = tx.get(payment).get();
                            if (!"CREATING".equals(current.getString("status"))) return null;
                            Map<String, Object> update = new HashMap<>();
                            for (String key :
                                    List.of(
                                            "paymentLinkId",
                                            "checkoutUrl",
                                            "qrCode",
                                            "accountName",
                                            "accountNumber",
                                            "bin"))
                                if (result.hasNonNull(key))
                                    update.put(key, result.get(key).asText());
                            if (!update.containsKey("paymentLinkId") && result.hasNonNull("id")) {
                                String recovered = result.path("id").asText();
                                PaymentPolicy.require(
                                        recovered.matches("[A-Za-z0-9-]{1,128}"),
                                        "Mã payOS không hợp lệ.");
                                update.put("paymentLinkId", recovered);
                                update.put("checkoutUrl", "https://pay.payos.vn/web/" + recovered);
                            }
                            update.put("status", "PENDING");
                            update.put("updatedAt", FieldValue.serverTimestamp());
                            tx.update(payment, update);
                            return null;
                        })
                .get();
        // Nếu booking bị hủy trong lúc gọi nhà cung cấp, không trả QR mới cho khách.
        Map<String, Object> latestPayment = payment.get().get().getData();
        if (Set.of("PAID", "REVIEW_REQUIRED").contains((String) latestPayment.get("status")))
            return publicView(latestPayment);
        Map<String, Object> latestBooking = booking.get().get().getData();
        PaymentPolicy.canCreate(latestBooking, uid, host, System.currentTimeMillis());
        return refresh(uid, id);
    }

    public Map<String, Object> refresh(String uid, String id) throws Exception {
        validId(id);
        DocumentSnapshot actor = db.collection("users").document(uid).get().get();
        active(actor);
        DocumentSnapshot p = db.collection("payments").document(id).get().get();
        PaymentPolicy.require(
                p.exists()
                        && (uid.equals(p.getString("guestUid"))
                                || uid.equals(p.getString("ownerUid"))
                                || "ADMIN".equals(actor.getString("role"))),
                "Không có quyền xem giao dịch.");
        reconcile(((Number) p.get("orderCode")).longValue(), null);
        return publicView(db.collection("payments").document(id).get().get().getData());
    }

    public void webhook(byte[] body) throws Exception {
        JsonNode data = provider.verify(body);
        PaymentPolicy.require(
                data.path("orderCode").canConvertToLong(), "Webhook thiếu mã giao dịch.");
        long order = data.path("orderCode").asLong();
        // Sự kiện kiểm tra đăng ký webhook không thuộc booking: xác nhận tiếp nhận, không ghi tiền.
        if (!db.collection("paymentOrders").document(Long.toString(order)).get().get().exists())
            return;
        if (!"00".equals(data.path("code").asText())) return;
        reconcile(order, data);
    }

    private void reconcile(long order, JsonNode event) throws Exception {
        DocumentSnapshot link =
                db.collection("paymentOrders").document(Long.toString(order)).get().get();
        if (!link.exists()) return;
        String id = link.getString("bookingId");
        JsonNode remote = provider.get(order);
        DocumentReference payment = db.collection("payments").document(id);
        DocumentReference booking = db.collection("bookings").document(id);
        db.runTransaction(
                        tx -> {
                            DocumentSnapshot p = tx.get(payment).get(), b = tx.get(booking).get();
                            PaymentPolicy.require(
                                    p.exists() && b.exists(),
                                    "Mất liên kết giao dịch với booking.");
                            DocumentSnapshot owner =
                                    tx.get(db.collection("users").document(p.getString("ownerUid")))
                                            .get();
                            long expected = ((Number) p.get("amount")).longValue();
                            PaymentPolicy.require(
                                    remote.path("orderCode").asLong() == order
                                            && remote.path("amount").asLong() == expected,
                                    "Số tiền payOS không khớp.");
                            String linkId = p.getString("paymentLinkId");
                            if (linkId != null)
                                PaymentPolicy.require(
                                        linkId.equals(
                                                remote.path("id")
                                                        .asText(
                                                                remote.path("paymentLinkId")
                                                                        .asText())),
                                        "Mã liên kết thanh toán không khớp.");
                            if (event != null)
                                PaymentPolicy.require(
                                        event.path("amount").asLong() == expected
                                                && event.path("paymentLinkId")
                                                        .asText()
                                                        .equals(
                                                                remote.path("id")
                                                                        .asText(
                                                                                remote.path(
                                                                                                "paymentLinkId")
                                                                                        .asText())),
                                        "Webhook không khớp giao dịch.");
                            String old = p.getString("status"),
                                    current = remote.path("status").asText();
                            if (Set.of("PAID", "REVIEW_REQUIRED").contains(old)) return null;
                            if ("PAID".equals(current)) {
                                long received = remote.path("amountPaid").asLong();
                                PaymentPolicy.require(
                                        received >= expected
                                                && remote.path("amountRemaining").asLong(-1) == 0,
                                        "payOS chưa xác nhận nhận đủ tiền.");
                                boolean review =
                                        PaymentPolicy.needsReview(b.getData())
                                                || received != expected
                                                || !"ACTIVE".equals(owner.getString("status"));
                                String status = review ? "REVIEW_REQUIRED" : "PAID";
                                Map<String, Object> update = new HashMap<>();
                                update.put("status", status);
                                update.put("paidAmount", received);
                                update.put("paidAt", FieldValue.serverTimestamp());
                                update.put("updatedAt", FieldValue.serverTimestamp());
                                if (event != null)
                                    update.put(
                                            "providerReference", event.path("reference").asText());
                                tx.update(payment, update);
                                Map<String, Object> money = new HashMap<>();
                                money.put("paymentStatus", status);
                                money.put("paidAmount", received);
                                money.put("updatedAt", FieldValue.serverTimestamp());
                                if (review) money.put("refundStatus", "MANUAL_REVIEW");
                                tx.update(booking, money);
                                tx.create(
                                        db.collection("auditLogs")
                                                .document("payment_" + order + "_paid"),
                                        Map.of(
                                                "schemaVersion",
                                                1,
                                                "actorUid",
                                                "payment_backend",
                                                "targetPath",
                                                "bookings/" + id,
                                                "action",
                                                "PAYMENT_RECEIVED",
                                                "before",
                                                Map.of(
                                                        "paymentStatus",
                                                        String.valueOf(b.get("paymentStatus"))),
                                                "after",
                                                Map.of(
                                                        "paymentStatus",
                                                        status,
                                                        "paidAmount",
                                                        received),
                                                "reason",
                                                "payOS server reconciliation",
                                                "createdAt",
                                                FieldValue.serverTimestamp()));
                            } else if (Set.of("CANCELLED", "EXPIRED").contains(current)) {
                                tx.update(
                                        payment,
                                        Map.of(
                                                "status",
                                                current,
                                                "updatedAt",
                                                FieldValue.serverTimestamp()));
                                if ("PENDING".equals(b.getString("paymentStatus")))
                                    tx.update(
                                            booking,
                                            Map.of(
                                                    "paymentStatus",
                                                    "UNPAID",
                                                    "updatedAt",
                                                    FieldValue.serverTimestamp()));
                            }
                            return null;
                        })
                .get();
    }

    private static void active(DocumentSnapshot user) {
        PaymentPolicy.require(
                user.exists() && "ACTIVE".equals(user.getString("status")),
                "Tài khoản không hoạt động.");
    }

    private static void validId(String id) {
        PaymentPolicy.require(
                id != null && id.matches("[A-Za-z0-9_-]{1,120}"), "Mã booking không hợp lệ.");
    }

    // Không trả timestamp đặc thù Firestore hoặc dữ liệu nội bộ của webhook về app.
    static Map<String, Object> publicView(Map<String, Object> value) {
        Map<String, Object> out = new HashMap<>();
        for (String key :
                List.of(
                        "bookingId",
                        "orderCode",
                        "amount",
                        "expiresAt",
                        "status",
                        "qrCode",
                        "checkoutUrl",
                        "accountName",
                        "accountNumber",
                        "bin",
                        "paidAmount")) if (value.containsKey(key)) out.put(key, value.get(key));
        return out;
    }
}
