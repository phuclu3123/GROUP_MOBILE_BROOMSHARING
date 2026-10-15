package vn.edu.ueh.phuclu.broomsharing.model;

/** A small observable result for Activity -> ViewModel -> Repository. */
public final class UiState<T> {
    public final boolean loading;
    public final T data;
    public final String error;

    private UiState(boolean loading, T data, String error) {
        this.loading = loading;
        this.data = data;
        this.error = error;
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
                || cause instanceof java.io.IOException) {
            return new UiState<>(false, null, cause.getMessage());
        }
        if (e instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code code =
                    ((com.google.firebase.firestore.FirebaseFirestoreException) e).getCode();
            if (code
                    == com.google.firebase.firestore.FirebaseFirestoreException.Code
                            .PERMISSION_DENIED)
                return new UiState<>(false, null, "Bạn chưa có quyền truy cập dữ liệu này.");
            if (code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE
                    || code
                            == com.google.firebase.firestore.FirebaseFirestoreException.Code
                                    .DEADLINE_EXCEEDED)
                return new UiState<>(false, null, "Không thể kết nối. Kiểm tra mạng và thử lại.");
        }
        android.util.Log.e("Repository", "Operation failed", e);
        return new UiState<>(
                false, null, "Chưa thể hoàn tất. Vui lòng thử lại hoặc liên hệ quản lý dự án.");
    }
}
