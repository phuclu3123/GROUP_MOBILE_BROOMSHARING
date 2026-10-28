package vn.edu.ueh.phuclu.broomsharing.utils;

import java.time.*;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;

// Lịch công khai chỉ có khoảng bận; không cần đọc tên hoặc thông tin khách khác.
public final class BookingAvailability {
    private BookingAvailability() {}

    // So sánh khoảng nửa mở [startAt,endAt) để hai lượt tiếp giáp không bị coi là trùng giờ.
    public static String conflict(Room room, List<Booking> held, Date from, Date to) {
        for (RoomBlackout range : room.getBlackoutRanges())
            if (HostSchedule.overlaps(from, to, range.getStartAt(), range.getEndAt()))
                return "Host khóa lịch";
        for (String value : room.getBlackoutDates()) {
            Date day = Date.from(HostSchedule.blackoutDate(value).atStartOfDay(HostSchedule.ZONE).toInstant());
            if (HostSchedule.overlaps(from, to, day, new Date(day.getTime() + 86400000)))
                return "Host khóa ngày";
        }
        for (Booking booking : held)
            if (HostSchedule.occupies(booking) && HostSchedule.overlaps(from, to, booking.startAt, booking.endAt))
                return booking.isInstantPayment() && "PENDING".equals(booking.status)
                        ? "Tạm giữ, chờ thanh toán" : "Đã đặt";
        return null;
    }

    public static String fixedSummary(Room room, List<Booking> held, LocalDate from, int months, LocalDate loadedUntil) {
        LocalDate start = from.withDayOfMonth(1), end = start.plusMonths(months);
        String conflict = conflict(room, held, at(start), at(end));
        if (conflict == null) return start + " đến " + end + ": Kỳ thuê còn trống";
        for (LocalDate next = start.plusMonths(1); !next.plusMonths(months).isAfter(loadedUntil); next = next.plusMonths(1))
            if (conflict(room, held, at(next), at(next.plusMonths(months))) == null)
                return start + " đến " + end + ": " + conflict
                        + "\nKỳ " + months + " tháng trống kế tiếp trong lịch đã tải: " + next;
        return start + " đến " + end + ": " + conflict
                + "\nChưa tìm được kỳ trống tiếp theo trong lịch 12 tháng đã tải.";
    }

    private static Date at(LocalDate day) {
        return Date.from(day.atStartOfDay(HostSchedule.ZONE).toInstant());
    }
}
