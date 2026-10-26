package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import org.json.*;
import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingPaymentActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

// Opt-in: nghiệp vụ thật trên Firebase/Cloudinary/payOS; tạo QR 2.000đ, không tự chuyển tiền.
public class PayOSAcceptanceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();
    private final Properties accounts = new Properties();
    private final JSONObject receipt = new JSONObject();
    private final JSONArray checks = new JSONArray();
    private final String roomId = "payos_demo_20261026_room";
    private File output;

    private static class Answer<T> implements HostRepository.Callback<T> {
        final CompletableFuture<T> future = new CompletableFuture<>();

        public void onSuccess(T value) {
            future.complete(value);
        }

        public void onError(Exception error) {
            future.completeExceptionally(error);
        }

        T get() throws Exception {
            return future.get(90, TimeUnit.SECONDS);
        }
    }

    private void login(String account) throws Exception {
        auth.signOut();
        Tasks.await(
                auth.signInWithEmailAndPassword(
                        accounts.getProperty(account + ".email"),
                        accounts.getProperty(account + ".password")),
                45,
                TimeUnit.SECONDS);
        Tasks.await(auth.getCurrentUser().getIdToken(true), 30, TimeUnit.SECONDS);
    }

    private DocumentSnapshot read(String collection, String id) throws Exception {
        return Tasks.await(
                db.collection(collection).document(id).get(Source.SERVER), 45, TimeUnit.SECONDS);
    }

    private void check(String message) throws Exception {
        checks.put(message);
        receipt.put("checks", checks);
        try (FileOutputStream out = new FileOutputStream(new File(output, "receipt.json"))) {
            out.write(receipt.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private Payment payment(String id, boolean create) throws Exception {
        Answer<Payment> answer = new Answer<>();
        new PaymentRepository().request(id, create, answer);
        return answer.get();
    }

    @Test
    public void realConfirmedBookingCreatesQrAndDisplaysIt() throws Exception {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runPayosLive")));
        assertTrue(BuildConfig.FIRESTORE_EMULATOR_HOST.isEmpty());
        assertTrue(PaymentRepository.configured());
        output = new File(context.getExternalFilesDir(null), "payos-acceptance");
        assertTrue(output.isDirectory() || output.mkdirs());
        try (InputStream in = new FileInputStream(new File(output, "accounts.properties"))) {
            accounts.load(in);
        }
        receipt.put("scope", "Live Firebase/Cloudinary/payOS QR; no bank transfer");
        receipt.put("bankTransferPerformed", false);

        login("applicant");
        String owner = auth.getUid();
        assertEquals("HOST", read("users", owner).getString("role"));
        HostRepository host = new HostRepository();
        String buildingId = "payos_demo_20261026_building";
        if (!read("buildings", buildingId).exists()) {
            Building b =
                    new Building(
                            owner,
                            "[DEMO PAYOS] Không gian minh họa",
                            "[DEMO] TP.HCM",
                            "TP.HCM",
                            "+84900000900",
                            10.762622,
                            106.660172,
                            "Địa điểm giả định phục vụ nghiệm thu API.");
            b.setId(buildingId);
            b.setContactName("[DEMO] Chủ nhà thử nghiệm");
            b.setCoverUrl("");
            Answer<String> saved = new Answer<>();
            host.addBuilding(b, saved);
            assertEquals(buildingId, saved.get());
        }
        if (!read("rooms", roomId).exists()) {
            List<String> photos;
            try (MediaRepository media = new MediaRepository(context)) {
                Answer<List<String>> uploaded = new Answer<>();
                media.upload(
                        Collections.singletonList(Uri.fromFile(new File(output, "room.jpg"))),
                        "roomMedia",
                        roomId,
                        uploaded);
                photos = uploaded.get();
            }
            Room r = new Room();
            r.setId(roomId);
            r.setBuildingId(buildingId);
            r.setRoomName("[DEMO PAYOS] Phòng thử API 5.000đ/giờ");
            r.setRoomType("Phòng họp");
            r.setCapacity(6);
            r.setArea(30);
            r.setFloor("2");
            r.setAddress("[DEMO] Địa điểm giả định tại TP.HCM");
            r.setActiveMode(Room.MODE_HOURLY);
            r.setPricePerHour(5000);
            r.setPricePerMonth(0);
            r.setPricePerQuarter(0);
            r.setOpenTime("08:00");
            r.setCloseTime("22:00");
            r.setAmenities(Arrays.asList("Wi-Fi", "Điều hòa"));
            r.setRules("Phòng và giá chỉ dùng demo API; ảnh minh họa từ bộ ảnh có nguồn của nhóm.");
            r.setRentalPolicy("Trả trước 40% sau xác nhận. Bản demo chưa có API hoàn tiền.");
            r.setRenewalPolicy("Liên hệ Host; không tự gia hạn.");
            r.setImageUrls(photos);
            r.setApprovalStatus(Room.STATUS_PENDING_REVIEW);
            Answer<String> saved = new Answer<>();
            host.saveRoom(r, saved);
            assertEquals(roomId, saved.get());
        }
        receipt.put("roomId", roomId);
        receipt.put("ownerUid", owner);
        check("Host creates own demo room and uploads a real Cloudinary photo");
        login("admin");
        if (!"APPROVED".equals(read("rooms", roomId).getString("approvalStatus"))) {
            Answer<String> reviewed = new Answer<>();
            new AdminRepository()
                    .review(
                            new ReviewItem("ROOM", roomId, "", ""),
                            true,
                            "[DEMO] Duyệt phòng minh họa để nghiệm thu payOS; không xác minh tài"
                                + " sản thực tế.",
                            false,
                            reviewed);
            assertEquals(roomId, reviewed.get());
        }
        login("guest");
        receipt.put("guestUid", auth.getUid());
        Booking request = new Booking();
        request.roomId = roomId;
        request.activeMode = Room.MODE_HOURLY;
        request.requestKey = "payos_acceptance_" + UUID.randomUUID().toString().replace("-", "");
        ZonedDateTime start =
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                        .plusDays(1)
                        .atTime(10, 0)
                        .atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        request.startAt = Date.from(start.toInstant());
        request.endAt = Date.from(start.plusHours(1).toInstant());
        Answer<String> created = new Answer<>();
        new BookingRepository().create(request, created);
        String id = created.get();
        receipt.put("bookingId", id);
        check("Guest creates PENDING booking with paidAmount=0 through app transaction");
        assertEquals(0L, read("bookings", id).getLong("paidAmount").longValue());
        try {
            payment(id, true);
            fail("PENDING booking must not create a payment");
        } catch (ExecutionException expected) {
            assertNotNull(expected.getCause());
        }
        assertFalse(read("payments", id).exists());
        check("Payment before Host confirmation is rejected without creating an order");
        login("applicant");
        Answer<String> confirmed = new Answer<>();
        new BookingRepository().transition(id, "CONFIRMED", "", confirmed);
        assertEquals(id, confirmed.get());
        try {
            payment(id, true);
            fail("Host cannot create QR on Guest's behalf");
        } catch (ExecutionException expected) {
            assertNotNull(expected.getCause());
        }
        check("Host confirms booking; Host cannot pay as the Guest");
        login("guest");
        Payment p = payment(id, true);
        assertEquals(2000, p.amount);
        assertEquals("PENDING", p.status);
        assertFalse(p.received());
        assertFalse(p.qrCode.isEmpty());
        assertTrue(p.checkoutUrl.startsWith("https://pay.payos.vn/"));
        Payment again = payment(id, true);
        assertEquals(p.orderCode, again.orderCode);
        Payment status = payment(id, false);
        assertEquals("PENDING", status.status);
        assertEquals(0, status.paidAmount);
        receipt.put("orderCode", p.orderCode);
        receipt.put("advanceAmount", p.amount);
        receipt.put("checkoutUrl", p.checkoutUrl);
        check(
                "Real payOS QR is exactly 40% = 2.000đ; retries reuse the same order; provider"
                    + " reports unpaid");
        try {
            Tasks.await(
                    db.collection("bookings")
                            .document(id)
                            .update("paymentStatus", "PAID", "paidAmount", 2000L),
                    30,
                    TimeUnit.SECONDS);
            fail("Client must not mark PAID");
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause() instanceof FirebaseFirestoreException);
        }
        assertEquals("PENDING", read("bookings", id).getString("paymentStatus"));
        check("Deployed Rules reject a direct client attempt to mark this booking PAID");
        Intent intent = new Intent(context, BookingPaymentActivity.class).putExtra("bookingId", id);
        try (ActivityScenario<BookingPaymentActivity> screen = ActivityScenario.launch(intent)) {
            boolean[] visible = {false};
            for (int i = 0; i < 40; i++) {
                screen.onActivity(
                        a -> {
                            ImageView qr = a.findViewById(R.id.payment_qr);
                            visible[0] =
                                    qr != null
                                            && qr.getVisibility() == View.VISIBLE
                                            && qr.getDrawable() != null;
                        });
                if (visible[0]) break;
                Thread.sleep(500);
            }
            assertTrue("QR must render in actual Android Activity", visible[0]);
            screen.recreate();
            Thread.sleep(1500);
            screen.onActivity(
                    a ->
                            assertNotNull(
                                    ((ImageView) a.findViewById(R.id.payment_qr)).getDrawable()));
            Bitmap image =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            try (FileOutputStream file =
                    new FileOutputStream(new File(output, "payment-screen.png"))) {
                image.compress(Bitmap.CompressFormat.PNG, 100, file);
            }
        }
        assertEquals(p.orderCode, read("payments", id).getLong("orderCode").longValue());
        receipt.put("accepted", true);
        check("Actual QR screen renders, survives recreation and retains the same provider order");
    }
}
