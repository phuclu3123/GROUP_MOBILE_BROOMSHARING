package vn.edu.ueh.phuclu.broomsharing.repository;

import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;

public interface AuthRepository {
    interface Callback {
        void onSuccess(UserProfile profile);
        void onError(AuthError error);
    }

    boolean isConfigured();
    boolean hasSession();
    void login(String identifier, String password, Callback callback);
    void register(String name, String username, String email, String phone, String password, Callback callback);
    void loadSession(Callback callback);
    void logout();
}
