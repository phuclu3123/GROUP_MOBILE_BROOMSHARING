package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

public class InstantBookingTest {
    private Date at(String day) {
        return Date.from(LocalDateTime.parse(day).atZone(HostSchedule.ZONE).toInstant());
    }

    private Booking hold(String from, String to) {
        Booking b = new Booking();
        b.id = "hold"; b.roomId = "room"; b.paymentFlow = "INSTANT_5M";
        b.status = "PENDING"; b.startAt = at(from); b.endAt = at(to);
        b.createdAt = new Date(b.startAt.getTime() - 3600000);
        return b;
    }

    @Test public void temporaryHoldHasFiveMinuteDeadlineCappedByStart() {
        Booking b = hold("2026-11-01T09:00:00", "2026-11-01T10:00:00");
        assertEquals(b.createdAt.getTime() + 300000, b.paymentDeadline());
        b.createdAt = new Date(b.startAt.getTime() - 60000);
        assertEquals(b.startAt.getTime(), b.paymentDeadline());
    }

    @Test public void temporaryAndExpiredLabelsDoNotClaimSuccess() {
        Booking b = hold("2026-11-01T09:00:00", "2026-11-01T10:00:00");
        assertTrue(FormatUtils.bookingStatus(b).contains("Chờ thanh toán"));
        b.status = "EXPIRED";
        assertTrue(FormatUtils.bookingStatus(b).contains("không thành công"));
        b.paymentStatus = "REVIEW_REQUIRED";
        assertTrue(FormatUtils.bookingStatus(b).contains("Cần đối soát"));
    }

    @Test public void hostCannotConfirmInstantBooking() {
        Booking b = hold("2026-11-01T09:00:00", "2026-11-01T10:00:00");
        assertThrows(IllegalStateException.class, () -> BookingPolicy.transition(b, "CONFIRMED", "", b.createdAt.getTime()));
        b.paymentFlow = "";
        BookingPolicy.transition(b, "CONFIRMED", "", b.createdAt.getTime());
    }

    @Test public void expiryNeverBlocksAdjacentExclusiveSlot() {
        Room room = new Room();
        Booking b = hold("2026-11-01T09:00:00", "2026-11-01T10:00:00");
        assertNull(BookingAvailability.conflict(room, List.of(b), at("2026-11-01T10:00:00"), at("2026-11-01T11:00:00")));
        assertTrue(BookingAvailability.conflict(room, List.of(b), at("2026-11-01T09:30:00"), at("2026-11-01T10:30:00")).contains("Tạm giữ"));
        b.status = "EXPIRED";
        assertNull(BookingAvailability.conflict(room, List.of(b), b.startAt, b.endAt));
    }

    @Test public void nextQuarterSkipsAdjacentBookingsAndBlackouts() {
        Room room = new Room();
        Booking one = hold("2026-11-01T00:00:00", "2027-02-01T00:00:00");
        Booking two = hold("2027-02-01T00:00:00", "2027-03-01T00:00:00");
        room.setBlackoutDates(List.of("2027-04-15"));
        String summary = BookingAvailability.fixedSummary(room, List.of(one, two), LocalDate.parse("2026-11-01"), 3, LocalDate.parse("2027-11-01"));
        assertTrue(summary, summary.contains("2027-05-01"));
    }

    @Test public void pendingMarkersAreRemovedWhenOnlyOwnHoldIsReleased() {
        AvailabilityMonth month = new AvailabilityMonth();
        Booking b = hold("2026-11-01T09:00:00", "2026-11-01T10:00:00");
        b.slotKeys = List.of(18,19);
        month.slotsBusy.add(20); month.hourlyBookings.put("other", List.of(20));
        BookingPolicy.reserve(month, b);
        assertEquals(List.of(b.id), month.pendingPaymentIds);
        BookingPolicy.release(month, b);
        assertTrue(month.pendingPaymentIds.isEmpty());
        assertEquals(List.of(20), month.slotsBusy);
        assertEquals(List.of(20), month.hourlyBookings.get("other"));
    }
}
