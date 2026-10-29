package vn.edu.ueh.phuclu.broomsharing;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.*;
import android.os.SystemClock;
import android.widget.*;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingNoticesActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ConversationsActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

// Chỉ chạy khi được bật rõ; dùng tài khoản demo cloud thật, giữ lại hồ sơ để quay video.
public class MemberIntegrationCloudTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();
    private final Properties credentials = new Properties();
    private final List<String> checks = new ArrayList<>();

    private <T> T task(Task<T> task) throws Exception {
        return Tasks.await(task, 60, TimeUnit.SECONDS);
    }

    private void login(String alias) throws Exception {
        task(
                auth.signInWithEmailAndPassword(
                        credentials.getProperty(alias + ".email"),
                        credentials.getProperty(alias + ".password")));
    }

    private static class Answer<T> implements HostRepository.Callback<T> {
        final CountDownLatch done = new CountDownLatch(1);
        T value;
        Exception error;

        public void onSuccess(T value) {
            this.value = value;
            done.countDown();
        }

        public void onError(Exception error) {
            this.error = error;
            done.countDown();
        }

        T result() throws Exception {
            assertTrue(done.await(60, TimeUnit.SECONDS));
            if (error != null) throw error;
            return value;
        }

        void denied() throws Exception {
            assertTrue(done.await(60, TimeUnit.SECONDS));
            assertNotNull(error);
        }
    }

    private void denied(Task<?> task) throws Exception {
        try {
            task(task);
            fail("Ghi/đọc trái quyền đã được chấp nhận");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof FirebaseFirestoreException);
            assertEquals(
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    ((FirebaseFirestoreException) e.getCause()).getCode());
        }
    }

    private <T extends android.app.Activity> void waitView(ActivityScenario<T> screen, int id) {
        AtomicBoolean ready = new AtomicBoolean();
        for (int i = 0; i < 120 && !ready.get(); i++) {
            screen.onActivity(a -> ready.set(a.findViewById(id) != null));
            if (!ready.get()) SystemClock.sleep(250);
        }
        assertTrue("Màn hình chưa sẵn sàng", ready.get());
    }

    private void capture(String name) throws Exception {
        android.graphics.Bitmap image =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try (FileOutputStream out =
                new FileOutputStream(new File(context.getExternalFilesDir(null), name))) {
            assertNotNull(image);
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            if (image != null) image.recycle();
        }
    }

    @Test
    public void integratedMemberFeaturesUseCurrentCloudContracts() throws Exception {
        assumeTrue(
                "Explicit cloud member acceptance required",
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("runMemberIntegrationChecks")));
        String installed =
                (String)
                        Class.forName(
                                        context.getPackageName() + ".BuildConfig",
                                        false,
                                        context.getClassLoader())
                                .getField("FIRESTORE_EMULATOR_HOST")
                                .get(null);
        assertEquals("", installed);
        assertEquals(
                "broom-sharing-firebase-mobile",
                com.google.firebase.FirebaseApp.getInstance().getOptions().getProjectId());
        assertEquals("broomsharing", FirebaseConfig.FIRESTORE_DATABASE_ID);
        try (Reader in =
                new InputStreamReader(
                        new FileInputStream(
                                new File(
                                        context.getExternalFilesDir(null),
                                        "video-credentials.local.properties")),
                        StandardCharsets.UTF_8)) {
            credentials.load(in);
        }
        String verificationId = null;
        boolean passed = false;
        try {
            login("host");
            Intent calendarScreen =
                    new Intent(context, HostBookingsActivity.class)
                            .putExtra("ROOM_ID", credentials.getProperty("businessRun") + "room");
            try (ActivityScenario<HostBookingsActivity> screen =
                    ActivityScenario.launch(calendarScreen)) {
                AtomicBoolean ready = new AtomicBoolean();
                for (int i = 0; i < 120 && !ready.get(); i++) {
                    screen.onActivity(
                            a -> {
                                android.view.View button = a.findViewById(R.id.btnMonthCalendar);
                                ready.set(button != null && button.isEnabled());
                            });
                    if (!ready.get()) SystemClock.sleep(250);
                }
                assertTrue("Lịch tháng chưa nhận đủ phòng và booking server", ready.get());
                screen.onActivity(a -> a.findViewById(R.id.btnMonthCalendar).performClick());
                onView(withId(R.id.gridCalendarDays))
                        .check(
                                androidx.test.espresso.assertion.ViewAssertions.matches(
                                        isDisplayed()));
                onView(withId(R.id.btnNextMonth)).perform(click());
                capture("member-month-calendar-holds.png");
                onView(withId(R.id.btnChangeRoom)).perform(click());
                onView(isAssignableFrom(ListView.class))
                        .perform(
                                new androidx.test.espresso.ViewAction() {
                                    public org.hamcrest.Matcher<android.view.View>
                                            getConstraints() {
                                        return isAssignableFrom(ListView.class);
                                    }

                                    public String getDescription() {
                                        return "Chọn phòng trên lịch tháng";
                                    }

                                    public void perform(
                                            androidx.test.espresso.UiController ui,
                                            android.view.View view) {
                                        ListView list = (ListView) view;
                                        list.performItemClick(
                                                list.getChildAt(0),
                                                0,
                                                list.getAdapter().getItemId(0));
                                        ui.loopMainThreadUntilIdle();
                                    }
                                });
                capture("member-month-calendar.png");
                onView(withId(R.id.btnCloseCalendar)).perform(click());
            }
            checks.add("Host month calendar loads server rooms/bookings, changes month and room");
            AtomicReference<String> created = new AtomicReference<>();
            try (ActivityScenario<PropertyVerificationActivity> screen =
                    ActivityScenario.launch(PropertyVerificationActivity.class)) {
                waitView(screen, R.id.etDescription);
                String description =
                        "Đề nghị đối chiếu thông tin địa điểm trong lượt nghiệm thu tích hợp.";
                screen.onActivity(
                        a -> ((EditText) a.findViewById(R.id.etDescription)).setText(description));
                screen.recreate();
                waitView(screen, R.id.etDescription);
                AtomicBoolean ready = new AtomicBoolean();
                for (int i = 0; i < 120 && !ready.get(); i++) {
                    screen.onActivity(
                            a ->
                                    ready.set(
                                            a.findViewById(R.id.btnSubmitVerification) != null
                                                    && a.findViewById(R.id.btnSubmitVerification)
                                                            .isEnabled()));
                    if (!ready.get()) SystemClock.sleep(250);
                }
                assertTrue(ready.get());
                screen.onActivity(
                        a -> {
                            assertEquals(
                                    description,
                                    ((EditText) a.findViewById(R.id.etDescription))
                                            .getText()
                                            .toString());
                            created.set(
                                    new ViewModelProvider(a)
                                            .get(PropertyVerificationViewModel.class)
                                            .draftId);
                            a.findViewById(R.id.btnSubmitVerification).performClick();
                        });
                boolean found = false;
                for (int i = 0; i < 80 && !found; i++) {
                    try {
                        found =
                                task(db.collection("propertyVerifications")
                                                .document(created.get())
                                                .get(Source.SERVER))
                                        .exists();
                    } catch (ExecutionException ignored) {
                    }
                    if (!found) SystemClock.sleep(250);
                }
                assertTrue("Hồ sơ chưa được lưu server", found);
                SystemClock.sleep(500);
                capture("member-verification-pending.png");
            }
            verificationId = created.get();
            PropertyVerification item =
                    task(db.collection("propertyVerifications")
                                    .document(verificationId)
                                    .get(Source.SERVER))
                            .toObject(PropertyVerification.class);
            assertEquals("PENDING", item.status);
            assertEquals(credentials.getProperty("host.uid"), item.ownerUid);
            assertTrue(item.evidenceRefs.isEmpty());
            assertNotNull(item.createdAt);
            Answer<String> retry = new Answer<>();
            new PropertyVerificationRepository().submit(verificationId, item, retry);
            assertEquals(verificationId, retry.result());
            denied(
                    db.collection("propertyVerifications")
                            .document(verificationId)
                            .update("status", "VERIFIED"));
            Map<String, Object> forged =
                    new HashMap<>(
                            task(db.collection("propertyVerifications")
                                            .document(verificationId)
                                            .get(Source.SERVER))
                                    .getData());
            String foreignId = "member_foreign_" + System.currentTimeMillis();
            forged.put("id", foreignId);
            forged.put("roomId", "missing_asset");
            forged.put("createdAt", FieldValue.serverTimestamp());
            forged.put("updatedAt", FieldValue.serverTimestamp());
            denied(db.collection("propertyVerifications").document(foreignId).set(forged));
            checks.add(
                    "Host verification form survives rotation, writes evidenceRefs metadata once,"
                        + " rejects self verification and foreign asset");
            login("guest");
            denied(
                    db.collection("propertyVerifications")
                            .document(verificationId)
                            .get(Source.SERVER));
            forged.put("ownerUid", credentials.getProperty("guest.uid"));
            denied(db.collection("propertyVerifications").document(foreignId).set(forged));
            login("admin");
            ReviewItem review = new ReviewItem("VERIFICATION", verificationId, "", "");
            review.status = "PENDING";
            Answer<String> noProof = new Answer<>();
            new AdminRepository()
                    .review(review, true, "Kiểm hồ sơ chưa có tệp bằng chứng.", false, noProof);
            noProof.denied();
            Answer<String> reject = new Answer<>();
            new AdminRepository()
                    .review(
                            review,
                            false,
                            "Cần bổ sung bằng chứng sau khi API tệp được tích hợp.",
                            false,
                            reject);
            reject.result();
            QuerySnapshot audit =
                    task(
                            db.collection("auditLogs")
                                    .whereEqualTo(
                                            "targetPath", "propertyVerifications/" + verificationId)
                                    .get(Source.SERVER));
            assertTrue("Duyệt hồ sơ phải có audit", !audit.isEmpty());
            assertEquals(
                    credentials.getProperty("admin.uid"),
                    audit.getDocuments().get(0).getString("actorUid"));
            checks.add(
                    "Guest cannot read/submit Host verification; Admin cannot approve without proof"
                        + " and can reject with audit");
            login("host");
            assertEquals(
                    "REJECTED",
                    task(db.collection("propertyVerifications")
                                    .document(verificationId)
                                    .get(Source.SERVER))
                            .getString("status"));
            QuerySnapshot reports =
                    task(
                            db.collection("reports")
                                    .whereEqualTo("ownerUid", auth.getUid())
                                    .get(Source.SERVER));
            DocumentSnapshot open = null;
            for (DocumentSnapshot row : reports)
                if ("OPEN".equals(row.getString("status"))) open = row;
            assertNotNull("Cần báo cáo OPEN trong cohort demo", open);
            try (ActivityScenario<HostReportsActivity> screen =
                    ActivityScenario.launch(HostReportsActivity.class)) {
                waitView(screen, R.id.rvReports);
                AtomicBoolean ready = new AtomicBoolean();
                for (int i = 0; i < 120 && !ready.get(); i++) {
                    screen.onActivity(
                            a -> {
                                RecyclerView list = a.findViewById(R.id.rvReports);
                                ready.set(
                                        list.getAdapter() != null
                                                && list.getAdapter().getItemCount() > 0);
                            });
                    if (!ready.get()) SystemClock.sleep(250);
                }
                assertTrue(ready.get());
                onView(allOf(withId(R.id.btnExplain), isDisplayed())).perform(click());
                onView(isAssignableFrom(EditText.class))
                        .perform(
                                replaceText("Host đã đối chiếu thông tin sau khi ghép tính năng."));
                onView(withId(android.R.id.button1)).perform(click());
                boolean replied = false;
                for (int i = 0; i < 80 && !replied; i++) {
                    DocumentSnapshot row =
                            task(
                                    db.collection("reports")
                                            .document(open.getId())
                                            .get(Source.SERVER));
                    replied =
                            "Host đã đối chiếu thông tin sau khi ghép tính năng."
                                            .equals(row.getString("hostResponse"))
                                    && row.getTimestamp("hostRespondedAt") != null;
                    if (!replied) SystemClock.sleep(250);
                }
                assertTrue("Phản hồi phải đi qua contract có hostRespondedAt", replied);
                onView(
                                allOf(
                                        withId(R.id.tvReportDetails),
                                        withText(
                                                org.hamcrest.Matchers.containsString(
                                                        "Host đã đối chiếu thông tin sau khi ghép"
                                                            + " tính năng."))))
                        .check(
                                androidx.test.espresso.assertion.ViewAssertions.matches(
                                        isDisplayed()));
                capture("member-host-report.png");
            }
            checks.add(
                    "Hoang report card sends real reply through shared Repository and server"
                        + " respondedAt");
            login("guest");
            android.app.Instrumentation instrumentation =
                    InstrumentationRegistry.getInstrumentation();
            android.app.Instrumentation.ActivityMonitor monitor =
                    instrumentation.addMonitor(BookingNoticesActivity.class.getName(), null, false);
            try (ActivityScenario<ConversationsActivity> screen =
                    ActivityScenario.launch(ConversationsActivity.class)) {
                waitView(screen, R.id.openUpdates);
                screen.onActivity(a -> a.findViewById(R.id.openUpdates).performClick());
                android.app.Activity opened = monitor.waitForActivityWithTimeout(20000);
                assertNotNull("Lối tắt inbox chưa mở hộp cập nhật", opened);
                AtomicBoolean displayed = new AtomicBoolean();
                for (int i = 0; i < 120 && !displayed.get(); i++) {
                    instrumentation.runOnMainSync(
                            () -> displayed.set(opened.findViewById(R.id.noticeToolbar) != null));
                    if (!displayed.get()) SystemClock.sleep(250);
                }
                assertTrue("Hộp cập nhật chưa dựng giao diện", displayed.get());
                onView(withId(R.id.noticeToolbar))
                        .check(
                                androidx.test.espresso.assertion.ViewAssertions.matches(
                                        isDisplayed()));
                capture("member-inbox-updates.png");
                instrumentation.runOnMainSync(opened::finish);
            } finally {
                instrumentation.removeMonitor(monitor);
            }
            checks.add("Truc inbox shortcut opens the shared real notification center");
            passed = true;
        } finally {
            auth.signOut();
            org.json.JSONObject receipt =
                    new org.json.JSONObject()
                            .put("passed", passed)
                            .put("checks", new org.json.JSONArray(checks))
                            .put("verificationId", verificationId)
                            .put("realAuthAccountsRetained", 4)
                            .put(
                                    "mediaUpload",
                                    "Deferred to shared third-party API; metadata only");
            try (Writer out =
                    new OutputStreamWriter(
                            new FileOutputStream(
                                    new File(
                                            context.getExternalFilesDir(null),
                                            "member-integration-cloud.json")),
                            StandardCharsets.UTF_8)) {
                out.write(receipt.toString(2));
            }
        }
    }
}
