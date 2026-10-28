package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.app.Application;
import android.net.Uri;

import androidx.lifecycle.*;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

import java.util.*;

// Giữ draft qua recreate; listener của màn cũ không được ghi đè phiên mới.
public class PropertyVerificationViewModel extends AndroidViewModel {
    private final PropertyVerificationRepository repository = new PropertyVerificationRepository();
    private final MediaRepository media;
    public final ArrayList<Uri> attachments = new ArrayList<>();
    public final ArrayList<String> uploadedRefs = new ArrayList<>();
    public String uploadOwner = "";

    public PropertyVerificationViewModel(Application app) {
        super(app);
        media = new MediaRepository(app);
    }

    public boolean busy() {
        return action.getValue() != null && action.getValue().loading;
    }

    // Đổi tệp trước khi gửi lại sẽ bỏ cache upload của draft, không gắn nhầm ảnh cũ.
    public void selectEvidence(List<Uri> selected) {
        if (busy()) return;
        media.removeOwnUrls(uploadedRefs);
        uploadedRefs.clear();
        attachments.clear();
        attachments.addAll(selected);
        uploadOwner = "";
    }

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
        if (busy()) return;
        action.setValue(UiState.loading());
        String uid = FirebaseAuth.getInstance().getUid();
        if (!Objects.equals(uploadOwner, uid)) uploadedRefs.clear();
        if (!uploadedRefs.isEmpty() || attachments.isEmpty()) {
            item.evidenceRefs = new ArrayList<>(uploadedRefs);
            persist(item);
            return;
        }
        media.upload(
                attachments,
                "verification",
                draftId,
                new ResultCallback<List<String>>() {
                    public void onSuccess(List<String> urls) {
                        uploadedRefs.clear();
                        uploadedRefs.addAll(urls);
                        uploadOwner = uid;
                        item.evidenceRefs = new ArrayList<>(urls);
                        persist(item);
                    }

                    public void onError(Exception e) {
                        action.setValue(UiState.error(e));
                    }
                });
    }

    // Lưu hồ sơ sau upload bằng Repository chung, giữ UID và loại xác minh theo contract.
    private void persist(PropertyVerification item) {
        repository.submit(
                draftId,
                item,
                new ResultCallback<String>() {
                    public void onSuccess(String id) {
                        draftId = repository.newId();
                        description = "";
                        attachments.clear();
                        uploadedRefs.clear();
                        uploadOwner = "";
                        action.setValue(UiState.success(id));
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
        media.close();
    }
}
