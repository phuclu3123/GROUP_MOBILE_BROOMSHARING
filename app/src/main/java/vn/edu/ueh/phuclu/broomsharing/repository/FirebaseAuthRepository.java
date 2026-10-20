package vn.edu.ueh.phuclu.broomsharing.repository;

import android.content.Context;

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

import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.utils.AuthValidation;

import java.util.HashMap;
import java.util.Map;

public final class FirebaseAuthRepository implements AuthRepository {
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public FirebaseAuthRepository(Context context) {
        FirebaseApp app = FirebaseApp.initializeApp(context.getApplicationContext());
        auth = app == null ? null : FirebaseAuth.getInstance(app);
        db =
                app == null
                        ? null
                        : vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper.getFirestore();
    }

    @Override
    public boolean isConfigured() {
        return auth != null && db != null;
    }

    @Override
    public boolean hasSession() {
        return isConfigured() && auth.getCurrentUser() != null;
    }

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

    private void signIn(String email, String password, Callback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> loadSession(callback))
                .addOnFailureListener(e -> callback.onError(authenticationError(e)));
    }

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
                            UserProfile profile =
                                    new UserProfile(user.getUid(), name, username, email, phone);
                            DocumentReference alias = db.collection("usernames").document(username);
                            DocumentReference account =
                                    db.collection("users").document(user.getUid());
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
                            Map<String, Object> aliasData = new HashMap<>();
                            aliasData.put("uid", profile.uid);
                            aliasData.put("email", profile.email);
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
        } else if (profile.getUserRole() == vn.edu.ueh.phuclu.broomsharing.model.UserRole.HOST) {
            // Only these three public fields are synchronized; private contact/evidence stays
            // private.
            db.collection("publicHosts")
                    .document(user.getUid())
                    .set(
                            new vn.edu.ueh.phuclu.broomsharing.model.PublicHost(
                                    profile.uid, profile.name, profile.status))
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
        android.util.Log.w(
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
