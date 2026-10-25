package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import androidx.lifecycle.ViewModelProvider;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityHostApplyBinding;
import vn.edu.ueh.phuclu.broomsharing.model.HostApplication;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;
import vn.edu.ueh.phuclu.broomsharing.utils.UiForms;

/*
    Nhận thông tin xin quyền Host và quan sát kết quả nộp đơn qua ViewModel.
    Gửi đơn không tự nâng role; quyền cho thuê chỉ thay đổi khi Admin duyệt.
 */
public class HostApplyActivity extends SessionActivity {
    private ActivityHostApplyBinding binding;
    private HostViewModel model;
    private boolean pending;

    @Override
    protected int pageLayout() {
        return R.layout.activity_host_apply;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        if (profile.getUserRole() == UserRole.HOST) {
            startActivity(new Intent(this, HostDashboardActivity.class));
            finish();
            return;
        }
        binding =
                ActivityHostApplyBinding.bind(
                        ((ViewGroup) findViewById(android.R.id.content))
                                .getChildAt(0));
        model = new ViewModelProvider(this).get(HostViewModel.class);
        binding.etFullName.setText(profile.name);
        binding.etEmail.setText(profile.email);
        binding.etPhone.setText(profile.phone);
        binding.btnBack.setOnClickListener(
                v -> {
                    if (!model.busy()) finish();
                });
        binding.btnRefresh.setOnClickListener(v -> model.loadApplication());
        binding.btnSubmit.setOnClickListener(
                v -> {
                    if (model.busy() || pending) return;
                    HostApplication a =
                            new HostApplication(
                                    profile.uid,
                                    UiForms.text(binding.etFullName),
                                    UiForms.text(binding.etPhone),
                                    UiForms.text(binding.etEmail),
                                    binding.rbBusiness.isChecked() ? "BUSINESS" : "INDIVIDUAL",
                                    UiForms.text(binding.etDoc));
                    model.submitApplication(a);
                });
        model.application.observe(
                this,
                state -> {
                    binding.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
                    binding.btnSubmit.setEnabled(!state.loading && !model.busy() && !pending);
                    if (state.error != null) {
                        binding.tvFeedback.setText(state.error);
                        return;
                    }
                    if (state.loading) return;
                    HostApplication a = state.data;
                    pending = a != null && !"REJECTED".equals(a.getStatus());
                    binding.btnSubmit.setEnabled(!pending && !model.busy());
                    binding.tvApplicationStatus.setText(
                            a == null
                                    ? "Bạn chưa gửi hồ sơ."
                                    : FormatUtils.reviewStatus(
                                                    a.getStatus())
                                            + (a.getRejectionReason() == null
                                                    ? ""
                                                    : "\n" + a.getRejectionReason()));
                    if (a != null) {
                        binding.etFullName.setText(a.getFullName());
                        binding.etPhone.setText(a.getPhone());
                        binding.etEmail.setText(a.getEmail());
                        binding.etDoc.setText(a.getIdentityDoc());
                        binding.rgOrgType.check(
                                "BUSINESS".equals(a.getOrganizationType())
                                        ? R.id.rbBusiness
                                        : R.id.rbIndividual);
                    }
                });
        model.saved.observe(
                this,
                state -> {
                    if (state == null) return;
                    UiForms.enabled(binding.getRoot(), !state.loading);
                    binding.btnSubmit.setEnabled(!state.loading && !pending);
                    binding.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
                    if (state.error != null) binding.tvFeedback.setText(state.error);
                    if (state.data != null) {
                        model.consumeSave();
                        binding.tvFeedback.setText("");
                        model.loadApplication();
                    }
                });
        model.loadApplication();
    }
}
