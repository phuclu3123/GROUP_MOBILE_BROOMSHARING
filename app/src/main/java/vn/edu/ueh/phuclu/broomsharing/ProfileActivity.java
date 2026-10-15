package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.widget.TextView;
import android.view.ViewGroup;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;

public class ProfileActivity extends SessionActivity {
    @Override protected int pageLayout() { return R.layout.activity_profile; }

    @Override protected void onProfileReady(UserProfile profile) {
        ((TextView) findViewById(R.id.profileName)).setText(profile.name);
        ((TextView) findViewById(R.id.profileSubtitle)).setText(profile.email);
        ((TextView) findViewById(R.id.profileInitial)).setText(
                profile.name == null || profile.name.isEmpty() ? "B" : profile.name.substring(0, 1));
        ((TextView) findViewById(R.id.profileBadge)).setText(profile.getUserRole() == UserRole.HOST
                ? R.string.auth_role_host : R.string.auth_role_guest);
        findViewById(R.id.profileSignOut).setOnClickListener(v -> signOut());
        ViewGroup bottomNav = findViewById(R.id.bottomNav);
        bottomNav.getChildAt(0).setOnClickListener(v -> {
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            finish();
        });
    }
}
