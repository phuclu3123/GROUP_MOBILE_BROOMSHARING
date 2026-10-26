package vn.edu.ueh.phuclu.broomsharing.ui.admin;

import androidx.lifecycle.*;

import com.google.firebase.firestore.ListenerRegistration;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

import java.util.List;

// Admin xem đơn còn hoạt động; mọi quyết định vẫn được kiểm lại trong transaction.
public class AdminBookingsViewModel extends ViewModel {
    private final BookingRepository repository = new BookingRepository();
    private ListenerRegistration listener;
    private int generation;
    public final MutableLiveData<UiState<List<Booking>>> rows = new MutableLiveData<>();
    public final MutableLiveData<UiState<String>> action = new MutableLiveData<>();

    public void watch() {
        stop();
        int run = generation;
        rows.setValue(UiState.loading());
        listener =
                repository.watchAdmin(
                        new ResultCallback<List<Booking>>() {
                            public void onSuccess(List<Booking> value) {
                                if (run == generation) rows.setValue(UiState.success(value));
                            }

                            public void onError(Exception e) {
                                if (run == generation) rows.setValue(UiState.error(e));
                            }
                        });
    }

    public void cancel(String id, String reason) {
        resolve(id, "CANCELLED", reason);
    }

    public void resolve(String id, String target, String reason) {
        if (busy()) return;
        action.setValue(UiState.loading());
        repository.transition(id, target, reason, result());
    }

    public void reconcile(String roomId) {
        if (busy()) return;
        action.setValue(UiState.loading());
        repository.reconcileRoomCount(roomId, result());
    }

    private boolean busy() {
        return action.getValue() != null && action.getValue().loading;
    }

    private ResultCallback<String> result() {
        return new ResultCallback<String>() {
            public void onSuccess(String value) {
                action.setValue(UiState.success(value));
            }

            public void onError(Exception e) {
                action.setValue(UiState.error(e));
            }
        };
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
