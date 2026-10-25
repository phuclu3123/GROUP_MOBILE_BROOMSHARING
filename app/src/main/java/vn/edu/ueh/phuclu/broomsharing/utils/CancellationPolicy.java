package vn.edu.ueh.phuclu.broomsharing.utils;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;

/** Versioned, deterministic policy. Payment integrations are deliberately unavailable. */
public final class CancellationPolicy {
    public static final int VERSION = 1;

    private CancellationPolicy() {}

    public static long fullWindow(String mode) {
        return "HOURLY".equals(mode) ? 24L * 3600000 : 7L * 86400000;
    }

    public static long partialWindow(String mode) {
        return "HOURLY".equals(mode) ? 6L * 3600000 : 48L * 3600000;
    }

    public static int percent(Booking b, long now) {
        BookingPolicy.require(
                b.startAt != null && b.startAt.getTime() > now,
                "Đã bắt đầu sử dụng; cần yêu cầu xử lý riêng.");
        long remaining = b.startAt.getTime() - now;
        return remaining >= fullWindow(b.activeMode)
                ? 100
                : remaining >= partialWindow(b.activeMode) ? 50 : 0;
    }

    public static boolean versioned(Booking b) {
        return b.priceSnapshot != null
                && b.priceSnapshot.get("cancellationPolicyVersion") instanceof Number
                && ((Number) b.priceSnapshot.get("cancellationPolicyVersion")).intValue()
                        == VERSION;
    }

    public static String terms(String mode) {
        return "HOURLY".equals(mode)
                ? "Hủy trước ít nhất 24 giờ: hoàn 100%; từ 6 đến dưới 24 giờ: hoàn 50%; dưới 6 giờ:"
                      + " không hoàn tiền trả trước."
                : "Hủy trước ít nhất 7 ngày: hoàn 100%; từ 48 giờ đến dưới 7 ngày: hoàn 50%; dưới"
                      + " 48 giờ: không hoàn tiền trả trước.";
    }

    public static String summary(Booking b, long now) {
        if (!versioned(b))
            return "Booking cũ giữ nguyên điều kiện đã đặt; chưa áp dụng chính sách hủy mới.\n"
                       + "Chưa ghi nhận giao dịch tiền thật; không phát sinh hoàn tiền tự động.";
        String who =
                b.ownerUid != null && b.ownerUid.equals(b.cancelledByUid)
                        ? "Host"
                        : b.guestUid != null && b.guestUid.equals(b.cancelledByUid)
                                ? "người thuê"
                                : "bên quản trị";
        String state =
                b.cancelledAt == null
                        ? ""
                        : "\nĐã hủy bởi "
                                + who
                                + ": "
                                + TimeUtils.formatVietnam(b.cancelledAt)
                                + ". Lịch đã được giải phóng. Không phát sinh hoàn tiền vì chưa"
                                + " thanh toán.";
        return terms(b.activeMode)
                + "\n"
                + "Chính sách v1 áp dụng trên tiền trả trước thực nhận. Thanh toán đang phát triển."
                + state;
    }

    public static String preview(Booking b, long now) {
        return (versioned(b)
                        ? "Mức hoàn theo thời điểm hiện tại: "
                                + percent(b, now)
                                + "% tiền trả trước thực nhận.\n"
                        : "Booking cũ chưa áp dụng chính sách hủy mới.\n")
                + "Chưa ghi nhận thanh toán: số tiền hoàn là 0 đ.\n"
                + "Hủy thành công sẽ nhả lịch ngay, không cần Admin duyệt.\n"
                + "Mức chính sách được kiểm tra lại khi xác nhận; nếu vừa qua mốc, app sẽ yêu cầu"
                + " bạn xem lại.";
    }
}
