package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;

import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.util.*;

// Kiểm mốc thời gian, quyền xử lý và quyền đánh giá độc lập với Android/Firebase.
public class BookingResolutionPolicyTest {
    private Booking booking(String status) {
        Booking b = new Booking();
        b.status = status;
        b.guestUid = "guest";
        b.startAt = new Date(100);
        b.endAt = new Date(200);
        return b;
    }

    private void resolve(
            String status, String target, String reason, boolean admin, boolean blocked, long now) {
        BookingResolutionPolicy.validate(booking(status), target, reason, admin, blocked, now);
    }

    @Test
    public void noShowAtEnd() {
        resolve("CONFIRMED", "NO_SHOW", "Đã liên hệ khách", false, false, 200);
    }

    @Test(expected = IllegalStateException.class)
    public void noShowNotAtStart() {
        resolve("CONFIRMED", "NO_SHOW", "Đã liên hệ khách", false, false, 100);
    }

    @Test(expected = IllegalStateException.class)
    public void noShowNeedsReason() {
        resolve("CONFIRMED", "NO_SHOW", " ", false, false, 200);
    }

    @Test(expected = IllegalStateException.class)
    public void checkedInCannotBeNoShow() {
        resolve("CHECKED_IN", "NO_SHOW", "Lý do", false, false, 200);
    }

    @Test
    public void blockedHostAdminNoShow() {
        resolve("CONFIRMED", "NO_SHOW", "Đã xác minh", true, true, 200);
    }

    @Test(expected = IllegalStateException.class)
    public void adminDoesNotTakeOverActiveHostNoShow() {
        resolve("CONFIRMED", "NO_SHOW", "Lý do", true, false, 200);
    }

    @Test
    public void normalCompletion() {
        resolve("CHECKED_IN", "COMPLETED", "", false, false, 200);
    }

    @Test(expected = IllegalStateException.class)
    public void completionCannotBeEarly() {
        resolve("CHECKED_IN", "COMPLETED", "", false, false, 199);
    }

    @Test(expected = IllegalStateException.class)
    public void unrecordedUseCannotCompleteSilently() {
        resolve("CONFIRMED", "COMPLETED", "", false, false, 200);
    }

    @Test
    public void lateUseConfirmationNeedsExplanation() {
        resolve(
                "CONFIRMED",
                "COMPLETED",
                "Quên ghi nhận, đã xác minh khách dùng phòng",
                false,
                false,
                200);
    }

    @Test
    public void adminCompletesBlockedHost() {
        resolve("CHECKED_IN", "COMPLETED", "Đã hết kỳ thuê", true, true, 200);
    }

    @Test(expected = IllegalStateException.class)
    public void adminNeedsCompletionReason() {
        resolve("CHECKED_IN", "COMPLETED", "", true, true, 200);
    }

    @Test
    public void adminTerminatesEarly() {
        resolve("CHECKED_IN", "TERMINATED", "Sự cố, khách đã ngừng sử dụng", true, true, 150);
    }

    @Test(expected = IllegalStateException.class)
    public void activeHostCannotTerminate() {
        resolve("CHECKED_IN", "TERMINATED", "Sự cố", false, false, 150);
    }

    @Test(expected = IllegalStateException.class)
    public void adminCannotTerminateActiveHost() {
        resolve("CHECKED_IN", "TERMINATED", "Sự cố", true, false, 150);
    }

    @Test(expected = IllegalStateException.class)
    public void cannotTerminateUnstartedBooking() {
        resolve("CONFIRMED", "TERMINATED", "Sự cố", true, true, 150);
    }

    @Test
    public void adminCorrectsNoShow() {
        resolve("NO_SHOW", "COMPLETED", "Xác minh phản ánh, khách có sử dụng", true, false, 210);
    }

    @Test
    public void adminVerifiesForgottenCheckInAfterGuestReport() {
        resolve("CONFIRMED", "COMPLETED", "Xác minh phản ánh của Guest", true, false, 210);
    }

    @Test(expected = IllegalStateException.class)
    public void hostCannotRewriteNoShow() {
        resolve("NO_SHOW", "COMPLETED", "Đổi ý", false, false, 210);
    }

    @Test
    public void adminVerifiesLegacyCompletion() {
        resolve("COMPLETED", "COMPLETED", "Đối chiếu sử dụng thực tế", true, false, 210);
    }

    @Test
    public void terminalStatesDoNotHoldRoom() {
        for (String s :
                Arrays.asList(
                        "NO_SHOW", "TERMINATED", "COMPLETED", "CANCELLED", "REJECTED", "EXPIRED")) {
            assertTrue(BookingResolutionPolicy.terminal(s));
            assertFalse(BookingResolutionPolicy.holds(s));
        }
    }

    @Test
    public void reviewTerminatedRentalBeforeOriginalEnd() {
        Booking b = booking("TERMINATED");
        b.usageConfirmedAt = new Date(110);
        b.closedAt = new Date(150);
        ReviewPolicy.validate(b, "guest", 3, "Có sử dụng nhưng phải ngừng vì sự cố.", 150);
    }

    @Test
    public void noShowAndUnverifiedLegacyCannotReview() {
        assertFalse(ReviewPolicy.canReview(booking("COMPLETED"), 300));
        Booking b = booking("NO_SHOW");
        b.usageConfirmedAt = new Date(110);
        assertFalse(ReviewPolicy.canReview(b, 300));
    }

    @Test
    public void noReviewForUnclosedIncident() {
        Booking b = booking("TERMINATED");
        b.usageConfirmedAt = new Date(110);
        assertFalse(ReviewPolicy.canReview(b, 300));
    }
}
