package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

public class FixedRentalBadgeTest {
    private Date at(String value) { return Date.from(LocalDateTime.parse(value).atZone(HostSchedule.ZONE).toInstant()); }
    private Room room() {
        Room r = new Room(); r.setActiveMode("FIXED"); r.setPricePerMonth(3000000);
        r.setFixedStartMonth("2026-10"); return r;
    }
    private Booking lease(String from, String to, boolean pending) {
        Booking b = new Booking(); b.startAt = at(from); b.endAt = at(to);
        b.status = pending ? "PENDING" : "CONFIRMED"; b.paymentFlow = pending ? "INSTANT_5M" : "";
        return b;
    }
    private FixedRentalBadge badge(Room r, List<Booking> windows, String now) {
        return FixedRentalBadge.from(r, windows, LocalDate.parse("2027-11-01"), at(now).getTime());
    }
    @Test public void emptyConfirmedCalendarShowsActualNextStartMonth() {
        FixedRentalBadge b = badge(room(), List.of(), "2026-10-27T09:00:00");
        assertEquals(1, b.tone); assertTrue(b.detail.contains("01/11/2026"));
    }
    @Test public void futureLeaseIsNotDescribedAsCurrentOccupancy() {
        FixedRentalBadge b = badge(room(), List.of(lease("2026-11-01T00:00:00","2027-02-01T00:00:00",false)), "2026-10-27T09:00:00");
        assertEquals("Đã đặt trước", b.label); assertTrue(b.detail.contains("01/02/2027"));
    }
    @Test public void temporaryHoldIsNotPresentedAsPaid() {
        FixedRentalBadge b = badge(room(), List.of(lease("2026-11-01T00:00:00","2026-12-01T00:00:00",true)), "2026-10-27T09:00:00");
        assertEquals(2, b.tone); assertTrue(b.label.contains("Chờ thanh toán"));
    }
    @Test public void endingThresholdIsExactlyThreeDays() {
        List<Booking> windows = List.of(lease("2026-10-01T00:00:00","2026-11-01T00:00:00",false));
        assertEquals(3, badge(room(), windows, "2026-10-28T23:59:59").tone);
        assertEquals(4, badge(room(), windows, "2026-10-29T00:00:00").tone);
    }
    @Test public void adjacentLeasePreventsClaimingAvailabilityAtFirstEnd() {
        FixedRentalBadge b = badge(room(), List.of(lease("2026-10-01T00:00:00","2026-11-01T00:00:00",false),
                lease("2026-11-01T00:00:00","2026-12-01T00:00:00",false)), "2026-10-27T09:00:00");
        assertTrue(b.detail.contains("Nhận thuê 1 tháng từ 01/12/2026"));
    }
    @Test public void blackoutMovesNextAvailableMonth() {
        Room r = room(); r.setBlackoutDates(List.of("2026-11-15"));
        assertTrue(badge(r, List.of(), "2026-10-27T09:00:00").detail.contains("01/12/2026"));
    }
    @Test public void missingOrFailedCalendarNeverClaimsFree() {
        assertEquals(0, FixedRentalBadge.unknown(false).tone);
        Room r = room(); r.setFixedStartMonth("");
        assertEquals(0, badge(r, List.of(), "2026-10-27T09:00:00").tone);
    }
}
