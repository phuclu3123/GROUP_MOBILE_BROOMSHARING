package vn.edu.ueh.phuclu.broomsharing.ui.report;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.repository.ReportRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;

import java.util.UUID;

/**
 * Quản lý trạng thái gửi báo cáo phòng và kết quả xử lý cho giao diện. Gọi ReportRepository để ghi
 * dữ liệu.
 */
public class ReportViewModel extends ViewModel {
    // Trạng thái gửi báo cáo: đang xử lý, thành công hoặc có lỗi.
    public final MutableLiveData<UiState<String>> result = new MutableLiveData<>();

    // Giữ nội dung đang nhập khi Activity được tạo lại.
    public String draft = "";
    public int selectedReason;

    private final String id = UUID.randomUUID().toString();
    private final ReportRepository repository = new ReportRepository();

    public void submit(String room, String reason, String note) {
        submit(room, null, reason, note);
    }

    public void submit(String room, String bookingId, String reason, String note) {
        // Không gửi tiếp khi đang xử lý hoặc báo cáo này đã được gửi thành công.
        if (result.getValue() != null
                && (result.getValue().loading || result.getValue().data != null)) return;

        result.setValue(UiState.loading());
        repository.submit(
                id,
                room,
                bookingId,
                reason,
                note,
                new ResultCallback<String>() {
                    public void onSuccess(String id) {
                        result.setValue(UiState.success(id));
                    }

                    public void onError(Exception e) {
                        result.setValue(UiState.error(e));
                    }
                });
    }
}
