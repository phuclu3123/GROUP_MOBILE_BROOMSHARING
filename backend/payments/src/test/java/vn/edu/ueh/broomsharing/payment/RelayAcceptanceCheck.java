package vn.edu.ueh.broomsharing.payment;

import com.fasterxml.jackson.databind.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import com.google.firebase.cloud.FirestoreClient;

import vn.payos.PayOS;
import vn.payos.model.webhooks.*;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

// Sự kiện hợp đồng tự sinh để thử tuyến WEB_TCC -> Java. Không phải webhook ngân hàng thật.
public final class RelayAcceptanceCheck {
    public static void main(String[] args) throws Exception {
        Config config = new Config(Path.of(".env"));
        ObjectMapper json = new ObjectMapper();
        Path evidence = Path.of("../../output/verification/payos-2026-10-26");
        JsonNode previous = json.readTree(evidence.resolve("live-android-receipt.json").toFile());
        String id = previous.path("bookingId").asText();
        long order = previous.path("orderCode").asLong();
        GoogleCredentials credentials;
        try (InputStream file =
                Files.newInputStream(Path.of(config.required("GOOGLE_APPLICATION_CREDENTIALS")))) {
            credentials = GoogleCredentials.fromStream(file);
        }
        FirebaseApp app =
                FirebaseApp.initializeApp(
                        FirebaseOptions.builder()
                                .setProjectId(config.required("FIREBASE_PROJECT_ID"))
                                .setCredentials(credentials)
                                .build(),
                        "relay-check");
        var db = FirestoreClient.getFirestore(app, "broomsharing");
        PayOS sdk =
                new PayOS(
                        config.required("PAYOS_CLIENT_ID"),
                        config.required("PAYOS_API_KEY"),
                        config.required("PAYOS_CHECKSUM_KEY"));
        Map<String, Object> receipt = new LinkedHashMap<>();
        try {
            var before = db.collection("payments").document(id).get().get();
            PaymentPolicy.require(
                    "PENDING".equals(before.getString("status")),
                    "Use an unpaid demo order for the contract check.");
            WebhookData data =
                    WebhookData.builder()
                            .orderCode(order)
                            .amount(previous.path("advanceAmount").asLong())
                            .code("00")
                            .desc("success")
                            .description("BS " + order)
                            .currency("VND")
                            .paymentLinkId(before.getString("paymentLinkId"))
                            .accountNumber("DEMO")
                            .reference("ACCEPTANCE-NO-BANK-TRANSFER")
                            .transactionDateTime(LocalDateTime.now().toString())
                            .counterAccountBankId("")
                            .counterAccountBankName("")
                            .counterAccountName("")
                            .counterAccountNumber("")
                            .virtualAccountName("")
                            .virtualAccountNumber("")
                            .build();
            Webhook body =
                    Webhook.builder()
                            .code("00")
                            .desc("success")
                            .success(true)
                            .data(data)
                            .signature(
                                    sdk.getCrypto()
                                            .createSignatureFromObj(
                                                    data, config.required("PAYOS_CHECKSUM_KEY")))
                            .build();
            HttpClient http =
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(15))
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
            String endpoint = config.required("PAYOS_RELAY_BASE_URL") + "/api/payos/webhook";
            List<String> checks = new ArrayList<>();
            for (int attempt = 0; attempt < 2; attempt++) {
                HttpResponse<String> response = post(http, json, endpoint, body);
                PaymentPolicy.require(
                        response.statusCode() == 200
                                && "BROOMSHARING"
                                        .equals(
                                                json.readTree(response.body())
                                                        .path("relayed")
                                                        .asText()),
                        "WEB_TCC did not confirm forwarding to Java.");
            }
            checks.add("Two signed contract events crossed deployed WEB_TCC and Java successfully");
            body.setSignature("invalid-signature");
            PaymentPolicy.require(
                    post(http, json, endpoint, body).statusCode() == 400,
                    "Invalid signature was not rejected at ingress.");
            checks.add("Invalid signature rejected by deployed WEB_TCC before forwarding");
            data.setAmount(1L);
            body.setSignature(
                    sdk.getCrypto()
                            .createSignatureFromObj(data, config.required("PAYOS_CHECKSUM_KEY")));
            PaymentPolicy.require(
                    post(http, json, endpoint, body).statusCode() == 503,
                    "Mismatch must remain retryable and must not be acknowledged.");
            checks.add(
                    "Java rejected a signed wrong-amount event; WEB_TCC returned retryable"
                        + " failure");
            var after = db.collection("payments").document(id).get().get();
            var booking = db.collection("bookings").document(id).get().get();
            PaymentPolicy.require(
                    "PENDING".equals(after.getString("status"))
                            && after.get("paidAmount") == null
                            && "CONFIRMED".equals(booking.getString("status"))
                            && "PENDING".equals(booking.getString("paymentStatus")),
                    "Contract event incorrectly changed booking or payment state.");
            checks.add(
                    "Provider still reports unpaid: signed test events never became a fictitious"
                        + " payment");
            receipt.put("checkedAt", Instant.now().toString());
            receipt.put("bookingId", id);
            receipt.put("orderCode", order);
            receipt.put("contractEventsGeneratedByTest", true);
            receipt.put("bankWebhookVerified", false);
            receipt.put("bankTransferPerformed", false);
            receipt.put("checks", checks);
            receipt.put("passed", true);
            json.writerWithDefaultPrettyPrinter()
                    .writeValue(evidence.resolve("web-tcc-relay-live.json").toFile(), receipt);
            System.out.println(
                    "PASS: deployed WEB_TCC -> Java relay contract; no bank transfer or fake"
                        + " PAID.");
        } finally {
            sdk.close();
            db.close();
            app.delete();
        }
    }

    private static HttpResponse<String> post(
            HttpClient client, ObjectMapper json, String endpoint, Webhook body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(40))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
