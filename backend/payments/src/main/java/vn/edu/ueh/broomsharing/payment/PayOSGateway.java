package vn.edu.ueh.broomsharing.payment;

import com.fasterxml.jackson.databind.*;

import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.webhooks.Webhook;

// SDK chính thức ký yêu cầu và kiểm chữ ký phản hồi/webhook bằng checksum key ở server.
public final class PayOSGateway implements PaymentGateway {
    private final PayOS client;
    private final ObjectMapper json = new ObjectMapper();

    public PayOSGateway(Config config) {
        client =
                new PayOS(
                        ClientOptions.builder()
                                .clientId(config.required("PAYOS_CLIENT_ID"))
                                .apiKey(config.required("PAYOS_API_KEY"))
                                .checksumKey(config.required("PAYOS_CHECKSUM_KEY"))
                                .timeoutMs(15000)
                                .maxRetries(1)
                                .build());
    }

    public JsonNode create(long order, long amount, long expiresAt, String base) throws Exception {
        return json.valueToTree(
                client.paymentRequests()
                        .create(
                                CreatePaymentLinkRequest.builder()
                                        .orderCode(order)
                                        .amount(amount)
                                        .description("BS " + order)
                                        .returnUrl(base + "/payment/return")
                                        .cancelUrl(base + "/payment/cancel")
                                        .expiredAt(expiresAt)
                                        .build()));
    }

    public JsonNode get(long order) throws Exception {
        return json.valueToTree(client.paymentRequests().get(order));
    }

    @Override
    public boolean isMissing(Exception error) {
        return error instanceof vn.payos.exception.NotFoundException;
    }

    public JsonNode cancel(long order, String reason) throws Exception {
        return json.valueToTree(client.paymentRequests().cancel(order, reason));
    }

    // Chỉ dữ liệu qua xác minh chữ ký bằng SDK chính thức mới được chuyển sang PaymentService.
    public JsonNode verify(byte[] body) throws Exception {
        return json.valueToTree(client.webhooks().verify(json.readValue(body, Webhook.class)));
    }
}
