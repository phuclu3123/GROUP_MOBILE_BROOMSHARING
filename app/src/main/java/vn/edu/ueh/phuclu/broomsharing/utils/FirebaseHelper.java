package vn.edu.ueh.phuclu.broomsharing.utils;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

public class FirebaseHelper {
    public static FirebaseFirestore getFirestore() {
        return FirebaseFirestore.getInstance(FirebaseConfig.FIRESTORE_DATABASE_ID);
    }

    public static FirebaseStorage getStorage() {
        return FirebaseStorage.getInstance();
    }

    public static String getCurrentHostUid(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("Vui lòng đăng nhập trước khi sử dụng chức năng Host.");
        }
        return user.getUid();
    }
}
