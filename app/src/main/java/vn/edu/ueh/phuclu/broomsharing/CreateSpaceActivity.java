package vn.edu.ueh.phuclu.broomsharing;

import android.app.TimePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import vn.edu.ueh.phuclu.broomsharing.adapter.SelectedImageAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityCreateSpaceBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.repository.HostRepository;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CreateSpaceActivity extends AppCompatActivity {

    private ActivityCreateSpaceBinding binding;
    private HostRepository repository;

    private String selectedType = "Bàn làm việc";
    private final List<Uri> selectedPhotoUris = new ArrayList<>();
    private SelectedImageAdapter photoAdapter;
    private ActivityResultLauncher<String> photoPickerLauncher;

    private final List<Building> userBuildings = new ArrayList<>();
    private ArrayAdapter<String> buildingSpinnerAdapter;
    private final List<String> buildingNames = new ArrayList<>();

    private Room editingRoom = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateSpaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new HostRepository(this);

        if (getIntent() != null && getIntent().hasExtra("EXTRA_ROOM")) {
            editingRoom = (Room) getIntent().getSerializableExtra("EXTRA_ROOM");
        }

        initPhotoPicker();
        setupTopBar();
        setupBuildingSpinner();
        setupCategoryChips();
        setupTimePickers();
        setupPhotoRecycler();
        setupActionButtons();

        if (editingRoom != null) {
            populateEditingRoomData();
        }
    }

    private void setupTopBar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void populateEditingRoomData() {
        if (editingRoom == null) return;

        if (Room.STATUS_DRAFT.equals(editingRoom.getApprovalStatus())) {
            binding.tvTitle.setText("Chỉnh sửa bản nháp");
            binding.tvDraftBadge.setText("BẢN NHÁP");
        } else if (Room.STATUS_PENDING_REVIEW.equals(editingRoom.getApprovalStatus())) {
            binding.tvTitle.setText("Không gian đang chờ duyệt");
            binding.tvDraftBadge.setText("CHỜ DUYỆT");
        } else {
            binding.tvTitle.setText("Chi tiết không gian");
            binding.tvDraftBadge.setText(editingRoom.getApprovalStatus());
        }

        binding.etSpaceName.setText(editingRoom.getRoomName());
        binding.etAddress.setText(editingRoom.getAddress());
        if (editingRoom.getArea() > 0) binding.etArea.setText(String.valueOf(editingRoom.getArea()));
        if (editingRoom.getCapacity() > 0) binding.etCapacity.setText(String.valueOf(editingRoom.getCapacity()));
        if (editingRoom.getPricePerHour() > 0) binding.etPriceHour.setText(String.format(Locale.getDefault(), "%.0f", editingRoom.getPricePerHour()));
        if (editingRoom.getPricePerDay() > 0) binding.etPriceDay.setText(String.format(Locale.getDefault(), "%.0f", editingRoom.getPricePerDay()));
        if (editingRoom.getDeposit() > 0) binding.etDeposit.setText(String.format(Locale.getDefault(), "%.0f", editingRoom.getDeposit()));
        binding.etDescription.setText(editingRoom.getRules());

        if (editingRoom.getOpenTime() != null && !editingRoom.getOpenTime().isEmpty()) {
            binding.tvOpenTime.setText("🕒 " + editingRoom.getOpenTime() + " (Mở)");
        }
        if (editingRoom.getCloseTime() != null && !editingRoom.getCloseTime().isEmpty()) {
            binding.tvCloseTime.setText("🕒 " + editingRoom.getCloseTime() + " (Đóng)");
        }

        if (editingRoom.getAmenities() != null) {
            binding.cbWifi.setChecked(editingRoom.getAmenities().contains("Wi-Fi"));
            binding.cbAirConditioner.setChecked(editingRoom.getAmenities().contains("Máy lạnh"));
            binding.cbProjector.setChecked(editingRoom.getAmenities().contains("Máy chiếu"));
            binding.cbParking.setChecked(editingRoom.getAmenities().contains("Bãi giữ xe"));
        }

        if (editingRoom.getRoomType() != null) {
            selectedType = editingRoom.getRoomType();
            selectCategoryChip(selectedType);
        }
    }

    private void selectCategoryChip(String type) {
        TextView[] chips = new TextView[]{
                binding.chipDesk,
                binding.chipPrivateRoom,
                binding.chipMeetingRoom,
                binding.chipFloor
        };

        for (TextView c : chips) {
            if (c.getText().toString().equalsIgnoreCase(type)) {
                c.setBackgroundResource(R.drawable.bg_chip_selected);
                c.setTextColor(ContextCompat.getColor(this, R.color.white));
            } else {
                c.setBackgroundResource(R.drawable.bg_chip);
                c.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            }
        }
    }

    private void setupBuildingSpinner() {
        String uid = FirebaseHelper.getCurrentHostUid(this);
        buildingNames.clear();
        buildingNames.add("— Không thuộc tòa nhà cụ thể —");
        buildingSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, buildingNames);
        binding.spBuilding.setAdapter(buildingSpinnerAdapter);

        repository.getBuildingsByOwner(uid, new HostRepository.Callback<List<Building>>() {
            @Override
            public void onSuccess(List<Building> list) {
                userBuildings.clear();
                userBuildings.addAll(list);
                int selectIdx = 0;
                for (int i = 0; i < list.size(); i++) {
                    Building b = list.get(i);
                    buildingNames.add(b.getName() + " (" + b.getAddress() + ")");
                    if (editingRoom != null && b.getId() != null && b.getId().equals(editingRoom.getBuildingId())) {
                        selectIdx = i + 1;
                    }
                }
                buildingSpinnerAdapter.notifyDataSetChanged();
                if (selectIdx > 0) {
                    binding.spBuilding.setSelection(selectIdx);
                }
            }

            @Override
            public void onError(Exception e) {
                // Không chặn UI nếu chưa có building
            }
        });

        binding.spBuilding.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && position - 1 < userBuildings.size()) {
                    Building b = userBuildings.get(position - 1);
                    if (TextUtils.isEmpty(binding.etAddress.getText())) {
                        binding.etAddress.setText(b.getAddress());
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupTimePickers() {
        binding.tvOpenTime.setOnClickListener(v -> showTimePickerDialog(true));
        binding.tvCloseTime.setOnClickListener(v -> showTimePickerDialog(false));
    }

    private void showTimePickerDialog(boolean isOpenTime) {
        int defaultHour = isOpenTime ? 8 : 22;
        int defaultMinute = 0;

        String currentText = isOpenTime ? binding.tvOpenTime.getText().toString() : binding.tvCloseTime.getText().toString();
        currentText = currentText.replaceAll("[^0-9:]", "").trim();
        if (currentText.contains(":")) {
            try {
                String[] parts = currentText.split(":");
                defaultHour = Integer.parseInt(parts[0].trim());
                defaultMinute = Integer.parseInt(parts[1].trim());
            } catch (Exception ignored) {}
        }

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                    if (isOpenTime) {
                        binding.tvOpenTime.setText("🕒 " + timeFormatted + " (Mở)");
                    } else {
                        binding.tvCloseTime.setText("🕒 " + timeFormatted + " (Đóng)");
                    }
                },
                defaultHour,
                defaultMinute,
                true
        );
        dialog.show();
    }

    private void setupCategoryChips() {
        TextView[] chips = new TextView[]{
                binding.chipDesk,
                binding.chipPrivateRoom,
                binding.chipMeetingRoom,
                binding.chipFloor
        };

        for (TextView chip : chips) {
            chip.setOnClickListener(v -> {
                for (TextView c : chips) {
                    c.setBackgroundResource(R.drawable.bg_chip);
                    c.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                }
                chip.setBackgroundResource(R.drawable.bg_chip_selected);
                chip.setTextColor(ContextCompat.getColor(this, R.color.white));
                selectedType = chip.getText().toString();
            });
        }
    }

    private void initPhotoPicker() {
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        for (Uri u : uris) {
                            if (selectedPhotoUris.size() < 6) {
                                selectedPhotoUris.add(u);
                            }
                        }
                        photoAdapter.notifyDataSetChanged();
                        binding.rvSpaceImages.setVisibility(View.VISIBLE);
                        binding.tvPhotoCount.setText("Đã chọn " + selectedPhotoUris.size() + " ảnh (nhấn để thêm)");
                    }
                }
        );
    }

    private void setupPhotoRecycler() {
        binding.rvSpaceImages.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        photoAdapter = new SelectedImageAdapter(selectedPhotoUris, position -> {
            selectedPhotoUris.remove(position);
            photoAdapter.notifyItemRemoved(position);
            if (selectedPhotoUris.isEmpty()) {
                binding.rvSpaceImages.setVisibility(View.GONE);
                binding.tvPhotoCount.setText("Thêm ảnh không gian");
            } else {
                binding.tvPhotoCount.setText("Đã chọn " + selectedPhotoUris.size() + " ảnh (nhấn để thêm)");
            }
        });
        binding.rvSpaceImages.setAdapter(photoAdapter);

        binding.btnPickPhotos.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));
    }

    private void setupActionButtons() {
        binding.btnSaveDraft.setOnClickListener(v -> processSaveSpace(Room.STATUS_DRAFT));
        binding.btnSubmitApproval.setOnClickListener(v -> processSaveSpace(Room.STATUS_PENDING_REVIEW));
    }

    private void processSaveSpace(String approvalStatus) {
        String name = binding.etSpaceName.getText().toString().trim();
        String address = binding.etAddress.getText().toString().trim();
        String areaStr = binding.etArea.getText().toString().trim();
        String capStr = binding.etCapacity.getText().toString().trim();
        String priceHourStr = binding.etPriceHour.getText().toString().trim();
        String priceDayStr = binding.etPriceDay.getText().toString().trim();
        String depositStr = binding.etDeposit.getText().toString().trim();
        String desc = binding.etDescription.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            binding.etSpaceName.setError("Vui lòng nhập tên không gian");
            binding.etSpaceName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(address)) {
            binding.etAddress.setError("Vui lòng nhập địa chỉ");
            binding.etAddress.requestFocus();
            return;
        }

        double area = 15;
        try { if (!TextUtils.isEmpty(areaStr)) area = Double.parseDouble(areaStr); } catch (Exception ignored) {}

        int capacity = 1;
        try { if (!TextUtils.isEmpty(capStr)) capacity = Integer.parseInt(capStr); } catch (Exception ignored) {}

        double priceHour = 0;
        try { if (!TextUtils.isEmpty(priceHourStr)) priceHour = Double.parseDouble(priceHourStr.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}

        double priceDay = 0;
        try { if (!TextUtils.isEmpty(priceDayStr)) priceDay = Double.parseDouble(priceDayStr.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}

        double deposit = 0;
        try { if (!TextUtils.isEmpty(depositStr)) deposit = Double.parseDouble(depositStr.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}

        List<String> amenities = new ArrayList<>();
        if (binding.cbWifi.isChecked()) amenities.add("Wi-Fi");
        if (binding.cbAirConditioner.isChecked()) amenities.add("Máy lạnh");
        if (binding.cbProjector.isChecked()) amenities.add("Máy chiếu");
        if (binding.cbParking.isChecked()) amenities.add("Bãi giữ xe");

        String ownerUid = FirebaseHelper.getCurrentHostUid(this);

        String openTimeClean = binding.tvOpenTime.getText().toString().replaceAll("[^0-9:]", "").trim();
        String closeTimeClean = binding.tvCloseTime.getText().toString().replaceAll("[^0-9:]", "").trim();

        Room room = editingRoom != null ? editingRoom : new Room();
        room.setOwnerUid(ownerUid);
        room.setRoomName(name);
        room.setAddress(address);
        room.setRoomType(selectedType);
        room.setArea(area);
        room.setCapacity(capacity);
        room.setPricePerHour(priceHour);
        room.setPricePerDay(priceDay);
        room.setDeposit(deposit);
        room.setOpenTime(!openTimeClean.isEmpty() ? openTimeClean : "08:00");
        room.setCloseTime(!closeTimeClean.isEmpty() ? closeTimeClean : "22:00");
        room.setAmenities(amenities);
        room.setRules(desc);
        room.setApprovalStatus(approvalStatus);
        room.setActiveMode(priceHour > 0 ? Room.MODE_HOURLY : Room.MODE_FIXED);

        // Gắn tòa nhà (H04)
        int selectedBuildingIndex = binding.spBuilding.getSelectedItemPosition();
        if (selectedBuildingIndex > 0 && selectedBuildingIndex - 1 < userBuildings.size()) {
            Building b = userBuildings.get(selectedBuildingIndex - 1);
            room.setBuildingId(b.getId());
            room.setBuildingName(b.getName());
        }

        setLoading(true);

        // Upload ảnh lên Storage (H06)
        if (!selectedPhotoUris.isEmpty()) {
            repository.uploadRoomPhotos(selectedPhotoUris, room.getId(), new HostRepository.Callback<List<String>>() {
                @Override
                public void onSuccess(List<String> urls) {
                    room.setImageUrls(urls);
                    saveToFirestore(room, approvalStatus);
                }

                @Override
                public void onError(Exception e) {
                    setLoading(false);
                    Toast.makeText(CreateSpaceActivity.this, "Lỗi tải ảnh lên Storage: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            saveToFirestore(room, approvalStatus);
        }
    }

    private void saveToFirestore(Room room, String approvalStatus) {
        setLoading(true);
        repository.saveRoom(room, new HostRepository.Callback<String>() {
            @Override
            public void onSuccess(String roomId) {
                setLoading(false);
                String msg = Room.STATUS_PENDING_REVIEW.equals(approvalStatus) ?
                        "Không gian đã được gửi duyệt thành công lên Firestore!" :
                        "Đã lưu bản nháp thành công lên Firestore!";
                Toast.makeText(CreateSpaceActivity.this, msg, Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(Exception e) {
                setLoading(false);
                Toast.makeText(CreateSpaceActivity.this, "Lỗi Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSaveDraft.setEnabled(!isLoading);
        binding.btnSubmitApproval.setEnabled(!isLoading);
    }
}
