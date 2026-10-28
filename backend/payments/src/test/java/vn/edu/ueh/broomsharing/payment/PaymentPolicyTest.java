package vn.edu.ueh.broomsharing.payment;

import static org.junit.Assert.*;

import com.google.cloud.Timestamp;

import org.junit.*;

import java.util.*;

/*
   Các ca kiểm thử hồi quy của PaymentPolicyTest.
   Dữ liệu được tạo trong test; kết quả này không thay cho nghiệm thu thao tác trên thiết bị hoặc cloud.
*/
public class PaymentPolicyTest {
    private Map<String, Object> b;

    @Before
    public void setup() {
        b =
                new HashMap<>(
                        Map.of(
                                "schemaVersion",
                                2L,
                                "guestUid",
                                "guest",
                                "ownerUid",
                                "host",
                                "status",
                                "CONFIRMED",
                                "paymentStatus",
                                "UNAVAILABLE",
                                "startAt",
                                Timestamp.of(new Date(System.currentTimeMillis() + 3600000)),
                                "priceSnapshot",
                                new HashMap<>(
                                        Map.of(
                                                "currency",
                                                "VND",
                                                "advancePercent",
                                                40L,
                                                "rentalAmount",
                                                100000L,
                                                "depositAmount",
                                                40000L,
                                                "paymentDueAmount",
                                                40000L))));
    }

    @Test
    public void authoritativeFortyPercent() {
        assertEquals(40000, PaymentPolicy.amount(b));
        PaymentPolicy.canCreate(b, "guest", "host", System.currentTimeMillis());
    }

    @Test
    public void oldVersionDenied() {
        b.put("schemaVersion", 1L);
        denied(() -> PaymentPolicy.amount(b));
    }

    @Test
    public void pendingBookingDenied() {
        b.put("status", "PENDING");
        denied(() -> PaymentPolicy.canCreate(b, "guest", "host", 0));
    }

    @Test
    public void strangerDenied() {
        denied(() -> PaymentPolicy.canCreate(b, "other", "host", 0));
    }

    @Test
    public void foreignMerchantDenied() {
        denied(() -> PaymentPolicy.canCreate(b, "guest", "other", 0));
    }

    @Test
    public void missingMerchantDenied() {
        denied(() -> PaymentPolicy.canCreate(b, "guest", "", 0));
    }

    @Test
    public void paidCannotCreateAnother() {
        b.put("paymentStatus", "PAID");
        denied(() -> PaymentPolicy.canCreate(b, "guest", "host", 0));
    }

    @Test
    public void reviewCannotChargeAgain() {
        b.put("paymentStatus", "REVIEW_REQUIRED");
        denied(() -> PaymentPolicy.canCreate(b, "guest", "host", 0));
    }

    @Test
    public void startedDenied() {
        denied(
                () ->
                        PaymentPolicy.canCreate(
                                b, "guest", "host", System.currentTimeMillis() + 3600000));
    }

    @Test
    public void missingSnapshotDenied() {
        b.remove("priceSnapshot");
        denied(() -> PaymentPolicy.amount(b));
    }

    @Test
    public void tamperedAmountDenied() {
        price().put("paymentDueAmount", 1L);
        denied(() -> PaymentPolicy.amount(b));
    }

    @Test
    public void wrongCurrencyDenied() {
        price().put("currency", "USD");
        denied(() -> PaymentPolicy.amount(b));
    }

    @Test
    public void wrongAdvanceDenied() {
        price().put("advancePercent", 100L);
        denied(() -> PaymentPolicy.amount(b));
    }

    @Test
    public void decimalMoneyDenied() {
        denied(() -> PaymentPolicy.number(40000.5));
    }

    @Test
    public void nonFiniteMoneyDenied() {
        denied(() -> PaymentPolicy.number(Double.NaN));
    }

    @Test
    public void cancelledMoneyNeedsReview() {
        b.put("status", "CANCELLED");
        assertTrue(PaymentPolicy.needsReview(b));
    }

    @Test
    public void checkedInMoneyDoesNotChangeUse() {
        b.put("status", "CHECKED_IN");
        assertFalse(PaymentPolicy.needsReview(b));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> price() {
        return (Map<String, Object>) b.get("priceSnapshot");
    }

    @Test
    public void lastMinuteStillUsesStartBoundary() {
        b.put("startAt", Timestamp.of(new Date(System.currentTimeMillis() + 5000)));
        PaymentPolicy.canCreate(b, "guest", "host", System.currentTimeMillis());
    }

    private static void denied(Runnable action) {
        assertThrows(IllegalArgumentException.class, action::run);
    }
}
