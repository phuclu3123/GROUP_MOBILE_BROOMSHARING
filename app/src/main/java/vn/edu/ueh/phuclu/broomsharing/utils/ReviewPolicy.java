package vn.edu.ueh.phuclu.broomsharing.utils;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;

// Kiểm tra thuần Java; Repository và Rules vẫn phải kiểm lại booking trên server.
/*
   Điều kiện thuần Java cho nhận xét: đúng Guest, booking hoàn tất và đã qua giờ kết thúc.
   Điểm 1..5 và độ dài nội dung được kiểm thêm lần nữa trong Firestore Rules.
*/
public final class ReviewPolicy {
    private ReviewPolicy() {}

    public static void validate(Booking booking, String uid, int rating, String comment, long now) {
        BookingPolicy.require(
                booking != null && uid != null && uid.equals(booking.guestUid),
                "Chỉ người thuê trong booking được đánh giá.");
        BookingPolicy.require(
                "COMPLETED".equals(booking.status)
                        && booking.endAt != null
                        && booking.endAt.getTime() <= now,
                "Chỉ đánh giá sau khi lượt thuê đã hoàn tất.");
        BookingPolicy.require(rating >= 1 && rating <= 5, "Chọn từ 1 đến 5 sao.");
        BookingPolicy.require(
                comment != null && comment.trim().length() >= 10 && comment.trim().length() <= 1000,
                "Nhận xét cần từ 10 đến 1.000 ký tự.");
    }
}
