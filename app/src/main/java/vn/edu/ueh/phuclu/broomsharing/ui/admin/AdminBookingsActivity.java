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
                    for (int i = 0; i < b.items.getChildCount(); i++)
                        if (b.items.getChildAt(i) instanceof MaterialButton)
                            b.items.getChildAt(i).setEnabled(!state.loading);
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
                                            ? "Xử lý từng đơn của Host bị khóa hoặc xác minh lại"
                                                  + " việc sử dụng. Mọi quyết định cần lý do và"
                                                  + " được lưu nhật ký."
                                            : state.error);
                    if (state.data == null) return;
                    for (Booking row : state.data) {
                        if ("COMPLETED".equals(row.status) && row.usageConfirmedAt != null)
                            continue;
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
                                        + FormatUtils.bookingStatus(row.status)
                                        + "\nKết thúc dự kiến: "
                                        + TimeUtils.formatVietnam(row.endAt)
                                        + (row.rejectionReason == null
                                                        || row.rejectionReason.isEmpty()
                                                ? ""
                                                : "\nGhi nhận: " + row.rejectionReason));
                        text.setTextColor(getColor(R.color.text_primary));
                        b.items.addView(text);
                        if ("PENDING".equals(row.status) || "CONFIRMED".equals(row.status)) {
                            addAction(row, "Hủy nếu Host bị khóa", "CANCELLED");
                        }
                        long now = System.currentTimeMillis();
                        if ("CHECKED_IN".equals(row.status)) {
                            addAction(row, "Kết thúc do sự cố (Host bị khóa)", "TERMINATED");
                            if (row.endAt != null && row.endAt.getTime() <= now)
                                addAction(row, "Hoàn tất thay Host bị khóa", "COMPLETED");
                        } else if ("CONFIRMED".equals(row.status)
                                && row.endAt != null
                                && row.endAt.getTime() <= now) {
                            addAction(row, "Xác minh khách đã sử dụng", "COMPLETED");
                            addAction(row, "Khách không đến (Host bị khóa)", "NO_SHOW");
                        } else if ("NO_SHOW".equals(row.status) || "COMPLETED".equals(row.status)) {
                            addAction(row, "Xác minh và xác nhận khách đã sử dụng", "COMPLETED");
                        }
                    }
                });
        model.watch();
    }

    private void addAction(Booking row, String title, String target) {
        MaterialButton button = new MaterialButton(this);
        button.setText(title);
        button.setTag(row.id + ":" + target);
        button.setEnabled(model.action.getValue() == null || !model.action.getValue().loading);
        button.setOnClickListener(v -> resolve(row, target));
        b.items.addView(button);
    }

    private void resolve(Booking row, String target) {
        EditText input = new EditText(this);
        input.setHint("Lý do can thiệp, tối đa 500 ký tự");
        input.setMinLines(2);
        input.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(500)});
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(FormatUtils.bookingStatus(target))
                        .setMessage(
                                "Xác minh thực tế với các bên trước khi xử lý. Giữ lịch sử và nhật"
                                    + " ký; không xác nhận thu/hoàn tiền. Việc khép đơn không mở"
                                    + " khóa Host.")
                        .setView(input)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Xác nhận xử lý", null)
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
                                            model.resolve(row.id, target, reason);
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
