package vn.edu.ueh.phuclu.broomsharing.model;

// Bản đọc giao dịch do backend ghi. Android không được tự xác nhận đã thu tiền.
public class Payment {
    public String bookingId = "";
    public String status = "";
    public long orderCode;
    public long amount;
    public long paidAmount;
    public long expiresAt;
    public String qrCode = "";
    public String checkoutUrl = "";
    public String accountName = "";
    public String accountNumber = "";
    public String bin = "";

    public boolean received() {
        return "PAID".equals(status) || "REVIEW_REQUIRED".equals(status);
    }

    public long remainingSeconds(long nowMillis) {
        return Math.max(0, expiresAt - nowMillis / 1000);
    }

    // Chỉ dùng để ẩn thao tác đã hết hạn; server vẫn quyết định trạng thái tiền.
    public boolean canPay(long nowMillis) {
        return "PENDING".equals(status) && remainingSeconds(nowMillis) > 0;
    }

    public boolean canReplaceWith(Payment next) {
        if (received()) return next.received();
        if ("CANCELLED".equals(status) || "EXPIRED".equals(status))
            return next.received() || "CANCELLED".equals(next.status) || "EXPIRED".equals(next.status);
        return true;
    }
}
