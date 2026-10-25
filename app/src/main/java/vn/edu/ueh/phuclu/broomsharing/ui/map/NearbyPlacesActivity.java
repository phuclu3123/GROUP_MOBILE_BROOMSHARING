package vn.edu.ueh.phuclu.broomsharing.ui.map;

import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityNearbyPlacesBinding;
import vn.edu.ueh.phuclu.broomsharing.databinding.ItemConversationBinding;
import vn.edu.ueh.phuclu.broomsharing.model.MapPoint;
import vn.edu.ueh.phuclu.broomsharing.model.NearbyPlace;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;
import vn.edu.ueh.phuclu.broomsharing.utils.MapArea;

import java.util.*;

/**
 * Màn hình địa điểm lân cận: hiển thị các tiện ích quanh vị trí phòng.
 * Gửi yêu cầu tìm kiếm qua NearbyPlacesViewModel.
 */
public class NearbyPlacesActivity extends SessionActivity {
    public static Intent intent(Context c, MapPoint p) {
        return new Intent(c, NearbyPlacesActivity.class)
                .putExtra("lat", p.latitude)
                .putExtra("lon", p.longitude)
                .putExtra("name", p.name);
    }

    @Override
    protected int pageLayout() {
        return R.layout.activity_nearby_places;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        ActivityNearbyPlacesBinding b =
                ActivityNearbyPlacesBinding.bind(findViewById(R.id.workspaceRoot));
        ManagementNavigation.applyInsets(this);
        b.placesToolbar.setNavigationOnClickListener(v -> finish());
        MapPoint center =
                new MapPoint(
                        getIntent().getDoubleExtra("lat", Double.NaN),
                        getIntent().getDoubleExtra("lon", Double.NaN),
                        getIntent().getStringExtra("name"));
        b.placesCenter.setText("Tìm quanh: " + center.name);
        NearbyPlacesViewModel model = new ViewModelProvider(this).get(NearbyPlacesViewModel.class);
        PlacesAdapter adapter = new PlacesAdapter();
        b.placesList.setLayoutManager(new LinearLayoutManager(this));
        b.placesList.setAdapter(adapter);
        b.placesRetry.setOnClickListener(v -> model.load(center));
        b.placesCredits.setOnClickListener(v -> source("https://www.maptiler.com/copyright/"));
        model.places.observe(
                this,
                s -> {
                    b.placesProgress.setVisibility(s.loading ? View.VISIBLE : View.GONE);
                    b.placesRetry.setVisibility(s.error == null ? View.GONE : View.VISIBLE);
                    b.placesStatus.setText(
                            s.loading
                                    ? "Đang tìm tiện ích…"
                                    : s.error != null
                                            ? s.error
                                            : s.data == null || s.data.isEmpty()
                                                    ? "Chưa có tiện ích trong dữ liệu trả về; không có nghĩa khu vực không có dịch vụ."
                                                    : s.data.size()
                                                            + " tiện ích · Chạm để xem vị trí hoặc nguồn");
                    adapter.rows = s.data == null ? Collections.emptyList() : s.data;
                    adapter.notifyDataSetChanged();
                    if (s.error != null && !model.fallbackOpened && MapArea.contains(center)) {
                        model.fallbackOpened = true;
                        openFallback(center);
                    }
                    if (s.data != null) model.fallbackOpened = false;
                });
        model.ensure(center);
    }

    private void openFallback(MapPoint point) {
        String url =
                "https://www.google.com/maps/search/?api=1&query="
                        + Uri.encode("tiện ích gần " + point.latitude + "," + point.longitude);
        try {
            startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .setPackage("com.google.android.apps.maps"));
        } catch (ActivityNotFoundException e) {
            source(url);
        }
    }

    private void source(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Chưa có ứng dụng mở liên kết.", Toast.LENGTH_SHORT).show();
        }
    }

    private void choose(NearbyPlace p) {
        setResult(
                RESULT_OK,
                new Intent()
                        .putExtra("placeLat", p.point.latitude)
                        .putExtra("placeLon", p.point.longitude)
                        .putExtra("placeName", p.name)
                        .putExtra("originLat", getIntent().getDoubleExtra("originLat", Double.NaN))
                        .putExtra(
                                "originLon", getIntent().getDoubleExtra("originLon", Double.NaN)));
        finish();
    }

    private class PlacesAdapter extends RecyclerView.Adapter<Holder> {
        List<NearbyPlace> rows = Collections.emptyList();

        public int getItemCount() {
            return rows.size();
        }

        public Holder onCreateViewHolder(ViewGroup p, int t) {
            return new Holder(
                    ItemConversationBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
        }

        public void onBindViewHolder(Holder h, int position) {
            NearbyPlace p = rows.get(position);
            h.b.conversationName.setText(p.name);
            h.b.conversationPreview.setText(p.type + " · " + Math.round(p.distance) + " m");
            h.b.conversationUnread.setVisibility(View.GONE);
            h.b.getRoot()
                    .setOnClickListener(
                            v ->
                                    new AlertDialog.Builder(NearbyPlacesActivity.this)
                                            .setTitle(p.name)
                                            .setMessage(
                                                    p.type
                                                            + "\n"
                                                            + p.point.latitude
                                                            + ", "
                                                            + p.point.longitude
                                                            + "\nNguồn: MapTiler / OpenStreetMap. Vị trí có thể là tâm khuôn viên.")
                                            .setPositiveButton(
                                                    "Xem tuyến trong app", (d, w) -> choose(p))
                                            .setNeutralButton(
                                                    "Xem nguồn", (d, w) -> source(p.sourceUrl))
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show());
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        final ItemConversationBinding b;

        Holder(ItemConversationBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
