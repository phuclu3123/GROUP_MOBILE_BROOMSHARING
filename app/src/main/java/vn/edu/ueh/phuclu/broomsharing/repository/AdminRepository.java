package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.android.gms.tasks.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.storage.StorageReference;

import vn.edu.ueh.phuclu.broomsharing.BuildConfig;
import vn.edu.ueh.phuclu.broomsharing.model.AuditLog;
import vn.edu.ueh.phuclu.broomsharing.model.PublicHost;
import vn.edu.ueh.phuclu.broomsharing.model.ReviewItem;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.utils.BookingPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;
import vn.edu.ueh.phuclu.broomsharing.utils.HostValidation;
import vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.TimeUtils;

import java.util.*;

/** Admin reads and atomic writes through the Firestore Android SDK. */
public class AdminRepository {
    // Hàng đợi chỉ dành cho Admin đang ACTIVE; quyền vẫn được Rules kiểm lại lúc đọc và ghi.
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();

    public void load(String kind, ResultCallback<List<ReviewItem>> cb) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            cb.onError(new Exception("Vui lòng đăng nhập lại."));
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("users")
                .document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        user -> {
                            if (!"ADMIN".equals(user.getString("role"))
                                    || !"ACTIVE".equals(user.getString("status"))) {
                                cb.onError(new Exception("Bạn không có quyền Admin."));
                                return;
                            }
                            final Query query;
                            switch (kind) {
                                case "HOST":
                                    query =
                                            db.collection("hostApplications")
                                                    .whereEqualTo("status", "PENDING")
                                                    .limit(100);
                                    break;
                                case "ROOM":
                                    query =
                                            db.collection("rooms")
                                                    .whereEqualTo(
                                                            "approvalStatus", "PENDING_REVIEW")
                                                    .limit(100);
                                    break;
                                case "VERIFICATION":
                                    query =
                                            db.collection("propertyVerifications")
                                                    .whereEqualTo("status", "PENDING")
                                                    .limit(100);
                                    break;
                                case "HOST_STATUS":
                                    query =
                                            db.collection("users")
                                                    .whereEqualTo("role", "HOST")
                                                    .limit(200);
                                    break;
                                case "ROOM_STATUS":
                                    query = db.collection("rooms").limit(200);
                                    break;
                                case "AUDIT":
                                    query =
                                            db.collection("auditLogs")
                                                    .orderBy(
                                                            "createdAt", Query.Direction.DESCENDING)
                                                    .limit(100);
                                    break;
                                case "REPORT":
                                    query =
                                            db.collection("reports")
                                                    .whereEqualTo("status", "OPEN")
                                                    .limit(100);
                                    break;
                                default:
                                    cb.onError(new Exception("Nhóm quản trị không hợp lệ."));
                                    return;
                            }
                            query.get(Source.SERVER)
                                    .addOnSuccessListener(
                                            rows -> {
                                                try {
                                                    List<ReviewItem> result = new ArrayList<>();
                                                    List<Task<?>> extra = new ArrayList<>();
                                                    for (DocumentSnapshot d : rows) {
                                                        ReviewItem item = item(kind, d);
                                                        result.add(item);
                                                        if (("ROOM".equals(kind)
                                                                        || "ROOM_STATUS"
                                                                                .equals(kind))
                                                                && d.getString("buildingId")
                                                                        != null) {
                                                            extra.add(
                                                                    db.collection("buildings")
                                                                            .document(
                                                                                    d.getString(
                                                                                            "buildingId"))
                                                                            .get(Source.SERVER)
                                                                            .addOnSuccessListener(
                                                                                    building -> {
                                                                                        if (building
                                                                                                .exists())
                                                                                            item.details +=
                                                                                                    "\n"
                                                                                                        + "Tòa nhà:"
                                                                                                        + " "
                                                                                                            + text(
                                                                                                                    building,
                                                                                                                    "name")
                                                                                                            + "\n"
                                                                                                            + "Địa chỉ:"
                                                                                                            + " "
                                                                                                            + text(
                                                                                                                    building,
                                                                                                                    "address")
                                                                                                            + "\n"
                                                                                                            + "Quận:"
                                                                                                            + " "
                                                                                                            + text(
                                                                                                                    building,
                                                                                                                    "district")
                                                                                                            + "\n"
                                                                                                            + "Tọa độ"
                                                                                                            + " do chủ"
                                                                                                            + " cung"
                                                                                                            + " cấp:"
                                                                                                            + " "
                                                                                                            + building
                                                                                                                    .get(
                                                                                                                            "latitude")
                                                                                                            + ", "
                                                                                                            + building
                                                                                                                    .get(
                                                                                                                            "longitude")
                                                                                                            + "\n"
                                                                                                            + "Hướng"
                                                                                                            + " dẫn vào:"
                                                                                                            + " "
                                                                                                            + text(
                                                                                                                    building,
                                                                                                                    "directionGuide");
                                                                                    }));
                                                        }
                                                    }
                                                    Tasks.whenAll(extra)
                                                            .addOnSuccessListener(
                                                                    ignored -> cb.onSuccess(result))
                                                            .addOnFailureListener(cb::onError);
                                                } catch (Exception e) {
                                                    cb.onError(e);
                                                }
                                            })
                                    .addOnFailureListener(cb::onError);
                        })
                .addOnFailureListener(cb::onError);
    }

    private String text(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private ReviewItem item(String kind, DocumentSnapshot d) {
        String title, details, status = text(d, "status");
        if ("HOST".equals(kind)) {
            title = text(d, "fullName");
            details =
                    "Người nộp: "
                            + text(d, "applicantUid")
                            + "\n"
                            + text(d, "phone")
                            + " • "
                            + text(d, "email")
                            + "\n"
                            + text(d, "organizationType")
                            + "\nMô tả giấy tờ: "
                            + text(d, "identityDoc");
        } else if ("ROOM".equals(kind) || "ROOM_STATUS".equals(kind)) {
            Room r = d.toObject(Room.class);
            title = r.getRoomName();
            status = r.getListingStatus();
            details =
                    "Chủ: "
                            + r.getOwnerUid()
                            + "\n"
                            + r.getAddress()
                            + " • Tầng "
                            + r.getFloor()
                            + "\n"
                            + r.getCapacity()
                            + " người • "
                            + r.getArea()
                            + " m²"
                            + "\nDuyệt: "
                            + FormatUtils.reviewStatus(r.getApprovalStatus())
                            + " • Rao: "
                            + status
                            + "\nHình thức: "
                            + r.getActiveMode()
                            + " • Giờ mở: "
                            + r.getOpenTime()
                            + "–"
                            + r.getCloseTime()
                            + "\nNgày hoạt động: "
                            + r.getWorkingDays()
                            + " • Bước: "
                            + r.getSlotMinutes()
                            + " phút"
                            + "\nGiờ: "
                            + FormatUtils.formatCurrency(r.getPricePerHour())
                            + " • Tháng: "
                            + FormatUtils.formatCurrency(r.getPricePerMonth())
                            + " • Quý: "
                            + FormatUtils.formatCurrency(r.getPricePerQuarter())
                            + "\nCọc: "
                            + FormatUtils.formatCurrency(r.getDeposit())
                            + "\nTiện ích: "
                            + r.getAmenities()
                            + "\nNội quy: "
                            + r.getRules()
                            + "\nChính sách thuê: "
                            + r.getRentalPolicy()
                            + "\nGia hạn: "
                            + r.getRenewalPolicy()
                            + "\nTrạng thái giấy tờ: "
                            + r.getLocationVerification()
                            + " / "
                            + r.getPhotosVerification()
                            + "\nLý do quản trị: "
                            + text(d, "moderationReason");
        } else if ("HOST_STATUS".equals(kind)) {
            title = text(d, "name");
            details =
                    "UID: "
                            + d.getId()
                            + "\n"
                            + text(d, "email")
                            + " • "
                            + text(d, "phone")
                            + "\nTrạng thái: "
                            + status
                            + "\nLý do: "
                            + text(d, "moderationReason")
                            + "\nKhóa chủ chặn đặt mới; giữ nguyên lịch sử booking.";
        } else if ("VERIFICATION".equals(kind)) {
            title = "Xác minh " + verificationType(text(d, "verificationType"));
            details =
                    "Chủ: "
                            + text(d, "ownerUid")
                            + "\nPhòng: "
                            + text(d, "roomId")
                            + "\nTòa nhà: "
                            + text(d, "buildingId")
                            + "\nMô tả: "
                            + text(d, "description")
                            + "\nChỉ xét duyệt nội bộ, không gán nhãn đối tác.";
        } else if ("AUDIT".equals(kind)) {
            title = action(text(d, "action"));
            details =
                    "Người xử lý: "
                            + text(d, "actorUid")
                            + "\nĐối tượng: "
                            + text(d, "targetPath")
                            + "\nTrước: "
                            + text(d, "before")
                            + "\nSau: "
                            + text(d, "after")
                            + "\nLý do: "
                            + text(d, "reason");
        } else {
            title = text(d, "reason");
            details =
                    text(d, "description")
                            + "\n\nPhòng: "
                            + text(d, "roomId")
                            + "\nChủ: "
                            + text(d, "ownerUid")
                            + "\nNgười gửi: "
                            + text(d, "reporterUid")
                            + "\nTrạng thái: "
                            + status
                            + "\nBooking liên quan: "
                            + text(d, "bookingId")
                            + "\nPhản hồi Host: "
                            + text(d, "hostResponse")
                            + "\nKết luận: "
                            + text(d, "resolution")
                            + "\n"
                            + "Tệp bằng chứng: đang phát triển; không coi mô tả là kết luận vi"
                            + " phạm.";
        }
        ReviewItem item =
                new ReviewItem(
                        kind,
                        d.getId(),
                        title == null || title.isEmpty() ? d.getId() : title,
                        details + "\n" + TimeUtils.formatVietnam(d.getDate("createdAt")));
        item.status = status;
        if ("HOST".equals(kind)) {
            Object refs = d.get("identityImageUrls");
            if (refs instanceof List) item.evidenceRefs = (List<String>) refs;
        }
        if ("ROOM".equals(kind) || "ROOM_STATUS".equals(kind)) {
            Object urls = d.get("imageUrls");
            if (urls instanceof List) item.imageUrls = (List<String>) urls;
        }
        if ("VERIFICATION".equals(kind)) {
            Object refs = d.get("evidenceRefs");
            if (refs instanceof List) item.evidenceRefs = (List<String>) refs;
            else if (d.getString("evidencePath") != null)
                item.evidenceRefs = Collections.singletonList(d.getString("evidencePath"));
        }
        return item;
    }

    private String verificationType(String s) {
        if ("IDENTITY".equals(s)) return "danh tính";
        if ("LOCATION".equals(s)) return "địa điểm";
        if ("PHOTOS".equals(s)) return "ảnh phòng";
        return "chưa rõ loại";
    }

    private String action(String s) {
        switch (s) {
            case "APPROVE":
                return "Duyệt hồ sơ";
            case "REJECT":
                return "Từ chối hồ sơ";
            case "BLOCK_HOST":
                return "Khóa Host";
            case "UNBLOCK_HOST":
                return "Mở khóa Host";
            case "SUSPEND_ROOM":
                return "Khóa phòng";
            case "UNSUSPEND_ROOM":
                return "Gỡ khóa phòng";
            case "CREATE":
                return "Tạo booking";
            case "CONFIRMED":
                return "Xác nhận booking";
            case "CANCELLED":
                return "Hủy booking";
            case "REJECTED":
                return "Từ chối booking";
            case "CHECKED_IN":
                return "Ghi nhận vào phòng demo";
            case "COMPLETED":
                return "Hoàn tất booking";
            case "NO_SHOW":
                return "Xác nhận khách không đến";
            case "TERMINATED":
                return "Kết thúc lượt thuê do sự cố";
            case "ATTENDANCE_CONFIRMED":
                return "Xác minh sử dụng cho booking cũ";
            case "SAVE_ROOM":
                return "Sửa thông tin phòng";
            case "SET_LISTING":
                return "Đổi trạng thái nhận đặt";
            default:
                return s;
        }
    }

    public void review(
            ReviewItem item,
            boolean approve,
            String reason,
            boolean evidenceReviewed,
            ResultCallback<String> cb) {
        if ("REPORT".equals(item.kind)) {
            new ReportRepository()
                    .resolve(item.id, approve ? "RESOLVED" : "DISMISSED", "NO_ACTION", reason, cb);
            return;
        }
        final String actor;
        String note = reason == null ? "" : reason.trim();
        try {
            actor = FirebaseAuth.getInstance().getUid();
            BookingPolicy.require(actor != null, "Vui lòng đăng nhập lại.");
            BookingPolicy.id(item.id);
            BookingPolicy.require(
                    note.length() <= 1000
                            && ((approve
                                            && !"VERIFICATION".equals(item.kind)
                                            && !item.kind.endsWith("STATUS"))
                                    || !note.isEmpty()),
                    "Nhập lý do/ghi chú, tối đa 1.000 ký tự.");
        } catch (Exception error) {
            cb.onError(error);
            return;
        }
        db.runTransaction(
                        tx -> {
                            DocumentSnapshot admin = tx.get(db.collection("users").document(actor));
                            BookingPolicy.require(
                                    actor.equals(FirebaseAuth.getInstance().getUid())
                                            && "ADMIN".equals(admin.getString("role"))
                                            && "ACTIVE".equals(admin.getString("status")),
                                    "Bạn không có quyền Admin.");
                            String collection;
                            switch (item.kind) {
                                case "HOST":
                                    collection = "hostApplications";
                                    break;
                                case "ROOM":
                                case "ROOM_STATUS":
                                    collection = "rooms";
                                    break;
                                case "HOST_STATUS":
                                    collection = "users";
                                    break;
                                case "VERIFICATION":
                                    collection = "propertyVerifications";
                                    break;
                                default:
                                    throw new IllegalStateException("Nhóm duyệt không hợp lệ.");
                            }
                            DocumentReference ref = db.collection(collection).document(item.id);
                            DocumentSnapshot target = tx.get(ref);
                            BookingPolicy.require(target.exists(), "Hồ sơ không tồn tại.");
                            Map<String, Object> changes = new HashMap<>();
                            changes.put("updatedAt", FieldValue.serverTimestamp());
                            String field =
                                    "ROOM".equals(item.kind)
                                            ? "approvalStatus"
                                            : "ROOM_STATUS".equals(item.kind)
                                                    ? "listingStatus"
                                                    : "status";
                            String before = target.getString(field), after, action;
                            DocumentReference hostRef = null, verificationRoom = null;
                            DocumentSnapshot host = null;
                            if ("HOST_STATUS".equals(item.kind)) {
                                BookingPolicy.require(
                                        "HOST".equals(target.getString("role")),
                                        "Chỉ khóa/mở tài khoản Host.");
                                BookingPolicy.require(
                                        item.status.equals(before),
                                        "Trạng thái đã đổi, hãy tải lại.");
                                after = "BLOCKED".equals(before) ? "ACTIVE" : "BLOCKED";
                                action = "BLOCKED".equals(after) ? "BLOCK_HOST" : "UNBLOCK_HOST";
                                changes.put("moderationReason", note);
                                changes.put("moderatedBy", actor);
                            } else if ("ROOM_STATUS".equals(item.kind)) {
                                BookingPolicy.require(
                                        item.status.equals(before),
                                        "Trạng thái đã đổi, hãy tải lại.");
                                after = "SUSPENDED".equals(before) ? "INACTIVE" : "SUSPENDED";
                                action =
                                        "SUSPENDED".equals(after)
                                                ? "SUSPEND_ROOM"
                                                : "UNSUSPEND_ROOM";
                                if ("INACTIVE".equals(after)) {
                                    host =
                                            tx.get(
                                                    db.collection("users")
                                                            .document(
                                                                    BookingPolicy.id(
                                                                            target.getString(
                                                                                    "ownerUid"))));
                                    BookingPolicy.require(
                                            "HOST".equals(host.getString("role"))
                                                    && "ACTIVE".equals(host.getString("status")),
                                            "Cần mở khóa Host trước.");
                                }
                                changes.put("moderationReason", note);
                                changes.put("moderatedBy", actor);
                            } else {
                                String pending =
                                        "ROOM".equals(item.kind) ? "PENDING_REVIEW" : "PENDING";
                                BookingPolicy.require(
                                        pending.equals(before),
                                        "Hồ sơ đã được xử lý. Vui lòng tải lại.");
                                action = approve ? "APPROVE" : "REJECT";
                                after =
                                        "VERIFICATION".equals(item.kind)
                                                ? approve ? "VERIFIED" : "REJECTED"
                                                : approve ? "APPROVED" : "REJECTED";
                                changes.put("reviewedBy", actor);
                                changes.put("rejectionReason", note);
                                if ("HOST".equals(item.kind)) {
                                    List<String> identity =
                                            (List<String>) target.get("identityImageUrls");
                                    if (approve) {
                                        BookingPolicy.require(
                                                evidenceReviewed,
                                                "Cần mở xem cả hai mặt giấy tờ trước khi duyệt"
                                                    + " Host.");
                                        BookingPolicy.require(
                                                Long.valueOf(2)
                                                                .equals(
                                                                        target.getLong(
                                                                                "schemaVersion"))
                                                        && "DEMO_PUBLIC"
                                                                .equals(
                                                                        target.getString(
                                                                                "evidenceMode"))
                                                        && identity != null
                                                        && identity.size() == 2
                                                        && !identity.get(0).equals(identity.get(1)),
                                                "Hồ sơ thiếu hai ảnh giấy tờ. Hãy từ chối kèm yêu"
                                                    + " cầu bổ sung.");
                                        for (String url : identity)
                                            BookingPolicy.require(
                                                    vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy
                                                            .hostIdentityUrl(
                                                                    url,
                                                                    vn.edu.ueh.phuclu.broomsharing
                                                                            .BuildConfig
                                                                            .CLOUDINARY_CLOUD_NAME,
                                                                    item.id),
                                                    "Ảnh giấy tờ không thuộc đúng người nộp.");
                                        changes.put(
                                                "identityReviewedAt", FieldValue.serverTimestamp());
                                    }
                                    hostRef =
                                            db.collection("users")
                                                    .document(
                                                            BookingPolicy.id(
                                                                    target.getString(
                                                                            "applicantUid")));
                                    host = tx.get(hostRef);
                                    BookingPolicy.require(
                                            "ACTIVE".equals(host.getString("status"))
                                                    && Arrays.asList("GUEST", "HOST")
                                                            .contains(host.getString("role")),
                                            "Tài khoản xin quyền Host không hợp lệ.");
                                }
                                if ("ROOM".equals(item.kind) && approve) {
                                    host =
                                            tx.get(
                                                    db.collection("users")
                                                            .document(
                                                                    BookingPolicy.id(
                                                                            target.getString(
                                                                                    "ownerUid"))));
                                    DocumentSnapshot building =
                                            tx.get(
                                                    db.collection("buildings")
                                                            .document(
                                                                    BookingPolicy.id(
                                                                            target.getString(
                                                                                    "buildingId"))));
                                    BookingPolicy.require(
                                            "HOST".equals(host.getString("role"))
                                                    && "ACTIVE".equals(host.getString("status"))
                                                    && target.getString("ownerUid")
                                                            .equals(building.getString("ownerUid"))
                                                    && "ACTIVE"
                                                            .equals(building.getString("status")),
                                            "Chủ phòng/tòa nhà không còn hoạt động.");
                                    String error =
                                            HostValidation.room(target.toObject(Room.class), true);
                                    BookingPolicy.require(error == null, error);
                                }
                                if ("VERIFICATION".equals(item.kind)) {
                                    String type = target.getString("verificationType"),
                                            owner = target.getString("ownerUid");
                                    BookingPolicy.require(
                                            Arrays.asList("IDENTITY", "LOCATION", "PHOTOS")
                                                    .contains(type),
                                            "Loại xác minh không hợp lệ.");
                                    host =
                                            tx.get(
                                                    db.collection("users")
                                                            .document(BookingPolicy.id(owner)));
                                    BookingPolicy.require(
                                            "HOST".equals(host.getString("role"))
                                                    && "ACTIVE".equals(host.getString("status")),
                                            "Chủ hồ sơ không còn hoạt động.");
                                    if (!HostValidation.empty(target.getString("roomId"))) {
                                        verificationRoom =
                                                db.collection("rooms")
                                                        .document(
                                                                BookingPolicy.id(
                                                                        target.getString(
                                                                                "roomId")));
                                        BookingPolicy.require(
                                                owner.equals(
                                                        tx.get(verificationRoom)
                                                                .getString("ownerUid")),
                                                "Phòng không thuộc hồ sơ.");
                                    } else if (!HostValidation.empty(
                                            target.getString("buildingId")))
                                        BookingPolicy.require(
                                                owner.equals(
                                                        tx.get(
                                                                        db.collection("buildings")
                                                                                .document(
                                                                                        BookingPolicy
                                                                                                .id(
                                                                                                        target
                                                                                                                .getString(
                                                                                                                        "buildingId"))))
                                                                .getString("ownerUid")),
                                                "Tòa nhà không thuộc hồ sơ.");
                                    else
                                        BookingPolicy.require(
                                                "IDENTITY".equals(type),
                                                "Xác minh địa điểm/ảnh cần phòng hoặc tòa nhà.");
                                    if (approve) {
                                        List<String> refs = evidencePaths(target);
                                        BookingPolicy.require(
                                                evidenceReviewed
                                                        && !refs.isEmpty()
                                                        && refs.size() <= 10,
                                                "Cần xem bằng chứng trước khi duyệt.");
                                        for (String path : refs)
                                            BookingPolicy.require(
                                                    validEvidence(owner, path, item.id),
                                                    "Đường dẫn bằng chứng không hợp lệ.");
                                    }
                                    changes.put("method", "ADMIN_REVIEWED");
                                    changes.put("reviewNote", note);
                                    changes.put("reviewedAt", FieldValue.serverTimestamp());
                                    changes.put("partnerName", "");
                                }
                            }
                            // All transaction reads are complete before any write.
                            changes.put(field, after);
                            tx.update(ref, changes);
                            if ("HOST".equals(item.kind) && approve) {
                                tx.update(
                                        hostRef,
                                        "role",
                                        "HOST",
                                        "updatedAt",
                                        FieldValue.serverTimestamp());
                                tx.set(
                                        db.collection("publicHosts").document(host.getId()),
                                        new PublicHost(
                                                host.getId(),
                                                host.getString("name"),
                                                "ACTIVE",
                                                host.getString("photoUrl")));
                            }
                            if ("HOST_STATUS".equals(item.kind))
                                tx.set(
                                        db.collection("publicHosts").document(item.id),
                                        new PublicHost(
                                                item.id,
                                                target.getString("name"),
                                                after,
                                                target.getString("photoUrl")));
                            if (verificationRoom != null
                                    && approve
                                    && !"IDENTITY".equals(target.getString("verificationType")))
                                tx.update(
                                        verificationRoom,
                                        "LOCATION".equals(target.getString("verificationType"))
                                                ? "locationVerification"
                                                : "photosVerification",
                                        "ADMIN_REVIEWED");
                            tx.set(
                                    db.collection("auditLogs").document(),
                                    new AuditLog(
                                            actor,
                                            ref.getPath(),
                                            action,
                                            Collections.singletonMap(field, before),
                                            Collections.singletonMap(field, after),
                                            note));
                            return item.id;
                        })
                .addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(cb::onError);
    }

    public void evidence(String id, String path, ResultCallback<byte[]> cb) {
        if (path != null && path.contains("/broomsharing/hostIdentity/")) {
            hostEvidence(id, path, cb);
            return;
        }
        final String actor = FirebaseAuth.getInstance().getUid();
        try {
            BookingPolicy.id(id);
            BookingPolicy.require(actor != null, "Vui lòng đăng nhập lại.");
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
                            db.collection("propertyVerifications")
                                    .document(id)
                                    .get(Source.SERVER)
                                    .addOnSuccessListener(
                                            doc -> {
                                                if (!doc.exists()
                                                        || !validEvidence(
                                                                doc.getString("ownerUid"), path, id)
                                                        || !evidencePaths(doc).contains(path)) {
                                                    cb.onError(
                                                            new Exception(
                                                                    "Bằng chứng không thuộc hồ"
                                                                            + " sơ."));
                                                    return;
                                                }
                                                if (path.startsWith("https://")) {
                                                    EvidenceDownloader.download(path, actor, cb);
                                                    return;
                                                }
                                                StorageReference file =
                                                        FirebaseHelper.getStorage()
                                                                .getReference()
                                                                .child(path);
                                                file.getMetadata()
                                                        .addOnSuccessListener(
                                                                metadata -> {
                                                                    if (metadata.getSizeBytes() <= 0
                                                                            || metadata
                                                                                            .getSizeBytes()
                                                                                    > 5 * 1024
                                                                                            * 1024
                                                                            || !Arrays.asList(
                                                                                            "image/jpeg",
                                                                                            "image/png",
                                                                                            "image/webp",
                                                                                            "application/pdf")
                                                                                    .contains(
                                                                                            metadata
                                                                                                    .getContentType())) {
                                                                        cb.onError(
                                                                                new Exception(
                                                                                        "Bằng chứng"
                                                                                            + " phải"
                                                                                            + " là ảnh/PDF"
                                                                                            + " tối đa"
                                                                                            + " 5 MiB."));
                                                                        return;
                                                                    }
                                                                    file.getBytes(5 * 1024 * 1024)
                                                                            .addOnSuccessListener(
                                                                                    cb::onSuccess)
                                                                            .addOnFailureListener(
                                                                                    cb::onError);
                                                                })
                                                        .addOnFailureListener(cb::onError);
                                            })
                                    .addOnFailureListener(cb::onError);
                        })
                .addOnFailureListener(cb::onError);
    }

    private void hostEvidence(String id, String path, ResultCallback<byte[]> cb) {
        String actor = FirebaseAuth.getInstance().getUid();
        if (actor == null
                || !vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy.hostIdentityUrl(
                        path,
                        vn.edu.ueh.phuclu.broomsharing.BuildConfig.CLOUDINARY_CLOUD_NAME,
                        id)) {
            cb.onError(new IllegalArgumentException("Ảnh không thuộc hồ sơ Host này."));
            return;
        }
        db.collection("users")
                .document(actor)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        user -> {
                            if (!"ADMIN".equals(user.getString("role"))
                                    || !"ACTIVE".equals(user.getString("status"))) {
                                cb.onError(new IllegalStateException("Bạn không có quyền Admin."));
                                return;
                            }
                            db.collection("hostApplications")
                                    .document(id)
                                    .get(Source.SERVER)
                                    .addOnSuccessListener(
                                            doc -> {
                                                Object refs = doc.get("identityImageUrls");
                                                if (!actor.equals(
                                                                FirebaseAuth.getInstance().getUid())
                                                        || !doc.exists()
                                                        || !id.equals(doc.getString("applicantUid"))
                                                        || !(refs instanceof List)
                                                        || !((List<?>) refs).contains(path)) {
                                                    cb.onError(
                                                            new IllegalArgumentException(
                                                                    "Ảnh không thuộc hồ sơ Host."));
                                                    return;
                                                }
                                                EvidenceDownloader.download(path, actor, cb);
                                            })
                                    .addOnFailureListener(cb::onError);
                        })
                .addOnFailureListener(cb::onError);
    }

    @SuppressWarnings("unchecked")
    private List<String> evidencePaths(DocumentSnapshot doc) {
        Object value = doc.get("evidenceRefs");
        return value instanceof List
                ? (List<String>) value
                : doc.getString("evidencePath") == null
                        ? Collections.emptyList()
                        : Collections.singletonList(doc.getString("evidencePath"));
    }

    private boolean validEvidence(String owner, String path, String id) {
        if (owner == null || path == null || id == null) return false;
        if (path.startsWith("https://")) {
            return MediaPolicy.evidenceUrl(path, BuildConfig.CLOUDINARY_CLOUD_NAME, owner, id);
        }
        return path.startsWith("verification/" + owner + "/")
                && !path.contains("..")
                && path.split("/").length == 3;
    }
}
