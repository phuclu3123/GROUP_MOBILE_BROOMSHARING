package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.Payment;

// Đồng hồ UI chỉ chặn QR; không tự biến hết hạn thành đã trả tiền hoặc khép booking.
public class PaymentExpiryTest {
    @Test public void qrStopsAtExactDeadlineAndNeverReopensAfterPayment() {
        Payment p = new Payment();
        p.status = "PENDING";
        p.expiresAt = 1000;
        assertTrue(p.canPay(999000));
        assertFalse(p.canPay(1000000));
        assertFalse(p.canPay(1001000));
        assertEquals(0, p.remainingSeconds(1001000));
        p.status = "PAID";
        assertFalse(p.canPay(999000));
        p.status = "CANCELLED";
        assertFalse(p.canPay(999000));
    }

    @Test public void staleRefreshCannotReopenCancelledQrButLateMoneyCanArrive() {
        Payment old = new Payment(), next = new Payment();
        old.status = "CANCELLED";
        next.status = "PENDING";
        assertFalse(old.canReplaceWith(next));
        next.status = "REVIEW_REQUIRED";
        assertTrue(old.canReplaceWith(next));
        old.status = "PAID";
        next.status = "EXPIRED";
        assertFalse(old.canReplaceWith(next));
    }
}
