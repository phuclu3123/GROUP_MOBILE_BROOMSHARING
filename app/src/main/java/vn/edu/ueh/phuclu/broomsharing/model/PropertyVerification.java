package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// Hồ sơ xét duyệt của Host; tệp riêng tư dùng evidenceRefs, cùng contract Admin.
public class PropertyVerification implements Serializable {
    public int schemaVersion = 1;
    public String id;
    public String ownerUid;
    public String roomId;
    public String buildingId;
    public String roomName;
    public String buildingName;
    public String verificationType; // LOCATION, PHOTOS, IDENTITY
    public String description;
    public List<String> evidenceRefs = new ArrayList<>();
    public String status = "PENDING"; // PENDING, VERIFIED, REJECTED
    public String rejectionReason = "";
    public String reviewedBy = "";
    @ServerTimestamp public Date createdAt;
    @ServerTimestamp public Date updatedAt;

    public PropertyVerification() {}
}
