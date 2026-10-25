package vn.edu.ueh.phuclu.broomsharing.ui.favorites;

import android.content.Intent;
import android.view.ViewGroup;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.FavoritesAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityFavoritesBinding;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.room.SpaceDetailActivity;

/**
 * Màn hình yêu thích: hiển thị các phòng đã lưu và mở chi tiết phòng.
 * Gửi thao tác bỏ yêu thích sang FavoritesViewModel.
 */
public class FavoritesActivity extends SessionActivity {
    @Override
    protected int pageLayout() {
        return R.layout.activity_favorites;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        ActivityFavoritesBinding b =
                ActivityFavoritesBinding.bind(
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        FavoritesViewModel model = new ViewModelProvider(this).get(FavoritesViewModel.class);
        b.back.setOnClickListener(v -> finish());
        b.refresh.setOnClickListener(v -> model.load());
        FavoritesAdapter adapter =
                new FavoritesAdapter(
                        item ->
                                startActivity(
                                        new Intent(this, SpaceDetailActivity.class)
                                                .putExtra(
                                                        SpaceDetailActivity.ROOM_ID, item.roomId)),
                        item -> model.set(item.roomId, item.roomName, false));
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        model.items.observe(
                this,
                state -> {
                    if (state == null) return;
                    b.refresh.setEnabled(!state.loading && !model.busy());
                    b.feedback.setText(
                            state.loading
                                    ? "Đang tải…"
                                    : state.error == null
                                            ? "Tên phòng được ghi nhận lúc lưu. Chọn Xem phòng để kiểm tra tình trạng hiện tại. Tối đa 100 mục."
                                            : state.error);
                    if (state.data != null) {
                        adapter.setData(state.data);
                        if (state.data.isEmpty()) b.feedback.setText("Bạn chưa lưu phòng nào.");
                    }
                });
        model.saved.observe(
                this,
                state -> {
                    if (state == null) return;
                    adapter.setBusy(state.loading);
                    b.refresh.setEnabled(!state.loading);
                    if (state.error != null) b.feedback.setText(state.error);
                });
        model.load();
    }
}
