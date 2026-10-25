package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared Firestore contract for bookings. Instants stored as Timestamp (UTC). */
/*
    Snapshot phòng, người thuê, giá và khóa lịch tại lúc đặt.
    Các chuyển trạng thái phải qua BookingRepository để booking và lịch thay đổi cùng transaction.
    Thông tin tỷ lệ hủy không xác nhận đã thu hoặc hoàn tiền.
 */
public class Booking implements Serializable {
    public static final long CURRENT_SCHEMA_VERSION = 2;
    public static final long LEGACY_SCHEMA_VERSION = 1;
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CHECKED_IN = "CHECKED_IN";
    public static final String STATUS_EXPIRED = "EXPIRED";

    public String id;
    public String roomId;
    public String roomName;
    public String roomAddress;
    public String ownerUid;
    public String guestUid;
    public String guestName;
    public String guestPhone;
    public String status = STATUS_PENDING;
    public String paymentStatus = "UNPAID";
    public String activeMode = "HOURLY";
    public String rejectionReason = "";
    public Date startAt;
    public Date endAt;
    public double totalAmount;
    public Map<String, Object> priceSnapshot = new HashMap<>();
    // Khi đọc document thiếu version, giữ dấu hiệu dữ liệu cũ để không tự nhận là hợp lệ.
    // BookingPolicy luôn gán CURRENT_SCHEMA_VERSION khi tạo booking mới.
    public long schemaVersion = LEGACY_SCHEMA_VERSION;
    public int rentalMonths;
    public int slotStart;
    public int slotCount;
    public List<String> monthKeys = new ArrayList<>();
    public List<Integer> slotKeys = new ArrayList<>();
    public String requestFingerprint = "";
    public Date cancelledAt;
    public String cancelledByUid = "";
    public int cancellationRefundPercent;
    public double refundAmount;
    public String refundStatus = "NOT_REQUIRED";
    @Exclude public String requestKey = "";

    @ServerTimestamp public Date createdAt;
    @ServerTimestamp public Date updatedAt;

    public Booking() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getOwnerUid() {
        return ownerUid;
    }

    public void setOwnerUid(String ownerUid) {
        this.ownerUid = ownerUid;
    }

    public String getGuestUid() {
        return guestUid;
    }

    public void setGuestUid(String guestUid) {
        this.guestUid = guestUid;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getGuestPhone() {
        return guestPhone;
    }

    public void setGuestPhone(String guestPhone) {
        this.guestPhone = guestPhone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getActiveMode() {
        return activeMode;
    }

    public void setActiveMode(String activeMode) {
        this.activeMode = activeMode;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Date getStartAt() {
        return startAt;
    }

    public void setStartAt(Date startAt) {
        this.startAt = startAt;
    }

    public Date getEndAt() {
        return endAt;
    }

    public void setEndAt(Date endAt) {
        this.endAt = endAt;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
