package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.model.AuditLog;
import vn.edu.ueh.phuclu.broomsharing.model.AvailabilityMonth;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.PublicHost;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.utils.BookingPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.BookingResolutionPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;
import vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule;

import java.time.*;
import java.time.Instant;
import java.util.*;
import java.util.Objects;

/** Firestore Android SDK only. Reads precede writes in every transaction. */
/*
   Tạo và chuyển trạng thái booking bằng transaction của Firestore Android SDK.
   Luôn đọc đủ hồ sơ, phòng và lịch trước khi ghi; audit và revision phòng đi cùng lần ghi.
*/
public class BookingRepository {
    // Firebase SDK dùng để đọc/ghi Firestore và lấy tài khoản đang đăng nhập.
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public BookingRepository() {
        // Dùng cấu hình Firebase thật của ứng dụng.
        this(FirebaseHelper.getFirestore(), FirebaseAuth.getInstance());
    }

    // Constructor nhận dependency riêng, hữu ích khi khởi tạo Repository với cấu hình khác.
    public BookingRepository(FirebaseFirestore database, FirebaseAuth authentication) {
        db = database;
        auth = authentication;
    }

    private String actor() {
        // UID của người đang đăng nhập; không có UID thì không cho chạy nghiệp vụ.
        String uid = auth.getUid();
        BookingPolicy.require(uid != null, "Vui lòng đăng nhập lại.");
        return uid;
    }

    private DocumentSnapshot user(Transaction tx, String uid, String... roles)
            throws FirebaseFirestoreException {
        // Đảm bảo UID truyền vào vẫn là tài khoản hiện tại và đọc hồ sơ trong transaction.
        BookingPolicy.require(uid.equals(actor()), "Phiên đăng nhập đã thay đổi.");
        DocumentSnapshot user = tx.get(db.collection("users").document(uid));

        // Chỉ tài khoản ACTIVE có một trong các role được phép mới tiếp tục.
        BookingPolicy.require(
                "ACTIVE".equals(user.getString("status"))
                        && Arrays.asList(roles).contains(user.getString("role")),
                "Tài khoản không có quyền hoặc đã bị khóa.");
        return user;
    }

    private Room room(Transaction tx, String roomId, String uid) throws FirebaseFirestoreException {
        // Đọc và kiểm tra phòng trong transaction để dùng dữ liệu mới nhất trước khi đặt.
        DocumentSnapshot doc = tx.get(db.collection("rooms").document(BookingPolicy.id(roomId)));
        Room room = doc.toObject(Room.class);
        BookingPolicy.require(
                room != null
                        && "APPROVED".equals(room.getApprovalStatus())
                        && "ACTIVE".equals(room.getListingStatus()),
                "Phòng hiện không nhận đặt.");
        room.setId(doc.getId());

        // Không cho tự đặt phòng của mình; đồng thời xác nhận Host và tòa nhà vẫn hoạt động.
        BookingPolicy.require(!uid.equals(room.getOwnerUid()), "Không thể tự đặt phòng của mình.");
        PublicHost host =
                tx.get(db.collection("publicHosts").document(BookingPolicy.id(room.getOwnerUid())))
                        .toObject(PublicHost.class);
        BookingPolicy.require(
                host != null && "ACTIVE".equals(host.status),
                "Chủ phòng chưa có hồ sơ công khai hoặc đã ngừng hoạt động.");
        DocumentSnapshot building =
                tx.get(db.collection("buildings").document(BookingPolicy.id(room.getBuildingId())));
        BookingPolicy.require(
                "ACTIVE".equals(building.getString("status"))
                        && room.getOwnerUid().equals(building.getString("ownerUid")),
                "Tòa nhà không còn hoạt động.");
        return room;
    }

    private List<AvailabilityMonth> months(Transaction tx, Booking booking)
            throws FirebaseFirestoreException {
        // Lấy các tài liệu lịch tháng mà booking cần chiếm hoặc cần giải phóng.
        List<AvailabilityMonth> months = new ArrayList<>();
        for (String key : booking.monthKeys) {
            DocumentSnapshot doc =
                    tx.get(db.collection("availabilityMonths").document(BookingPolicy.id(key)));
            AvailabilityMonth month = doc.toObject(AvailabilityMonth.class);
            if (month == null) {
                // Chưa có tài liệu tháng thì tạo đối tượng rỗng trong bộ nhớ để xử lý.
                month = new AvailabilityMonth();
                month.roomId = booking.roomId;
                month.monthIndex = Integer.parseInt(key.substring(key.lastIndexOf('_') + 1));
            }
            BookingPolicy.require(
                    booking.roomId.equals(month.roomId), "Dữ liệu lịch không khớp phòng.");
            months.add(month);
        }
        return months;
    }

    private void audit(
            Transaction tx, String uid, String bookingId, String from, String to, String reason) {
        // Ghi lại ai đã tạo/chuyển trạng thái booking và trạng thái trước/sau.
        tx.set(
                db.collection("auditLogs")
                        .document(
                                "booking_"
                                        + bookingId
                                        + "_"
                                        + (from.equals(to)
                                                ? "ATTENDANCE_CONFIRMED"
                                                : from.isEmpty() ? "CREATE" : to)),
                new AuditLog(
                        uid,
                        "bookings/" + bookingId,
                        from.equals(to) ? "ATTENDANCE_CONFIRMED" : from.isEmpty() ? "CREATE" : to,
                        Collections.singletonMap("status", from),
                        Collections.singletonMap("status", to),
                        reason));
    }

    public void quote(Booking request, ResultCallback<Booking> callback) {
        final String uid;
        try {
            // Xác thực phiên trước khi bắt đầu transaction.
            uid = actor();
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        db.runTransaction(
                        tx -> {
                            // Kiểm tra quyền, phòng, giá và lịch trong cùng một transaction.
                            user(tx, uid, "GUEST", "HOST");
                            Booking booking =
                                    BookingPolicy.plan(
                                            room(tx, request.roomId, uid),
                                            request,
                                            System.currentTimeMillis());
                            booking.id = "quote";
                            List<AvailabilityMonth> months = months(tx, booking);

                            // Reserve chỉ tính/xác nhận khả năng giữ lịch trong bộ nhớ ở bước báo
                            // giá.
                            // quote() không ghi booking hoặc lịch xuống Firestore.
                            for (AvailabilityMonth month : months)
                                BookingPolicy.reserve(month, booking);
                            return booking;
                        })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    public void create(Booking request, ResultCallback<String> callback) {
        // Lần đầu tạo booking cho phép thử lại một lần nếu Rules từ chối do lịch vừa đổi.
        create(request, callback, true);
    }

    private void create(
            Booking request, ResultCallback<String> callback, boolean refreshAfterDenial) {
        final String uid, bookingId, fingerprint;
        try {
            // Kiểm tra đăng nhập và dữ liệu nhận diện trước khi mở transaction.
            uid = actor();
            BookingPolicy.id(request.requestKey);
            BookingPolicy.require(request.startAt != null, "Chọn thời gian bắt đầu.");
            bookingId = BookingPolicy.hash(uid + ":" + request.requestKey);
            fingerprint = BookingPolicy.fingerprint(request);
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        db.runTransaction(
                        tx -> {
                            // Đọc hồ sơ Guest và booking theo ID xác định từ UID + requestKey.
                            DocumentSnapshot guest = user(tx, uid, "GUEST", "HOST");
                            DocumentReference ref = db.collection("bookings").document(bookingId);
                            DocumentSnapshot previous = tx.get(ref);
                            if (previous.exists()) {
                                // Nếu cùng requestKey và cùng nội dung thì trả lại ID cũ
                                // (idempotent).
                                BookingPolicy.require(
                                        fingerprint.equals(
                                                previous.getString("requestFingerprint")),
                                        "Mã gửi lại đã dùng cho yêu cầu khác.");
                                return bookingId;
                            }

                            // Đọc lại phòng và tạo booking theo giá/chính sách hiện tại.
                            Room room = room(tx, request.roomId, uid);
                            Booking booking =
                                    BookingPolicy.plan(room, request, System.currentTimeMillis());
                            booking.id = bookingId;
                            booking.guestUid = uid;
                            booking.guestName = guest.getString("name");
                            booking.guestPhone = guest.getString("phone");
                            booking.requestFingerprint = fingerprint;
                            List<AvailabilityMonth> months = months(tx, booking);

                            // Đánh dấu lịch bận trong bộ nhớ; nếu xung đột thì transaction thất
                            // bại.
                            for (AvailabilityMonth month : months)
                                BookingPolicy.reserve(month, booking);

                            // Ghi các tháng lịch, booking, phiên bản lịch của phòng và audit cùng
                            // lúc.
                            for (int i = 0; i < months.size(); i++)
                                tx.set(
                                        db.collection("availabilityMonths")
                                                .document(booking.monthKeys.get(i)),
                                        months.get(i));
                            tx.set(ref, booking);
                            tx.update(
                                    db.collection("rooms").document(room.getId()),
                                    "activeBookingCount",
                                    room.activeBookingCount < 0 ? -1 : room.activeBookingCount + 1,
                                    "bookingRevision",
                                    FieldValue.increment(1),
                                    "lastBookingId",
                                    bookingId);
                            audit(tx, uid, bookingId, "", "PENDING", "");
                            return bookingId;
                        })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(
                        error -> {
                            // Nếu Rules từ chối vì lịch vừa đổi, đọc lại dữ liệu một lần để trả lỗi
                            // xung đột chính xác; lần thử lại vẫn phải tuân theo Rules.
                            if (refreshAfterDenial
                                    && error instanceof FirebaseFirestoreException
                                    && ((FirebaseFirestoreException) error).getCode()
                                            == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                create(request, callback, false);
                            } else callback.onError(error);
                        });
    }

    public void transition(
            String id, String target, String reason, ResultCallback<String> callback) {
        // Phiên bản không có phần trăm hoàn dự kiến gọi overload đầy đủ với null.
        transition(id, target, reason, null, callback);
    }

    public void transition(
            String id,
            String target,
            String reason,
            Integer expectedRefundPercent,
            ResultCallback<String> callback) {
        final String uid;
        try {
            // Chỉ tài khoản đăng nhập mới được chuyển trạng thái booking.
            uid = actor();
            BookingPolicy.id(id);
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        String note = reason == null ? "" : reason.trim();
        db.runTransaction(
                        tx -> {
                            // Khi hủy, Guest hoặc Host tham gia booking có thể yêu cầu;
                            // các chuyển trạng thái khác chỉ do Host xử lý.
                            DocumentSnapshot actorProfile = user(tx, uid, "GUEST", "HOST", "ADMIN");
                            DocumentReference ref = db.collection("bookings").document(id);
                            Booking booking = tx.get(ref).toObject(Booking.class);
                            BookingPolicy.require(booking != null, "Không tìm thấy booking.");
                            booking.id = id;
                            // Kiểm tra người yêu cầu có phải Guest/Host sở hữu booking không.
                            boolean admin = "ADMIN".equals(actorProfile.getString("role"));
                            boolean expired = "EXPIRED".equals(target);
                            boolean participant =
                                    uid.equals(booking.guestUid) || uid.equals(booking.ownerUid);
                            BookingPolicy.require(
                                    admin
                                            || ((expired || "CANCELLED".equals(target))
                                                    ? participant
                                                    : uid.equals(booking.ownerUid)),
                                    "Không được xử lý booking này.");
                            if (target.equals(booking.status)
                                    && !(admin
                                            && "COMPLETED".equals(target)
                                            && booking.usageConfirmedAt == null)) return id;
                            boolean blockedOwner = false;
                            if (admin && !expired) {
                                DocumentSnapshot owner =
                                        tx.get(db.collection("users").document(booking.ownerUid));
                                blockedOwner = "BLOCKED".equals(owner.getString("status"));
                            }
                            // Kiểm tra chuyển trạng thái có hợp lệ theo domain và thời điểm không.
                            BookingResolutionPolicy.validate(
                                    booking,
                                    target,
                                    note,
                                    admin,
                                    blockedOwner,
                                    System.currentTimeMillis());
                            int refundPercent = 0;
                            if ("CANCELLED".equals(target)) {
                                // Hủy vẫn nhả lịch; chỉ ghi khoản cần xử lý, chưa gọi API hoàn
                                // tiền.
                                if (uid.equals(booking.ownerUid) || admin)
                                    BookingPolicy.require(!note.isEmpty(), "Cần nhập lý do hủy.");
                                BookingPolicy.require(
                                        Arrays.asList(
                                                        "UNAVAILABLE",
                                                        "DEMO_PENDING",
                                                        "UNPAID",
                                                        "PENDING",
                                                        "PAID",
                                                        "REVIEW_REQUIRED")
                                                .contains(booking.paymentStatus),
                                        "Giao dịch cần Admin đối soát trước khi xử lý booking.");
                                if (CancellationPolicy.versioned(booking)) {
                                    refundPercent =
                                            (uid.equals(booking.ownerUid) || admin)
                                                    ? 100
                                                    : CancellationPolicy.percent(
                                                            booking, System.currentTimeMillis());
                                    // So sánh mức Guest vừa xác nhận với chính sách hiện tại.
                                    // Nếu thời hạn đã qua mốc, yêu cầu mở lại hộp thoại xác nhận.
                                    BookingPolicy.require(
                                            expectedRefundPercent == null
                                                    || expectedRefundPercent == refundPercent,
                                            "Đã qua mốc chính sách. Vui lòng mở lại xác nhận hủy để"
                                                    + " xem mức hoàn hiện tại.");
                                }
                            }
                            BookingPolicy.require(
                                    booking.schemaVersion == Booking.CURRENT_SCHEMA_VERSION
                                            && !booking.monthKeys.isEmpty(),
                                    "Booking mẫu cũ chưa có lịch Java; không thể tự thay đổi giữ"
                                            + " chỗ.");
                            DocumentReference roomRef =
                                    db.collection("rooms")
                                            .document(BookingPolicy.id(booking.roomId));
                            Room room = tx.get(roomRef).toObject(Room.class);
                            BookingPolicy.require(
                                    room != null && booking.ownerUid.equals(room.getOwnerUid()),
                                    "Phòng/Host không còn khớp booking.");
                            if ("CONFIRMED".equals(target))
                                BookingPolicy.require(
                                        "APPROVED".equals(room.getApprovalStatus())
                                                && "ACTIVE".equals(room.getListingStatus()),
                                        "Phòng không còn nhận đặt.");
                            boolean heldBefore = BookingResolutionPolicy.holds(booking.status);
                            List<AvailabilityMonth> months =
                                    heldBefore ? months(tx, booking) : Collections.emptyList();
                            // Từ chối, hủy hoặc hoàn tất sẽ giải phóng khoảng lịch đã giữ.
                            boolean release =
                                    heldBefore && BookingResolutionPolicy.terminal(target);
                            BookingPolicy.require(
                                    !release || room.activeBookingCount != 0,
                                    "Số booking giữ lịch không khớp. Cần Admin đối soát trước khi"
                                            + " tiếp tục.");
                            for (AvailabilityMonth month : months) {
                                if (release) BookingPolicy.release(month, booking);
                                else if ("FIXED".equals(booking.activeMode))
                                    BookingPolicy.require(
                                            id.equals(month.fixedBookingId), "Mất giữ chỗ.");
                                else
                                    BookingPolicy.require(
                                            booking.slotKeys.equals(month.hourlyBookings.get(id)),
                                            "Mất giữ chỗ.");
                            }
                            if (release)
                                // Cập nhật các tài liệu lịch cùng transaction với booking.
                                for (int i = 0; i < months.size(); i++)
                                    tx.set(
                                            db.collection("availabilityMonths")
                                                    .document(booking.monthKeys.get(i)),
                                            months.get(i));
                            // Cập nhật trạng thái và thông tin lý do; khi hủy lưu kết quả chính
                            // sách.
                            tx.update(
                                    ref,
                                    "status",
                                    target,
                                    "rejectionReason",
                                    Arrays.asList(
                                                            "REJECTED",
                                                            "CANCELLED",
                                                            "NO_SHOW",
                                                            "TERMINATED",
                                                            "COMPLETED")
                                                    .contains(target)
                                            ? note
                                            : "",
                                    "updatedAt",
                                    FieldValue.serverTimestamp());
                            // Không sửa snapshot giá, thời gian thuê hoặc trạng thái tiền khi khép
                            // vụ việc.
                            if ("CHECKED_IN".equals(target)
                                    || "COMPLETED".equals(target)
                                    || "TERMINATED".equals(target)) {
                                if (booking.usageConfirmedAt == null)
                                    tx.update(
                                            ref,
                                            "usageConfirmedAt",
                                            FieldValue.serverTimestamp(),
                                            "usageConfirmedBy",
                                            uid);
                            }
                            if (BookingResolutionPolicy.terminal(target))
                                tx.update(ref, "closedAt", FieldValue.serverTimestamp());
                            if ("CANCELLED".equals(target))
                                tx.update(
                                        ref,
                                        "cancelledAt",
                                        FieldValue.serverTimestamp(),
                                        "cancelledByUid",
                                        uid,
                                        "cancellationRefundPercent",
                                        refundPercent,
                                        "refundAmount",
                                        CancellationPolicy.refundDue(booking, refundPercent),
                                        "refundStatus",
                                        "REVIEW_REQUIRED".equals(booking.paymentStatus)
                                                        || CancellationPolicy.refundDue(
                                                                        booking, refundPercent)
                                                                > 0
                                                ? "MANUAL_REVIEW"
                                                : "NOT_REQUIRED");
                            tx.update(
                                    roomRef,
                                    "activeBookingCount",
                                    room.activeBookingCount < 0
                                            ? -1
                                            : room.activeBookingCount - (release ? 1 : 0),
                                    "bookingRevision",
                                    FieldValue.increment(1),
                                    "lastBookingId",
                                    id);
                            audit(tx, uid, id, booking.status, target, note);
                            return id;
                        })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    private List<Booking> read(QuerySnapshot rows) {
        // Chuyển từng tài liệu Firestore thành Booking và lưu ID tài liệu vào model.
        List<Booking> result = new ArrayList<>();
        for (DocumentSnapshot doc : rows) {
            Booking booking = doc.toObject(Booking.class);
            if (booking != null) {
                booking.id = doc.getId();
                result.add(booking);
            }
        }
        // Sắp xếp booking mới hơn lên trước; ngày null được đưa xuống cuối.
        result.sort(
                Comparator.comparing(
                        Booking::getStartAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private void deliver(QuerySnapshot rows, ResultCallback<List<Booking>> callback) {
        try {
            // Dùng chung bước chuyển đổi dữ liệu cho truy vấn một lần và listener realtime.
            List<Booking> values = read(rows);
            callback.onSuccess(values);
            reconcileExpired(values, callback);
        } catch (Exception error) {
            callback.onError(error);
        }
    }

    // Không có job máy chủ: chỉ dọn đơn quá giờ khi đồng bộ foreground.
    // ID booking cố định + transaction bảo vệ trường hợp Guest/Host cùng dọn một đơn.
    private final Map<String, Long> expiryAttempts = new HashMap<>();

    public void reconcileExpired(List<Booking> values, ResultCallback<?> cb) {
        long now = System.currentTimeMillis();
        for (Booking b : values)
            if (b.schemaVersion == Booking.CURRENT_SCHEMA_VERSION
                    && "PENDING".equals(b.status)
                    && b.startAt != null
                    && b.startAt.getTime() <= now
                    && now - expiryAttempts.getOrDefault(b.id, 0L) > 60000) {
                expiryAttempts.put(b.id, now);
                transition(
                        b.id,
                        "EXPIRED",
                        "Hết hạn chờ Host duyệt tại giờ bắt đầu.",
                        new ResultCallback<String>() {
                            public void onSuccess(String id) {}

                            public void onError(Exception e) {
                                cb.onError(e);
                            }
                        });
            }
    }

    public void mine(String uid, ResultCallback<List<Booking>> callback) {
        try {
            // Chỉ cho phép tải danh sách của UID đang đăng nhập.
            BookingPolicy.require(actor().equals(uid), "Không được xem đặt chỗ của người khác.");
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        db.collection("bookings")
                .whereEqualTo("guestUid", uid)
                // SERVER yêu cầu dữ liệu mới nhất thay vì chỉ dùng cache offline.
                .get(Source.SERVER)
                .addOnSuccessListener(rows -> deliver(rows, callback))
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration watchMine(String uid, ResultCallback<List<Booking>> callback) {
        // Guest theo dõi các booking có guestUid là UID của mình.
        return watchParticipant(uid, "guestUid", callback);
    }

    public ListenerRegistration watchOwner(String uid, ResultCallback<List<Booking>> callback) {
        // Host theo dõi các booking có ownerUid là UID của mình.
        return watchParticipant(uid, "ownerUid", callback);
    }

    public ListenerRegistration watchAdmin(ResultCallback<List<Booking>> callback) {
        // Chỉ lấy đơn chưa kết thúc; không để giới hạn trang che mất các yêu cầu quá hạn cũ.
        return db.collection("bookings")
                .whereIn(
                        "status",
                        Arrays.asList("PENDING", "CONFIRMED", "CHECKED_IN", "NO_SHOW", "COMPLETED"))
                .orderBy("startAt")
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (rows, e) -> {
                            if (e != null) {
                                callback.onError(e);
                                return;
                            }
                            if (rows != null
                                    && !rows.getMetadata().isFromCache()
                                    && !rows.getMetadata().hasPendingWrites())
                                deliver(rows, callback);
                        });
    }

    public void reconcileRoomCount(String roomId, ResultCallback<String> cb) {
        // Dữ liệu cũ không được tự coi là rỗng. Admin đối soát theo revision, không sửa booking.
        final String uid;
        try {
            uid = actor();
            BookingPolicy.id(roomId);
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        DocumentReference roomRef = db.collection("rooms").document(roomId);
        roomRef.get(Source.SERVER)
                .addOnSuccessListener(
                        before -> {
                            db.collection("bookings")
                                    .whereEqualTo("roomId", roomId)
                                    .get(Source.SERVER)
                                    .addOnSuccessListener(
                                            rows -> {
                                                long count = 0;
                                                for (DocumentSnapshot row : rows) {
                                                    String status = row.getString("status");
                                                    if (Arrays.asList(
                                                                    "PENDING",
                                                                    "CONFIRMED",
                                                                    "CHECKED_IN")
                                                            .contains(status)) count++;
                                                }
                                                final long value = count;
                                                db.runTransaction(
                                                                tx -> {
                                                                    user(tx, uid, "ADMIN");
                                                                    DocumentSnapshot now =
                                                                            tx.get(roomRef);
                                                                    BookingPolicy.require(
                                                                            now.exists()
                                                                                    && Objects
                                                                                            .equals(
                                                                                                    now
                                                                                                            .get(
                                                                                                                    "bookingRevision"),
                                                                                                    before
                                                                                                            .get(
                                                                                                                    "bookingRevision")),
                                                                            "Lịch vừa đổi; hãy đối"
                                                                                    + " soát lại.");
                                                                    tx.update(
                                                                            roomRef,
                                                                            "activeBookingCount",
                                                                            value,
                                                                            "updatedAt",
                                                                            FieldValue
                                                                                    .serverTimestamp());
                                                                    tx.set(
                                                                            db.collection(
                                                                                            "auditLogs")
                                                                                    .document(),
                                                                            new AuditLog(
                                                                                    uid,
                                                                                    roomRef
                                                                                            .getPath(),
                                                                                    "RECONCILE_ROOM",
                                                                                    Collections
                                                                                            .singletonMap(
                                                                                                    "activeBookingCount",
                                                                                                    now
                                                                                                            .get(
                                                                                                                    "activeBookingCount")),
                                                                                    Collections
                                                                                            .singletonMap(
                                                                                                    "activeBookingCount",
                                                                                                    value),
                                                                                    "Đối soát số"
                                                                                        + " booking"
                                                                                        + " còn giữ"
                                                                                        + " lịch."));
                                                                    return roomId;
                                                                })
                                                        .addOnSuccessListener(cb::onSuccess)
                                                        .addOnFailureListener(cb::onError);
                                            })
                                    .addOnFailureListener(cb::onError);
                        })
                .addOnFailureListener(cb::onError);
    }

    private ListenerRegistration watchParticipant(
            String uid, String field, ResultCallback<List<Booking>> callback) {
        try {
            // Không cho người dùng nghe danh sách booking của UID khác.
            BookingPolicy.require(actor().equals(uid), "Không được xem đặt chỗ của người khác.");
        } catch (Exception e) {
            callback.onError(e);
            return null;
        }
        return db.collection("bookings")
                .whereEqualTo(field, uid)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (rows, error) -> {
                            if (error != null) {
                                callback.onError(error);
                                return;
                            }
                            // Chỉ phát dữ liệu đã xác nhận từ server, không phát cache hay ghi đang
                            // chờ.
                            if (rows != null
                                    && !rows.getMetadata().isFromCache()
                                    && !rows.getMetadata().hasPendingWrites())
                                deliver(rows, callback);
                        });
    }

    public void get(String id, ResultCallback<Booking> callback) {
        try {
            // Kiểm tra mã booking và phiên đăng nhập trước khi đọc tài liệu.
            BookingPolicy.id(id);
            actor();
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        db.collection("bookings")
                .document(id)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        doc -> {
                            try {
                                Booking booking = doc.toObject(Booking.class);
                                // Chỉ Guest tạo booking được đọc chi tiết theo hàm này.
                                if (booking == null || !actor().equals(booking.guestUid)) {
                                    callback.onError(
                                            new Exception("Không tìm thấy đặt chỗ của bạn."));
                                    return;
                                }
                                booking.id = doc.getId();
                                callback.onSuccess(booking);
                            } catch (Exception error) {
                                callback.onError(error);
                            }
                        })
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration watchOne(String id, ResultCallback<Booking> callback) {
        // Theo dõi một booking realtime; chỉ trả tài liệu thuộc về Guest đang đăng nhập.
        try {
            BookingPolicy.id(id);
            actor();
        } catch (Exception e) {
            callback.onError(e);
            return null;
        }
        return db.collection("bookings")
                .document(id)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (doc, e) -> {
                            if (e != null) {
                                callback.onError(e);
                                return;
                            }
                            // Bỏ qua snapshot cache hoặc thao tác ghi chưa được server xác nhận.
                            if (doc == null
                                    || doc.getMetadata().isFromCache()
                                    || doc.getMetadata().hasPendingWrites()) return;
                            Booking b = doc.toObject(Booking.class);
                            if (b == null || !Objects.equals(auth.getUid(), b.guestUid)) {
                                callback.onError(new Exception("Không tìm thấy đặt chỗ của bạn."));
                                return;
                            }
                            b.id = doc.getId();
                            callback.onSuccess(b);
                        });
    }

    public void availability(
            String roomId, long from, long to, ResultCallback<List<Booking>> callback) {
        try {
            // Xác thực phiên, mã phòng và giới hạn khoảng thời gian được hỏi.
            actor();
            BookingPolicy.id(roomId);
            BookingPolicy.require(
                    from < to && to - from <= 100L * 86400000, "Khoảng xem lịch tối đa 100 ngày.");
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        // Xác định các tháng lịch giao với khoảng người dùng đang chọn.
        YearMonth first = YearMonth.from(Instant.ofEpochMilli(from).atZone(HostSchedule.ZONE)),
                last = YearMonth.from(Instant.ofEpochMilli(to - 1).atZone(HostSchedule.ZONE));
        // Đọc các tài liệu lịch theo tháng từ server.
        for (YearMonth month = first; month.compareTo(last) <= 0; month = month.plusMonths(1))
            tasks.add(
                    db.collection("availabilityMonths")
                            .document(BookingPolicy.monthKey(roomId, month))
                            .get(Source.SERVER));
        Tasks.whenAll(tasks)
                .addOnSuccessListener(
                        ignored -> {
                            // Ghép các slot bận thành các khoảng thời gian để UI kiểm tra giao
                            // nhau.
                            List<Booking> result = new ArrayList<>();
                            for (Task<DocumentSnapshot> task : tasks) {
                                AvailabilityMonth month =
                                        task.getResult().toObject(AvailabilityMonth.class);
                                if (month == null) continue;
                                YearMonth ym =
                                        YearMonth.of(
                                                month.monthIndex / 100, month.monthIndex % 100);
                                ZonedDateTime start = ym.atDay(1).atStartOfDay(HostSchedule.ZONE);
                                if (!month.fixedBookingId.isEmpty())
                                    // Lịch thuê cố định chiếm trọn tháng.
                                    addInterval(
                                            result,
                                            start.toInstant().toEpochMilli(),
                                            start.plusMonths(1).toInstant().toEpochMilli(),
                                            from,
                                            to);
                                else
                                    // Mỗi slot thuê giờ tương ứng 30 phút.
                                    for (int slot : month.slotsBusy) {
                                        long at =
                                                start.plusMinutes(slot * 30L)
                                                        .toInstant()
                                                        .toEpochMilli();
                                        addInterval(result, at, at + 1800000, from, to);
                                    }
                            }
                            callback.onSuccess(result);
                        })
                .addOnFailureListener(callback::onError);
    }

    private void addInterval(List<Booking> rows, long start, long end, long from, long to) {
        // Chỉ trả khoảng bận nếu nó giao với khoảng đang được xem.
        if (start < to && from < end) {
            Booking booking = new Booking();
            booking.startAt = new Date(start);
            booking.endAt = new Date(end);
            booking.status = "CONFIRMED";
            // Đây là khoảng lịch tổng hợp để hiển thị; không kèm hồ sơ Guest của booking khác.
            rows.add(booking);
        }
    }
}
