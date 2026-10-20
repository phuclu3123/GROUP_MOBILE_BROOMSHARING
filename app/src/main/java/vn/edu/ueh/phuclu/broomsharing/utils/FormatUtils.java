package vn.edu.ueh.phuclu.broomsharing.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Date;
import java.util.Locale;

public class FormatUtils {
    public static String verificationSummary(vn.edu.ueh.phuclu.broomsharing.model.Room room) {
        return "Địa điểm: " + ("ADMIN_REVIEWED".equals(room.getLocationVerification())
                ? "đã kiểm tra bằng chứng nội bộ" : "chưa kiểm tra nội bộ")
                + "\nẢnh phòng: " + ("ADMIN_REVIEWED".equals(room.getPhotosVerification())
                ? "đã kiểm tra bằng chứng nội bộ" : "chưa kiểm tra nội bộ")
                + "\nKhông phải xác minh từ đối tác hoặc cam kết đã kiểm tra thực địa.";
    }
    public static String bookingStatus(String value) {
        if ("PENDING".equals(value) || "REQUESTED".equals(value)) return "Chờ duyệt • Đang giữ chỗ";
        if ("CONFIRMED".equals(value)) return "Đã xác nhận";
        if ("CANCELLED".equals(value)) return "Đã hủy";
        if ("REJECTED".equals(value)) return "Đã từ chối";
        if ("COMPLETED".equals(value)) return "Hoàn tất";
        if ("CHECKED_IN".equals(value)) return "Đã ghi nhận vào phòng (demo)";
        return "Trạng thái: " + value;
    }
    public static String paymentStatus(String value) {
        if ("UNAVAILABLE".equals(value)) return "Thanh toán đang phát triển • Chưa thu tiền";
        if ("DEMO_PENDING".equals(value)) return "Cọc demo • Chưa thanh toán";
        if ("UNPAID".equals(value)) return "Chưa thanh toán";
        if ("PENDING".equals(value)) return "Chờ xử lý";
        if ("PAID".equals(value)) return "Đã thanh toán theo dữ liệu hệ thống";
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
