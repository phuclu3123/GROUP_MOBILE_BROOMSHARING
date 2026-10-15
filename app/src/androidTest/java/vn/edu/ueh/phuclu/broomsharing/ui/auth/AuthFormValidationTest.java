package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.firebase.auth.FirebaseAuth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.repository.FirebaseAuthRepository;

/** Invalid form input must show an error without submitting to Firebase. */
@RunWith(AndroidJUnit4.class)
public class AuthFormValidationTest {
    @Before
    public void signOut() {
        if (new FirebaseAuthRepository(
                        InstrumentationRegistry.getInstrumentation().getTargetContext())
                .isConfigured()) FirebaseAuth.getInstance().signOut();
    }

    @Test
    public void registrationMissingFieldsAndCorrections() {
        try (ActivityScenario<RegisterActivity> ignored =
                ActivityScenario.launch(RegisterActivity.class)) {
            submit();
            error(R.string.auth_name_required);
            enter(R.id.nameInput, "   ");
            submit();
            error(R.string.auth_name_required);
            enter(R.id.nameInput, "Phúc kiểm thử");
            submit();
            error(R.string.auth_username_required);
            enter(R.id.usernameInput, "valid_user");
            submit();
            error(R.string.auth_email_required);
            enter(R.id.emailInput, "valid@example.com");
            submit();
            error(R.string.auth_phone_required);
            enter(R.id.phoneInput, "+12025550123");
            submit();
            error(R.string.auth_password_required);
            enter(R.id.passwordInput, "123456");
            submit();
            error(R.string.auth_confirm_password_required);
            enter(R.id.confirmPasswordInput, "654321");
            submit();
            error(R.string.auth_password_mismatch);
        }
    }

    @Test
    public void registrationRejectsInvalidFormatsAndLengths() {
        try (ActivityScenario<RegisterActivity> ignored =
                ActivityScenario.launch(RegisterActivity.class)) {
            enter(R.id.nameInput, letters(101));
            submit();
            error(R.string.auth_name_too_long);
            enter(R.id.nameInput, "Phuc Test");
            for (String username :
                    new String[] {
                        "ab", letters(25), "phuc lu", "phúc", "phuc/admin", "phuc@example.com"
                    }) {
                enter(R.id.usernameInput, username);
                submit();
                error(R.string.auth_username_invalid);
            }
            enter(R.id.usernameInput, "valid_user");
            for (String email :
                    new String[] {"plainaddress", "phuc@", "@example.com", "phuc lu@example.com"}) {
                enter(R.id.emailInput, email);
                submit();
                error(R.string.auth_email_invalid);
            }
            enter(R.id.emailInput, "valid@example.com");
            for (String phone : new String[]{"abc", "12345", "+0123456789", "090/1234567"}) {
                enter(R.id.phoneInput, phone); submit(); error(R.string.auth_phone_invalid);
            }
            enter(R.id.phoneInput, "+12025550123");
            enter(R.id.passwordInput, "12345");
            submit();
            error(R.string.auth_password_short);
        }
    }

    @Test
    public void loginMissingFieldsAndInvalidFormats() {
        try (ActivityScenario<LoginActivity> ignored =
                ActivityScenario.launch(LoginActivity.class)) {
            submit();
            error(R.string.auth_identifier_required);
            enter(R.id.identifierInput, "  ");
            submit();
            error(R.string.auth_identifier_required);
            for (String identifier : new String[] {"a@", "@example.com", "phuc lu@example.com"}) {
                enter(R.id.identifierInput, identifier);
                submit();
                error(R.string.auth_email_invalid);
            }
            for (String identifier : new String[] {"ab", "phuc/admin", "phuc lu"}) {
                enter(R.id.identifierInput, identifier);
                submit();
                error(R.string.auth_username_invalid);
            }
            enter(R.id.identifierInput, "valid_user");
            submit();
            error(R.string.auth_password_required);
            enter(R.id.identifierInput, "valid@example.com");
            submit();
            error(R.string.auth_password_required);
        }
    }

    private String letters(int count) {
        return new String(new char[count]).replace('\0', 'a');
    }

    private void enter(int id, String value) {
        onView(withId(id)).perform(scrollTo(), replaceText(value));
        closeSoftKeyboard();
    }

    private void submit() {
        onView(withId(R.id.submitButton)).perform(scrollTo(), click());
    }

    private void error(int stringId) {
        onView(withText(stringId)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.submitButton)).check(matches(isEnabled()));
    }
}
