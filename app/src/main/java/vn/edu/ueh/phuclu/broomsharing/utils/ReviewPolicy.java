package vn.edu.ueh.phuclu.broomsharing.utils;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;

// Kiểm tra thuần Java; Repository và Rules vẫn phải kiểm lại booking trên server.
/*
   Điều kiện thuần Java cho nhận xét: đúng Guest, đã xác nhận sử dụng và đã khép lượt thuê.
   Điểm 1..5 và độ dài nội dung được kiểm thêm lần nữa trong Firestore Rules.
*/
public final class ReviewPolicy {
    private ReviewPolicy() {}

    // Chỉ việc sử dụng đã được xác nhận mới mở quyền đánh giá, kể cả kết thúc do sự cố.
    public static boolean canReview(Booking b, long now) {
        return b != null
                && b.usageConfirmedAt != null
                && b.usageConfirmedAt.getTime() <= now
                && ("COMPLETED".equals(b.status) && b.endAt != null && b.endAt.getTime() <= now
                        || "TERMINATED".equals(b.status)
                                && b.closedAt != null
                                && b.closedAt.getTime() <= now);
    }

    public static void validate(Booking booking, String uid, int rating, String comment, long now) {
        BookingPolicy.require(
                booking != null && uid != null && uid.equals(booking.guestUid),
                "Chỉ người thuê trong booking được đánh giá.");
        BookingPolicy.require(
                canReview(booking, now), "Chỉ đánh giá lượt đã xác nhận sử dụng và đã kết thúc.");
        BookingPolicy.require(rating >= 1 && rating <= 5, "Chọn từ 1 đến 5 sao.");
        BookingPolicy.require(
                comment != null && comment.trim().length() >= 10 && comment.trim().length() <= 1000,
                "Nhận xét cần từ 10 đến 1.000 ký tự.");
    }
}
