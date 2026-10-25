package vn.edu.ueh.phuclu.broomsharing.ui.booking;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.view.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityBookingNoticesBinding;
import vn.edu.ueh.phuclu.broomsharing.databinding.ItemConversationBinding;
import vn.edu.ueh.phuclu.broomsharing.model.BookingNotice;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ChatActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.BookingNotifier;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostBookingsActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.report.ReportCenterActivity;

import java.util.*;

/**
 * Màn hình thông báo: hiển thị tin mới và mở đúng cuộc trò chuyện hoặc đơn đặt chỗ. Theo dõi trạng
 * thái đã đọc thông qua BookingNoticeViewModel.
 */
public class BookingNoticesActivity extends SessionActivity {
    private ActivityBookingNoticesBinding b;
    private String uid;
    private BookingNoticeViewModel model;
    private final ActivityResultLauncher<String> permission =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    allowed -> {
                        if (allowed) enable();
                        else if (b != null)
                            b.noticeStatus.setText(
                                    "Chưa có quyền thông báo. Bạn vẫn xem được cập nhật trong ứng"
                                        + " dụng.");
                    });

    @Override
    protected int pageLayout() {
        return R.layout.activity_booking_notices;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        if (profile.getUserRole() == UserRole.ADMIN) {
            finish();
            return;
        }
        uid = profile.uid;
        b = ActivityBookingNoticesBinding.bind(findViewById(R.id.workspaceRoot));
        ManagementNavigation.applyInsets(this);
        model = new ViewModelProvider(this).get(BookingNoticeViewModel.class);
        b.noticeToolbar.setNavigationOnClickListener(v -> finish());
        NoticeAdapter adapter = new NoticeAdapter();
        b.noticeList.setLayoutManager(new LinearLayoutManager(this));
        b.noticeList.setAdapter(adapter);
        b.noticePermission.setOnClickListener(
                v -> {
                    if (BookingNotifier.preferences(this, uid).getBoolean("enabled", false)) {
                        BookingNotifier.preferences(this, uid)
                                .edit()
                                .putBoolean("enabled", false)
                                .apply();
                        renderButton();
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 33)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS);
                    else enable();
                });
        renderButton();
        b.noticeRetry.setOnClickListener(v -> model.retry());
        model.notices.observe(
                this,
                state ->
                        b.noticeRetry.setVisibility(
                                state != null && state.error != null ? View.VISIBLE : View.GONE));
        model.notices.observe(
                this,
                state -> {
                    if (state == null) return;
                    b.noticeStatus.setText(
                            state.loading
                                    ? "Đang đồng bộ…"
                                    : state.error != null
                                            ? state.error
                                            : state.data == null || state.data.isEmpty()
                                                    ? "Chưa có cập nhật đặt chỗ."
                                                    : "");
                    adapter.rows = state.data == null ? Collections.emptyList() : state.data;
                    adapter.notifyDataSetChanged();
                });
        model.readError.observe(
                this,
                error -> {
                    if (error != null) b.noticeStatus.setText(error);
                });
        model.start(profile.getUserRole() == UserRole.HOST);
    }

    private void renderButton() {
        b.noticePermission.setText(
                BookingNotifier.preferences(this, uid).getBoolean("enabled", false)
                        ? "Tắt thông báo trên thiết bị"
                        : "Bật thông báo trên thiết bị");
    }

    private void enable() {
        BookingNotifier.preferences(this, uid).edit().putBoolean("enabled", true).apply();
        renderButton();
        if (model.notices.getValue() != null && model.notices.getValue().data != null)
            BookingNotifier.deliver(this, uid, model.notices.getValue().data);
    }

    private class NoticeAdapter extends RecyclerView.Adapter<NoticeHolder> {
        List<BookingNotice> rows = Collections.emptyList();

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public NoticeHolder onCreateViewHolder(ViewGroup p, int type) {
            return new NoticeHolder(
                    ItemConversationBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
        }

        @Override
        public void onBindViewHolder(NoticeHolder h, int pos) {
            BookingNotice n = rows.get(pos);
            h.b.conversationName.setText(n.title);
            h.b.conversationPreview.setMaxLines(6);
            h.b.conversationPreview.setText(n.body);
            h.b.conversationUnread.setVisibility(n.unread ? View.VISIBLE : View.GONE);
            h.b.conversationUnread.setText("Mới");
            h.b.getRoot()
                    .setOnClickListener(
                            v -> {
                                model.markRead(n);
                                Intent target =
                                        n.reportNotice
                                                ? new Intent(
                                                                BookingNoticesActivity.this,
                                                                ReportCenterActivity.class)
                                                        .putExtra("received", n.forHost)
                                                : !n.conversationId.isEmpty()
                                                        ? new Intent(
                                                                        BookingNoticesActivity.this,
                                                                        ChatActivity.class)
                                                                .putExtra(
                                                                        "conversationId",
                                                                        n.conversationId)
                                                        : new Intent(
                                                                        BookingNoticesActivity.this,
                                                                        n.forHost
                                                                                ? HostBookingsActivity
                                                                                        .class
                                                                                : BookingSuccessActivity
                                                                                        .class)
                                                                .putExtra("bookingId", n.bookingId);
                                startActivity(target);
                            });
        }
    }

    private static class NoticeHolder extends RecyclerView.ViewHolder {
        final ItemConversationBinding b;

        NoticeHolder(ItemConversationBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
