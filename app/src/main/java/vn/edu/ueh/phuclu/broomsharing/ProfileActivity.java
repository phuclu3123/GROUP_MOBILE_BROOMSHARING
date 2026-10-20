package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.view.View;

import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityProfileBinding;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.AddBuildingActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostApplyActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostDashboardActivity;

public class ProfileActivity extends SessionActivity {
    @Override
    protected int pageLayout() {
        return R.layout.activity_profile;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        if (profile.getUserRole() == UserRole.ADMIN) {
            startActivity(new Intent(this, vn.edu.ueh.phuclu.broomsharing.ui.common.RoleAccountActivity.class));
            finish();
            return;
        }
        ActivityProfileBinding binding =
                ActivityProfileBinding.bind(findViewById(R.id.profileRoot));
        binding.profileName.setText(profile.name);
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
        binding.profileBadge.setText(isHost ? R.string.guest_renter_mode : R.string.auth_role_guest);
        binding.profileSignOut.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.workspace_signout_title).setMessage(R.string.workspace_signout_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.workspace_signout, (dialog, which) -> signOut()).show());
        binding.btnFavorites.setOnClickListener(v -> startActivity(new Intent(this,FavoritesActivity.class)));
        binding.btnNotices.setOnClickListener(v -> startActivity(new Intent(this,BookingNoticesActivity.class)));

        binding.btnMenuMySpaces.setText(R.string.guest_return_host);
        binding.btnMenuMySpaces.setOnClickListener(
                v -> startActivity(new Intent(this, HostDashboardActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)));
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
        vn.edu.ueh.phuclu.broomsharing.ui.common.GuestNavigation.attach(this, R.id.nav_profile);
    }
}
