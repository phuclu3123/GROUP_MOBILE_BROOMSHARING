package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.utils.AuthValidation;

public class LoginActivity extends BaseAuthActivity {
    private TextInputEditText identifierInput;
    private TextInputEditText passwordInput;
    private TextInputLayout identifierLayout;
    private TextInputLayout passwordLayout;

    @Override
    protected int authLayout() {
        return R.layout.activity_login;
    }

    @Override
    protected int initialMessage() {
        int notice = getIntent().getIntExtra("auth_notice", 0);
        return notice == R.string.auth_role_invalid ? notice : super.initialMessage();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        identifierInput = findViewById(R.id.identifierInput);
        passwordInput = findViewById(R.id.passwordInput);
        identifierLayout = findViewById(R.id.identifierLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        findViewById(R.id.submitButton).setOnClickListener(v -> submit());
        findViewById(R.id.registerButton)
                .setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        findViewById(R.id.forgotButton)
                .setOnClickListener(
                        v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        findViewById(R.id.googleButton)
                .setOnClickListener(v -> showMessage(R.string.auth_google_later));
        findViewById(R.id.phoneButton)
                .setOnClickListener(v -> showMessage(R.string.auth_phone_later));
        passwordInput.setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (actionId != EditorInfo.IME_ACTION_DONE) return false;
                    submit();
                    return true;
                });
        model.restoreSessionOnce();
    }

    private void submit() {
        if (model.isBusy()) return;
        message.setVisibility(android.view.View.GONE);
        identifierLayout.setError(null);
        passwordLayout.setError(null);
        String identifier = text(identifierInput).trim();
        String password = text(passwordInput);
        if (identifier.isEmpty()) {
            identifierLayout.setError(getString(R.string.auth_identifier_required));
            identifierInput.requestFocus();
        } else if (identifier.contains("@")
                && !Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
            identifierLayout.setError(getString(R.string.auth_email_invalid));
            identifierInput.requestFocus();
        } else if (!identifier.contains("@") && !AuthValidation.isUsernameValid(identifier)) {
            identifierLayout.setError(getString(R.string.auth_username_invalid));
            identifierInput.requestFocus();
        } else if (password.isEmpty()) {
            passwordLayout.setError(getString(R.string.auth_password_required));
            passwordInput.requestFocus();
        } else {
            model.login(identifier, password);
        }
    }
}
