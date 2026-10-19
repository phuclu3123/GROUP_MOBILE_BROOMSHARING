package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostBookingsActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseConfig;

/** Explicit opt-in; setup and exact cleanup belong to live-acceptance.cjs. */
@RunWith(AndroidJUnit4.class)
public class LiveBookingAcceptanceTest {
    private final Bundle args = InstrumentationRegistry.getArguments();
    private void login(String role) throws Exception {
        FirebaseAuth.getInstance().signOut();
        Tasks.await(FirebaseAuth.getInstance().signInWithEmailAndPassword(
                args.getString(role + "Email"), args.getString(role + "Password")), 60, TimeUnit.SECONDS);
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String name, Map<String, Object> data) throws Exception {
        return (Map<String, Object>) Tasks.await(FirebaseConfig.functions().getHttpsCallable(name).call(data),
                60, TimeUnit.SECONDS).getData();
    }
    private static Map<String, Object> data(Object... pairs) {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) data.put((String) pairs[i], pairs[i + 1]);
        return data;
    }
    private <T extends android.app.Activity> void awaitRows(ActivityScenario<T> scenario, int id) {
        long deadline = SystemClock.elapsedRealtime() + 60000;
        AtomicBoolean ready = new AtomicBoolean();
        long readySince = 0;
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity(a -> {
                RecyclerView list = a.findViewById(id);
                android.view.View progress = a.findViewById(R.id.progressBar);
                ready.set(list != null && list.isShown() && list.getAdapter() != null
                        && list.getAdapter().getItemCount() > 0
                        && (progress == null || progress.getVisibility() != android.view.View.VISIBLE));
            });
            if (ready.get()) {
                if (readySince == 0) readySince = SystemClock.elapsedRealtime();
                if (SystemClock.elapsedRealtime() - readySince >= 1000) return;
            } else readySince = 0;
            SystemClock.sleep(250);
        }
        fail("Firebase-backed booking list did not load");
    }
    private void screenshot(String name) throws Exception {
        android.graphics.Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        java.io.File folder = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null);
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(new java.io.File(folder, name + ".png"))) {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        } finally { image.recycle(); }
    }
    @Test public void realAuthFirestoreGuestHostAdmin() throws Exception {
        assumeTrue("Explicit live acceptance required", "true".equals(args.getString("runLiveBooking")));
        assertTrue(BuildConfig.DEBUG);
        assertEquals("10.0.2.2", BuildConfig.FUNCTIONS_EMULATOR_HOST);
        try {
            login("host");
            call("setHostListing", data("roomId", args.getString("roomId"), "status", "ACTIVE"));
            login("guest");
            long start = Long.parseLong(args.getString("startAt")) + 7200000;
            Map<String, Object> request = data("roomId", args.getString("roomId"), "startAt", start,
                    "endAt", start + 3600000, "idempotencyKey", args.getString("runId") + "_android");
            Map<String, Object> quote = call("quoteBooking", request);
            assertEquals(100000L, ((Number)((Map<?,?>)quote.get("priceSnapshot")).get("totalAmount")).longValue());
            String bookingId = (String) call("createBooking", request).get("bookingId");
            try (ActivityScenario<MyBookingsActivity> screen = ActivityScenario.launch(MyBookingsActivity.class)) {
                awaitRows(screen, R.id.bookings);
                screenshot("acceptance-guest");
            }
            login("host");
            try (ActivityScenario<HostBookingsActivity> screen = ActivityScenario.launch(HostBookingsActivity.class)) {
                awaitRows(screen, R.id.rvBookings);
                screenshot("acceptance-host");
            }
            call("transitionBooking", data("bookingId", bookingId, "status", "CONFIRMED"));
            String status = Tasks.await(FirebaseFirestore.getInstance(FirebaseConfig.FIRESTORE_DATABASE_ID)
                    .collection("bookings").document(bookingId).get(Source.SERVER),60,TimeUnit.SECONDS).getString("status");
            assertEquals("CONFIRMED",status);
            login("admin");
            call("moderateRoom", data("roomId", args.getString("roomId"), "status", "SUSPENDED", "reason", "Android acceptance"));
            call("moderateRoom", data("roomId", args.getString("roomId"), "status", "INACTIVE", "reason", "Android acceptance complete"));
            try (ActivityScenario<AdminDashboardActivity> screen = ActivityScenario.launch(AdminDashboardActivity.class)) {
                AtomicBoolean loaded = new AtomicBoolean();
                long deadline = SystemClock.elapsedRealtime() + 60000;
                while (!loaded.get() && SystemClock.elapsedRealtime() < deadline) {
                    screen.onActivity(a -> {
                        android.widget.Spinner sections = a.findViewById(R.id.adminSections);
                        if (sections != null) { sections.setSelection(5); loaded.set(true); }
                    });
                    SystemClock.sleep(250);
                }
                assertTrue("Admin session screen did not load", loaded.get());
                awaitRows(screen, R.id.reviewList);
            }
            login("guest");
            call("transitionBooking", data("bookingId", bookingId, "status", "CANCELLED"));
        } finally { FirebaseAuth.getInstance().signOut(); }
    }
}
