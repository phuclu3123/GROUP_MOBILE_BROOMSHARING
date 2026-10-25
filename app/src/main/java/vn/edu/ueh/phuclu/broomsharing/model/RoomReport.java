package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/*
   Báo cáo về phòng, phản hồi của Host và kết luận xử lý của Admin.
   Host chỉ được phản hồi khi còn OPEN, không tự sửa trạng thái hay kết luận.
*/
public class RoomReport {
    public long schemaVersion = 1;
    public String id = "",
            reporterUid = "",
            roomId = "",
            ownerUid = "",
            reason = "",
            description = "",
            status = "OPEN";
    public String hostResponse = "", resolution = "", action = "", bookingId = "";
    public Date hostRespondedAt, reviewedAt;
    @ServerTimestamp public Date createdAt, updatedAt;

    public RoomReport() {}
}
