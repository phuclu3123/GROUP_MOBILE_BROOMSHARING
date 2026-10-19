package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.phuclu.broomsharing.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;

/**
 * Shared session screen observes AuthViewModel; no Repository or Firebase calls from Activities.
 */
public abstract class SessionActivity extends AppCompatActivity {
    private AuthViewModel model;
    private boolean shown;

    protected abstract int pageLayout();

    protected void onProfileReady(UserProfile profile) {}

    protected boolean requiresAdmin() {
        return false;
    }

    protected boolean requiresHost() {
        return false;
    }

    protected boolean isGuestHome() {
        return false;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(AuthViewModel.class);
        setContentView(R.layout.activity_session_check);
        findViewById(R.id.retryButton).setOnClickListener(v -> checkSession());
        if (!model.hasSession()) {
            toLogin(0);
            return;
        }
        model.reloadSession();
        model.getState()
                .observe(
                        this,
                        value -> {
                            if (shown || isFinishing() || isDestroyed()) return;
                            findViewById(R.id.authProgress)
                                    .setVisibility(value.loading ? View.VISIBLE : View.GONE);
                            findViewById(R.id.retryButton)
                                    .setVisibility(value.error == null ? View.GONE : View.VISIBLE);
                            ((TextView) findViewById(R.id.authMessage))
                                    .setText(
                                            value.error == null
                                                    ? R.string.auth_busy
                                                    : AuthMessages.resource(value.error));
                            if (value.profile != null) show(value.profile);
                        });
    }

    private void checkSession() {
        if (!model.hasSession()) toLogin(0);
        else model.reloadSession();
    }

    private void show(UserProfile profile) {
        if (requiresAdmin() && profile.getUserRole() != UserRole.ADMIN) {
            toLogin(R.string.auth_role_invalid);
            return;
        }
        if (requiresHost() && profile.getUserRole() != UserRole.HOST) {
            android.widget.Toast.makeText(
                            this,
                            "Chức năng này dành cho tài khoản Host đã được duyệt.",
                            android.widget.Toast.LENGTH_LONG)
                    .show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        if (isGuestHome() && profile.getUserRole() == UserRole.ADMIN) {
            startActivity(
                    new Intent(this, AdminDashboardActivity.class)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }
        shown = true;
        setContentView(pageLayout());
        onProfileReady(profile);
    }

    protected void signOut() {
        model.signOut();
        toLogin(0);
    }

    private void toLogin(int notice) {
        if (notice != 0) model.signOut();
        Intent intent =
                new Intent(this, LoginActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (notice != 0) intent.putExtra("auth_notice", notice);
        startActivity(intent);
        finish();
    }
}
