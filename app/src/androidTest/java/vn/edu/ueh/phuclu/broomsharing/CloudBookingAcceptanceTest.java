package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

/** Explicit opt-in: writes only dedicated QA fixtures; releases all holds and hides QA rooms. */
public class CloudBookingAcceptanceTest {
    private final android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();
    private final Properties credentials = new Properties();
    private final List<FirebaseApp> apps = new ArrayList<>();
    private final Map<String, BookingRepository> renterRepos = new HashMap<>();
    private final Map<String, String> created = new LinkedHashMap<>();
    private final List<String> checks = new ArrayList<>();
    private final String hourlyId = "qa_dot2_host1_hourly", fixedId = "qa_dot2_host1_fixed";

    private static class Answer<T> implements HostRepository.Callback<T> {
        final CountDownLatch done = new CountDownLatch(1);
        T value; Exception error;
        public void onSuccess(T value) { this.value = value; done.countDown(); }
        public void onError(Exception error) { this.error = error; done.countDown(); }
        T result() throws Exception {
            assertTrue("Cloud callback timed out", done.await(60, TimeUnit.SECONDS));
            if (error != null) throw error;
            return value;
        }
        void denied() throws Exception {
            assertTrue("Cloud callback timed out", done.await(60, TimeUnit.SECONDS));
            assertNotNull("Invalid operation was accepted", error);
        }
    }
    private <T> T task(Task<T> value) throws Exception { return Tasks.await(value, 60, TimeUnit.SECONDS); }
    private void signIn(String alias) throws Exception {
        task(auth.signInWithEmailAndPassword(credentials.getProperty(alias + ".email"), credentials.getProperty(alias + ".password")));
    }
    private BookingRepository renter(String alias) throws Exception {
        FirebaseApp app = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().getOptions(), "cloud-qa-" + alias + System.nanoTime());
        apps.add(app);
        FirebaseAuth login = FirebaseAuth.getInstance(app);
        FirebaseFirestore store = FirebaseFirestore.getInstance(app, FirebaseConfig.FIRESTORE_DATABASE_ID);
        store.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
        task(login.signInWithEmailAndPassword(credentials.getProperty(alias + ".email"), credentials.getProperty(alias + ".password")));
        BookingRepository repo = new BookingRepository(store, login);
        renterRepos.put(login.getUid(), repo);
        return repo;
    }
    private Room room(String id, String mode, String photo, YearMonth first) {
        Room room = new Room(); room.setId(id); room.setBuildingId("qa_dot2_host1_building");
        room.setRoomName("[DEMO][NGHIỆM THU] " + (Room.MODE_HOURLY.equals(mode) ? "Thuê giờ" : "Tháng / quý"));
        room.setRoomType("Meeting"); room.setAddress("[DEMO] Địa điểm nghiệm thu TP.HCM");
        room.setFloor("1"); room.setCapacity(5); room.setArea(20);
        room.setImageUrls(Collections.singletonList(photo)); room.setOpenTime("08:00"); room.setCloseTime("22:00");
        room.setActiveMode(mode); room.setPricePerHour(100000); room.setPricePerMonth(3000000); room.setPricePerQuarter(8000000);
        room.setDeposit(200000); room.setFixedStartMonth(first.toString()); room.setRenewalPolicy("Thỏa thuận lại khi gia hạn");
        room.setRules("[DEMO] Chỉ dùng nghiệm thu, không giao dịch thật."); room.setApprovalStatus("PENDING_REVIEW");
        return room;
    }
    private Booking request(String roomId, LocalDate day, int hour, int months) {
        Booking request = new Booking(); request.roomId = roomId;
        request.startAt = Date.from(day.atTime(hour, 0).atZone(HostSchedule.ZONE).toInstant());
        request.endAt = months == 0 ? new Date(request.startAt.getTime() + 3600000) : null;
        request.rentalMonths = months; request.requestKey = UUID.randomUUID().toString();
        return request;
    }
    private String create(BookingRepository repo, Booking request) throws Exception {
        Answer<String> answer = new Answer<>(); repo.create(request, answer);
        String id = answer.result(); created.put(id, request.roomId); return id;
    }
    private void transition(BookingRepository repo, String id, String status) throws Exception {
        Answer<String> answer = new Answer<>(); repo.transition(id, status, "[DEMO] Nghiệm thu Đợt 2", answer);
        assertEquals(id, answer.result());
    }
    private void review(String kind, String id, boolean approve) throws Exception {
        Answer<String> answer = new Answer<>();
        ReviewItem item = new ReviewItem(kind, id, "", "");
        if ("ROOM_STATUS".equals(kind)) item.status = task(db.collection("rooms").document(id).get(Source.SERVER)).getString("listingStatus");
        new AdminRepository().review(item, approve, "[DEMO] Nghiệm thu Đợt 2", false, answer);
        assertEquals(id, answer.result());
    }
    private void denied(Task<?> request) throws Exception {
        try { task(request); fail("Unauthorized write was accepted"); }
        catch (ExecutionException error) {
            assertTrue(error.getCause() instanceof FirebaseFirestoreException);
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ((FirebaseFirestoreException) error.getCause()).getCode());
        }
    }

    @Test public void realCloudHourlyFixedConflictAndModeration() throws Exception {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runCloudBookingChecks")));
        assertTrue("Cloud build required", BuildConfig.FIRESTORE_EMULATOR_HOST.isEmpty());
        try (InputStream in = new FileInputStream(new File(context.getExternalFilesDir(null), "demo-credentials.properties"))) { credentials.load(in); }
        boolean success = false;
        try {
            signIn("host1");
            String owner = auth.getUid();
            task(db.collection("publicHosts").document(owner).set(new PublicHost(owner,
                    task(db.collection("users").document(owner).get(Source.SERVER)).getString("name"), "ACTIVE")));
            QuerySnapshot ownRooms = task(db.collection("rooms").whereEqualTo("ownerUid", owner).get(Source.SERVER));
            String photo = null;
            for (DocumentSnapshot row : ownRooms) {
                Room candidate = row.toObject(Room.class);
                if (candidate.getImageUrls() != null && !candidate.getImageUrls().isEmpty()) { photo = candidate.getImageUrls().get(0); break; }
            }
            assertNotNull("Existing demo image required; do not invent an image URL", photo);
            HostRepository hosts = new HostRepository();
            Building building = new Building(); building.setId("qa_dot2_host1_building");
            building.setName("[DEMO][NGHIỆM THU] TP.HCM"); building.setAddress("[DEMO] Địa điểm thử nghiệm TP.HCM");
            building.setDistrict("Quận 1"); building.setContactName("Liên hệ demo"); building.setContactPhone("0900000000");
            building.setLatitude(10.7769); building.setLongitude(106.7009);
            Answer<String> buildingSaved = new Answer<>(); hosts.addBuilding(building, buildingSaved); buildingSaved.result();
            YearMonth first = YearMonth.now(HostSchedule.ZONE).plusMonths(2);
            Room hourly = room(hourlyId, Room.MODE_HOURLY, photo, first), fixed = room(fixedId, Room.MODE_FIXED, photo, first);
            for (Room value : Arrays.asList(hourly, fixed)) { Answer<String> saved = new Answer<>(); hosts.saveRoom(value, saved); saved.result(); }
            checks.add("Host creates building and two pending listings through Java repositories");
            BookingRepository guest = renter("guest2"), other = renter("guest3");
            Booking hidden = request(hourlyId, first.atDay(3), 9, 0);
            Answer<Booking> unpublished = new Answer<>(); guest.quote(hidden, unpublished); unpublished.denied();
            checks.add("Pending listing cannot be booked");
            signIn("admin"); review("ROOM", hourlyId, true); review("ROOM", fixedId, true);
            checks.add("Admin approves both listings on cloud");

            Booking one = request(hourlyId, first.atDay(3), 9, 0), two = request(hourlyId, first.atDay(3), 9, 0);
            Answer<String> raceA = new Answer<>(), raceB = new Answer<>();
            guest.create(one, raceA); other.create(two, raceB);
            assertTrue(raceA.done.await(60, TimeUnit.SECONDS)); assertTrue(raceB.done.await(60, TimeUnit.SECONDS));
            assertTrue("Exactly one contender must win", (raceA.error == null) != (raceB.error == null));
            BookingRepository winner = raceA.error == null ? guest : other;
            Booking request = raceA.error == null ? one : two;
            String id = raceA.error == null ? raceA.value : raceB.value; created.put(id, hourlyId);
            assertEquals(id, create(winner, request));
            Booking persisted = task(db.collection("bookings").document(id).get(Source.SERVER)).toObject(Booking.class);
            assertEquals(100000, persisted.totalAmount, 0); assertEquals("DEMO_PENDING", persisted.paymentStatus);
            checks.add("Two authenticated guests race: one hold; retry returns same booking; server price and payment state match");
            Answer<String> illegal = new Answer<>(); winner.transition(id, "CONFIRMED", "", illegal); illegal.denied();
            signIn("host1"); transition(new BookingRepository(), id, "CONFIRMED");
            Room changed = task(db.collection("rooms").document(hourlyId).get(Source.SERVER)).toObject(Room.class);
            changed.setId(hourlyId); changed.setActiveMode(Room.MODE_FIXED); changed.setApprovalStatus("PENDING_REVIEW");
            Answer<String> sensitiveEdit = new Answer<>(); hosts.saveRoom(changed, sensitiveEdit); sensitiveEdit.denied();
            transition(winner, id, "CANCELLED");
            String replacement = create(guest, request(hourlyId, first.atDay(3), 9, 0));
            transition(new BookingRepository(), replacement, "REJECTED");
            checks.add("Guest cannot confirm; Host confirms; occupied room cannot switch mode; cancel/reject release slots");

            String quarter = create(guest, request(fixedId, first.atDay(1), 0, 3));
            Answer<String> overlap = new Answer<>(); other.create(request(fixedId, first.plusMonths(1).atDay(1), 0, 1), overlap); overlap.denied();
            transition(guest, quarter, "CANCELLED");
            String month = create(other, request(fixedId, first.plusMonths(1).atDay(1), 0, 1)); transition(other, month, "CANCELLED");
            checks.add("Quarter locks three calendar months; overlapping month rejected; cancellation releases all months");
            signIn("host1");
            Room closedDay = task(db.collection("rooms").document(hourlyId).get(Source.SERVER)).toObject(Room.class);
            closedDay.setId(hourlyId); closedDay.setApprovalStatus("PENDING_REVIEW");
            closedDay.setBlackoutDates(Collections.singletonList(first.atDay(4).toString()));
            Answer<String> calendarSaved = new Answer<>(); hosts.saveRoom(closedDay, calendarSaved); calendarSaved.result();
            signIn("admin"); review("ROOM", hourlyId, true);
            Answer<Booking> blackout = new Answer<>(); guest.quote(request(hourlyId, first.atDay(4), 9, 0), blackout); blackout.denied();
            Answer<Booking> closedHours = new Answer<>(); guest.quote(request(hourlyId, first.atDay(5), 7, 0), closedHours); closedHours.denied();
            checks.add("Host saves blackout dates; real quotes reject closed days and hours outside opening schedule");
            signIn("host1");
            Answer<String> pause = new Answer<>(); hosts.toggleListingStatus(hourlyId, "INACTIVE", pause); pause.result();
            Answer<String> notListed = new Answer<>(); guest.create(request(hourlyId, first.atDay(5), 9, 0), notListed); notListed.denied();
            Answer<String> resume = new Answer<>(); hosts.toggleListingStatus(hourlyId, "ACTIVE", resume); resume.result();
            checks.add("Host pauses and resumes new bookings without deleting booking history");
            signIn("admin"); review("ROOM_STATUS", hourlyId, true);
            Answer<String> suspended = new Answer<>(); guest.create(request(hourlyId, first.atDay(5), 9, 0), suspended); suspended.denied();
            review("ROOM_STATUS", hourlyId, true);
            checks.add("Admin suspension blocks new bookings and keeps existing booking history");
            signIn("guest2");
            denied(db.collection("users").document(auth.getUid()).update("role", "ADMIN"));
            denied(db.collection("bookings").document(replacement).update("totalAmount", 1));
            denied(db.collection("bookings").document(replacement).update("paymentStatus", "PAID"));
            checks.add("Cloud rules reject self-promotion, price tampering and fake PAID state");
            signIn("host4");
            Answer<List<Room>> blockedHost = new Answer<>();
            hosts.getRoomsByOwner(auth.getUid(), blockedHost); blockedHost.denied();
            checks.add("Blocked demo Host cannot access active Host operations");
            success = true;
        } finally {
            try {
                signIn("admin");
                for (String room : Arrays.asList(hourlyId, fixedId)) {
                    QuerySnapshot rows = task(db.collection("bookings").whereEqualTo("roomId", room).get(Source.SERVER));
                    for (DocumentSnapshot row : rows) {
                        Booking booking = row.toObject(Booking.class);
                        if (Arrays.asList("PENDING", "CONFIRMED").contains(booking.status)) {
                            BookingRepository repo = renterRepos.get(booking.guestUid);
                            assertNotNull("QA hold has no known renter", repo);
                            transition(repo, row.getId(), "CANCELLED");
                        }
                    }
                }
                signIn("host1");
                for (String room : Arrays.asList(hourlyId, fixedId)) {
                    if (task(db.collection("rooms").document(room).get(Source.SERVER)).exists()) {
                        Answer<String> paused = new Answer<>(); new HostRepository().toggleListingStatus(room, "INACTIVE", paused); paused.result();
                    }
                }
                signIn("admin");
                for (String room : Arrays.asList(hourlyId, fixedId)) {
                    QuerySnapshot calendars = task(db.collection("availabilityMonths").whereEqualTo("roomId", room).get(Source.SERVER));
                    for (DocumentSnapshot calendar : calendars) {
                        AvailabilityMonth month = calendar.toObject(AvailabilityMonth.class);
                        assertTrue("QA calendar retains a fixed hold", month.fixedBookingId.isEmpty());
                        assertTrue("QA calendar retains hourly holds", month.slotsBusy.isEmpty() && month.hourlyBookings.isEmpty());
                    }
                }
                JSONObject report = new JSONObject().put("passed", success).put("project", FirebaseApp.getInstance().getOptions().getProjectId())
                        .put("database", FirebaseConfig.FIRESTORE_DATABASE_ID).put("checks", new org.json.JSONArray(checks))
                        .put("bookingIds", new org.json.JSONArray(created.keySet())).put("fixturesHidden", true).put("holdsReleased", true)
                        .put("note", "Dedicated DEMO QA fixtures retained as audit evidence; no real payment; no existing demo roles changed.");
                try (Writer out = new OutputStreamWriter(new FileOutputStream(new File(context.getExternalFilesDir(null), "cloud-booking-verification.json")), java.nio.charset.StandardCharsets.UTF_8)) { out.write(report.toString(2)); }
            } finally {
                auth.signOut();
                for (FirebaseApp app : apps) { FirebaseAuth.getInstance(app).signOut(); FirebaseFirestore.getInstance(app, FirebaseConfig.FIRESTORE_DATABASE_ID).terminate(); app.delete(); }
            }
        }
    }
}
