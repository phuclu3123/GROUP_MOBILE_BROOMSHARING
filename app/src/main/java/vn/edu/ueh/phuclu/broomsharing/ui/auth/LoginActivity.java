package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.utils.AuthValidation;

public class LoginActivity extends BaseAuthActivity {

    // Ô input nhập dữ Tài khoản và mật khẩu vào
    private TextInputEditText identifierInput;
    private TextInputEditText passwordInput;

    // Ô Container hỗ trợ nhãn lỗi và các tính năng hiển thị VD: Vui lòng nhập tên đăng nhập (red)
    private TextInputLayout identifierLayout;
    private TextInputLayout passwordLayout;

    // Trả về trang đăng nhập
    @Override
    protected int authLayout() {
        return R.layout.activity_login;
    }

    /* Ví dụ một người khác mở trang đăng nhập:
        Intent intent = new Intent(this, LoginActivity.class);

        intent.putExtra("auth_notice", R.string.auth_role_invalid);
        startActivity(intent);
        ---
        Thì lúc này thông báo của ta nhạn notice với auth_notice và giá trị mặc định 0;
        Sau đó notice = R.string.auth_role_invalid; nếu không có thì notice = 0;

        Cấu trúc tổng thể: putExtra(key, value) => getExtra(key, defaultValue);
     */
    @Override
    protected int initialMessage() {
        int notice = getIntent().getIntExtra("auth_notice", 0);
        return notice == R.string.auth_role_invalid ? notice : super.initialMessage();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Mapping với xml
        identifierInput = findViewById(R.id.identifierInput);
        passwordInput = findViewById(R.id.passwordInput);
        identifierLayout = findViewById(R.id.identifierLayout);
        passwordLayout = findViewById(R.id.passwordLayout);

        View submitButton = findViewById(R.id.submitButton);
        View registerButton = findViewById(R.id.registerButton);
        View forgotButton = findViewById(R.id.forgotButton);

        // Khi nhấn button Đăng nhập, gọi hàm xử lý đăng nhập
        submitButton.setOnClickListener(v -> submit());

        // Khi nhấn button Đăng ký, mở màn hình đăng ký
        registerButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        });

        // Khi nhấn button Quên mật khẩu, chuyển email hợp lệ sang màn hình kế tiếp
        forgotButton.setOnClickListener(v -> {
            String email = text(identifierInput).trim();
            Intent intent = new Intent(this, ForgotPasswordActivity.class);

            if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                intent.putExtra("reset_email", email);
            }
            startActivity(intent);
        });

        // Nhấn nút enter(done) trên bàn phím cũng thực hiện đăng nhập
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) {
                return false;
            }
            submit();
            return true;
        });
        model.restoreSessionOnce();
    }

    private void submit() {
        if (model.isBusy()) return;
        // Hiển thị thông báo
        message.setVisibility(View.GONE);
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
