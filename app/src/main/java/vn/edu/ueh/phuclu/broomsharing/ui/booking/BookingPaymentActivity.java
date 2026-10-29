package vn.edu.ueh.phuclu.broomsharing.ui.booking;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.*;

import androidx.lifecycle.ViewModelProvider;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityBookingPaymentBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

// Hiện QR ngay trong app; trang payOS mở bằng trình duyệt ngoài, không nạp web không tin cậy vào
// WebView.
public final class BookingPaymentActivity extends SessionActivity {
    private PaymentViewModel model;
    private Payment current;
    private ActivityBookingPaymentBinding binding;
    private BookingViewModel bookings;
    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), allowed -> {
                PaymentReminderService.setEnabled(this, allowed);
                updatePhoneReminder();
            });

    @Override
    protected int pageLayout() {
        return R.layout.activity_booking_payment;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        ActivityBookingPaymentBinding b =
                ActivityBookingPaymentBinding.bind(
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        binding = b;
        b.paymentNotifications.setOnClickListener(v -> {
            if (PaymentReminderService.enabled(this)) PaymentReminderService.setEnabled(this, false);
            else if (android.os.Build.VERSION.SDK_INT >= 33 && !PaymentReminderService.allowed(this))
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            else PaymentReminderService.setEnabled(this, true);
            updatePhoneReminder();
        });
        model = new ViewModelProvider(this).get(PaymentViewModel.class);
        b.paymentBack.setOnClickListener(v -> finish());
        b.paymentDetails.setOnClickListener(v -> startActivity(new Intent(this, BookingSuccessActivity.class)
                .putExtra("bookingId", getIntent().getStringExtra("bookingId"))));
        bookings = new ViewModelProvider(this).get(BookingViewModel.class);
        bookings.detail.observe(this, state -> {
            renderOutcome();
        });
        bookings.watchDetail(getIntent().getStringExtra("bookingId"));
        b.paymentRefresh.setOnClickListener(v -> model.load(false));
        b.paymentRetry.setOnClickListener(v -> model.load(true));
        b.paymentOpen.setOnClickListener(
                v -> {
                    if (current == null || !current.canPay(System.currentTimeMillis())) return;
                    Uri uri = Uri.parse(current.checkoutUrl);
                    if ("https".equals(uri.getScheme()) && "pay.payos.vn".equals(uri.getHost()))
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                });
        model.state.observe(
                this,
                state -> {
                    if (state == null) return;
                    current = state.data;
                    updatePhoneReminder();
                    b.paymentStatus.setText(
                            state.loading
                                    ? "Đang kết nối payOS…"
                                    : state.error != null
                                            ? state.error
                                            : "REVIEW_REQUIRED".equals(current.status)
                                                    ? "Đã ghi nhận tiền, cần đối soát booking. Chưa"
                                                          + " hoàn tiền."
                                                    : current.received()
                                                            ? "Đã nhận khoản trả trước 40%."
                                                            : "PENDING".equals(current.status)
                                                                    ? "Chờ chuyển khoản"
                                                                    : "CANCELLED".equals(current.status)
                                                                            ? "Thanh toán đã hủy • Chưa đặt thành công"
                                                                            : "EXPIRED".equals(current.status)
                                                                                    ? "Đặt chỗ không thành công • Hết hạn thanh toán"
                                                                                    : "Đang tạo mã thanh toán…");
                    boolean pending = current != null && current.canPay(System.currentTimeMillis());
                    b.paymentQr.setVisibility(
                            pending && !current.qrCode.isEmpty() ? View.VISIBLE : View.GONE);
                    b.paymentOpen.setVisibility(
                            pending && !current.checkoutUrl.isEmpty() ? View.VISIBLE : View.GONE);
                    b.paymentRefresh.setEnabled(!state.loading);
                    b.paymentRetry.setVisibility(state.error == null ? View.GONE : View.VISIBLE);
                    if (current == null) {
                        b.paymentAmount.setText("");
                        b.paymentAccount.setText("");
                        renderOutcome();
                        return;
                    }
                    b.paymentAmount.setText(FormatUtils.formatCurrency(current.amount));
                    b.paymentAccount.setText(
                            "Mã giao dịch: "
                                    + current.orderCode
                                    + "\n"
                                    + current.accountName
                                    + "\n"
                                    + current.accountNumber);
                    renderExpiry();
                    if (pending && !current.qrCode.isEmpty()) {
                        try {
                            BitMatrix matrix =
                                    new MultiFormatWriter()
                                            .encode(
                                                    current.qrCode,
                                                    BarcodeFormat.QR_CODE,
                                                    768,
                                                    768);
                            int[] pixels = new int[768 * 768];
                            for (int y = 0; y < 768; y++)
                                for (int x = 0; x < 768; x++)
                                    pixels[y * 768 + x] =
                                            matrix.get(x, y) ? 0xff000000 : 0xffffffff;
                            b.paymentQr.setImageBitmap(
                                    Bitmap.createBitmap(pixels, 768, 768, Bitmap.Config.ARGB_8888));
                        } catch (Exception e) {
                            b.paymentQr.setVisibility(View.GONE);
                        }
                    }
                    renderOutcome();
                });
        model.bind(getIntent().getStringExtra("bookingId"));
        model.clock.observe(this, ignored -> renderExpiry());
        model.startUpdates();
    }

    private void renderExpiry() {
        if (binding == null) return;
        boolean payable = current != null && current.canPay(System.currentTimeMillis());
        if (!payable) binding.paymentNotifications.setVisibility(View.GONE);
        binding.paymentQr.setVisibility(payable && !current.qrCode.isEmpty() ? View.VISIBLE : View.GONE);
        binding.paymentOpen.setVisibility(payable && !current.checkoutUrl.isEmpty() ? View.VISIBLE : View.GONE);
        if (current == null || !"PENDING".equals(current.status)) {
            binding.paymentExpiry.setText("");
            return;
        }
        long seconds = current.remainingSeconds(System.currentTimeMillis());
        binding.paymentExpiry.setText(seconds == 0
                ? "QR đã hết thời hạn. Đang chờ server đối soát; không chuyển thêm tiền."
                : "Còn " + seconds / 60 + " phút " + seconds % 60 + " giây để thanh toán.");
        if (seconds == 0) binding.paymentStatus.setText("Hết thời hạn thanh toán");
        renderOutcome();
    }

    private void updatePhoneReminder() {
        if (binding == null) return;
        boolean payable = current != null && current.canPay(System.currentTimeMillis());
        binding.paymentNotifications.setVisibility(payable ? View.VISIBLE : View.GONE);
        // Android 13+ vẫn cần người dùng cấp quyền; chỉ hỏi khi đã có phiên QR thật.
        if (payable && android.os.Build.VERSION.SDK_INT >= 33
                && PaymentReminderService.shouldAskPermission(this)) {
            PaymentReminderService.markPermissionAsked(this);
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        binding.paymentNotifications.setText(PaymentReminderService.enabled(this)
                ? "Tắt nhắc thanh toán trên điện thoại" : "Bật nhắc thanh toán trên điện thoại");
        if (payable) PaymentReminderService.start(this, current);
    }

    private void renderOutcome() {
        if (binding == null || bookings == null || bookings.detail.getValue() == null) return;
        Booking booking = bookings.detail.getValue().data;
        if (booking == null || !booking.isInstantPayment()) return;
        boolean review = "REVIEW_REQUIRED".equals(booking.paymentStatus);
        boolean terminal = java.util.Arrays.asList("EXPIRED", "CANCELLED", "REJECTED").contains(booking.status);
        if (review || terminal) {
            binding.paymentStatus.setText(review
                    ? "Đã ghi nhận tiền • Cần đối soát, chưa xác nhận đặt chỗ"
                    : "EXPIRED".equals(booking.status) ? "Đặt chỗ không thành công • Hết hạn thanh toán"
                    : "Đặt chỗ đã hủy • Không tiếp tục thanh toán");
            binding.paymentQr.setVisibility(View.GONE);
            binding.paymentOpen.setVisibility(View.GONE);
            binding.paymentRetry.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (model != null && current != null) model.load(false);
        if (model != null) model.startUpdates();
        if (bookings != null) bookings.watchDetail(getIntent().getStringExtra("bookingId"));
    }

    @Override
    protected void onStop() {
        if (bookings != null) bookings.stopDetail();
        if (model != null) model.stopUpdates();
        super.onStop();
    }
}
