package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.BuildConfig;
import vn.edu.ueh.phuclu.broomsharing.model.PublicHost;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.util.*;

/*
   Cập nhật ảnh của chính tài khoản đang hoạt động, đồng bộ PublicHost trong cùng transaction.
   Chỉ nhận URL Cloudinary đúng cloud/UID; không cho thay role hoặc trạng thái qua hồ sơ cá nhân.
*/
public class ProfileRepository {
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();

    public void setPhoto(String expectedUid, String url, ResultCallback<String> cb) {
        if (expectedUid == null
                || !expectedUid.equals(FirebaseAuth.getInstance().getUid())
                || url == null
                || !url.isEmpty()
                        && !MediaPolicy.avatarUrl(
                                url, BuildConfig.CLOUDINARY_CLOUD_NAME, expectedUid)) {
            cb.onError(new IllegalArgumentException("Ảnh hoặc phiên đăng nhập không hợp lệ."));
            return;
        }
        DocumentReference ref = db.collection("users").document(expectedUid);
        db.runTransaction(
                        tx -> {
                            if (!expectedUid.equals(FirebaseAuth.getInstance().getUid()))
                                throw new IllegalStateException("Phiên đăng nhập đã thay đổi.");
                            DocumentSnapshot user = tx.get(ref);
                            if (!"ACTIVE".equals(user.getString("status")))
                                throw new IllegalStateException("Tài khoản không còn hoạt động.");
                            tx.update(
                                    ref,
                                    "photoUrl",
                                    url,
                                    "updatedAt",
                                    FieldValue.serverTimestamp());
                            if ("HOST".equals(user.getString("role")))
                                tx.set(
                                        db.collection("publicHosts").document(expectedUid),
                                        new PublicHost(
                                                expectedUid,
                                                user.getString("name"),
                                                user.getString("status"),
                                                url));
                            return url;
                        })
                .addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(
                        error -> {
                            // Nếu chỉ mất ack, giữ URL đã lưu để tránh dọn nhầm ảnh đang được sử
                            // dụng.
                            ref.get(Source.SERVER)
                                    .addOnSuccessListener(
                                            doc -> {
                                                if (expectedUid.equals(
                                                                FirebaseAuth.getInstance().getUid())
                                                        && url.equals(doc.getString("photoUrl")))
                                                    cb.onSuccess(url);
                                                else cb.onError(error);
                                            })
                                    .addOnFailureListener(ignored -> cb.onError(error));
                        });
    }
}
