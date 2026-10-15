package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.view.ViewGroup;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;

public class MainActivity extends SessionActivity {
    @Override protected int pageLayout() { return R.layout.activity_main; }
    @Override protected boolean isGuestHome() { return true; }

    @Override protected void onProfileReady(UserProfile profile) {
        ViewGroup bottomNav = findViewById(R.id.bottomNav);
        bottomNav.getChildAt(bottomNav.getChildCount() - 1).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
    }
}
