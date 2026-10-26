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

    @Override
    protected int pageLayout() {
        return R.layout.activity_booking_payment;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        ActivityBookingPaymentBinding b =
                ActivityBookingPaymentBinding.bind(
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        model = new ViewModelProvider(this).get(PaymentViewModel.class);
        b.paymentBack.setOnClickListener(v -> finish());
        b.paymentRefresh.setOnClickListener(v -> model.load(false));
        b.paymentRetry.setOnClickListener(v -> model.load(true));
        b.paymentOpen.setOnClickListener(
                v -> {
                    if (current == null) return;
                    Uri uri = Uri.parse(current.checkoutUrl);
                    if ("https".equals(uri.getScheme()) && "pay.payos.vn".equals(uri.getHost()))
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                });
        model.state.observe(
                this,
                state -> {
                    if (state == null) return;
                    current = state.data;
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
                                                                    : "Trạng thái: "
                                                                            + current.status);
                    boolean pending = current != null && "PENDING".equals(current.status);
                    b.paymentQr.setVisibility(
                            pending && !current.qrCode.isEmpty() ? View.VISIBLE : View.GONE);
                    b.paymentOpen.setVisibility(
                            pending && !current.checkoutUrl.isEmpty() ? View.VISIBLE : View.GONE);
                    b.paymentRefresh.setEnabled(!state.loading);
                    b.paymentRetry.setVisibility(state.error == null ? View.GONE : View.VISIBLE);
                    if (current == null) {
                        b.paymentAmount.setText("");
                        b.paymentAccount.setText("");
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
                });
        model.bind(getIntent().getStringExtra("bookingId"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (model != null && current != null) model.load(false);
    }
}
