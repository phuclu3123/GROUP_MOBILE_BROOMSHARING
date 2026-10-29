package vn.edu.ueh.broomsharing.payment;

import com.fasterxml.jackson.databind.JsonNode;

// Cổng nhà cung cấp để kiểm thử lỗi mạng/webhook mà không phát sinh giao dịch tiền thật.
public interface PaymentGateway {
    // Chỉ lỗi không tồn tại được nhà cung cấp xác nhận mới cho phép khép QR chưa tạo xong.
    default boolean isMissing(Exception error) { return false; }
    JsonNode create(long order, long amount, long expiresAt, String base) throws Exception;

    JsonNode get(long order) throws Exception;

    JsonNode cancel(long order, String reason) throws Exception;

    JsonNode verify(byte[] body) throws Exception;
}
