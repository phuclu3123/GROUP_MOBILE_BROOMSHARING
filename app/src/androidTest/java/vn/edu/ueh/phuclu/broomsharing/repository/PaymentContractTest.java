package vn.edu.ueh.phuclu.broomsharing.repository;

import static org.junit.Assert.*;

import com.google.zxing.*;
import com.google.zxing.common.*;

import org.json.JSONObject;
import org.junit.Test;

// Contract Android chạy trên thiết bị; không đăng nhập, không giả nhận có chuyển khoản thật.
public class PaymentContractTest {
    JSONObject fixture() throws Exception {
        return new JSONObject()
                .put("bookingId", "booking_demo")
                .put("orderCode", 12345)
                .put("amount", 40000)
                .put("status", "PENDING")
                .put("checkoutUrl", "https://pay.payos.vn/web/demo")
                .put("qrCode", "DEMO QR CONTENT");
    }

    @Test
    public void pendingIsNotPaid() throws Exception {
        assertFalse(PaymentRepository.decode(fixture(), "booking_demo").received());
    }

    @Test
    public void confirmedReceivedAmountIsReadable() throws Exception {
        var json = fixture().put("status", "PAID").put("paidAmount", 40000);
        assertTrue(PaymentRepository.decode(json, "booking_demo").received());
    }

    @Test
    public void foreignBookingRejected() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> PaymentRepository.decode(fixture(), "another_booking"));
    }

    @Test
    public void untrustedCheckoutRejected() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PaymentRepository.decode(
                                fixture().put("checkoutUrl", "https://evil.example/pay"),
                                "booking_demo"));
    }

    @Test
    public void httpCheckoutRejected() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PaymentRepository.decode(
                                fixture().put("checkoutUrl", "http://pay.payos.vn/web/demo"),
                                "booking_demo"));
    }

    @Test
    public void unsupportedStatusRejected() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PaymentRepository.decode(
                                fixture().put("status", "SUCCESS_FAKE"), "booking_demo"));
    }

    @Test
    public void paidWithoutReceivedAmountRejected() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> PaymentRepository.decode(fixture().put("status", "PAID"), "booking_demo"));
    }

    @Test
    public void qrRoundTripPreservesExactProviderPayload() throws Exception {
        String content = "000201-DEMO-PAYOS-QR-40000";
        BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 768, 768);
        int[] pixels = new int[768 * 768];
        for (int y = 0; y < 768; y++)
            for (int x = 0; x < 768; x++)
                pixels[y * 768 + x] = matrix.get(x, y) ? 0xff000000 : 0xffffffff;
        BinaryBitmap image =
                new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(768, 768, pixels)));
        assertEquals(content, new MultiFormatReader().decode(image).getText());
    }
}
