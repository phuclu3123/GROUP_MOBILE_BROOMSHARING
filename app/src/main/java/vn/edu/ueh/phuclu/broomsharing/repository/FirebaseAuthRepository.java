package vn.edu.ueh.phuclu.broomsharing.repository;

import android.content.Context;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import java.util.HashMap;
import java.util.Map;
import vn.edu.ueh.phuclu.broomsharing.model.PublicHost;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.utils.AuthValidation;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

public final class FirebaseAuthRepository implements AuthRepository {
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public FirebaseAuthRepository(Context context) {
        FirebaseApp app = FirebaseApp.initializeApp(context.getApplicationContext());
        auth = app == null ? null : FirebaseAuth.getInstance(app);
        db = app == null ? null : FirebaseHelper.getFirestore();
    }

    // Cấu hình khi đã khởi tạo FirebaseAuth và FirebaseFirestore
    @Override
    public boolean isConfigured() {
        return auth != null && db != null;
    }

    /* Kiểm tra có người dùng đang đăng nhập hay không với điều kiện là đã cấu hình
        và có người đăng nhập bằng hàm getCurrentUser.
     */
    @Override
    public boolean hasSession() {
        return isConfigured() && auth.getCurrentUser() != null;
    }

    /*
        Nếu chưa cấu hinh thì trả lỗi. Cấu hình tiếng việt.
        Đặt mật khẩu email

        Tài liệu hướng dẫn: https://firebase.google.com/docs/auth/android/manage-users#java
     */
    @Override
    public void resetPassword(String email, ResetCallback callback) {
        if (!isConfigured()) { callback.onError(AuthError.NOT_CONFIGURED); return; }
        auth.setLanguageCode("vi");
        auth.sendPasswordResetEmail(email.trim())
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(error -> {
                    String code = error instanceof FirebaseAuthException
                            ? ((FirebaseAuthException) error).getErrorCode() : null;
                    // Keep the same message even on projects without enumeration protection.
                    if ("ERROR_USER_NOT_FOUND".equals(code) || "ERROR_USER_DISABLED".equals(code))
                        callback.onSuccess();
                    else callback.onError(authenticationError(error));
                });
    }

    public static String normalizeUsername(String username) {
        return AuthValidation.normalizeUsername(username);
    }

    /*
        Tài liệu: https://firebase.google.com/docs/firestore/data-model#java_4
                   https://firebase.google.com/docs/auth/android/password-auth
        .document(key): chọn document có id = key
        .get(Source.SERVER): yêu cầu đọc firestore trên sever, không lấy dữ liệu cũ trên cache
        .addOnSuccessListener: event xử lý khi thành công
        -> gán vào biến doc

        Tức ở đây ta sẽ đăng nhập bằng email, nếu người dùng sử dụng username thì ta
        lục trong firestore thằng email rồi đăng nhập bằng email.
     */
    @Override
    public void login(String identifier, String password, Callback callback) {
        if (!isConfigured()) {
            callback.onError(AuthError.NOT_CONFIGURED);
            return;
        }
        String value = identifier.trim();
        if (value.contains("@")) {
            signIn(value, password, callback);
        } else {
            // Username lookup precedes Auth. The demo rules permit this read.
            db.collection("usernames")
                    .document(normalizeUsername(value))
                    .get(Source.SERVER)
                    .addOnSuccessListener(
                            doc -> {
                                String email = doc.getString("email");
                                if (!doc.exists() || email == null || email.isEmpty()) {
                                    callback.onError(AuthError.INVALID_CREDENTIALS);
                                } else {
                                    signIn(email, password, callback);
                                }
                            })
                    .addOnFailureListener(e -> callback.onError(databaseError(e)));
        }
    }

    // Nâng cấp thêm cho signInWithEmailAndPassword
    private void signIn(String email, String password, Callback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> loadSession(callback))
                .addOnFailureListener(e -> callback.onError(authenticationError(e)));
    }

    // Đăng ký - Create database mới vào firestore
    @Override
    public void register(
            String name,
            String username,
            String email,
            String phone,
            String password,
            Callback callback) {
        if (!isConfigured()) {
            callback.onError(AuthError.NOT_CONFIGURED);
            return;
        }
        String key = normalizeUsername(username);
        String address = email.trim();
        String number = AuthValidation.normalizePhone(phone);
        if (number.isEmpty()) {
            callback.onError(AuthError.PHONE_INVALID);
            return;
        }
        // Preflight gives a useful error without creating an unnecessary Auth user.
        db.collection("usernames")
                .document(key)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        doc -> {
                            if (doc.exists()) {
                                callback.onError(AuthError.USERNAME_IN_USE);
                            } else {
                                createAccount(
                                        name.trim(), key, address, number, password, callback);
                            }
                        })
                .addOnFailureListener(e -> callback.onError(databaseError(e)));
    }
    /*
        Tài liệu: https://firebase.google.com/docs/auth/android/password-auth
                https://firebase.google.com/docs/firestore/manage-data/transactions
     */
    private void createAccount(
            String name,
            String username,
            String email,
            String phone,
            String password,
            Callback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(
                        result -> {
                            FirebaseUser user = result.getUser();
                            if (user == null) {
                                callback.onError(AuthError.UNKNOWN);
                                return;
                            }
                            UserProfile profile = new UserProfile(user.getUid(), name, username, email, phone);
                            DocumentReference alias = db.collection("usernames").document(username);
                            DocumentReference account = db.collection("users").document(user.getUid());

                            // Data là hồ sơ đầy dủ
                            Map<String, Object> data = new HashMap<>();
                            data.put("schemaVersion", 1L);
                            data.put("uid", profile.uid);
                            data.put("name", profile.name);
                            data.put("username", profile.username);
                            data.put("email", profile.email);
                            data.put("phone", profile.phone);
                            data.put("phoneVerified", false);
                            data.put("role", profile.role);
                            data.put("status", profile.status);
                            data.put("createdAt", FieldValue.serverTimestamp());
                            data.put("updatedAt", FieldValue.serverTimestamp());

                            // Alias là hồ sơ đối chiếu nhanh
                            Map<String, Object> aliasData = new HashMap<>();
                            aliasData.put("uid", profile.uid);
                            aliasData.put("email", profile.email);

                            // Xử lý transaction trước
                            db.runTransaction(
                                            transaction -> {
                                                // Two concurrent registrations cannot claim the
                                                // same normalized username.
                                                if (transaction.get(alias).exists())
                                                    throw new UsernameTakenException();
                                                transaction.set(account, data);
                                                transaction.set(alias, aliasData);
                                                return null;
                                            })
                                    .addOnSuccessListener(ignored -> callback.onSuccess(profile))
                                    .addOnFailureListener(
                                            e -> {
                                                AuthError failure =
                                                        hasUsernameTakenCause(e)
                                                                ? AuthError.USERNAME_IN_USE
                                                                : databaseError(e);
                                                // Auth and Firestore cannot share a transaction:
                                                // compensate on failure.
                                                user.delete()
                                                        .addOnCompleteListener(
                                                                deleted -> {
                                                                    auth.signOut();
                                                                    callback.onError(
                                                                            deleted.isSuccessful()
                                                                                    ? failure
                                                                                    : AuthError
                                                                                            .INCOMPLETE_REGISTRATION);
                                                                });
                                            });
                        })
                .addOnFailureListener(e -> callback.onError(authenticationError(e)));
    }

    // Khôi phục trạng thái người dùng đã đăng nhập khi app mở hoặc cần tải lại hồ sơ
    @Override
    public void loadSession(Callback callback) {
        if (!isConfigured()) {
            callback.onError(AuthError.NOT_CONFIGURED);
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(AuthError.SESSION_EXPIRED);
            return;
        }
        // Do not route using stale role data from Firestore's offline cache.
        db.collection("users")
                .document(user.getUid())
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> readProfile(user, doc, callback))
                .addOnFailureListener(e -> callback.onError(databaseError(e)));
    }

    // Đọc hồ sơ của người dùng đang đăng nhập
    private void readProfile(FirebaseUser user, DocumentSnapshot doc, Callback callback) {
        if (!doc.exists()) {
            callback.onError(AuthError.PROFILE_MISSING);
            return;
        }
        final UserProfile profile;
        try {
            profile = doc.toObject(UserProfile.class);
        } catch (RuntimeException e) {
            callback.onError(AuthError.PROFILE_UNAVAILABLE);
            return;
        }
        if (profile == null || !user.getUid().equals(profile.uid)) {
            callback.onError(AuthError.PROFILE_UNAVAILABLE);
        } else if (!"ACTIVE".equals(profile.status)) {
            auth.signOut();
            callback.onError(AuthError.ACCOUNT_BLOCKED);
        } else if (profile.getUserRole() == null) {
            callback.onError(AuthError.INVALID_ROLE);
        } else if (profile.getUserRole() == UserRole.HOST) {
            // Only these three public fields are synchronized; private contact/evidence stays
            // private.
            db.collection("publicHosts")
                    .document(user.getUid())
                    .set(new PublicHost(profile.uid, profile.name, profile.status))
                    .addOnSuccessListener(ignored -> callback.onSuccess(profile))
                    .addOnFailureListener(error -> callback.onError(databaseError(error)));
        } else {
            callback.onSuccess(profile);
        }
    }

    @Override
    public void logout() {
        if (auth != null) auth.signOut();
    }

    private AuthError databaseError(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
                return AuthError.NETWORK;
        }
        return e instanceof FirebaseNetworkException
                ? AuthError.NETWORK
                : AuthError.PROFILE_UNAVAILABLE;
    }

    private AuthError authenticationError(Exception e) {
        if (e instanceof FirebaseNetworkException) return AuthError.NETWORK;
        if (e instanceof FirebaseTooManyRequestsException) return AuthError.TOO_MANY_REQUESTS;
        String code =
                e instanceof FirebaseAuthException
                        ? ((FirebaseAuthException) e).getErrorCode()
                        : null;
        AuthError error = AuthError.fromFirebase(code, e.getMessage());
        // No email, password, token or raw server payload in our diagnostic log.
        Log.w(
                "AuthRepository", "Firebase Auth failed: code=" + code + "; mapped=" + error);
        return error;
    }

    private boolean hasUsernameTakenCause(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof UsernameTakenException) return true;
        }
        return false;
    }

    private static final class UsernameTakenException extends RuntimeException {}
}
