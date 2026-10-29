package vn.edu.ueh.broomsharing.payment;

import static org.junit.Assert.*;

import org.junit.*;

import vn.payos.PayOS;
import vn.payos.model.webhooks.*;

// SDK kiểm HMAC thật, không gọi ngân hàng; chữ ký không hợp lệ không được tới Firestore.
public class WebhookSignatureTest {
    private PayOS sdk;
    private Webhook body;

    @Before
    public void fixture() {
        sdk = new PayOS("test-client", "test-key", "fixture-checksum-not-a-secret");
        WebhookData data =
                WebhookData.builder()
                        .orderCode(12345L)
                        .amount(40000L)
                        .code("00")
                        .paymentLinkId("fixture-link")
                        .description("DEMO")
                        .desc("success")
                        .currency("VND")
                        .reference("DEMO-REFERENCE")
                        .accountNumber("0000")
                        .transactionDateTime("2026-10-26 20:00:00")
                        .build();
        String signature =
                sdk.getCrypto().createSignatureFromObj(data, "fixture-checksum-not-a-secret");
        body =
                Webhook.builder()
                        .code("00")
                        .desc("success")
                        .success(true)
                        .data(data)
                        .signature(signature)
                        .build();
    }

    @After
    public void close() {
        sdk.close();
    }

    @Test
    public void correctSignatureAccepted() {
        assertEquals(40000L, sdk.webhooks().verify(body).getAmount().longValue());
    }

    @Test
    public void changedAmountRejected() {
        body.getData().setAmount(1L);
        assertThrows(Exception.class, () -> sdk.webhooks().verify(body));
    }

    @Test
    public void changedOrderRejected() {
        body.getData().setOrderCode(2L);
        assertThrows(Exception.class, () -> sdk.webhooks().verify(body));
    }

    @Test
    public void changedSignatureRejected() {
        body.setSignature("00");
        assertThrows(Exception.class, () -> sdk.webhooks().verify(body));
    }

    @Test
    public void missingDataRejected() {
        assertThrows(Exception.class, () -> sdk.webhooks().verify(new Webhook()));
    }
}
