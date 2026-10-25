package vn.edu.ueh.phuclu.broomsharing.ui.host;

import androidx.lifecycle.*;

import com.google.firebase.firestore.ListenerRegistration;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

import java.util.List;

// Giữ draft qua recreate; listener của màn cũ không được ghi đè phiên mới.
public class PropertyVerificationViewModel extends ViewModel {
    private final PropertyVerificationRepository repository = new PropertyVerificationRepository();
    public final MutableLiveData<UiState<List<PropertyVerification>>> rows =
            new MutableLiveData<>();
    public final MutableLiveData<UiState<String>> action = new MutableLiveData<>();
    public String draftId = repository.newId();
    public String description = "";
    public int selectedAsset;
    public String selectedAssetKey = "";
    public boolean photos;
    private ListenerRegistration listener;
    private int generation;

    public void watch() {
        stop();
        int token = generation;
        rows.setValue(UiState.loading());
        try {
            listener =
                    repository.watch(
                            new ResultCallback<List<PropertyVerification>>() {
                                public void onSuccess(List<PropertyVerification> value) {
                                    if (token == generation) rows.setValue(UiState.success(value));
                                }

                                public void onError(Exception e) {
                                    if (token == generation) rows.setValue(UiState.error(e));
                                }
                            });
        } catch (Exception e) {
            rows.setValue(UiState.error(e));
        }
    }

    public void submit(PropertyVerification item) {
        if (action.getValue() != null && action.getValue().loading) return;
        action.setValue(UiState.loading());
        repository.submit(
                draftId,
                item,
                new ResultCallback<String>() {
                    public void onSuccess(String id) {
                        action.setValue(UiState.success(id));
                        draftId = repository.newId();
                        description = "";
                    }

                    public void onError(Exception e) {
                        action.setValue(UiState.error(e));
                    }
                });
    }

    public void stop() {
        generation++;
        if (listener != null) listener.remove();
        listener = null;
    }

    @Override
    protected void onCleared() {
        stop();
    }
}
