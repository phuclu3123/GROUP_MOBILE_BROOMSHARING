package vn.edu.ueh.phuclu.broomsharing.ui.common;

import android.content.Intent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.databinding.ViewPendingCheckoutBinding;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.*;
import vn.edu.ueh.phuclu.broomsharing.utils.PendingCheckout;

// Dùng chung ở trang phòng và form chọn lịch; luôn mở đúng booking/order cũ, không tạo đơn mới.
public final class CheckoutReminder {
    private CheckoutReminder() {}

    public static void attach(AppCompatActivity activity, View view, String uid, String roomId) {
        ViewPendingCheckoutBinding binding = ViewPendingCheckoutBinding.bind(view);
        PendingPaymentViewModel model = new ViewModelProvider(activity).get(PendingPaymentViewModel.class);
        model.entries.observe(activity, all -> {
            List<PendingCheckout> matching = new ArrayList<>();
            for (PendingCheckout entry : all)
                if (roomId == null || roomId.equals(entry.booking.roomId)) matching.add(entry);
            view.setVisibility(matching.isEmpty() ? View.GONE : View.VISIBLE);
            if (matching.isEmpty()) return;
            vn.edu.ueh.phuclu.broomsharing.ui.booking.PaymentReminderService.start(activity, matching.get(0).payment);
            binding.pendingSummary.setText(matching.size() == 1 ? matching.get(0).summary(System.currentTimeMillis())
                    : matching.size() + " lượt đang chờ thanh toán • Chọn lượt để tiếp tục");
            binding.pendingResume.setOnClickListener(v -> {
                if (matching.size() == 1) open(activity, matching.get(0));
                else {
                    String[] labels = new String[matching.size()];
                    for (int i = 0; i < labels.length; i++) labels[i] = matching.get(i).summary(System.currentTimeMillis());
                    new MaterialAlertDialogBuilder(activity).setTitle("Tiếp tục thanh toán")
                            .setItems(labels, (dialog, index) -> open(activity, matching.get(index)))
                            .setNegativeButton(android.R.string.cancel, null).show();
                }
            });
        });
        activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onStart(LifecycleOwner owner) { model.start(uid); }
            @Override public void onStop(LifecycleOwner owner) { model.stop(); }
        });
    }

    private static void open(AppCompatActivity activity, PendingCheckout entry) {
        if (!entry.payment.canPay(System.currentTimeMillis())) return;
        activity.startActivity(new Intent(activity, BookingPaymentActivity.class).putExtra("bookingId", entry.booking.id));
    }
}
