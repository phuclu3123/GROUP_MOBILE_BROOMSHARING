package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.app.Application;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.repository.AuthError;
import vn.edu.ueh.phuclu.broomsharing.repository.AuthRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.FirebaseAuthRepository;

// Giữ trạng thái xác thực qua tái tạo Activity, chặn gửi lặp và chuyển yêu cầu xuống Repository.
public class AuthViewModel extends AndroidViewModel {
    /*
        Quản lý trạng thái cho nhóm màn hình đăng nhập, đăng ký và khôi phục mật khẩu.
        Tức là cầu nối giữa View và Repository
    */

    // State: 1. Đang xử lý, 2. Thông tin người dùng, 3. Lỗi
    public static final class State {
        public final boolean loading;
        public final UserProfile profile;
        public final AuthError error;

        // Constructor của State
        State(boolean loading, UserProfile profile, AuthError error) {
            this.loading = loading;
            this.profile = profile;
            this.error = error;
        }
    }

    // Khai báo Repository
    private final AuthRepository repository;
    // MutableLiveData là nơi ViewModel cập nhật trạng thái của View. Khá giống React.
    private final MutableLiveData<State> state =
            new MutableLiveData<>(new State(false, null, null));

    // Khôi phục phiên từ màn hình này chưa ?
    private boolean restored;
    // Yêu cầu đặt mật khẩu gần nhất có thành công hay không ?
    private boolean resetSent;
    // Thời điểm được phép gửi lại yêu cầu
    private long resetRetryAt;

    // Yêu cầu gửi reset mật khẩu - trạng thái & tính thời gian còn chờ.
    public boolean passwordResetSent() { return resetSent; }
    public long passwordResetWaitSeconds() {
        return Math.max(0, (resetRetryAt - SystemClock.elapsedRealtime() + 999) / 1000);
    }

    /* ResetPassword - nếu đang bận - hoặc thời gian chờ vẫn còn thì trả về
        Trạng thái gửi reset = false;
        Cập nhật trạng thái lại = true;

        Callback là một hàm hoặc đối tượng chứa các phương thức được cung cấp cho đoạn code
        khác, để đoạn code đó gọi lại khi thích hợp.
        Ta đang tiến hành tạo (Anonymous Class)

        Tức ở đây nó chỉ là cập nhật trạng thái từ MV -> V
        Dùng cho thằng quên mật khẩu email (đặt lại mật khẩu). Thành công thì
        thông báo "đã gửi"; không cần UserProfile, error thì AuthEror
     */
    public void resetPassword(String email) {
        if (isBusy() || passwordResetWaitSeconds() > 0) return;
        resetSent = false;
        state.setValue(new State(true, null, null));
        repository.resetPassword(email, new AuthRepository.ResetCallback() {
            public void onSuccess() {
                resetSent = true;
                resetRetryAt = SystemClock.elapsedRealtime() + 60_000;
                state.setValue(new State(false, null, null));
            }
            public void onError(AuthError error) {
                state.setValue(new State(false, null, error));
            }
        });
    }

    public AuthViewModel(@NonNull Application application) {
        super(application);
        repository = new FirebaseAuthRepository(application);
    }

    // Chỉ đọc trạng thái của state;
    public LiveData<State> getState() {
        return state;
    }

    // Hàm chuyển tiếp
    public boolean isConfigured() {
        return repository.isConfigured();
    }

    // Lấy trạng thái hiện tại nếu có rồi hoặc hàm trả về trạng thái loading => bận
    public boolean isBusy() {
        return state.getValue() != null && state.getValue().loading;
    }

    // Hàm trả về trạng thái của session
    public boolean hasSession() {
        return repository.hasSession();
    }

    /* Hàm reload trạng thái của session, nếu đang bận thì trả về.
        Nếu chưa thì bật trạng thái loading = true;
        Gọi hàm LoadSession cho repo
     */
    public void reloadSession() {
        if (isBusy()) return;
        state.setValue(new State(true, null, null));
        repository.loadSession(callback);
    }

    // Hàm logout của repo
    public void signOut() {
        repository.logout();
        consumeSuccess();
    }

    /* Tương tự với ResetCallback ở trên, đóng vai trò callback (Dùng cho đăng nhập
        đăng ký, hồ sơ phiên)

        Nhưng khác hơn xíu cần trả về UserProfile nên có tham số.
     */
    private final AuthRepository.Callback callback =
            new AuthRepository.Callback() {
                @Override
                public void onSuccess(UserProfile profile) {
                    state.setValue(new State(false, profile, null));
                }

                @Override
                public void onError(AuthError error) {
                    state.setValue(new State(false, null, error));
                }
            };

    // Đăng nhập - identifier là chuỗi người dùng nhập vào. Password là mật khẩu
    public void login(String identifier, String password) {
        if (isBusy()) return;
        state.setValue(new State(true, null, null));
        repository.login(identifier, password, callback);
    }

    // Đăng ký - tên, tên đăng nhập, email, số điện thoại, mật khẩu
    public void register(
            String name, String username, String email, String phone, String password) {
        if (isBusy()) return;
        state.setValue(new State(true, null, null));
        repository.register(name, username, email, phone, password, callback);
    }

    // khôi phục phiên đăng nhập
    public void restoreSessionOnce() {
        if (restored || isBusy()) return;
        restored = true;
        if (repository.hasSession()) {
            state.setValue(new State(true, null, null));
            repository.loadSession(callback);
        }
    }

    // Dọn trạng thái thành công sau khi màn hình đã dùng kết quả.
    public void consumeSuccess() {
        state.setValue(new State(false, null, null));
    }
}
