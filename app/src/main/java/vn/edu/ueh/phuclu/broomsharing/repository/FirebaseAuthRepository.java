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

import java.util.HashMap;
import java.util.Map;

import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.utils.AuthValidation;

public final class FirebaseAuthRepository implements AuthRepository {
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public FirebaseAuthRepository(Context context) {
        FirebaseApp app = FirebaseApp.initializeApp(context.getApplicationContext());
        auth = app == null ? null : FirebaseAuth.getInstance(app);
        db = app == null ? null : FirebaseFirestore.getInstance(app);
    }

    @Override public boolean isConfigured() { return auth != null && db != null; }
    @Override public boolean hasSession() { return isConfigured() && auth.getCurrentUser() != null; }

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
            db.collection("usernames").document(normalizeUsername(value)).get(Source.SERVER)
                    .addOnSuccessListener(doc -> {
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
    public void register(String name, String username, String email, String password, Callback callback) {
        if (!isConfigured()) {
            callback.onError(AuthError.NOT_CONFIGURED);
            return;
        }
        String key = normalizeUsername(username);
        String address = email.trim();
        // Preflight gives a useful error without creating an unnecessary Auth user.
        db.collection("usernames").document(key).get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onError(AuthError.USERNAME_IN_USE);
                    } else {
                        createAccount(name.trim(), key, address, password, callback);
                    }
                })
                .addOnFailureListener(e -> callback.onError(databaseError(e)));
    }

    private void createAccount(String name, String username, String email, String password, Callback callback) {
        auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener(result -> {
            FirebaseUser user = result.getUser();
            if (user == null) {
                callback.onError(AuthError.UNKNOWN);
                return;
            }
            UserProfile profile = new UserProfile(user.getUid(), name, username, email);
            DocumentReference alias = db.collection("usernames").document(username);
            DocumentReference account = db.collection("users").document(user.getUid());
            Map<String, Object> data = new HashMap<>();
            data.put("schemaVersion", 1L);
            data.put("uid", profile.uid);
            data.put("name", profile.name);
            data.put("username", profile.username);
            data.put("email", profile.email);
            data.put("role", profile.role);
            data.put("status", profile.status);
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());
            Map<String, Object> aliasData = new HashMap<>();
            aliasData.put("uid", profile.uid);
            aliasData.put("email", profile.email);
            db.runTransaction(transaction -> {
                // Two concurrent registrations cannot claim the same normalized username.
                if (transaction.get(alias).exists()) throw new UsernameTakenException();
                transaction.set(account, data);
                transaction.set(alias, aliasData);
                return null;
            }).addOnSuccessListener(ignored -> callback.onSuccess(profile))
                    .addOnFailureListener(e -> {
                        AuthError failure = hasUsernameTakenCause(e)
                                ? AuthError.USERNAME_IN_USE : databaseError(e);
                        // Auth and Firestore cannot share a transaction: compensate on failure.
                        user.delete().addOnCompleteListener(deleted -> {
                            auth.signOut();
                            callback.onError(deleted.isSuccessful()
                                    ? failure : AuthError.INCOMPLETE_REGISTRATION);
                        });
                    });
        }).addOnFailureListener(e -> callback.onError(authenticationError(e)));
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
        db.collection("users").document(user.getUid()).get(Source.SERVER)
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
        } else {
            callback.onSuccess(profile);
        }
    }

    @Override public void logout() { if (auth != null) auth.signOut(); }

    private AuthError databaseError(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) return AuthError.NETWORK;
        }
        return e instanceof FirebaseNetworkException ? AuthError.NETWORK : AuthError.PROFILE_UNAVAILABLE;
    }

    private AuthError authenticationError(Exception e) {
        if (e instanceof FirebaseNetworkException) return AuthError.NETWORK;
        if (e instanceof FirebaseTooManyRequestsException) return AuthError.TOO_MANY_REQUESTS;
        if (e instanceof FirebaseAuthException) {
            switch (((FirebaseAuthException) e).getErrorCode()) {
                case "ERROR_EMAIL_ALREADY_IN_USE": return AuthError.EMAIL_IN_USE;
                case "ERROR_WEAK_PASSWORD": return AuthError.WEAK_PASSWORD;
                case "ERROR_USER_DISABLED": return AuthError.DISABLED;
                case "ERROR_OPERATION_NOT_ALLOWED": return AuthError.OPERATION_DISABLED;
                case "ERROR_INVALID_EMAIL":
                case "ERROR_WRONG_PASSWORD":
                case "ERROR_USER_NOT_FOUND":
                case "ERROR_INVALID_CREDENTIAL":
                case "ERROR_INVALID_LOGIN_CREDENTIALS": return AuthError.INVALID_CREDENTIALS;
                default: return AuthError.UNKNOWN;
            }
        }
        return AuthError.UNKNOWN;
    }

    private boolean hasUsernameTakenCause(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof UsernameTakenException) return true;
        }
        return false;
    }

    private static final class UsernameTakenException extends RuntimeException { }
}
