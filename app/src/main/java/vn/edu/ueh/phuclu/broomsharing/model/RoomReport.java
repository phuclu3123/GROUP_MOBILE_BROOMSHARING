package vn.edu.ueh.phuclu.broomsharing.model;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;
public class RoomReport {
    public long schemaVersion=1;
    public String id="",reporterUid="",roomId="",ownerUid="",reason="",description="",status="OPEN";
    @ServerTimestamp public Date createdAt,updatedAt;
    public RoomReport(){}
}
