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
    public final MutableLiveData<Long> clock = new MutableLiveData<>(System.currentTimeMillis());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean visible;
    private final Runnable tick = new Runnable() {
        public void run() {
            if (!visible) return;
            clock.setValue(System.currentTimeMillis());
            // Đồng hồ chỉ cập nhật UI. Backend đối soát tiền; listener nhận kết quả thực tế.
            handler.postDelayed(this, 1000);
        }
    };

    // Chỉ chạy đồng hồ khi màn hình đang mở; không gọi API mỗi giây hoặc sau khi QR đã hết hạn.
    public void startUpdates() {
        if (visible) return;
        visible = true;
        handler.post(tick);
    }

    // Dừng đồng hồ khi View không còn hiển thị; kết quả tiền vẫn do backend và listener quyết định.
    public void stopUpdates() {
        visible = false;
        handler.removeCallbacks(tick);
    }

    // Gắn đúng booking và bắt đầu quan sát giao dịch; mở lại màn hình phải dùng cùng mã đơn, không gia hạn thời hạn.
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

    // Tạo/lấy lại QR hoặc đối soát qua Repository, đồng bộ cờ bận và lỗi cho màn hình.
    public void load(boolean create) {
        if (busy || bookingId == null) return;
        UiState<Payment> previous = state.getValue();
        if (previous != null && previous.data != null && previous.data.received()) return;
        busy = true;
        if (previous == null || previous.data == null) state.setValue(UiState.loading());
        repository.request(
                bookingId,
                create,
                new ResultCallback<Payment>() {
                    public void onSuccess(Payment p) {
                        busy = false;
                        if (lastKnown != null && !lastKnown.canReplaceWith(p)) return;
                        lastKnown = p;
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
        stopUpdates();
        if (listener != null) listener.remove();
    }
}
