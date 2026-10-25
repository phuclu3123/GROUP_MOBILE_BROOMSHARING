package vn.edu.ueh.phuclu.broomsharing.model;



/*
    Số liệu tổng hợp cho dashboard Host từ phòng và booking hiện có.
    Không lưu thành collection riêng và không biểu diễn doanh thu đã thanh toán.
 */
public class HostStats {
    public int totalRooms, pendingRooms, activeRooms, bookingRequests;
}
