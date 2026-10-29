package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;

import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.util.*;

// Mốc biên dùng số thời gian cố định, không phụ thuộc đồng hồ máy chạy test.
public class BusinessPolicyTest {
    private Booking pending() {
        Booking b = new Booking();
        b.id = "test";
        b.guestUid = "guest";
        b.startAt = new Date(100000);
        b.endAt = new Date(200000);
        b.usageConfirmedAt = new Date(100000);
        return b;
    }

    @Test
    public void expiresExactlyAtStart() {
        BookingPolicy.transition(pending(), "EXPIRED", "", 100000);
    }

    @Test(expected = IllegalStateException.class)
    public void cannotExpireEarly() {
        BookingPolicy.transition(pending(), "EXPIRED", "", 99999);
    }

    @Test(expected = IllegalStateException.class)
    public void confirmedDoesNotExpire() {
        Booking b = pending();
        b.status = "CONFIRMED";
        BookingPolicy.transition(b, "EXPIRED", "", 100001);
    }

    @Test
    public void reviewOfCompletedRental() {
        Booking b = pending();
        b.status = "COMPLETED";
        ReviewPolicy.validate(b, "guest", 5, "Phòng sạch và đúng mô tả.", 200000);
    }

    @Test(expected = IllegalStateException.class)
    public void noReviewBeforeCompletion() {
        ReviewPolicy.validate(pending(), "guest", 5, "Phòng rất tốt.", 300000);
    }

    @Test(expected = IllegalStateException.class)
    public void noPeerReview() {
        Booking b = pending();
        b.status = "COMPLETED";
        ReviewPolicy.validate(b, "other", 5, "Phòng rất tốt.", 300000);
    }

    @Test(expected = IllegalStateException.class)
    public void noFutureCompletedReview() {
        Booking b = pending();
        b.status = "COMPLETED";
        ReviewPolicy.validate(b, "guest", 5, "Phòng rất tốt.", 199999);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsZeroStars() {
        Booking b = pending();
        b.status = "COMPLETED";
        ReviewPolicy.validate(b, "guest", 0, "Phòng rất tốt.", 300000);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsSixStars() {
        Booking b = pending();
        b.status = "COMPLETED";
        ReviewPolicy.validate(b, "guest", 6, "Phòng rất tốt.", 300000);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsBlankReview() {
        Booking b = pending();
        b.status = "COMPLETED";
        ReviewPolicy.validate(b, "guest", 5, "          ", 300000);
    }

    @Test
    public void calendarOnlyForConfirmedUnfinishedBooking() {
        Booking b = pending();
        assertFalse(BookingCalendar.canSave(b, 100000));
        b.status = "CONFIRMED";
        assertTrue(BookingCalendar.canSave(b, 100000));
        assertFalse(BookingCalendar.canSave(b, 200000));
        b.status = "CANCELLED";
        assertFalse(BookingCalendar.canSave(b, 100000));
    }

    @Test(expected = IllegalStateException.class)
    public void occupiedRoomCannotChangeSchedule() {
        Room a = new Room(), b = new Room();
        a.activeBookingCount = 1;
        b.setOpenTime("09:00");
        BookingPolicy.validateRoomChange(a, b, Collections.emptyList(), 0);
    }

    @Test(expected = IllegalStateException.class)
    public void unknownLegacyCountDoesNotMeanEmpty() {
        Room a = new Room(), b = new Room();
        b.setActiveMode("FIXED");
        BookingPolicy.validateRoomChange(a, b, Collections.emptyList(), 0);
    }

    @Test
    public void emptyReconciledRoomCanChangeSchedule() {
        Room a = new Room(), b = new Room();
        a.activeBookingCount = 0;
        b.setOpenTime("09:00");
        BookingPolicy.validateRoomChange(a, b, Collections.emptyList(), 0);
    }
}
