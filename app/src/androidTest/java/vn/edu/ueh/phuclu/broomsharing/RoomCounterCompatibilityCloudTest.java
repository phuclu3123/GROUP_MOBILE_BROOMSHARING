package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import org.json.JSONObject;
import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

// Chỉ đọc cloud sau chuyển đổi: kiểm Model, bộ đếm và điều kiện đổi chế độ trên dữ liệu thật.
public class RoomCounterCompatibilityCloudTest {
    private <T> T await(Task<T> task) throws Exception {
        return Tasks.await(task, 60, TimeUnit.SECONDS);
    }

    @Test
    public void repairedRoomsUseCurrentBookingContract() throws Exception {
        assumeTrue(
                "Explicit cloud read acceptance required",
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("runRoomCounterReadChecks")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String host =
                (String)
                        Class.forName(
                                        context.getPackageName() + ".BuildConfig",
                                        false,
                                        context.getClassLoader())
                                .getField("FIRESTORE_EMULATOR_HOST")
                                .get(null);
        assertEquals("", host);
        assertEquals(
                "broom-sharing-firebase-mobile",
                FirebaseApp.getInstance().getOptions().getProjectId());
        assertEquals("broomsharing", FirebaseConfig.FIRESTORE_DATABASE_ID);
        Properties credentials = new Properties();
        try (Reader in =
                new InputStreamReader(
                        new FileInputStream(
                                new File(
                                        context.getExternalFilesDir(null),
                                        "video-credentials.local.properties")),
                        StandardCharsets.UTF_8)) {
            credentials.load(in);
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        boolean passed = false;
        int roomsChecked = 0, emptyAllowed = 0, heldDenied = 0;
        try {
            await(
                    auth.signInWithEmailAndPassword(
                            credentials.getProperty("admin.email"),
                            credentials.getProperty("admin.password")));
            QuerySnapshot bookings = await(db.collection("bookings").get(Source.SERVER));
            QuerySnapshot months = await(db.collection("availabilityMonths").get(Source.SERVER));
            QuerySnapshot rooms = await(db.collection("rooms").get(Source.SERVER));
            Map<String, Long> counts = new HashMap<>();
            for (DocumentSnapshot doc : bookings) {
                Booking booking = doc.toObject(Booking.class);
                assertEquals(2, booking.schemaVersion);
                if (HostSchedule.occupies(booking))
                    counts.put(booking.roomId, counts.getOrDefault(booking.roomId, 0L) + 1);
            }
            Map<String, List<AvailabilityMonth>> schedules = new HashMap<>();
            for (DocumentSnapshot doc : months) {
                AvailabilityMonth month = doc.toObject(AvailabilityMonth.class);
                schedules.computeIfAbsent(month.roomId, key -> new ArrayList<>()).add(month);
            }
            for (DocumentSnapshot doc : rooms) {
                Room before = doc.toObject(Room.class);
                assertNotNull(
                        "Missing persisted counter: " + doc.getId(),
                        doc.getLong("activeBookingCount"));
                long count = counts.getOrDefault(doc.getId(), 0L);
                assertEquals("Counter mismatch: " + doc.getId(), count, before.activeBookingCount);
                Room next = doc.toObject(Room.class);
                next.setActiveMode(
                        Room.MODE_HOURLY.equals(before.getActiveMode())
                                ? Room.MODE_FIXED
                                : Room.MODE_HOURLY);
                if (count == 0) {
                    BookingPolicy.validateRoomChange(
                            before,
                            next,
                            schedules.getOrDefault(doc.getId(), Collections.emptyList()),
                            System.currentTimeMillis());
                    emptyAllowed++;
                } else {
                    try {
                        BookingPolicy.validateRoomChange(
                                before,
                                next,
                                schedules.getOrDefault(doc.getId(), Collections.emptyList()),
                                System.currentTimeMillis());
                        fail("Occupied room allowed mode change");
                    } catch (IllegalStateException expected) {
                        heldDenied++;
                    }
                }
                roomsChecked++;
            }
            assertTrue(emptyAllowed > 0);
            assertTrue(heldDenied > 0);
            // Constructor giữ fallback cũ; đường tạo mới phải dùng phiên bản hiện tại và đủ khóa.
            assertEquals(1, new Booking().schemaVersion);
            passed = true;
        } finally {
            auth.signOut();
            JSONObject receipt =
                    new JSONObject()
                            .put("passed", passed)
                            .put("roomsChecked", roomsChecked)
                            .put("emptyRoomsAllowModeChange", emptyAllowed)
                            .put("heldRoomsDenyModeChange", heldDenied)
                            .put("documentWrites", 0)
                            .put(
                                    "scope",
                                    "Real Firebase Android SDK reads and BookingPolicy on hydrated"
                                        + " models; no room mode was written");
            try (Writer out =
                    new OutputStreamWriter(
                            new FileOutputStream(
                                    new File(
                                            context.getExternalFilesDir(null),
                                            "room-counter-cloud-sdk.json")),
                            StandardCharsets.UTF_8)) {
                out.write(receipt.toString(2));
            }
        }
    }
}
