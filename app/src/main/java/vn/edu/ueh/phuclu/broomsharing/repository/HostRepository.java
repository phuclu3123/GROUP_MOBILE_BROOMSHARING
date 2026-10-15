package vn.edu.ueh.phuclu.broomsharing.repository;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;
import vn.edu.ueh.phuclu.broomsharing.utils.HostValidation;

import java.util.*;
import java.util.function.Consumer;

/** Firebase operations only. Activities observe HostViewModel instead of owning callbacks. */
public class HostRepository {
    public interface Callback<T> extends ResultCallback<T> {}

    private final FirebaseFirestore db = FirebaseHelper.getFirestore();

    public HostRepository() {}

    public HostRepository(Context ignored) {}

    private String uid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null)
            throw new IllegalStateException("Vui lòng đăng nhập lại.");
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private <T> void asHost(Callback<T> cb, Consumer<String> operation) {
        final String actor;
        try {
            actor = uid();
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        db.collection("users")
                .document(actor)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        user -> {
                            if (!"HOST".equals(user.getString("role"))
                                    || !"ACTIVE".equals(user.getString("status"))) {
                                cb.onError(
                                        new Exception(
                                                "Tài khoản chưa được duyệt Host hoặc đã bị khóa."));
                                return;
                            }
                            if (FirebaseAuth.getInstance().getCurrentUser() == null
                                    || !actor.equals(
                                            FirebaseAuth.getInstance().getCurrentUser().getUid())) {
                                cb.onError(
                                        new IllegalStateException("Phiên đăng nhập đã thay đổi."));
                                return;
                            }
                            operation.accept(actor);
                        })
                .addOnFailureListener(cb::onError);
    }

    private void requireHost(Transaction tx, String actor) throws FirebaseFirestoreException {
        if (!actor.equals(uid()))
            throw new IllegalStateException("Phiên đăng nhập đã thay đổi. Vui lòng thử lại.");
        DocumentSnapshot user = tx.get(db.collection("users").document(actor));
        if (!"HOST".equals(user.getString("role")) || !"ACTIVE".equals(user.getString("status")))
            throw new IllegalStateException("Bạn không có quyền Host.");
    }

    public String newRoomId() {
        return db.collection("rooms").document().getId();
    }

    public String newBuildingId() {
        return db.collection("buildings").document().getId();
    }

    public void submitHostApplication(HostApplication app, Callback<String> cb) {
        final String actor;
        try {
            actor = uid();
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        if (HostValidation.empty(app.getFullName())
                || !HostValidation.phone(app.getPhone())
                || !android.util.Patterns.EMAIL_ADDRESS
                        .matcher(app.getEmail() == null ? "" : app.getEmail())
                        .matches()
                || HostValidation.empty(app.getIdentityDoc())) {
            cb.onError(
                    new Exception(
                            "Nhập đầy đủ họ tên, điện thoại hợp lệ, email và mô tả giấy tờ."));
            return;
        }
        checkHostApplicationStatus(
                actor,
                new Callback<HostApplication>() {
                    @Override
                    public void onSuccess(HostApplication previous) {
                        if (previous != null
                                && !HostApplication.STATUS_REJECTED.equals(previous.getStatus())) {
                            cb.onError(
                                    new Exception(
                                            "Bạn đã có hồ sơ chờ duyệt hoặc đã được duyệt. Hãy kiểm"
                                                    + " tra trạng thái."));
                            return;
                        }
                        DocumentReference ref = db.collection("hostApplications").document(actor);
                        app.setId(actor);
                        app.setApplicantUid(actor);
                        app.setStatus("PENDING");
                        app.setRejectionReason("");
                        app.setUpdatedAt(null);
                        db.runTransaction(
                                        tx -> {
                                            if (!actor.equals(uid()))
                                                throw new IllegalStateException(
                                                        "Phiên đăng nhập đã thay đổi.");
                                            DocumentSnapshot user =
                                                    tx.get(db.collection("users").document(actor));
                                            DocumentSnapshot old = tx.get(ref);
                                            if (!"GUEST".equals(user.getString("role"))
                                                    || !"ACTIVE".equals(user.getString("status")))
                                                throw new IllegalStateException(
                                                        "Chỉ tài khoản khách đang hoạt động mới cần"
                                                                + " nộp hồ sơ Host.");
                                            if (old.exists()
                                                    && !"REJECTED".equals(old.getString("status")))
                                                throw new IllegalStateException(
                                                        "Hồ sơ đã được gửi trước đó.");
                                            tx.set(ref, app);
                                            return actor;
                                        })
                                .addOnSuccessListener(cb::onSuccess)
                                .addOnFailureListener(cb::onError);
                    }

                    @Override
                    public void onError(Exception e) {
                        cb.onError(e);
                    }
                });
    }

    public void checkHostApplicationStatus(String applicantUid, Callback<HostApplication> cb) {
        try {
            if (!uid().equals(applicantUid))
                throw new IllegalStateException("Không được xem hồ sơ của người khác.");
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        db.collection("hostApplications")
                .whereEqualTo("applicantUid", applicantUid)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        rows -> {
                            HostApplication latest = null;
                            for (DocumentSnapshot d : rows) {
                                HostApplication a = d.toObject(HostApplication.class);
                                if (a == null) continue;
                                a.setId(d.getId());
                                Date date =
                                        a.getUpdatedAt() != null
                                                ? a.getUpdatedAt()
                                                : a.getCreatedAt();
                                Date before =
                                        latest == null
                                                ? null
                                                : latest.getUpdatedAt() != null
                                                        ? latest.getUpdatedAt()
                                                        : latest.getCreatedAt();
                                if (latest == null
                                        || (date != null && (before == null || date.after(before))))
                                    latest = a;
                            }
                            cb.onSuccess(latest);
                        })
                .addOnFailureListener(cb::onError);
    }

    public void addBuilding(Building building, Callback<String> cb) {
        String error = HostValidation.building(building);
        if (error != null) {
            cb.onError(new Exception(error));
            return;
        }
        asHost(
                cb,
                actor -> {
                    if (HostValidation.empty(building.getId())) building.setId(newBuildingId());
                    DocumentReference ref = db.collection("buildings").document(building.getId());
                    db.runTransaction(
                                    tx -> {
                                        requireHost(tx, actor);
                                        DocumentSnapshot old = tx.get(ref);
                                        if (old.exists()
                                                && !actor.equals(old.getString("ownerUid")))
                                            throw new IllegalStateException(
                                                    "Tòa nhà không thuộc tài khoản của bạn.");
                                        building.setOwnerUid(actor);
                                        building.setUpdatedAt(null);
                                        if (old.exists()) {
                                            building.setCreatedAt(old.getDate("createdAt"));
                                            building.setStatus(old.getString("status"));
                                        }
                                        tx.set(ref, building);
                                        return ref.getId();
                                    })
                            .addOnSuccessListener(cb::onSuccess)
                            .addOnFailureListener(cb::onError);
                });
    }

    public void getBuildingsByOwner(String ownerUid, Callback<List<Building>> cb) {
        asHost(
                cb,
                actor -> {
                    if (!actor.equals(ownerUid)) {
                        cb.onError(new Exception("Không được xem tòa nhà của chủ khác."));
                        return;
                    }
                    db.collection("buildings")
                            .whereEqualTo("ownerUid", actor)
                            .get(Source.SERVER)
                            .addOnSuccessListener(
                                    rows -> {
                                        List<Building> result = new ArrayList<>();
                                        for (DocumentSnapshot d : rows) {
                                            Building b = d.toObject(Building.class);
                                            if (b != null) {
                                                b.setId(d.getId());
                                                result.add(b);
                                            }
                                        }
                                        cb.onSuccess(result);
                                    })
                            .addOnFailureListener(cb::onError);
                });
    }

    public void saveRoom(Room room, Callback<String> cb) {
        if (!Room.STATUS_DRAFT.equals(room.getApprovalStatus())
                && !Room.STATUS_PENDING_REVIEW.equals(room.getApprovalStatus())) {
            cb.onError(new Exception("Chủ phòng chỉ được lưu nháp hoặc gửi duyệt."));
            return;
        }
        String error =
                HostValidation.room(
                        room, Room.STATUS_PENDING_REVIEW.equals(room.getApprovalStatus()));
        if (error != null) {
            cb.onError(new Exception(error));
            return;
        }
        asHost(
                cb,
                actor -> {
                    if (HostValidation.empty(room.getId())) room.setId(newRoomId());
                    DocumentReference ref = db.collection("rooms").document(room.getId());
                    db.runTransaction(
                                    tx -> {
                                        requireHost(tx, actor);
                                        DocumentSnapshot building =
                                                tx.get(
                                                        db.collection("buildings")
                                                                .document(room.getBuildingId()));
                                        DocumentSnapshot old = tx.get(ref);
                                        if (!building.exists()
                                                || !actor.equals(building.getString("ownerUid")))
                                            throw new IllegalStateException(
                                                    "Vui lòng chọn tòa nhà thuộc tài khoản của"
                                                            + " bạn.");
                                        if (old.exists()
                                                && !actor.equals(old.getString("ownerUid")))
                                            throw new IllegalStateException(
                                                    "Phòng không thuộc tài khoản của bạn.");
                                        room.setOwnerUid(actor);
                                        room.setBuildingName(building.getString("name"));
                                        room.setUpdatedAt(null);
                                        room.setRejectionReason("");
                                        if (old.exists()) {
                                            room.setCreatedAt(old.getDate("createdAt"));
                                            room.setListingStatus(old.getString("listingStatus"));
                                        }
                                        tx.set(ref, room);
                                        return ref.getId();
                                    })
                            .addOnSuccessListener(cb::onSuccess)
                            .addOnFailureListener(cb::onError);
                });
    }

    public void getRoomsByOwner(String ownerUid, Callback<List<Room>> cb) {
        asHost(
                cb,
                actor -> {
                    if (!actor.equals(ownerUid)) {
                        cb.onError(new Exception("Không được xem phòng của chủ khác."));
                        return;
                    }
                    readRooms(db.collection("rooms").whereEqualTo("ownerUid", actor), cb);
                });
    }

    private void readRooms(Query query, Callback<List<Room>> cb) {
        query.get(Source.SERVER)
                .addOnSuccessListener(
                        rows -> {
                            List<Room> result = new ArrayList<>();
                            for (DocumentSnapshot d : rows) {
                                Room r = d.toObject(Room.class);
                                if (r != null) {
                                    r.setId(d.getId());
                                    result.add(r);
                                }
                            }
                            cb.onSuccess(result);
                        })
                .addOnFailureListener(cb::onError);
    }

    public void getStats(Callback<HostStats> cb) {
        asHost(
                cb,
                actor ->
                        db.collection("rooms")
                                .whereEqualTo("ownerUid", actor)
                                .get(Source.SERVER)
                                .addOnSuccessListener(
                                        rooms -> {
                                            HostStats stats = new HostStats();
                                            stats.totalRooms = rooms.size();
                                            for (DocumentSnapshot r : rooms) {
                                                if ("PENDING_REVIEW"
                                                        .equals(r.getString("approvalStatus")))
                                                    stats.pendingRooms++;
                                                if ("APPROVED".equals(r.getString("approvalStatus"))
                                                        && "ACTIVE"
                                                                .equals(
                                                                        r.getString(
                                                                                "listingStatus")))
                                                    stats.activeRooms++;
                                            }
                                            db.collection("bookings")
                                                    .whereEqualTo("ownerUid", actor)
                                                    .whereEqualTo("status", "PENDING")
                                                    .get(Source.SERVER)
                                                    .addOnSuccessListener(
                                                            bookings -> {
                                                                stats.bookingRequests =
                                                                        bookings.size();
                                                                cb.onSuccess(stats);
                                                            })
                                                    .addOnFailureListener(cb::onError);
                                        })
                                .addOnFailureListener(cb::onError));
    }
}
