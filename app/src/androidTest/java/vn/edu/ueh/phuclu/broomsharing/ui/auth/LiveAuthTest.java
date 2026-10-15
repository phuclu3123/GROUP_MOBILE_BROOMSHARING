package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import org.junit.Test;
import org.junit.runner.RunWith;

import vn.edu.ueh.phuclu.broomsharing.MainActivity;
import vn.edu.ueh.phuclu.broomsharing.ProfileActivity;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseConfig;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Opt-in real Firebase test. Only creates and removes its own random test account. */
@RunWith(AndroidJUnit4.class)
public class LiveAuthTest {
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String username, email, password, createdUid;

    @Test
    public void realRegistrationUniquenessLoginAndLogout() throws Exception {
        assumeTrue(
                "Use -e runLiveAuth true to create a temporary cloud test account",
                "true".equals(InstrumentationRegistry.getArguments().getString("runLiveAuth")));
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance(FirebaseConfig.FIRESTORE_DATABASE_ID);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        username = "authtest_" + suffix;
        email = username + "@example.com";
        password = "Test_" + suffix + "!7";
        auth.signOut();
        try {
            try (ActivityScenario<LoginActivity> ignored =
                    ActivityScenario.launch(LoginActivity.class)) {
                onView(withId(R.id.registerButton)).perform(scrollTo(), click());
                fillRegistration(username, email);
                submit();
                awaitActivity(MainActivity.class);
                createdUid = auth.getCurrentUser().getUid();
                DocumentSnapshot user =
                        Tasks.await(
                                db.collection("users").document(createdUid).get(Source.SERVER),
                                30,
                                TimeUnit.SECONDS);
                assertEquals("GUEST", user.getString("role"));
                assertEquals(username, user.getString("username"));
                assertEquals(email, user.getString("email"));
                assertEquals("+12025550123", user.getString("phone"));
                assertFalse(Boolean.TRUE.equals(user.getBoolean("phoneVerified")));
                assertFalse("Never store passwords in Firestore", user.contains("password"));
                DocumentSnapshot alias =
                        Tasks.await(
                                db.collection("usernames").document(username).get(Source.SERVER),
                                30,
                                TimeUnit.SECONDS);
                assertEquals(createdUid, alias.getString("uid"));
                logoutThroughUi();

                // The same normalized username cannot be claimed by another email.
                onView(withId(R.id.registerButton)).perform(scrollTo(), click());
                fillRegistration(" " + username.toUpperCase(Locale.ROOT) + " ", "other_" + email);
                submit();
                awaitMessage(R.string.auth_username_in_use);
                assertNull(auth.getCurrentUser());

                // A fresh username cannot claim an existing email (including different case).
                enter(R.id.usernameInput, username + "_x");
                enter(R.id.emailInput, email.toUpperCase(Locale.ROOT));
                submit();
                awaitMessage(R.string.auth_email_in_use);
                assertFalse(
                        Tasks.await(
                                        db.collection("usernames")
                                                .document(username + "_x")
                                                .get(Source.SERVER),
                                        30,
                                        TimeUnit.SECONDS)
                                .exists());
                onView(withId(R.id.loginButton)).perform(scrollTo(), click());

                loginExpectError(email, "WrongPassword!7");
                loginExpectError(username, "WrongPassword!7");
                loginExpectError("missing_" + suffix, password);
                loginExpectError("missing_" + email, password);

                login(email, password);
                awaitActivity(MainActivity.class);
                assertEquals(createdUid, auth.getCurrentUser().getUid());
                logoutThroughUi();
                login(" " + username.toUpperCase(Locale.ROOT) + " ", password);
                awaitActivity(MainActivity.class);
                assertEquals(createdUid, auth.getCurrentUser().getUid());

                // Recreating the entry screen must restore the existing authenticated session.
                Intent relaunch =
                        new Intent(
                                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                                LoginActivity.class);
                relaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .startActivity(relaunch);
                awaitActivity(MainActivity.class);
                logoutThroughUi();

                // Protected home must not be reachable again after sign-out.
                Intent protectedPage =
                        new Intent(
                                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                                MainActivity.class);
                protectedPage.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .startActivity(protectedPage);
                awaitActivity(LoginActivity.class);
                assertNull(auth.getCurrentUser());
            }
        } finally {
            cleanupOwnAccount();
        }
    }

    private void fillRegistration(String name, String address) {
        enter(R.id.nameInput, "Auth integration test");
        enter(R.id.usernameInput, name);
        enter(R.id.emailInput, address);
        enter(R.id.phoneInput, "+12025550123");
        enter(R.id.passwordInput, password);
        enter(R.id.confirmPasswordInput, password);
    }

    private void enter(int id, String value) {
        onView(withId(id)).perform(scrollTo(), replaceText(value));
        closeSoftKeyboard();
    }

    private void submit() {
        onView(withId(R.id.submitButton)).perform(scrollTo(), click());
    }

    private void login(String identifier, String credential) {
        enter(R.id.identifierInput, identifier);
        enter(R.id.passwordInput, credential);
        submit();
    }

    private void loginExpectError(String identifier, String credential) {
        login(identifier, credential);
        awaitMessage(R.string.auth_invalid_credentials);
        assertNull("Failed login must not open a session", auth.getCurrentUser());
    }

    private void logoutThroughUi() {
        onView(withId(R.id.nav_profile)).perform(click());
        awaitActivity(ProfileActivity.class);
        onView(withId(R.id.profileSignOut)).perform(scrollTo(), click());
        awaitActivity(LoginActivity.class);
        assertNull(auth.getCurrentUser());
    }

    private Activity resumed() {
        Activity[] result = new Activity[1];
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            for (Activity a :
                                    ActivityLifecycleMonitorRegistry.getInstance()
                                            .getActivitiesInStage(Stage.RESUMED)) result[0] = a;
                        });
        return result[0];
    }

    private void awaitActivity(Class<?> expected) {
        long end = SystemClock.elapsedRealtime() + 45000;
        while (SystemClock.elapsedRealtime() < end) {
            Activity a = resumed();
            boolean[] ready = {false};
            if (a != null && expected.isInstance(a)) {
                InstrumentationRegistry.getInstrumentation()
                        .runOnMainSync(
                                () ->
                                        ready[0] =
                                                expected == MainActivity.class
                                                        ? a.findViewById(R.id.nav_profile) != null
                                                        : expected == ProfileActivity.class
                                                                ? a.findViewById(
                                                                                R.id.profileSignOut)
                                                                        != null
                                                                : true);
                if (ready[0]) return;
            }
            SystemClock.sleep(200);
        }
        fail("Expected " + expected.getSimpleName() + "; " + screenMessage());
    }

    private String screenMessage() {
        String[] message = {"no resumed activity"};
        Activity a = resumed();
        if (a != null)
            InstrumentationRegistry.getInstrumentation()
                    .runOnMainSync(
                            () -> {
                                TextView v = a.findViewById(R.id.authMessage);
                                message[0] =
                                        a.getClass().getSimpleName()
                                                + ": "
                                                + (v == null ? "no auth message" : v.getText());
                            });
        return message[0];
    }

    private void awaitMessage(int resource) {
        String expected =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getString(resource);
        long end = SystemClock.elapsedRealtime() + 45000;
        while (SystemClock.elapsedRealtime() < end) {
            if (screenMessage().endsWith(": " + expected)) {
                onView(withId(R.id.submitButton)).check(matches(isEnabled()));
                return;
            }
            SystemClock.sleep(200);
        }
        fail("Expected " + expected + "; " + screenMessage());
    }

    private void cleanupOwnAccount() throws Exception {
        // Re-authenticate only with this test's random credentials. Never delete other accounts.
        FirebaseUser user = auth.getCurrentUser();
        if (createdUid == null && user != null && email.equalsIgnoreCase(user.getEmail()))
            createdUid = user.getUid();
        if (createdUid == null) {
            auth.signOut();
            return;
        }
        if (user == null || !createdUid.equals(user.getUid())) {
            user =
                    Tasks.await(
                                    auth.signInWithEmailAndPassword(email, password),
                                    30,
                                    TimeUnit.SECONDS)
                            .getUser();
        }
        assertEquals(createdUid, user.getUid());
        Tasks.await(db.collection("usernames").document(username).delete(), 30, TimeUnit.SECONDS);
        Tasks.await(db.collection("users").document(createdUid).delete(), 30, TimeUnit.SECONDS);
        Tasks.await(user.delete(), 30, TimeUnit.SECONDS);
        auth.signOut();
    }
}
