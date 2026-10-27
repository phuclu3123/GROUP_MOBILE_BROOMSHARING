package vn.edu.ueh.phuclu.broomsharing.ui.booking;

import android.os.Handler;
import android.os.Looper;
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
    private Payment lastKnown;
    private boolean initialRequestFailed;
    public final MutableLiveData<Long> clock = new MutableLiveData<>(System.currentTimeMillis());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean visible;
    private long lastRefresh;
    private final Runnable tick = new Runnable() {
        public void run() {
            if (!visible) return;
            clock.setValue(System.currentTimeMillis());
            if (!initialRequestFailed && System.currentTimeMillis() - lastRefresh >= 15000) load(false);
            handler.postDelayed(this, 1000);
        }
    };

    // Chỉ chạy đồng hồ/poll khi màn hình đang mở; không tạo QR lại theo timer.
    public void startUpdates() {
        if (visible) return;
        visible = true;
        handler.post(tick);
    }

    public void stopUpdates() {
        visible = false;
        handler.removeCallbacks(tick);
    }

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
                                    if (p != null) {
                                        if (lastKnown != null && !lastKnown.canReplaceWith(p)) return;
                                        lastKnown = p;
                                        state.setValue(UiState.success(p));
                                    }
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
        if (create) initialRequestFailed = false;
        lastRefresh = System.currentTimeMillis();
        if (previous == null || previous.data == null) state.setValue(UiState.loading());
        repository.request(
                bookingId,
                create,
                new ResultCallback<Payment>() {
                    public void onSuccess(Payment p) {
                        busy = false;
                        initialRequestFailed = false;
                        if (lastKnown != null && !lastKnown.canReplaceWith(p)) return;
                        lastKnown = p;
                        state.setValue(UiState.success(p));
                    }

                    public void onError(Exception e) {
                        busy = false;
                        if (lastKnown == null) initialRequestFailed = true;
                        UiState<Payment> current = state.getValue();
                        if (current == null || current.data == null || !current.data.received())
                            state.setValue(UiState.error(e));
                    }
                });
    }

    @Override
    protected void onCleared() {
        stopUpdates();
        if (listener != null) listener.remove();
    }
}
