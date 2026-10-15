package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import vn.edu.ueh.phuclu.broomsharing.AdminDashboardActivity;
import vn.edu.ueh.phuclu.broomsharing.MainActivity;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;

public abstract class BaseAuthActivity extends AppCompatActivity {
    protected AuthViewModel model;
    protected TextView message;

    protected abstract int authLayout();
    protected int initialMessage() {
        return model.isConfigured() ? 0 : R.string.auth_setup_required;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(authLayout());
        model = new ViewModelProvider(this).get(AuthViewModel.class);
        message = findViewById(R.id.authMessage);
        View root = findViewById(R.id.authRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (model.isBusy()) return;
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
        model.getState().observe(this, state -> {
            setInputsEnabled((ViewGroup) root, !state.loading);
            findViewById(R.id.authProgress).setVisibility(state.loading ? View.VISIBLE : View.GONE);
            if (state.loading) {
                showMessage(R.string.auth_busy);
            } else if (state.error != null) {
                showMessage(AuthMessages.resource(state.error));
            } else {
                int notice = initialMessage();
                if (notice == 0) message.setVisibility(View.GONE);
                else showMessage(notice);
            }
            if (state.profile != null) {
                model.consumeSuccess();
                clearPasswords((ViewGroup) root);
                openHome(state.profile);
            }
        });
    }

    private void setInputsEnabled(ViewGroup group, boolean enabled) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof EditText || child instanceof MaterialButton) child.setEnabled(enabled);
            if (child instanceof ViewGroup) setInputsEnabled((ViewGroup) child, enabled);
        }
    }

    private void clearPasswords(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getId() == R.id.passwordInput || child.getId() == R.id.confirmPasswordInput) {
                ((EditText) child).setText("");
            }
            if (child instanceof ViewGroup) clearPasswords((ViewGroup) child);
        }
    }

    protected String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    protected void showMessage(int resource) {
        message.setText(resource);
        message.setVisibility(View.VISIBLE);
    }

    protected void openHome(UserProfile profile) {
        Class<?> target = profile.getUserRole() == UserRole.ADMIN
                ? AdminDashboardActivity.class : MainActivity.class;
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
