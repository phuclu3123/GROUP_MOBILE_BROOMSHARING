package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.repository.AuthError;
import static org.junit.Assert.assertEquals;

/*
   Kiểm thử configurationNotFoundInsideInternalErrorIsActionable, providerDisabledIsSeparateFromMissingConfiguration, credentialAndCollisionMessagesRemainSpecific.
   Dữ liệu được tạo trong test; kết quả này không thay cho nghiệm thu thao tác trên thiết bị hoặc cloud.
*/
public class AuthErrorMappingTest {
    @Test public void configurationNotFoundInsideInternalErrorIsActionable() {
        assertEquals(AuthError.AUTH_CONFIGURATION_MISSING,
                AuthError.fromFirebase("ERROR_INTERNAL_ERROR",
                        "An internal error has occurred. [ CONFIGURATION_NOT_FOUND ]"));
        assertEquals(AuthError.AUTH_CONFIGURATION_MISSING,
                AuthError.fromFirebase(null, "CONFIGURATION_NOT_FOUND"));
    }
    @Test public void providerDisabledIsSeparateFromMissingConfiguration() {
        assertEquals(AuthError.OPERATION_DISABLED,
                AuthError.fromFirebase("ERROR_OPERATION_NOT_ALLOWED", null));
    }
    @Test public void credentialAndCollisionMessagesRemainSpecific() {
        assertEquals(AuthError.EMAIL_IN_USE, AuthError.fromFirebase("ERROR_EMAIL_ALREADY_IN_USE", null));
        assertEquals(AuthError.INVALID_CREDENTIALS, AuthError.fromFirebase("ERROR_INVALID_LOGIN_CREDENTIALS", null));
        assertEquals(AuthError.WEAK_PASSWORD, AuthError.fromFirebase("ERROR_WEAK_PASSWORD", null));
    }
    @Test public void unknownErrorsAreNotMisreportedAsConfigurationIssues() {
        assertEquals(AuthError.UNKNOWN, AuthError.fromFirebase("ERROR_INTERNAL_ERROR", "Other error"));
        assertEquals(AuthError.UNKNOWN, AuthError.fromFirebase(null, null));
    }
}
