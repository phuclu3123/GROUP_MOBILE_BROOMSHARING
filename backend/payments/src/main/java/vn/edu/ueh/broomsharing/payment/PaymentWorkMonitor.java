package vn.edu.ueh.broomsharing.payment;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.ListenerRegistration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// Theo dõi có việc chờ bằng hai query limit(1); không quét Firestore định kỳ lúc rảnh.
final class PaymentWorkMonitor implements AutoCloseable {
    @FunctionalInterface
    interface Sweep { void run() throws Exception; }

    private volatile boolean pendingHolds = true, pendingPayments = true, closed;
    private final ListenerRegistration holds, payments;
    private final AtomicLong scans = new AtomicLong(), idleSkips = new AtomicLong();

    PaymentWorkMonitor(Firestore db) {
        // Chưa nhận snapshot hoặc listener lỗi thì vẫn cho đối soát để không bỏ sót nhả lịch.
        holds = db.collection("bookings").whereEqualTo("status", "PENDING")
                .whereEqualTo("paymentFlow", "INSTANT_5M").limit(1)
                .addSnapshotListener((rows, error) -> {
                    if (!closed) pendingHolds = error != null || rows == null || !rows.isEmpty();
                });
        payments = db.collection("payments").whereIn("status", List.of("CREATING", "PENDING"))
                .limit(1).addSnapshotListener((rows, error) -> {
                    if (!closed) pendingPayments = error != null || rows == null || !rows.isEmpty();
                });
    }

    boolean hasWork() { return !closed && (pendingHolds || pendingPayments); }

    void runIfNeeded(Sweep sweep) throws Exception {
        if (hasWork()) { scans.incrementAndGet(); sweep.run(); }
        else idleSkips.incrementAndGet();
    }

    long runs() { return scans.get() + idleSkips.get(); }

    // Chỉ ghi số lượt chạy/bỏ qua, không ghi mã booking, nội dung webhook hoặc thông tin ngân hàng.
    String summary() {
        return "Payment sync: scans=" + scans.get() + ", idleSkips=" + idleSkips.get()
                + ", workPending=" + hasWork();
    }

    @Override public void close() {
        closed = true;
        holds.remove();
        payments.remove();
    }
}
