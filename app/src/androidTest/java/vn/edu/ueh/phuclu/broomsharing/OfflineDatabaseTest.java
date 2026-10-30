package vn.edu.ueh.phuclu.broomsharing;

import android.content.ContentValues;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.FirebaseFirestoreException;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.data.local.OfflineDatabase;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.OfflineRepository;
import static org.junit.Assert.*;

/*
    Chạy SQLite thật trong database riêng của kiểm thử, không đọc/ghi cloud.
    Kiểm tra lưu bền vững, cách ly UID, dữ liệu hỏng và giới hạn dung lượng.
 */
@RunWith(AndroidJUnit4.class)
public class OfflineDatabaseTest {
    private Context context;
    private OfflineDatabase db;
    private final String name = "offline_acceptance.db";

    @Before public void open() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(name);
        db = new OfflineDatabase(context, name);
    }

    @After public void close() { db.close(); context.deleteDatabase(name); }

    @Test public void roomAndBookingSurviveDatabaseReopen() throws Exception {
        Room room = new Room(); room.setId("room-1"); room.setRoomName("Phòng đã xem");
        room.setWorkingDays(Arrays.asList(1, 5)); room.setImageUrls(Arrays.asList("https://example.org/image.jpg"));
        RoomBlackout block = new RoomBlackout(new Date(100), new Date(200), "Bảo trì");
        room.setBlackoutRanges(Arrays.asList(block));
        Building place = new Building(); place.setName("Tòa nhà A");
        RoomListing listing = new RoomListing(room, place, "Host A");
        Booking booking = new Booking(); booking.id = "booking-1"; booking.guestUid = "alice";
        booking.status = Booking.STATUS_CONFIRMED; booking.createdAt = new Date(300);
        booking.priceSnapshot.put("depositAmount", 2000L);
        db.put("alice", "room", "room-1", listing, 1000);
        db.put("alice", "booking", booking.id, booking, 2000);
        db.close(); db = new OfflineDatabase(context, name);
        OfflineDatabase.Saved<RoomListing> saved = db.read("alice", "room", "room-1", RoomListing.class);
        assertEquals("Phòng đã xem", saved.data.room.getRoomName());
        assertEquals("Tòa nhà A", saved.data.building.getName());
        assertEquals("Bảo trì", saved.data.room.getBlackoutRanges().get(0).getReason());
        assertNull(saved.data.availability);
        assertEquals(1000, saved.savedAt);
        Booking restored = db.read("alice", "booking", booking.id, Booking.class).data;
        assertEquals(2000L, restored.priceSnapshot.get("depositAmount"));
        assertEquals(300, restored.createdAt.getTime());
    }

    @Test public void uidIsolatesSameKeysAndClearOnlyRemovesOneAccount() throws Exception {
        db.put("alice", "rooms", "list", "Alice", 1);
        db.put("bob", "rooms", "list", "Bob", 2);
        assertEquals("Alice", db.read("alice", "rooms", "list", String.class).data);
        assertNull(db.read("unknown", "rooms", "list", String.class));
        db.clear("alice");
        assertNull(db.read("alice", "rooms", "list", String.class));
        assertEquals("Bob", db.read("bob", "rooms", "list", String.class).data);
    }

    @Test public void replaceAndEmptyListDoNotReviveOldData() throws Exception {
        db.put("alice", "bookings", "list", new ArrayList<>(Arrays.asList("old")), 10);
        db.put("alice", "bookings", "list", new ArrayList<>(), 20);
        OfflineDatabase.Saved<ArrayList> saved = db.read("alice", "bookings", "list", ArrayList.class);
        assertTrue(saved.data.isEmpty()); assertEquals(20, saved.savedAt);
    }

    @Test public void corruptAndWrongTypeRowsAreDiscarded() throws Exception {
        ContentValues values = new ContentValues();
        values.put("uid", "alice"); values.put("kind", "room"); values.put("item_id", "bad");
        values.put("payload", new byte[]{1,2,3}); values.put("saved_at", 1);
        db.getWritableDatabase().insertOrThrow("snapshots", null, values);
        assertNull(db.read("alice", "room", "bad", RoomListing.class));
        db.put("alice", "room", "wrong", "not a room", 1);
        assertNull(db.read("alice", "room", "wrong", RoomListing.class));
    }

    @Test public void boundedCacheRetainsProfileAndOtherUid() throws Exception {
        db.put("alice", "profile", "current", new UserProfile("alice", "A", "a", "a@example.org", ""), 1);
        db.put("bob", "room", "first", "Bob", 1);
        for (int i = 0; i < 405; i++) db.put("alice", "room", "r" + i, "data", i + 2);
        assertNotNull(db.read("alice", "profile", "current", UserProfile.class));
        assertNull(db.read("alice", "room", "r0", String.class));
        assertNotNull(db.read("alice", "room", "r404", String.class));
        assertNotNull(db.read("bob", "room", "first", String.class));
    }

    @Test public void oversizedSnapshotDoesNotReplaceExistingRow() throws Exception {
        db.put("alice", "room", "r", "valid", 1);
        try { db.put("alice", "room", "r", new byte[800_000], 2); fail("Expected size limit"); }
        catch (java.io.IOException expected) { }
        assertEquals("valid", db.read("alice", "room", "r", String.class).data);
    }

    @Test public void permissionErrorsAreNeverCacheFallbacks() {
        assertFalse(OfflineRepository.networkFailure(new FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)));
        assertFalse(OfflineRepository.networkFailure(new FirebaseFirestoreException("missing", FirebaseFirestoreException.Code.NOT_FOUND)));
        assertTrue(OfflineRepository.networkFailure(new FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)));
        assertTrue(OfflineRepository.networkFailure(new java.io.IOException("network")));
        // SessionGuard phải phân biệt mất mạng với thu hồi quyền trước khi cập nhật UI.
        assertTrue(vn.edu.ueh.phuclu.broomsharing.model.UiState.error(
                new FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)).networkError);
        assertFalse(vn.edu.ueh.phuclu.broomsharing.model.UiState.error(
                new FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)).networkError);
    }
}
