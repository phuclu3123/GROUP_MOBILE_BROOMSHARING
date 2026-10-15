package vn.edu.ueh.phuclu.broomsharing;

import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;

import vn.edu.ueh.phuclu.broomsharing.databinding.ActivitySpaceDetailBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.RoomViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

public class SpaceDetailActivity extends SessionActivity {
    public static final String ROOM_ID = "ROOM_ID";
    private int scrollY;
    private ActivitySpaceDetailBinding binding;

    @Override
    protected void onCreate(android.os.Bundle state) {
        if (state != null) scrollY = state.getInt("detail.scroll", 0);
        super.onCreate(state);
    }

    @Override
    protected void onSaveInstanceState(android.os.Bundle out) {
        out.putInt("detail.scroll", binding == null ? scrollY : binding.detailScroll.getScrollY());
        super.onSaveInstanceState(out);
    }

    @Override
    protected int pageLayout() {
        return R.layout.activity_space_detail;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        ActivitySpaceDetailBinding b =
                ActivitySpaceDetailBinding.bind(
                        ((android.view.ViewGroup) findViewById(android.R.id.content))
                                .getChildAt(0));
        b.gallery.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(
                        this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        RoomViewModel model = new ViewModelProvider(this).get(RoomViewModel.class);
        binding = b;
        String id = getIntent().getStringExtra(ROOM_ID);
        b.detailToolbar.setNavigationOnClickListener(v -> finish());
        b.btnRefresh.setOnClickListener(v -> model.load(id));
        model.detail()
                .observe(
                        this,
                        state -> {
                            b.btnRefresh.setEnabled(!state.loading);
                            b.details.setText("");
                            b.title.setText("");
                            com.bumptech.glide.Glide.with(this).clear(b.cover);
                            b.cover.setImageResource(R.drawable.bg_image_placeholder);
                            b.gallery.setAdapter(null);
                            b.tvFeedback.setText(
                                    state.loading
                                            ? "Đang tải…"
                                            : state.error == null ? "" : state.error);
                            if (state.data == null) return;
                            Room r = state.data.room;
                            Building building = state.data.building;
                            b.title.setText(r.getRoomName());
                            String price =
                                    Room.MODE_HOURLY.equals(r.getActiveMode())
                                            ? FormatUtils.formatCurrency(r.getPricePerHour())
                                                    + "/giờ"
                                            : (r.getPricePerMonth() > 0
                                                            ? FormatUtils.formatCurrency(
                                                                            r.getPricePerMonth())
                                                                    + "/tháng"
                                                            : "")
                                                    + (r.getPricePerMonth() > 0
                                                                    && r.getPricePerQuarter() > 0
                                                            ? " • "
                                                            : "")
                                                    + (r.getPricePerQuarter() > 0
                                                            ? FormatUtils.formatCurrency(
                                                                            r.getPricePerQuarter())
                                                                    + "/quý"
                                                            : "");
                            b.details.setText(
                                    safe(r.getAddress())
                                            + "\n"
                                            + safe(building.getName())
                                            + " • "
                                            + safe(r.getFloor())
                                            + "\n"
                                            + r.getCapacity()
                                            + " người • "
                                            + r.getArea()
                                            + " m²\n"
                                            + price
                                            + "\nCọc: "
                                            + FormatUtils.formatCurrency(r.getDeposit())
                                            + "\n"
                                            + safe(r.getOpenTime())
                                            + "–"
                                            + safe(r.getCloseTime())
                                            + "\n"
                                            + android.text.TextUtils.join(
                                                    ", ",
                                                    r.getAmenities() == null
                                                            ? java.util.Collections.emptyList()
                                                            : r.getAmenities())
                                            + "\n\n"
                                            + safe(r.getRules())
                                            + "\n\nNgười cho thuê: "
                                            + safe(state.data.hostName)
                                            + "\nLiên hệ tại tòa nhà: "
                                            + safe(building.getContactName())
                                            + " • "
                                            + safe(building.getContactPhone())
                                            + "\nKhu vực: "
                                            + safe(building.getDistrict())
                                            + "\nĐịa chỉ tòa nhà: "
                                            + safe(building.getAddress())
                                            + "\nHướng dẫn vào: "
                                            + safe(building.getDirectionGuide())
                                            + (building.getLocation() == null
                                                    ? ""
                                                    : "\nTọa độ do chủ cung cấp: "
                                                            + building.getLatitude()
                                                            + ", "
                                                            + building.getLongitude())
                                            + "\n\n"
                                            + "Nội dung được Admin duyệt; địa điểm chưa được xác"
                                            + " minh độc lập.");
                            if (r.getImageUrls() != null && !r.getImageUrls().isEmpty()) {
                                Glide.with(this)
                                        .load(r.getImageUrls().get(0))
                                        .placeholder(R.drawable.bg_image_placeholder)
                                        .error(R.drawable.bg_image_placeholder)
                                        .into(b.cover);
                                java.util.List<android.net.Uri> photos =
                                        new java.util.ArrayList<>();
                                for (String url : r.getImageUrls())
                                    photos.add(android.net.Uri.parse(url));
                                b.gallery.setAdapter(
                                        new vn.edu.ueh.phuclu.broomsharing.adapter
                                                .SelectedImageAdapter(photos, null));
                            }
                            b.detailScroll.post(() -> b.detailScroll.scrollTo(0, scrollY));
                        });
        model.ensureDetail(id);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? getString(R.string.guest_unknown) : value;
    }
}
