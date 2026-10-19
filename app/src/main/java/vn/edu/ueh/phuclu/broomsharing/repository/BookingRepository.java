package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;
import java.util.*;

/** All booking/config writes go through the same trusted service. No offline write fallback. */
public class BookingRepository {
    private final FirebaseFunctions functions = vn.edu.ueh.phuclu.broomsharing.utils.FirebaseConfig.functions();

    @SuppressWarnings("unchecked")
    private void call(String name, Map<String, Object> data, ResultCallback<Map<String, Object>> cb) {
        functions.getHttpsCallable(name).call(data)
                .addOnSuccessListener(result -> {
                    if (!(result.getData() instanceof Map)) { cb.onError(new Exception("Phản hồi dịch vụ không hợp lệ.")); return; }
                    cb.onSuccess((Map<String, Object>) result.getData());
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseFunctionsException) {
                        FirebaseFunctionsException f = (FirebaseFunctionsException) e;
                        if (f.getCode() == FirebaseFunctionsException.Code.NOT_FOUND
                                || f.getCode() == FirebaseFunctionsException.Code.UNAVAILABLE) {
                            cb.onError(new Exception("Dịch vụ đặt phòng chưa sẵn sàng. Vui lòng thử lại; yêu cầu chưa được xác nhận."));
                            return;
                        }
                    }
                    cb.onError(e);
                });
    }

    @SuppressWarnings("unchecked")
    public void quote(Map<String, Object> data, ResultCallback<Booking> cb) {
        call("quoteBooking", data, new ResultCallback<Map<String, Object>>() {
            public void onSuccess(Map<String, Object> value) {
                try {
                    Booking b = new Booking(); b.priceSnapshot = (Map<String, Object>) value.get("priceSnapshot");
                    b.setStartAt(new Date(((Number) value.get("startAt")).longValue()));
                    b.setEndAt(new Date(((Number) value.get("endAt")).longValue()));
                    b.setActiveMode((String) value.get("activeMode")); cb.onSuccess(b);
                } catch (Exception e) { cb.onError(new Exception("Phản hồi giá không hợp lệ.", e)); }
            }
            public void onError(Exception e) { cb.onError(e); }
        });
    }

    public void create(Map<String, Object> data, ResultCallback<String> cb) {
        call("createBooking", data, result(cb, "bookingId"));
    }

    public void transition(String bookingId, String status, String reason, ResultCallback<String> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", bookingId); data.put("status", status); data.put("reason", reason == null ? "" : reason);
        call("transitionBooking", data, result(cb, "bookingId"));
    }

    public void listing(String roomId, String status, ResultCallback<String> cb) {
        Map<String, Object> data = new HashMap<>(); data.put("roomId", roomId); data.put("status", status);
        call("setHostListing", data, result(cb, "roomId"));
    }

    public void saveRoom(Room r, ResultCallback<String> cb) {
        Map<String, Object> room = new HashMap<>();
        room.put("buildingId", r.getBuildingId()); room.put("roomName", r.getRoomName());
        room.put("roomType", r.getRoomType()); room.put("capacity", r.getCapacity()); room.put("area", r.getArea());
        room.put("floor", r.getFloor()); room.put("address", r.getAddress()); room.put("activeMode", r.getActiveMode());
        room.put("pricePerHour", r.getPricePerHour()); room.put("pricePerMonth", r.getPricePerMonth());
        room.put("pricePerQuarter", r.getPricePerQuarter()); room.put("deposit", r.getDeposit());
        room.put("openTime", r.getOpenTime()); room.put("closeTime", r.getCloseTime()); room.put("amenities", r.getAmenities());
        room.put("rules", r.getRules()); room.put("imageUrls", r.getImageUrls()); room.put("blackoutDates", r.getBlackoutDates());
        room.put("workingDays", r.getWorkingDays()); room.put("slotMinutes", r.getSlotMinutes());
        room.put("minBookingMinutes", r.getMinBookingMinutes()); room.put("fixedStartMonth", r.getFixedStartMonth());
        room.put("renewalPolicy", r.getRenewalPolicy()); room.put("rentalPolicy", r.getRentalPolicy());
        room.put("approvalStatus", r.getApprovalStatus()); room.put("listingStatus", r.getListingStatus());
        List<Map<String, Object>> ranges = new ArrayList<>();
        if (r.getBlackoutRanges() != null) for (RoomBlackout b : r.getBlackoutRanges()) {
            Map<String, Object> range = new HashMap<>(); range.put("startAt", b.getStartAt().getTime());
            range.put("endAt", b.getEndAt().getTime()); range.put("reason", b.getReason()); ranges.add(range);
        }
        room.put("blackoutRanges", ranges);
        Map<String, Object> data = new HashMap<>(); data.put("roomId", r.getId()); data.put("room", room);
        call("saveHostRoom", data, result(cb, "roomId"));
    }

    public void mine(String uid, ResultCallback<List<Booking>> cb) {
        FirebaseHelper.getFirestore().collection("bookings").whereEqualTo("guestUid", uid)
                .get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener(rows -> {
                    List<Booking> list = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot d : rows) {
                        Booking b = d.toObject(Booking.class);
                        if (b != null) { b.setId(d.getId()); list.add(b); }
                    }
                    list.sort(Comparator.comparing(Booking::getStartAt, Comparator.nullsLast(Comparator.reverseOrder())));
                    cb.onSuccess(list);
                }).addOnFailureListener(cb::onError);
    }

    public void get(String id, ResultCallback<Booking> cb) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,128}")) { cb.onError(new Exception("Mã đặt chỗ không hợp lệ.")); return; }
        FirebaseHelper.getFirestore().collection("bookings").document(id)
            .get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener(doc -> {
                try {
                    Booking booking = doc.toObject(Booking.class);
                    String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
                    if (booking == null || uid == null || !uid.equals(booking.guestUid)) throw new Exception("Không tìm thấy đặt chỗ của bạn.");
                    booking.id = doc.getId(); cb.onSuccess(booking);
                } catch (Exception e) { cb.onError(e); }
            }).addOnFailureListener(cb::onError);
    }

    public com.google.firebase.firestore.ListenerRegistration watchMine(String uid, ResultCallback<List<Booking>> cb) {
        return FirebaseHelper.getFirestore().collection("bookings").whereEqualTo("guestUid", uid)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (rows,error) -> {
                if (error != null) { cb.onError(error); return; }
                if (rows == null || rows.getMetadata().isFromCache()) return;
                try {
                    List<Booking> list = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : rows) {
                        Booking booking = doc.toObject(Booking.class);
                        if (booking != null) { booking.id = doc.getId(); list.add(booking); }
                    }
                    list.sort(Comparator.comparing(Booking::getStartAt, Comparator.nullsLast(Comparator.reverseOrder())));
                    cb.onSuccess(list);
                } catch (Exception e) { cb.onError(e); }
            });
    }

    public void availability(String roomId, long start, long end, ResultCallback<List<Booking>> cb) {
        Map<String,Object> data = new HashMap<>(); data.put("roomId",roomId); data.put("startAt",start); data.put("endAt",end);
        call("getRoomAvailability",data,new ResultCallback<Map<String,Object>>() {
            public void onSuccess(Map<String,Object> value) {
                try {
                    List<Booking> result = new ArrayList<>();
                    for (Object raw : (List<?>)value.get("intervals")) {
                        Map<?,?> range = (Map<?,?>)raw; Booking booking = new Booking();
                        booking.startAt = new Date(((Number)range.get("startAt")).longValue());
                        booking.endAt = new Date(((Number)range.get("endAt")).longValue());
                        booking.status = Booking.STATUS_CONFIRMED; result.add(booking);
                    }
                    cb.onSuccess(result);
                } catch (Exception e) { cb.onError(new Exception("Chưa đọc được lịch trống.",e)); }
            }
            public void onError(Exception e) { cb.onError(e); }
        });
    }

    private ResultCallback<Map<String, Object>> result(ResultCallback<String> cb, String key) {
        return new ResultCallback<Map<String, Object>>() {
            public void onSuccess(Map<String, Object> value) {
                if (value.get(key) instanceof String) cb.onSuccess((String) value.get(key));
                else cb.onError(new Exception("Phản hồi dịch vụ thiếu mã kết quả."));
            }
            public void onError(Exception e) { cb.onError(e); }
        };
    }
}
