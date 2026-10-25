package vn.edu.ueh.phuclu.broomsharing.utils;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.RoomBlackout;

import java.time.LocalDate;
import java.util.*;

// Lưới tháng dùng cùng khoảng [startAt,endAt) và tập trạng thái giữ chỗ với lịch ngày.
public final class HostCalendarPolicy {
    private HostCalendarPolicy() {}

    // Chỉ đánh dấu ngày có phần bị khóa; chi tiết bên dưới vẫn tách rõ các khoảng giờ.
    public static boolean hasClosure(Room room, LocalDate day) {
        if (HostSchedule.validate(room) != null) return true;
        if (Room.MODE_HOURLY.equals(room.getActiveMode())
                && !room.getWorkingDays().contains(day.getDayOfWeek().getValue())) return true;
        for (String date : room.getBlackoutDates())
            if (HostSchedule.blackoutDate(date).equals(day)) return true;
        Date start = Date.from(day.atStartOfDay(HostSchedule.ZONE).toInstant());
        Date end = Date.from(day.plusDays(1).atStartOfDay(HostSchedule.ZONE).toInstant());
        for (RoomBlackout range : room.getBlackoutRanges())
            if (HostSchedule.overlaps(start, end, range.getStartAt(), range.getEndAt()))
                return true;
        return false;
    }

    public static List<Booking> heldOnDay(List<Booking> rows, String roomId, LocalDate day) {
        Date start = Date.from(day.atStartOfDay(HostSchedule.ZONE).toInstant());
        Date end = Date.from(day.plusDays(1).atStartOfDay(HostSchedule.ZONE).toInstant());
        List<Booking> result = new ArrayList<>();
        for (Booking row : rows) {
            if (roomId != null && !roomId.equals(row.roomId)) continue;
            if (row.startAt != null
                    && row.endAt != null
                    && HostSchedule.occupies(row)
                    && HostSchedule.overlaps(start, end, row.startAt, row.endAt)) result.add(row);
        }
        result.sort(Comparator.comparing(b -> b.startAt));
        return result;
    }
}
