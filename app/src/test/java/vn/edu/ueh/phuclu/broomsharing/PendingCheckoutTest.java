package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.PendingCheckout;

/*
   Kiểm thử noPaymentNeverShowsResume, creatingWithoutQrNeverShowsResume, pendingWithoutAnyUsableLinkNeverShowsResume.
   Dữ liệu được tạo trong test; kết quả này không thay cho nghiệm thu thao tác trên thiết bị hoặc cloud.
*/
public class PendingCheckoutTest {
    private Booking booking() {
        Booking b = new Booking(); b.id = "booking"; b.guestUid = "guest";
        b.roomId = "room"; b.status = "PENDING"; b.paymentFlow = "INSTANT_5M";
        return b;
    }
    private Payment payment() {
        Payment p = new Payment(); p.bookingId = "booking"; p.status = "PENDING";
        p.expiresAt = 1300; p.qrCode = "server-confirmed-qr"; return p;
    }
    private boolean visible(Booking b, List<Payment> p) {
        return !PendingCheckout.select("guest", "room", List.of(b), p, 1000000).isEmpty();
    }
    @Test public void noPaymentNeverShowsResume() { assertFalse(visible(booking(), List.of())); }
    @Test public void creatingWithoutQrNeverShowsResume() {
        Payment p = payment(); p.status = "CREATING"; assertFalse(visible(booking(), List.of(p)));
    }
    @Test public void pendingWithoutAnyUsableLinkNeverShowsResume() {
        Payment p = payment(); p.qrCode = ""; assertFalse(visible(booking(), List.of(p)));
    }
    @Test public void liveTransactionShowsResumeForItsOwnRoomOnly() {
        assertTrue(visible(booking(), List.of(payment())));
        assertTrue(PendingCheckout.select("other", null, List.of(booking()), List.of(payment()), 1000000).isEmpty());
        assertTrue(PendingCheckout.select("guest", "other-room", List.of(booking()), List.of(payment()), 1000000).isEmpty());
    }
    @Test public void expiredOrPaidQrImmediatelyHidesResume() {
        for (String status : List.of("PAID", "EXPIRED", "CANCELLED", "REVIEW_REQUIRED")) {
            Payment p = payment(); p.status = status; assertFalse(visible(booking(), List.of(p)));
        }
        Payment p = payment(); p.expiresAt = 1000; assertFalse(visible(booking(), List.of(p)));
    }
    @Test public void closedBookingHidesEvenIfPaymentSnapshotArrivesLater() {
        Booking b = booking(); b.status = "CANCELLED"; assertFalse(visible(b, List.of(payment())));
        b.status = "CONFIRMED"; assertFalse(visible(b, List.of(payment())));
    }
}
