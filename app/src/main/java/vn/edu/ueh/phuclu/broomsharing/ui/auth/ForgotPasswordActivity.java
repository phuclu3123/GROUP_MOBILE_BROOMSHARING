package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import vn.edu.ueh.phuclu.broomsharing.R;

/** Requests the real Firebase password reset through the shared ViewModel. */
public class ForgotPasswordActivity extends BaseAuthActivity {
    // Tương tự như login và Register.
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { updateButton(); timer.postDelayed(this, 1000); }
    };
    @Override protected int authLayout() { return R.layout.activity_forgot_password; }
    @Override protected int initialMessage() { return model.passwordResetSent() ? R.string.auth_reset_sent : R.string.auth_reset_help; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        findViewById(R.id.loginButton).setOnClickListener(v -> finish());
        findViewById(R.id.submitButton).setOnClickListener(v -> submit());
        TextInputEditText email = findViewById(R.id.emailInput);
        if (state == null) email.setText(getIntent().getStringExtra("reset_email"));
        model.getState().observe(this, ignored -> updateButton());
        email.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            submit();
            return true;
        });
    }

    @Override protected void onStart() { super.onStart(); timer.post(tick); }
    @Override protected void onStop() { timer.removeCallbacks(tick); super.onStop(); }

    private void updateButton() {
        long wait = model.passwordResetWaitSeconds();
        MaterialButton button = findViewById(R.id.submitButton);
        button.setEnabled(!model.isBusy() && wait == 0);
        button.setText(wait > 0 ? getString(R.string.auth_reset_wait, wait)
                : getString(model.passwordResetSent() ? R.string.auth_reset_again : R.string.auth_reset));
    }

    private void submit() {
        if (model.isBusy() || model.passwordResetWaitSeconds() > 0) return;
        TextInputEditText email = findViewById(R.id.emailInput);
        TextInputLayout layout = findViewById(R.id.emailLayout);
        layout.setError(null);
        String value = text(email).trim();
        if (value.isEmpty()) {
            layout.setError(getString(R.string.auth_email_required));
            email.requestFocus();
        } else if (value.length() > 254 || !Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            layout.setError(getString(R.string.auth_email_invalid));
            email.requestFocus();
        } else {
            model.resetPassword(value);
        }
    }
}
