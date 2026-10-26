package vn.edu.ueh.broomsharing.payment;

import com.google.cloud.Timestamp;

import java.util.*;

// Chỉ thu khoản trả trước đã chốt trong snapshot schema 2, không nhận số tiền từ app.
public final class PaymentPolicy {
    private PaymentPolicy() {}

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
                "CONFIRMED".equals(booking.get("status")),
                "Host cần xác nhận booking trước khi thanh toán.");
        require(startMillis(booking) > now, "Đã tới giờ bắt đầu thuê.");
        require(
                !Set.of("PAID", "REVIEW_REQUIRED")
                        .contains(String.valueOf(booking.get("paymentStatus"))),
                "Booking đã ghi nhận tiền; không tạo thêm QR.");
        amount(booking);
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
        return !Set.of("CONFIRMED", "CHECKED_IN", "COMPLETED")
                .contains(String.valueOf(booking.get("status")));
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
