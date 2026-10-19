package vn.edu.ueh.phuclu.broomsharing;

import org.junit.Test;
import static org.junit.Assert.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;
import java.time.*;
import java.util.*;

public class HostScheduleTest {
    private Room room() {
        Room r = new Room(); r.setOpenTime("08:00"); r.setCloseTime("11:00");
        r.setSlotMinutes(30); r.setMinBookingMinutes(60); return r;
    }
    private Date at(int hour) { return Date.from(LocalDate.of(2026,10,20).atTime(hour,0).atZone(HostSchedule.ZONE).toInstant()); }
    @Test public void rejectsEmptyWeekdaysMisalignedStepAndTooLongMinimum() {
        Room r = room(); assertNull(HostSchedule.validate(r));
        r.setWorkingDays(Collections.emptyList()); assertNotNull(HostSchedule.validate(r));
        r = room(); r.setSlotMinutes(45); assertNotNull(HostSchedule.validate(r));
        r = room(); r.setMinBookingMinutes(200); assertNotNull(HostSchedule.validate(r));
        r = room(); r.setOpenTime("08:15"); assertNotNull(HostSchedule.validate(r));
    }
    @Test public void blackoutRequiresReasonAndExclusivePositiveRange() {
        Room r = room(); r.setBlackoutRanges(Arrays.asList(new RoomBlackout(at(9),at(9),"Maintenance")));
        assertNotNull(HostSchedule.validate(r));
        r.setBlackoutRanges(Arrays.asList(new RoomBlackout(at(9),at(10),""))); assertNotNull(HostSchedule.validate(r));
        r.setBlackoutRanges(Arrays.asList(new RoomBlackout(at(9),at(10),"Maintenance"))); assertNull(HostSchedule.validate(r));
        assertFalse(HostSchedule.overlaps(at(8),at(9),at(9),at(10)));
    }
    @Test public void calendarSeparatesConfirmedBlockedFreeAndPending() {
        Room r = room(); r.setCloseTime("12:00");
        r.setBlackoutRanges(Arrays.asList(new RoomBlackout(at(9),at(10),"Maintenance")));
        Booking confirmed = new Booking(); confirmed.startAt = at(8); confirmed.endAt = at(9); confirmed.status = "CONFIRMED";
        Booking pending = new Booking(); pending.startAt = at(10); pending.endAt = at(11);
        String view = HostSchedule.daySummary(r,Arrays.asList(confirmed,pending),LocalDate.of(2026,10,20));
        assertTrue(view.contains("08:00–09:00: Đã xác nhận"));
        assertTrue(view.contains("09:00–10:00: Khóa: Maintenance"));
        assertTrue(view.contains("10:00–11:00: Chờ duyệt"));
        assertTrue(view.contains("11:00–12:00: Còn trống"));
        pending.status = "CANCELLED";
        view = HostSchedule.daySummary(r,Arrays.asList(confirmed,pending),LocalDate.of(2026,10,20));
        assertTrue(view.contains("10:00–12:00: Còn trống"));
    }
    @Test public void legacyBlackoutDatesKeepMeaning() {
        assertEquals(LocalDate.of(2026,10,20),HostSchedule.blackoutDate("20/10/2026"));
        assertEquals(LocalDate.of(2026,10,20),HostSchedule.blackoutDate("2026-10-20"));
    }
    @Test public void moneyIsIntegerVnd() { assertFalse(HostValidation.money(0.5)); assertTrue(HostValidation.money(1)); }
}
