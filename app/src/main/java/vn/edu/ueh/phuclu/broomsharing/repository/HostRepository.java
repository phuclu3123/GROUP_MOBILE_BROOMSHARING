package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.BookingPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;
import vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule;
import vn.edu.ueh.phuclu.broomsharing.utils.HostValidation;

import java.util.*;
import java.util.function.Consumer;

/** Firebase operations only. Activities observe HostViewModel instead of owning callbacks. */
public class HostRepository {
    public interface Callback<T> extends ResultCallback<T> {}

    private final FirebaseFirestore db = FirebaseHelper.getFirestore();

    public HostRepository() {}

    public String currentUserId() {
        return uid();
    }

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
        final String actor;
        try {
            actor = uid();
            BookingPolicy.id(room.getId());
            BookingPolicy.id(room.getBuildingId());
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        db.runTransaction(
                        tx -> {
                            requireHost(tx, actor);
                            DocumentReference ref = db.collection("rooms").document(room.getId());
                            DocumentSnapshot old = tx.get(ref);
                            BookingPolicy.require(
                                    !old.exists() || actor.equals(old.getString("ownerUid")),
                                    "Phòng không thuộc tài khoản này.");
                            DocumentSnapshot building =
                                    tx.get(
                                            db.collection("buildings")
                                                    .document(room.getBuildingId()));
                            BookingPolicy.require(
                                    actor.equals(building.getString("ownerUid"))
                                            && "ACTIVE".equals(building.getString("status")),
                                    "Chọn tòa nhà đang hoạt động của bạn.");
                            if (old.exists()) {
                                List<AvailabilityMonth> months = new ArrayList<>();
                                java.time.YearMonth first =
                                        java.time.YearMonth.now(HostSchedule.ZONE);
                                for (int i = 0; i < 15; i++) {
                                    AvailabilityMonth month =
                                            tx.get(
                                                            db.collection("availabilityMonths")
                                                                    .document(
                                                                            BookingPolicy.monthKey(
                                                                                    room.getId(),
                                                                                    first
                                                                                            .plusMonths(
                                                                                                    i))))
                                                    .toObject(AvailabilityMonth.class);
                                    if (month != null) months.add(month);
                                }
                                BookingPolicy.validateRoomChange(
                                        old.toObject(Room.class),
                                        room,
                                        months,
                                        System.currentTimeMillis());
                            }
                            room.setOwnerUid(actor);
                            room.setBuildingName(building.getString("name"));
                            room.setListingStatus(
                                    "SUSPENDED".equals(old.getString("listingStatus"))
                                            ? "SUSPENDED"
                                            : "INACTIVE".equals(room.getListingStatus())
                                                    ? "INACTIVE"
                                                    : "ACTIVE");
                            room.setRejectionReason("");
                            room.setLocationVerification("UNVERIFIED");
                            room.setPhotosVerification("UNVERIFIED");
                            room.setCreatedAt(old.getDate("createdAt"));
                            room.setUpdatedAt(null);
                            Number revision = (Number) old.get("bookingRevision");
                            room.bookingRevision =
                                    (revision == null ? 0 : revision.longValue()) + 1;
                            room.lastBookingId =
                                    old.getString("lastBookingId") == null
                                            ? ""
                                            : old.getString("lastBookingId");
                            tx.set(ref, room, SetOptions.merge());
                            tx.set(
                                    db.collection("auditLogs").document(),
                                    new AuditLog(
                                            actor,
                                            ref.getPath(),
                                            "SAVE_ROOM",
                                            Collections.singletonMap(
                                                    "approvalStatus",
                                                    old.getString("approvalStatus")),
                                            Collections.singletonMap(
                                                    "approvalStatus", room.getApprovalStatus()),
                                            ""));
                            return room.getId();
                        })
                .addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(cb::onError);
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

    public void getBookings(Callback<List<Booking>> cb) {
        asHost(
                cb,
                actor ->
                        db.collection("bookings")
                                .whereEqualTo("ownerUid", actor)
                                .get(Source.SERVER)
                                .addOnSuccessListener(
                                        rows -> {
                                            List<Booking> list = new ArrayList<>();
                                            for (DocumentSnapshot d : rows) {
                                                Booking b = d.toObject(Booking.class);
                                                if (b != null) {
                                                    b.setId(d.getId());
                                                    list.add(b);
                                                }
                                            }
                                            // Sap xep moi nhat len dau neu co ngay tao
                                            Collections.sort(
                                                    list,
                                                    (a, b) -> {
                                                        if (a.getCreatedAt() == null
                                                                || b.getCreatedAt() == null)
                                                            return 0;
                                                        return b.getCreatedAt()
                                                                .compareTo(a.getCreatedAt());
                                                    });
                                            cb.onSuccess(list);
                                        })
                                .addOnFailureListener(cb::onError));
    }

    public void updateBookingStatus(
            String bookingId, String newStatus, String reason, Callback<String> cb) {
        new BookingRepository().transition(bookingId, newStatus, reason, cb);
    }

    public void toggleListingStatus(String roomId, String newStatus, Callback<String> cb) {
        final String actor;
        try {
            actor = uid();
            BookingPolicy.id(roomId);
            BookingPolicy.require(
                    Arrays.asList("ACTIVE", "INACTIVE").contains(newStatus),
                    "Chủ phòng chỉ được mở/tạm ngừng nhận đặt.");
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        db.runTransaction(
                        tx -> {
                            requireHost(tx, actor);
                            DocumentReference ref = db.collection("rooms").document(roomId);
                            DocumentSnapshot room = tx.get(ref);
                            BookingPolicy.require(
                                    actor.equals(room.getString("ownerUid")),
                                    "Phòng không thuộc tài khoản này.");
                            BookingPolicy.require(
                                    !"SUSPENDED".equals(room.getString("listingStatus")),
                                    "Phòng đang bị Admin khóa.");
                            tx.update(
                                    ref,
                                    "listingStatus",
                                    newStatus,
                                    "bookingRevision",
                                    FieldValue.increment(1),
                                    "updatedAt",
                                    FieldValue.serverTimestamp());
                            tx.set(
                                    db.collection("auditLogs").document(),
                                    new AuditLog(
                                            actor,
                                            ref.getPath(),
                                            "SET_LISTING",
                                            Collections.singletonMap(
                                                    "listingStatus",
                                                    room.getString("listingStatus")),
                                            Collections.singletonMap("listingStatus", newStatus),
                                            ""));
                            return roomId;
                        })
                .addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(cb::onError);
    }

    public void submitPropertyVerification(
            PropertyVerification verification,
            android.net.Uri fileUri,
            android.content.Context context,
            Callback<String> cb) {
        asHost(cb, actor -> {
            try {
                if (HostValidation.empty(verification.verificationType)) {
                    cb.onError(new Exception("Vui lòng chọn loại xác minh (Địa điểm hoặc Ảnh)."));
                    return;
                }
                if (HostValidation.empty(verification.roomId) && HostValidation.empty(verification.buildingId)) {
                    cb.onError(new Exception("Vui lòng chọn Phòng hoặc Tòa nhà cần xác minh."));
                    return;
                }
                verification.ownerUid = actor;
                verification.status = "PENDING";
                final String docId = db.collection("propertyVerifications").document().getId();
                verification.id = docId;

                if (fileUri != null && context != null) {
                    com.google.firebase.storage.FirebaseStorage storage = com.google.firebase.storage.FirebaseStorage.getInstance();
                    String fileName = docId + ".jpg";
                    String storagePath = "verification/" + actor + "/" + fileName;
                    com.google.firebase.storage.StorageReference ref = storage.getReference().child(storagePath);

                    try (java.io.InputStream in = context.getContentResolver().openInputStream(fileUri)) {
                        if (in == null) {
                            cb.onError(new Exception("Không thể đọc tệp đã chọn."));
                            return;
                        }
                        byte[] bytes = readAllBytes(in);
                        if (bytes.length > 5 * 1024 * 1024) {
                            cb.onError(new Exception("Tệp đính kèm không vượt quá 5MB."));
                            return;
                        }
                        com.google.firebase.storage.StorageMetadata metadata = new com.google.firebase.storage.StorageMetadata.Builder()
                                .setContentType("image/jpeg")
                                .build();
                        ref.putBytes(bytes, metadata)
                                .addOnSuccessListener(taskSnapshot -> {
                                    verification.evidencePaths = Collections.singletonList(storagePath);
                                    saveVerificationDoc(verification, docId, cb);
                                })
                                .addOnFailureListener(cb::onError);
                    } catch (Exception e) {
                        cb.onError(e);
                    }
                } else {
                    saveVerificationDoc(verification, docId, cb);
                }
            } catch (Exception e) {
                cb.onError(e);
            }
        });
    }

    private void saveVerificationDoc(PropertyVerification verification, String docId, Callback<String> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", docId);
        data.put("ownerUid", verification.ownerUid);
        data.put("roomId", verification.roomId != null ? verification.roomId : "");
        data.put("buildingId", verification.buildingId != null ? verification.buildingId : "");
        data.put("roomName", verification.roomName != null ? verification.roomName : "");
        data.put("buildingName", verification.buildingName != null ? verification.buildingName : "");
        data.put("verificationType", verification.verificationType);
        data.put("description", verification.description != null ? verification.description : "");
        data.put("evidencePaths", verification.evidencePaths != null ? verification.evidencePaths : Collections.emptyList());
        data.put("status", "PENDING");
        data.put("rejectionReason", "");
        data.put("reviewedBy", "");
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("propertyVerifications")
                .document(docId)
                .set(data)
                .addOnSuccessListener(aVoid -> cb.onSuccess(docId))
                .addOnFailureListener(cb::onError);
    }

    private byte[] readAllBytes(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    public void getPropertyVerifications(Callback<List<PropertyVerification>> cb) {
        asHost(cb, actor -> {
            db.collection("propertyVerifications")
                    .whereEqualTo("ownerUid", actor)
                    .get(Source.SERVER)
                    .addOnSuccessListener(querySnapshot -> {
                        List<PropertyVerification> list = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            PropertyVerification item = doc.toObject(PropertyVerification.class);
                            if (item != null) {
                                item.id = doc.getId();
                                list.add(item);
                            }
                        }
                        list.sort((a, b) -> {
                            if (a.createdAt == null && b.createdAt == null) return 0;
                            if (a.createdAt == null) return 1;
                            if (b.createdAt == null) return -1;
                            return b.createdAt.compareTo(a.createdAt);
                        });
                        cb.onSuccess(list);
                    })
                    .addOnFailureListener(cb::onError);
        });
    }

    public void getReportsForHost(Callback<List<ReviewItem>> cb) {
        asHost(cb, actor -> {
            db.collection("reports")
                    .whereEqualTo("ownerUid", actor)
                    .get(Source.SERVER)
                    .addOnSuccessListener(querySnapshot -> {
                        List<ReviewItem> list = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String id = doc.getId();
                            String title = doc.getString("reason");
                            String details = "Mô tả: " + (doc.getString("description") != null ? doc.getString("description") : "")
                                    + (doc.getString("resolution") != null && !doc.getString("resolution").isEmpty()
                                        ? "\nKết luận Admin: " + doc.getString("resolution") : "")
                                    + (doc.getString("hostResponse") != null && !doc.getString("hostResponse").isEmpty()
                                        ? "\nGiải trình của Host: " + doc.getString("hostResponse") : "");
                            ReviewItem item = new ReviewItem("REPORT", id, title != null ? title : id, details);
                            item.status = doc.getString("status");
                            list.add(item);
                        }
                        cb.onSuccess(list);
                    })
                    .addOnFailureListener(cb::onError);
        });
    }

    public void submitHostReportResponse(String reportId, String responseText, Callback<Void> cb) {
        asHost(cb, actor -> {
            if (reportId == null || reportId.trim().isEmpty()) {
                cb.onError(new Exception("Mã báo cáo không hợp lệ."));
                return;
            }
            if (responseText == null || responseText.trim().isEmpty()) {
                cb.onError(new Exception("Vui lòng nhập nội dung giải trình."));
                return;
            }

            Map<String, Object> update = new HashMap<>();
            update.put("hostResponse", responseText.trim());
            update.put("updatedAt", FieldValue.serverTimestamp());

            db.collection("reports")
                    .document(reportId)
                    .update(update)
                    .addOnSuccessListener(aVoid -> cb.onSuccess(null))
                    .addOnFailureListener(cb::onError);
        });
    }
}
