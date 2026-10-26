package vn.edu.ueh.phuclu.broomsharing.ui.booking;

import androidx.lifecycle.*;

import com.google.firebase.firestore.ListenerRegistration;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

// Giữ trạng thái qua xoay màn hình; không tạo QR lặp và không suy ra PAID từ việc mở ngân hàng.
public final class PaymentViewModel extends ViewModel {
    public final MutableLiveData<UiState<Payment>> state = new MutableLiveData<>();
    private final PaymentRepository repository = new PaymentRepository();
    private ListenerRegistration listener;
    private String bookingId;
    private boolean busy;

    public void bind(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,120}")) {
            state.setValue(UiState.error(new IllegalArgumentException("Thiếu mã booking.")));
            return;
        }
        if (bookingId != null) return;
        bookingId = id;
        if (PaymentRepository.configured())
            listener =
                    repository.watch(
                            id,
                            new ResultCallback<Payment>() {
                                public void onSuccess(Payment p) {
                                    if (p.received()) state.setValue(UiState.success(p));
                                }

                                public void onError(Exception e) {
                                    if (!busy) state.setValue(UiState.error(e));
                                }
                            });
        load(true);
    }

    public void load(boolean create) {
        if (busy || bookingId == null) return;
        UiState<Payment> previous = state.getValue();
        if (previous != null && previous.data != null && previous.data.received()) return;
        busy = true;
        state.setValue(UiState.loading());
        repository.request(
                bookingId,
                create,
                new ResultCallback<Payment>() {
                    public void onSuccess(Payment p) {
                        busy = false;
                        UiState<Payment> current = state.getValue();
                        if (current != null
                                && current.data != null
                                && current.data.received()
                                && !p.received()) return;
                        state.setValue(UiState.success(p));
                    }

                    public void onError(Exception e) {
                        busy = false;
                        UiState<Payment> current = state.getValue();
                        if (current == null || current.data == null || !current.data.received())
                            state.setValue(UiState.error(e));
                    }
                });
    }

    @Override
    protected void onCleared() {
        if (listener != null) listener.remove();
    }
}
