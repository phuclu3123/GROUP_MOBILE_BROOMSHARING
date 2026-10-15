package vn.edu.ueh.phuclu.broomsharing.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Date;
import java.util.Locale;

public class FormatUtils {
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
