package vn.edu.ueh.phuclu.broomsharing.model;

import java.util.Date;

/** Shared Firestore contract. Instants are stored as Timestamp (UTC). */
public class Booking {
    public String id, roomId, ownerUid, guestUid;
    public String status = "PENDING", paymentStatus = "UNPAID", activeMode = "HOURLY";
    public Date startAt, endAt;
    @com.google.firebase.firestore.ServerTimestamp public Date createdAt;
    @com.google.firebase.firestore.ServerTimestamp public Date updatedAt;
    public double totalAmount;
    public long schemaVersion = 1;

    public Booking() {}
    public String roomName;
    public String roomAddress;
    public String priceUnit;

    public double depositAmount;
    public double priceSnapshot;
    public String getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }
}
