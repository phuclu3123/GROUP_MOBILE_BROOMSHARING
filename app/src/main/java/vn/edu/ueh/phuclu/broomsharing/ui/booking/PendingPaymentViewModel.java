package vn.edu.ueh.phuclu.broomsharing.ui.booking;

import android.os.*;
import androidx.lifecycle.*;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.utils.PendingCheckout;

// Hai listener server quyết định có nhắc thanh toán hay không; đồng hồ chỉ cập nhật thời gian còn lại.
public final class PendingPaymentViewModel extends ViewModel {
    public final MutableLiveData<List<PendingCheckout>> entries = new MutableLiveData<>(Collections.emptyList());
    private final BookingRepository bookings = new BookingRepository();
    private final PaymentRepository payments = new PaymentRepository();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ListenerRegistration bookingListener, paymentListener;
    private List<Booking> mine = Collections.emptyList();
    private List<Payment> transactions = Collections.emptyList();
    private String uid;
    private int generation;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            if (bookingListener != null) handler.postDelayed(this, 1000);
        }
    };

    // Nghe cả booking và payment; chỉ giao dịch thật còn hạn mới được đưa vào danh sách tiếp tục thanh toán.
    public void start(String actor) {
        if (bookingListener != null && actor.equals(uid)) return;
        stop(); uid = actor;
        final int run = generation;
        bookingListener = bookings.watchMine(actor, new ResultCallback<List<Booking>>() {
            public void onSuccess(List<Booking> value) { if (run == generation) { mine = value; render(); } }
            public void onError(Exception error) { if (run == generation) { mine = Collections.emptyList(); render(); } }
        });
        paymentListener = payments.watchMine(new ResultCallback<List<Payment>>() {
            public void onSuccess(List<Payment> value) { if (run == generation) { transactions = value; render(); } }
            public void onError(Exception error) { if (run == generation) { transactions = Collections.emptyList(); render(); } }
        });
        handler.post(tick);
    }

    private void render() {
        if (uid != null) entries.setValue(PendingCheckout.select(uid, null, mine, transactions, System.currentTimeMillis()));
    }

    // Tháo hai listener và ngừng bộ đếm của View; thao tác này không hủy booking trên server.
    public void stop() {
        ++generation;
        uid = null;
        handler.removeCallbacks(tick);
        if (bookingListener != null) bookingListener.remove();
        if (paymentListener != null) paymentListener.remove();
        bookingListener = paymentListener = null;
        mine = Collections.emptyList(); transactions = Collections.emptyList();
        entries.setValue(Collections.emptyList());
    }

    @Override protected void onCleared() { stop(); }
}
