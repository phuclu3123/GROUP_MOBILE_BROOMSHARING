package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.util.*;

public class AdminRepository {
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();

    private String uid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null)
            throw new IllegalStateException("Vui lòng đăng nhập lại.");
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private String collection(String kind) {
        switch (kind) {
            case "HOST":
                return "hostApplications";
            case "ROOM":
                return "rooms";
            case "REPORT":
                return "reports";
            default:
                throw new IllegalArgumentException("Nhóm duyệt không hợp lệ.");
        }
    }

    public void load(String kind, ResultCallback<List<ReviewItem>> cb) {
        final String actor, name;
        try {
            actor = uid();
            name = collection(kind);
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        db.collection("users")
                .document(actor)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        user -> {
                            if (!"ADMIN".equals(user.getString("role"))
                                    || !"ACTIVE".equals(user.getString("status"))) {
                                cb.onError(new Exception("Bạn không có quyền Admin."));
                                return;
                            }
                            db.collection(name)
                                    .whereEqualTo(
                                            "ROOM".equals(kind) ? "approvalStatus" : "status",
                                            "ROOM".equals(kind)
                                                    ? "PENDING_REVIEW"
                                                    : "REPORT".equals(kind) ? "OPEN" : "PENDING")
                                    .get(Source.SERVER)
                                    .addOnSuccessListener(
                                            rows -> {
                                                List<ReviewItem> items = new ArrayList<>();
                                                for (DocumentSnapshot d : rows) {
                                                    String title, details;
                                                    if ("HOST".equals(kind)) {
                                                        HostApplication a =
                                                                d.toObject(HostApplication.class);
                                                        title = a.getFullName();
                                                        details =
                                                                a.getPhone()
                                                                        + " • "
                                                                        + a.getEmail()
                                                                        + "\n"
                                                                        + a.getOrganizationType()
                                                                        + "\n"
                                                                        + a.getIdentityDoc();
                                                    } else if ("ROOM".equals(kind)) {
                                                        Room r = d.toObject(Room.class);
                                                        title = r.getRoomName();
                                                        details =
                                                                r.getAddress()
                                                                        + " • "
                                                                        + r.getFloor()
                                                                        + "\n"
                                                                        + r.getCapacity()
                                                                        + " người • "
                                                                        + r.getArea()
                                                                        + " m²\nGiờ: "
                                                                        + r.getPricePerHour()
                                                                        + " • Tháng: "
                                                                        + r.getPricePerMonth()
                                                                        + " • Quý: "
                                                                        + r.getPricePerQuarter()
                                                                        + "\n"
                                                                        + r.getRules();
                                                    } else {
                                                        title = d.getString("reason");
                                                        details = d.getString("description");
                                                    }
                                                    ReviewItem item =
                                                            new ReviewItem(
                                                                    kind,
                                                                    d.getId(),
                                                                    title == null
                                                                            ? d.getId()
                                                                            : title,
                                                                    (details == null ? "" : details)
                                                                            + "\n"
                                                                            + TimeUtils
                                                                                    .formatVietnam(
                                                                                            d
                                                                                                    .getDate(
                                                                                                            "createdAt")));
                                                    if ("ROOM".equals(kind)) {
                                                        Room room = d.toObject(Room.class);
                                                        if (room.getImageUrls() != null)
                                                            item.imageUrls = room.getImageUrls();
                                                    }
                                                    items.add(item);
                                                }
                                                cb.onSuccess(items);
                                            })
                                    .addOnFailureListener(cb::onError);
                        })
                .addOnFailureListener(cb::onError);
    }

    public void review(ReviewItem item, boolean approve, String reason, ResultCallback<String> cb) {
        if (!approve && HostValidation.empty(reason)) {
            cb.onError(new Exception("Vui lòng nhập lý do từ chối/bỏ qua."));
            return;
        }
        final String actor;
        final DocumentReference ref;
        try {
            actor = uid();
            ref = db.collection(collection(item.kind)).document(item.id);
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        DocumentReference audit = db.collection("auditLogs").document();
        db.runTransaction(
                        tx -> {
                            if (!actor.equals(uid()))
                                throw new IllegalStateException("Phiên đăng nhập đã thay đổi.");
                            DocumentSnapshot admin = tx.get(db.collection("users").document(actor));
                            DocumentSnapshot target = tx.get(ref);
                            if (!"ADMIN".equals(admin.getString("role"))
                                    || !"ACTIVE".equals(admin.getString("status")))
                                throw new IllegalStateException("Bạn không có quyền Admin.");
                            if (!target.exists())
                                throw new IllegalStateException("Dữ liệu không còn tồn tại.");
                            String statusField =
                                    "ROOM".equals(item.kind) ? "approvalStatus" : "status";
                            String pending =
                                    "ROOM".equals(item.kind)
                                            ? "PENDING_REVIEW"
                                            : "REPORT".equals(item.kind) ? "OPEN" : "PENDING";
                            if (!pending.equals(target.getString(statusField)))
                                throw new IllegalStateException(
                                        "Mục này đã được xử lý. Hãy tải lại.");
                            if ("HOST".equals(item.kind)) {
                                DocumentReference account =
                                        db.collection("users")
                                                .document(target.getString("applicantUid"));
                                DocumentSnapshot user = tx.get(account);
                                if (!user.exists()
                                        || !"ACTIVE".equals(user.getString("status"))
                                        || !("GUEST".equals(user.getString("role"))
                                                || "HOST".equals(user.getString("role"))))
                                    throw new IllegalStateException(
                                            "Hồ sơ người dùng không hợp lệ hoặc đã bị khóa.");
                                if (approve)
                                    tx.update(
                                            account,
                                            "role",
                                            "HOST",
                                            "updatedAt",
                                            FieldValue.serverTimestamp());
                            } else if ("ROOM".equals(item.kind) && approve) {
                                DocumentSnapshot owner =
                                        tx.get(
                                                db.collection("users")
                                                        .document(target.getString("ownerUid")));
                                if (!"HOST".equals(owner.getString("role"))
                                        || !"ACTIVE".equals(owner.getString("status")))
                                    throw new IllegalStateException(
                                            "Chủ phòng không còn quyền Host hoạt động.");
                                DocumentSnapshot building =
                                        tx.get(
                                                db.collection("buildings")
                                                        .document(target.getString("buildingId")));
                                if (!building.exists()
                                        || !target.getString("ownerUid")
                                                .equals(building.getString("ownerUid")))
                                    throw new IllegalStateException(
                                            "Tòa nhà không thuộc chủ phòng.");
                                String error =
                                        HostValidation.room(target.toObject(Room.class), true);
                                if (error != null) throw new IllegalStateException(error);
                            }
                            Map<String, Object> changes = new HashMap<>();
                            changes.put(
                                    statusField,
                                    "REPORT".equals(item.kind)
                                            ? (approve ? "RESOLVED" : "DISMISSED")
                                            : (approve ? "APPROVED" : "REJECTED"));
                            changes.put(
                                    "REPORT".equals(item.kind) ? "resolution" : "rejectionReason",
                                    approve ? "" : reason.trim());
                            changes.put("reviewedBy", actor);
                            changes.put("updatedAt", FieldValue.serverTimestamp());
                            tx.update(ref, changes);
                            Map<String, Object> log = new HashMap<>();
                            log.put("schemaVersion", 1);
                            log.put("actorUid", actor);
                            log.put("targetPath", ref.getPath());
                            log.put("action", approve ? "APPROVE" : "REJECT");
                            log.put("reason", reason.trim());
                            log.put("createdAt", FieldValue.serverTimestamp());
                            tx.set(audit, log);
                            return item.id;
                        })
                .addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(cb::onError);
    }
}
