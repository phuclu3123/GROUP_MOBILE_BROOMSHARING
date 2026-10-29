package vn.edu.ueh.phuclu.broomsharing;

import java.time.*;
import java.util.*;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.AvailabilityMonth;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.utils.BookingPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule;
import static org.junit.Assert.*;

public class BookingPolicyTest {
    private final long now = at("2026-10-19T08:00:00").getTime();

    private static Date at(String local) {
        return Date.from(LocalDateTime.parse(local).atZone(HostSchedule.ZONE).toInstant());
    }

    private Room room() {
        Room r = new Room();
        r.setId("room");
        r.setBuildingId("building");
        r.setOwnerUid("host");
        r.setRoomName("Phòng thử");
        r.setAddress("TP.HCM");
        r.setFloor("1");
        r.setCapacity(5);
        r.setArea(20);
        r.setImageUrls(Collections.singletonList("https://example.com/photo.jpg"));
        r.setOpenTime("08:00");
        r.setCloseTime("22:00");
        r.setPricePerHour(100001);
        r.setPricePerMonth(3000000);
        r.setPricePerQuarter(8000000);
        r.setDeposit(200000);
        r.setFixedStartMonth("2026-11");
        r.setRenewalPolicy("Thỏa thuận lại");
        return r;
    }

    private Booking request(String start, String end, int months) {
        Booking b = new Booking();
        b.roomId = "room";
        b.startAt = at(start);
        b.endAt = end == null ? null : at(end);
        b.rentalMonths = months;
        return b;
    }

    private Booking hourly() {
        Booking b =
                BookingPolicy.plan(
                        room(), request("2026-11-02T09:00:00", "2026-11-02T10:00:00", 0), now);
        b.id = "booking";
        return b;
    }

    @Test
    public void quoteUsesCurrentRoomPriceAndFortyPercentAdvance() {
        Booking b = hourly();
        assertEquals(2, b.schemaVersion);
        assertEquals(100001, b.totalAmount, 0);
        assertEquals(40000, ((Number) b.priceSnapshot.get("paymentDueAmount")).doubleValue(), 0);
        assertEquals("UNAVAILABLE", b.paymentStatus);
        assertTrue(CancellationPolicy.versioned(b));
        assertEquals(Arrays.asList("room_202611"), b.monthKeys);
        assertEquals(Arrays.asList(66, 67), b.slotKeys);
    }

    @Test
    public void halfHourPriceRoundsPositiveVnd() {
        Room r = room();
        r.setSlotMinutes(30);
        r.setMinBookingMinutes(30);
        Booking b =
                BookingPolicy.plan(
                        r, request("2026-11-02T09:00:00", "2026-11-02T09:30:00", 0), now);
        assertEquals(50001, b.totalAmount, 0);
    }

    @Test
    public void quarterCrossesYearAndUsesCalendarMonths() {
        Room r = room();
        r.setActiveMode("FIXED");
        Booking b = BookingPolicy.plan(r, request("2026-11-01T00:00:00", null, 3), now);
        assertEquals(at("2027-02-01T00:00:00"), b.endAt);
        assertEquals(Arrays.asList("room_202611", "room_202612", "room_202701"), b.monthKeys);
        assertEquals(8000000, b.totalAmount, 0);
    }

    @Test(expected = IllegalStateException.class)
    public void fixedDoesNotStartMidMonth() {
        Room r = room();
        r.setActiveMode("FIXED");
        BookingPolicy.plan(r, request("2026-11-02T00:00:00", null, 1), now);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsPastRequest() {
        BookingPolicy.plan(room(), request("2026-10-18T09:00:00", "2026-10-18T10:00:00", 0), now);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsBlackoutOverlap() {
        Room r = room();
        r.setBlackoutDates(Collections.singletonList("02/11/2026"));
        BookingPolicy.plan(r, request("2026-11-02T09:00:00", "2026-11-02T10:00:00", 0), now);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsClosedWorkingDay() {
        Room r = room();
        r.setWorkingDays(Collections.singletonList(7));
        BookingPolicy.plan(r, request("2026-11-02T09:00:00", "2026-11-02T10:00:00", 0), now);
    }

    @Test(expected = IllegalStateException.class)
    public void secondReservationCannotOverwriteFirst() {
        AvailabilityMonth month = new AvailabilityMonth();
        BookingPolicy.reserve(month, hourly());
        Booking other = hourly();
        other.id = "other";
        BookingPolicy.reserve(month, other);
    }

    @Test(expected = IllegalStateException.class)
    public void monthlyReservationConflictsWithHourly() {
        AvailabilityMonth month = new AvailabilityMonth();
        BookingPolicy.reserve(month, hourly());
        Room r = room();
        r.setActiveMode("FIXED");
        Booking fixed = BookingPolicy.plan(r, request("2026-11-01T00:00:00", null, 1), now);
        fixed.id = "fixed";
        BookingPolicy.reserve(month, fixed);
    }

    @Test
    public void releasingOneBookingKeepsOtherSlots() {
        AvailabilityMonth month = new AvailabilityMonth();
        Booking first = hourly();
        BookingPolicy.reserve(month, first);
        Booking other =
                BookingPolicy.plan(
                        room(), request("2026-11-02T10:00:00", "2026-11-02T11:00:00", 0), now);
        other.id = "other";
        BookingPolicy.reserve(month, other);
        BookingPolicy.release(month, first);
        assertEquals(2, month.slotsBusy.size());
        assertEquals(Collections.singleton("other"), month.hourlyBookings.keySet());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectionRequiresReason() {
        BookingPolicy.transition(hourly(), "REJECTED", "", now);
    }

    @Test(expected = IllegalStateException.class)
    public void checkInCannotHappenBeforeRental() {
        Booking b = hourly();
        b.status = "CONFIRMED";
        BookingPolicy.transition(b, "CHECKED_IN", "", now);
    }

    @Test
    public void fingerprintChangesWithActualSelection() {
        Booking first = request("2026-11-02T09:00:00", "2026-11-02T10:00:00", 0),
                second = request("2026-11-02T10:00:00", "2026-11-02T11:00:00", 0);
        assertEquals(BookingPolicy.fingerprint(first), BookingPolicy.fingerprint(first));
        assertNotEquals(BookingPolicy.fingerprint(first), BookingPolicy.fingerprint(second));
    }
}
