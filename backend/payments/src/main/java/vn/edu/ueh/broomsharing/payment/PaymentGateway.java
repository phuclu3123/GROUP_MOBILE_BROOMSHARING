package vn.edu.ueh.broomsharing.payment;

import com.fasterxml.jackson.databind.JsonNode;

// Cổng nhà cung cấp để kiểm thử lỗi mạng/webhook mà không phát sinh giao dịch tiền thật.
public interface PaymentGateway {
    JsonNode create(long order, long amount, long expiresAt, String base) throws Exception;

    JsonNode get(long order) throws Exception;

    JsonNode verify(byte[] body) throws Exception;
}
