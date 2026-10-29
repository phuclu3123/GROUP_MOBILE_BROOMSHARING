package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.HostReportAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityHostReportsBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;
import vn.edu.ueh.phuclu.broomsharing.ui.report.ReportCenterViewModel;

// Màn Host được ghép từ nhánh Hoàng; mọi đọc/ghi vẫn dùng ReportRepository chung.
public class HostReportsActivity extends SessionActivity {
    private ActivityHostReportsBinding b;
    private ReportCenterViewModel model;

    @Override
    protected int pageLayout() {
        return R.layout.activity_host_reports;
    }

    @Override
    protected boolean requiresHost() {
        return true;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        b = ActivityHostReportsBinding.bind(findViewById(R.id.workspaceRoot));
        ManagementNavigation.applyInsets(this);
        model = new ViewModelProvider(this).get(ReportCenterViewModel.class);
        model.received = true;
        HostReportAdapter adapter = new HostReportAdapter(this::reply);
        b.rvReports.setLayoutManager(new LinearLayoutManager(this));
        b.rvReports.setAdapter(adapter);
        b.btnBack.setOnClickListener(v -> finish());
        b.btnRefresh.setOnClickListener(v -> model.watch());
        model.rows.observe(
                this,
                s -> {
                    if (s == null) return;
                    b.progressBar.setVisibility(s.loading ? View.VISIBLE : View.GONE);
                    b.tvEmptyReports.setText(
                            s.error != null ? s.error : "Chưa có báo cáo về phòng của bạn.");
                    b.tvEmptyReports.setVisibility(
                            s.error != null || s.data == null || s.data.isEmpty()
                                    ? View.VISIBLE
                                    : View.GONE);
                    adapter.setData(s.data);
                });
        model.action.observe(
                this,
                s -> {
                    if (s == null) return;
                    adapter.setBusy(s.loading);
                    if (!s.loading) {
                        Toast.makeText(
                                        this,
                                        s.error == null ? "Đã lưu phản hồi." : s.error,
                                        Toast.LENGTH_LONG)
                                .show();
                        model.action.setValue(null);
                    }
                });
        model.watch();
    }

    private void reply(RoomReport item) {
        EditText input = new EditText(this);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(2000)});
        input.setText(item.hostResponse);
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Giải trình báo cáo")
                        .setView(input)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Gửi", null)
                        .create();
        dialog.setOnShowListener(
                v ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        button -> {
                                            String value = input.getText().toString().trim();
                                            if (value.length() < 10) {
                                                input.setError("Nhập ít nhất 10 ký tự.");
                                                return;
                                            }
                                            model.reply(item.id, value);
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
