package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.GeoPoint;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityAddBuildingBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.util.Locale;

public class AddBuildingActivity extends SessionActivity {
    private ActivityAddBuildingBinding binding;
    private HostViewModel model;
    private Bundle restored;
    private final ActivityResultLauncher<String[]> picker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri == null || model == null || model.busy()) return;
                        try {
                            getContentResolver()
                                    .takePersistableUriPermission(
                                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) {
                        }
                        model.photos.clear();
                        model.photos.add(uri);
                        showCover(uri);
                    });

    @Override
    protected int pageLayout() {
        return R.layout.activity_add_building;
    }

    @Override
    protected boolean requiresHost() {
        return true;
    }

    @Override
    protected void onCreate(Bundle state) {
        restored = state;
        super.onCreate(state);
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        binding =
                ActivityAddBuildingBinding.bind(
                        ((android.view.ViewGroup) findViewById(android.R.id.content))
                                .getChildAt(0));
        model = new ViewModelProvider(this).get(HostViewModel.class);
        if (model.editingBuilding == null) {
            model.editingBuilding = (Building) getIntent().getSerializableExtra("EXTRA_BUILDING");
            if (model.editingBuilding == null) model.editingBuilding = new Building();
        }
        Building b = model.editingBuilding;
        binding.etBuildingName.setText(b.getName());
        binding.etAddress.setText(b.getAddress());
        binding.etDistrict.setText(b.getDistrict());
        binding.etContactPhone.setText(b.getContactPhone());
        binding.etContactName.setText(
                b.getContactName() == null ? profile.name : b.getContactName());
        binding.etGuide.setText(b.getDirectionGuide());
        if (b.getLocation() != null) {
            binding.etLat.setText(String.format(Locale.US, "%.6f", b.getLatitude()));
            binding.etLng.setText(String.format(Locale.US, "%.6f", b.getLongitude()));
        }
        if (b.getCoverUrl() != null) showCover(Uri.parse(b.getCoverUrl()));
        if (!model.photos.isEmpty()) showCover(model.photos.get(0));
        binding.btnBack.setOnClickListener(
                v -> {
                    if (!model.busy()) finish();
                });
        binding.btnPickCover.setOnClickListener(
                v -> picker.launch(new String[] {"image/jpeg", "image/png", "image/webp"}));
        binding.btnSave.setOnClickListener(v -> save());
        if (restored != null && restored.containsKey("form"))
            binding.getRoot().restoreHierarchyState(restored.getSparseParcelableArray("form"));
        model.saved.observe(
                this,
                state -> {
                    if (state == null) return;
                    UiForms.enabled(binding.getRoot(), !state.loading);
                    binding.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
                    if (state.error != null) binding.tvFeedback.setText(state.error);
                    if (state.data != null) {
                        model.consumeSave();
                        Toast.makeText(this, "Đã lưu tòa nhà.", Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    }
                });
    }

    private void showCover(Uri uri) {
        binding.ivCover.setVisibility(View.VISIBLE);
        Glide.with(this).load(uri).centerCrop().into(binding.ivCover);
    }

    private void save() {
        if (model.busy()) return;
        Building b = model.editingBuilding;
        b.setName(UiForms.text(binding.etBuildingName));
        b.setAddress(UiForms.text(binding.etAddress));
        b.setDistrict(UiForms.text(binding.etDistrict));
        b.setContactPhone(UiForms.text(binding.etContactPhone));
        b.setContactName(UiForms.text(binding.etContactName));
        b.setDirectionGuide(UiForms.text(binding.etGuide));
        String lat = UiForms.text(binding.etLat), lng = UiForms.text(binding.etLng);
        if (lat.isEmpty() != lng.isEmpty()) {
            binding.tvFeedback.setText("Nhập cả vĩ độ và kinh độ hoặc để trống cả hai.");
            return;
        }
        try {
            b.setLatitude(0);
            b.setLongitude(0);
            b.setLocation(
                    lat.isEmpty()
                            ? null
                            : new GeoPoint(Double.parseDouble(lat), Double.parseDouble(lng)));
        } catch (IllegalArgumentException e) {
            binding.tvFeedback.setText("Vĩ độ từ -90 đến 90; kinh độ từ -180 đến 180.");
            return;
        }
        String error = HostValidation.building(b);
        if (error != null) {
            binding.tvFeedback.setText(error);
            return;
        }
        binding.tvFeedback.setText("");
        model.saveBuilding(b, model.photos.isEmpty() ? null : model.photos.get(0));
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (binding != null) {
            android.util.SparseArray<android.os.Parcelable> form = new android.util.SparseArray<>();
            binding.getRoot().saveHierarchyState(form);
            state.putSparseParcelableArray("form", form);
        }
        super.onSaveInstanceState(state);
    }
}
