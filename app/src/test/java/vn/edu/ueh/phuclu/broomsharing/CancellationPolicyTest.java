package vn.edu.ueh.phuclu.broomsharing;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Date;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy;

public class CancellationPolicyTest {
    private Booking booking(String mode) { Booking b=new Booking();b.activeMode=mode;b.startAt=new Date(2000000000L);return b; }
    @Test public void hourlyBoundariesAreInclusiveAndNoRefundAfterPartialWindow() {
        Booking b=booking("HOURLY");long start=b.startAt.getTime();
        assertEquals(100,CancellationPolicy.percent(b,start-86400000));
        assertEquals(50,CancellationPolicy.percent(b,start-86400000+1));
        assertEquals(50,CancellationPolicy.percent(b,start-21600000));
        assertEquals(0,CancellationPolicy.percent(b,start-21600000+1));
    }
    @Test public void fixedUsesSevenDaysAndFortyEightHoursForMonthAndQuarter() {
        Booking b=booking("FIXED");long start=b.startAt.getTime();
        for(int months:new int[]{1,3}){
            b.rentalMonths=months;
            assertEquals(100,CancellationPolicy.percent(b,start-604800000));
            assertEquals(50,CancellationPolicy.percent(b,start-604800000+1));
            assertEquals(50,CancellationPolicy.percent(b,start-172800000));
            assertEquals(0,CancellationPolicy.percent(b,start-172800000+1));
        }
    }
    @Test(expected=IllegalStateException.class) public void alreadyStartedIsNotOrdinaryCancellation(){Booking b=booking("HOURLY");CancellationPolicy.percent(b,b.startAt.getTime());}
    @Test public void oldBookingDoesNotAcquireNewPolicy(){assertFalse(CancellationPolicy.versioned(booking("HOURLY")));}
}
