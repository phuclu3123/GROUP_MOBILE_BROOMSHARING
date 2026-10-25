package vn.edu.ueh.phuclu.broomsharing.ui.report;

import android.os.Bundle;
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
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

// Guest theo dõi báo cáo đã gửi; Host xem, phản hồi và đọc kết luận về phòng của mình.
public class ReportCenterActivity extends SessionActivity {
    private ReportCenterViewModel model;
    private ActivityBusinessListBinding b;
    private Boolean restoredMode;
    private boolean host;

    @Override
    protected int pageLayout() {
        return R.layout.activity_business_list;
    }

    @Override
    protected void onCreate(Bundle state) {
        if (state != null) restoredMode = state.getBoolean("reports.received");
        super.onCreate(state);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("reports.received", model != null && model.received);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        host = profile.getUserRole() == UserRole.HOST;
        b = ActivityBusinessListBinding.bind(findViewById(R.id.workspaceRoot));
        ManagementNavigation.applyInsets(this);
        b.toolbar.setNavigationOnClickListener(v -> finish());
        model = new ViewModelProvider(this).get(ReportCenterViewModel.class);
        if (restoredMode != null) model.received = restoredMode;
        else if (getIntent().hasExtra("received"))
            model.received = getIntent().getBooleanExtra("received", false);
        if (profile.getUserRole() != UserRole.HOST) model.received = false;
        b.switchMode.setVisibility(
                profile.getUserRole() == UserRole.HOST ? View.VISIBLE : View.GONE);
        b.switchMode.setOnClickListener(
                v -> {
                    model.received = !model.received;
                    watch();
                });
        b.refresh.setOnClickListener(v -> watch());
        model.action.observe(
                this,
                state -> {
                    if (state == null) return;
                    b.feedback.setText(
                            state.loading
                                    ? getString(R.string.business_loading)
                                    : state.error == null
                                            ? "Đã gửi phản hồi cho Admin."
                                            : state.error);
                    b.refresh.setEnabled(!state.loading);
                });
        model.rows.observe(
                this,
                state -> {
                    b.items.removeAllViews();
                    b.refresh.setEnabled(!state.loading);
                    b.feedback.setText(
                            state.loading
                                    ? getString(R.string.business_loading)
                                    : state.error != null
                                            ? state.error
                                            : "Tối đa 100 báo cáo mới nhất.");
                    if (state.data == null) return;
                    if (state.data.isEmpty()) b.feedback.setText(R.string.business_empty);
                    for (RoomReport row : state.data) {
                        TextView text = new TextView(this);
                        text.setText(
                                row.reason
                                        + " · "
                                        + FormatUtils.reportStatus(row.status)
                                        + "\nPhòng: "
                                        + row.roomId
                                        + (row.bookingId == null || row.bookingId.isEmpty()
                                                ? ""
                                                : "\nBooking: " + row.bookingId)
                                        + "\n"
                                        + row.description
                                        + "\nPhản hồi Host: "
                                        + row.hostResponse
                                        + "\nKết luận Admin: "
                                        + row.resolution);
                        text.setTextSize(15);
                        text.setTextColor(getColor(R.color.text_primary));
                        text.setPadding(8, 24, 8, 16);
                        b.items.addView(text);
                        if (model.received && "OPEN".equals(row.status)) {
                            MaterialButton button = new MaterialButton(this);
                            button.setText(R.string.reports_reply);
                            button.setOnClickListener(v -> reply(row));
                            b.items.addView(button);
                        }
                    }
                });
        watch();
    }

    private void watch() {
        if (host && model.received) {
            startActivity(
                    new android.content.Intent(
                            this,
                            vn.edu.ueh.phuclu.broomsharing.ui.host.HostReportsActivity.class));
            finish();
            return;
        }
        b.toolbar.setTitle(model.received ? R.string.reports_received : R.string.reports_sent);
        b.switchMode.setText(model.received ? R.string.reports_sent : R.string.reports_received);
        model.watch();
    }

    private void reply(RoomReport row) {
        EditText input = new EditText(this);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(2000)});
        input.setHint("Phản hồi từ 10 đến 2.000 ký tự");
        input.setText(row.hostResponse);
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.reports_reply)
                        .setView(input)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Gửi", null)
                        .create();
        dialog.setOnShowListener(
                d ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            String value = input.getText().toString().trim();
                                            if (value.length() < 10) {
                                                input.setError("Nhập ít nhất 10 ký tự.");
                                                return;
                                            }
                                            model.reply(row.id, value);
                                            dialog.dismiss();
                                        }));
        dialog.show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (model != null) watch();
    }

    @Override
    protected void onStop() {
        if (model != null) model.stop();
        super.onStop();
    }
}
