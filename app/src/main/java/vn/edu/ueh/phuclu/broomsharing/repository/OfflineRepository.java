package vn.edu.ueh.phuclu.broomsharing.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestoreException;

import vn.edu.ueh.phuclu.broomsharing.data.local.OfflineDatabase;
import vn.edu.ueh.phuclu.broomsharing.model.*;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
   Bản lưu SQLite chạy I/O trên luồng nền, trả callback ở main thread.
   Chỉ đọc dữ liệu của UID hiện tại, không lưu mật khẩu/token hay xếp hàng thao tác ghi cloud.
*/
public final class OfflineRepository {
    private static Context context;
    private static OfflineDatabase database;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    // Khởi tạo một lần bằng Application Context để không giữ tham chiếu Activity.
    public static synchronized void initialize(Context app) {
        if (database == null) {
            context = app.getApplicationContext();
            database = new OfflineDatabase(context, OfflineDatabase.NAME);
        }
    }

    public static String uid() {
        try {
            return FirebaseAuth.getInstance().getUid();
        } catch (IllegalStateException unconfigured) {
            return null;
        }
    }

    // Chỉ coi là online khi mạng mặc định có Internet đã được Android xác nhận.
    public static boolean online() {
        if (context == null) return true;
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    // Chỉ lỗi mạng được dùng bản lưu; không dùng cache để lách quyền hoặc dữ liệu đã bị gỡ.
    public static boolean networkFailure(Exception error) {
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) error).getCode();
            return code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED;
        }
        return error instanceof java.io.IOException;
    }

    // Ghi trên hàng đợi nền; lỗi cache không được làm thất bại thao tác cloud đã hoàn tất.
    private static void save(String uid, String kind, String id, Serializable value) {
        if (database == null || uid == null) return;
        long now = System.currentTimeMillis();
        worker.execute(
                () -> {
                    try {
                        database.put(uid, kind, id, value, now);
                    } catch (Exception error) {
                        Log.w("OfflineRepository", "Không lưu được bản xem ngoại tuyến.");
                    }
                });
    }

    // Callback của tài khoản cũ không được đưa dữ liệu lên giao diện tài khoản mới.
    public static <T> void read(
            String kind,
            String id,
            Class<T> type,
            ResultCallback<OfflineDatabase.Saved<T>> callback) {
        String owner = uid();
        worker.execute(
                () -> {
                    OfflineDatabase.Saved<T> saved = null;
                    try {
                        if (database != null) saved = database.read(owner, kind, id, type);
                    } catch (RuntimeException error) {
                        Log.w("OfflineRepository", "Không đọc được bản lưu.");
                    }
                    OfflineDatabase.Saved<T> result = saved;
                    main.post(
                            () -> {
                                if (!Objects.equals(owner, uid())) return;
                                if (result == null)
                                    callback.onError(
                                            new IllegalStateException(
                                                    "Chưa có dữ liệu đã lưu trên thiết bị. Kết nối"
                                                        + " mạng và tải trang này trước."));
                                else callback.onSuccess(result);
                            });
                });
    }

    public static void saveProfile(UserProfile profile) {
        if (profile != null
                && "ACTIVE".equals(profile.status)
                && Objects.equals(uid(), profile.uid))
            save(profile.uid, "profile", "current", profile);
    }

    // Giới hạn danh sách lưu và bỏ nhãn lịch động để không hiển thị lịch trống đã cũ.
    public static void saveRooms(Collection<RoomListing> items) {
        String owner = uid();
        ArrayList<RoomListing> rows = new ArrayList<>();
        for (RoomListing item : items) {
            if (rows.size() >= 200) break;
            // Không phát lại nhãn lịch trống/giữ chỗ như dữ liệu hiện tại.
            RoomListing copy =
                    new RoomListing(item.room, item.building, item.hostName, item.hostPhotoUrl);
            rows.add(copy);
            save(owner, "room", item.room.getId(), copy);
        }
        save(owner, "rooms", "list", rows);
    }

    public static void saveRoom(RoomListing room) {
        save(
                uid(),
                "room",
                room.room.getId(),
                new RoomListing(room.room, room.building, room.hostName, room.hostPhotoUrl));
    }

    // Chỉ lưu booking mà UID hiện tại là người thuê; không trộn bản lưu của hai tài khoản.
    public static void saveBookings(List<Booking> items) {
        String owner = uid();
        ArrayList<Booking> rows = new ArrayList<>();
        for (Booking item : items)
            if (Objects.equals(owner, item.guestUid) && rows.size() < 200) {
                rows.add(item);
                save(owner, "booking", item.id, item);
            }
        save(owner, "bookings", "list", rows);
    }

    public static void saveBooking(Booking item) {
        if (Objects.equals(uid(), item.guestUid)) save(uid(), "booking", item.id, item);
    }

    public static void clear(String uid) {
        if (database != null && uid != null)
            worker.execute(
                    () -> {
                        try {
                            database.clear(uid);
                        } catch (RuntimeException error) {
                            Log.w("OfflineRepository", "Không xóa được bản lưu.");
                        }
                    });
    }
}
