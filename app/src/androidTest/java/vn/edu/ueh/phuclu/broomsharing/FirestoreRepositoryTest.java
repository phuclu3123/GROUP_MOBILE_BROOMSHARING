package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.*;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Real Android repositories + Auth, isolated local Firestore with production rules. No JS harness.
 */
@RunWith(AndroidJUnit4.class)
public class FirestoreRepositoryTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<Actor> actors = new ArrayList<>();
    private final String run = "java_" + System.currentTimeMillis();
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
            if (error != null) throw error;
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
        a.db.useEmulator(BuildConfig.FIRESTORE_EMULATOR_HOST, 8085);
        a.db.setFirestoreSettings(
                new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
        a.email = run + label + "@example.com";
        a.password = UUID.randomUUID() + "aA1!";
        task(a.auth.createUserWithEmailAndPassword(a.email, a.password));
        a.uid = a.auth.getUid();
        actors.add(a);
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
        if (value instanceof String) return new JSONObject().put("stringValue", value);
        if (value instanceof Boolean) return new JSONObject().put("booleanValue", value);
        if (value instanceof Number) return new JSONObject().put("integerValue", value.toString());
        throw new IllegalArgumentException("Unsupported fixture field");
    }

    private void rest(String method, String path, JSONObject body) throws Exception {
        assertEquals("10.0.2.2", BuildConfig.FIRESTORE_EMULATOR_HOST);
        String url =
                "http://10.0.2.2:8085/v1/projects/"
                        + project
                        + "/databases/"
                        + FirebaseConfig.FIRESTORE_DATABASE_ID
                        + "/documents/"
                        + path;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer owner");
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
        r.setRoomName("Nghiệm thu Java");
        r.setRoomType("Meeting");
        r.setAddress("TP.HCM");
        r.setFloor("1");
        r.setCapacity(5);
        r.setArea(20);
        r.setImageUrls(Collections.singletonList("https://example.com/test.jpg"));
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

    private void awaitGuestScreen(androidx.test.core.app.ActivityScenario<MainActivity> screen) {
        java.util.concurrent.atomic.AtomicBoolean ready =
                new java.util.concurrent.atomic.AtomicBoolean();
        for (int attempt = 0; attempt < 80 && !ready.get(); attempt++) {
            screen.onActivity(
                    activity -> ready.set(activity.findViewById(R.id.publicRooms) != null));
            if (!ready.get()) android.os.SystemClock.sleep(250);
        }
        assertTrue("Guest screen failed to load through AuthViewModel", ready.get());
    }

    @Test
    public void javaBookingTransactionsAdminAndRules() throws Exception {
        assumeTrue(
                "Explicit Firestore emulator build required",
                "10.0.2.2".equals(BuildConfig.FIRESTORE_EMULATOR_HOST));
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
                            .set(new PublicHost(host.uid, "Java HOST", "ACTIVE")));
            HostRepository hosts = new HostRepository();
            String buildingId = run + "building";
            Building building = new Building();
            building.setId(buildingId);
            building.setName("Tòa nhà Java");
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
            assertEquals("Java HOST", listing.result().hostName);
            // SessionActivity must reach the actual Guest screen through AuthViewModel, also after
            // rotation.
            try (androidx.test.core.app.ActivityScenario<MainActivity> screen =
                    androidx.test.core.app.ActivityScenario.launch(MainActivity.class)) {
                awaitGuestScreen(screen);
                screen.recreate();
                awaitGuestScreen(screen);
            }
            denied(db.collection("users").document(guest.uid).update("role", "ADMIN"));
            denied(db.collection("rooms").document(room.getId()).update("pricePerHour", 1));
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
        } finally {
            auth.signOut();
            for (Actor actor : actors) {
                try {
                    if (actor.auth.getCurrentUser() != null) {
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
                                        + "; cloud Firestore writes: 0\n")
                                .getBytes(StandardCharsets.UTF_8));
            }
            assertEquals("All temporary Auth accounts must be removed", actors.size(), removed);
        }
    }
}
