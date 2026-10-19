package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class BookingRepository {
    // Chỉ thêm biến auth
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    // Dùng interface Callback
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private final FirebaseFirestore db =
            FirebaseHelper.getFirestore();

    private final TimeZone vietnam =
            TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

    private String currentUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            throw new IllegalStateException(
                    "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }

        return FirebaseAuth.getInstance()
                .getCurrentUser()
                .getUid();
    }

    public void create(
            String requestKey,
            String roomId,
            String mode,
            Date startAt,
            Date endAt,
            String unitLabel,
            ResultCallback<String> callback) {

        final String guestUid;

        try {
            guestUid = currentUid();
        } catch (Exception e) {
            callback.onError(e);
            return;
        }

        if (roomId == null || roomId.trim().isEmpty()
                || startAt == null || endAt == null
                || !startAt.before(endAt)) {

            callback.onError(new IllegalArgumentException(
                    "Thông tin đặt phòng không hợp lệ."));
            return;
        }

        if (requestKey == null
                || requestKey.trim().isEmpty()
                || requestKey.contains("/")) {

            callback.onError(new IllegalArgumentException(
                    "Mã yêu cầu đặt chỗ không hợp lệ."));
            return;
        }

        final DocumentReference roomRef =
                db.collection("rooms").document(roomId);

        final DocumentReference bookingRef =
                db.collection("bookings").document(requestKey);

        db.runTransaction(transaction -> {

                    DocumentSnapshot existingBooking =
                            transaction.get(bookingRef);

                    if (existingBooking.exists()) {
                        if (guestUid.equals(
                                existingBooking.getString("guestUid"))) {
                            return bookingRef.getId();
                        }

                        throw new IllegalStateException(
                                "Mã yêu cầu đã được sử dụng.");
                    }

                    DocumentSnapshot room = transaction.get(roomRef);

                    if (!room.exists()
                            || !"APPROVED".equals(
                            room.getString("approvalStatus"))
                            || !"ACTIVE".equals(
                            room.getString("listingStatus"))) {

                        throw new IllegalStateException(
                                "Phòng không còn nhận đặt chỗ.");
                    }

                    String ownerUid = room.getString("ownerUid");

                    if (ownerUid == null || ownerUid.equals(guestUid)) {
                        throw new IllegalStateException(
                                "Bạn không thể tự đặt không gian của mình.");
                    }

                    String roomMode = room.getString("activeMode");

                    Calendar startCalendar =
                            Calendar.getInstance(vietnam, Locale.getDefault());
                    startCalendar.setTime(startAt);

                    Calendar endCalendar =
                            Calendar.getInstance(vietnam, Locale.getDefault());
                    endCalendar.setTime(endAt);

                    if (startAt.before(new Date())) {
                        throw new IllegalStateException(
                                "Không thể đặt thời gian trong quá khứ.");
                    }

                    if ("HOURLY".equals(mode)) {

                        if (startCalendar.get(Calendar.YEAR)
                                != endCalendar.get(Calendar.YEAR)
                                || startCalendar.get(Calendar.DAY_OF_YEAR)
                                != endCalendar.get(Calendar.DAY_OF_YEAR)) {

                            throw new IllegalStateException(
                                    "Đặt theo giờ phải nằm trong cùng một ngày.");
                        }

                        if (startCalendar.get(Calendar.MINUTE) % 30 != 0
                                || endCalendar.get(Calendar.MINUTE) % 30 != 0) {

                            throw new IllegalStateException(
                                    "Khung giờ phải theo bước 30 phút.");
                        }

                        if (!withinOpeningHours(
                                room.getString("openTime"),
                                room.getString("closeTime"),
                                startAt,
                                endAt)) {

                            throw new IllegalStateException(
                                    "Khung giờ nằm ngoài giờ mở cửa của phòng.");
                        }

                    } else {

                        if (startCalendar.get(Calendar.DAY_OF_MONTH) != 1
                                || startCalendar.get(Calendar.HOUR_OF_DAY) != 0
                                || startCalendar.get(Calendar.MINUTE) != 0) {

                            throw new IllegalStateException(
                                    "Thuê theo tháng/quý phải bắt đầu ngày 1.");
                        }

                        Calendar expectedEnd =
                                (Calendar) startCalendar.clone();

                        if ("QUARTERLY".equals(mode)) {
                            expectedEnd.add(Calendar.MONTH, 3);
                        } else {
                            expectedEnd.add(Calendar.MONTH, 1);
                        }

                        if (expectedEnd.getTimeInMillis()
                                != endAt.getTime()) {

                            throw new IllegalStateException(
                                    "Kỳ thuê tháng/quý không hợp lệ.");
                        }
                    }

                    if ("HOURLY".equals(mode)
                            && !"HOURLY".equals(roomMode)) {

                        throw new IllegalStateException(
                                "Phòng này không hỗ trợ thuê theo giờ.");
                    }

                    if (!"HOURLY".equals(mode)
                            && !"FIXED".equals(roomMode)) {

                        throw new IllegalStateException(
                                "Hình thức thuê đã thay đổi. Vui lòng tải lại phòng.");
                    }

                    if (!"HOURLY".equals(mode)
                            && !"MONTHLY".equals(mode)
                            && !"QUARTERLY".equals(mode)) {

                        throw new IllegalStateException(
                                "Hình thức thuê không hợp lệ.");
                    }

                    List<DocumentReference> lockRefs =
                            new ArrayList<>();

                    List<String> slotKeys =
                            new ArrayList<>();

                    if ("HOURLY".equals(mode)) {

                        Calendar start =
                                Calendar.getInstance(vietnam, Locale.getDefault());
                        start.setTime(startAt);

                        Calendar end =
                                Calendar.getInstance(vietnam, Locale.getDefault());
                        end.setTime(endAt);

                        Calendar cursor = (Calendar) start.clone();

                        while (cursor.before(end)) {

                            String dayId = roomId + "_"
                                    + formatDate("yyyyMMdd", cursor.getTime());

                            DocumentReference lock =
                                    db.collection("availabilityDays")
                                            .document(dayId);

                            if (!lockRefs.contains(lock)) {
                                lockRefs.add(lock);
                            }

                            slotKeys.add(
                                    formatDate("HHmm", cursor.getTime()));

                            cursor.add(Calendar.MINUTE, 30);
                        }

                    } else {

                        Calendar cursor =
                                Calendar.getInstance(vietnam, Locale.getDefault());
                        cursor.setTime(startAt);

                        Calendar end =
                                Calendar.getInstance(vietnam, Locale.getDefault());
                        end.setTime(endAt);

                        while (cursor.before(end)) {

                            String monthId = roomId + "_"
                                    + formatDate("yyyyMM", cursor.getTime());

                            lockRefs.add(
                                    db.collection("fixedMonthLocks")
                                            .document(monthId));

                            cursor.add(Calendar.MONTH, 1);
                        }
                    }

                    // Đọc tất cả tài liệu khóa trước khi thực hiện ghi.
                    List<DocumentSnapshot> lockSnapshots =
                            new ArrayList<>();

                    for (DocumentReference lockRef : lockRefs) {
                        lockSnapshots.add(transaction.get(lockRef));
                    }

                    if ("HOURLY".equals(mode)) {

                        for (DocumentSnapshot lockSnapshot : lockSnapshots) {
                            if (overlapsClosedRanges(
                                    lockSnapshot, startAt, endAt)) {

                                throw new IllegalStateException(
                                        "Khung giờ đã bị khóa. "
                                                + "Vui lòng chọn thời gian khác.");
                            }
                        }

                        int slotIndex = 0;

                        for (int i = 0; i < lockRefs.size(); i++) {

                            DocumentSnapshot lockSnapshot =
                                    lockSnapshots.get(i);

                            Map<String, Object> busy;

                            if (lockSnapshot.exists()) {
                                busy = (Map<String, Object>)
                                        lockSnapshot.get("slotsBusy");
                            } else {
                                busy = new HashMap<>();
                            }

                            if (busy == null) {
                                busy = new HashMap<>();
                            } else {
                                busy = new HashMap<>(busy);
                            }

                            String lockDay = lockRefs.get(i)
                                    .getId()
                                    .substring(roomId.length() + 1);

                            while (slotIndex < slotKeys.size()) {

                                Calendar slotTime =
                                        Calendar.getInstance(
                                                vietnam, Locale.getDefault());

                                slotTime.setTime(startAt);
                                slotTime.add(
                                        Calendar.MINUTE, slotIndex * 30);

                                String thisDay =
                                        formatDate(
                                                "yyyyMMdd", slotTime.getTime());

                                if (!lockDay.equals(thisDay)) {
                                    break;
                                }

                                String slotKey = slotKeys.get(slotIndex);

                                if (busy.containsKey(slotKey)) {
                                    throw new IllegalStateException(
                                            "Khung giờ vừa được người khác đặt. "
                                                    + "Vui lòng chọn giờ khác.");
                                }

                                busy.put(slotKey, bookingRef.getId());
                                slotIndex++;
                            }

                            Map<String, Object> lockData =
                                    new HashMap<>();

                            lockData.put("roomId", roomId);
                            lockData.put("day", lockDay);
                            lockData.put("slotsBusy", busy);

                            Long oldVersion =
                                    lockSnapshot.exists()
                                            ? lockSnapshot.getLong("version")
                                            : null;

                            lockData.put(
                                    "version",
                                    oldVersion == null
                                            ? 1L
                                            : oldVersion + 1L);

                            lockData.put(
                                    "updatedAt",
                                    FieldValue.serverTimestamp());

                            transaction.set(
                                    lockRefs.get(i),
                                    lockData,
                                    SetOptions.merge());
                        }

                    } else {

                        for (int i = 0; i < lockRefs.size(); i++) {

                            DocumentSnapshot lockSnapshot =
                                    lockSnapshots.get(i);

                            if (lockSnapshot.exists()) {
                                throw new IllegalStateException(
                                        "Tháng/quý này vừa được người khác đặt. "
                                                + "Vui lòng chọn kỳ khác.");
                            }

                            Map<String, Object> lockData =
                                    new HashMap<>();

                            lockData.put("roomId", roomId);
                            lockData.put("bookingId", bookingRef.getId());
                            lockData.put("state", "LOCKED");
                            lockData.put(
                                    "updatedAt",
                                    FieldValue.serverTimestamp());

                            transaction.set(lockRefs.get(i), lockData);
                        }
                    }

                    // Lấy giá từ Firestore để tránh tin vào giá gửi từ giao diện.
                    double trustedDeposit = number(room, "deposit");

                    double trustedUnitPrice;
                    double trustedRent;

                    if ("HOURLY".equals(mode)) {

                        trustedUnitPrice =
                                number(room, "pricePerHour");

                        trustedRent =
                                ((endAt.getTime() - startAt.getTime())
                                        / 3600000.0) * trustedUnitPrice;

                    } else if ("QUARTERLY".equals(mode)) {

                        trustedUnitPrice =
                                number(room, "pricePerQuarter");

                        trustedRent = trustedUnitPrice;

                    } else {

                        trustedUnitPrice =
                                number(room, "pricePerMonth");

                        trustedRent = trustedUnitPrice;
                    }

                    if (trustedUnitPrice <= 0 || trustedRent < 0) {
                        throw new IllegalStateException(
                                "Giá thuê chưa được cập nhật cho hình thức này.");
                    }

                    Map<String, Object> booking =
                            new HashMap<>();

                    booking.put("idempotencyKey", requestKey);
                    booking.put("roomId", roomId);
                    booking.put("ownerUid", ownerUid);
                    booking.put("guestUid", guestUid);
                    booking.put("roomName", room.getString("roomName"));
                    booking.put("roomAddress", room.getString("address"));
                    booking.put("activeMode", mode);
                    booking.put("startAt", new Timestamp(startAt));
                    booking.put("endAt", new Timestamp(endAt));
                    booking.put("status", "PENDING");
                    booking.put("paymentStatus", "UNPAID");
                    booking.put("totalAmount", trustedRent + trustedDeposit);
                    booking.put("depositAmount", trustedDeposit);
                    booking.put("priceSnapshot", trustedUnitPrice);
                    booking.put("priceUnit", unitLabel);
                    booking.put("createdAt", FieldValue.serverTimestamp());
                    booking.put("updatedAt", FieldValue.serverTimestamp());
                    booking.put("schemaVersion", 1L);

                    transaction.set(bookingRef, booking);

                    return bookingRef.getId();

                }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    private String formatDate(String pattern, Date date) {
        SimpleDateFormat format =
                new SimpleDateFormat(pattern, Locale.US);

        format.setTimeZone(vietnam);

        return format.format(date);
    }

    private double number(
            DocumentSnapshot document,
            String field) {

        Double value = document.getDouble(field);

        return value == null ? 0.0 : value;
    }

    private boolean withinOpeningHours(
            String open,
            String close,
            Date startAt,
            Date endAt) {

        try {
            if (open == null || close == null
                    || open.length() < 5
                    || close.length() < 5) {
                return true;
            }

            int openMinutes =
                    Integer.parseInt(open.substring(0, 2)) * 60
                            + Integer.parseInt(open.substring(3, 5));

            int closeMinutes =
                    Integer.parseInt(close.substring(0, 2)) * 60
                            + Integer.parseInt(close.substring(3, 5));

            Calendar start =
                    Calendar.getInstance(vietnam, Locale.getDefault());
            start.setTime(startAt);

            Calendar end =
                    Calendar.getInstance(vietnam, Locale.getDefault());
            end.setTime(endAt);

            int startMinutes =
                    start.get(Calendar.HOUR_OF_DAY) * 60
                            + start.get(Calendar.MINUTE);

            int endMinutes =
                    end.get(Calendar.HOUR_OF_DAY) * 60
                            + end.get(Calendar.MINUTE);

            return startMinutes >= openMinutes
                    && endMinutes <= closeMinutes;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean overlapsClosedRanges(
            DocumentSnapshot snapshot,
            Date startAt,
            Date endAt) {

        if (!snapshot.exists()) {
            return false;
        }

        Object value = snapshot.get("closedRanges");

        if (!(value instanceof List)) {
            return false;
        }

        for (Object item : (List<?>) value) {

            if (!(item instanceof Map)) {
                continue;
            }

            Map<?, ?> range = (Map<?, ?>) item;

            Date blockedStart =
                    dateValue(range.get("startAt"));

            Date blockedEnd =
                    dateValue(range.get("endAt"));

            if (blockedStart != null && blockedEnd != null
                    && startAt.before(blockedEnd)
                    && endAt.after(blockedStart)) {

                return true;
            }
        }

        return false;
    }

    private Date dateValue(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate();
        }

        if (value instanceof Date) {
            return (Date) value;
        }

        return null;
    }

    public void listForGuest(
            ResultCallback<List<Booking>> callback) {

        final String uid;

        try {
            uid = currentUid();
        } catch (Exception e) {
            callback.onError(e);
            return;
        }

        db.collection("bookings")
                .whereEqualTo("guestUid", uid)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(snapshot ->
                        callback.onSuccess(readBookings(snapshot)))
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration watchForGuest(
            ResultCallback<List<Booking>> callback) {

        final String uid;

        try {
            uid = currentUid();
        } catch (Exception e) {
            callback.onError(e);
            return null;
        }

        return db.collection("bookings")
                .whereEqualTo("guestUid", uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onError(error);
                        return;
                    }

                    if (snapshot != null) {
                        callback.onSuccess(readBookings(snapshot));
                    }
                });
    }

    private List<Booking> readBookings(
            QuerySnapshot snapshot) {

        List<Booking> bookings = new ArrayList<>();

        for (DocumentSnapshot doc : snapshot.getDocuments()) {

            Booking booking = doc.toObject(Booking.class);

            if (booking != null) {
                booking.id = doc.getId();

                if (booking.startAt == null
                        && doc.getTimestamp("startAt") != null) {
                    booking.startAt =
                            doc.getTimestamp("startAt").toDate();
                }

                if (booking.endAt == null
                        && doc.getTimestamp("endAt") != null) {
                    booking.endAt =
                            doc.getTimestamp("endAt").toDate();
                }

                bookings.add(booking);
            }
        }

        Collections.sort(bookings, (a, b) -> {
            Date left = a.createdAt == null
                    ? new Date(0)
                    : a.createdAt;

            Date right = b.createdAt == null
                    ? new Date(0)
                    : b.createdAt;

            return right.compareTo(left);
        });

        return bookings;
    }

    public void cancel(
            String bookingId,
            ResultCallback<Void> callback) {

        final String uid;

        try {
            uid = currentUid();
        } catch (Exception e) {
            callback.onError(e);
            return;
        }

        DocumentReference bookingRef =
                db.collection("bookings").document(bookingId);

        db.runTransaction(transaction -> {

                    DocumentSnapshot booking =
                            transaction.get(bookingRef);

                    if (!booking.exists()
                            || !uid.equals(booking.getString("guestUid"))) {

                        throw new IllegalStateException(
                                "Không tìm thấy đặt chỗ của bạn.");
                    }

                    if (!"PENDING".equals(booking.getString("status"))) {
                        throw new IllegalStateException(
                                "Chỉ có thể hủy yêu cầu đang chờ chủ phòng xác nhận.");
                    }

                    String roomId = booking.getString("roomId");
                    String mode = booking.getString("activeMode");

                    Timestamp startTimestamp =
                            booking.getTimestamp("startAt");

                    Timestamp endTimestamp =
                            booking.getTimestamp("endAt");

                    if (roomId == null
                            || startTimestamp == null
                            || endTimestamp == null) {

                        throw new IllegalStateException(
                                "Thông tin đặt chỗ không đầy đủ.");
                    }

                    Date startAt = startTimestamp.toDate();
                    Date endAt = endTimestamp.toDate();

                    List<DocumentReference> lockRefs =
                            new ArrayList<>();

                    List<DocumentSnapshot> lockSnapshots =
                            new ArrayList<>();

                    if ("HOURLY".equals(mode)) {

                        Calendar cursor =
                                Calendar.getInstance(vietnam, Locale.getDefault());
                        cursor.setTime(startAt);

                        Calendar end =
                                Calendar.getInstance(vietnam, Locale.getDefault());
                        end.setTime(endAt);

                        while (cursor.before(end)) {

                            String dayId = roomId + "_"
                                    + formatDate("yyyyMMdd", cursor.getTime());

                            DocumentReference ref =
                                    db.collection("availabilityDays")
                                            .document(dayId);

                            if (!lockRefs.contains(ref)) {
                                lockRefs.add(ref);
                            }

                            cursor.add(Calendar.MINUTE, 30);
                        }

                    } else {

                        Calendar cursor =
                                Calendar.getInstance(vietnam, Locale.getDefault());
                        cursor.setTime(startAt);

                        Calendar end =
                                Calendar.getInstance(vietnam, Locale.getDefault());
                        end.setTime(endAt);

                        while (cursor.before(end)) {

                            String monthId = roomId + "_"
                                    + formatDate("yyyyMM", cursor.getTime());

                            lockRefs.add(
                                    db.collection("fixedMonthLocks")
                                            .document(monthId));

                            cursor.add(Calendar.MONTH, 1);
                        }
                    }

                    for (DocumentReference ref : lockRefs) {
                        lockSnapshots.add(transaction.get(ref));
                    }

                    if ("HOURLY".equals(mode)) {

                        for (int i = 0; i < lockRefs.size(); i++) {

                            DocumentSnapshot snap = lockSnapshots.get(i);

                            if (!snap.exists()) {
                                continue;
                            }

                            Map<String, Object> oldBusy =
                                    (Map<String, Object>) snap.get("slotsBusy");

                            Map<String, Object> busy =
                                    oldBusy == null
                                            ? new HashMap<>()
                                            : new HashMap<>(oldBusy);

                            Calendar slot =
                                    Calendar.getInstance(vietnam, Locale.getDefault());
                            slot.setTime(startAt);

                            Calendar end =
                                    Calendar.getInstance(vietnam, Locale.getDefault());
                            end.setTime(endAt);

                            while (slot.before(end)) {

                                String day =
                                        formatDate("yyyyMMdd", slot.getTime());

                                if (lockRefs.get(i).getId().endsWith(day)) {

                                    String key =
                                            formatDate("HHmm", slot.getTime());

                                    if (bookingId.equals(busy.get(key))) {
                                        busy.remove(key);
                                    }
                                }

                                slot.add(Calendar.MINUTE, 30);
                            }

                            transaction.update(
                                    lockRefs.get(i),
                                    "slotsBusy", busy,
                                    "updatedAt", FieldValue.serverTimestamp(),
                                    "version", FieldValue.increment(1));
                        }

                    } else {

                        for (int i = 0; i < lockRefs.size(); i++) {

                            DocumentSnapshot snap = lockSnapshots.get(i);

                            if (snap.exists()
                                    && bookingId.equals(
                                    snap.getString("bookingId"))) {

                                transaction.delete(lockRefs.get(i));
                            }
                        }
                    }

                    transaction.update(
                            bookingRef,
                            "status", "CANCELLED",
                            "updatedAt", FieldValue.serverTimestamp());

                    return null;

                }).addOnSuccessListener(ignored ->
                        callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
    // NÚT YÊU THÍCH
    public void toggleFavorite(String roomId, boolean isFavorite, Callback<Boolean> callback) {
        if (auth.getCurrentUser() == null) {
            if (callback != null) callback.onError(new Exception("Chưa đăng nhập"));
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        if (isFavorite) {
            db.collection("users").document(uid)
                    .collection("favorites").document(roomId)
                    .set(new java.util.HashMap<String, Object>() {{ put("addedAt", System.currentTimeMillis()); }})
                    .addOnSuccessListener(aVoid -> {
                        if (callback != null) callback.onSuccess(true);
                    })
                    .addOnFailureListener(e -> {
                        if (callback != null) callback.onError(e);
                    });
        } else {
            db.collection("users").document(uid)
                    .collection("favorites").document(roomId)
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        if (callback != null) callback.onSuccess(false);
                    })
                    .addOnFailureListener(e -> {
                        if (callback != null) callback.onError(e);
                    });
        }
    }
    /*Khóa các tháng/quý cho thuê cố định (FIXED) bằng Firestore Transaction
     * @param monthKeys Danh sách chuỗi tháng cần khóa, ví dụ: ["202611", "202612", "202701"]
     */
    public Task<Void> createFixedBookingTransaction(Booking booking, List<String> monthKeys) {
        return db.runTransaction(transaction -> {
            // Step 1: Kiểm tra xem các tháng này đã bị ai khóa chưa
            for (String monthKey : monthKeys) {
                String lockId = booking.getRoomId() + "_" + monthKey;
                DocumentReference lockRef = db.collection("fixedMonthLocks").document(lockId);
                DocumentSnapshot snapshot = transaction.get(lockRef);

                if (snapshot.exists()) {
                    throw new FirebaseFirestoreException(
                            "Tháng " + monthKey + " đã được người khác đặt trước!",
                            FirebaseFirestoreException.Code.ABORTED
                    );
                }
            }

            // Step 2: Tiến hành khóa các tháng
            for (String monthKey : monthKeys) {
                String lockId = booking.getRoomId() + "_" + monthKey;
                DocumentReference lockRef = db.collection("fixedMonthLocks").document(lockId);

                Map<String, Object> lockData = new HashMap<>();
                lockData.put("roomId", booking.getRoomId());
                lockData.put("month", monthKey);
                lockData.put("bookingId", booking.getId());
                lockData.put("state", "LOCKED");
                lockData.put("createdAt", FieldValue.serverTimestamp());

                transaction.set(lockRef, lockData);
            }

            // Step 3: Tạo bản ghi booking chính
            DocumentReference bookingRef = db.collection("bookings").document(booking.getId());
            transaction.set(bookingRef, booking);

            return null;
        });
    }


}