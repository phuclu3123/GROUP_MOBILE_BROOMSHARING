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
    public void load(String id, String path) {
        if (evidence.getValue() != null && evidence.getValue().loading) return;
        evidence.setValue(UiState.loading());
        repository.evidence(id, path, new ResultCallback<byte[]>() {
            public void onSuccess(byte[] data) { evidence.setValue(UiState.success(data)); }
            public void onError(Exception e) { evidence.setValue(UiState.error(e)); }
        });
    }
}
