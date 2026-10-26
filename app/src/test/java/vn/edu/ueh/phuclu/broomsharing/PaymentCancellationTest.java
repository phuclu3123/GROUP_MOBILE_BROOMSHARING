package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;

import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy;

// Khoản cần đối soát khác với tiền đã hoàn. Đơn chưa trả tiền luôn có khoản cần hoàn bằng 0.
public class PaymentCancellationTest {
    @Test
    public void paidFortyPercentAdvanceUsesActualReceived() {
        Booking b = new Booking();
        b.paymentStatus = "PAID";
        b.paidAmount = 40000;
        assertEquals(40000, CancellationPolicy.refundDue(b, 100));
        assertEquals(20000, CancellationPolicy.refundDue(b, 50));
        assertEquals(0, CancellationPolicy.refundDue(b, 0));
    }

    @Test
    public void pendingDoesNotCreateFictionalRefund() {
        Booking b = new Booking();
        b.paymentStatus = "PENDING";
        b.paidAmount = 0;
        assertEquals(0, CancellationPolicy.refundDue(b, 100));
    }

    @Test
    public void unknownPaymentRequiresReconciliation() {
        Booking b = new Booking();
        b.paymentStatus = "REVIEW_REQUIRED";
        b.paidAmount = 45000;
        assertEquals(0, CancellationPolicy.refundDue(b, 100));
    }
}
