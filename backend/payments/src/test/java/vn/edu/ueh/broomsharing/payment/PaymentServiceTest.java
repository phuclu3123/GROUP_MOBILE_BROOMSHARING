package vn.edu.ueh.broomsharing.payment;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.*;
import com.google.auth.oauth2.*;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.firebase.*;
import com.google.firebase.cloud.FirestoreClient;

import org.junit.*;

import java.util.*;

// Firestore emulator chạy transaction thật; chỉ cổng ngân hàng được thay bằng fixture.
public class PaymentServiceTest {
    static Firestore db;
    static FirebaseApp app;
    Fake provider;
    PaymentService service;
    String id;

    @BeforeClass
    public static void connect() {
        Assume.assumeTrue(
                "Requires isolated Firestore emulator",
                System.getenv("FIRESTORE_EMULATOR_HOST") != null);
        app =
                FirebaseApp.initializeApp(
                        FirebaseOptions.builder()
                                .setProjectId("broom-sharing-firebase-mobile")
                                .setCredentials(
                                        GoogleCredentials.create(
                                                new AccessToken("owner", new Date(Long.MAX_VALUE))))
                                .build(),
                        "payments-test");
        db = FirestoreClient.getFirestore(app, "payment_backend_qa_" + UUID.randomUUID().toString().replace("-", ""));
    }

    @AfterClass
    public static void close() throws Exception {
        if (db != null) db.close();
        if (app != null) app.delete();
    }

    @Before
    public void seed() throws Exception {
        db.close();
        db = FirestoreClient.getFirestore(app, "payment_case_" + UUID.randomUUID().toString().replace("-", ""));
        id = "payment_" + UUID.randomUUID().toString().replace("-", "");
        provider = new Fake();
        service = new PaymentService(db, provider, "host", "https://example.test");
        db.collection("users")
                .document("guest")
                .set(Map.of("status", "ACTIVE", "role", "GUEST"))
                .get();
        db.collection("users")
                .document("host")
                .set(Map.of("status", "ACTIVE", "role", "HOST"))
                .get();
        db.collection("bookings")
                .document(id)
                .set(
                        Map.of(
                                "id",
                                id,
                                "schemaVersion",
                                2L,
                                "ownerUid",
                                "host",
                                "guestUid",
                                "guest",
                                "status",
                                "CONFIRMED",
                                "paymentStatus",
                                "UNAVAILABLE",
                                "startAt",
                                Timestamp.of(new Date(System.currentTimeMillis() + 3600000)),
                                "priceSnapshot",
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
                                        40000L)))
                .get();
    }

    @Test
    public void retryReusesOneOrder() throws Exception {
        var first = service.checkout("guest", id);
        var second = service.checkout("guest", id);
        assertEquals(first.get("orderCode"), second.get("orderCode"));
        assertEquals(1, provider.creates);
    }

    @Test
    public void pendingDoesNotPretendPaid() throws Exception {
        service.checkout("guest", id);
        service.refresh("guest", id);
        assertEquals("PENDING", booking().getString("paymentStatus"));
        assertNull(booking().get("paidAmount"));
    }

    @Test
    public void paidIsAtomicAndIdempotent() throws Exception {
        service.checkout("guest", id);
        provider.status = "PAID";
        service.refresh("guest", id);
        service.refresh("guest", id);
        assertEquals("PAID", booking().getString("paymentStatus"));
        assertEquals(40000L, booking().getLong("paidAmount").longValue());
        assertEquals("CONFIRMED", booking().getString("status"));
        assertEquals("PAID", service.checkout("guest", id).get("status"));
        assertEquals(1, provider.creates);
    }

    @Test
    public void moneyAfterCancellationDoesNotRevive() throws Exception {
        service.checkout("guest", id);
        db.collection("bookings").document(id).update("status", "CANCELLED").get();
        provider.status = "PAID";
        service.refresh("guest", id);
        assertEquals("REVIEW_REQUIRED", booking().getString("paymentStatus"));
        assertEquals("CANCELLED", booking().getString("status"));
        assertEquals("MANUAL_REVIEW", booking().getString("refundStatus"));
    }

    @Test
    public void wrongAmountNeverMarksPaid() throws Exception {
        service.checkout("guest", id);
        provider.amount = 1;
        provider.status = "PAID";
        assertThrows(Exception.class, () -> service.refresh("guest", id));
        assertEquals("PENDING", booking().getString("paymentStatus"));
    }

    @Test
    public void unpaidExpiredDoesNotReleaseBooking() throws Exception {
        service.checkout("guest", id);
        provider.status = "EXPIRED";
        service.refresh("guest", id);
        assertEquals("UNPAID", booking().getString("paymentStatus"));
        assertEquals("CONFIRMED", booking().getString("status"));
    }

    @Test
    public void blockedHostCannotCreate() throws Exception {
        db.collection("users").document("host").update("status", "BLOCKED").get();
        assertThrows(Exception.class, () -> service.checkout("guest", id));
        assertEquals(0, provider.creates);
    }

    @Test
    public void strangerCannotRead() throws Exception {
        service.checkout("guest", id);
        db.collection("users")
                .document("stranger")
                .set(Map.of("status", "ACTIVE", "role", "GUEST"))
                .get();
        assertThrows(Exception.class, () -> service.refresh("stranger", id));
    }

    @Test
    public void blockedGuestCannotRead() throws Exception {
        service.checkout("guest", id);
        db.collection("users").document("guest").update("status", "BLOCKED").get();
        assertThrows(Exception.class, () -> service.refresh("guest", id));
    }

    @Test
    public void providerTimeoutKeepsOrderForRetry() throws Exception {
        provider.fail = true;
        assertThrows(Exception.class, () -> service.checkout("guest", id));
        long order = db.collection("payments").document(id).get().get().getLong("orderCode");
        provider.fail = false;
        var result = service.checkout("guest", id);
        assertEquals(order, ((Number) result.get("orderCode")).longValue());
    }

    @Test
    public void partialMoneyNeverMarksPaid() throws Exception {
        service.checkout("guest", id);
        provider.status = "PAID";
        provider.received = 20000;
        assertThrows(Exception.class, () -> service.refresh("guest", id));
        assertEquals("PENDING", booking().getString("paymentStatus"));
    }

    @Test
    public void signedEventStillRequiresProviderConfirmation() throws Exception {
        service.checkout("guest", id);
        service.webhook(new byte[0]);
        assertEquals("PENDING", booking().getString("paymentStatus"));
        provider.status = "PAID";
        service.webhook(new byte[0]);
        service.webhook(new byte[0]);
        assertEquals("PAID", booking().getString("paymentStatus"));
    }

    @Test
    public void excessMoneyNeedsReview() throws Exception {
        service.checkout("guest", id);
        provider.status = "PAID";
        provider.received = 45000;
        service.refresh("guest", id);
        assertEquals("REVIEW_REQUIRED", booking().getString("paymentStatus"));
    }

    @Test
    public void concurrentRequestsReserveOneOrder() throws Exception {
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> service.checkout("guest", id));
            var two = pool.submit(() -> service.checkout("guest", id));
            assertEquals(
                    one.get(30, java.util.concurrent.TimeUnit.SECONDS).get("orderCode"),
                    two.get(30, java.util.concurrent.TimeUnit.SECONDS).get("orderCode"));
        } finally {
            pool.shutdownNow();
        }
    }

    DocumentSnapshot booking() throws Exception {
        return db.collection("bookings").document(id).get().get();
    }

    @Test
    public void existingQrPaymentForBlockedHostNeedsReview() throws Exception {
        service.checkout("guest", id);
        db.collection("users").document("host").update("status", "BLOCKED").get();
        provider.status = "PAID";
        service.refresh("guest", id);
        assertEquals("REVIEW_REQUIRED", booking().getString("paymentStatus"));
        assertEquals("CONFIRMED", booking().getString("status"));
    }

    @Test
    public void cancellationClosesProviderLinkWithoutRevivingBooking() throws Exception {
        service.checkout("guest", id);
        db.collection("bookings").document(id).update("status", "CANCELLED").get();
        service.reconcilePending();
        assertEquals(1, provider.cancels);
        assertEquals("CANCELLED", service.refresh("guest", id).get("status"));
        assertEquals("CANCELLED", booking().getString("status"));
        assertEquals("UNPAID", booking().getString("paymentStatus"));
    }

    @Test
    public void expiredQrIsClosedEvenWithoutAnOpenAndroidScreen() throws Exception {
        service.checkout("guest", id);
        db.collection("payments").document(id).update("expiresAt", System.currentTimeMillis() / 1000 - 1).get();
        service.reconcilePending();
        assertEquals("EXPIRED", service.refresh("guest", id).get("status"));
        assertEquals(1, provider.cancels);
        assertEquals("CONFIRMED", booking().getString("status"));
    }

    @Test
    public void paymentWinningCancellationRaceStillRequiresReview() throws Exception {
        service.checkout("guest", id);
        db.collection("bookings").document(id).update("status", "CANCELLED").get();
        provider.payDuringCancel = true;
        assertEquals("REVIEW_REQUIRED", service.refresh("guest", id).get("status"));
        assertEquals("CANCELLED", booking().getString("status"));
    }

    @Test
    public void failedProviderCancellationCanBeRetried() throws Exception {
        service.checkout("guest", id);
        db.collection("bookings").document(id).update("status", "CANCELLED").get();
        provider.cancelFails = true;
        assertThrows(Exception.class, () -> service.refresh("guest", id));
        assertEquals("PENDING", db.collection("payments").document(id).get().get().getString("status"));
        provider.cancelFails = false;
        assertEquals("CANCELLED", service.refresh("guest", id).get("status"));
    }

    // Lịch có thêm một booking khác để bắt lỗi xóa nhầm slot hoặc giảm bộ đếm hai lần.
    private String instant(boolean fixed) throws Exception {
        String room = "room_" + id;
        List<String> keys = fixed ? List.of(room + "_202611", room + "_202612", room + "_202701") : List.of(room + "_202611");
        db.collection("rooms").document(room).set(Map.of("activeBookingCount", fixed ? 1 : 2,
                "bookingRevision", 1, "approvalStatus", "APPROVED", "listingStatus", "ACTIVE")).get();
        db.collection("bookings").document(id).update(Map.of("paymentFlow", "INSTANT_5M", "status", "PENDING",
                "createdAt", Timestamp.now(), "roomId", room, "activeMode", fixed ? "FIXED" : "HOURLY",
                "monthKeys", keys, "slotKeys", fixed ? List.of() : List.of(10,11))).get();
        for (String key : keys) db.collection("availabilityMonths").document(key).set(Map.of(
                "roomId", room, "fixedBookingId", fixed ? id : "", "slotsBusy", fixed ? List.of() : List.of(10,11,20),
                "hourlyBookings", fixed ? Map.of() : Map.of(id, List.of(10,11), "other", List.of(20)),
                "pendingPaymentIds", List.of(id))).get();
        return room;
    }

    private void expireClock() throws Exception {
        db.collection("bookings").document(id).update("createdAt", Timestamp.of(new Date(System.currentTimeMillis()-301000))).get();
        var p = db.collection("payments").document(id);
        if (p.get().get().exists()) p.update("expiresAt", System.currentTimeMillis()/1000-1).get();
    }

    @Test public void instantPaymentConfirmsAndKeepsOnlyItsHold() throws Exception {
        String room = instant(false);
        long before = System.currentTimeMillis()/1000;
        var qr = service.checkout("guest", id);
        assertTrue(((Number) qr.get("expiresAt")).longValue() <= before + 300);
        assertEquals("PENDING", booking().getString("status"));
        provider.status = "PAID";
        service.refresh("guest", id); service.refresh("guest", id);
        assertEquals("CONFIRMED", booking().getString("status"));
        assertEquals(2L, (long) db.collection("rooms").document(room).get().get().getLong("activeBookingCount"));
        var month = db.collection("availabilityMonths").document(room+"_202611").get().get();
        assertEquals(List.of(), month.get("pendingPaymentIds"));
        assertEquals(List.of(10L,11L,20L), month.get("slotsBusy"));
    }

    @Test public void expiryReleasesOnlyOwnHourlySlotsOnce() throws Exception {
        String room = instant(false);
        service.checkout("guest", id); expireClock();
        service.refresh("guest", id); service.refresh("guest", id);
        assertEquals("EXPIRED", booking().getString("status"));
        assertEquals("UNPAID", booking().getString("paymentStatus"));
        assertEquals(1L, (long) db.collection("rooms").document(room).get().get().getLong("activeBookingCount"));
        assertEquals(List.of(20L), db.collection("availabilityMonths").document(room+"_202611").get().get().get("slotsBusy"));
    }

    @Test public void appClosingBeforeQrStillReleasesQuarter() throws Exception {
        String room = instant(true); expireClock();
        service.reconcilePending(); service.reconcilePending();
        assertEquals("EXPIRED", booking().getString("status"));
        assertEquals(0L, (long) db.collection("rooms").document(room).get().get().getLong("activeBookingCount"));
        for (String suffix : List.of("202611","202612","202701"))
            assertEquals("", db.collection("availabilityMonths").document(room+"_"+suffix).get().get().getString("fixedBookingId"));
    }

    @Test public void expiredMoneyNeverConfirmsEvenBeforeCleanup() throws Exception {
        String room = instant(false); service.checkout("guest", id); expireClock();
        provider.status = "PAID";
        assertEquals("REVIEW_REQUIRED", service.refresh("guest", id).get("status"));
        assertEquals("EXPIRED", booking().getString("status"));
        assertEquals("MANUAL_REVIEW", booking().getString("refundStatus"));
        assertEquals(List.of(20L), db.collection("availabilityMonths").document(room+"_202611").get().get().get("slotsBusy"));
    }

    @Test public void lateWebhookNeverReclaimsReleasedCalendar() throws Exception {
        String room = instant(false); service.checkout("guest", id); expireClock(); service.refresh("guest", id);
        provider.status = "PAID"; service.webhook(new byte[0]);
        assertEquals("REVIEW_REQUIRED", booking().getString("paymentStatus"));
        assertEquals("EXPIRED", booking().getString("status"));
        assertEquals(1L, (long) db.collection("rooms").document(room).get().get().getLong("activeBookingCount"));
    }

    @Test public void retryDoesNotExtendInstantDeadline() throws Exception {
        instant(false);
        var first = service.checkout("guest", id);
        var retry = service.checkout("guest", id);
        assertEquals(first.get("expiresAt"), retry.get("expiresAt"));
        assertEquals(first.get("orderCode"), retry.get("orderCode"));
    }

    @Test public void suspendedRoomPaidNeedsReviewAndReleasesHold() throws Exception {
        String room = instant(false); service.checkout("guest", id);
        db.collection("rooms").document(room).update("listingStatus", "SUSPENDED").get();
        provider.status = "PAID"; service.refresh("guest", id);
        assertEquals("REVIEW_REQUIRED", booking().getString("paymentStatus"));
        assertEquals("EXPIRED", booking().getString("status"));
    }

    @Test public void uncertainProviderFailureRetainsHoldForReconciliation() throws Exception {
        String room = instant(false); service.checkout("guest", id); expireClock();
        provider.fail = true;
        assertThrows(Exception.class, () -> service.refresh("guest", id));
        assertEquals("PENDING", booking().getString("status"));
        assertEquals(List.of(10L,11L,20L), db.collection("availabilityMonths").document(room+"_202611").get().get().get("slotsBusy"));
        provider.fail = false; service.refresh("guest", id);
        assertEquals("EXPIRED", booking().getString("status"));
    }

    @Test public void providerConfirmedMissingCreationCanExpire() throws Exception {
        instant(false); provider.fail = true;
        assertThrows(Exception.class, () -> service.checkout("guest", id));
        expireClock(); provider.missing = true;
        service.reconcilePending();
        assertEquals("EXPIRED", booking().getString("status"));
    }

    @Test public void partialTransferAtExpiryIsRecordedForManualReview() throws Exception {
        instant(false); service.checkout("guest", id); expireClock(); provider.partial = 1000;
        assertEquals("REVIEW_REQUIRED", service.refresh("guest", id).get("status"));
        assertEquals(1000L, (long) booking().getLong("paidAmount"));
        assertEquals("EXPIRED", booking().getString("status"));
        assertEquals("MANUAL_REVIEW", booking().getString("refundStatus"));
    }

    @Test public void bankTimestampBeforeDeadlineAcceptsDelayedWebhook() throws Exception {
        instant(false); service.checkout("guest", id); expireClock();
        provider.transactionDate = java.time.Instant.now().minusSeconds(5).toString();
        provider.status = "PAID";
        assertEquals("PAID", service.refresh("guest", id).get("status"));
        assertEquals("CONFIRMED", booking().getString("status"));
    }

    @Test public void forgedMissingCalendarNeverConfirmsPaidBooking() throws Exception {
        String room = instant(false); service.checkout("guest", id);
        db.collection("availabilityMonths").document(room + "_202611").update("slotsBusy", List.of(20)).get();
        provider.status = "PAID";
        assertEquals("REVIEW_REQUIRED", service.refresh("guest", id).get("status"));
        assertNotEquals("CONFIRMED", booking().getString("status"));
    }

    static class Fake implements PaymentGateway {
        int creates;
        boolean fail, missing;
        public boolean isMissing(Exception error) { return missing; }
        int cancels;
        boolean payDuringCancel, cancelFails;
        long order, amount = 40000, received = 40000;
        long partial;
        String transactionDate;
        String status = "PENDING";

        public JsonNode create(long o, long a, long expires, String base) {
            creates++;
            order = o;
            if (fail) throw new IllegalStateException("timeout");
            return node();
        }

        public JsonNode get(long o) {
            if (fail) throw new IllegalStateException("timeout");
            return node();
        }

        public JsonNode cancel(long o, String reason) {
            cancels++;
            if (payDuringCancel) { status = "PAID"; throw new IllegalStateException("already paid"); }
            if (cancelFails) throw new IllegalStateException("cancel timeout");
            status = "CANCELLED";
            return node();
        }

        public JsonNode verify(byte[] body) {
            return new ObjectMapper()
                    .createObjectNode()
                    .put("orderCode", order)
                    .put("code", "00")
                    .put("amount", 40000)
                    .put("paymentLinkId", "link");
        }

        JsonNode node() {
            var result = new ObjectMapper()
                    .createObjectNode()
                    .put("orderCode", order)
                    .put("amount", amount)
                    .put("status", status)
                    .put("amountPaid", status.equals("PAID") ? received : partial)
                    .put("amountRemaining", 0)
                    .put("paymentLinkId", "link")
                    .put("id", "link")
                    .put("qrCode", "fixture-qr")
                    .put("checkoutUrl", "https://pay.payos.vn/web/link");
            if (transactionDate != null) result.putArray("transactions").addObject()
                    .put("amount", received).put("transactionDateTime", transactionDate);
            return result;
        }
    }
}
