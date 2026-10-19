package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.*;

/** Firestore availabilityMonths/{roomId_yyyyMM}; no guest contact information. */
public class AvailabilityMonth {
    public String roomId = "";
    public int monthIndex;
    public String fixedBookingId = "";
    public List<Integer> slotsBusy = new ArrayList<>();
    public Map<String, List<Integer>> hourlyBookings = new HashMap<>();
    public String lastBookingId = "";
    @ServerTimestamp public Date updatedAt;

    public AvailabilityMonth() {}
}
