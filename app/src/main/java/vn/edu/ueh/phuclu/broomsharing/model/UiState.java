package vn.edu.ueh.phuclu.broomsharing.model;

import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestoreException;
import java.io.IOException;

/** A small observable result for Activity -> ViewModel -> Repository. */
/*
    Trạng thái tải, dữ liệu hoặc lỗi mà ViewModel phát cho View quan sát.
    Không lưu đối tượng trạng thái giao diện này xuống Firestore.
 */
public final class UiState<T> {
    public final boolean loading;
    public final T data;
    public final String error;
    public final boolean offline;
    public final long savedAt;
    public final boolean networkError;

    private UiState(boolean loading, T data, String error) {
        this(loading, data, error, false, 0);
    }

    private UiState(boolean loading, T data, String error, boolean offline, long savedAt) {
        this(loading, data, error, offline, savedAt, false);
    }

    private UiState(boolean loading, T data, String error, boolean offline, long savedAt, boolean networkError) {
        this.loading = loading;
        this.data = data;
        this.error = error;
        this.offline = offline;
        this.savedAt = savedAt;
        this.networkError = networkError;
    }

    // Bản lưu chỉ để xem; thời điểm này không phải lần xác nhận mới từ server.
    public static <T> UiState<T> cached(T data, long savedAt) {
        return new UiState<>(false, data, null, true, savedAt);
    }

    public String offlineNotice() {
        return offline ? "Ngoại tuyến • Bản lưu "
                + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.forLanguageTag("vi-VN"))
                        .format(new java.util.Date(savedAt))
                + ". Chỉ xem; kết nối mạng để cập nhật." : "";
    }

    public static <T> UiState<T> loading() {
        return new UiState<>(true, null, null);
    }

    public static <T> UiState<T> success(T data) {
        return new UiState<>(false, data, null);
    }

    public static <T> UiState<T> error(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause.getClass() == Exception.class
                || cause instanceof IllegalStateException
                || cause instanceof IllegalArgumentException
                || cause instanceof IOException) {
            return new UiState<>(false, null, cause.getMessage(), false, 0, cause instanceof IOException);
        }
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code =
                    ((FirebaseFirestoreException) e).getCode();
            if (code
                    == FirebaseFirestoreException.Code
                            .PERMISSION_DENIED)
                return new UiState<>(false, null, "Bạn chưa có quyền truy cập dữ liệu này.");
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code
                            == FirebaseFirestoreException.Code
                                    .DEADLINE_EXCEEDED)
                return new UiState<>(false, null, "Không thể kết nối. Kiểm tra mạng và thử lại.", false, 0, true);
        }
        Log.e("Repository", "Operation failed", e);
        return new UiState<>(
                false, null, "Chưa thể hoàn tất. Vui lòng thử lại hoặc liên hệ quản lý dự án.");
    }
}
