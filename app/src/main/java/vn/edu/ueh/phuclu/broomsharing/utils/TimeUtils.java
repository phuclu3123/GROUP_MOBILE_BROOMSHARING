package vn.edu.ueh.phuclu.broomsharing.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class TimeUtils {
    private TimeUtils() {}

    public static String formatVietnam(Date instant) {
        if (instant == null) return "";
        SimpleDateFormat format =
                new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
        format.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return format.format(instant);
    }
}
