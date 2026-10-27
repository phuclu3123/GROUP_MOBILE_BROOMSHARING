package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.firestore.*;
import java.time.*;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

// Chỉ đọc availabilityMonths công khai. Không truy vấn booking/hồ sơ riêng của người thuê khác.
public final class PublicCalendarRepository {
    public static final class Calendar {
        public final List<Booking> windows;
        public final LocalDate until;
        Calendar(List<Booking> windows, LocalDate until) { this.windows = windows; this.until = until; }
    }

    public ListenerRegistration watch(Room room, ResultCallback<Calendar> callback) {
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        YearMonth first = YearMonth.now(HostSchedule.ZONE).minusMonths(2);
        int count = 15; // Đủ đầu kỳ quý đang thuê và 12 tháng tiếp theo.
        Map<String, DocumentSnapshot> confirmed = new HashMap<>();
        List<ListenerRegistration> listeners = new ArrayList<>();
        boolean[] stopped = {false};
        for (int i = 0; i < count; i++) {
            YearMonth month = first.plusMonths(i);
            String key = BookingPolicy.monthKey(room.getId(), month);
            listeners.add(db.collection("availabilityMonths").document(key)
                    .addSnapshotListener(MetadataChanges.INCLUDE, (row, error) -> {
                        if (stopped[0]) return;
                        if (error != null) { confirmed.remove(key); callback.onError(error); return; }
                        if (row == null || row.getMetadata().isFromCache() || row.getMetadata().hasPendingWrites()) {
                            // Không giữ nhãn còn trống từ lần đọc cũ khi lịch chưa được server xác nhận.
                            if (confirmed.remove(key) != null) callback.onError(new IllegalStateException("Chưa xác nhận được lịch từ máy chủ."));
                            return;
                        }
                        confirmed.put(key, row);
                        if (confirmed.size() != count) return;
                        try {
                            List<Booking> ranges = new ArrayList<>();
                            for (int offset = 0; offset < count; offset++) {
                                YearMonth ym = first.plusMonths(offset);
                                DocumentSnapshot snapshot = confirmed.get(BookingPolicy.monthKey(room.getId(), ym));
                                if (!snapshot.exists()) continue;
                                AvailabilityMonth value = snapshot.toObject(AvailabilityMonth.class);
                                BookingPolicy.require(value != null && room.getId().equals(value.roomId), "Lịch không khớp phòng.");
                                if (value.fixedBookingId.isEmpty()) continue;
                                Booking b = new Booking(); b.id = value.fixedBookingId; b.roomId = room.getId();
                                b.startAt = Date.from(ym.atDay(1).atStartOfDay(HostSchedule.ZONE).toInstant());
                                b.endAt = Date.from(ym.plusMonths(1).atDay(1).atStartOfDay(HostSchedule.ZONE).toInstant());
                                boolean pending = value.pendingPaymentIds.contains(b.id);
                                b.status = pending ? "PENDING" : "CONFIRMED";
                                b.paymentFlow = pending ? "INSTANT_5M" : "";
                                Booking last = ranges.isEmpty() ? null : ranges.get(ranges.size() - 1);
                                if (last != null && last.id.equals(b.id) && last.status.equals(b.status) && last.endAt.equals(b.startAt)) last.endAt = b.endAt;
                                else ranges.add(b);
                            }
                            callback.onSuccess(new Calendar(ranges, first.plusMonths(count).atDay(1)));
                        } catch (RuntimeException invalid) { callback.onError(invalid); }
                    }));
        }
        return () -> { stopped[0] = true; for (ListenerRegistration listener : listeners) listener.remove(); };
    }
}
