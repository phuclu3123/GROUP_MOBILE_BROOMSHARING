package vn.edu.ueh.phuclu.broomsharing.repository;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.model.HostApplication;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HostRepository: Quản lý toàn bộ tương tác Firebase Firestore và Storage cho vai trò HOST
 * Đợt 1 theo BroomSharing_PhanCong_4Dot_Android_Java_Firebase.xlsx (H01 -> H08).
 * Thuần Firestore và Storage, không dùng dữ liệu giả/local fallback.
 */
public class HostRepository {

    private static final String TAG = "HostRepository";
    private static final long FIRESTORE_TIMEOUT_MS = 15000; // 15s timeout phù hợp với độ trễ mạng máy ảo

    static {
        try {
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.net.preferIPv6Addresses", "false");
        } catch (Exception ignored) {}
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private final FirebaseFirestore db;
    private final FirebaseStorage storage;
    private Context context;

    public HostRepository() {
        this.db = FirebaseHelper.getFirestore();
        this.storage = FirebaseHelper.getStorage();
        initFirestoreSettings();
        ensureFirebaseAuth();
    }

    public HostRepository(Context context) {
        this();
        this.context = context;
    }

    private void initFirestoreSettings() {
        try {
            com.google.firebase.firestore.FirebaseFirestoreSettings settings =
                    new com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                            .setPersistenceEnabled(true)
                            .build();
            db.setFirestoreSettings(settings);
        } catch (Exception ignored) {}
    }

    private void ensureFirebaseAuth() {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                auth.signInAnonymously().addOnCompleteListener(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        Log.w(TAG, "Anonymous sign-in not available: " + task.getException().getMessage());
                    }
                });
            }
        } catch (Exception e) {
            Log.w(TAG, "Auth init: " + e.getMessage());
        }
    }

    // ==========================================
    // H01: HOST ONBOARDING / APPLICATION (hostApplications)
    // ==========================================
    public void submitHostApplication(HostApplication application, Callback<String> callback) {
        CollectionReference ref = db.collection("hostApplications");
        DocumentReference doc = ref.document();
        application.setId(doc.getId());

        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean completed = new AtomicBoolean(false);

        Runnable timeoutRunnable = () -> {
            if (completed.compareAndSet(false, true)) {
                callback.onError(new Exception("Kết nối Firestore quá hạn (15s). Vui lòng kiểm tra kết nối mạng và Rule Firestore trên Firebase Console."));
            }
        };
        handler.postDelayed(timeoutRunnable, FIRESTORE_TIMEOUT_MS);

        doc.set(application)
                .addOnSuccessListener(aVoid -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        callback.onSuccess(doc.getId());
                    }
                })
                .addOnFailureListener(e -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        callback.onError(e);
                    }
                });
    }

    public void checkHostApplicationStatus(String applicantUid, Callback<HostApplication> callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean completed = new AtomicBoolean(false);

        Runnable timeoutRunnable = () -> {
            if (completed.compareAndSet(false, true)) {
                callback.onError(new Exception("Quá thời gian tải trạng thái đơn xét duyệt."));
            }
        };
        handler.postDelayed(timeoutRunnable, FIRESTORE_TIMEOUT_MS);

        db.collection("hostApplications")
                .whereEqualTo("applicantUid", applicantUid)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        if (!snapshots.isEmpty()) {
                            HostApplication app = snapshots.getDocuments().get(0).toObject(HostApplication.class);
                            callback.onSuccess(app);
                        } else {
                            callback.onSuccess(null);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        callback.onError(e);
                    }
                });
    }

    // ==========================================
    // H03 & H07: BUILDINGS (buildings)
    // ==========================================
    public void addBuilding(Building building, Callback<String> callback) {
        CollectionReference ref = db.collection("buildings");
        DocumentReference doc = ref.document();
        building.setId(doc.getId());

        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean completed = new AtomicBoolean(false);

        Runnable timeoutRunnable = () -> {
            if (completed.compareAndSet(false, true)) {
                callback.onError(new Exception("Lỗi: Quá thời gian gửi tòa nhà tới Firestore (6s). Vui lòng kiểm tra Firestore Rules."));
            }
        };
        handler.postDelayed(timeoutRunnable, FIRESTORE_TIMEOUT_MS);

        doc.set(building)
                .addOnSuccessListener(aVoid -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        callback.onSuccess(doc.getId());
                    }
                })
                .addOnFailureListener(e -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        callback.onError(e);
                    }
                });
    }

    public void getBuildingsByOwner(String ownerUid, Callback<List<Building>> callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean completed = new AtomicBoolean(false);

        Runnable timeoutRunnable = () -> {
            if (completed.compareAndSet(false, true)) {
                callback.onError(new Exception("Quá thời gian tải danh sách tòa nhà từ Firestore."));
            }
        };
        handler.postDelayed(timeoutRunnable, FIRESTORE_TIMEOUT_MS);

        db.collection("buildings")
                .whereEqualTo("ownerUid", ownerUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        List<Building> list = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Building b = doc.toObject(Building.class);
                            b.setId(doc.getId());
                            list.add(b);
                        }
                        callback.onSuccess(list);
                    }
                })
                .addOnFailureListener(e -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        callback.onError(e);
                    }
                });
    }

    // ==========================================
    // H04, H05, H08: ROOMS / SPACES (rooms)
    // ==========================================
    public void saveRoom(Room room, Callback<String> callback) {
        CollectionReference ref = db.collection("rooms");
        DocumentReference doc;
        if (room.getId() == null || room.getId().isEmpty()) {
            doc = ref.document();
            room.setId(doc.getId());
        } else {
            doc = ref.document(room.getId());
        }

        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean completed = new AtomicBoolean(false);

        Runnable timeoutRunnable = () -> {
            if (completed.compareAndSet(false, true)) {
                callback.onError(new Exception("Lỗi: Quá thời gian lưu phòng tới Firestore (6s). Vui lòng kiểm tra Firestore Rules trên Firebase Console."));
            }
        };
        handler.postDelayed(timeoutRunnable, FIRESTORE_TIMEOUT_MS);

        doc.set(room)
                .addOnSuccessListener(aVoid -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        callback.onSuccess(doc.getId());
                    }
                })
                .addOnFailureListener(e -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        callback.onError(e);
                    }
                });
    }

    public void getRoomsByOwner(String ownerUid, Callback<List<Room>> callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean completed = new AtomicBoolean(false);

        Runnable timeoutRunnable = () -> {
            if (completed.compareAndSet(false, true)) {
                callback.onError(new Exception("Quá thời gian tải danh sách phòng từ Firestore."));
            }
        };
        handler.postDelayed(timeoutRunnable, FIRESTORE_TIMEOUT_MS);

        db.collection("rooms")
                .whereEqualTo("ownerUid", ownerUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        List<Room> list = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Room r = doc.toObject(Room.class);
                            r.setId(doc.getId());
                            list.add(r);
                        }
                        callback.onSuccess(list);
                    }
                })
                .addOnFailureListener(e -> {
                    if (completed.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable);
                        callback.onError(e);
                    }
                });
    }

    // ==========================================
    // H06: UPLOAD ROOM PHOTOS (Firebase Storage roomMedia)
    // ==========================================
    public void uploadRoomPhotos(List<Uri> imageUris, String roomId, Callback<List<String>> callback) {
        if (imageUris == null || imageUris.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean completed = new AtomicBoolean(false);

        Runnable timeoutRunnable = () -> {
            if (completed.compareAndSet(false, true)) {
                callback.onError(new Exception("Tải ảnh lên Firebase Storage quá hạn. Vui lòng kiểm tra Storage Bucket/Rules."));
            }
        };
        handler.postDelayed(timeoutRunnable, 10000); // 10s cho upload ảnh

        AtomicInteger remaining = new AtomicInteger(imageUris.size());
        List<String> cloudUrls = new ArrayList<>();

        for (Uri uri : imageUris) {
            String fileName = UUID.randomUUID().toString() + ".jpg";
            StorageReference fileRef = storage.getReference()
                    .child("roomMedia")
                    .child(roomId != null ? roomId : "temp")
                    .child(fileName);

            fileRef.putFile(uri)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful() && task.getException() != null) {
                            throw task.getException();
                        }
                        return fileRef.getDownloadUrl();
                    })
                    .addOnSuccessListener(downloadUri -> {
                        cloudUrls.add(downloadUri.toString());
                        if (remaining.decrementAndGet() == 0 && completed.compareAndSet(false, true)) {
                            handler.removeCallbacks(timeoutRunnable);
                            callback.onSuccess(cloudUrls);
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (completed.compareAndSet(false, true)) {
                            handler.removeCallbacks(timeoutRunnable);
                            callback.onError(new Exception("Lỗi khi tải ảnh lên Firebase Storage: " + e.getMessage()));
                        }
                    });
        }
    }
}
