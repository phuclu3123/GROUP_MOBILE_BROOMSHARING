package vn.edu.ueh.phuclu.broomsharing;

import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.edu.ueh.phuclu.broomsharing.adapter.ReviewAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityAdminDashboardBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.admin.AdminViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.UiForms;

public class AdminDashboardActivity extends SessionActivity {
    @Override
    protected int pageLayout() {
        return R.layout.activity_admin_dashboard;
    }

    @Override
    protected boolean requiresAdmin() {
        return true;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        ActivityAdminDashboardBinding b =
                ActivityAdminDashboardBinding.bind(
                        ((android.view.ViewGroup) findViewById(android.R.id.content))
                                .getChildAt(0));
        AdminViewModel model = new ViewModelProvider(this).get(AdminViewModel.class);
        b.adminSignOut.setOnClickListener(v -> signOut());
        b.tabHosts.setOnClickListener(v -> model.load("HOST"));
        b.tabRooms.setOnClickListener(v -> model.load("ROOM"));
        b.tabReports.setOnClickListener(v -> model.load("REPORT"));
        b.btnRefresh.setOnClickListener(v -> model.load(model.selected));
        b.reviewList.setLayoutManager(new LinearLayoutManager(this));
        model.items.observe(
                this,
                state -> {
                    b.progressBar.setVisibility(
                            state.loading ? android.view.View.VISIBLE : android.view.View.GONE);
                    if (state.loading) {
                        b.reviewList.setAdapter(null);
                        b.tvFeedback.setText("Đang tải…");
                    }
                    if (state.error != null) b.tvFeedback.setText(state.error);
                    if (state.data != null) {
                        b.tvFeedback.setText(
                                state.data.isEmpty()
                                        ? "Không có mục chờ xử lý."
                                        : "Chờ xử lý: " + state.data.size());
                        b.reviewList.setAdapter(
                                new ReviewAdapter(
                                        state.data,
                                        (item, approve) -> {
                                            if (approve)
                                                new AlertDialog.Builder(this)
                                                        .setTitle("Xác nhận xử lý")
                                                        .setMessage(item.title)
                                                        .setNegativeButton("Hủy", null)
                                                        .setPositiveButton(
                                                                "Xác nhận",
                                                                (dialog, which) ->
                                                                        model.review(
                                                                                item, true, ""))
                                                        .show();
                                            else {
                                                EditText input = new EditText(this);
                                                input.setHint("Lý do từ chối/bỏ qua");
                                                new AlertDialog.Builder(this)
                                                        .setTitle("Phản hồi")
                                                        .setView(input)
                                                        .setNegativeButton("Hủy", null)
                                                        .setPositiveButton(
                                                                "Gửi",
                                                                (dialog, which) ->
                                                                        model.review(
                                                                                item,
                                                                                false,
                                                                                input.getText()
                                                                                        .toString()))
                                                        .show();
                                            }
                                        }));
                    }
                });
        model.operation.observe(
                this,
                state -> {
                    if (state == null) return;
                    UiForms.enabled(b.getRoot(), !state.loading);
                    b.progressBar.setVisibility(
                            state.loading ? android.view.View.VISIBLE : android.view.View.GONE);
                    if (state.error != null) b.tvFeedback.setText(state.error);
                    if (state.data != null) {
                        model.operation.setValue(null);
                        model.load(model.selected);
                    }
                });
        model.load(model.selected);
    }
}
