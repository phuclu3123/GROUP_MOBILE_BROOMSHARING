package vn.edu.ueh.phuclu.broomsharing.utils;

import vn.edu.ueh.phuclu.broomsharing.model.Room;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Date;
import java.util.Locale;

/*
   Chuyển mã trạng thái và giá trị tiền thành nhãn hiển thị.
   Nhãn giao diện không thay đổi trạng thái nghiệp vụ hay xác nhận giao dịch tiền.
*/
public class FormatUtils {
    public static String verificationSummary(Room room) {
        return "Địa điểm: "
                + ("ADMIN_REVIEWED".equals(room.getLocationVerification())
                        ? "đã kiểm tra bằng chứng nội bộ"
                        : "chưa kiểm tra nội bộ")
                + "\nẢnh phòng: "
                + ("ADMIN_REVIEWED".equals(room.getPhotosVerification())
                        ? "đã kiểm tra bằng chứng nội bộ"
                        : "chưa kiểm tra nội bộ")
                + "\nKhông phải xác minh từ đối tác hoặc cam kết đã kiểm tra thực địa.";
    }

    public static String bookingStatus(vn.edu.ueh.phuclu.broomsharing.model.Booking booking) {
        if ("REVIEW_REQUIRED".equals(booking.paymentStatus)) return "Đã nhận tiền • Cần đối soát";
        if (booking.isInstantPayment() && "PENDING".equals(booking.status))
            return "Chờ thanh toán • Tạm giữ 5 phút";
        if (booking.isInstantPayment() && "EXPIRED".equals(booking.status))
            return "Đặt chỗ không thành công • Hết hạn thanh toán";
        return bookingStatus(booking.status);
    }

    public static String bookingStatus(String value) {
        if ("EXPIRED".equals(value)) return "Hết hạn chờ duyệt";
        if ("PENDING".equals(value) || "REQUESTED".equals(value)) return "Chờ duyệt • Đang giữ chỗ";
        if ("CONFIRMED".equals(value)) return "Đã xác nhận";
        if ("CANCELLED".equals(value)) return "Đã hủy";
        if ("REJECTED".equals(value)) return "Đã từ chối";
        if ("COMPLETED".equals(value)) return "Hoàn tất";
        if ("CHECKED_IN".equals(value)) return "Đã ghi nhận vào phòng";
        if ("NO_SHOW".equals(value)) return "Khách không đến";
        if ("TERMINATED".equals(value)) return "Kết thúc do sự cố";
        return "Trạng thái: " + value;
    }

    public static String reportStatus(String value) {
        if ("OPEN".equals(value)) return "Đang chờ xử lý";
        if ("RESOLVED".equals(value)) return "Đã giải quyết";
        if ("DISMISSED".equals(value)) return "Không xác nhận vi phạm";
        return "Chưa xác định";
    }

    public static String paymentStatus(String value) {
        if ("UNAVAILABLE".equals(value)) return "Chưa khởi tạo thanh toán • Chưa ghi nhận tiền";
        if ("DEMO_PENDING".equals(value)) return "Cọc demo • Chưa thanh toán";
        if ("UNPAID".equals(value)) return "Chưa thanh toán";
        if ("PENDING".equals(value)) return "Chờ xử lý";
        if ("PAID".equals(value)) return "Đã nhận khoản trả trước 40% qua payOS";
        if ("REVIEW_REQUIRED".equals(value)) return "Đã ghi nhận tiền, cần đối soát; chưa hoàn tiền";
        return value == null ? "Chưa có dữ liệu" : value;
    }

    public static String formatCurrency(double amount) {
        return new DecimalFormat(
                        "#,##0 'đ'",
                        DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN")))
                .format(amount);
    }

    public static String formatDate(Date date) {
        if (date == null) return "Vừa xong";
        return TimeUtils.formatVietnam(date);
    }

    public static String reviewStatus(String status) {
        if ("PENDING".equals(status) || "PENDING_REVIEW".equals(status)) return "Chờ duyệt";
        if ("APPROVED".equals(status)) return "Đã duyệt";
        if ("REJECTED".equals(status)) return "Từ chối";
        return "Bản nháp";
    }
}
