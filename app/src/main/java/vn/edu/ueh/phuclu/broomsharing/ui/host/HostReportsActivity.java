package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.HostReportAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityHostReportsBinding;
import vn.edu.ueh.phuclu.broomsharing.model.ReviewItem;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;

public class HostReportsActivity extends SessionActivity {
    private ActivityHostReportsBinding b;
    private HostViewModel viewModel;
    private HostReportAdapter adapter;

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
        viewModel = new ViewModelProvider(this).get(HostViewModel.class);

        b.btnBack.setOnClickListener(v -> finish());
        b.btnRefresh.setOnClickListener(v -> refresh());

        setupList();
        observeViewModel();

        refresh();
    }

    private void setupList() {
        adapter = new HostReportAdapter(this::showExplainDialog);
        b.rvReports.setLayoutManager(new LinearLayoutManager(this));
        b.rvReports.setAdapter(adapter);
    }

    private void refresh() {
        viewModel.loadReports();
    }

    private void showExplainDialog(ReviewItem item) {
        final EditText etResponse = new EditText(this);
        etResponse.setHint("Nhập nội dung giải trình hoặc cung cấp thông tin cho Admin...");
        etResponse.setPadding(40, 30, 40, 30);
        etResponse.setMinLines(3);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Giải trình về báo cáo")
                .setMessage("Lý do: " + item.title + "\n\nNhập phản hồi chính thức của bạn để Admin đối chiếu khi xử lý:")
                .setView(etResponse)
                .setPositiveButton("Gửi giải trình", null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = etResponse.getText().toString().trim();
            if (text.isEmpty()) {
                etResponse.setError("Vui lòng nhập nội dung giải trình");
                return;
            }
            viewModel.submitReportResponse(item.id, text);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void observeViewModel() {
        viewModel.reports.observe(this, state -> {
            if (state == null) return;
            b.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            b.btnRefresh.setEnabled(!state.loading);
            if (state.error != null) {
                Toast.makeText(this, "Lỗi tải báo cáo: " + state.error, Toast.LENGTH_SHORT).show();
            } else if (state.data != null) {
                adapter.setData(state.data);
                b.tvEmptyReports.setVisibility(state.data.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.reportResponseSubmitted.observe(this, state -> {
            if (state == null) return;
            b.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            if (state.error != null) {
                Toast.makeText(this, "Gửi giải trình thất bại: " + state.error, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Đã gửi văn bản giải trình thành công tới Admin!", Toast.LENGTH_SHORT).show();
                viewModel.reportResponseSubmitted.setValue(null);
                refresh();
            }
        });
    }
}
