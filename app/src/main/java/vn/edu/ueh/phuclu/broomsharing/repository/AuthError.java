package vn.edu.ueh.phuclu.broomsharing.repository;



public enum AuthError {
    NOT_CONFIGURED, INVALID_CREDENTIALS, EMAIL_IN_USE, USERNAME_IN_USE,
    WEAK_PASSWORD, NETWORK, TOO_MANY_REQUESTS, DISABLED, OPERATION_DISABLED,
    PROFILE_UNAVAILABLE, PROFILE_MISSING, INCOMPLETE_REGISTRATION,
    SESSION_EXPIRED, ACCOUNT_BLOCKED, INVALID_ROLE, AUTH_CONFIGURATION_MISSING, PHONE_INVALID, UNKNOWN;

    /** Pure mapping so server errors can be tested without a device or cloud account. */
    public static AuthError fromFirebase(String code, String detail) {
        if ("ERROR_CONFIGURATION_NOT_FOUND".equals(code)
                || (detail != null && detail.contains("CONFIGURATION_NOT_FOUND"))) {
            return AUTH_CONFIGURATION_MISSING;
        }
        if (code == null) return UNKNOWN;
        switch (code) {
            case "ERROR_EMAIL_ALREADY_IN_USE": return EMAIL_IN_USE;
            case "ERROR_WEAK_PASSWORD": return WEAK_PASSWORD;
            case "ERROR_USER_DISABLED": return DISABLED;
            case "ERROR_OPERATION_NOT_ALLOWED": return OPERATION_DISABLED;
            case "ERROR_INVALID_EMAIL":
            case "ERROR_WRONG_PASSWORD":
            case "ERROR_USER_NOT_FOUND":
            case "ERROR_INVALID_CREDENTIAL":
            case "ERROR_INVALID_LOGIN_CREDENTIALS": return INVALID_CREDENTIALS;
            default: return UNKNOWN;
        }
    }
}
