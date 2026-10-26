package vn.edu.ueh.phuclu.broomsharing.utils;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;

import java.util.Arrays;

// Chính sách khép lượt thuê và xác minh việc sử dụng; Repository/Rules kiểm lại quyền trên server.
public final class BookingResolutionPolicy {
    private BookingResolutionPolicy() {}

    public static boolean holds(String status) {
        return Arrays.asList("PENDING", "CONFIRMED", "CHECKED_IN").contains(status);
    }

    public static boolean terminal(String status) {
        return Arrays.asList(
                        "REJECTED", "CANCELLED", "COMPLETED", "EXPIRED", "NO_SHOW", "TERMINATED")
                .contains(status);
    }

    public static void validate(
            Booking b,
            String target,
            String reason,
            boolean admin,
            boolean blockedOwner,
            long now) {
        BookingPolicy.require(reason != null && reason.length() <= 500, "Lý do tối đa 500 ký tự.");
        boolean ended = b.endAt != null && b.endAt.getTime() <= now;
        if ("NO_SHOW".equals(target)) {
            BookingPolicy.require(
                    "CONFIRMED".equals(b.status) && ended && !reason.trim().isEmpty(),
                    "Chỉ xác nhận khách không đến sau giờ kết thúc, khi chưa check-in và có lý"
                        + " do.");
            BookingPolicy.require(!admin || blockedOwner, "Admin chỉ xử lý thay Host bị khóa.");
        } else if ("TERMINATED".equals(target)) {
            BookingPolicy.require(
                    admin
                            && blockedOwner
                            && "CHECKED_IN".equals(b.status)
                            && b.startAt != null
                            && b.startAt.getTime() <= now
                            && !reason.trim().isEmpty(),
                    "Admin chỉ kết thúc do sự cố từng lượt đã sử dụng của Host bị khóa, có lý do.");
        } else if ("COMPLETED".equals(target)) {
            // Admin được sửa kết luận NO_SHOW hoặc bổ sung chứng cứ cho đơn cũ; không nhả lịch lần
            // hai.
            boolean correction =
                    admin
                            && ("CONFIRMED".equals(b.status)
                                    || "NO_SHOW".equals(b.status)
                                    || ("COMPLETED".equals(b.status)
                                            && b.usageConfirmedAt == null));
            BookingPolicy.require(
                    ended
                            && ("CHECKED_IN".equals(b.status)
                                    || "CONFIRMED".equals(b.status)
                                    || correction),
                    "Chưa đủ điều kiện hoàn tất lượt thuê.");
            BookingPolicy.require(
                    !admin || correction || blockedOwner, "Admin chỉ xử lý thay Host bị khóa.");
            BookingPolicy.require(
                    (!admin && "CHECKED_IN".equals(b.status)) || !reason.trim().isEmpty(),
                    "Cần lý do xác minh việc sử dụng trước khi hoàn tất.");
        } else if (admin && !"EXPIRED".equals(target)) {
            BookingPolicy.require(
                    blockedOwner
                            && "CANCELLED".equals(target)
                            && Arrays.asList("PENDING", "CONFIRMED").contains(b.status)
                            && !reason.trim().isEmpty(),
                    "Admin chỉ hủy đơn chưa check-in của Host bị khóa.");
        } else {
            BookingPolicy.transition(b, target, reason, now);
        }
    }
}
