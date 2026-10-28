package vn.edu.ueh.broomsharing.payment;

import com.google.cloud.Timestamp;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.time.format.DateTimeFormatter;

import java.util.*;

// Chỉ thu khoản trả trước đã chốt trong snapshot schema 2, không nhận số tiền từ app.
public final class PaymentPolicy {
    private PaymentPolicy() {}

    // Tính khoản trả trước từ snapshot đã chốt; không tin số tiền do Android gửi lên.
    public static long amount(Map<String, Object> booking) {
        require(
                Objects.equals(booking.get("schemaVersion"), 2L)
                        || Objects.equals(booking.get("schemaVersion"), 2),
                "Booking cũ chưa hỗ trợ thanh toán.");
        Object raw = booking.get("priceSnapshot");
        require(raw instanceof Map, "Thiếu snapshot giá.");
        Map<?, ?> price = (Map<?, ?>) raw;
        require(
                "VND".equals(price.get("currency")) && number(price.get("advancePercent")) == 40,
                "Snapshot không thuộc chính sách trả trước 40%.");
        long due = number(price.get("paymentDueAmount"));
        require(
                due > 0
                        && due == number(price.get("depositAmount"))
                        && due == Math.round(number(price.get("rentalAmount")) * .4),
                "Khoản trả trước không khớp snapshot 40%.");
        return due;
    }

    public static void canCreate(Map<String, Object> booking, String uid, String host, long now) {
        require(uid.equals(booking.get("guestUid")), "Chỉ người thuê của booking được thanh toán.");
        require(
                !host.isBlank() && host.equals(booking.get("ownerUid")),
                "Booking này chưa thuộc kênh thanh toán demo đã cấu hình.");
        require(
                instant(booking) ? "PENDING".equals(booking.get("status")) : "CONFIRMED".equals(booking.get("status")),
                "Booking không còn trong trạng thái cho phép thanh toán.");
        require(deadline(booking) > now, "Phiên thanh toán đã hết hạn.");
        require(
                !Set.of("PAID", "REVIEW_REQUIRED")
                        .contains(String.valueOf(booking.get("paymentStatus"))),
                "Booking đã ghi nhận tiền; không tạo thêm QR.");
        amount(booking);
    }

    public static boolean instant(Map<String, Object> booking) {
        return "INSTANT_5M".equals(booking.get("paymentFlow"));
    }

    // Hạn giữ tạm không vượt createdAt + 5 phút hoặc giờ bắt đầu thuê, tùy mốc nào tới trước.
    public static long deadline(Map<String, Object> booking) {
        if (!instant(booking)) return startMillis(booking);
        require(booking.get("createdAt") instanceof Timestamp, "Thiếu thời điểm tạo trên server.");
        return Math.min(startMillis(booking), ((Timestamp) booking.get("createdAt")).toDate().getTime() + 300000L);
    }

    // Chỉ dùng giao dịch đã đọc qua API có chữ ký. Webhook chậm không đồng nghĩa trả tiền muộn.
    // Đối chiếu số tiền và thời điểm ngân hàng nhận, không dùng riêng lúc webhook tới làm căn cứ.
    public static boolean paidInTime(JsonNode remote, long expected, long deadline, long now) {
        if (now < deadline) return true;
        long beforeDeadline = 0;
        for (JsonNode transaction : remote.path("transactions")) {
            String text = transaction.path("transactionDateTime").asText();
            try {
                Instant at;
                try { at = OffsetDateTime.parse(text).toInstant(); }
                catch (java.time.format.DateTimeParseException local) {
                    at = LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
                }
                long amount = transaction.path("amount").asLong();
                if (at.toEpochMilli() < deadline && amount > 0) beforeDeadline = Math.addExact(beforeDeadline, amount);
            } catch (RuntimeException invalid) {
                // Thiếu mốc ngân hàng đáng tin thì giữ ở diện đối soát, không tự suy đoán.
            }
        }
        return beforeDeadline >= expected;
    }

    public static long startMillis(Map<String, Object> booking) {
        Object value = booking.get("startAt");
        require(value instanceof Timestamp, "Booking thiếu thời gian bắt đầu.");
        return ((Timestamp) value).toDate().getTime();
    }

    public static long number(Object value) {
        require(
                value instanceof Number
                        && Double.isFinite(((Number) value).doubleValue())
                        && ((Number) value).doubleValue() == ((Number) value).longValue(),
                "Giá trị tiền phải là số nguyên VND.");
        return ((Number) value).longValue();
    }

    // Tiền đến muộn vẫn được ghi nhận, nhưng không phục hồi booking hoặc lịch đã nhả.
    public static boolean needsReview(Map<String, Object> booking) {
        return !(instant(booking) && "PENDING".equals(booking.get("status"))) && !Set.of("CONFIRMED", "CHECKED_IN", "COMPLETED")
                .contains(String.valueOf(booking.get("status")));
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
