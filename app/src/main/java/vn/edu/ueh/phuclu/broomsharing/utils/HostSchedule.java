package vn.edu.ueh.phuclu.broomsharing.utils;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.RoomBlackout;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/** Pure date and interval rules shared by Host forms and the booking contract. */
public final class HostSchedule {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private HostSchedule() {}

    public static boolean overlaps(Date a, Date b, Date c, Date d) {
        return a != null && b != null && c != null && d != null && a.before(d) && c.before(b);
    }

    public static boolean occupies(Booking b) {
        return Booking.STATUS_PENDING.equals(b.getStatus())
                || "REQUESTED".equals(b.getStatus())
                || Booking.STATUS_CONFIRMED.equals(b.getStatus())
                || "CHECKED_IN".equals(b.getStatus());
    }

    public static String validate(Room r) {
        try {
            if (r.getBlackoutDates().size() > 16)
                return "Tối đa 16 ngày khóa lịch; dùng khoảng khóa cho nhiều ngày liên tiếp.";
            for (String day : r.getBlackoutDates()) blackoutDate(day);
        } catch (Exception e) {
            return "Ngày khóa lịch không hợp lệ.";
        }
        if (Room.MODE_HOURLY.equals(r.getActiveMode())) {
            if (r.getWorkingDays() == null
                    || r.getWorkingDays().isEmpty()
                    || new HashSet<>(r.getWorkingDays()).size() != r.getWorkingDays().size()
                    || r.getWorkingDays().stream().anyMatch(d -> d == null || d < 1 || d > 7))
                return "Chọn ít nhất một ngày hoạt động hợp lệ.";
            int step = r.getSlotMinutes(), min = r.getMinBookingMinutes();
            if ((step != 30 && step != 60) || min <= 0 || min > 1440 || min % step != 0)
                return "Bước giờ là 30/60 phút; thời lượng tối thiểu phải là bội số của bước giờ.";
            try {
                LocalTime open = LocalTime.parse(r.getOpenTime()),
                        close = LocalTime.parse(r.getCloseTime());
                if (!open.isBefore(close)
                        || open.getMinute() % step != 0
                        || close.getMinute() % step != 0
                        || Duration.between(open, close).toMinutes() < min)
                    return "Giờ mở/đóng phải khớp bước giờ và đủ thời lượng thuê tối thiểu.";
            } catch (Exception e) {
                return "Giờ hoạt động không hợp lệ.";
            }
        }
        if (r.getFixedStartMonth() != null && !r.getFixedStartMonth().isEmpty()) {
            try {
                YearMonth.parse(r.getFixedStartMonth());
            } catch (Exception e) {
                return "Tháng bắt đầu phải theo YYYY-MM.";
            }
        }
        if (r.getBlackoutRanges() != null) {
            if (r.getBlackoutRanges().size() > 8) return "Tối đa 8 khoảng khóa lịch.";
            for (RoomBlackout b : r.getBlackoutRanges())
                if (b == null
                        || b.getStartAt() == null
                        || b.getEndAt() == null
                        || !b.getStartAt().before(b.getEndAt())
                        || HostValidation.empty(b.getReason()))
                    return "Khóa lịch cần giờ bắt đầu trước kết thúc và lý do.";
        }
        return null;
    }

    /** Read legacy dd/MM/yyyy dates and new ISO dates without changing their meaning. */
    public static LocalDate blackoutDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return LocalDate.parse(
                    value,
                    DateTimeFormatter.ofPattern("dd/MM/uuuu")
                            .withResolverStyle(ResolverStyle.STRICT));
        }
    }

    public static String daySummary(Room r, List<Booking> bookings, LocalDate day) {
        if (validate(r) != null)
            return "Lịch hoạt động chưa hợp lệ, cần cập nhật trước khi nhận đặt.";
        if (Room.MODE_FIXED.equals(r.getActiveMode())) {
            Date start = Date.from(day.atStartOfDay(ZONE).toInstant());
            Date end = Date.from(day.plusDays(1).atStartOfDay(ZONE).toInstant());
            for (Booking b : bookings)
                if (Objects.equals(r.getId(), b.roomId)
                        && occupies(b)
                        && overlaps(start, end, b.startAt, b.endAt))
                    return day + " • " + FormatUtils.bookingStatus(b.status);
            for (RoomBlackout b : r.getBlackoutRanges())
                if (overlaps(start, end, b.getStartAt(), b.getEndAt()))
                    return day + " • Bị khóa: " + b.getReason();
            for (String value : r.getBlackoutDates())
                if (blackoutDate(value).equals(day)) return day + " • Ngày khóa lịch";
            return day + " • Chưa có booking giữ chỗ; thuê cố định cần kiểm tra trọn kỳ.";
        }
        if (r.getWorkingDays() == null
                || !r.getWorkingDays().contains(day.getDayOfWeek().getValue()))
            return day + " • Ngày không hoạt động";
        try {
            LocalTime from = LocalTime.parse(r.getOpenTime()),
                    endTime = LocalTime.parse(r.getCloseTime());
            StringBuilder result = new StringBuilder(day + " • Giờ Việt Nam\n");
            String last = null;
            LocalTime runStart = from, cursor = from;
            while (cursor.isBefore(endTime)) {
                LocalTime next = cursor.plusMinutes(30);
                if (next.isAfter(endTime)) next = endTime;
                Date start = Date.from(day.atTime(cursor).atZone(ZONE).toInstant());
                Date end = Date.from(day.atTime(next).atZone(ZONE).toInstant());
                String state = "Còn trống";
                for (String value : r.getBlackoutDates())
                    if (blackoutDate(value).equals(day)) state = "Ngày khóa lịch";
                for (RoomBlackout b : r.getBlackoutRanges())
                    if (overlaps(start, end, b.getStartAt(), b.getEndAt()))
                        state = "Khóa: " + b.getReason();
                for (Booking b : bookings)
                    if (Objects.equals(r.getId(), b.roomId)
                            && occupies(b)
                            && overlaps(start, end, b.startAt, b.endAt))
                        state = FormatUtils.bookingStatus(b.status);
                if (last != null && !last.equals(state)) {
                    result.append(runStart)
                            .append("–")
                            .append(cursor)
                            .append(": ")
                            .append(last)
                            .append('\n');
                    runStart = cursor;
                }
                last = state;
                cursor = next;
            }
            if (last != null)
                result.append(runStart).append("–").append(cursor).append(": ").append(last);
            return result.toString();
        } catch (Exception e) {
            return "Chưa có lịch hoạt động hợp lệ.";
        }
    }
}
