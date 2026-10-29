package vn.edu.ueh.phuclu.broomsharing.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.ReviewAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityAdminDashboardBinding;
import vn.edu.ueh.phuclu.broomsharing.model.ReviewItem;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;
import vn.edu.ueh.phuclu.broomsharing.utils.UiForms;

/**
 * Màn hình Admin: hiển thị các yêu cầu và thao tác quản trị. Nhận trạng thái từ AdminViewModel để
 * cập nhật giao diện.
 */
public class AdminDashboardActivity extends SessionActivity {
    private AdminViewModel model;
    private ActivityAdminDashboardBinding b;
    private int currentGroup = R.id.admin_overview;
    private String restoredSection;
    private boolean rebuildingTabs;
    private String[] sections = {};
    private final ActivityResultLauncher<Intent> evidence =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null
                                && model != null)
                            model.viewedEvidence.add(
                                    result.getData().getStringExtra("ID")
                                            + ":"
                                            + result.getData().getStringExtra("PATH"));
                    });

    @Override
    protected int pageLayout() {
        return R.layout.activity_admin_dashboard;
    }

    @Override
    protected boolean requiresAdmin() {
        return true;
    }

    @Override
    protected void onCreate(Bundle state) {
        currentGroup =
                state == null
                        ? getIntent().getIntExtra(ManagementNavigation.SECTION, R.id.admin_overview)
                        : state.getInt("group", R.id.admin_overview);
        if (state != null) restoredSection = state.getString("section");
        super.onCreate(state);
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        b = ActivityAdminDashboardBinding.bind(findViewById(R.id.workspaceRoot));
        ManagementNavigation.applyInsets(this);
        model = new ViewModelProvider(this).get(AdminViewModel.class);
        b.adminGreeting.setText(getString(R.string.workspace_greeting, profile.name));
        b.openReview.setOnClickListener(v -> showGroup(R.id.admin_review, null));
        b.openManage.setOnClickListener(v -> showGroup(R.id.admin_manage, null));
        b.openReports.setOnClickListener(v -> showGroup(R.id.admin_reports, null));
        com.google.android.material.button.MaterialButton bookings =
                new com.google.android.material.button.MaterialButton(this);
        bookings.setText("Booking cần xử lý");
        ((android.view.ViewGroup) b.openReports.getParent()).addView(bookings);
        bookings.setOnClickListener(
                v -> startActivity(new Intent(this, AdminBookingsActivity.class)));
        b.openAudit.setOnClickListener(v -> showGroup(R.id.admin_manage, "AUDIT"));
        b.btnRefresh.setOnClickListener(v -> model.load(model.selected));
        b.reviewList.setLayoutManager(new LinearLayoutManager(this));
        b.adminTabs.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {
                    public void onTabSelected(TabLayout.Tab tab) {
                        if (!rebuildingTabs && tab.getPosition() < sections.length)
                            model.load(sections[tab.getPosition()]);
                    }

                    public void onTabUnselected(TabLayout.Tab tab) {}

                    public void onTabReselected(TabLayout.Tab tab) {}
                });
        ManagementNavigation.attach(this, UserRole.ADMIN, currentGroup, id -> showGroup(id, null));
        model.items.observe(
                this,
                state -> {
                    if (state == null || currentGroup == R.id.admin_overview) return;
                    b.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
                    b.btnRefresh.setEnabled(!state.loading);
                    b.btnRefresh.setText(
                            state.error == null
                                    ? R.string.workspace_refresh
                                    : R.string.workspace_retry);
                    if (state.loading || state.error != null) b.reviewList.setAdapter(null);
                    b.tvFeedback.setText(
                            state.loading
                                    ? getString(R.string.admin_loading)
                                    : state.error == null ? "" : state.error);
                    if (state.data != null) {
                        b.tvFeedback.setText(
                                state.data.isEmpty()
                                        ? getString(R.string.workspace_no_items)
                                        : getString(
                                                        R.string.workspace_item_count,
                                                        state.data.size())
                                                + ("AUDIT".equals(model.selected)
                                                        ? " · "
                                                                + getString(
                                                                        R.string
                                                                                .workspace_audit_limit)
                                                        : ""));
                        b.reviewList.setAdapter(
                                new ReviewAdapter(state.data, this::select, this::viewEvidence));
                    }
                });
        model.operation.observe(
                this,
                state -> {
                    if (state == null) return;
                    UiForms.enabled(b.getRoot(), !state.loading);
                    b.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
                    if (state.error != null) b.tvFeedback.setText(state.error);
                    if (state.data != null) {
                        model.operation.setValue(null);
                        model.load(model.selected);
                    }
                });
        showGroup(currentGroup, restoredSection);
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            public void handleOnBackPressed() {
                                if (currentGroup != R.id.admin_overview)
                                    showGroup(R.id.admin_overview, null);
                                else {
                                    setEnabled(false);
                                    getOnBackPressedDispatcher().onBackPressed();
                                    setEnabled(true);
                                }
                            }
                        });
    }

    private void showGroup(int id, String preferred) {
        currentGroup =
                id == R.id.admin_review || id == R.id.admin_manage || id == R.id.admin_reports
                        ? id
                        : R.id.admin_overview;
        boolean home = currentGroup == R.id.admin_overview;
        b.adminOverview.setVisibility(home ? View.VISIBLE : View.GONE);
        b.adminWorkPanel.setVisibility(home ? View.GONE : View.VISIBLE);
        ManagementNavigation.select(this, currentGroup);
        b.adminTitle.setText(
                home
                        ? R.string.workspace_overview
                        : currentGroup == R.id.admin_review
                                ? R.string.workspace_review
                                : currentGroup == R.id.admin_manage
                                        ? R.string.workspace_manage
                                        : R.string.workspace_reports);
        if (home) return;
        int[] labels;
        if (currentGroup == R.id.admin_review) {
            sections = new String[] {"HOST", "ROOM", "VERIFICATION"};
            labels =
                    new int[] {
                        R.string.workspace_host_applications,
                        R.string.workspace_room_approval,
                        R.string.workspace_verifications
                    };
            b.sectionHint.setText(R.string.workspace_admin_review_hint);
        } else if (currentGroup == R.id.admin_manage) {
            sections = new String[] {"HOST_STATUS", "ROOM_STATUS", "AUDIT"};
            labels =
                    new int[] {
                        R.string.workspace_hosts, R.string.workspace_rooms, R.string.workspace_audit
                    };
            b.sectionHint.setText(R.string.workspace_admin_manage_hint);
        } else {
            sections = new String[] {"REPORT"};
            labels = new int[] {R.string.workspace_reports};
            b.sectionHint.setText(R.string.workspace_admin_reports_hint);
        }
        rebuildingTabs = true;
        b.adminTabs.removeAllTabs();
        int selected = 0;
        for (int i = 0; i < sections.length; i++) {
            b.adminTabs.addTab(b.adminTabs.newTab().setText(labels[i]));
            if (sections[i].equals(preferred)) selected = i;
        }
        b.adminTabs.selectTab(b.adminTabs.getTabAt(selected));
        b.adminTabs.setVisibility(sections.length > 1 ? View.VISIBLE : View.GONE);
        rebuildingTabs = false;
        model.load(sections[selected]);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        currentGroup = intent.getIntExtra(ManagementNavigation.SECTION, R.id.admin_overview);
        if (b != null) showGroup(currentGroup, null);
    }

    private void viewEvidence(ReviewItem item) {
        if (item.evidenceRefs.isEmpty()) {
            b.tvFeedback.setText("Hồ sơ chưa có tệp bằng chứng. Có thể từ chối kèm lý do.");
            return;
        }
        String[] labels = new String[item.evidenceRefs.size()];
        for (int i = 0; i < labels.length; i++) {
            String path = item.evidenceRefs.get(i);
            labels[i] = (i + 1) + ". " + path.substring(path.lastIndexOf('/') + 1);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_evidence)
                .setItems(
                        labels,
                        (d, index) ->
                                evidence.launch(
                                        new Intent(this, EvidencePreviewActivity.class)
                                                .putExtra("ID", item.id)
                                                .putExtra("PATH", item.evidenceRefs.get(index))))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void select(ReviewItem item, boolean approve) {
        if ("REPORT".equals(item.kind)) {
            String[] labels =
                    approve
                            ? new String[] {
                                "Đóng báo cáo, ghi kết luận",
                                "Ẩn phòng khỏi danh sách công khai",
                                "Khóa Host, chặn nhận đặt mới"
                            }
                            : new String[] {"Bỏ qua báo cáo, ghi lý do"};
            String[] actions = {"NO_ACTION", "SUSPEND_ROOM", "BLOCK_HOST"};
            new AlertDialog.Builder(this)
                    .setTitle("Xử lý báo cáo")
                    .setItems(labels, (d, index) -> reportReason(item, approve, actions[index]))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        if (("VERIFICATION".equals(item.kind) || "HOST".equals(item.kind))
                && approve
                && !model.evidenceViewed(item)) {
            b.tvFeedback.setText(R.string.admin_evidence_required);
            viewEvidence(item);
            return;
        }
        boolean reasonRequired =
                !approve || item.kind.endsWith("_STATUS") || "VERIFICATION".equals(item.kind);
        EditText input = new EditText(this);
        input.setHint(R.string.admin_reason_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.admin_confirm)
                        .setMessage(item.title)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.admin_submit, null);
        if (reasonRequired) builder.setView(input);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(
                d ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            String reason = input.getText().toString().trim();
                                            if (reasonRequired
                                                    && (reason.isEmpty()
                                                            || reason.length() > 1000)) {
                                                input.setError(
                                                        getString(R.string.admin_reason_error));
                                                return;
                                            }
                                            model.review(item, approve, reason);
                                            dialog.dismiss();
                                        }));
        dialog.show();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("group", currentGroup);
        if (model != null) state.putString("section", model.selected);
        super.onSaveInstanceState(state);
    }

    private void reportReason(ReviewItem item, boolean resolve, String action) {
        EditText note = new EditText(this);
        note.setHint("Kết luận và lý do (10–1.000 ký tự)");
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Xác nhận xử lý")
                        .setMessage(
                                item.title
                                        + "\n"
                                        + "Không tự hủy booking hoặc chuyển tiền. Lịch sử và nhật"
                                        + " ký được giữ lại.")
                        .setView(note)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Xác nhận", null)
                        .create();
        dialog.setOnShowListener(
                d ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            String reason = note.getText().toString().trim();
                                            if (reason.length() < 10 || reason.length() > 1000) {
                                                note.setError("Nhập từ 10 đến 1.000 ký tự.");
                                                return;
                                            }
                                            model.resolveReport(item, resolve, action, reason);
                                            dialog.dismiss();
                                        }));
        dialog.show();
    }
}
