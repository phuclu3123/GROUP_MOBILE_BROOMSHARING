package vn.edu.ueh.phuclu.broomsharing.utils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FormatUtils {
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,### VNĐ");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public static String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(amount);
    }

    public static String formatDate(Date date) {
        if (date == null) return "Vừa xong";
        return DATE_FORMAT.format(date);
    }
}
