package vn.edu.ueh.phuclu.broomsharing.ui.profile;

import android.app.Application;
import android.net.Uri;

import androidx.lifecycle.*;

import com.google.firebase.auth.FirebaseAuth;

import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

import java.util.*;

// Giữ trạng thái upload qua recreate; chỉ bỏ ảnh cũ sau khi transaction lưu ảnh mới thành công.
public class ProfilePhotoViewModel extends AndroidViewModel {
    public final MutableLiveData<UiState<String>> photo = new MutableLiveData<>();
    private final MediaRepository media;
    private final ProfileRepository profiles = new ProfileRepository();
    private String owner;
    private String current = "";

    public ProfilePhotoViewModel(Application app) {
        super(app);
        media = new MediaRepository(app);
    }

    public void bind(String uid, String savedUrl) {
        if (Objects.equals(uid, owner)) return;
        owner = uid;
        current = savedUrl == null ? "" : savedUrl;
        photo.setValue(UiState.success(current));
    }

    public boolean busy() {
        return photo.getValue() != null && photo.getValue().loading;
    }

    public void change(Uri selected) {
        if (busy()) return;
        if (owner == null || !owner.equals(FirebaseAuth.getInstance().getUid())) {
            photo.setValue(UiState.error(new IllegalStateException("Vui lòng đăng nhập lại.")));
            return;
        }
        photo.setValue(UiState.loading());
        if (selected == null) {
            persist("", Collections.emptyList());
            return;
        }
        media.upload(
                Collections.singletonList(selected),
                "avatarMedia",
                "avatar",
                new ResultCallback<List<String>>() {
                    public void onSuccess(List<String> urls) {
                        persist(urls.get(0), urls);
                    }

                    public void onError(Exception e) {
                        photo.setValue(UiState.error(e));
                    }
                });
    }

    // Lưu URL ảnh mới trước; chỉ dọn ảnh upload gần đây khi kết quả lưu hồ sơ cho phép.
    private void persist(String url, List<String> uploaded) {
        profiles.setPhoto(
                owner,
                url,
                new ResultCallback<String>() {
                    public void onSuccess(String value) {
                        String previous = current;
                        current = value;
                        if (!previous.isEmpty() && !previous.equals(value))
                            media.removeOwnUrls(Collections.singletonList(previous));
                        photo.setValue(UiState.success(value));
                    }

                    public void onError(Exception e) {
                        media.removeOwnUrls(uploaded);
                        photo.setValue(UiState.error(e));
                    }
                });
    }

    public String current() {
        return current;
    }

    @Override
    protected void onCleared() {
        media.close();
    }
}
