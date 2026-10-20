package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.PropertyVerificationAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityPropertyVerificationBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;

public class PropertyVerificationActivity extends SessionActivity {
    private ActivityPropertyVerificationBinding b;
    private HostViewModel viewModel;
    private PropertyVerificationAdapter adapter;

    private final List<Room> rooms = new ArrayList<>();
    private final List<Building> buildings = new ArrayList<>();
    private final List<String> spaceLabels = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    private Uri selectedEvidenceUri;

    private final ActivityResultLauncher<String> photoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedEvidenceUri = uri;
                    b.tvEvidenceFileName.setText(uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "Đã chọn 1 tệp");
                    b.ivEvidencePreview.setVisibility(View.VISIBLE);
                    b.ivEvidencePreview.setImageURI(uri);
                }
            });

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
        viewModel = new ViewModelProvider(this).get(HostViewModel.class);

        b.btnBack.setOnClickListener(v -> finish());
        b.btnRefresh.setOnClickListener(v -> refresh());

        setupTabs();
        setupSpinner();
        setupList();
        setupForm();
        observeViewModel();

        refresh();
    }

    private void setupTabs() {
        b.verificationTabs.addTab(b.verificationTabs.newTab().setText("Nộp hồ sơ mới"));
        b.verificationTabs.addTab(b.verificationTabs.newTab().setText("Hồ sơ đã gửi"));
        b.verificationTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean isForm = tab.getPosition() == 0;
                b.scrollForm.setVisibility(isForm ? View.VISIBLE : View.GONE);
                b.layoutList.setVisibility(!isForm ? View.VISIBLE : View.GONE);
                if (!isForm) viewModel.loadVerifications();
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupSpinner() {
        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spaceLabels);
        b.spinnerSpaces.setAdapter(spinnerAdapter);
    }

    private void setupList() {
        adapter = new PropertyVerificationAdapter();
        b.rvVerifications.setLayoutManager(new LinearLayoutManager(this));
        b.rvVerifications.setAdapter(adapter);
    }

    private void setupForm() {
        b.btnPickEvidence.setOnClickListener(v -> photoPicker.launch("image/*"));
        b.btnSubmitVerification.setOnClickListener(v -> submitForm());
    }

    private void submitForm() {
        int selectedIndex = b.spinnerSpaces.getSelectedItemPosition();
        if (selectedIndex < 0 || spaceLabels.isEmpty()) {
            Toast.makeText(this, "Bạn chưa có phòng hoặc tòa nhà nào để xác minh", Toast.LENGTH_SHORT).show();
            return;
        }

        String type = b.rbLocation.isChecked() ? "LOCATION" : "PHOTOS";
        String desc = b.etDescription.getText().toString().trim();
        if (desc.isEmpty()) {
            b.etDescription.setError("Vui lòng nhập mô tả bằng chứng / giấy tờ");
            b.etDescription.requestFocus();
            return;
        }

        PropertyVerification pv = new PropertyVerification();
        pv.verificationType = type;
        pv.description = desc;

        // Map to selected room or building
        if (selectedIndex < rooms.size()) {
            Room r = rooms.get(selectedIndex);
            pv.roomId = r.getId();
            pv.buildingId = r.getBuildingId() != null ? r.getBuildingId() : "";
            pv.roomName = r.getRoomName();
        } else {
            int buildingIdx = selectedIndex - rooms.size();
            if (buildingIdx < buildings.size()) {
                Building bg = buildings.get(buildingIdx);
                pv.buildingId = bg.getId();
                pv.buildingName = bg.getName();
            }
        }

        b.btnSubmitVerification.setEnabled(false);
        viewModel.submitVerification(pv, selectedEvidenceUri, this);
    }

    private void refresh() {
        viewModel.loadDashboard();
        viewModel.loadVerifications();
    }

    private void observeViewModel() {
        viewModel.rooms.observe(this, state -> {
            rooms.clear();
            if (state.data != null) rooms.addAll(state.data);
            updateSpaceLabels();
        });

        viewModel.buildings.observe(this, state -> {
            buildings.clear();
            if (state.data != null) buildings.addAll(state.data);
            updateSpaceLabels();
        });

        viewModel.verifications.observe(this, state -> {
            if (state == null) return;
            b.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            if (state.error != null) {
                Toast.makeText(this, "Lỗi tải hồ sơ: " + state.error, Toast.LENGTH_SHORT).show();
            } else if (state.data != null) {
                adapter.setData(state.data);
                b.tvEmptyVerifications.setVisibility(state.data.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.verificationSubmitted.observe(this, state -> {
            if (state == null) return;
            b.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            b.btnSubmitVerification.setEnabled(!state.loading);
            if (state.error != null) {
                Toast.makeText(this, "Nộp hồ sơ thất bại: " + state.error, Toast.LENGTH_LONG).show();
            } else if (state.data != null) {
                Toast.makeText(this, "Gửi hồ sơ xác minh thành công! Đang chờ Admin duyệt.", Toast.LENGTH_LONG).show();
                viewModel.verificationSubmitted.setValue(null);
                // Reset form
                b.etDescription.setText("");
                selectedEvidenceUri = null;
                b.tvEvidenceFileName.setText("Chưa chọn tệp");
                b.ivEvidencePreview.setVisibility(View.GONE);
                // Switch to list tab
                TabLayout.Tab listTab = b.verificationTabs.getTabAt(1);
                if (listTab != null) listTab.select();
            }
        });
    }

    private void updateSpaceLabels() {
        spaceLabels.clear();
        for (Room r : rooms) {
            spaceLabels.add("[Phòng] " + r.getRoomName() + " (ID: " + r.getId() + ")");
        }
        for (Building bg : buildings) {
            spaceLabels.add("[Tòa nhà] " + bg.getName() + " (ID: " + bg.getId() + ")");
        }
        if (spaceLabels.isEmpty()) {
            spaceLabels.add("Chưa có phòng hoặc tòa nhà nào");
        }
        spinnerAdapter.notifyDataSetChanged();
    }
}
