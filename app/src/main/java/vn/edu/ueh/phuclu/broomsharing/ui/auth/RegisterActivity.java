package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.utils.AuthValidation;

public class RegisterActivity extends BaseAuthActivity {
    private TextInputEditText nameInput, usernameInput, emailInput, passwordInput, confirmInput;
    private TextInputLayout nameLayout, usernameLayout, emailLayout, passwordLayout, confirmLayout;

    @Override protected int authLayout() { return R.layout.activity_register; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        nameInput = findViewById(R.id.nameInput);
        usernameInput = findViewById(R.id.usernameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmInput = findViewById(R.id.confirmPasswordInput);
        nameLayout = findViewById(R.id.nameLayout);
        usernameLayout = findViewById(R.id.usernameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmLayout = findViewById(R.id.confirmPasswordLayout);
        usernameLayout.setHelperText(getString(R.string.auth_username_hint));
        findViewById(R.id.submitButton).setOnClickListener(v -> submit());
        findViewById(R.id.loginButton).setOnClickListener(v -> finish());
        confirmInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            submit();
            return true;
        });
    }

    private void submit() {
        if (model.isBusy()) return;
        for (TextInputLayout layout : new TextInputLayout[]{nameLayout, usernameLayout, emailLayout, passwordLayout, confirmLayout}) {
            layout.setError(null);
        }
        String name = text(nameInput).trim();
        String username = text(usernameInput).trim();
        String email = text(emailInput).trim();
        String password = text(passwordInput);
        if (name.isEmpty()) {
            invalid(nameLayout, nameInput, R.string.auth_name_required);
        } else if (name.length() > 100) {
            invalid(nameLayout, nameInput, R.string.auth_name_too_long);
        } else if (!AuthValidation.isUsernameValid(username)) {
            invalid(usernameLayout, usernameInput, R.string.auth_username_invalid);
        } else if (email.isEmpty()) {
            invalid(emailLayout, emailInput, R.string.auth_email_required);
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            invalid(emailLayout, emailInput, R.string.auth_email_invalid);
        } else if (!AuthValidation.isPasswordLongEnough(password)) {
            invalid(passwordLayout, passwordInput, R.string.auth_password_short);
        } else if (!AuthValidation.passwordsMatch(password, text(confirmInput))) {
            invalid(confirmLayout, confirmInput, R.string.auth_password_mismatch);
        } else {
            model.register(name, username, email, password);
        }
    }

    private void invalid(TextInputLayout layout, TextInputEditText input, int error) {
        layout.setError(getString(error));
        input.requestFocus();
    }
}
