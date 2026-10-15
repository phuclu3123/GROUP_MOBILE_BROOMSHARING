package vn.edu.ueh.phuclu.broomsharing;

import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;

public class AdminDashboardActivity extends SessionActivity {
    @Override protected int pageLayout() { return R.layout.activity_admin_dashboard; }
    @Override protected boolean requiresAdmin() { return true; }
    @Override protected void onProfileReady(UserProfile profile) {
        findViewById(R.id.adminSignOut).setOnClickListener(v -> signOut());
    }
}
