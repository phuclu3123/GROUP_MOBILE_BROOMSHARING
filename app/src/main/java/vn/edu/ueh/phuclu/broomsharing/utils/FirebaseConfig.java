package vn.edu.ueh.phuclu.broomsharing.utils;

import com.google.firebase.functions.FirebaseFunctions;
import vn.edu.ueh.phuclu.broomsharing.BuildConfig;

/** Shared database selected by Phuc for Auth and Host modules. */
public final class FirebaseConfig {
    public static final String FIRESTORE_DATABASE_ID = "broomsharing";
    private static FirebaseFunctions functions;

    public static synchronized FirebaseFunctions functions() {
        if (functions == null) {
            functions = FirebaseFunctions.getInstance("asia-southeast1");
            if (BuildConfig.DEBUG && !BuildConfig.FUNCTIONS_EMULATOR_HOST.isEmpty()) {
                functions.useEmulator(BuildConfig.FUNCTIONS_EMULATOR_HOST, 5001);
            }
        }
        return functions;
    }

    private FirebaseConfig() {}
}
