package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;

import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.RoomBlackout;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.time.*;
import java.util.*;

// Bắt lỗi ngày kết thúc bị tính thêm và trạng thái đã nhả lịch xuất hiện lại trên lưới.
public class HostCalendarPolicyTest {
    private Booking booking(String from, String until, String status) {
        Booking b = new Booking();
        b.roomId = "one";
        b.status = status;
        b.startAt = Date.from(LocalDateTime.parse(from).atZone(HostSchedule.ZONE).toInstant());
        b.endAt = Date.from(LocalDateTime.parse(until).atZone(HostSchedule.ZONE).toInstant());
        return b;
    }

    @Test
    public void fixedEndMonthIsExclusive() {
        List<Booking> rows =
                Collections.singletonList(
                        booking("2026-10-01T00:00", "2026-11-01T00:00", "CONFIRMED"));
        assertEquals(
                1, HostCalendarPolicy.heldOnDay(rows, "one", LocalDate.of(2026, 10, 31)).size());
        assertTrue(HostCalendarPolicy.heldOnDay(rows, "one", LocalDate.of(2026, 11, 1)).isEmpty());
    }

    @Test
    public void hourlyMidnightDoesNotOccupyNextDay() {
        List<Booking> rows =
                Collections.singletonList(
                        booking("2026-12-31T23:00", "2027-01-01T00:00", "PENDING"));
        assertEquals(
                1, HostCalendarPolicy.heldOnDay(rows, "one", LocalDate.of(2026, 12, 31)).size());
        assertTrue(HostCalendarPolicy.heldOnDay(rows, "one", LocalDate.of(2027, 1, 1)).isEmpty());
    }

    @Test
    public void releasedStatesAndOtherRoomsAreNotHolds() {
        for (String status : Arrays.asList("EXPIRED", "REJECTED", "CANCELLED", "COMPLETED")) {
            assertTrue(
                    HostCalendarPolicy.heldOnDay(
                                    Collections.singletonList(
                                            booking(
                                                    "2026-10-25T09:00",
                                                    "2026-10-25T10:00",
                                                    status)),
                                    "one",
                                    LocalDate.of(2026, 10, 25))
                            .isEmpty());
        }
        assertTrue(
                HostCalendarPolicy.heldOnDay(
                                Collections.singletonList(
                                        booking(
                                                "2026-10-25T09:00",
                                                "2026-10-25T10:00",
                                                "CONFIRMED")),
                                "other",
                                LocalDate.of(2026, 10, 25))
                        .isEmpty());
    }

    @Test
    public void checkedInAndPendingRemainHolds() {
        List<Booking> rows =
                Arrays.asList(
                        booking("2026-10-25T09:00", "2026-10-25T10:00", "CHECKED_IN"),
                        booking("2026-10-25T10:00", "2026-10-25T11:00", "PENDING"));
        assertEquals(
                2, HostCalendarPolicy.heldOnDay(rows, "one", LocalDate.of(2026, 10, 25)).size());
    }

    @Test
    public void dayDetailsDoNotUseAnotherRoomsBooking() {
        Room room = new Room();
        room.setId("other");
        room.setOpenTime("08:00");
        room.setCloseTime("12:00");
        LocalDate day = LocalDate.of(2026, 10, 25);
        List<Booking> all =
                Collections.singletonList(
                        booking("2026-10-25T09:00", "2026-10-25T10:00", "CONFIRMED"));
        String summary = HostSchedule.daySummary(room, all, day);
        assertTrue(summary.contains("Còn trống"));
        assertFalse(summary.contains("Đã xác nhận"));
    }

    @Test
    public void closureMarkerHonorsRangeEndAndWeekdays() {
        Room room = new Room();
        room.setOpenTime("08:00");
        room.setCloseTime("22:00");
        room.setWorkingDays(Arrays.asList(1, 2, 3, 4, 5, 6, 7));
        Booking range = booking("2026-10-25T23:00", "2026-10-26T00:00", "PENDING");
        room.setBlackoutRanges(
                Collections.singletonList(new RoomBlackout(range.startAt, range.endAt, "Bảo trì")));
        assertTrue(HostCalendarPolicy.hasClosure(room, LocalDate.of(2026, 10, 25)));
        assertFalse(HostCalendarPolicy.hasClosure(room, LocalDate.of(2026, 10, 26)));
        room.setWorkingDays(Arrays.asList(2, 3, 4, 5, 6, 7));
        assertTrue(HostCalendarPolicy.hasClosure(room, LocalDate.of(2026, 10, 26)));
    }
}
