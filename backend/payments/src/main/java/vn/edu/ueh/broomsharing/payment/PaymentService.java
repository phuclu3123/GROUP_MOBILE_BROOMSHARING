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

    // Khóa mã đơn trong Firestore trước, gọi nhà cung cấp ngoài transaction, rồi lưu kết quả.
    // Retry dùng lại orderCode và hạn cũ; không tạo một lần thu mới cho cùng booking.
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
                                            && Set.of("PAID", "REVIEW_REQUIRED", "CANCELLED", "EXPIRED")
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
                                    if (PaymentPolicy.instant(data)) {
                                        BookingHold hold = new BookingHold(db, tx, b);
                                        PaymentPolicy.require(hold.matches() && hold.roomOpen(),
                                                "Lịch hoặc phòng đã thay đổi; chưa tạo QR. Vui lòng kiểm tra lại.");
                                    }
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
                                    n.put("expiresAt", PaymentPolicy.deadline(data) / 1000);
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
        if (mustClose(latestBooking, latestPayment, true)) return refresh(uid, id);
        PaymentPolicy.canCreate(latestBooking, uid, host, System.currentTimeMillis());
        return refresh(uid, id);
    }

    // Đối soát chủ động khi app mở lại hoặc webhook bị lỡ; kiểm UID trước khi trả dữ liệu giao dịch.
    public Map<String, Object> refresh(String uid, String id) throws Exception {
        validId(id);
        DocumentSnapshot actor = db.collection("users").document(uid).get().get();
        active(actor);
        DocumentSnapshot p = db.collection("payments").document(id).get().get();
        if (!p.exists()) {
            DocumentSnapshot b = db.collection("bookings").document(id).get().get();
            PaymentPolicy.require(b.exists() && (uid.equals(b.getString("guestUid"))
                    || uid.equals(b.getString("ownerUid")) || "ADMIN".equals(actor.getString("role"))),
                    "Không có quyền xem giao dịch.");
            PaymentPolicy.require(host.equals(b.getString("ownerUid")),
                    "Phòng này chưa thuộc Host payOS demo. Chưa tạo giao dịch thanh toán.");
            throw new IllegalStateException("Chưa khởi tạo giao dịch thanh toán. Bạn có thể thử tải lại QR nếu phiên còn hạn.");
        }
        PaymentPolicy.require(
                p.exists()
                        && (uid.equals(p.getString("guestUid"))
                                || uid.equals(p.getString("ownerUid"))
                                || "ADMIN".equals(actor.getString("role"))),
                "Không có quyền xem giao dịch.");
        reconcile(((Number) p.get("orderCode")).longValue(), null);
        return publicView(db.collection("payments").document(id).get().get().getData());
    }

    // SDK payOS xác thực chữ ký trước khi ánh xạ orderCode về booking và áp dụng kết quả tiền.
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

    // Server tiếp tục khép QR dù người dùng đã đóng app. Mỗi lượt có giới hạn và cursor.
    private DocumentSnapshot pendingCursor, holdCursor;

    // Tác vụ định kỳ xử lý một trang giao dịch đang chờ; lỗi một đơn không chặn các đơn còn lại.
    public synchronized void reconcilePending() throws Exception {
        // App có thể mất mạng sau khi giữ lịch và trước khi gọi tạo QR.
        expireUnstartedHolds();
        Query query = db.collection("payments")
                .whereIn("status", List.of("CREATING", "PENDING"))
                .orderBy(FieldPath.documentId()).limit(50);
        if (pendingCursor != null) query = query.startAfter(pendingCursor);
        List<QueryDocumentSnapshot> rows = query.get().get().getDocuments();
        pendingCursor = rows.size() == 50 ? rows.get(rows.size() - 1) : null;
        for (DocumentSnapshot row : rows) {
            try {
                reconcile(((Number) row.get("orderCode")).longValue(), null);
            } catch (Exception error) {
                // Giữ đơn để thử lại; không ghi log payload ngân hàng hoặc secret.
                System.err.println("Pending payment sync: " + error.getClass().getSimpleName());
            }
        }
    }

    // Lọc flow ngay trên Firestore để vòng quét không đọc lại các đơn PENDING lịch sử mỗi 15 giây.
    // Hai điều kiện equality dùng index đơn được Firestore ghép; giữ cursor theo document ID.
    Query pendingInstantHolds() {
        return db.collection("bookings").whereEqualTo("status", "PENDING")
                .whereEqualTo("paymentFlow", "INSTANT_5M")
                .orderBy(FieldPath.documentId()).limit(50);
    }

    // Nhả các lượt giữ tạm đã hết hạn nhưng chưa tạo được giao dịch, giữ booking để tra cứu lịch sử.
    private void expireUnstartedHolds() throws Exception {
        Query query = pendingInstantHolds();
        if (holdCursor != null) query = query.startAfter(holdCursor);
        List<QueryDocumentSnapshot> rows = query.get().get().getDocuments();
        holdCursor = rows.size() == 50 ? rows.get(rows.size() - 1) : null;
        for (DocumentSnapshot row : rows) {
            if (!PaymentPolicy.instant(row.getData()) || PaymentPolicy.deadline(row.getData()) > System.currentTimeMillis()) continue;
            try {
                db.runTransaction(tx -> {
                    DocumentSnapshot b = tx.get(row.getReference()).get();
                    DocumentSnapshot p = tx.get(db.collection("payments").document(row.getId())).get();
                    if (!"PENDING".equals(b.getString("status")) || p.exists()
                            || PaymentPolicy.deadline(b.getData()) > System.currentTimeMillis()) return null;
                    new BookingHold(db, tx, b).finish(db, tx, false);
                    return null;
                }).get();
            } catch (Exception error) { System.err.println("Hold expiry: " + error.getClass().getSimpleName()); }
        }
    }

    private static boolean mustClose(Map<String, Object> booking, Map<String, Object> payment,
                                     boolean ownerActive) {
        return !ownerActive || PaymentPolicy.needsReview(booking)
                || ((Number) payment.get("expiresAt")).longValue() <= System.currentTimeMillis() / 1000;
    }

    private void reconcile(long order, JsonNode event) throws Exception {
        DocumentSnapshot link =
                db.collection("paymentOrders").document(Long.toString(order)).get().get();
        if (!link.exists()) return;
        String id = link.getString("bookingId");
        DocumentReference payment = db.collection("payments").document(id);
        DocumentReference booking = db.collection("bookings").document(id);
        DocumentSnapshot before = payment.get().get(), beforeBooking = booking.get().get();
        PaymentPolicy.require(before.exists() && beforeBooking.exists(), "Mất liên kết giao dịch.");
        if (Set.of("PAID", "REVIEW_REQUIRED").contains(before.getString("status"))) return;
        DocumentSnapshot beforeOwner = db.collection("users").document(before.getString("ownerUid")).get().get();
        JsonNode observed;
        try {
            observed = provider.get(order);
        } catch (Exception lookupError) {
            // Timeout không chứng minh chưa có giao dịch. Chỉ nhả khi payOS xác nhận không tồn tại,
            // phiên đã hết hạn và lần tạo chưa từng trả một link hợp lệ.
            if (!provider.isMissing(lookupError) || !"CREATING".equals(before.getString("status"))
                    || ((Number) before.get("expiresAt")).longValue() > System.currentTimeMillis() / 1000)
                throw lookupError;
            observed = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                    .put("orderCode", order).put("amount", ((Number) before.get("amount")).longValue())
                    .put("status", "EXPIRED");
        }
        if ("PENDING".equals(observed.path("status").asText())
                && mustClose(beforeBooking.getData(), before.getData(),
                             "ACTIVE".equals(beforeOwner.getString("status")) && "HOST".equals(beforeOwner.getString("role")))) {
            try {
                provider.cancel(order, "BroomSharing: booking closed or payment expired");
            } catch (Exception race) {
                // Thanh toán có thể hoàn tất ngay lúc hủy; đọc lại trước khi quyết định.
                JsonNode latest = provider.get(order);
                if ("PENDING".equals(latest.path("status").asText())) throw race;
            }
            observed = provider.get(order);
        }
        final JsonNode remote = observed;
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
                                        (PaymentPolicy.instant(b.getData())
                                                ? event.path("amount").asLong() > 0
                                                : event.path("amount").asLong() == expected)
                                                && event.path("paymentLinkId")
                                                        .asText()
                                                        .equals(
                                                                remote.path("id")
                                                                        .asText(
                                                                                remote.path(
                                                                                                "paymentLinkId")
                                                                                        .asText())),
                                        "Webhook không khớp giao dịch.");
                            BookingHold hold = PaymentPolicy.instant(b.getData()) && "PENDING".equals(b.getString("status"))
                                    ? new BookingHold(db, tx, b) : null;
                            String old = p.getString("status"),
                                    current = remote.path("status").asText();
                            if (Set.of("PAID", "REVIEW_REQUIRED").contains(old)) return null;
                            boolean instant = PaymentPolicy.instant(b.getData());
                            boolean closedWithMoney = instant && Set.of("CANCELLED", "EXPIRED").contains(current)
                                    && remote.path("amountPaid").asLong() > 0;
                            if ("PAID".equals(current) || closedWithMoney) {
                                long received = remote.path("amountPaid").asLong();
                                PaymentPolicy.require(
                                        instant ? received > 0 : received >= expected
                                                && remote.path("amountRemaining").asLong(-1) == 0,
                                        "payOS chưa xác nhận nhận đủ tiền.");
                                boolean review =
                                        PaymentPolicy.needsReview(b.getData())
                                                || received != expected
                                                || !"PAID".equals(current)
                                                || remote.path("amountRemaining").asLong(-1) != 0
                                                || (hold != null && (!hold.matches() || !hold.roomOpen()
                                                    || !PaymentPolicy.paidInTime(remote, expected,
                                                        ((Number) p.get("expiresAt")).longValue() * 1000,
                                                        System.currentTimeMillis())))
                                                || !"ACTIVE".equals(owner.getString("status"))
                                                || !"HOST".equals(owner.getString("role"));
                                String status = review ? "REVIEW_REQUIRED" : "PAID";
                                if (hold != null && hold.matches()) hold.finish(db, tx, !review);
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
                                // Chỉ nhả đúng khóa của booking đang chờ; không đụng lịch sử đã xác nhận.
                                if (hold != null) hold.finish(db, tx, false);
                                String terminal = "CANCELLED".equals(current)
                                                && ((Number) p.get("expiresAt")).longValue() <= System.currentTimeMillis() / 1000
                                        ? "EXPIRED" : current;
                                tx.update(
                                        payment,
                                        Map.of(
                                                "status",
                                                terminal,
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
