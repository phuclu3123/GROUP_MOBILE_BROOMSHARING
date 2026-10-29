package vn.edu.ueh.broomsharing.payment;

import com.google.cloud.firestore.*;
import java.util.*;

// Đọc đủ khóa lịch trước khi ghi; xác nhận tiền, nhả lịch và bộ đếm phải cùng transaction.
final class BookingHold {
    private final DocumentSnapshot booking, room;
    private final List<DocumentSnapshot> months = new ArrayList<>();
    private final List<Long> slots;
    private final boolean fixed;

    @SuppressWarnings("unchecked")
    BookingHold(Firestore db, Transaction tx, DocumentSnapshot booking) throws Exception {
        this.booking = booking;
        room = tx.get(db.collection("rooms").document(booking.getString("roomId"))).get();
        slots = (List<Long>) booking.get("slotKeys");
        fixed = "FIXED".equals(booking.getString("activeMode"));
        for (String key : (List<String>) booking.get("monthKeys"))
            months.add(tx.get(db.collection("availabilityMonths").document(key)).get());
    }

    @SuppressWarnings("unchecked")
    boolean matches() {
        if (!room.exists() || months.isEmpty()) return false;
        for (DocumentSnapshot month : months) {
            if (!month.exists() || !booking.getString("roomId").equals(month.getString("roomId"))) return false;
            if (fixed) {
                if (!booking.getId().equals(month.getString("fixedBookingId"))) return false;
            } else {
                Map<String, Object> hourly = (Map<String, Object>) month.get("hourlyBookings");
                List<Long> busy = (List<Long>) month.get("slotsBusy");
                if (slots == null || hourly == null || busy == null
                        || !slots.equals(hourly.get(booking.getId())) || !busy.containsAll(slots)) return false;
            }
        }
        return true;
    }

    boolean roomOpen() {
        return "APPROVED".equals(room.getString("approvalStatus"))
                && "ACTIVE".equals(room.getString("listingStatus"));
    }

    @SuppressWarnings("unchecked")
    void finish(Firestore db, Transaction tx, boolean paid) {
        PaymentPolicy.require(matches(), "Lịch giữ chỗ không khớp; cần Admin đối soát.");
        for (DocumentSnapshot month : months) {
            Map<String, Object> next = new HashMap<>(month.getData());
            List<String> pending = new ArrayList<>((List<String>) next.getOrDefault("pendingPaymentIds", List.of()));
            pending.remove(booking.getId());
            next.put("pendingPaymentIds", pending);
            if (!paid) {
                if (fixed) next.put("fixedBookingId", "");
                else {
                    Map<String, Object> hourly = new HashMap<>((Map<String, Object>) next.get("hourlyBookings"));
                    hourly.remove(booking.getId());
                    List<Long> busy = new ArrayList<>((List<Long>) next.get("slotsBusy"));
                    busy.removeAll(slots);
                    next.put("hourlyBookings", hourly);
                    next.put("slotsBusy", busy);
                }
            }
            next.put("lastBookingId", booking.getId());
            next.put("updatedAt", FieldValue.serverTimestamp());
            tx.set(month.getReference(), next);
        }
        long count = room.contains("activeBookingCount") ? room.getLong("activeBookingCount") : -1;
        PaymentPolicy.require(paid || count != 0, "Bộ đếm lịch không khớp.");
        tx.update(room.getReference(), Map.of("activeBookingCount", paid ? count : count < 0 ? -1 : count - 1,
                "bookingRevision", FieldValue.increment(1), "lastBookingId", booking.getId()));
        Map<String, Object> change = new HashMap<>();
        change.put("status", paid ? "CONFIRMED" : "EXPIRED");
        change.put("updatedAt", FieldValue.serverTimestamp());
        if (!paid) {
            change.put("closedAt", FieldValue.serverTimestamp());
            change.put("rejectionReason", "Phiên thanh toán kết thúc, lịch tạm giữ đã được giải phóng.");
        }
        tx.update(booking.getReference(), change);
        tx.set(db.collection("auditLogs").document("booking_" + booking.getId() + (paid ? "_PAYMENT_CONFIRMED" : "_PAYMENT_EXPIRED")),
                Map.of("schemaVersion", 1, "actorUid", "payment_backend", "targetPath", "bookings/" + booking.getId(),
                        "action", paid ? "PAYMENT_CONFIRMED" : "PAYMENT_EXPIRED", "before", Map.of("status", "PENDING"),
                        "after", Map.of("status", paid ? "CONFIRMED" : "EXPIRED"),
                        "reason", "payOS reconciliation", "createdAt", FieldValue.serverTimestamp()));
    }
}
