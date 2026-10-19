package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.*;

/** The auditLogs Firestore document shared by booking, Host and Admin repositories. */
public class AuditLog {
    public long schemaVersion = 1;
    public String actorUid, targetPath, action, reason;
    public Map<String, Object> before, after;
    @ServerTimestamp public Date createdAt;

    public AuditLog() {}

    public AuditLog(
            String actor,
            String path,
            String action,
            Map<String, Object> before,
            Map<String, Object> after,
            String reason) {
        this.actorUid = actor;
        this.targetPath = path;
        this.action = action;
        this.before = before;
        this.after = after;
        this.reason = reason;
    }
}
