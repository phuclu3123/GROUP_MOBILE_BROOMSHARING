package vn.edu.ueh.phuclu.broomsharing.ui.profile;

import android.content.Intent;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityProfileBinding;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingNoticesActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestNavigation;
import vn.edu.ueh.phuclu.broomsharing.ui.common.RoleAccountActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.favorites.FavoritesActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostApplyActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostDashboardActivity;

/**
 * Màn hình hồ sơ Guest: hiển thị thông tin tài khoản và các lối tắt cá nhân. Kế thừa
 * SessionActivity để kiểm tra phiên trước khi mở giao diện.
 */
public class ProfileActivity extends SessionActivity {
    private final AvatarEditor avatar = new AvatarEditor(this);

    @Override
    protected int pageLayout() {
        return R.layout.activity_profile;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        if (profile.getUserRole() == UserRole.ADMIN) {
            startActivity(new Intent(this, RoleAccountActivity.class));
            finish();
            return;
        }
        ActivityProfileBinding binding =
                ActivityProfileBinding.bind(findViewById(R.id.profileRoot));
        binding.profileName.setText(profile.name);
        avatar.bind(profile);
        binding.profileInitial.setVisibility(View.GONE);
        binding.profileSubtitle.setText(profile.email);
        if (profile.phone != null && !profile.phone.isEmpty()) {
            binding.profilePhone.setVisibility(View.VISIBLE);
            binding.profilePhone.setText(
                    profile.phoneVerified
                            ? profile.phone
                            : getString(R.string.auth_phone_unverified, profile.phone));
        }
        binding.profileInitial.setText(
                profile.name == null || profile.name.isEmpty()
                        ? "B"
                        : profile.name.substring(0, 1));
        boolean isHost = profile.getUserRole() == UserRole.HOST;
        binding.profileBadge.setText(
                isHost ? R.string.guest_renter_mode : R.string.auth_role_guest);
        binding.profileSignOut.setOnClickListener(
                v ->
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.workspace_signout_title)
                                .setMessage(R.string.workspace_signout_message)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(
                                        R.string.workspace_signout, (dialog, which) -> signOut())
                                .show());
        binding.btnFavorites.setOnClickListener(
                v -> startActivity(new Intent(this, FavoritesActivity.class)));
        binding.btnNotices.setOnClickListener(
                v -> startActivity(new Intent(this, BookingNoticesActivity.class)));

        binding.btnMenuMySpaces.setText(R.string.guest_return_host);
        binding.btnMenuMySpaces.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(this, HostDashboardActivity.class)
                                        .addFlags(
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP)));
        binding.btnMenuHostApply.setOnClickListener(
                v -> startActivity(new Intent(this, HostApplyActivity.class)));
        binding.hostManagementMenu.setVisibility(isHost ? View.VISIBLE : View.GONE);
        binding.btnMenuHostApply.setVisibility(isHost ? View.GONE : View.VISIBLE);
        binding.hostSectionTitle.setText(
                isHost ? R.string.profile_host_management : R.string.profile_host_join);

        binding.profileUsername.setText(
                getString(
                        R.string.profile_username_label,
                        profile.username == null ? "" : profile.username));
        GuestNavigation.attach(this, R.id.nav_profile);
        // Lối vào báo cáo đã gửi, dùng chung màn hình phản hồi với khu vực Host.
        com.google.android.material.button.MaterialButton reports =
                new com.google.android.material.button.MaterialButton(this);
        reports.setText(R.string.reports_sent);
        ((android.view.ViewGroup) binding.btnNotices.getParent()).addView(reports);
        reports.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(
                                        this,
                                        vn.edu.ueh.phuclu.broomsharing.ui.report
                                                .ReportCenterActivity.class)));
    }
}
