package vn.edu.ueh.phuclu.broomsharing;

import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestSectionActivity;

public class MyBookingsActivity extends GuestSectionActivity {
    @Override
    protected int tab() {
        return R.id.nav_bookings;
    }

    @Override
    protected int title() {
        return R.string.nav_bookings_label;
    }

    @Override
    protected int heading() {
        return R.string.bookings_pending_title;
    }

    @Override
    protected int description() {
        return R.string.bookings_pending_body;
    }

    @Override
    protected int icon() {
        return R.drawable.ic_booking;
    }
}
