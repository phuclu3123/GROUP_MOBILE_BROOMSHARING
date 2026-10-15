package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import vn.edu.ueh.phuclu.broomsharing.R;

/** UI only for this milestone; no reset email is sent. */
public class ForgotPasswordActivity extends BaseAuthActivity {
    @Override protected int authLayout() { return R.layout.activity_forgot_password; }
    @Override protected int initialMessage() { return R.string.auth_reset_later; }

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
        showMessage(R.string.auth_reset_later);
    }

    private void submit() {
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
            showMessage(R.string.auth_reset_later);
        }
    }
}
