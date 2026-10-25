package vn.edu.ueh.phuclu.broomsharing.ui.admin;

import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityBusinessListBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

// Không hủy hàng loạt khi khóa Host; Admin đọc từng đơn và nhập lý do trước khi can thiệp.
public class AdminBookingsActivity extends SessionActivity {
    private AdminBookingsViewModel model;
    private ActivityBusinessListBinding b;

    @Override
    protected int pageLayout() {
        return R.layout.activity_business_list;
    }

    @Override
    protected boolean requiresAdmin() {
        return true;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        b = ActivityBusinessListBinding.bind(findViewById(R.id.workspaceRoot));
        ManagementNavigation.applyInsets(this);
        b.toolbar.setTitle("Booking cần xử lý");
        b.toolbar.setNavigationOnClickListener(v -> finish());
        model = new ViewModelProvider(this).get(AdminBookingsViewModel.class);
        b.refresh.setOnClickListener(v -> model.watch());
        b.switchMode.setVisibility(View.VISIBLE);
        b.switchMode.setText("Đối soát lịch phòng cũ");
        b.switchMode.setOnClickListener(
                v -> {
                    EditText input = new EditText(this);
                    input.setHint("Mã phòng cần đối soát");
                    new AlertDialog.Builder(this)
                            .setTitle("Đối soát số booking giữ chỗ")
                            .setView(input)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(
                                    "Đối soát",
                                    (d, w) -> model.reconcile(input.getText().toString().trim()))
                            .show();
                });
        model.action.observe(
                this,
                state -> {
                    if (state == null) return;
                    b.feedback.setText(
                            state.loading
                                    ? getString(R.string.business_loading)
                                    : state.error == null
                                            ? "Đã xử lý và lưu nhật ký."
                                            : state.error);
                });
        model.rows.observe(
                this,
                state -> {
                    b.refresh.setEnabled(!state.loading);
                    b.items.removeAllViews();
                    b.feedback.setText(
                            state.loading
                                    ? getString(R.string.business_loading)
                                    : state.error == null
                                            ? "Các đơn chưa kết thúc, sắp xếp theo giờ bắt đầu. Đơn"
                                                    + " quá giờ chờ duyệt được đồng bộ hết hạn;"
                                                    + " CHECKED_IN chỉ xem xét riêng, không tự kết"
                                                    + " thúc."
                                            : state.error);
                    if (state.data == null) return;
                    for (Booking row : state.data) {
                        TextView text = new TextView(this);
                        text.setTextSize(15);
                        text.setPadding(8, 24, 8, 16);
                        text.setText(
                                row.roomName
                                        + "\n"
                                        + row.id
                                        + "\nHost: "
                                        + row.ownerUid
                                        + "\n"
                                        + TimeUtils.formatVietnam(row.startAt)
                                        + "\n"
                                        + FormatUtils.bookingStatus(row.status));
                        text.setTextColor(getColor(R.color.text_primary));
                        b.items.addView(text);
                        if ("PENDING".equals(row.status) || "CONFIRMED".equals(row.status)) {
                            MaterialButton button = new MaterialButton(this);
                            button.setText("Hủy nếu Host bị khóa");
                            button.setOnClickListener(v -> cancel(row));
                            b.items.addView(button);
                        }
                    }
                });
        model.watch();
    }

    private void cancel(Booking row) {
        EditText input = new EditText(this);
        input.setHint("Lý do can thiệp, tối đa 500 ký tự");
        input.setMinLines(2);
        input.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(500)});
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Hủy booking của Host bị khóa")
                        .setMessage(
                                "Chỉ hủy đơn chưa check-in. Nhả lịch và giữ lịch sử; không xác nhận"
                                        + " hoàn tiền.")
                        .setView(input)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Hủy booking", null)
                        .create();
        dialog.setOnShowListener(
                d ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            String reason = input.getText().toString().trim();
                                            if (reason.isEmpty()) {
                                                input.setError("Cần lý do xử lý.");
                                                return;
                                            }
                                            model.cancel(row.id, reason);
                                            dialog.dismiss();
                                        }));
        dialog.show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (model != null) model.watch();
    }

    @Override
    protected void onStop() {
        if (model != null) model.stop();
        super.onStop();
    }
}
