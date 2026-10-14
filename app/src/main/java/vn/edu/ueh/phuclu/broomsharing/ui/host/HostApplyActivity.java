package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityHostApplyBinding;
import vn.edu.ueh.phuclu.broomsharing.model.HostApplication;
import vn.edu.ueh.phuclu.broomsharing.repository.HostRepository;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

public class HostApplyActivity extends AppCompatActivity {

    private ActivityHostApplyBinding binding;
    private HostRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHostApplyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new HostRepository();

        setupTopBar();
        setupListeners();
    }

    private void setupTopBar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void setupListeners() {
        binding.btnSubmit.setOnClickListener(v -> submitApplication());
    }

    private void submitApplication() {
        String fullName = binding.etFullName.getText().toString().trim();
        String phone = binding.etPhone.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String doc = binding.etDoc.getText().toString().trim();
        String orgType = binding.rbBusiness.isChecked() ? "Doanh nghiệp / Chuỗi" : "Cá nhân";

        if (TextUtils.isEmpty(fullName)) {
            binding.etFullName.setError("Vui lòng nhập họ và tên");
            binding.etFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            binding.etPhone.setError("Vui lòng nhập số điện thoại");
            binding.etPhone.requestFocus();
            return;
        }

        String currentUid = FirebaseHelper.getCurrentHostUid(this);

        HostApplication app = new HostApplication(currentUid, fullName, phone, email, orgType, doc);

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSubmit.setEnabled(false);

        repository.submitHostApplication(app, new HostRepository.Callback<String>() {
            @Override
            public void onSuccess(String result) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSubmit.setEnabled(true);
                Toast.makeText(HostApplyActivity.this, "Hồ sơ đã được gửi! Vui lòng chờ Admin xét duyệt.", Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(Exception e) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSubmit.setEnabled(true);
                Toast.makeText(HostApplyActivity.this, "Lỗi khi gửi hồ sơ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
