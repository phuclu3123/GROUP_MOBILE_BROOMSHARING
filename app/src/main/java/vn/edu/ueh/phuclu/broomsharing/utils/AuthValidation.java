package vn.edu.ueh.phuclu.broomsharing.utils;

import java.util.Locale;

public final class AuthValidation {
    private AuthValidation() { }

    public static boolean isUsernameValid(String value) {
        return value != null && value.trim().matches("[a-zA-Z0-9_]{3,24}");
    }

    public static String normalizeUsername(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** Accept a 10-digit VN number or an international number with country code. */
    public static String normalizePhone(String value) {
        if (value == null || !value.trim().matches("[+0-9 ().-]+")) return "";
        String digits = value.trim().replaceAll("[ ().-]", "");
        if (digits.matches("0[0-9]{9}")) digits = "+84" + digits.substring(1);
        return digits.matches("\\+[1-9][0-9]{7,14}") ? digits : "";
    }

    public static boolean isPasswordLongEnough(String value) {
        // Passwords are never trimmed: whitespace can be part of the credential.
        return value != null && value.length() >= 6;
    }

    public static boolean passwordsMatch(String password, String confirmation) {
        return password != null && password.equals(confirmation);
    }
}
