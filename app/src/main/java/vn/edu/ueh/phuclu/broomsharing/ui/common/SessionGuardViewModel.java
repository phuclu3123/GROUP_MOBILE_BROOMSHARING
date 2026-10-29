package vn.edu.ueh.phuclu.broomsharing.ui.common;

import androidx.lifecycle.*;

import com.google.firebase.firestore.ListenerRegistration;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

// SessionActivity sở hữu vòng đời nghe; callback cũ bị loại khi đổi màn hình/phiên.
public class SessionGuardViewModel extends ViewModel {
    private ListenerRegistration listener;
    private int generation;
    public final MutableLiveData<UiState<UserProfile>> profile = new MutableLiveData<>();

    public void watch(String uid) {
        stop();
        int run = generation;
        listener =
                new SessionGuardRepository()
                        .watch(
                                uid,
                                new ResultCallback<UserProfile>() {
                                    public void onSuccess(UserProfile user) {
                                        if (run == generation)
                                            profile.setValue(UiState.success(user));
                                    }

                                    public void onError(Exception e) {
                                        if (run == generation) profile.setValue(UiState.error(e));
                                    }
                                });
    }

    public void stop() {
        generation++;
        if (listener != null) listener.remove();
        listener = null;
    }

    @Override
    protected void onCleared() {
        stop();
    }
}
