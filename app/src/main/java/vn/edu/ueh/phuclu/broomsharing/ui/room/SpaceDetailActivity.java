package vn.edu.ueh.phuclu.broomsharing.ui.room;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.SelectedImageAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivitySpaceDetailBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.RoomListing;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ChatActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ChatViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.favorites.FavoritesViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.map.MapActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.report.RoomReportActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.review.RoomReviewsActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

import java.text.NumberFormat;
import java.util.*;

/**
 * Màn hình chi tiết phòng: hiển thị thông tin và mở các chức năng liên quan. Kiểm tra phiên qua
 * SessionActivity trước khi tải nội dung.
 */
public class SpaceDetailActivity extends SessionActivity {
    // Key truyền mã phòng giữa các màn hình liên quan đến phòng.
    public static final String ROOM_ID = "ROOM_ID";

    // Giữ vị trí cuộn để khôi phục trang chi tiết sau khi Activity được dựng lại.
    private int scrollY;

    // Binding của activity_space_detail.xml.
    private ActivitySpaceDetailBinding b;

    // Hồ sơ của người đang xem màn hình.
    private UserProfile viewer;

    @Override
    protected void onCreate(Bundle state) {
        // Khôi phục vị trí cuộn trước khi lớp cha thiết lập phiên và giao diện.
        if (state != null) scrollY = state.getInt("detail.scroll", 0);
        super.onCreate(state);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        // Lưu vị trí cuộn hiện tại để không đưa người dùng về đầu trang khi xoay màn hình.
        out.putInt("detail.scroll", b == null ? scrollY : b.detailScroll.getScrollY());
        super.onSaveInstanceState(out);
    }

    @Override
    protected int pageLayout() {
        // SessionActivity dùng layout chi tiết phòng sau khi kiểm tra phiên.
        return R.layout.activity_space_detail;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        viewer = profile;
        // Nối Binding với giao diện đã được SessionActivity nạp.
        b =
                ActivitySpaceDetailBinding.bind(
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        b.gallery.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        RoomViewModel model = new ViewModelProvider(this).get(RoomViewModel.class);
        FavoritesViewModel favorites = new ViewModelProvider(this).get(FavoritesViewModel.class);

        // Nhận mã phòng từ Intent; mã này sẽ được chuyển tiếp sang các màn hình khác.
        String id = getIntent().getStringExtra(ROOM_ID);
        vn.edu.ueh.phuclu.broomsharing.ui.common.CheckoutReminder.attach(
                this, b.pendingCheckout.getRoot(), profile.uid, id);
        b.btnReviews.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(this, RoomReviewsActivity.class)
                                        .putExtra("roomId", id)));
        b.detailToolbar.setNavigationOnClickListener(v -> finish());

        // Mở BookingActivity và gửi kèm mã phòng để tạo yêu cầu đặt đúng phòng này.
        b.btnBook.setOnClickListener(
                v -> startActivity(new Intent(this, BookingActivity.class).putExtra(ROOM_ID, id)));
        b.btnDirections.setOnClickListener(
                v -> startActivity(new Intent(this, MapActivity.class).putExtra(ROOM_ID, id)));
        b.btnChat.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(this, ChatActivity.class)
                                        .putExtra(ROOM_ID, id)
                                        .putExtra("roomName", b.title.getText().toString())));
        b.btnRefresh.setOnClickListener(v -> model.load(id));
        b.btnReport.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(this, RoomReportActivity.class).putExtra(ROOM_ID, id)));
        b.btnFavorite.setOnClickListener(
                v -> {
                    UiState<RoomListing> state = model.detail().getValue();
                    if (state != null && state.data != null)
                        favorites.set(id, state.data.room.getRoomName(), !favorites.contains(id));
                });
        favorites.items.observe(
                this,
                state -> {
                    b.btnFavorite.setEnabled(
                            state != null
                                    && state.data != null
                                    && !state.loading
                                    && !favorites.busy());
                    b.btnFavorite.setText(favorites.contains(id) ? "Bỏ lưu phòng" : "Lưu phòng");
                    if (state != null && state.error != null) b.tvFeedback.setText(state.error);
                });
        favorites.saved.observe(
                this,
                state -> {
                    if (state == null) return;
                    b.btnFavorite.setEnabled(!state.loading);
                    if (state.error != null) b.tvFeedback.setText(state.error);
                });
        favorites.load();
        model.detail().observe(this, this::render);
        model.ensureDetail(id);
    }

    private void showPhoto(Object uri) {
        Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(b.cover);
    }

    private void render(UiState<RoomListing> state) {
        b.btnRefresh.setVisibility(state.error == null ? View.GONE : View.VISIBLE);
        b.btnRefresh.setEnabled(!state.loading);
        b.btnBook.setEnabled(state.data != null && !state.loading);
        b.btnDirections.setEnabled(false);
        b.btnChat.setEnabled(false);
        b.btnCall.setEnabled(false);
        b.btnReport.setEnabled(false);
        for (TextView field :
                new TextView[] {
                    b.title,
                    b.price,
                    b.summary,
                    b.address,
                    b.details,
                    b.deposit,
                    b.policies,
                    b.location,
                    b.verification
                }) field.setText("");
        Glide.with(this).clear(b.cover);
        b.cover.setImageResource(R.drawable.bg_image_placeholder);
        b.gallery.setAdapter(null);
        b.tvFeedback.setText(
                state.loading
                        ? "Đang tải thông tin phòng…"
                        : state.error == null ? "" : state.error);
        if (state.data == null) return;
        Room room = state.data.room;
        Building building = state.data.building;
        b.btnReport.setVisibility(
                viewer.getUserRole() == UserRole.ADMIN || viewer.uid.equals(room.getOwnerUid())
                        ? View.GONE
                        : View.VISIBLE);
        b.btnReport.setEnabled(true);
        b.title.setText(room.getRoomName());
        String price;
        if (Room.MODE_HOURLY.equals(room.getActiveMode()))
            price = FormatUtils.formatCurrency(room.getPricePerHour()) + "/giờ";
        else {
            List<String> prices = new ArrayList<>();
            if (room.getPricePerMonth() > 0)
                prices.add(FormatUtils.formatCurrency(room.getPricePerMonth()) + "/tháng");
            if (room.getPricePerQuarter() > 0)
                prices.add(FormatUtils.formatCurrency(room.getPricePerQuarter()) + "/quý");
            price = TextUtils.join(" • ", prices);
        }
        b.price.setText(price);
        b.summary.setText(
                safe(room.getRoomType())
                        + " • "
                        + room.getCapacity()
                        + " người • "
                        + NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
                                .format(room.getArea())
                        + " m²");
        b.address.setText(safe(room.getAddress()));
        b.details.setText(
                "Ngày hoạt động: "
                        + workingDays(room.getWorkingDays())
                        + "\nGiờ mở cửa: "
                        + safe(room.getOpenTime())
                        + "–"
                        + safe(room.getCloseTime())
                        + "\nTiện ích: "
                        + TextUtils.join(
                                ", ",
                                room.getAmenities() == null
                                        ? Collections.emptyList()
                                        : room.getAmenities()));
        b.deposit.setText(
                "Trả trước dự kiến: 40% tiền thuê, tính theo kỳ đã chọn.\n"
                    + "Thanh toán ngay qua kênh Host payOS demo. Lịch được tạm giữ tối đa 5 phút khi mở QR; nhận đủ tiền mới xác nhận đặt chỗ, không cần Host duyệt.");
        b.policies.setText(
                "Chính sách hủy cho booking mới\n"
                        + CancellationPolicy.terms(room.getActiveMode())
                        + "\n\nNội quy\n"
                        + safe(room.getRules())
                        + "\n\nĐiều kiện thuê\n"
                        + safe(room.getRentalPolicy())
                        + "\n\nGia hạn\n"
                        + safe(room.getRenewalPolicy()));
        b.hostAvatar.setVisibility(state.data.hostPhotoUrl.isEmpty() ? View.GONE : View.VISIBLE);
        if (!state.data.hostPhotoUrl.isEmpty())
            vn.edu.ueh.phuclu.broomsharing.utils.OnlineImages.show(
                    b.hostAvatar, Uri.parse(state.data.hostPhotoUrl));
        b.location.setText(
                safe(building.getName())
                        + " • Tầng "
                        + safe(room.getFloor())
                        + "\n"
                        + safe(building.getDistrict())
                        + " • "
                        + safe(building.getAddress())
                        + "\n\nNgười cho thuê: "
                        + safe(state.data.hostName)
                        + "\nLiên hệ: "
                        + safe(building.getContactName())
                        + "\n"
                        + safe(building.getContactPhone())
                        + "\n\nHướng dẫn đến: "
                        + safe(building.getDirectionGuide()));
        b.verification.setText(FormatUtils.verificationSummary(room));
        b.btnDirections.setEnabled(building.getLocation() != null);
        b.btnChat.setEnabled(
                !room.getOwnerUid()
                        .equals(new ViewModelProvider(this).get(ChatViewModel.class).uid()));
        String phone = building.getContactPhone();
        b.btnCall.setEnabled(
                phone != null && phone.replaceAll("[^+0-9]", "").matches("[+]?[0-9]{8,15}"));
        b.btnCall.setOnClickListener(
                v -> {
                    Intent dial =
                            new Intent(
                                    Intent.ACTION_DIAL,
                                    Uri.fromParts("tel", phone.replaceAll("[^+0-9]", ""), null));
                    try {
                        startActivity(dial);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(
                                        this,
                                        "Thiết bị chưa có ứng dụng gọi điện.",
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                });
        List<String> urls = room.getImageUrls();
        b.gallery.setVisibility(urls == null || urls.size() < 2 ? View.GONE : View.VISIBLE);
        if (urls != null && !urls.isEmpty()) {
            showPhoto(urls.get(0));
            List<Uri> photos = new ArrayList<>();
            for (String url : urls) photos.add(Uri.parse(url));
            b.gallery.setAdapter(new SelectedImageAdapter(photos, null, this::showPhoto));
        }
        b.detailScroll.post(() -> b.detailScroll.scrollTo(0, scrollY));
    }

    private String workingDays(List<Integer> days) {
        if (days == null || days.isEmpty()) return "Chưa có lịch";
        if (days.size() == 7 && days.containsAll(Arrays.asList(1, 2, 3, 4, 5, 6, 7)))
            return "Hằng ngày";
        List<String> labels = new ArrayList<>();
        for (Integer day : days)
            if (day != null && day >= 1 && day <= 7)
                labels.add(day == 7 ? "Chủ nhật" : "Thứ " + (day + 1));
        return TextUtils.join(", ", labels);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? getString(R.string.guest_unknown) : value;
    }
}
