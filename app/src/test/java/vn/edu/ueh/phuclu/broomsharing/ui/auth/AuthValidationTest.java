package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.utils.AuthValidation;
import java.util.Locale;
import static org.junit.Assert.*;

public class AuthValidationTest {
    @Test public void typedPhoneDoesNotBecomeAVerifiedIdentity() {
        vn.edu.ueh.phuclu.broomsharing.model.UserProfile profile =
                new vn.edu.ueh.phuclu.broomsharing.model.UserProfile(
                        "uid", "Name", "username", "user@example.com", "+84912345678");
        assertEquals("+84912345678", profile.phone);
        assertFalse(profile.phoneVerified);
        assertEquals("", new vn.edu.ueh.phuclu.broomsharing.model.UserProfile().phone);
    }
    @Test public void localAndInternationalPhoneFormatsAreNormalized() {
        assertEquals("+84912345678", AuthValidation.normalizePhone("0912 345 678"));
        assertEquals("+84912345678", AuthValidation.normalizePhone("+84 912-345-678"));
        assertEquals("+12025550123", AuthValidation.normalizePhone("+1 (202) 555-0123"));
    }

    @Test public void invalidPhoneIsNotSilentlyConvertedToAValidOne() {
        for (String phone : new String[]{"", "   ", "12345", "090/1234567", "abc0912345678", "+0123456789", "+1234567890123456"}) {
            assertEquals("", AuthValidation.normalizePhone(phone));
        }
        assertEquals("", AuthValidation.normalizePhone(null));
    }

    @Test public void usernameLengthBoundaries() {
        assertFalse(AuthValidation.isUsernameValid("ab"));
        assertTrue(AuthValidation.isUsernameValid("abc"));
        assertTrue(AuthValidation.isUsernameValid("a".repeat(24)));
        assertFalse(AuthValidation.isUsernameValid("a".repeat(25)));
    }

    @Test public void usernameCannotBeEmailOrDocumentPath() {
        assertFalse(AuthValidation.isUsernameValid("phuc@example.com"));
        assertFalse(AuthValidation.isUsernameValid("phuc/admin"));
        assertFalse(AuthValidation.isUsernameValid("phúc"));
        assertFalse(AuthValidation.isUsernameValid("phuc lu"));
        assertFalse(AuthValidation.isUsernameValid(""));
        assertFalse(AuthValidation.isUsernameValid(null));
        assertTrue(AuthValidation.isUsernameValid("phuc_lu89"));
    }

    @Test public void sameUsernameHasSameLookupKey() {
        assertEquals(AuthValidation.normalizeUsername(" PHUC_LU "), AuthValidation.normalizeUsername("phuc_lu"));
    }

    @Test public void usernameCaseIsIndependentOfDeviceLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("linh", AuthValidation.normalizeUsername("LINH"));
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test public void registrationChecksPasswordLengthWithoutChangingCredential() {
        assertFalse(AuthValidation.isPasswordLongEnough(null));
        assertFalse(AuthValidation.isPasswordLongEnough("12345"));
        assertTrue(AuthValidation.isPasswordLongEnough("123456"));
        assertTrue(AuthValidation.isPasswordLongEnough(" pass "));
        assertFalse(AuthValidation.passwordsMatch(" pass ", "pass"));
        assertTrue(AuthValidation.passwordsMatch(" pass ", " pass "));
    }

    @Test public void hostInheritsGuestCapability() {
        assertTrue(UserRole.HOST.canRent());
        assertTrue(UserRole.HOST.canHost());
        assertTrue(UserRole.GUEST.canRent());
        assertFalse(UserRole.GUEST.canHost());
        assertFalse(UserRole.ADMIN.canHost());
    }
}
