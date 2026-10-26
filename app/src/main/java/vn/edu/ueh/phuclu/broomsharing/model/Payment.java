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
}
