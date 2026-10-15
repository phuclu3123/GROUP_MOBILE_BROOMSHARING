package vn.edu.ueh.phuclu.broomsharing.ui.common;

import android.content.Intent;
import android.widget.*;

import com.google.android.material.appbar.MaterialToolbar;

import vn.edu.ueh.phuclu.broomsharing.*;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;

/** Consistent destination for modules whose business logic belongs to a later phase. */
public abstract class GuestSectionActivity extends SessionActivity {
    protected abstract int tab();

    protected abstract int title();

    protected abstract int heading();

    protected abstract int description();

    protected abstract int icon();

    @Override
    protected int pageLayout() {
        return R.layout.activity_guest_section;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        ((MaterialToolbar) findViewById(R.id.sectionToolbar)).setTitle(title());
        ((TextView) findViewById(R.id.sectionTitle)).setText(heading());
        ((TextView) findViewById(R.id.sectionBody)).setText(description());
        ((ImageView) findViewById(R.id.sectionIcon)).setImageResource(icon());
        findViewById(R.id.sectionExplore)
                .setOnClickListener(
                        v ->
                                startActivity(
                                        new Intent(this, MainActivity.class)
                                                .addFlags(
                                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                                | Intent
                                                                        .FLAG_ACTIVITY_SINGLE_TOP)));
        GuestNavigation.attach(this, tab());
    }
}
