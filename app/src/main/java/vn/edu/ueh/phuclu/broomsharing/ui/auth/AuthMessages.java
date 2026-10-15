package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.repository.AuthError;

public final class AuthMessages {
    private AuthMessages() { }

    public static int resource(AuthError error) {
        switch (error) {
            case NOT_CONFIGURED: return R.string.auth_setup_required;
            case INVALID_CREDENTIALS: return R.string.auth_invalid_credentials;
            case EMAIL_IN_USE: return R.string.auth_email_in_use;
            case USERNAME_IN_USE: return R.string.auth_username_in_use;
            case WEAK_PASSWORD: return R.string.auth_weak_password;
            case NETWORK: return R.string.auth_network_error;
            case TOO_MANY_REQUESTS: return R.string.auth_rate_limit;
            case DISABLED: return R.string.auth_disabled;
            case OPERATION_DISABLED: return R.string.auth_operation_disabled;
            case PROFILE_UNAVAILABLE: return R.string.auth_profile_error;
            case PROFILE_MISSING: return R.string.auth_profile_missing;
            case INCOMPLETE_REGISTRATION: return R.string.auth_incomplete_registration;
            case SESSION_EXPIRED: return R.string.auth_session_expired;
            case ACCOUNT_BLOCKED: return R.string.auth_account_blocked;
            case INVALID_ROLE: return R.string.auth_role_invalid;
            default: return R.string.auth_unknown_error;
        }
    }
}
