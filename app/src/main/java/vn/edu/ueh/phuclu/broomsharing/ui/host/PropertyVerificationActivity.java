package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.PropertyVerificationAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityPropertyVerificationBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;

import java.util.*;

// Form/list lấy từ nhánh Hoàng, nối ViewModel/contract chung và giữ giới hạn tệp rõ ràng.
public class PropertyVerificationActivity extends SessionActivity {
    private ActivityPropertyVerificationBinding b;
    private PropertyVerificationViewModel model;
    private HostViewModel assets;
    private final List<Room> rooms = new ArrayList<>();
    private final List<Building> buildings = new ArrayList<>();
    private final List<String> assetKeys = new ArrayList<>();
    private Bundle restored;
    private final ActivityResultLauncher<String[]> picker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenMultipleDocuments(),
                    selected -> {
                        if (model == null || model.busy() || selected.isEmpty()) return;
                        if (selected.size() > 6) {
                            b.tvEvidenceFileName.setText("Tối đa 6 tệp. Vui lòng chọn lại.");
                            return;
                        }
                        for (Uri uri : selected)
                            try {
                                getContentResolver()
                                        .takePersistableUriPermission(
                                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException ignored) {
                            }
                        model.selectEvidence(selected);
                        renderEvidence();
                    });

    @Override
    protected void onCreate(Bundle state) {
        restored = state;
        super.onCreate(state);
    }

    @Override
    protected int pageLayout() {
        return R.layout.activity_property_verification;
    }

    @Override
    protected boolean requiresHost() {
        return true;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        b = ActivityPropertyVerificationBinding.bind(findViewById(R.id.workspaceRoot));
        ManagementNavigation.applyInsets(this);
        model = new ViewModelProvider(this).get(PropertyVerificationViewModel.class);
        if (restored != null && model.action.getValue() == null) {
            model.description = restored.getString("verification.description", model.description);
            model.draftId = restored.getString("verification.draft", model.draftId);
            model.selectedAssetKey =
                    restored.getString("verification.asset", model.selectedAssetKey);
            model.photos = restored.getBoolean("verification.photos", model.photos);
            ArrayList<Uri> chosen = restored.getParcelableArrayList("verification.files");
            if (chosen != null && model.attachments.isEmpty()) model.attachments.addAll(chosen);
            ArrayList<String> uploaded = restored.getStringArrayList("verification.uploaded");
            if (uploaded != null && model.uploadedRefs.isEmpty())
                model.uploadedRefs.addAll(uploaded);
            model.uploadOwner = restored.getString("verification.uploadOwner", model.uploadOwner);
        }
        assets = new ViewModelProvider(this).get(HostViewModel.class);
        b.btnBack.setOnClickListener(v -> finish());
        b.btnRefresh.setOnClickListener(
                v -> {
                    assets.loadRooms();
                    assets.loadBuildings();
                    model.watch();
                });
        b.verificationTabs.addTab(b.verificationTabs.newTab().setText("Gửi yêu cầu"));
        b.verificationTabs.addTab(b.verificationTabs.newTab().setText("Hồ sơ đã gửi"));
        b.verificationTabs.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {
                    public void onTabSelected(TabLayout.Tab tab) {
                        b.scrollForm.setVisibility(
                                tab.getPosition() == 0 ? View.VISIBLE : View.GONE);
                        b.layoutList.setVisibility(
                                tab.getPosition() == 0 ? View.GONE : View.VISIBLE);
                    }

                    public void onTabUnselected(TabLayout.Tab tab) {}

                    public void onTabReselected(TabLayout.Tab tab) {}
                });
        if (restored != null)
            b.verificationTabs.selectTab(
                    b.verificationTabs.getTabAt(restored.getInt("verification.tab", 0)));
        b.btnPickEvidence.setVisibility(View.VISIBLE);
        b.btnPickEvidence.setText("Chọn ảnh/PDF");
        b.btnPickEvidence.setOnClickListener(
                v ->
                        picker.launch(
                                new String[] {
                                    "image/jpeg", "image/png", "image/webp", "application/pdf"
                                }));
        b.btnPickEvidence.setOnLongClickListener(
                v -> {
                    model.selectEvidence(Collections.emptyList());
                    renderEvidence();
                    return true;
                });
        renderEvidence();
        b.etDescription.setText(model.description);
        b.etDescription.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(2000)});
        b.rbPhotos.setChecked(model.photos);
        b.rbLocation.setChecked(!model.photos);
        b.spinnerSpaces.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                        if (position < assetKeys.size())
                            model.selectedAssetKey = assetKeys.get(position);
                    }

                    public void onNothingSelected(AdapterView<?> p) {}
                });
        PropertyVerificationAdapter adapter = new PropertyVerificationAdapter();
        b.rvVerifications.setLayoutManager(new LinearLayoutManager(this));
        b.rvVerifications.setAdapter(adapter);
        assets.rooms.observe(
                this,
                s -> {
                    rooms.clear();
                    if (s.data != null) rooms.addAll(s.data);
                    updateAssets();
                });
        assets.buildings.observe(
                this,
                s -> {
                    buildings.clear();
                    if (s.data != null) buildings.addAll(s.data);
                    updateAssets();
                });
        b.btnSubmitVerification.setOnClickListener(
                v -> {
                    int index = b.spinnerSpaces.getSelectedItemPosition();
                    if (index < 0 || index >= rooms.size() + buildings.size()) {
                        b.etDescription.setError("Chọn tài sản hợp lệ.");
                        return;
                    }
                    PropertyVerification item = new PropertyVerification();
                    item.verificationType = b.rbLocation.isChecked() ? "LOCATION" : "PHOTOS";
                    item.description = b.etDescription.getText().toString().trim();
                    if (item.description.length() < 20 || item.description.length() > 2000) {
                        b.etDescription.setError("Nhập từ 20 đến 2.000 ký tự.");
                        return;
                    }
                    if (index < rooms.size()) {
                        Room r = rooms.get(index);
                        item.roomId = r.getId();
                        item.buildingId = r.getBuildingId();
                    } else {
                        item.roomId = "";
                        item.buildingId = buildings.get(index - rooms.size()).getId();
                    }
                    model.submit(item);
                });
        model.rows.observe(
                this,
                s -> {
                    b.progressBar.setVisibility(
                            s.loading || model.busy() ? View.VISIBLE : View.GONE);
                    adapter.setData(s.data);
                    b.tvEmptyVerifications.setText(
                            s.error != null
                                    ? s.error
                                    : "Chưa có hồ sơ. Hiển thị tối đa 100 hồ sơ của bạn.");
                    b.tvEmptyVerifications.setVisibility(
                            s.data == null || s.data.isEmpty() ? View.VISIBLE : View.GONE);
                });
        model.action.observe(
                this,
                s -> {
                    if (s == null) return;
                    b.progressBar.setVisibility(
                            s.loading
                                            || model.rows.getValue() != null
                                                    && model.rows.getValue().loading
                                    ? View.VISIBLE
                                    : View.GONE);
                    b.btnSubmitVerification.setText(
                            s.loading ? "Đang gửi hồ sơ…" : "Gửi hồ sơ xác minh cho Admin");
                    b.btnSubmitVerification.setEnabled(
                            !s.loading && rooms.size() + buildings.size() > 0);
                    b.btnPickEvidence.setEnabled(!s.loading);
                    b.spinnerSpaces.setEnabled(!s.loading);
                    b.rbLocation.setEnabled(!s.loading);
                    b.rbPhotos.setEnabled(!s.loading);
                    b.etDescription.setEnabled(!s.loading);
                    if (!s.loading) {
                        if (s.error != null) b.etDescription.setError(s.error);
                        else if (s.data != null) {
                            b.etDescription.setText("");
                            renderEvidence();
                            Toast.makeText(
                                            this,
                                            "Đã ghi nhận hồ sơ. Xem kết quả trong danh sách.",
                                            Toast.LENGTH_LONG)
                                    .show();
                            b.verificationTabs.selectTab(b.verificationTabs.getTabAt(1));
                            model.action.setValue(null);
                        }
                    }
                });
        assets.loadRooms();
        assets.loadBuildings();
        model.watch();
    }

    private void updateAssets() {
        List<String> labels = new ArrayList<>();
        String selectedKey = model.selectedAssetKey;
        assetKeys.clear();
        for (Room r : rooms) {
            labels.add("Phòng: " + r.getRoomName());
            assetKeys.add("room:" + r.getId());
        }
        for (Building building : buildings) {
            labels.add("Tòa nhà: " + building.getName());
            assetKeys.add("building:" + building.getId());
        }
        b.spinnerSpaces.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        if (!labels.isEmpty())
            b.spinnerSpaces.setSelection(Math.max(0, assetKeys.indexOf(selectedKey)));
        b.btnSubmitVerification.setEnabled(
                !labels.isEmpty()
                        && (model.action.getValue() == null || !model.action.getValue().loading));
    }

    private void renderEvidence() {
        b.tvEvidenceFileName.setText(
                model.attachments.isEmpty()
                        ? "Chưa chọn tệp (tối đa 6, mỗi tệp 5 MiB)."
                        : "Đã chọn "
                                + model.attachments.size()
                                + "/6 tệp. Nhấn giữ nút chọn để bỏ tệp.");
        b.ivEvidencePreview.setVisibility(View.GONE);
        if (!model.attachments.isEmpty()) {
            Uri first = model.attachments.get(0);
            if (!"application/pdf".equals(getContentResolver().getType(first))) {
                b.ivEvidencePreview.setVisibility(View.VISIBLE);
                Glide.with(this).load(first).into(b.ivEvidencePreview);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (b != null)
            state.putInt(
                    "verification.tab", Math.max(0, b.verificationTabs.getSelectedTabPosition()));
        if (model != null) {
            model.description = b.etDescription.getText().toString();
            model.selectedAsset = b.spinnerSpaces.getSelectedItemPosition();
            model.photos = b.rbPhotos.isChecked();
        }
        if (model != null) {
            state.putString("verification.description", model.description);
            state.putString("verification.draft", model.draftId);
            state.putString("verification.asset", model.selectedAssetKey);
            state.putBoolean("verification.photos", model.photos);
            state.putParcelableArrayList("verification.files", new ArrayList<>(model.attachments));
            state.putStringArrayList("verification.uploaded", new ArrayList<>(model.uploadedRefs));
            state.putString("verification.uploadOwner", model.uploadOwner);
        }
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (model != null) model.watch();
    }

    @Override
    protected void onStop() {
        if (model != null) model.stop();
        super.onStop();
    }
}
