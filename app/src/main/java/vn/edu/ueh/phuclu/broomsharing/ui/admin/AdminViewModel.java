package vn.edu.ueh.phuclu.broomsharing.ui.admin;

import androidx.lifecycle.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import vn.edu.ueh.phuclu.broomsharing.model.ReviewItem;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.repository.AdminRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ReportRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;

/*
    Giữ dữ liệu hàng đợi và kết quả xét duyệt qua vòng đời màn hình Admin.
    View gửi ý định, Repository kiểm lại quyền và dữ liệu trước khi cập nhật.
 */
public class AdminViewModel extends ViewModel {
    private final AdminRepository repository = new AdminRepository();
    public final MutableLiveData<UiState<List<ReviewItem>>> items = new MutableLiveData<>();
    public final MutableLiveData<UiState<String>> operation = new MutableLiveData<>();
    public String selected = "HOST";
    public final Set<String> viewedEvidence = new HashSet<>();

    public boolean evidenceViewed(ReviewItem item) {
        return !item.evidenceRefs.isEmpty() && item.evidenceRefs.stream()
                .allMatch(path -> viewedEvidence.contains(item.id + ":" + path));
    }

    public void load(String kind) {
        if (operation.getValue() != null && operation.getValue().loading) return;
        selected = kind;
        items.setValue(UiState.loading());
        repository.load(
                kind,
                new ResultCallback<List<ReviewItem>>() {
                    public void onSuccess(List<ReviewItem> data) {
                        if (kind.equals(selected)) items.setValue(UiState.success(data));
                    }

                    public void onError(Exception e) {
                        if (kind.equals(selected)) items.setValue(UiState.error(e));
                    }
                });
    }

    // Chuyển ý định xét duyệt cùng căn cứ đã xem bằng chứng xuống Repository; role không được nâng ngay trên View.
    public void review(ReviewItem item, boolean approve, String reason) {
        if (operation.getValue() != null && operation.getValue().loading) return;
        operation.setValue(UiState.loading());
        repository.review(
                item,
                approve,
                reason,
                evidenceViewed(item),
                new ResultCallback<String>() {
                    public void onSuccess(String data) {
                        operation.setValue(UiState.success(data));
                    }

                    public void onError(Exception e) {
                        operation.setValue(UiState.error(e));
                    }
                });
    }
    public void resolveReport(ReviewItem item,boolean resolve,String action,String reason){
        if(operation.getValue()!=null&&operation.getValue().loading)return;
        operation.setValue(UiState.loading());new ReportRepository().resolve(item.id,resolve?"RESOLVED":"DISMISSED",action,reason,new ResultCallback<String>(){
            public void onSuccess(String value){operation.setValue(UiState.success(value));}
            public void onError(Exception e){operation.setValue(UiState.error(e));}
        });
    }
}
