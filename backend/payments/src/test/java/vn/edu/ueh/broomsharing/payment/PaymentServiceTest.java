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
        db = FirestoreClient.getFirestore(app, "payment_backend_qa");
    }

    @AfterClass
    public static void close() throws Exception {
        if (db != null) db.close();
        if (app != null) app.delete();
    }

    @Before
    public void seed() throws Exception {
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

    static class Fake implements PaymentGateway {
        int creates;
        boolean fail;
        long order, amount = 40000, received = 40000;
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

        public JsonNode verify(byte[] body) {
            return new ObjectMapper()
                    .createObjectNode()
                    .put("orderCode", order)
                    .put("code", "00")
                    .put("amount", 40000)
                    .put("paymentLinkId", "link");
        }

        JsonNode node() {
            return new ObjectMapper()
                    .createObjectNode()
                    .put("orderCode", order)
                    .put("amount", amount)
                    .put("status", status)
                    .put("amountPaid", status.equals("PAID") ? received : 0)
                    .put("amountRemaining", 0)
                    .put("paymentLinkId", "link")
                    .put("id", "link")
                    .put("qrCode", "fixture-qr")
                    .put("checkoutUrl", "https://pay.payos.vn/web/link");
        }
    }
}
