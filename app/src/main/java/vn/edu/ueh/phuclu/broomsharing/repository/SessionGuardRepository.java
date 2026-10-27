package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

// Theo dõi thay đổi quyền trong phiên đang mở; không dùng dữ liệu cache để cấp lại quyền.
/*
   Nghe hồ sơ của chính UID để phát hiện khóa tài khoản hoặc thay đổi vai trò.
   Bỏ callback của phiên cũ; Security Rules vẫn kiểm quyền ở từng lần đọc/ghi.
*/
public class SessionGuardRepository {
    public ListenerRegistration watch(String uid, ResultCallback<UserProfile> cb) {
        return FirebaseHelper.getFirestore()
                .collection("users")
                .document(uid)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (doc, error) -> {
                            if (!uid.equals(FirebaseAuth.getInstance().getUid())) return;
                            if (error != null) {
                                cb.onError(error);
                                return;
                            }
                            if (doc == null
                                    || doc.getMetadata().isFromCache()
                                    || doc.getMetadata().hasPendingWrites()) return;
                            try {
                                UserProfile profile = doc.toObject(UserProfile.class);
                                if (profile == null)
                                    cb.onError(new Exception("Hồ sơ tài khoản không còn tồn tại."));
                                else cb.onSuccess(profile);
                            } catch (RuntimeException invalid) {
                                cb.onError(new IllegalStateException(
                                        "Hồ sơ tài khoản không hợp lệ. Vui lòng liên hệ Admin."));
                            }
                        });
    }
}
