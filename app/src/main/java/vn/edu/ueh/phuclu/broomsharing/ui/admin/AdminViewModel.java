package vn.edu.ueh.phuclu.broomsharing.ui.admin;

import androidx.lifecycle.*;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

import java.util.List;

public class AdminViewModel extends ViewModel {
    private final AdminRepository repository = new AdminRepository();
    public final MutableLiveData<UiState<List<ReviewItem>>> items = new MutableLiveData<>();
    public final MutableLiveData<UiState<String>> operation = new MutableLiveData<>();
    public String selected = "HOST";
    public final java.util.Set<String> viewedEvidence = new java.util.HashSet<>();

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
}
