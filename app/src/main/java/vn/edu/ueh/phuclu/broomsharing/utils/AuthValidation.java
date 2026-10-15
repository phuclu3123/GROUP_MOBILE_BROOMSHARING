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

    public static boolean isPasswordLongEnough(String value) {
        // Passwords are never trimmed: whitespace can be part of the credential.
        return value != null && value.length() >= 6;
    }

    public static boolean passwordsMatch(String password, String confirmation) {
        return password != null && password.equals(confirmation);
    }
}
