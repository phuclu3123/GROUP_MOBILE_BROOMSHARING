package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.repository.AuthError;
import vn.edu.ueh.phuclu.broomsharing.repository.AuthRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.FirebaseAuthRepository;

public class AuthViewModel extends AndroidViewModel {
    public static final class State {
        public final boolean loading;
        public final UserProfile profile;
        public final AuthError error;

        State(boolean loading, UserProfile profile, AuthError error) {
            this.loading = loading;
            this.profile = profile;
            this.error = error;
        }
    }

    private final AuthRepository repository;
    private final MutableLiveData<State> state =
            new MutableLiveData<>(new State(false, null, null));
    private boolean restored;
    private boolean resetSent;

    public boolean passwordResetSent() { return resetSent; }

    public void resetPassword(String email) {
        if (isBusy()) return;
        resetSent = false;
        state.setValue(new State(true, null, null));
        repository.resetPassword(email, new AuthRepository.ResetCallback() {
            public void onSuccess() { resetSent = true; state.setValue(new State(false, null, null)); }
            public void onError(AuthError error) { state.setValue(new State(false, null, error)); }
        });
    }

    public AuthViewModel(@NonNull Application application) {
        super(application);
        repository = new FirebaseAuthRepository(application);
    }

    public LiveData<State> getState() {
        return state;
    }

    public boolean isConfigured() {
        return repository.isConfigured();
    }

    public boolean isBusy() {
        return state.getValue() != null && state.getValue().loading;
    }

    public boolean hasSession() {
        return repository.hasSession();
    }

    public void reloadSession() {
        if (isBusy()) return;
        state.setValue(new State(true, null, null));
        repository.loadSession(callback);
    }

    public void signOut() {
        repository.logout();
        consumeSuccess();
    }

    private final AuthRepository.Callback callback =
            new AuthRepository.Callback() {
                @Override
                public void onSuccess(UserProfile profile) {
                    state.setValue(new State(false, profile, null));
                }

                @Override
                public void onError(AuthError error) {
                    state.setValue(new State(false, null, error));
                }
            };

    public void login(String identifier, String password) {
        if (isBusy()) return;
        state.setValue(new State(true, null, null));
        repository.login(identifier, password, callback);
    }

    public void register(
            String name, String username, String email, String phone, String password) {
        if (isBusy()) return;
        state.setValue(new State(true, null, null));
        repository.register(name, username, email, phone, password, callback);
    }

    public void restoreSessionOnce() {
        if (restored || isBusy()) return;
        restored = true;
        if (repository.hasSession()) {
            state.setValue(new State(true, null, null));
            repository.loadSession(callback);
        }
    }

    public void consumeSuccess() {
        state.setValue(new State(false, null, null));
    }
}
