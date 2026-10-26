package vn.edu.ueh.phuclu.broomsharing;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.SystemClock;
import android.widget.EditText;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import org.json.*;
import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.ui.admin.AdminBookingsActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingSuccessActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostBookingsActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

// Chỉ bật tường minh: Auth dùng tài khoản demo thật, mọi dữ liệu nghiệp vụ nằm ở emulator.
public class BookingResolutionAcceptanceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();
    private final Properties credentials = new Properties();
    private final List<String> checks = new ArrayList<>();
    private final String base =
            "http://10.0.2.2:8085/v1/projects/broom-sharing-firebase-mobile/databases/broomsharing/documents/";

    private <T> T task(Task<T> value) throws Exception {
        return Tasks.await(value, 40, TimeUnit.SECONDS);
    }

    private <T> T result(Consumer<ResultCallback<T>> operation) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        operation.accept(
                new ResultCallback<T>() {
                    public void onSuccess(T value) {
                        done.complete(value);
                    }

                    public void onError(Exception error) {
                        done.completeExceptionally(error);
                    }
                });
        return done.get(40, TimeUnit.SECONDS);
    }

    private void transition(String id, String target, String reason) throws Exception {
        assertEquals(
                id,
                this.<String>result(
                        cb -> new BookingRepository().transition(id, target, reason, cb)));
    }

    private void denied(Consumer<ResultCallback<String>> operation) throws Exception {
        try {
            this.<String>result(operation);
            fail("Forbidden action accepted");
        } catch (ExecutionException expected) {
            assertNotNull(expected.getCause());
        }
    }

    private void login(String alias) throws Exception {
        auth.signOut();
        task(
                auth.signInWithEmailAndPassword(
                        credentials.getProperty(alias + ".email"),
                        credentials.getProperty(alias + ".password")));
    }

    private JSONObject encode(Object value) throws Exception {
        JSONObject out = new JSONObject();
        if (value == null) return out.put("nullValue", JSONObject.NULL);
        if (value instanceof Date)
            return out.put("timestampValue", ((Date) value).toInstant().toString());
        if (value instanceof Number) return out.put("integerValue", value.toString());
        if (value instanceof List) {
            JSONArray values = new JSONArray();
            for (Object item : (List<?>) value) values.put(encode(item));
            return out.put("arrayValue", new JSONObject().put("values", values));
        }
        if (value instanceof Map) {
            JSONObject fields = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
                fields.put(entry.getKey().toString(), encode(entry.getValue()));
            return out.put("mapValue", new JSONObject().put("fields", fields));
        }
        return out.put("stringValue", value.toString());
    }

    private void seed(String path, Map<String, Object> values) throws Exception {
        // owner bypass chỉ tồn tại trong emulator, tuyệt đối không dùng URL cloud.
        assertFalse(BuildConfig.FIRESTORE_EMULATOR_HOST.isEmpty());
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setRequestMethod("PATCH");
        connection.setRequestProperty("Authorization", "Bearer owner");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        JSONObject fields = new JSONObject();
        for (Map.Entry<String, Object> entry : values.entrySet())
            fields.put(entry.getKey(), encode(entry.getValue()));
        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(
                    new JSONObject()
                            .put("fields", fields)
                            .toString()
                            .getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("Emulator seed failed", 200, connection.getResponseCode());
        connection.disconnect();
    }

    private Map<String, Object> fields(Object... pairs) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }

    private void profiles(boolean blocked) throws Exception {
        for (String alias : new String[] {"host", "guest", "admin", "other"})
            seed(
                    "users/" + credentials.getProperty(alias + ".uid"),
                    fields(
                            "uid",
                            credentials.getProperty(alias + ".uid"),
                            "name",
                            credentials.getProperty(alias + ".name"),
                            "phone",
                            "+84900000000",
                            "username",
                            alias,
                            "email",
                            credentials.getProperty(alias + ".email"),
                            "role",
                            alias.equals("admin")
                                    ? "ADMIN"
                                    : alias.equals("host") ? "HOST" : "GUEST",
                            "status",
                            blocked && alias.equals("host") ? "BLOCKED" : "ACTIVE",
                            "schemaVersion",
                            1));
    }

    private Booking fixture(String status, boolean ended) throws Exception {
        StringBuilder hash = new StringBuilder();
        for (byte part :
                MessageDigest.getInstance("SHA-256")
                        .digest(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)))
            hash.append(String.format("%02x", part));
        Booking b = new Booking();
        b.id = hash.toString();
        b.schemaVersion = 2;
        b.roomId = "qa_resolution_" + b.id.substring(0, 12);
        b.ownerUid = credentials.getProperty("host.uid");
        b.guestUid = credentials.getProperty("guest.uid");
        b.roomName = "[QA] Khép lượt thuê";
        b.status = status;
        b.paymentStatus = "UNAVAILABLE";
        b.startAt = new Date(System.currentTimeMillis() - 7200000);
        b.endAt = new Date(System.currentTimeMillis() + (ended ? -60000 : 7200000));
        YearMonth month = YearMonth.from(b.startAt.toInstant().atZone(HostSchedule.ZONE));
        b.monthKeys =
                Collections.singletonList(
                        b.roomId + "_" + (month.getYear() * 100 + month.getMonthValue()));
        b.slotKeys = Arrays.asList(10, 11);
        b.slotStart = 10;
        b.slotCount = 2;
        seed(
                "bookings/" + b.id,
                fields(
                        "id",
                        b.id,
                        "schemaVersion",
                        2,
                        "roomId",
                        b.roomId,
                        "roomName",
                        b.roomName,
                        "roomAddress",
                        "[QA]",
                        "guestName",
                        credentials.getProperty("guest.name"),
                        "ownerUid",
                        b.ownerUid,
                        "guestUid",
                        b.guestUid,
                        "status",
                        status,
                        "paymentStatus",
                        "UNAVAILABLE",
                        "activeMode",
                        "HOURLY",
                        "rentalMonths",
                        0,
                        "slotStart",
                        10,
                        "slotCount",
                        2,
                        "slotKeys",
                        b.slotKeys,
                        "monthKeys",
                        b.monthKeys,
                        "startAt",
                        b.startAt,
                        "endAt",
                        b.endAt,
                        "rejectionReason",
                        "",
                        "totalAmount",
                        100000,
                        "refundAmount",
                        0,
                        "refundStatus",
                        "NOT_REQUIRED",
                        "priceSnapshot",
                        new HashMap<>(),
                        "createdAt",
                        new Date(System.currentTimeMillis() - 86400000)));
        seed(
                "rooms/" + b.roomId,
                fields(
                        "id",
                        b.roomId,
                        "ownerUid",
                        b.ownerUid,
                        "roomName",
                        b.roomName,
                        "activeMode",
                        "HOURLY",
                        "activeBookingCount",
                        2,
                        "bookingRevision",
                        0,
                        "lastBookingId",
                        b.id));
        seed(
                "availabilityMonths/" + b.monthKeys.get(0),
                fields(
                        "roomId",
                        b.roomId,
                        "monthIndex",
                        month.getYear() * 100 + month.getMonthValue(),
                        "fixedBookingId",
                        "",
                        "slotsBusy",
                        Arrays.asList(10, 11, 20),
                        "hourlyBookings",
                        fields(b.id, b.slotKeys, "other_booking", Collections.singletonList(20)),
                        "lastBookingId",
                        b.id));
        return b;
    }

    private Booking read(Booking b) throws Exception {
        return task(db.document("bookings/" + b.id).get(Source.SERVER)).toObject(Booking.class);
    }

    private void releasedOnce(Booking b) throws Exception {
        assertEquals(
                Long.valueOf(1),
                task(db.document("rooms/" + b.roomId).get(Source.SERVER))
                        .getLong("activeBookingCount"));
        AvailabilityMonth m =
                task(db.document("availabilityMonths/" + b.monthKeys.get(0)).get(Source.SERVER))
                        .toObject(AvailabilityMonth.class);
        assertEquals(Collections.singletonList(20), m.slotsBusy);
        assertEquals(Collections.singleton("other_booking"), m.hourlyBookings.keySet());
    }

    @Test
    public void repositoriesAndScreensCloseNoShowAndBlockedHostCases() throws Exception {
        assumeTrue(
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("runBookingResolutionChecks")));
        assertEquals("10.0.2.2", BuildConfig.FIRESTORE_EMULATOR_HOST);
        try (InputStream in =
                new FileInputStream(
                        new File(
                                context.getExternalFilesDir(null),
                                "resolution-credentials.properties"))) {
            credentials.load(in);
        }
        try {
            profiles(false);
            Booking noShow = fixture("CONFIRMED", true);
            login("host");
            try (ActivityScenario<HostBookingsActivity> screen =
                    ActivityScenario.launch(HostBookingsActivity.class)) {
                long deadline = System.currentTimeMillis() + 20000;
                while (System.currentTimeMillis() < deadline) {
                    java.util.concurrent.atomic.AtomicBoolean loaded =
                            new java.util.concurrent.atomic.AtomicBoolean();
                    screen.onActivity(a -> {
                        RecyclerView rows = a.findViewById(R.id.rvBookings);
                        loaded.set(rows != null && rows.getAdapter() != null && rows.getAdapter().getItemCount() > 0);
                    });
                    if (loaded.get()) break;
                    SystemClock.sleep(200);
                }
                screen.onActivity(a -> {
                    RecyclerView rows = a.findViewById(R.id.rvBookings);
                    assertNotNull("Host screen did not load", rows);
                    assertNotNull("Booking card did not render", rows.findViewHolderForAdapterPosition(0));
                    rows.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.btnProgress).performClick();
                });
                onView(withText("Khách không đến"))
                        .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                        .perform(click());
                onView(isAssignableFrom(EditText.class))
                        .perform(
                                replaceText("[QA] Đã liên hệ, xác nhận khách không đến"),
                                closeSoftKeyboard());
                onView(withText("Xác nhận")).perform(click());
                for (int i = 0; i < 60 && !"NO_SHOW".equals(read(noShow).status); i++)
                    SystemClock.sleep(200);
                assertEquals("NO_SHOW", read(noShow).status);
                releasedOnce(noShow);
            }
            checks.add(
                    "Host UI explicitly chooses no-show after end; transaction retains other"
                        + " booking's slot");
            login("guest");
            denied(
                    cb ->
                            new ReviewRepository()
                                    .submit(
                                            noShow.id,
                                            4,
                                            "Đánh giá khi chưa sử dụng không hợp lệ.",
                                            cb));
            String reportId = UUID.randomUUID().toString();
            assertEquals(
                    reportId,
                    this.<String>result(
                            cb ->
                                    new ReportRepository()
                                            .submit(
                                                    reportId,
                                                    noShow.roomId,
                                                    noShow.id,
                                                    "Khác",
                                                    "[QA] Khách phản ánh đã sử dụng nhưng bị ghi"
                                                        + " không đến.",
                                                    cb)));
            login("admin");
            transition(noShow.id, "COMPLETED", "[QA] Xác minh phản ánh, khách thực tế đã sử dụng");
            releasedOnce(noShow);
            long revision =
                    task(db.document("rooms/" + noShow.roomId).get(Source.SERVER))
                            .getLong("bookingRevision");
            transition(noShow.id, "COMPLETED", "[QA] Retry");
            assertEquals(
                    Long.valueOf(revision),
                    task(db.document("rooms/" + noShow.roomId).get(Source.SERVER))
                            .getLong("bookingRevision"));
            login("guest");
            assertEquals(
                    noShow.id,
                    this.<String>result(
                            cb ->
                                    new ReviewRepository()
                                            .submit(
                                                    noShow.id,
                                                    4,
                                                    "[QA] Đã sử dụng, kết luận được sửa đúng.",
                                                    cb)));
            checks.add(
                    "No-show cannot review; Guest reports; Admin corrects without second release;"
                        + " verified Guest can review; retry is idempotent");

            Booking ongoing = fixture("CHECKED_IN", false);
            profiles(true);
            login("host");
            denied(
                    cb ->
                            new BookingRepository()
                                    .transition(
                                            ongoing.id,
                                            "TERMINATED",
                                            "[QA] Cannot bypass block",
                                            cb));
            login("admin");
            try (ActivityScenario<AdminBookingsActivity> screen =
                    ActivityScenario.launch(AdminBookingsActivity.class)) {
                for (int i = 0; i < 80; i++) {
                    java.util.concurrent.atomic.AtomicBoolean loaded =
                            new java.util.concurrent.atomic.AtomicBoolean();
                    screen.onActivity(
                            a ->
                                    loaded.set(
                                            a.findViewById(android.R.id.content)
                                                            .findViewWithTag(
                                                                    ongoing.id + ":TERMINATED")
                                                    != null));
                    if (loaded.get()) break;
                    SystemClock.sleep(200);
                }
                onView(withTagValue(is((Object) (ongoing.id + ":TERMINATED"))))
                        .perform(scrollTo(), click());
                onView(isAssignableFrom(EditText.class))
                        .perform(
                                replaceText("[QA] Xác minh sự cố, khách đã ngừng sử dụng"),
                                closeSoftKeyboard());
                onView(withText("Xác nhận xử lý")).perform(click());
                for (int i = 0; i < 60 && !"TERMINATED".equals(read(ongoing).status); i++)
                    SystemClock.sleep(200);
                assertEquals("TERMINATED", read(ongoing).status);
            }
            Booking closed = read(ongoing);
            assertNotNull(closed.closedAt);
            assertNotNull(closed.usageConfirmedAt);
            assertEquals(ongoing.endAt.getTime(), closed.endAt.getTime());
            assertEquals("UNAVAILABLE", closed.paymentStatus);
            releasedOnce(ongoing);
            login("guest");
            assertEquals(
                    ongoing.id,
                    this.<String>result(
                            cb ->
                                    new ReviewRepository()
                                            .submit(
                                                    ongoing.id,
                                                    3,
                                                    "[QA] Có sử dụng nhưng phải ngừng do sự cố.",
                                                    cb)));
            try (ActivityScenario<BookingSuccessActivity> screen =
                    ActivityScenario.launch(
                            new android.content.Intent(context, BookingSuccessActivity.class)
                                    .putExtra("bookingId", ongoing.id))) {
                SystemClock.sleep(2000);
                onView(withId(R.id.bookingReview))
                        .check(
                                androidx.test.espresso.assertion.ViewAssertions.matches(
                                        withEffectiveVisibility(Visibility.VISIBLE)));
                onView(withId(R.id.tvConfirmationMode))
                        .check(
                                androidx.test.espresso.assertion.ViewAssertions.matches(
                                        withText(containsString("Kết thúc do sự cố"))));
            }
            checks.add(
                    "Blocked Host denied; Admin incident closure preserves original period/payment;"
                        + " Guest review allowed before original end; detail UI displays result");
            Booking due = fixture("CHECKED_IN", true);
            login("admin");
            transition(due.id, "COMPLETED", "[QA] Xác minh hết kỳ, hoàn tất thay Host bị khóa");
            releasedOnce(due);
            Booking waiting = fixture("CONFIRMED", true);
            profiles(false);
            login("host");
            denied(cb -> new BookingRepository().transition(waiting.id, "COMPLETED", "", cb));
            transition(
                    waiting.id,
                    "COMPLETED",
                    "[QA] Quên check-in, xác minh bổ sung sử dụng thực tế");
            releasedOnce(waiting);
            checks.add(
                    "Admin completes expired checked-in period; missing check-in cannot silently"
                        + " complete; documented late confirmation succeeds");
            JSONObject receipt =
                    new JSONObject()
                            .put("passed", true)
                            .put("checks", new JSONArray(checks))
                            .put("businessDatabase", "local emulator only");
            try (FileWriter writer =
                    new FileWriter(
                            new File(
                                    context.getExternalFilesDir(null),
                                    "booking-resolution-acceptance.json"))) {
                writer.write(receipt.toString(2));
            }
        } finally {
            profiles(false);
            auth.signOut();
        }
    }
}
