package vn.edu.ueh.phuclu.broomsharing.ui.review;

import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityRoomReviewsBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;
import vn.edu.ueh.phuclu.broomsharing.utils.TimeUtils;

// Xem nhận xét theo phòng; form gửi chỉ được mở từ chi tiết booking đã hoàn tất.
public class RoomReviewsActivity extends SessionActivity {
    private ActivityRoomReviewsBinding b;
    private RoomReviewsViewModel model;
    private String roomId, bookingId;

    @Override
    protected int pageLayout() {
        return R.layout.activity_room_reviews;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        b = ActivityRoomReviewsBinding.bind(findViewById(R.id.workspaceRoot));
        ManagementNavigation.applyInsets(this);
        roomId = getIntent().getStringExtra("roomId");
        bookingId = getIntent().getStringExtra("bookingId");
        b.toolbar.setNavigationOnClickListener(v -> finish());
        model = new ViewModelProvider(this).get(RoomReviewsViewModel.class);
        b.reviewForm.setVisibility(bookingId == null ? View.GONE : View.VISIBLE);
        b.send.setOnClickListener(
                v ->
                        model.submit(
                                bookingId,
                                (int) b.rating.getRating(),
                                b.comment.getText().toString()));
        b.retry.setOnClickListener(v -> model.watch(roomId));
        model.saved.observe(
                this,
                state -> {
                    if (state == null) return;
                    b.send.setEnabled(!state.loading);
                    b.rating.setEnabled(!state.loading);
                    b.comment.setEnabled(!state.loading);
                    b.feedback.setText(
                            state.loading
                                    ? getString(R.string.business_loading)
                                    : state.error == null ? "Đã lưu đánh giá." : state.error);
                    if (state.data != null) b.reviewForm.setVisibility(View.GONE);
                });
        model.rows.observe(
                this,
                state -> {
                    if (state == null) return;
                    b.retry.setEnabled(!state.loading);
                    b.reviewList.removeAllViews();
                    if (state.error != null || state.loading) {
                        b.feedback.setText(
                                state.loading ? getString(R.string.business_loading) : state.error);
                        return;
                    }
                    if (state.data == null) return;
                    b.feedback.setText(
                            state.data.isEmpty() ? getString(R.string.business_empty) : "");
                    for (RoomReview row : state.data) {
                        // Chỉ hiển thị snapshot tên và nhận xét; không công khai email/số điện
                        // thoại.
                        TextView view = new TextView(this);
                        view.setText(
                                row.rating
                                        + "/5 · "
                                        + row.guestName
                                        + "\n"
                                        + row.comment
                                        + "\n"
                                        + TimeUtils.formatVietnam(row.createdAt));
                        view.setTextSize(15);
                        view.setTextColor(getColor(R.color.text_primary));
                        view.setPadding(12, 24, 12, 24);
                        b.reviewList.addView(view);
                        if (row.bookingId.equals(bookingId)) b.reviewForm.setVisibility(View.GONE);
                    }
                });
        model.watch(roomId);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (model != null) model.watch(roomId);
    }

    @Override
    protected void onStop() {
        if (model != null) model.stop();
        super.onStop();
    }
}
