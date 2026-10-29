package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityHostApplyBinding;
import vn.edu.ueh.phuclu.broomsharing.model.HostApplication;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.profile.AvatarEditor;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;
import vn.edu.ueh.phuclu.broomsharing.utils.OnlineImages;
import vn.edu.ueh.phuclu.broomsharing.utils.UiForms;

/*
   Nhận thông tin xin quyền Host và quan sát kết quả nộp đơn qua ViewModel.
   Gửi đơn không tự nâng role; quyền cho thuê chỉ thay đổi khi Admin duyệt.
*/
public class HostApplyActivity extends SessionActivity {
    private ActivityHostApplyBinding binding;
    private HostApplicationViewModel model;
    private boolean pending;
    private final ActivityResultLauncher<String[]> frontPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(), uri -> choose(0, uri));
    private final ActivityResultLauncher<String[]> backPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(), uri -> choose(1, uri));

    private void choose(int side, Uri uri) {
        if (uri == null || model == null || model.busy() || pending) return;
        if ("content".equals(uri.getScheme()))
            try {
                getContentResolver()
                        .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
        model.select(side, uri);
        renderImages();
    }

    private void renderImages() {
        if (model.selected[0] != null) OnlineImages.show(binding.identityFront, model.selected[0]);
        if (model.selected[1] != null) OnlineImages.show(binding.identityBack, model.selected[1]);
        boolean enabled = !pending && !model.busy();
        binding.btnIdentityFrontGallery.setEnabled(enabled);
        binding.btnIdentityFrontUrl.setEnabled(enabled);
        binding.btnIdentityBackGallery.setEnabled(enabled);
        binding.btnIdentityBackUrl.setEnabled(enabled);
    }

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
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        model = new ViewModelProvider(this).get(HostApplicationViewModel.class);
        binding.btnIdentityFrontGallery.setOnClickListener(
                v -> frontPicker.launch(new String[] {"image/jpeg", "image/png", "image/webp"}));
        binding.btnIdentityBackGallery.setOnClickListener(
                v -> backPicker.launch(new String[] {"image/jpeg", "image/png", "image/webp"}));
        binding.btnIdentityFrontUrl.setOnClickListener(
                v -> AvatarEditor.urlDialog(this, uri -> choose(0, uri)));
        binding.btnIdentityBackUrl.setOnClickListener(
                v -> AvatarEditor.urlDialog(this, uri -> choose(1, uri)));
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
                    renderImages();
                    binding.btnSubmit.setEnabled(!pending && !model.busy());
                    binding.tvApplicationStatus.setText(
                            a == null
                                    ? "Bạn chưa gửi hồ sơ."
                                    : FormatUtils.reviewStatus(a.getStatus())
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
                    renderImages();
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
