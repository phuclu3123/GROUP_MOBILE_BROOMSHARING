package vn.edu.ueh.phuclu.broomsharing.utils;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

public final class UiForms {
    private UiForms() {}

    public static String text(EditText input) {
        return input.getText().toString().trim();
    }

    public static double number(EditText input) {
        String value = text(input);
        if (value.isEmpty()) return 0;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            input.setError("Nhập số, dùng dấu chấm cho phần thập phân; không nhập đơn vị.");
            input.requestFocus();
            throw e;
        }
    }

    public static void enabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) enabled(group.getChildAt(i), enabled);
        }
    }
}
