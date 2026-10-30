package vn.edu.ueh.phuclu.broomsharing.ui.admin;

import androidx.lifecycle.*;

import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.repository.AdminRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;

/*
   Tải nội dung bằng chứng và giữ trạng thái xem tệp cho màn hình quản trị.
   Quyền mở tệp phụ thuộc dịch vụ lưu trữ, không suy ra từ quyền đọc Firestore.
*/
public class AdminEvidenceViewModel extends ViewModel {
    private final AdminRepository repository = new AdminRepository();
    public final MutableLiveData<UiState<byte[]>> evidence = new MutableLiveData<>();
    private String currentPath;
    private int generation;

    // Đối chiếu tệp hiện hành để Activity bỏ qua dữ liệu của tab trước.
    public boolean hasFile(String path) {
        return java.util.Objects.equals(currentPath, path);
    }

    // Giữ trạng thái tải byte của bằng chứng cho màn preview, trả lỗi khi nguồn không cho phép đọc.
    public void load(String id, String path) {
        if (hasFile(path) && evidence.getValue() != null && evidence.getValue().loading) return;
        currentPath = path;
        final int run = ++generation;
        evidence.setValue(UiState.loading());
        repository.evidence(
                id,
                path,
                new ResultCallback<byte[]>() {
                    public void onSuccess(byte[] data) {
                        if (run == generation) evidence.setValue(UiState.success(data));
                    }

                    public void onError(Exception e) {
                        if (run == generation) evidence.setValue(UiState.error(e));
                    }
                });
    }
}
