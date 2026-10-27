package vn.edu.ueh.phuclu.broomsharing.utils;

import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;

// Nhắc trả tiền chỉ khi cả booking và giao dịch QR thật đều đang chờ, còn hạn.
public final class PendingCheckout {
    public final Booking booking;
    public final Payment payment;
    private PendingCheckout(Booking booking, Payment payment) { this.booking = booking; this.payment = payment; }

    public static List<PendingCheckout> select(String uid, String roomId, List<Booking> bookings, List<Payment> payments, long now) {
        Map<String, Payment> byBooking = new HashMap<>();
        for (Payment payment : payments) byBooking.put(payment.bookingId, payment);
        List<PendingCheckout> result = new ArrayList<>();
        for (Booking booking : bookings) {
            Payment payment = byBooking.get(booking.id);
            if (uid.equals(booking.guestUid) && booking.isInstantPayment() && "PENDING".equals(booking.status)
                    && (roomId == null || roomId.equals(booking.roomId)) && payment != null && payment.canPay(now)
                    && (!payment.qrCode.isEmpty() || !payment.checkoutUrl.isEmpty()))
                result.add(new PendingCheckout(booking, payment));
        }
        result.sort(Comparator.comparingLong(entry -> entry.payment.expiresAt));
        return result;
    }

    public String summary(long now) {
        long seconds = payment.remainingSeconds(now);
        return booking.roomName + "\n" + TimeUtils.formatVietnam(booking.startAt)
                + " • Còn " + seconds / 60 + " phút " + seconds % 60 + " giây";
    }
}
