package vn.edu.ueh.phuclu.broomsharing.repository;

import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;

public interface AuthRepository {
    // Callback: thành công - và lỗi
    interface Callback {
        void onSuccess(UserProfile profile);
        void onError(AuthError error);
    }

    // ResetPassword: thành công - và lỗi nhưng không có tham số
    interface ResetCallback {
        void onSuccess();
        void onError(AuthError error);
    }

    // Kiểm tra các thành phần Firebase cần cho xác thực và dữ liệu hồ sơ đã khởi tạo chưa
    boolean isConfigured();
    // Kiểm tra Firebase Auth hiện có người dùng đăng nhập hay không.
    boolean hasSession();
    // Tạo tài khoản mới với các thông tin ăng nhập trên => kết quả báo về bất đồng bộ callback
    void login(String identifier, String password, Callback callback);
    // Tương tự như đăng nhập
    void register(String name, String username, String email, String phone, String password, Callback callback);
    // Tải hồ sơ của người dùng đang đăng nhập, kết quả báo về bất đồng bộ callback
    void loadSession(Callback callback);
    // Đăng xuất
    void logout();
    // Yêu cầu khôi phục mật khẩu, kết quả báo về bất đồng bộ callback
    void resetPassword(String email, ResetCallback callback);
}
