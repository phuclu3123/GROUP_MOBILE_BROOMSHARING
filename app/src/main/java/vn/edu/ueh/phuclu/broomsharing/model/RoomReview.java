package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

// Một đánh giá gắn với một booking đã hoàn tất; ID tài liệu chính là bookingId.
/*
   Nhận xét sau lượt thuê, dùng bookingId làm document ID để chống gửi nhiều lần.
   Chỉ lưu tên hiển thị, điểm và nội dung; không sao chép liên hệ riêng của Guest.
*/
public class RoomReview {
    public String bookingId = "", roomId = "", guestUid = "", guestName = "", comment = "";
    public int rating;
    @ServerTimestamp public Date createdAt;

    public RoomReview() {}
}
