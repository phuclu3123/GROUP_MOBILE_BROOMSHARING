package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.app.Application;
import android.net.Uri;

import androidx.lifecycle.*;

import com.google.firebase.auth.FirebaseAuth;

import vn.edu.ueh.phuclu.broomsharing.BuildConfig;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy;

import java.util.*;

/*
   Đơn Host có hai mặt giấy tờ giả lập. Nhận ảnh từ picker hoặc URL, upload rồi mới gửi đơn.
   Giữ URL đã upload khi lỗi lưu Firestore để retry không tạo thêm tệp.
   Việc cấp quyền Host chỉ được thực hiện ở luồng Admin sau khi xem đủ ảnh.
*/
public class HostApplicationViewModel extends AndroidViewModel {
    public final MutableLiveData<UiState<HostApplication>> application = new MutableLiveData<>();
    public final MutableLiveData<UiState<String>> saved = new MutableLiveData<>();
    public final Uri[] selected = new Uri[2];
    private final HostRepository repository = new HostRepository();
    private final MediaRepository media;
    private List<String> uploaded = new ArrayList<>();
    private final String owner = FirebaseAuth.getInstance().getUid();

    public HostApplicationViewModel(Application app) {
        super(app);
        media = new MediaRepository(app);
    }

    public boolean busy() {
        return saved.getValue() != null && saved.getValue().loading;
    }

    public void consumeSave() {
        saved.setValue(null);
    }

    public void select(int side, Uri uri) {
        if (busy()) return;
        if (side < 0 || side > 1) throw new IllegalArgumentException("Mặt giấy tờ không hợp lệ.");
        selected[side] = uri;
        uploaded.clear();
    }

    public void loadApplication() {
        application.setValue(UiState.loading());
        repository.checkHostApplicationStatus(
                owner,
                new HostRepository.Callback<HostApplication>() {
                    public void onSuccess(HostApplication item) {
                        if (item != null
                                && item.getIdentityImageUrls() != null
                                && item.getIdentityImageUrls().size() == 2
                                && selected[0] == null
                                && selected[1] == null) {
                            selected[0] = Uri.parse(item.getIdentityImageUrls().get(0));
                            selected[1] = Uri.parse(item.getIdentityImageUrls().get(1));
                        }
                        application.setValue(UiState.success(item));
                    }

                    public void onError(Exception e) {
                        application.setValue(UiState.error(e));
                    }
                });
    }

    public void submitApplication(HostApplication item) {
        if (busy()) return;
        if (vn.edu.ueh.phuclu.broomsharing.utils.HostValidation.empty(item.getFullName())
                || !vn.edu.ueh.phuclu.broomsharing.utils.HostValidation.phone(item.getPhone())
                || item.getEmail() == null
                || !android.util.Patterns.EMAIL_ADDRESS.matcher(item.getEmail()).matches()
                || vn.edu.ueh.phuclu.broomsharing.utils.HostValidation.empty(
                        item.getIdentityDoc())) {
            saved.setValue(
                    UiState.error(
                            new IllegalArgumentException(
                                    "Nhập đầy đủ họ tên, số liên hệ, email và mô tả giấy tờ.")));
            return;
        }
        if (owner == null || !owner.equals(FirebaseAuth.getInstance().getUid())) {
            saved.setValue(
                    UiState.error(new IllegalStateException("Phiên đăng nhập đã thay đổi.")));
            return;
        }
        if (selected[0] == null || selected[1] == null || selected[0].equals(selected[1])) {
            saved.setValue(
                    UiState.error(
                            new IllegalArgumentException(
                                    "Cần chọn hai ảnh khác nhau cho mặt trước và mặt sau.")));
            return;
        }
        saved.setValue(UiState.loading());
        if (uploaded.size() == 2) {
            persist(item);
            return;
        }
        List<Uri> local = new ArrayList<>();
        List<String> ordered = new ArrayList<>();
        for (Uri uri : selected) {
            String url = uri.toString();
            if (MediaPolicy.hostIdentityUrl(url, BuildConfig.CLOUDINARY_CLOUD_NAME, owner))
                ordered.add(url);
            else {
                local.add(uri);
                ordered.add(null);
            }
        }
        media.upload(
                local,
                "hostIdentity",
                owner,
                new ResultCallback<List<String>>() {
                    public void onSuccess(List<String> urls) {
                        int next = 0;
                        for (int i = 0; i < ordered.size(); i++)
                            if (ordered.get(i) == null) ordered.set(i, urls.get(next++));
                        uploaded = ordered;
                        selected[0] = Uri.parse(ordered.get(0));
                        selected[1] = Uri.parse(ordered.get(1));
                        persist(item);
                    }

                    public void onError(Exception e) {
                        saved.setValue(UiState.error(e));
                    }
                });
    }

    private void persist(HostApplication item) {
        item.setIdentityImageUrls(new ArrayList<>(uploaded));
        item.setEvidenceMode("DEMO_PUBLIC");
        repository.submitHostApplication(
                item,
                new HostRepository.Callback<String>() {
                    public void onSuccess(String id) {
                        saved.setValue(UiState.success(id));
                    }

                    public void onError(Exception e) {
                        saved.setValue(UiState.error(e));
                    }
                });
    }

    @Override
    protected void onCleared() {
        media.close();
    }
}
