package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import org.junit.Test;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

/*
   Opt-in cloud: hai Guest tranh cùng giờ của phòng PAYOS được giữ cho demo.
   Không tạo tiền giả hoặc dùng Host duyệt. Hủy đúng các lượt thử và giữ lịch sử.
   Các trường hợp thuê quý và quản trị được kiểm ở bộ emulator, không đổi kênh cloud.
*/
public class CloudBookingAcceptanceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();
    private final Map<String, BookingRepository> created = new LinkedHashMap<>();
    private final List<FirebaseApp> sessions = new ArrayList<>();
    private static class Answer<T> implements HostRepository.Callback<T> {
        final CompletableFuture<T> future = new CompletableFuture<>();
        public void onSuccess(T value) { future.complete(value); }
        public void onError(Exception error) { future.completeExceptionally(error); }
        T get() throws Exception { return future.get(60, TimeUnit.SECONDS); }
        void denied() throws Exception {
            try { get(); fail("Operation should be rejected"); }
            catch (ExecutionException expected) { assertNotNull(expected.getCause()); }
        }
    }
    private Properties properties(File file) throws Exception {
        Properties values = new Properties();
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) { values.load(reader); }
        return values;
    }
    private BookingRepository guest(String label, Properties credentials, String prefix) throws Exception {
        FirebaseApp app = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().getOptions(), "instant-qa-" + label + System.nanoTime());
        sessions.add(app);
        FirebaseAuth login = FirebaseAuth.getInstance(app);
        FirebaseFirestore store = FirebaseFirestore.getInstance(app, FirebaseConfig.FIRESTORE_DATABASE_ID);
        store.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
        Tasks.await(login.signInWithEmailAndPassword(credentials.getProperty(prefix + ".email"), credentials.getProperty(prefix + ".password")), 45, TimeUnit.SECONDS);
        return new BookingRepository(store, login);
    }
    private Booking request(String roomId, ZonedDateTime start) {
        Booking b = new Booking(); b.roomId = roomId; b.activeMode = Room.MODE_HOURLY;
        b.startAt = Date.from(start.toInstant()); b.endAt = Date.from(start.plusHours(1).toInstant());
        b.requestKey = "instant_cloud_" + UUID.randomUUID(); return b;
    }
    @Test public void instantConcurrentGuestsCannotDoubleBookOrConfirmBeforePayment() throws Exception {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runCloudBookingChecks")));
        assertTrue("Cloud APK required", BuildConfig.FIRESTORE_EMULATOR_HOST.isEmpty());
        File files = context.getExternalFilesDir(null);
        Properties payos = properties(new File(context.getFilesDir(), "payos-acceptance/accounts.properties"));
        Properties video = properties(new File(context.getFilesDir(), "video-credentials.local.properties"));
        Tasks.await(auth.signInWithEmailAndPassword(video.getProperty("admin.email"), video.getProperty("admin.password")), 45, TimeUnit.SECONDS);
        String roomId = "payos_demo_20261026_room";
        BookingRepository one = guest("one", payos, "guest"), two = guest("two", video, "other");
        JSONObject receipt = new JSONObject().put("flow", "INSTANT_5M").put("bankTransferPerformed", false);
        try {
            DocumentSnapshot channel = Tasks.await(db.collection("appConfig").document("payments").get(Source.SERVER));
            Room room = Tasks.await(db.collection("rooms").document(roomId).get(Source.SERVER)).toObject(Room.class);
            assertEquals("INSTANT_5M", channel.getString("flow"));
            assertEquals(room.getOwnerUid(), channel.getString("demoHostUid"));
            Booking a = null;
            for (int day = 2; day < 20; day++) {
                Booking candidate = request(roomId, LocalDate.now(HostSchedule.ZONE).plusDays(day).atTime(14, 0).atZone(HostSchedule.ZONE));
                Answer<Booking> quote = new Answer<>(); one.quote(candidate, quote);
                try { quote.get(); a = candidate; break; }
                catch (ExecutionException conflict) { /* Tìm giờ trống khác; create vẫn kiểm lại. */ }
            }
            assertNotNull("No available period for acceptance", a);
            Booking b = request(roomId, a.startAt.toInstant().atZone(HostSchedule.ZONE));
            Answer<String> first = new Answer<>(), second = new Answer<>(); one.create(a, first); two.create(b, second);
            String firstId = null, secondId = null;
            try { firstId = first.get(); created.put(firstId, one); } catch (ExecutionException conflict) { }
            try { secondId = second.get(); created.put(secondId, two); } catch (ExecutionException conflict) { }
            assertTrue("Exactly one concurrent hold must win", (firstId == null) != (secondId == null));
            String id = firstId == null ? secondId : firstId;
            BookingRepository winner = firstId == null ? two : one;
            Booking chosen = firstId == null ? b : a;
            Booking stored = Tasks.await(db.collection("bookings").document(id).get(Source.SERVER)).toObject(Booking.class);
            assertEquals("PENDING", stored.status); assertTrue(stored.isInstantPayment());
            assertEquals(0, stored.paidAmount); assertTrue(stored.paymentDeadline() > System.currentTimeMillis());
            Answer<String> retry = new Answer<>(); winner.create(chosen, retry); assertEquals(id, retry.get());
            Answer<String> forged = new Answer<>(); winner.transition(id, "CONFIRMED", "", forged); forged.denied();
            receipt.put("bookingId", id).put("concurrentWinners", 1).put("idempotentRetry", true).put("clientConfirmationDenied", true).put("accepted", true);
        } finally {
            // Chỉ nhả các lượt của lần chạy này; không xóa phòng hoặc dữ liệu quay video.
            for (Map.Entry<String, BookingRepository> entry : created.entrySet()) {
                Answer<String> cancelled = new Answer<>();
                entry.getValue().transition(entry.getKey(), "CANCELLED", "[QA] Kết thúc thử tranh lịch, chưa chuyển tiền.", cancelled);
                assertEquals(entry.getKey(), cancelled.get());
                Booking closed = Tasks.await(db.collection("bookings").document(entry.getKey()).get(Source.SERVER)).toObject(Booking.class);
                assertEquals("CANCELLED", closed.status);
                for (String key : closed.monthKeys) {
                    AvailabilityMonth month = Tasks.await(db.collection("availabilityMonths").document(key).get(Source.SERVER)).toObject(AvailabilityMonth.class);
                    assertFalse(month.hourlyBookings.containsKey(entry.getKey())); assertFalse(month.pendingPaymentIds.contains(entry.getKey()));
                }
            }
            for (FirebaseApp app : sessions) { FirebaseAuth.getInstance(app).signOut(); app.delete(); }
            try (Writer out = new OutputStreamWriter(new FileOutputStream(new File(files, "instant-cloud-acceptance.json")), StandardCharsets.UTF_8)) { out.write(receipt.toString(2)); }
        }
    }
}
