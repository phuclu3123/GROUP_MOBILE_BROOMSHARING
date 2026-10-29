package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.repository.FirebaseAuthRepository;
import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assume.assumeFalse;

/** Form validation works both with and without a configured Firebase project. */
@RunWith(AndroidJUnit4.class)
public class AuthUiTest {
    @Before
    public void signOutTestDevice() {
        if (new FirebaseAuthRepository(
                        InstrumentationRegistry.getInstrumentation().getTargetContext())
                .isConfigured()) {
            FirebaseAuth.getInstance().signOut();
        }
    }

    @Test
    public void missingFirebaseDoesNotFakeSuccessfulLogin() {
        assumeFalse(
                new FirebaseAuthRepository(
                                InstrumentationRegistry.getInstrumentation().getTargetContext())
                        .isConfigured());
        try (ActivityScenario<LoginActivity> ignored =
                ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.identifierInput)).perform(replaceText("phuc_lu"));
            onView(withId(R.id.passwordInput)).perform(replaceText("123456"));
            closeSoftKeyboard();
            onView(withId(R.id.submitButton)).perform(scrollTo(), click());
            onView(withId(R.id.authMessage)).check(matches(withText(R.string.auth_setup_required)));
            onView(withId(R.id.submitButton)).check(matches(isEnabled()));
        }
    }

    @Test
    public void loginRejectsEmptyIdentifier() {
        try (ActivityScenario<LoginActivity> ignored =
                ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.submitButton)).perform(scrollTo(), click());
            onView(withText(R.string.auth_identifier_required)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void loginRejectsInvalidUsernamePath() {
        try (ActivityScenario<LoginActivity> ignored =
                ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.identifierInput)).perform(replaceText("phuc/admin"));
            closeSoftKeyboard();
            onView(withId(R.id.submitButton)).perform(scrollTo(), click());
            onView(withText(R.string.auth_username_invalid)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void registrationRejectsMismatchedPasswords() {
        try (ActivityScenario<LoginActivity> ignored =
                ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.registerButton)).perform(scrollTo(), click());
            onView(withId(R.id.nameInput)).perform(scrollTo(), replaceText("Phuc Lu"));
            onView(withId(R.id.usernameInput)).perform(scrollTo(), replaceText("phuc_lu"));
            onView(withId(R.id.emailInput)).perform(scrollTo(), replaceText("phuc@example.com"));
            onView(withId(R.id.phoneInput)).perform(scrollTo(), replaceText("+12025550123"));
            onView(withId(R.id.passwordInput)).perform(scrollTo(), replaceText("123456"));
            onView(withId(R.id.confirmPasswordInput)).perform(scrollTo(), replaceText("654321"));
            closeSoftKeyboard();
            onView(withId(R.id.submitButton)).perform(scrollTo(), click());
            onView(withText(R.string.auth_password_mismatch)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void forgotPasswordValidatesEmailBeforeSending() {
        try (ActivityScenario<LoginActivity> ignored =
                ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.forgotButton)).perform(scrollTo(), click());
            onView(withId(R.id.emailInput)).perform(replaceText("invalid-email"));
            closeSoftKeyboard();
            onView(withId(R.id.submitButton)).perform(scrollTo(), click());
            onView(withText(R.string.auth_email_invalid)).check(matches(isDisplayed()));
            onView(withId(R.id.loginButton)).perform(scrollTo(), click());
            onView(withId(R.id.identifierInput)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void recreationKeepsIdentifierButClearsPassword() {
        try (ActivityScenario<LoginActivity> scenario =
                ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.identifierInput)).perform(replaceText("phuc_lu"));
            onView(withId(R.id.passwordInput)).perform(replaceText("123456"));
            closeSoftKeyboard();
            scenario.recreate();
            onView(withId(R.id.identifierInput)).check(matches(withText("phuc_lu")));
            onView(withId(R.id.passwordInput)).check(matches(withText("")));
        }
    }
}
