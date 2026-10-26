package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.BuildConfig;
import vn.edu.ueh.phuclu.broomsharing.model.PropertyVerification;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;
import vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy;

import java.util.*;

// Lưu hồ sơ cùng URL Cloudinary của tệp demo; thông tin tài khoản vẫn thuộc Firestore.
public class PropertyVerificationRepository {
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();

    public String newId() {
        return db.collection("propertyVerifications").document().getId();
    }

    private String uid() {
        String value = FirebaseAuth.getInstance().getUid();
        if (value == null) throw new IllegalStateException("Vui lòng đăng nhập lại.");
        return value;
    }

    public void submit(String id, PropertyVerification item, ResultCallback<String> cb) {
        final String actor;
        try {
            actor = uid();
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,120}"))
                throw new IllegalArgumentException("Mã hồ sơ không hợp lệ.");
            if (!Arrays.asList("LOCATION", "PHOTOS").contains(item.verificationType))
                throw new IllegalArgumentException("Chọn loại xác minh.");
            item.description = item.description == null ? "" : item.description.trim();
            if (item.description.length() < 20 || item.description.length() > 2000)
                throw new IllegalArgumentException("Mô tả cần từ 20 đến 2.000 ký tự.");
            if (item.roomId == null) item.roomId = "";
            if (item.buildingId == null) item.buildingId = "";
            if (item.roomId.isEmpty() && item.buildingId.isEmpty())
                throw new IllegalArgumentException("Chọn phòng hoặc tòa nhà cần xác minh.");
            if (item.roomId.contains("/") || item.buildingId.contains("/"))
                throw new IllegalArgumentException("Mã tài sản không hợp lệ.");
            if (item.evidenceRefs == null) item.evidenceRefs = Collections.emptyList();
            if (item.evidenceRefs.size() > MediaPolicy.MAX_FILES)
                throw new IllegalArgumentException("Tối đa 6 tệp đính kèm.");
            for (String url : item.evidenceRefs)
                if (!MediaPolicy.evidenceUrl(url, BuildConfig.CLOUDINARY_CLOUD_NAME, actor, id))
                    throw new IllegalArgumentException(
                            "Tệp không thuộc đúng tài khoản và hồ sơ Cloudinary.");
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        String collection = item.roomId.isEmpty() ? "buildings" : "rooms";
        String target = item.roomId.isEmpty() ? item.buildingId : item.roomId;
        db.collection(collection)
                .document(target)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        asset -> {
                            if (!actor.equals(FirebaseAuth.getInstance().getUid())
                                    || !actor.equals(asset.getString("ownerUid"))) {
                                cb.onError(
                                        new IllegalStateException(
                                                "Tài sản không thuộc phiên Host hiện tại."));
                                return;
                            }
                            item.roomName =
                                    item.roomId.isEmpty() ? "" : asset.getString("roomName");
                            item.buildingName =
                                    item.roomId.isEmpty()
                                            ? asset.getString("name")
                                            : asset.getString("buildingName");
                            if (!item.roomId.isEmpty())
                                item.buildingId = asset.getString("buildingId");
                            Map<String, Object> data = new HashMap<>();
                            data.put("id", id);
                            data.put("schemaVersion", 1);
                            data.put("ownerUid", actor);
                            data.put("roomId", item.roomId);
                            data.put("buildingId", item.buildingId);
                            data.put("roomName", item.roomName == null ? "" : item.roomName);
                            data.put(
                                    "buildingName",
                                    item.buildingName == null ? "" : item.buildingName);
                            data.put("verificationType", item.verificationType);
                            data.put("description", item.description);
                            data.put("evidenceRefs", new ArrayList<>(item.evidenceRefs));
                            data.put("status", "PENDING");
                            data.put("createdAt", FieldValue.serverTimestamp());
                            data.put("updatedAt", FieldValue.serverTimestamp());
                            DocumentReference ref =
                                    db.collection("propertyVerifications").document(id);
                            ref.set(data)
                                    .addOnSuccessListener(
                                            v -> {
                                                if (actor.equals(
                                                        FirebaseAuth.getInstance().getUid()))
                                                    cb.onSuccess(id);
                                                else
                                                    cb.onError(
                                                            new IllegalStateException(
                                                                    "Phiên đăng nhập đã thay"
                                                                            + " đổi."));
                                            })
                                    .addOnFailureListener(
                                            error -> {
                                                // Nếu ack bị mất rồi gửi lại, đọc document đã có
                                                // thay vì tạo hồ sơ thứ hai.
                                                ref.get(Source.SERVER)
                                                        .addOnSuccessListener(
                                                                old -> {
                                                                    if (actor.equals(
                                                                                    FirebaseAuth
                                                                                            .getInstance()
                                                                                            .getUid())
                                                                            && old.exists()
                                                                            && actor.equals(
                                                                                    old.getString(
                                                                                            "ownerUid"))
                                                                            && item.roomId.equals(
                                                                                    old.getString(
                                                                                            "roomId"))
                                                                            && item.buildingId
                                                                                    .equals(
                                                                                            old
                                                                                                    .getString(
                                                                                                            "buildingId"))
                                                                            && item.verificationType
                                                                                    .equals(
                                                                                            old
                                                                                                    .getString(
                                                                                                            "verificationType"))
                                                                            && item.evidenceRefs
                                                                                    .equals(
                                                                                            old.get(
                                                                                                    "evidenceRefs"))
                                                                            && item.description
                                                                                    .equals(
                                                                                            old
                                                                                                    .getString(
                                                                                                            "description")))
                                                                        cb.onSuccess(id);
                                                                    else cb.onError(error);
                                                                })
                                                        .addOnFailureListener(
                                                                ignored -> cb.onError(error));
                                            });
                        })
                .addOnFailureListener(cb::onError);
    }

    public ListenerRegistration watch(ResultCallback<List<PropertyVerification>> cb) {
        String actor = uid();
        return db.collection("propertyVerifications")
                .whereEqualTo("ownerUid", actor)
                .limit(100)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (rows, error) -> {
                            if (!actor.equals(FirebaseAuth.getInstance().getUid())) return;
                            if (error != null) {
                                cb.onError(error);
                                return;
                            }
                            if (rows == null
                                    || rows.getMetadata().isFromCache()
                                    || rows.getMetadata().hasPendingWrites()) return;
                            List<PropertyVerification> result = new ArrayList<>();
                            for (DocumentSnapshot row : rows) {
                                PropertyVerification item =
                                        row.toObject(PropertyVerification.class);
                                item.id = row.getId();
                                result.add(item);
                            }
                            result.sort(
                                    (a, b) ->
                                            Long.compare(
                                                    b.createdAt == null ? 0 : b.createdAt.getTime(),
                                                    a.createdAt == null
                                                            ? 0
                                                            : a.createdAt.getTime()));
                            cb.onSuccess(result);
                        });
    }
}
