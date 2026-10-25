package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.SystemClock;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.*;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import vn.edu.ueh.phuclu.broomsharing.model.AvailabilityMonth;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.model.PublicHost;
import vn.edu.ueh.phuclu.broomsharing.model.ReviewItem;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.RoomListing;
import vn.edu.ueh.phuclu.broomsharing.repository.AdminRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.BookingRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ChatRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.HostRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.NoticeRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ReportRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ReviewRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.RoomRepository;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingNoticeViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingNoticesActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostDashboardActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.report.ReportCenterActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.report.ReportCenterViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.review.RoomReviewsActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.review.RoomReviewsViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.room.MainActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.BookingPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseConfig;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;
import vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android Repository acceptance. Emulator by default; cloud requires explicit opt-in and isolated
 * retained demo credentials. Historical preparation is recorded separately from business actions.
 */
@RunWith(AndroidJUnit4.class)
public class FirestoreRepositoryTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<Actor> actors = new ArrayList<>();
    private String run = "java_" + System.currentTimeMillis();
    private final boolean cloud =
            "true"
                    .equals(
                            InstrumentationRegistry.getArguments()
                                    .getString("runCloudBusinessChecks"));
    private final Properties demo = new Properties();
    private final List<String> checks = new ArrayList<>();
    private boolean passed;
    private final String project = FirebaseApp.getInstance().getOptions().getProjectId();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();

    private static class Actor {
        FirebaseApp app;
        FirebaseAuth auth;
        FirebaseFirestore db;
        String uid, email, password;
    }

    private static class Answer<T> implements HostRepository.Callback<T> {
        final CountDownLatch done = new CountDownLatch(1);
        T value;
        Exception error;

        public void onSuccess(T result) {
            value = result;
            done.countDown();
        }

        public void onError(Exception error) {
            this.error = error;
            done.countDown();
        }

        T result() throws Exception {
            assertTrue("Repository callback timed out", done.await(90, TimeUnit.SECONDS));
            if (error != null) throw new Exception("Repository callback failed", error);
            return value;
        }

        void rejected() throws Exception {
            assertTrue(done.await(90, TimeUnit.SECONDS));
            assertNotNull("Operation should be rejected", error);
        }
    }

    private <T> T task(Task<T> task) throws Exception {
        return Tasks.await(task, 90, TimeUnit.SECONDS);
    }

    private Actor actor(String label, String role) throws Exception {
        Actor a = new Actor();
        a.app =
                FirebaseApp.initializeApp(
                        context, FirebaseApp.getInstance().getOptions(), run + label);
        a.auth = FirebaseAuth.getInstance(a.app);
        a.db = FirebaseFirestore.getInstance(a.app, FirebaseConfig.FIRESTORE_DATABASE_ID);
        if (!cloud) a.db.useEmulator(BuildConfig.FIRESTORE_EMULATOR_HOST, 8085);
        a.db.setFirestoreSettings(
                new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
        a.email = cloud ? demo.getProperty(label + ".email") : run + label + "@example.com";
        a.password = cloud ? demo.getProperty(label + ".password") : UUID.randomUUID() + "aA1!";
        if (cloud) task(a.auth.signInWithEmailAndPassword(a.email, a.password));
        else task(a.auth.createUserWithEmailAndPassword(a.email, a.password));
        a.uid = a.auth.getUid();
        actors.add(a);
        if (cloud) {
            assertEquals(demo.getProperty(label + ".uid"), a.uid);
            DocumentSnapshot user =
                    task(a.db.collection("users").document(a.uid).get(Source.SERVER));
            assertEquals(role, user.getString("role"));
            assertEquals("ACTIVE", user.getString("status"));
            return a;
        }
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", a.uid);
        profile.put("name", "Java " + role);
        profile.put("role", role);
        profile.put("status", "ACTIVE");
        profile.put("schemaVersion", 1);
        profile.put("email", a.email);
        profile.put("phone", "+84900000000");
        rest("PATCH", "users/" + a.uid, new JSONObject().put("fields", fields(profile)));
        return a;
    }

    private JSONObject fields(Map<String, Object> map) throws Exception {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, Object> entry : map.entrySet())
            out.put(entry.getKey(), value(entry.getValue()));
        return out;
    }

    private JSONObject value(Object value) throws Exception {
        if (value == null) return new JSONObject().put("nullValue", JSONObject.NULL);
        if (value instanceof com.google.firebase.Timestamp)
            value = ((com.google.firebase.Timestamp) value).toDate();
        if (value instanceof Date)
            return new JSONObject().put("timestampValue", ((Date) value).toInstant().toString());
        if (value instanceof Map)
            return new JSONObject()
                    .put(
                            "mapValue",
                            new JSONObject().put("fields", fields((Map<String, Object>) value)));
        if (value instanceof List) {
            JSONArray items = new JSONArray();
            for (Object item : (List<?>) value) items.put(value(item));
            return new JSONObject().put("arrayValue", new JSONObject().put("values", items));
        }
        if (value instanceof String) return new JSONObject().put("stringValue", value);
        if (value instanceof Boolean) return new JSONObject().put("booleanValue", value);
        if (value instanceof Double || value instanceof Float)
            return new JSONObject().put("doubleValue", value);
        if (value instanceof Number) return new JSONObject().put("integerValue", value.toString());
        throw new IllegalArgumentException("Unsupported fixture field");
    }

    private void rest(String method, String path, JSONObject body) throws Exception {
        if (!cloud) assertEquals("10.0.2.2", BuildConfig.FIRESTORE_EMULATOR_HOST);
        String url =
                cloud
                        ? "http://10.0.2.2:8086/fixtures/" + path
                        : "http://10.0.2.2:8085/v1/projects/"
                                + project
                                + "/databases/"
                                + FirebaseConfig.FIRESTORE_DATABASE_ID
                                + "/documents/"
                                + path;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(cloud ? 60000 : 10000);
        connection.setRequestMethod(method);
        connection.setRequestProperty(
                "Authorization", "Bearer " + (cloud ? demo.getProperty("bridgeSecret") : "owner"));
        connection.setRequestProperty("Content-Type", "application/json");
        try {
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            assertTrue(
                    "Local fixture HTTP " + connection.getResponseCode(),
                    connection.getResponseCode() < 300);
        } finally {
            connection.disconnect();
        }
    }

    private void signIn(Actor a) throws Exception {
        task(auth.signInWithEmailAndPassword(a.email, a.password));
    }

    private Room room(String id, String building, String mode, LocalDate first) {
        Room r = new Room();
        r.setId(id);
        r.setBuildingId(building);
        r.setRoomName(
                cloud
                        ? "[DEMO] "
                                + ("FIXED".equals(mode)
                                        ? "Văn phòng theo tháng"
                                        : "Phòng họp Ánh Dương")
                        : "Nghiệm thu Java");
        r.setRoomType("Meeting");
        r.setAddress("TP.HCM");
        r.setFloor("1");
        r.setCapacity(5);
        r.setArea(20);
        r.setImageUrls(
                Collections.singletonList(
                        cloud ? demo.getProperty("photoUrl") : "https://example.com/test.jpg"));
        r.setOpenTime("08:00");
        r.setCloseTime("22:00");
        r.setActiveMode(mode);
        r.setPricePerHour(100000);
        r.setPricePerMonth(3000000);
        r.setPricePerQuarter(8000000);
        r.setDeposit(200000);
        r.setFixedStartMonth(YearMonth.from(first).toString());
        r.setRenewalPolicy("Thỏa thuận lại");
        r.setApprovalStatus("PENDING_REVIEW");
        return r;
    }

    private Booking request(String roomId, LocalDate day, int hour, int months) {
        Booking b = new Booking();
        b.roomId = roomId;
        b.startAt = Date.from(day.atTime(hour, 0).atZone(HostSchedule.ZONE).toInstant());
        b.endAt = months == 0 ? new Date(b.startAt.getTime() + 3600000) : null;
        b.rentalMonths = months;
        b.requestKey = UUID.randomUUID().toString();
        return b;
    }

    private String create(BookingRepository repo, Booking b) throws Exception {
        Answer<String> answer = new Answer<>();
        repo.create(b, answer);
        return answer.result();
    }

    private ReviewItem review(String kind, String id, String status) {
        ReviewItem item = new ReviewItem(kind, id, "", "");
        item.status = status;
        return item;
    }

    private void approve(Room room, Actor admin) throws Exception {
        signIn(admin);
        Answer<String> answer = new Answer<>();
        new AdminRepository()
                .review(review("ROOM", room.getId(), "ACTIVE"), true, "", false, answer);
        assertEquals(room.getId(), answer.result());
    }

    private void transition(BookingRepository repo, String id, String status, String reason)
            throws Exception {
        Answer<String> answer = new Answer<>();
        repo.transition(id, status, reason, answer);
        assertEquals(id, answer.result());
    }

    private void denied(Task<?> task) throws Exception {
        try {
            task(task);
            fail("Unauthorized write accepted");
        } catch (ExecutionException error) {
            assertTrue(error.getCause() instanceof FirebaseFirestoreException);
            assertEquals(
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    ((FirebaseFirestoreException) error.getCause()).getCode());
        }
    }

    private void awaitGuestScreen(ActivityScenario<MainActivity> screen) {
        AtomicBoolean ready = new AtomicBoolean();
        for (int attempt = 0; attempt < 80 && !ready.get(); attempt++) {
            screen.onActivity(
                    activity -> ready.set(activity.findViewById(R.id.publicRooms) != null));
            if (!ready.get()) SystemClock.sleep(250);
        }
        assertTrue("Guest screen failed to load through AuthViewModel", ready.get());
    }

    @Test
    public void javaBookingTransactionsAdminAndRules() throws Exception {
        // Đọc BuildConfig của APK đang cài, tránh hằng số bị inline từ một APK test emulator cũ.
        String installedHost = "";
        try {
            installedHost =
                    (String)
                            Class.forName(
                                            context.getPackageName() + ".BuildConfig",
                                            false,
                                            context.getClassLoader())
                                    .getField("FIRESTORE_EMULATOR_HOST")
                                    .get(null);
        } catch (ReflectiveOperationException ignored) {
        }
        assumeTrue(
                "Explicit Firestore emulator build required",
                cloud || "10.0.2.2".equals(installedHost));
        if (cloud) {
            assertEquals("Cloud acceptance requires a cloud APK", "", installedHost);
            assertEquals("broom-sharing-firebase-mobile", project);
            assertEquals("broomsharing", FirebaseConfig.FIRESTORE_DATABASE_ID);
            try (InputStream in =
                    new FileInputStream(
                            new File(
                                    context.getExternalFilesDir(null),
                                    "video-credentials.local.properties"))) {
                demo.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            run = demo.getProperty("businessRun");
            assertTrue(run != null && run.matches("video_[0-9]+_"));
        }
        int removed = 0;
        try {
            Actor host = actor("host", "HOST"),
                    guest = actor("guest", "GUEST"),
                    other = actor("other", "GUEST"),
                    admin = actor("admin", "ADMIN");
            signIn(host);
            task(
                    db.collection("publicHosts")
                            .document(host.uid)
                            .set(new PublicHost(host.uid, hostName(), "ACTIVE")));
            HostRepository hosts = new HostRepository();
            String buildingId = run + "building";
            Building building = new Building();
            building.setId(buildingId);
            building.setName(cloud ? "[DEMO] Không gian Ánh Dương" : "Tòa nhà Java");
            building.setAddress("TP.HCM");
            building.setDistrict("Quận 1");
            building.setContactName("Liên hệ thử");
            building.setContactPhone("0900000000");
            building.setLatitude(10.7725);
            building.setLongitude(106.698);
            Answer<String> buildingSaved = new Answer<>();
            hosts.addBuilding(building, buildingSaved);
            buildingSaved.result();
            LocalDate first = YearMonth.now(HostSchedule.ZONE).plusMonths(1).atDay(1),
                    day = first.plusDays(2);
            Room room = room(run + "room", buildingId, "HOURLY", first);
            Answer<String> saved = new Answer<>();
            hosts.saveRoom(room, saved);
            assertEquals(room.getId(), saved.result());
            approve(room, admin);
            signIn(guest);
            Answer<RoomListing> listing = new Answer<>();
            new RoomRepository().detail(room.getId(), listing);
            assertEquals(hostName(), listing.result().hostName);
            checkpoint("Host creates building/room; Admin approves; Guest reads published room");
            // SessionActivity must reach the actual Guest screen through AuthViewModel, also after
            // rotation.
            try (ActivityScenario<MainActivity> screen =
                    ActivityScenario.launch(MainActivity.class)) {
                awaitGuestScreen(screen);
                screen.recreate();
                awaitGuestScreen(screen);
            }
            denied(db.collection("users").document(guest.uid).update("role", "ADMIN"));
            denied(db.collection("rooms").document(room.getId()).update("pricePerHour", 1));
            checkpoint("Direct role and room-price forgery denied by cloud/emulator Rules");
            BookingRepository a = new BookingRepository(guest.db, guest.auth),
                    b = new BookingRepository(other.db, other.auth);
            Booking one = request(room.getId(), day, 9, 0), two = request(room.getId(), day, 9, 0);
            one.totalAmount = 1;
            one.priceSnapshot.put("totalAmount", 1);
            Answer<String> raceA = new Answer<>(), raceB = new Answer<>();
            a.create(one, raceA);
            b.create(two, raceB);
            assertTrue(raceA.done.await(90, TimeUnit.SECONDS));
            assertTrue(raceB.done.await(90, TimeUnit.SECONDS));
            assertEquals(
                    "Concurrent create: " + raceA.error + " / " + raceB.error,
                    1,
                    (raceA.error == null ? 1 : 0) + (raceB.error == null ? 1 : 0));
            Actor winner = raceA.error == null ? guest : other;
            BookingRepository winning = raceA.error == null ? a : b,
                    losing = raceA.error == null ? b : a;
            Booking winningRequest = raceA.error == null ? one : two;
            String bookingId = raceA.error == null ? raceA.value : raceB.value;
            Exception conflict = raceA.error == null ? raceB.error : raceA.error;
            assertFalse(
                    "Conflict must not be a rules/permission failure",
                    conflict instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) conflict).getCode()
                                    == FirebaseFirestoreException.Code.PERMISSION_DENIED);
            Booking persisted =
                    task(winner.db.collection("bookings").document(bookingId).get(Source.SERVER))
                            .toObject(Booking.class);
            assertEquals(100000, persisted.totalAmount, 0);
            assertEquals("UNAVAILABLE", persisted.paymentStatus);
            assertEquals(bookingId, create(winning, winningRequest));
            checkpoint(
                    "Two real guests race: one wins; price is recalculated; identical retry returns"
                        + " original booking");
            Answer<String> cannotConfirm = new Answer<>();
            winning.transition(bookingId, "CONFIRMED", "", cannotConfirm);
            cannotConfirm.rejected();
            String adjacent = create(losing, request(room.getId(), day, 10, 0));
            signIn(host);
            transition(new BookingRepository(), bookingId, "CONFIRMED", "");
            Room changed =
                    task(db.collection("rooms").document(room.getId()).get(Source.SERVER))
                            .toObject(Room.class);
            changed.setActiveMode("FIXED");
            changed.setApprovalStatus("PENDING_REVIEW");
            Answer<String> edit = new Answer<>();
            hosts.saveRoom(changed, edit);
            edit.rejected();
            denied(
                    db.collection("rooms")
                            .document(room.getId())
                            .update("activeMode", "FIXED", "approvalStatus", "PENDING_REVIEW"));
            denied(
                    db.collection("rooms")
                            .document(room.getId())
                            .update(
                                    "blackoutDates",
                                    Collections.singletonList(day.toString()),
                                    "approvalStatus",
                                    "PENDING_REVIEW"));
            denied(db.collection("rooms").document(room.getId()).update("activeBookingCount", 0));
            transition(winning, bookingId, "CANCELLED", "");
            String retry = create(losing, request(room.getId(), day, 9, 0));
            signIn(host);
            Answer<String> noReason = new Answer<>();
            new BookingRepository().transition(retry, "REJECTED", "", noReason);
            noReason.rejected();
            transition(new BookingRepository(), retry, "REJECTED", "Không phù hợp lịch");
            AvailabilityMonth month =
                    task(guest.db
                                    .collection("availabilityMonths")
                                    .document(
                                            BookingPolicy.monthKey(
                                                    room.getId(), YearMonth.from(day)))
                                    .get(Source.SERVER))
                            .toObject(AvailabilityMonth.class);
            assertEquals(2, month.slotsBusy.size());
            assertEquals(Collections.singleton(adjacent), month.hourlyBookings.keySet());
            checkpoint(
                    "Host confirms; occupied schedule and counter edits denied; cancel/reject"
                        + " release exactly their slots");
            signIn(admin);
            Answer<String> suspend = new Answer<>();
            new AdminRepository()
                    .review(
                            review("ROOM_STATUS", room.getId(), "ACTIVE"),
                            true,
                            "Tạm khóa thử",
                            false,
                            suspend);
            suspend.result();
            signIn(host);
            Answer<String> cannotOpen = new Answer<>();
            hosts.toggleListingStatus(room.getId(), "ACTIVE", cannotOpen);
            cannotOpen.rejected();
            signIn(admin);
            Answer<String> unsuspend = new Answer<>();
            new AdminRepository()
                    .review(
                            review("ROOM_STATUS", room.getId(), "SUSPENDED"),
                            true,
                            "Gỡ khóa thử",
                            false,
                            unsuspend);
            unsuspend.result();
            assertEquals(
                    "INACTIVE",
                    task(db.collection("rooms").document(room.getId()).get())
                            .getString("listingStatus"));
            signIn(host);
            Answer<String> reopen = new Answer<>();
            hosts.toggleListingStatus(room.getId(), "ACTIVE", reopen);
            reopen.result();
            signIn(admin);
            Answer<String> block = new Answer<>();
            new AdminRepository()
                    .review(
                            review("HOST_STATUS", host.uid, "ACTIVE"),
                            true,
                            "Kiểm tra khóa Host",
                            false,
                            block);
            block.result();
            signIn(guest);
            Answer<Booking> blockedQuote = new Answer<>();
            a.quote(request(room.getId(), day, 11, 0), blockedQuote);
            blockedQuote.rejected();
            Answer<RoomListing> hidden = new Answer<>();
            new RoomRepository().detail(room.getId(), hidden);
            hidden.rejected();
            signIn(admin);
            Answer<String> unblock = new Answer<>();
            new AdminRepository()
                    .review(
                            review("HOST_STATUS", host.uid, "BLOCKED"),
                            true,
                            "Khôi phục Host",
                            false,
                            unblock);
            unblock.result();
            checkpoint(
                    "Room suspension and Host block hide availability; unauthorized reopen denied;"
                        + " Admin restores access");
            signIn(host);
            Room fixed = room(run + "fixed", buildingId, "FIXED", first);
            Answer<String> fixedSaved = new Answer<>();
            hosts.saveRoom(fixed, fixedSaved);
            fixedSaved.result();
            approve(fixed, admin);
            Booking quarter = request(fixed.getId(), first, 0, 3);
            String fixedId = create(a, quarter);
            assertNotNull(fixedId);
            Answer<String> overlapping = new Answer<>();
            b.create(request(fixed.getId(), first.plusMonths(1), 0, 1), overlapping);
            overlapping.rejected();
            transition(a, fixedId, "CANCELLED", "");
            assertNotNull(create(b, request(fixed.getId(), first.plusMonths(1), 0, 1)));
            checkpoint(
                    "Quarter holds three months; overlap denied; cancellation allows the released"
                        + " month to be booked");
            Answer<List<Booking>> available = new Answer<>();
            a.availability(
                    room.getId(),
                    day.atStartOfDay(HostSchedule.ZONE).toInstant().toEpochMilli(),
                    day.plusDays(1).atStartOfDay(HostSchedule.ZONE).toInstant().toEpochMilli(),
                    available);
            for (Booking interval : available.result()) {
                assertNull(interval.guestUid);
                assertNull(interval.guestPhone);
            }
            businessCompletion(room, host, guest, other, admin, adjacent);
            if (cloud) prepareVideoState(room, host, guest, admin, day);
            passed = true;
            checkpoint("All business acceptance assertions completed");
        } finally {
            auth.signOut();
            for (Actor actor : actors) {
                try {
                    if (!cloud && actor.auth.getCurrentUser() != null) {
                        task(actor.auth.getCurrentUser().delete());
                        removed++;
                    }
                } finally {
                    task(actor.db.terminate());
                    actor.app.delete();
                }
            }
            try (FileOutputStream out =
                    new FileOutputStream(
                            new File(
                                    context.getExternalFilesDir(null),
                                    "java-firestore-cleanup.txt"))) {
                out.write(
                        ("Created test accounts: "
                                        + actors.size()
                                        + "; deleted: "
                                        + removed
                                        + (cloud
                                                ? "; retained for video; cloud business writes"
                                                      + " performed\n"
                                                : "; cloud Firestore writes: 0\n"))
                                .getBytes(StandardCharsets.UTF_8));
            }
            if (!cloud)
                assertEquals("All temporary Auth accounts must be removed", actors.size(), removed);
            else writeCloudReceipt();
        }
    }

    // Chuẩn bị mốc thời gian riêng; trên cloud bridge chỉ cho phép đúng prefix/UID của lượt demo.
    private void seed(String path, Map<String, Object> data) throws Exception {
        rest("PATCH", path, new JSONObject().put("fields", fields(data)));
    }

    private void businessCompletion(
            Room room, Actor host, Actor guest, Actor other, Actor admin, String activeId)
            throws Exception {
        signIn(guest);
        BookingRepository bookings = new BookingRepository();
        Answer<String> early = new Answer<>();
        bookings.transition(activeId, "EXPIRED", "", early);
        early.rejected();
        denied(
                db.collection("bookings")
                        .document(activeId)
                        .update("status", "EXPIRED", "updatedAt", FieldValue.serverTimestamp()));
        ChatRepository chat = new ChatRepository();
        Answer<String> opened = new Answer<>();
        chat.openRoom(room.getId(), opened);
        String threadId = opened.result();
        Answer<String> sent = new Answer<>();
        chat.send(
                threadId,
                run + "msg",
                "Tin nghiệm thu trước khi Host bị khóa.",
                room.getId(),
                sent);
        sent.result();
        Answer<String> resend = new Answer<>();
        chat.send(
                threadId,
                run + "msg",
                "Tin nghiệm thu trước khi Host bị khóa.",
                room.getId(),
                resend);
        resend.result();
        signIn(other);
        denied(db.collection("conversations").document(threadId).get(Source.SERVER));
        signIn(admin);
        denied(db.collection("conversations").document(threadId).get(Source.SERVER));
        signIn(host);
        Answer<String> answered = new Answer<>();
        chat.send(
                threadId,
                run + "reply",
                "Host trả lời bằng giao dịch chat.",
                room.getId(),
                answered);
        answered.result();
        denied(db.collection("conversations").document(threadId).update("guestRead", 1));
        signIn(guest);
        Answer<String> read = new Answer<>();
        chat.markRead(threadId, 1, read);
        read.result();
        DocumentSnapshot conversation =
                task(db.collection("conversations").document(threadId).get(Source.SERVER));
        assertEquals(2L, conversation.getLong("sequence").longValue());
        assertEquals(1L, conversation.getLong("guestRead").longValue());
        checkpoint(
                "Real chat transaction/retry and read counters match; outsider and Admin"
                    + " private-chat reads denied");

        // Tạo một lịch đã đến giờ bằng cùng BookingPolicy, không chờ đồng hồ chạy trong test.
        Booking request =
                request(room.getId(), LocalDate.now(HostSchedule.ZONE).minusDays(1), 9, 0);
        Booking past = BookingPolicy.plan(room, request, request.startAt.getTime() - 3600000);
        past.id = BookingPolicy.hash(run + "expired");
        past.guestUid = guest.uid;
        past.ownerUid = host.uid;
        signIn(admin);
        Map<String, Object> data =
                new HashMap<>(
                        task(db.collection("bookings").document(activeId).get(Source.SERVER))
                                .getData());
        data.put("id", past.id);
        data.put("guestUid", guest.uid);
        DocumentSnapshot historicalGuest =
                task(guest.db.collection("users").document(guest.uid).get(Source.SERVER));
        data.put("guestName", historicalGuest.getString("name"));
        data.put("guestPhone", historicalGuest.getString("phone"));
        data.put("ownerUid", host.uid);
        data.put("status", "PENDING");
        data.put("startAt", past.startAt);
        data.put("endAt", past.endAt);
        data.put("monthKeys", past.monthKeys);
        data.put("slotKeys", past.slotKeys);
        data.put("slotStart", past.slotStart);
        data.put("slotCount", past.slotCount);
        data.put("createdAt", new Date(past.startAt.getTime() - 3600000));
        data.put("updatedAt", new Date(past.startAt.getTime() - 3600000));
        seed("bookings/" + past.id, data);
        Map<String, Object> month = new HashMap<>();
        month.put("roomId", room.getId());
        month.put(
                "monthIndex",
                Integer.parseInt(
                        past.monthKeys
                                .get(0)
                                .substring(past.monthKeys.get(0).lastIndexOf('_') + 1)));
        month.put("fixedBookingId", "");
        month.put("slotsBusy", past.slotKeys);
        month.put("hourlyBookings", Collections.singletonMap(past.id, past.slotKeys));
        month.put("lastBookingId", past.id);
        month.put("updatedAt", new Date());
        seed("availabilityMonths/" + past.monthKeys.get(0), month);
        long before =
                task(db.collection("rooms").document(room.getId()).get(Source.SERVER))
                        .getLong("activeBookingCount");
        signIn(admin);
        task(
                db.collection("rooms")
                        .document(room.getId())
                        .update("activeBookingCount", before + 1));
        signIn(guest);
        CountDownLatch expired = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Exception> expiryError =
                new java.util.concurrent.atomic.AtomicReference<>();
        final String pendingId = past.id;
        ListenerRegistration expiryWatch =
                bookings.watchMine(
                        guest.uid,
                        new vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback<
                                List<Booking>>() {
                            public void onSuccess(List<Booking> rows) {
                                for (Booking row : rows)
                                    if (pendingId.equals(row.id) && "EXPIRED".equals(row.status))
                                        expired.countDown();
                            }

                            public void onError(Exception e) {
                                expiryError.set(e);
                                expired.countDown();
                            }
                        });
        try {
            assertTrue("Listener không đối soát hết hạn", expired.await(45, TimeUnit.SECONDS));
            if (expiryError.get() != null)
                throw new Exception("Expiry reconciliation failed", expiryError.get());
        } finally {
            expiryWatch.remove();
        }
        transition(bookings, past.id, "EXPIRED", "");
        assertEquals(
                "EXPIRED",
                task(db.collection("bookings").document(past.id).get(Source.SERVER))
                        .getString("status"));
        assertTrue(
                task(db.collection("availabilityMonths")
                                .document(past.monthKeys.get(0))
                                .get(Source.SERVER))
                        .toObject(AvailabilityMonth.class)
                        .slotsBusy
                        .isEmpty());
        assertEquals(
                before,
                task(db.collection("rooms").document(room.getId()).get(Source.SERVER))
                        .getLong("activeBookingCount")
                        .longValue());
        checkpoint(
                "PENDING at past start expires via listener; history retained; holds and count"
                    + " released once");
        signIn(admin);
        assertTrue(
                task(db.collection("auditLogs")
                                .document("booking_" + past.id + "_EXPIRED")
                                .get(Source.SERVER))
                        .exists());
        String checkedId = currentFixedCheckIn(room, host, guest, admin, data);
        checkpoint("Host checks in current-month FIXED booking using actual server time");

        // Review: đúng booking, một lần duy nhất, không tự nhận xét thay người thuê.
        String expiredId = past.id;
        past.id = BookingPolicy.hash(run + "completed");
        data.put("id", past.id);
        data.put("status", "CHECKED_IN");
        seed("bookings/" + past.id, data);
        month.put("hourlyBookings", Collections.singletonMap(past.id, past.slotKeys));
        month.put("lastBookingId", past.id);
        seed("availabilityMonths/" + past.monthKeys.get(0), month);
        task(
                db.collection("rooms")
                        .document(room.getId())
                        .update("activeBookingCount", before + 1));
        signIn(host);
        transition(bookings, past.id, "COMPLETED", "");
        signIn(admin);
        assertEquals(
                "EXPIRED",
                task(db.collection("bookings").document(expiredId).get(Source.SERVER))
                        .getString("status"));
        signIn(guest);
        ReviewRepository reviews = new ReviewRepository();
        Answer<String> review = new Answer<>();
        reviews.submit(past.id, 5, "Không gian phù hợp và sạch sẽ.", review);
        review.result();
        Answer<String> duplicate = new Answer<>();
        reviews.submit(past.id, 5, "Không gian phù hợp và sạch sẽ.", duplicate);
        duplicate.result();
        Answer<String> changed = new Answer<>();
        reviews.submit(past.id, 1, "Nội dung thay đổi lần thứ hai.", changed);
        changed.rejected();
        denied(db.collection("reviews").document(past.id).update("rating", 1));
        denied(db.collection("reviews").document(past.id).delete());
        signIn(other);
        Answer<String> foreign = new Answer<>();
        reviews.submit(past.id, 5, "Đánh giá thay một người khác.", foreign);
        foreign.rejected();
        Map<String, Object> forged =
                new HashMap<>(
                        task(db.collection("reviews").document(past.id).get(Source.SERVER))
                                .getData());
        forged.put("bookingId", activeId);
        forged.put("guestUid", other.uid);
        forged.put("createdAt", FieldValue.serverTimestamp());
        denied(db.collection("reviews").document(activeId).set(forged));
        checkpoint(
                "Host completes past checked-in stay; Guest reviews once; edits/deletion/foreign"
                    + " reviews denied");

        // Host phản hồi báo cáo, không được tự đóng hoặc sửa kết luận của Admin.
        signIn(guest);
        ReportRepository reports = new ReportRepository();
        String reportId = run + "report";
        Answer<String> report = new Answer<>();
        reports.submit(
                reportId,
                room.getId(),
                ReportRepository.REASONS.get(0),
                "Đối chiếu thông tin phòng trong bản nghiệm thu.",
                report);
        report.result();
        signIn(other);
        Answer<String> noReply = new Answer<>();
        reports.reply(reportId, "Tài khoản không sở hữu phòng.", noReply);
        noReply.rejected();
        signIn(host);
        Answer<String> reply = new Answer<>();
        reports.reply(reportId, "Host đã kiểm tra và gửi thông tin bổ sung.", reply);
        reply.result();
        denied(db.collection("reports").document(reportId).update("status", "RESOLVED"));
        signIn(admin);
        Answer<String> resolve = new Answer<>();
        reports.resolve(
                reportId,
                "RESOLVED",
                "NO_ACTION",
                "Đã đối chiếu nội dung báo cáo và phản hồi.",
                resolve);
        resolve.result();
        signIn(host);
        Answer<String> closed = new Answer<>();
        reports.reply(reportId, "Không được sửa báo cáo đã kết luận.", closed);
        closed.rejected();
        checkpoint(
                "Guest report; Host reply; Admin resolution; outsider reply and closed-report edits"
                    + " denied");

        // Nghiệm thu màn hình mới và xoay màn hình bằng tài khoản Guest thật.
        signIn(guest);
        Booking calendarSelection =
                request(room.getId(), LocalDate.now(HostSchedule.ZONE).plusDays(10), 12, 0);
        String calendarId = create(bookings, calendarSelection);
        signIn(host);
        transition(bookings, calendarId, "CONFIRMED", "");
        signIn(guest);
        Booking calendar =
                task(db.collection("bookings").document(calendarId).get(Source.SERVER))
                        .toObject(Booking.class);
        android.content.Intent intent =
                new android.content.Intent(context, RoomReviewsActivity.class)
                        .putExtra("roomId", room.getId())
                        .putExtra("bookingId", past.id);
        try (ActivityScenario<RoomReviewsActivity> screen = ActivityScenario.launch(intent)) {
            awaitView(screen, R.id.reviewList);
            screen.recreate();
            awaitView(screen, R.id.reviewList);
            awaitReviewData(screen);
            capture("reviews.png");
            android.app.Instrumentation instrumentation =
                    InstrumentationRegistry.getInstrumentation();
            java.util.concurrent.atomic.AtomicReference<android.content.Intent> dispatched =
                    new java.util.concurrent.atomic.AtomicReference<>();
            android.app.Instrumentation.ActivityMonitor monitor =
                    new android.app.Instrumentation.ActivityMonitor() {
                        @Override
                        public android.app.Instrumentation.ActivityResult onStartActivity(
                                android.content.Intent intent) {
                            if (!android.content.Intent.ACTION_INSERT.equals(intent.getAction()))
                                return null;
                            dispatched.set(intent);
                            return new android.app.Instrumentation.ActivityResult(
                                    android.app.Activity.RESULT_CANCELED, null);
                        }
                    };
            instrumentation.addMonitor(monitor);
            try {
                android.content.Intent event =
                        vn.edu.ueh.phuclu.broomsharing.utils.BookingCalendar.eventIntent(calendar);
                assertEquals(
                        calendar.startAt.getTime(),
                        event.getLongExtra(
                                android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0));
                assertEquals(
                        calendar.endAt.getTime(),
                        event.getLongExtra(
                                android.provider.CalendarContract.EXTRA_EVENT_END_TIME, 0));
                assertEquals(
                        calendar.roomName,
                        event.getStringExtra(android.provider.CalendarContract.Events.TITLE));
                screen.onActivity(
                        a ->
                                vn.edu.ueh.phuclu.broomsharing.utils.BookingCalendar.open(
                                        a, calendar));
                assertNotNull("Calendar Intent chưa được chuyển giao", dispatched.get());
                assertEquals(
                        calendar.startAt.getTime(),
                        dispatched
                                .get()
                                .getLongExtra(
                                        android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                                        0));
                checkpoint(
                        "Calendar ACTION_INSERT carries booking dates and title; device event is"
                            + " not silently created");
            } finally {
                instrumentation.removeMonitor(monitor);
            }
        }
        verifyCalendarButton(calendar);
        transition(bookings, calendarId, "CANCELLED", "Kết thúc kiểm tra lưu lịch thiết bị.");
        try (ActivityScenario<ReportCenterActivity> screen =
                ActivityScenario.launch(ReportCenterActivity.class)) {
            awaitView(screen, R.id.items);
            screen.recreate();
            awaitView(screen, R.id.items);
            AtomicBoolean ready = new AtomicBoolean();
            for (int i = 0; i < 80 && !ready.get(); i++) {
                screen.onActivity(
                        a -> {
                            vn.edu.ueh.phuclu.broomsharing.model.UiState<?> state =
                                    new ViewModelProvider(a)
                                            .get(ReportCenterViewModel.class)
                                            .rows
                                            .getValue();
                            if (state != null) {
                                assertNull(state.error);
                                ready.set(state.data != null);
                            }
                        });
                if (!ready.get()) SystemClock.sleep(250);
            }
            assertTrue("Danh sách báo cáo chưa nhận dữ liệu server", ready.get());
            capture("reports.png");
        }
        try (ActivityScenario<BookingNoticesActivity> screen =
                ActivityScenario.launch(BookingNoticesActivity.class)) {
            AtomicBoolean ready = new AtomicBoolean();
            for (int i = 0; i < 100 && !ready.get(); i++) {
                screen.onActivity(
                        a -> {
                            vn.edu.ueh.phuclu.broomsharing.model.UiState<?> state =
                                    new ViewModelProvider(a)
                                            .get(BookingNoticeViewModel.class)
                                            .notices
                                            .getValue();
                            if (state != null) {
                                assertNull(state.error);
                                ready.set(state.data != null);
                            }
                        });
                if (!ready.get()) SystemClock.sleep(250);
            }
            assertTrue("Hộp thông báo chưa đồng bộ server", ready.get());
            capture("notices.png");
        }
        checkpoint(
                "Review/report screens survive rotation; notification inbox loads real server"
                    + " data");
        NoticeRepository receipts = new NoticeRepository();
        Answer<String> receipt = new Answer<>();
        receipts.read(guest.uid, "acceptance:event", receipt);
        receipt.result();
        assertTrue(
                task(db.collection("users")
                                .document(guest.uid)
                                .collection("noticeReads")
                                .document("acceptance:event")
                                .get(Source.SERVER))
                        .exists());
        denied(
                db.collection("users")
                        .document(other.uid)
                        .collection("noticeReads")
                        .document("acceptance:event")
                        .set(Collections.singletonMap("readAt", FieldValue.serverTimestamp())));
        checkpoint("Notice receipt saved under correct UID; cross-user receipt rejected");
        signIn(admin);
        Answer<String> notBlocked = new Answer<>();
        bookings.transition(activeId, "CANCELLED", "Kiểm tra quyền Admin.", notBlocked);
        notBlocked.rejected();
        LocalDate activeDay =
                task(db.collection("bookings").document(activeId).get(Source.SERVER))
                        .toObject(Booking.class)
                        .startAt
                        .toInstant()
                        .atZone(HostSchedule.ZONE)
                        .toLocalDate();
        signIn(guest);
        String extraPending = create(bookings, request(room.getId(), activeDay, 14, 0));
        signIn(host);
        transition(bookings, activeId, "CONFIRMED", "");
        try (ActivityScenario<HostDashboardActivity> screen =
                ActivityScenario.launch(HostDashboardActivity.class)) {
            awaitView(screen, R.id.workspaceRoot);
            // Admin thứ hai thu hồi quyền trong lúc Host vẫn đang mở màn hình cũ.
            WriteBatch block = admin.db.batch();
            block.update(admin.db.collection("users").document(host.uid), "status", "BLOCKED");
            block.set(
                    admin.db.collection("publicHosts").document(host.uid),
                    new PublicHost(host.uid, hostName(), "BLOCKED"));
            task(block.commit());
            awaitView(screen, R.id.authMessage);
            capture("host-blocked.png");
            screen.onActivity(
                    a ->
                            assertEquals(
                                    a.getString(R.string.account_blocked),
                                    ((android.widget.TextView) a.findViewById(R.id.authMessage))
                                            .getText()
                                            .toString()));
        }
        signIn(admin);
        transition(bookings, activeId, "CANCELLED", "Host bị khóa, Admin xử lý đơn này.");
        transition(bookings, activeId, "CANCELLED", "Host bị khóa, Admin xử lý đơn này.");
        transition(
                bookings,
                extraPending,
                "CANCELLED",
                "Admin xử lý riêng đơn còn chờ của Host bị khóa.");
        assertEquals(
                "CANCELLED",
                task(db.collection("bookings").document(activeId).get(Source.SERVER))
                        .getString("status"));
        assertEquals(
                0,
                task(db.collection("rooms").document(room.getId()).get(Source.SERVER))
                        .getLong("activeBookingCount")
                        .longValue());
        Answer<String> reconcile = new Answer<>();
        bookings.reconcileRoomCount(room.getId(), reconcile);
        reconcile.result();
        Answer<String> noFinish = new Answer<>();
        bookings.transition(
                checkedId, "CANCELLED", "Không tự kết thúc đơn đã vào phòng.", noFinish);
        noFinish.rejected();
        assertEquals(
                "CHECKED_IN",
                task(db.collection("bookings").document(checkedId).get(Source.SERVER))
                        .getString("status"));
        task(db.collection("rooms").document(room.getId()).update("listingStatus", "SUSPENDED"));
        signIn(guest);
        Answer<String> blockedChat = new Answer<>();
        chat.send(
                threadId,
                run + "blocked",
                "Không được gửi đến Host bị khóa.",
                room.getId(),
                blockedChat);
        blockedChat.rejected();
        Answer<String> bookingReport = new Answer<>();
        reports.submit(
                run + "booking_report",
                room.getId(),
                past.id,
                ReportRepository.REASONS.get(3),
                "Báo cáo từ lịch sử thuê dù phòng đã bị đình chỉ.",
                bookingReport);
        bookingReport.result();
        assertEquals(
                past.id,
                task(db.collection("reports").document(run + "booking_report").get(Source.SERVER))
                        .getString("bookingId"));
        signIn(other);
        Answer<String> foreignReport = new Answer<>();
        reports.submit(
                run + "foreign_report",
                room.getId(),
                past.id,
                ReportRepository.REASONS.get(3),
                "Không được mạo nhận lượt thuê của người khác.",
                foreignReport);
        foreignReport.rejected();
        checkpoint(
                "Live Host block revokes screen; Admin cancels individual pending/confirmed"
                    + " bookings with audit; checked-in cancellation denied; booking-context report"
                    + " survives suspension");
    }

    // Lịch sử FIXED đang trong tháng hiện tại cho phép kiểm check-in bằng request.time thật.
    private String currentFixedCheckIn(
            Room base, Actor host, Actor guest, Actor admin, Map<String, Object> template)
            throws Exception {
        signIn(admin);
        DocumentSnapshot original =
                task(db.collection("rooms").document(base.getId()).get(Source.SERVER));
        Room room = original.toObject(Room.class);
        String roomId = run + "ongoingFixed";
        YearMonth current = YearMonth.now(HostSchedule.ZONE);
        room.setId(roomId);
        room.setActiveMode("FIXED");
        room.setFixedStartMonth(current.toString());
        Booking selection = request(roomId, current.atDay(1), 0, 1);
        Booking plan = BookingPolicy.plan(room, selection, selection.startAt.getTime() - 3600000);
        String id = BookingPolicy.hash(run + "checkin");
        Date created = new Date(plan.startAt.getTime() - 3600000);
        Map<String, Object> roomData = new HashMap<>(original.getData());
        roomData.put("id", roomId);
        roomData.put("activeMode", "FIXED");
        roomData.put("fixedStartMonth", current.toString());
        roomData.put("activeBookingCount", 1);
        roomData.put("bookingRevision", 0);
        roomData.put("lastBookingId", id);
        roomData.put("createdAt", created);
        roomData.put("updatedAt", created);
        seed("rooms/" + roomId, roomData);
        Map<String, Object> booking = new HashMap<>(template);
        booking.put("id", id);
        booking.put("roomId", roomId);
        booking.put("guestUid", guest.uid);
        booking.put("ownerUid", host.uid);
        booking.put("activeMode", "FIXED");
        booking.put("rentalMonths", 1);
        booking.put("startAt", plan.startAt);
        booking.put("endAt", plan.endAt);
        booking.put("slotStart", 0);
        booking.put("slotCount", 0);
        booking.put("slotKeys", Collections.emptyList());
        booking.put("monthKeys", plan.monthKeys);
        booking.put("totalAmount", plan.totalAmount);
        booking.put("priceSnapshot", plan.priceSnapshot);
        booking.put("status", "CONFIRMED");
        booking.put("createdAt", created);
        booking.put("updatedAt", created);
        seed("bookings/" + id, booking);
        Map<String, Object> month = new HashMap<>();
        month.put("roomId", roomId);
        month.put("monthIndex", current.getYear() * 100 + current.getMonthValue());
        month.put("fixedBookingId", id);
        month.put("slotsBusy", Collections.emptyList());
        month.put("hourlyBookings", Collections.emptyMap());
        month.put("lastBookingId", id);
        month.put("updatedAt", created);
        seed("availabilityMonths/" + plan.monthKeys.get(0), month);
        signIn(host);
        transition(new BookingRepository(), id, "CHECKED_IN", "");
        assertEquals(
                "CHECKED_IN",
                task(db.collection("bookings").document(id).get(Source.SERVER))
                        .getString("status"));
        signIn(admin);
        return id;
    }

    private void awaitReviewData(ActivityScenario<RoomReviewsActivity> screen) {
        AtomicBoolean ready = new AtomicBoolean();
        for (int i = 0; i < 80 && !ready.get(); i++) {
            screen.onActivity(
                    a -> {
                        vn.edu.ueh.phuclu.broomsharing.model.UiState<?> state =
                                new ViewModelProvider(a)
                                        .get(RoomReviewsViewModel.class)
                                        .rows
                                        .getValue();
                        if (state != null) {
                            assertNull(state.error);
                            ready.set(state.data != null);
                        }
                    });
            if (!ready.get()) SystemClock.sleep(250);
        }
        assertTrue("Danh sách đánh giá chưa nhận dữ liệu server", ready.get());
    }

    @Test
    public void cloudRetainedBookingDetailAfterVisualFix() throws Exception {
        assumeTrue(
                "Explicit retained-demo cloud read check required",
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("runCloudBusinessReadChecks")));
        String installedHost =
                (String)
                        Class.forName(
                                        context.getPackageName() + ".BuildConfig",
                                        false,
                                        context.getClassLoader())
                                .getField("FIRESTORE_EMULATOR_HOST")
                                .get(null);
        assertEquals("", installedHost);
        assertEquals("broom-sharing-firebase-mobile", project);
        try (Reader in =
                new InputStreamReader(
                        new FileInputStream(
                                new File(
                                        context.getExternalFilesDir(null),
                                        "video-credentials.local.properties")),
                        StandardCharsets.UTF_8)) {
            demo.load(in);
        }
        JSONObject state;
        try (Reader in =
                new InputStreamReader(
                        new FileInputStream(
                                new File(
                                        context.getExternalFilesDir(null),
                                        "video-business-state.json")),
                        StandardCharsets.UTF_8)) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[2048];
            int count;
            while ((count = in.read(buffer)) != -1) text.append(buffer, 0, count);
            state = new JSONObject(text.toString());
        }
        task(
                auth.signInWithEmailAndPassword(
                        demo.getProperty("guest.email"), demo.getProperty("guest.password")));
        try {
            String id = state.getString("confirmedBookingId");
            Booking booking =
                    task(db.collection("bookings").document(id).get(Source.SERVER))
                            .toObject(Booking.class);
            assertEquals("CONFIRMED", booking.status);
            assertEquals(100000, booking.totalAmount, 0);
            assertEquals(40000, ((Number) booking.priceSnapshot.get("depositAmount")).longValue());
            android.content.Intent intent =
                    new android.content.Intent(
                                    context,
                                    vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingSuccessActivity
                                            .class)
                            .putExtra("bookingId", id);
            try (ActivityScenario<vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingSuccessActivity>
                    screen = ActivityScenario.launch(intent)) {
                AtomicBoolean loaded = new AtomicBoolean();
                for (int i = 0; i < 120 && !loaded.get(); i++) {
                    screen.onActivity(
                            a -> {
                                android.widget.TextView total =
                                        a.findViewById(R.id.tvConfirmationTotal);
                                loaded.set(
                                        total != null
                                                && total.getText().toString().contains("100.000"));
                            });
                    if (!loaded.get()) SystemClock.sleep(250);
                }
                assertTrue("Chi tiết booking chưa nhận giá từ cloud", loaded.get());
                screen.onActivity(
                        a -> {
                            assertEquals(
                                    "Tổng tiền thuê và phí",
                                    ((android.widget.TextView)
                                                    a.findViewById(R.id.tvConfirmationTotalLabel))
                                            .getText()
                                            .toString());
                            assertTrue(
                                    ((android.widget.TextView)
                                                    a.findViewById(R.id.tvConfirmationDeposit))
                                            .getText()
                                            .toString()
                                            .contains("40.000"));
                        });
                screen.recreate();
                awaitView(screen, R.id.tvConfirmationTotalLabel);
                SystemClock.sleep(1000);
                capture("booking-confirmed-final.png");
            }
            verifyQuoteFormTotals(state.getString("roomId"));
        } finally {
            auth.signOut();
        }
    }

    // Chọn ngày và bấm báo giá thật để kiểm nhãn/tổng tiền cả trước và sau khi đặt.
    private void verifyQuoteFormTotals(String roomId) throws Exception {
        android.content.Intent intent = new android.content.Intent(context,
                vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingActivity.class)
                .putExtra(vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingActivity.ROOM_ID, roomId);
        try (ActivityScenario<vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingActivity> screen = ActivityScenario.launch(intent)) {
            AtomicBoolean loaded = new AtomicBoolean();
            for (int i = 0; i < 120 && !loaded.get(); i++) {
                screen.onActivity(a -> {
                    vn.edu.ueh.phuclu.broomsharing.model.UiState<Room> state = new ViewModelProvider(a)
                            .get(vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingViewModel.class).room.getValue();
                    loaded.set(state != null && state.data != null && state.error == null);
                });
                if (!loaded.get()) SystemClock.sleep(250);
            }
            assertTrue("Form đặt phòng chưa tải phòng cloud", loaded.get());
            screen.onActivity(a -> a.findViewById(R.id.dateButton).performClick());
            final LocalDate day = LocalDate.now(HostSchedule.ZONE).plusDays(2);
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.DatePicker.class))
                    .perform(new androidx.test.espresso.ViewAction() {
                        public org.hamcrest.Matcher<android.view.View> getConstraints() {
                            return androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.DatePicker.class);
                        }
                        public String getDescription() { return "Chọn ngày thuê tương lai trong DatePicker thật"; }
                        public void perform(androidx.test.espresso.UiController ui, android.view.View view) {
                            ((android.widget.DatePicker) view).updateDate(day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
                            ui.loopMainThreadUntilIdle();
                        }
                    });
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                    .perform(androidx.test.espresso.action.ViewActions.click());
            screen.onActivity(a -> a.findViewById(R.id.btnQuote).performClick());
            loaded.set(false);
            for (int i = 0; i < 120 && !loaded.get(); i++) {
                screen.onActivity(a -> loaded.set(((android.widget.TextView) a.findViewById(R.id.tvTotalAmount))
                        .getText().toString().contains("100.000")));
                if (!loaded.get()) SystemClock.sleep(250);
            }
            assertTrue("Tổng tiền form phải là 100.000đ, không phải khoản trả trước 40.000đ", loaded.get());
            screen.onActivity(a -> {
                assertTrue(((android.widget.TextView) a.findViewById(R.id.tvDepositAmount)).getText().toString().contains("40.000"));
                android.view.View total = a.findViewById(R.id.tvTotalAmount);
                total.requestRectangleOnScreen(new android.graphics.Rect(0, 0, total.getWidth(), total.getHeight()), true);
            });
            capture("booking-quote-final.png");
        }
    }

    private String hostName() {
        return cloud ? demo.getProperty("host.name") : "Java HOST";
    }

    private void checkpoint(String text) throws Exception {
        checks.add(text);
        if (cloud) writeCloudReceipt();
    }

    private void writeCloudReceipt() throws Exception {
        JSONObject receipt =
                new JSONObject()
                        .put("passed", passed)
                        .put("project", project)
                        .put("database", FirebaseConfig.FIRESTORE_DATABASE_ID)
                        .put("run", run)
                        .put("completedChecks", new JSONArray(checks))
                        .put("retainedRealAuthAccounts", actors.size())
                        .put(
                                "businessOperations",
                                "Real Android Repository/SDK with each role ID token and deployed"
                                    + " Rules")
                        .put(
                                "timeFixtures",
                                "Dedicated historical state prepared separately through restricted"
                                    + " local bridge; no server clock spoofing")
                        .put("atUTC", Instant.now().toString());
        try (Writer out =
                new OutputStreamWriter(
                        new FileOutputStream(
                                new File(
                                        context.getExternalFilesDir(null),
                                        "cloud-business-acceptance.json")),
                        StandardCharsets.UTF_8)) {
            out.write(receipt.toString(2));
        }
    }

    // Giữ các tài khoản và trạng thái dễ quay video; không thay đổi dữ liệu ngoài lượt thử.
    private void prepareVideoState(Room room, Actor host, Actor guest, Actor admin, LocalDate day)
            throws Exception {
        signIn(admin);
        Answer<String> unblock = new Answer<>();
        new AdminRepository()
                .review(
                        review("HOST_STATUS", host.uid, "BLOCKED"),
                        true,
                        "Khôi phục tài khoản demo sau nghiệm thu khóa Host.",
                        false,
                        unblock);
        unblock.result();
        Answer<String> unsuspend = new Answer<>();
        new AdminRepository()
                .review(
                        review("ROOM_STATUS", room.getId(), "SUSPENDED"),
                        true,
                        "Khôi phục phòng demo sau nghiệm thu.",
                        false,
                        unsuspend);
        unsuspend.result();
        signIn(host);
        Answer<String> publish = new Answer<>();
        new HostRepository().toggleListingStatus(room.getId(), "ACTIVE", publish);
        publish.result();
        signIn(guest);
        BookingRepository bookings = new BookingRepository();
        String pending = create(bookings, request(room.getId(), day.plusDays(1), 14, 0));
        String confirmed = create(bookings, request(room.getId(), day.plusDays(2), 14, 0));
        signIn(host);
        transition(bookings, confirmed, "CONFIRMED", "");
        signIn(admin);
        JSONObject state =
                new JSONObject()
                        .put("run", run)
                        .put("roomId", room.getId())
                        .put("buildingId", room.getBuildingId())
                        .put("pendingBookingId", pending)
                        .put("confirmedBookingId", confirmed)
                        .put("bookingDate", day.toString());
        JSONArray histories = new JSONArray();
        for (DocumentSnapshot row :
                task(
                        db.collection("bookings")
                                .whereEqualTo("ownerUid", host.uid)
                                .get(Source.SERVER))) {
            histories.put(
                    new JSONObject()
                            .put("id", row.getId())
                            .put("roomId", row.getString("roomId"))
                            .put("status", row.getString("status")));
        }
        state.put("bookings", histories);
        try (Writer out =
                new OutputStreamWriter(
                        new FileOutputStream(
                                new File(
                                        context.getExternalFilesDir(null),
                                        "video-business-state.json")),
                        StandardCharsets.UTF_8)) {
            out.write(state.toString(2));
        }
        checkpoint(
                "Demo accounts retained; Host restored ACTIVE; public room plus pending/confirmed"
                    + " bookings ready for video");
    }

    private void verifyCalendarButton(Booking calendar) throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        java.util.concurrent.atomic.AtomicReference<android.content.Intent> dispatched =
                new java.util.concurrent.atomic.AtomicReference<>();
        android.app.Instrumentation.ActivityMonitor monitor =
                new android.app.Instrumentation.ActivityMonitor() {
                    @Override
                    public android.app.Instrumentation.ActivityResult onStartActivity(
                            android.content.Intent intent) {
                        if (!android.content.Intent.ACTION_INSERT.equals(intent.getAction()))
                            return null;
                        dispatched.set(intent);
                        return new android.app.Instrumentation.ActivityResult(
                                android.app.Activity.RESULT_CANCELED, null);
                    }
                };
        instrumentation.addMonitor(monitor);
        android.content.Intent detail =
                new android.content.Intent(
                                context,
                                vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingSuccessActivity
                                        .class)
                        .putExtra("bookingId", calendar.id);
        try (ActivityScenario<vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingSuccessActivity>
                screen = ActivityScenario.launch(detail)) {
            AtomicBoolean ready = new AtomicBoolean();
            for (int i = 0; i < 120 && !ready.get(); i++) {
                screen.onActivity(
                        a -> {
                            android.view.View button = a.findViewById(R.id.bookingCalendar);
                            ready.set(
                                    button != null
                                            && button.getVisibility() == android.view.View.VISIBLE
                                            && button.isEnabled());
                        });
                if (!ready.get()) SystemClock.sleep(250);
            }
            assertTrue("Nút lưu lịch chưa sẵn sàng từ booking cloud", ready.get());
            capture("booking-confirmed.png");
            screen.onActivity(a -> a.findViewById(R.id.bookingCalendar).performClick());
            assertNotNull("Nút lưu lịch chưa chuyển Calendar Intent", dispatched.get());
            assertEquals(
                    calendar.startAt.getTime(),
                    dispatched
                            .get()
                            .getLongExtra(
                                    android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0));
            assertEquals(
                    calendar.endAt.getTime(),
                    dispatched
                            .get()
                            .getLongExtra(
                                    android.provider.CalendarContract.EXTRA_EVENT_END_TIME, 0));
            checkpoint(
                    "Actual confirmed booking detail button dispatches matching Calendar Intent");
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    private void capture(String file) throws Exception {
        android.graphics.Bitmap image =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        try (FileOutputStream out =
                new FileOutputStream(new File(context.getExternalFilesDir(null), file))) {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            image.recycle();
        }
    }

    private <T extends android.app.Activity> void awaitView(ActivityScenario<T> screen, int id) {
        AtomicBoolean found = new AtomicBoolean();
        for (int i = 0; i < 80 && !found.get(); i++) {
            screen.onActivity(a -> found.set(a.findViewById(id) != null));
            if (!found.get()) SystemClock.sleep(250);
        }
        assertTrue("Màn hình không sẵn sàng", found.get());
    }
}
