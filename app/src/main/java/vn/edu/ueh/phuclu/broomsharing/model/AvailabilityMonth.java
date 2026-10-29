package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.*;

/** Firestore availabilityMonths/{roomId_yyyyMM}; no guest contact information. */
/*
    Lịch kỹ thuật cho một phòng trong một tháng YYYYMM.
    fixedBookingId giữ cả tháng; hourlyBookings ánh xạ booking sang các slot 30 phút.
    Các mảng và map này là dữ liệu nhúng, không phải subcollection.
 */
public class AvailabilityMonth {
    public String roomId = "";
    public int monthIndex;
    public String fixedBookingId = "";
    public List<Integer> slotsBusy = new ArrayList<>();
    public Map<String, List<Integer>> hourlyBookings = new HashMap<>();
    // Chỉ chứa mã giữ tạm, không công khai thông tin khách hoặc thanh toán.
    public List<String> pendingPaymentIds = new ArrayList<>();
    public String lastBookingId = "";
    @ServerTimestamp public Date updatedAt;

    public AvailabilityMonth() {}
}
