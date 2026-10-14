package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityAddBuildingBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.repository.HostRepository;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.util.Locale;

public class AddBuildingActivity extends AppCompatActivity {

    private ActivityAddBuildingBinding binding;
    private HostRepository repository;
    private Building editingBuilding = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddBuildingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new HostRepository();

        if (getIntent() != null && getIntent().hasExtra("EXTRA_BUILDING")) {
            editingBuilding = (Building) getIntent().getSerializableExtra("EXTRA_BUILDING");
        }

        setupTopBar();
        setupListeners();

        if (editingBuilding != null) {
            populateBuildingData();
        }
    }

    private void setupTopBar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void populateBuildingData() {
        if (editingBuilding == null) return;
        binding.etBuildingName.setText(editingBuilding.getName());
        binding.etAddress.setText(editingBuilding.getAddress());
        binding.etDistrict.setText(editingBuilding.getDistrict());
        binding.etContactPhone.setText(editingBuilding.getContactPhone());
        binding.etGuide.setText(editingBuilding.getDirectionGuide());
        if (editingBuilding.getLatitude() != 0) {
            binding.etLat.setText(String.format(Locale.US, "%.6f", editingBuilding.getLatitude()));
        }
        if (editingBuilding.getLongitude() != 0) {
            binding.etLng.setText(String.format(Locale.US, "%.6f", editingBuilding.getLongitude()));
        }
    }

    private void setupListeners() {
        binding.btnSave.setOnClickListener(v -> saveBuilding());
    }

    private void saveBuilding() {
        String name = binding.etBuildingName.getText().toString().trim();
        String address = binding.etAddress.getText().toString().trim();
        String district = binding.etDistrict.getText().toString().trim();
        String phone = binding.etContactPhone.getText().toString().trim();
        String guide = binding.etGuide.getText().toString().trim();
        String latStr = binding.etLat.getText().toString().trim();
        String lngStr = binding.etLng.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            binding.etBuildingName.setError("Vui lòng nhập tên tòa nhà");
            binding.etBuildingName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(address)) {
            binding.etAddress.setError("Vui lòng nhập địa chỉ");
            binding.etAddress.requestFocus();
            return;
        }

        double latitude = 10.7769;
        double longitude = 106.7008;
        try {
            if (!TextUtils.isEmpty(latStr)) latitude = Double.parseDouble(latStr);
            if (!TextUtils.isEmpty(lngStr)) longitude = Double.parseDouble(lngStr);
        } catch (NumberFormatException ignored) {}

        String currentUid = FirebaseHelper.getCurrentHostUid(this);

        Building building = editingBuilding != null ? editingBuilding : new Building();
        building.setOwnerUid(currentUid);
        building.setName(name);
        building.setAddress(address);
        building.setDistrict(district);
        building.setContactPhone(phone);
        building.setLatitude(latitude);
        building.setLongitude(longitude);
        building.setDirectionGuide(guide);

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSave.setEnabled(false);

        repository.addBuilding(building, new HostRepository.Callback<String>() {
            @Override
            public void onSuccess(String result) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSave.setEnabled(true);
                Toast.makeText(AddBuildingActivity.this, "Lưu tòa nhà và ghim vị trí thành công!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(Exception e) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSave.setEnabled(true);
                Toast.makeText(AddBuildingActivity.this, "Lỗi khi lưu tòa nhà: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
