package vn.edu.ueh.phuclu.broomsharing.ui.booking;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.BookingAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityMyBookingsLiveBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestNavigation;
import vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Màn hình đơn của Guest: hiển thị danh sách và lọc theo trạng thái. Giao tiếp với BookingViewModel
 * để tải và cập nhật đơn.
 */
public class MyBookingsActivity extends SessionActivity {
    // Binding tới activity_my_bookings_live.xml.
    private ActivityMyBookingsLiveBinding b;

    // ViewModel tải danh sách, hủy booking và phát trạng thái cho giao diện.
    private BookingViewModel model;
    private PendingPaymentViewModel pendingPayments;

    // UID của Guest đang đăng nhập; dùng để tải danh sách của chính tài khoản đó.
    private String uid;

    // Vị trí bộ lọc được lưu để khôi phục sau khi Activity được dựng lại.
    private Integer restoredFilter;

    @Override
    protected void onCreate(Bundle state) {
        // Lưu bộ lọc đã chọn trước khi SessionActivity bắt đầu thiết lập màn hình.
        if (state != null) restoredFilter = state.getInt("booking.filter", 0);
        super.onCreate(state);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        // Lưu vị trí bộ lọc hiện tại; 0 tương ứng với xem tất cả trạng thái.
        state.putInt(
                "booking.filter",
                model == null
                        ? (restoredFilter == null ? 0 : restoredFilter)
                        : model.filterPosition);
        super.onSaveInstanceState(state);
    }

    @Override
    protected int pageLayout() {
        // Đây là layout live đang được Activity sử dụng.
        return R.layout.activity_my_bookings_live;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        // UID lấy từ hồ sơ đã xác thực, không lấy từ nội dung người dùng tự nhập.
        uid = profile.uid;

        // Gắn Binding vào layout lớp cha đã nạp và lấy ViewModel của Activity.
        b =
                ActivityMyBookingsLiveBinding.bind(
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        model = new ViewModelProvider(this).get(BookingViewModel.class);
        if (restoredFilter != null) model.filterPosition = restoredFilter;

        // Adapter nhận hai hành động từ từng thẻ: hủy booking và mở chi tiết.
        BookingAdapter adapter =
                new BookingAdapter(
                        item -> {
                            long now = System.currentTimeMillis();

                            // Tính mức hủy dự kiến để đưa vào hộp thoại xác nhận.
                            Integer rate =
                                    CancellationPolicy.versioned(item)
                                            ? CancellationPolicy.percent(item, now)
                                            : null;
                            new AlertDialog.Builder(this)
                                    .setTitle(R.string.booking_cancel)
                                    .setMessage(CancellationPolicy.preview(item, now))
                                    .setPositiveButton(
                                            R.string.booking_cancel,
                                            // Repository sẽ kiểm tra lại chính sách trước khi cập
                                            // nhật.
                                            (d, w) -> model.cancel(item.id, uid, rate))
                                    .setNegativeButton("Giữ đặt chỗ", null)
                                    .show();
                        },
                        item ->
                                // Mở chi tiết bằng mã của booking được nhấn.
                                startActivity(
                                        new Intent(this, BookingSuccessActivity.class)
                                                .putExtra("bookingId", item.id)),
                        item -> startActivity(new Intent(this, BookingPaymentActivity.class).putExtra("bookingId", item.id)));
        pendingPayments = new ViewModelProvider(this).get(PendingPaymentViewModel.class);
        pendingPayments.entries.observe(this, adapter::setPendingPayments);
        pendingPayments.start(uid);

        // RecyclerView hiển thị danh sách dọc và dùng BookingAdapter tạo từng thẻ.
        b.bookings.setLayoutManager(new LinearLayoutManager(this));
        b.bookings.setAdapter(adapter);

        // Nút thử lại đăng ký lại listener realtime cho danh sách của Guest.
        b.refresh.setOnClickListener(v -> model.watchMine(uid));

        // Khôi phục bộ lọc và cập nhật danh sách khi người dùng đổi trạng thái.
        b.bookingFilter.setSelection(model.filterPosition);
        b.bookingFilter.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onNothingSelected(AdapterView<?> parent) {}

                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        model.filterPosition = position;
                        render(adapter);
                    }
                });
        model.bookings.observe(
                this,
                state -> {
                    if (state == null) return;

                    // Cập nhật nút tải lại, thông báo lỗi/trạng thái và danh sách booking.
                    b.refresh.setEnabled(!state.loading && !model.busy());
                    b.feedback.setText(
                            state.loading
                                    ? getString(R.string.booking_loading)
                                    : state.error == null ? "" : state.error);
                    if (state.error != null) adapter.setData(Collections.emptyList());
                    if (state.data != null) {
                        render(adapter);
                    }
                });
        model.saved.observe(
                this,
                state -> {
                    if (state == null) return;

                    // Khóa thao tác hủy trong khi đang xử lý và báo kết quả cho Guest.
                    adapter.setBusy(state.loading);
                    b.refresh.setEnabled(!state.loading);
                    b.feedback.setText(
                            state.loading
                                    ? getString(R.string.booking_sending)
                                    : state.error == null
                                            ? getString(R.string.booking_updated)
                                            : state.error);
                    if (state.data != null) model.saved.setValue(null);
                });
        GuestNavigation.attach(this, R.id.nav_bookings);
        // Bắt đầu nghe danh sách ngay khi màn hình được thiết lập.
        model.watchMine(uid);
    }

    private void render(BookingAdapter adapter) {
        // Đọc danh sách mới nhất trong ViewModel; lúc tải/lỗi chưa có gì để lọc.
        UiState<List<Booking>> state = model.bookings.getValue();
        if (state == null || state.data == null) return;
        String[] statuses = {
            "",
            "PENDING",
            "CONFIRMED",
            "CANCELLED",
            "COMPLETED",
            "REJECTED",
            "CHECKED_IN",
            "EXPIRED",
            "NO_SHOW",
            "TERMINATED"
        };
        String wanted = statuses[Math.max(0, Math.min(model.filterPosition, statuses.length - 1))];
        List<Booking> rows = new ArrayList<>();

        // Lọc ở phía giao diện theo trạng thái đã chọn; chuỗi rỗng nghĩa là xem tất cả.
        for (Booking item : state.data)
            if (wanted.isEmpty() || wanted.equals(item.status)) rows.add(item);
        adapter.setData(rows);
        b.feedback.setText(rows.isEmpty() ? getString(R.string.booking_empty) : "");
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Màn hình quay lại foreground thì đăng ký listener để nhận thay đổi mới.
        if (model != null && uid != null) model.watchMine(uid);
        if (pendingPayments != null && uid != null) pendingPayments.start(uid);
    }

    @Override
    protected void onStop() {
        // Dừng listener khi màn hình không còn hiển thị.
        if (model != null) model.stopWatching();
        if (pendingPayments != null) pendingPayments.stop();
        super.onStop();
    }
}
