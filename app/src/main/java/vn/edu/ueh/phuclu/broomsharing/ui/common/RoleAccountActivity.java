package vn.edu.ueh.phuclu.broomsharing.ui.common;

import android.content.Intent;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import vn.edu.ueh.phuclu.broomsharing.MainActivity;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityRoleAccountBinding;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;

/** Dedicated Host/Admin account view; the Guest profile stays in the renter area. */
public class RoleAccountActivity extends SessionActivity {
    @Override protected int pageLayout() { return R.layout.activity_role_account; }

    @Override protected void onProfileReady(UserProfile profile) {
        UserRole role = profile.getUserRole();
        if (role != UserRole.HOST && role != UserRole.ADMIN) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        ActivityRoleAccountBinding b = ActivityRoleAccountBinding.bind(findViewById(R.id.workspaceRoot));
        ManagementNavigation.applyInsets(this);
        b.roleLabel.setText(role == UserRole.ADMIN ? R.string.workspace_role_admin : R.string.workspace_role_host);
        b.accountName.setText(profile.name);
        b.accountEmail.setText(profile.email);
        b.accountUsername.setText(getString(R.string.profile_username_label, profile.username == null ? "" : profile.username));
        b.accountPhone.setVisibility(profile.phone == null || profile.phone.isEmpty() ? View.GONE : View.VISIBLE);
        b.accountPhone.setText(profile.phoneVerified ? profile.phone : getString(R.string.auth_phone_unverified, profile.phone == null ? "" : profile.phone));
        b.accountNote.setText(role == UserRole.ADMIN ? R.string.workspace_admin_account_note : R.string.workspace_renter_hint);
        b.switchToRenter.setVisibility(role == UserRole.HOST ? View.VISIBLE : View.GONE);
        b.switchToRenter.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)));
        b.accountSignOut.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.workspace_signout_title).setMessage(R.string.workspace_signout_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.workspace_signout, (dialog, which) -> signOut()).show());
        ManagementNavigation.attach(this, role, role == UserRole.ADMIN ? R.id.admin_account : R.id.host_account, null);
    }
}
