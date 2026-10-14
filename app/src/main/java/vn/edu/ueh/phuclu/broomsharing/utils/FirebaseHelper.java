package vn.edu.ueh.phuclu.broomsharing.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import java.util.UUID;

public class FirebaseHelper {
    private static final String PREF_NAME = "broom_demo_prefs";
    private static final String KEY_DEMO_UID = "demo_host_uid";

    public static FirebaseFirestore getFirestore() {
        try {
            return FirebaseFirestore.getInstance("broomsharing");
        } catch (Exception e) {
            return FirebaseFirestore.getInstance();
        }
    }

    public static FirebaseStorage getStorage() {
        return FirebaseStorage.getInstance();
    }

    public static String getCurrentHostUid(Context context) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getUid() != null) {
                return user.getUid();
            }
        } catch (Exception ignored) {}

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String demoUid = prefs.getString(KEY_DEMO_UID, null);
        if (demoUid == null) {
            demoUid = "host_hoang_" + UUID.randomUUID().toString().substring(0, 8);
            prefs.edit().putString(KEY_DEMO_UID, demoUid).apply();
        }
        return demoUid;
    }
}
