package vn.edu.ueh.phuclu.broomsharing.utils;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.storage.FirebaseStorage;
import vn.edu.ueh.phuclu.broomsharing.BuildConfig;

/*
    Cấp Firestore của đúng database được cấu hình cho app.
    Bản kiểm thử emulator phải chỉ định host tường minh, không chuyển môi trường theo lỗi mạng.
 */
public class FirebaseHelper {
    private static FirebaseFirestore database;

    public static synchronized FirebaseFirestore getFirestore() {
        if (database == null) {
            database = FirebaseFirestore.getInstance(FirebaseConfig.FIRESTORE_DATABASE_ID);
            if (BuildConfig.DEBUG
                    && !BuildConfig.FIRESTORE_EMULATOR_HOST
                            .isEmpty()) {
                database.useEmulator(
                        BuildConfig.FIRESTORE_EMULATOR_HOST, 8085);
                database.setFirestoreSettings(
                        new FirebaseFirestoreSettings.Builder()
                                .setPersistenceEnabled(false)
                                .build());
            }
        }
        return database;
    }

    public static FirebaseStorage getStorage() {
        return FirebaseStorage.getInstance();
    }
}
