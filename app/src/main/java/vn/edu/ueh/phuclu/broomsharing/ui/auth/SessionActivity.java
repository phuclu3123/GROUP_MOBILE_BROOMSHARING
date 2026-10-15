package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import vn.edu.ueh.phuclu.broomsharing.AdminDashboardActivity;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.repository.AuthError;
import vn.edu.ueh.phuclu.broomsharing.repository.AuthRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.FirebaseAuthRepository;

/** Client navigation guard. Database enforcement is a separate P06 milestone. */
public abstract class SessionActivity extends AppCompatActivity {
    private AuthRepository repository;
    private boolean checking;

    protected abstract int pageLayout();
    protected void onProfileReady(UserProfile profile) { }
    protected boolean requiresAdmin() { return false; }
    protected boolean isGuestHome() { return false; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new FirebaseAuthRepository(getApplicationContext());
        setContentView(R.layout.activity_session_check);
        findViewById(R.id.retryButton).setOnClickListener(v -> checkSession());
        findViewById(R.id.signOutButton).setOnClickListener(v -> signOut());
        checkSession();
    }

    private void checkSession() {
        if (checking) return;
        if (!repository.hasSession()) {
            toLogin(0);
            return;
        }
        checking = true;
        findViewById(R.id.authProgress).setVisibility(View.VISIBLE);
        findViewById(R.id.retryButton).setVisibility(View.GONE);
        findViewById(R.id.signOutButton).setEnabled(false);
        ((TextView) findViewById(R.id.authMessage)).setText(R.string.auth_busy);
        repository.loadSession(new AuthRepository.Callback() {
            @Override public void onSuccess(UserProfile profile) {
                if (isFinishing() || isDestroyed()) return;
                checking = false;
                if (requiresAdmin() && profile.getUserRole() != UserRole.ADMIN) {
                    toLogin(R.string.auth_role_invalid);
                    return;
                }
                if (isGuestHome() && profile.getUserRole() == UserRole.ADMIN) {
                    Intent admin = new Intent(SessionActivity.this, AdminDashboardActivity.class);
                    admin.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(admin);
                    finish();
                    return;
                }
                setContentView(pageLayout());
                onProfileReady(profile);
            }

            @Override public void onError(AuthError error) {
                if (isFinishing() || isDestroyed()) return;
                checking = false;
                findViewById(R.id.authProgress).setVisibility(View.GONE);
                findViewById(R.id.signOutButton).setEnabled(true);
                ((TextView) findViewById(R.id.authMessage)).setText(AuthMessages.resource(error));
                findViewById(R.id.retryButton).setVisibility(View.VISIBLE);
            }
        });
    }

    protected void signOut() {
        repository.logout();
        toLogin(0);
    }

    private void toLogin(int notice) {
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (notice != 0) {
            repository.logout();
            login.putExtra("auth_notice", notice);
        }
        startActivity(login);
        finish();
    }
}
