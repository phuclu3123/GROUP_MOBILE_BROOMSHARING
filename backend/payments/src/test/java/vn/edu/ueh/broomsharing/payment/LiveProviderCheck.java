package vn.edu.ueh.broomsharing.payment;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;

import java.nio.file.*;
import java.util.*;

// Opt-in: tạo một link 2.000đ rồi hủy ngay. Không chuyển tiền và không đổi webhook kênh.
public final class LiveProviderCheck {
    public static void main(String[] args) throws Exception {
        Config config = new Config(Path.of(".env"));
        PayOS sdk =
                new PayOS(
                        config.required("PAYOS_CLIENT_ID"),
                        config.required("PAYOS_API_KEY"),
                        config.required("PAYOS_CHECKSUM_KEY"));
        long order =
                90_000_000_000_000L + new java.security.SecureRandom().nextLong(9_000_000_000_000L);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            var created =
                    sdk.paymentRequests()
                            .create(
                                    CreatePaymentLinkRequest.builder()
                                            .orderCode(order)
                                            .amount(2000L)
                                            .description("BS API CHECK")
                                            .returnUrl("https://example.com/payment-return")
                                            .cancelUrl("https://example.com/payment-cancel")
                                            .expiredAt(System.currentTimeMillis() / 1000 + 600)
                                            .build());
            result.put("created", created.getOrderCode() == order && created.getAmount() == 2000);
            result.put("qrPresent", created.getQrCode() != null && !created.getQrCode().isBlank());
            var read = sdk.paymentRequests().get(order);
            result.put("readStatus", read.getStatus().toString());
            result.put("amountPaid", read.getAmountPaid());
        } finally {
            try {
                result.put(
                        "cleanupStatus",
                        sdk.paymentRequests()
                                .cancel(order, "API acceptance; no money transferred")
                                .getStatus()
                                .toString());
            } finally {
                sdk.close();
                Path output =
                        Path.of("../../output/verification/payos-2026-10-26/provider-live.json");
                Files.createDirectories(output.getParent());
                new ObjectMapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValue(output.toFile(), result);
            }
        }
        System.out.println(new ObjectMapper().writeValueAsString(result));
    }
}
