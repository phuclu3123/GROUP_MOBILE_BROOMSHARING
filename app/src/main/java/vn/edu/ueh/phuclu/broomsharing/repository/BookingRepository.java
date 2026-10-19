package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.time.*;
import java.util.*;

/** Firestore Android SDK only. Reads precede writes in every transaction. */
public class BookingRepository {
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public BookingRepository() {
        this(FirebaseHelper.getFirestore(), FirebaseAuth.getInstance());
    }

    public BookingRepository(FirebaseFirestore database, FirebaseAuth authentication) {
        db = database;
        auth = authentication;
    }

    private String actor() {
        String uid = auth.getUid();
        BookingPolicy.require(uid != null, "Vui lòng đăng nhập lại.");
        return uid;
    }

    private DocumentSnapshot user(Transaction tx, String uid, String... roles)
            throws FirebaseFirestoreException {
        BookingPolicy.require(uid.equals(actor()), "Phiên đăng nhập đã thay đổi.");
        DocumentSnapshot user = tx.get(db.collection("users").document(uid));
        BookingPolicy.require(
                "ACTIVE".equals(user.getString("status"))
                        && Arrays.asList(roles).contains(user.getString("role")),
                "Tài khoản không có quyền hoặc đã bị khóa.");
        return user;
    }

    private Room room(Transaction tx, String roomId, String uid) throws FirebaseFirestoreException {
        DocumentSnapshot doc = tx.get(db.collection("rooms").document(BookingPolicy.id(roomId)));
        Room room = doc.toObject(Room.class);
        BookingPolicy.require(
                room != null
                        && "APPROVED".equals(room.getApprovalStatus())
                        && "ACTIVE".equals(room.getListingStatus()),
                "Phòng hiện không nhận đặt.");
        room.setId(doc.getId());
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
        List<AvailabilityMonth> months = new ArrayList<>();
        for (String key : booking.monthKeys) {
            DocumentSnapshot doc =
                    tx.get(db.collection("availabilityMonths").document(BookingPolicy.id(key)));
            AvailabilityMonth month = doc.toObject(AvailabilityMonth.class);
            if (month == null) {
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
        tx.set(
                db.collection("auditLogs").document(),
                new AuditLog(
                        uid,
                        "bookings/" + bookingId,
                        from.isEmpty() ? "CREATE" : to,
                        Collections.singletonMap("status", from),
                        Collections.singletonMap("status", to),
                        reason));
    }

    public void quote(Booking request, ResultCallback<Booking> callback) {
        final String uid;
        try {
            uid = actor();
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        db.runTransaction(
                        tx -> {
                            user(tx, uid, "GUEST", "HOST");
                            Booking booking =
                                    BookingPolicy.plan(
                                            room(tx, request.roomId, uid),
                                            request,
                                            System.currentTimeMillis());
                            booking.id = "quote";
                            List<AvailabilityMonth> months = months(tx, booking);
                            for (AvailabilityMonth month : months)
                                BookingPolicy.reserve(month, booking);
                            return booking;
                        })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    public void create(Booking request, ResultCallback<String> callback) {
        create(request, callback, true);
    }

    private void create(
            Booking request, ResultCallback<String> callback, boolean refreshAfterDenial) {
        final String uid, bookingId, fingerprint;
        try {
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
                            DocumentSnapshot guest = user(tx, uid, "GUEST", "HOST");
                            DocumentReference ref = db.collection("bookings").document(bookingId);
                            DocumentSnapshot previous = tx.get(ref);
                            if (previous.exists()) {
                                BookingPolicy.require(
                                        fingerprint.equals(
                                                previous.getString("requestFingerprint")),
                                        "Mã gửi lại đã dùng cho yêu cầu khác.");
                                return bookingId;
                            }
                            Room room = room(tx, request.roomId, uid);
                            Booking booking =
                                    BookingPolicy.plan(room, request, System.currentTimeMillis());
                            booking.id = bookingId;
                            booking.guestUid = uid;
                            booking.guestName = guest.getString("name");
                            booking.guestPhone = guest.getString("phone");
                            booking.requestFingerprint = fingerprint;
                            List<AvailabilityMonth> months = months(tx, booking);
                            for (AvailabilityMonth month : months)
                                BookingPolicy.reserve(month, booking);
                            for (int i = 0; i < months.size(); i++)
                                tx.set(
                                        db.collection("availabilityMonths")
                                                .document(booking.monthKeys.get(i)),
                                        months.get(i));
                            tx.set(ref, booking);
                            tx.update(
                                    db.collection("rooms").document(room.getId()),
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
                            // A concurrent reservation can change getAfter() before this commit is
                            // authorized.
                            // Retry once with fresh reads: report the actual overlap, never bypass
                            // Rules.
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
        final String uid;
        try {
            uid = actor();
            BookingPolicy.id(id);
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        String note = reason == null ? "" : reason.trim();
        db.runTransaction(
                        tx -> {
                            user(
                                    tx,
                                    uid,
                                    "CANCELLED".equals(target)
                                            ? new String[] {"GUEST", "HOST"}
                                            : new String[] {"HOST"});
                            DocumentReference ref = db.collection("bookings").document(id);
                            Booking booking = tx.get(ref).toObject(Booking.class);
                            BookingPolicy.require(booking != null, "Không tìm thấy booking.");
                            booking.id = id;
                            BookingPolicy.require(
                                    uid.equals(
                                            "CANCELLED".equals(target)
                                                    ? booking.guestUid
                                                    : booking.ownerUid),
                                    "Không được xử lý booking này.");
                            BookingPolicy.transition(
                                    booking, target, note, System.currentTimeMillis());
                            if (target.equals(booking.status)) return id;
                            BookingPolicy.require(
                                    booking.schemaVersion == 2 && !booking.monthKeys.isEmpty(),
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
                            List<AvailabilityMonth> months = months(tx, booking);
                            boolean release =
                                    Arrays.asList("REJECTED", "CANCELLED", "COMPLETED")
                                            .contains(target);
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
                                for (int i = 0; i < months.size(); i++)
                                    tx.set(
                                            db.collection("availabilityMonths")
                                                    .document(booking.monthKeys.get(i)),
                                            months.get(i));
                            tx.update(
                                    ref,
                                    "status",
                                    target,
                                    "rejectionReason",
                                    "REJECTED".equals(target) ? note : "",
                                    "updatedAt",
                                    FieldValue.serverTimestamp());
                            tx.update(
                                    roomRef,
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
        List<Booking> result = new ArrayList<>();
        for (DocumentSnapshot doc : rows) {
            Booking booking = doc.toObject(Booking.class);
            if (booking != null) {
                booking.id = doc.getId();
                result.add(booking);
            }
        }
        result.sort(
                Comparator.comparing(
                        Booking::getStartAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private void deliver(QuerySnapshot rows, ResultCallback<List<Booking>> callback) {
        try {
            callback.onSuccess(read(rows));
        } catch (Exception error) {
            callback.onError(error);
        }
    }

    public void mine(String uid, ResultCallback<List<Booking>> callback) {
        try {
            BookingPolicy.require(actor().equals(uid), "Không được xem đặt chỗ của người khác.");
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        db.collection("bookings")
                .whereEqualTo("guestUid", uid)
                .get(Source.SERVER)
                .addOnSuccessListener(rows -> deliver(rows, callback))
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration watchMine(String uid, ResultCallback<List<Booking>> callback) {
        try {
            BookingPolicy.require(actor().equals(uid), "Không được xem đặt chỗ của người khác.");
        } catch (Exception e) {
            callback.onError(e);
            return null;
        }
        return db.collection("bookings")
                .whereEqualTo("guestUid", uid)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (rows, error) -> {
                            if (error != null) {
                                callback.onError(error);
                                return;
                            }
                            if (rows != null && !rows.getMetadata().isFromCache())
                                deliver(rows, callback);
                        });
    }

    public void get(String id, ResultCallback<Booking> callback) {
        try {
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

    public void availability(
            String roomId, long from, long to, ResultCallback<List<Booking>> callback) {
        try {
            actor();
            BookingPolicy.id(roomId);
            BookingPolicy.require(
                    from < to && to - from <= 100L * 86400000, "Khoảng xem lịch tối đa 100 ngày.");
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        List<com.google.android.gms.tasks.Task<DocumentSnapshot>> tasks = new ArrayList<>();
        YearMonth
                first =
                        YearMonth.from(
                                java.time.Instant.ofEpochMilli(from).atZone(HostSchedule.ZONE)),
                last =
                        YearMonth.from(
                                java.time.Instant.ofEpochMilli(to - 1).atZone(HostSchedule.ZONE));
        for (YearMonth month = first; month.compareTo(last) <= 0; month = month.plusMonths(1))
            tasks.add(
                    db.collection("availabilityMonths")
                            .document(BookingPolicy.monthKey(roomId, month))
                            .get(Source.SERVER));
        com.google.android.gms.tasks.Tasks.whenAll(tasks)
                .addOnSuccessListener(
                        ignored -> {
                            List<Booking> result = new ArrayList<>();
                            for (com.google.android.gms.tasks.Task<DocumentSnapshot> task : tasks) {
                                AvailabilityMonth month =
                                        task.getResult().toObject(AvailabilityMonth.class);
                                if (month == null) continue;
                                YearMonth ym =
                                        YearMonth.of(
                                                month.monthIndex / 100, month.monthIndex % 100);
                                ZonedDateTime start = ym.atDay(1).atStartOfDay(HostSchedule.ZONE);
                                if (!month.fixedBookingId.isEmpty())
                                    addInterval(
                                            result,
                                            start.toInstant().toEpochMilli(),
                                            start.plusMonths(1).toInstant().toEpochMilli(),
                                            from,
                                            to);
                                else
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
        if (start < to && from < end) {
            Booking booking = new Booking();
            booking.startAt = new Date(start);
            booking.endAt = new Date(end);
            booking.status = "CONFIRMED";
            rows.add(booking);
        }
    }
}
