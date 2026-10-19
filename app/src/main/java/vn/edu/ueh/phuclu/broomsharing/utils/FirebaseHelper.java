package vn.edu.ueh.phuclu.broomsharing.utils;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

public class FirebaseHelper {
    private static FirebaseFirestore database;

    public static synchronized FirebaseFirestore getFirestore() {
        if (database == null) {
            database = FirebaseFirestore.getInstance(FirebaseConfig.FIRESTORE_DATABASE_ID);
            if (vn.edu.ueh.phuclu.broomsharing.BuildConfig.DEBUG
                    && !vn.edu.ueh.phuclu.broomsharing.BuildConfig.FIRESTORE_EMULATOR_HOST
                            .isEmpty()) {
                database.useEmulator(
                        vn.edu.ueh.phuclu.broomsharing.BuildConfig.FIRESTORE_EMULATOR_HOST, 8085);
                database.setFirestoreSettings(
                        new com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
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
