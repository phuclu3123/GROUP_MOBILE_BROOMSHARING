package vn.edu.ueh.phuclu.broomsharing.model;

/*
   Thông báo được dựng từ booking, hội thoại hoặc báo cáo của tài khoản.
   Đây là dữ liệu hiển thị; chỉ mốc đã đọc được lưu ở users/{uid}/noticeReads.
*/
public class BookingNotice {
    public final String id, title, body, bookingId;
    public boolean forHost;
    public String conversationId = "";
    public boolean reportNotice;
    public boolean unread = true;

    public BookingNotice(String id, String title, String body, String bookingId) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.bookingId = bookingId;
    }
}
