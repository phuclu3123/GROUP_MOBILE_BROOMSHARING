package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.ServerTimestamp;
import java.io.Serializable;
import java.util.Date;

public class HostApplication implements Serializable {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private String id;
    private String applicantUid;
    private String fullName;
    private String phone;
    private String email;
    private String organizationType;
    private String identityDoc;
    private String status;
    private String rejectionReason;

    @ServerTimestamp
    private Date createdAt;

    public HostApplication() {
        this.status = STATUS_PENDING;
    }

    public HostApplication(String applicantUid, String fullName, String phone, String email, String organizationType, String identityDoc) {
        this.applicantUid = applicantUid;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.organizationType = organizationType;
        this.identityDoc = identityDoc;
        this.status = STATUS_PENDING;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getApplicantUid() { return applicantUid; }
    public void setApplicantUid(String applicantUid) { this.applicantUid = applicantUid; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOrganizationType() { return organizationType; }
    public void setOrganizationType(String organizationType) { this.organizationType = organizationType; }

    public String getIdentityDoc() { return identityDoc; }
    public void setIdentityDoc(String identityDoc) { this.identityDoc = identityDoc; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
