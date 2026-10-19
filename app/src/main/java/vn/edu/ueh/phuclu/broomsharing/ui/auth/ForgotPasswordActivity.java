package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import vn.edu.ueh.phuclu.broomsharing.R;

/** Requests the real Firebase password reset through the shared ViewModel. */
public class ForgotPasswordActivity extends BaseAuthActivity {
    @Override protected int authLayout() { return R.layout.activity_forgot_password; }
    @Override protected int initialMessage() { return model.passwordResetSent() ? R.string.auth_reset_sent : R.string.auth_reset_help; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        findViewById(R.id.loginButton).setOnClickListener(v -> finish());
        findViewById(R.id.submitButton).setOnClickListener(v -> submit());
        TextInputEditText email = findViewById(R.id.emailInput);
        email.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            submit();
            return true;
        });
    }

    private void submit() {
        if (model.isBusy()) return;
        TextInputEditText email = findViewById(R.id.emailInput);
        TextInputLayout layout = findViewById(R.id.emailLayout);
        layout.setError(null);
        String value = text(email).trim();
        if (value.isEmpty()) {
            layout.setError(getString(R.string.auth_email_required));
            email.requestFocus();
        } else if (!Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            layout.setError(getString(R.string.auth_email_invalid));
            email.requestFocus();
        } else {
            model.resetPassword(value);
        }
    }
}
