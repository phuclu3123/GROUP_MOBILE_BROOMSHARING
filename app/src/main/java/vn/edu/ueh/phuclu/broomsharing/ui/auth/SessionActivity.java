package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.model.Conversation;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.admin.AdminDashboardActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingNoticeViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ChatActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ChatViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.common.BookingNotifier;
import vn.edu.ueh.phuclu.broomsharing.ui.common.SessionGuardViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.room.MainActivity;

/**
 * Activity cha dùng chung cho những màn hình yêu cầu đăng nhập. Kiểm tra Session, phân quyền, điều
 * hướng và theo dõi thông báo. Giao tiếp với AuthViewModel thay vì gọi trực tiếp
 * Firebase/Repository.
 */
public abstract class SessionActivity extends AppCompatActivity {
    private AuthViewModel model; // ViewModel quản lý trạng thái xác thực
    private boolean shown; // Đánh dấu màn hình chính đã được hiển thị
    private ChatViewModel chat; // ViewModel quản lý tin nhắn
    private BookingNoticeViewModel notices; // ViewModel quản lý thông báo đặt lịch
    private SessionGuardViewModel guard;
    private String guardUid, initialRole;
    private boolean accessStopped;

    // Lớp con bắt buộc cung cấp layout của màn hình tương ứng.
    protected abstract int pageLayout();

    // Hook cho lớp con xử lý sau khi hồ sơ người dùng đã sẵn sàng.
    protected void onProfileReady(UserProfile profile) {}

    // Các lớp con có thể Override để yêu cầu quyền truy cập cụ thể.
    protected boolean requiresAdmin() {
        return false;
    }

    protected boolean requiresHost() {
        return false;
    }

    protected boolean isGuestHome() {
        return false;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Lấy AuthViewModel với phạm vi vòng đời của Activity hiện tại.
        model = new ViewModelProvider(this).get(AuthViewModel.class);
        // Hiển thị giao diện kiểm tra phiên trước khi mở màn hình thực tế.
        setContentView(R.layout.activity_session_check);
        findViewById(R.id.retryButton).setOnClickListener(v -> checkSession());

        // Chưa có Session -> chuyển về Login và kết thúc Activity.
        if (!model.hasSession()) {
            toLogin(0);
            return;
        }

        // Xoay màn hình dùng lại phiên đã kiểm tra của chính Activity, không đọc role từ cache.
        // Listener vẫn kiểm tra lại quyền trên server khi kết nối trở lại.
        AuthViewModel.State retained = model.getState().getValue();
        if (retained == null || retained.profile == null) model.reloadSession();

        // LiveData Observer: nhận trạng thái mới khi ViewModel cập nhật State.
        // LifecycleOwner = this, Observer gắn với vòng đời Activity.
        model.getState()
                .observe(
                        this,
                        value -> {
                            // Tránh cập nhật khi đã mở trang hoặc Activity đang bị hủy.
                            if (shown || isFinishing() || isDestroyed()) return;

                            // Hiện ProgressBar nếu đang tải, ngược lại ẩn đi.
                            findViewById(R.id.authProgress)
                                    .setVisibility(value.loading ? View.VISIBLE : View.GONE);

                            // Chỉ hiện nút Retry khi có lỗi.
                            findViewById(R.id.retryButton)
                                    .setVisibility(value.error == null ? View.GONE : View.VISIBLE);

                            // Không lỗi -> thông báo đang xử lý; có lỗi -> lấy nội dung lỗi.
                            ((TextView) findViewById(R.id.authMessage))
                                    .setText(
                                            value.error == null
                                                    ? R.string.auth_busy
                                                    : AuthMessages.resource(value.error));

                            // Khi đã lấy được hồ sơ -> kiểm tra quyền và hiển thị màn hình.
                            if (value.profile != null) show(value.profile);
                        });
    }

    // Kiểm tra lại Session khi người dùng nhấn Retry.
    private void checkSession() {
        if (!model.hasSession()) toLogin(0);
        else model.reloadSession();
    }

    // Xử lý phân quyền và khởi tạo màn hình sau khi nhận được UserProfile.
    private void show(UserProfile profile) {
        // Màn hình yêu cầu ADMIN nhưng người dùng không phải ADMIN.
        if (requiresAdmin() && profile.getUserRole() != UserRole.ADMIN) {
            toLogin(R.string.auth_role_invalid);
            return;
        }

        // Màn hình yêu cầu HOST nhưng tài khoản chưa có quyền HOST.
        if (requiresHost() && profile.getUserRole() != UserRole.HOST) {
            Toast.makeText(
                            this,
                            "Chức năng này dành cho tài khoản Host đã được duyệt.",
                            Toast.LENGTH_LONG)
                    .show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        // ADMIN vào trang Home dành cho người dùng -> chuyển Admin Dashboard.
        if (isGuestHome() && profile.getUserRole() == UserRole.ADMIN) {
            startActivity(
                    new Intent(this, AdminDashboardActivity.class)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        // Đánh dấu đã hiển thị để tránh gọi show() nhiều lần.
        shown = true;
        // Thay layout kiểm tra Session bằng layout thật của lớp con.
        setContentView(pageLayout());
        // Gọi hook để lớp con khởi tạo giao diện bằng UserProfile.
        onProfileReady(profile);
        guardUid = profile.uid;
        initialRole = profile.role;
        guard = new ViewModelProvider(this).get(SessionGuardViewModel.class);
        guard.profile.observe(
                this,
                value -> {
                    if (accessStopped || isFinishing()) return;
                    if (value.error != null
                            || value.data == null
                            || !"ACTIVE".equals(value.data.status)) {
                        // Thu hồi UI ngay trong phiên cũ; Rules vẫn là lớp từ chối ghi cuối cùng.
                        accessStopped = true;
                        if (chat != null) chat.stop();
                        if (notices != null) notices.stop();
                        setContentView(R.layout.activity_session_check);
                        findViewById(R.id.authProgress).setVisibility(View.GONE);
                        ((TextView) findViewById(R.id.authMessage))
                                .setText(
                                        value.error != null
                                                ? value.error
                                                : getString(R.string.account_blocked));
                        ((android.widget.Button) findViewById(R.id.retryButton))
                                .setText(
                                        value.error == null
                                                ? R.string.workspace_signout
                                                : R.string.workspace_retry);
                        findViewById(R.id.retryButton).setVisibility(View.VISIBLE);
                        findViewById(R.id.retryButton)
                                .setOnClickListener(
                                        v -> {
                                            if (value.error == null) signOut();
                                            else {
                                                startActivity(
                                                        new Intent(this, MainActivity.class)
                                                                .addFlags(
                                                                        Intent
                                                                                        .FLAG_ACTIVITY_NEW_TASK
                                                                                | Intent
                                                                                        .FLAG_ACTIVITY_CLEAR_TASK));
                                                finish();
                                            }
                                        });
                    } else if (!initialRole.equals(value.data.role)) {
                        startActivity(
                                new Intent(this, MainActivity.class)
                                        .addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK
                                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                        finish();
                    }
                });
        guard.watch(guardUid);

        // Người dùng không phải ADMIN và không ở ChatActivity -> theo dõi Inbox.
        if (profile.getUserRole() != UserRole.ADMIN && !(this instanceof ChatActivity)) {
            chat = new ViewModelProvider(this).get(ChatViewModel.class);

            // Quan sát danh sách Conversation để tính tổng tin nhắn chưa đọc.
            chat.inbox.observe(
                    this,
                    state -> {
                        if (state == null || state.data == null) return;
                        long unread = 0;
                        for (Conversation c : state.data) {
                            unread += c.unread(profile.uid);
                        }
                        // Cập nhật Badge ở thanh điều hướng người dùng và HOST.
                        updateBadge(R.id.bottomNavigation, R.id.nav_messages, unread);
                        updateBadge(R.id.managementNavigation, R.id.host_messages, unread);
                    });
            // Bắt đầu theo dõi các thay đổi của Inbox.
            chat.watchInbox();
        }

        // Tài khoản không phải ADMIN -> theo dõi thông báo Booking.
        if (profile.getUserRole() != UserRole.ADMIN) {
            notices = new ViewModelProvider(this).get(BookingNoticeViewModel.class);

            // Observer chỉ gửi thông báo khi Activity, Session và dữ liệu hợp lệ.
            notices.notices.observe(
                    this,
                    state -> {
                        if (!isFinishing()
                                && model.hasSession()
                                && model.getState().getValue() != null
                                && model.getState().getValue().profile != null
                                && state != null
                                && state.data != null) {
                            BookingNotifier.deliver(this, profile.uid, state.data);
                        }
                    });

            // HOST nhận luồng thông báo dành cho HOST, các role khác dùng false.
            notices.start(profile.getUserRole() == UserRole.HOST);
        }
    }

    // Cập nhật số lượng chưa đọc trên BottomNavigationView.
    private void updateBadge(int viewId, int itemId, long unread) {
        BottomNavigationView nav = findViewById(viewId);

        // Không có NavigationView hoặc MenuItem -> bỏ qua.
        if (nav == null || nav.getMenu().findItem(itemId) == null) return;

        // Không có tin chưa đọc -> xóa Badge.
        if (unread == 0) nav.removeBadge(itemId);
        // Có tin chưa đọc -> tạo/lấy Badge, giới hạn số hiển thị tối đa 999.
        else nav.getOrCreateBadge(itemId).setNumber((int) Math.min(unread, 999));
    }

    // Activity vào trạng thái Started -> theo dõi hoặc tiếp tục các tác vụ.
    @Override
    protected void onStart() {
        super.onStart();
        if (accessStopped) return;
        if (guard != null && guardUid != null) guard.watch(guardUid);
        if (chat != null) chat.watchInbox();
        if (notices != null) notices.resume();
    }

    // Activity chuyển sang Stopped -> dừng các listener/tác vụ theo dõi.
    @Override
    protected void onStop() {
        if (guard != null) guard.stop();
        if (chat != null) chat.stop();
        if (notices != null) notices.stop();
        super.onStop();
    }

    // Đăng xuất: dừng listener, xóa thông báo, hủy phiên và về Login.
    protected void signOut() {
        if (guard != null) guard.stop();
        if (chat != null) chat.stop();
        if (notices != null) notices.stop();
        NotificationManagerCompat.from(this).cancelAll();
        model.signOut();
        toLogin(0);
    }

    // Chuyển đến LoginActivity và xóa Back Stack.
    private void toLogin(int notice) {
        // Nếu bị từ chối vì quyền truy cập -> đăng xuất phiên hiện tại.
        if (notice != 0) model.signOut();

        Intent intent =
                new Intent(this, LoginActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Truyền Resource ID thông báo lỗi đến LoginActivity nếu có.
        if (notice != 0) intent.putExtra("auth_notice", notice);

        startActivity(intent);
        finish(); // Kết thúc Activity hiện tại.
    }
}
