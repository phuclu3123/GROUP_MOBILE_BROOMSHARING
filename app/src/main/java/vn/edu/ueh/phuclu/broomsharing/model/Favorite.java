package vn.edu.ueh.phuclu.broomsharing.model;

import java.util.Date;

/** users/{uid}/favorites/{roomId}; title is a snapshot from the time of saving. */
/*
    Snapshot tên và thời gian lưu phòng yêu thích.
    Document nằm dưới tài khoản; roomId dùng đọc lại trạng thái phòng hiện tại.
 */
public class Favorite {
    public String roomId,roomName;
    public Date createdAt;
    public Favorite() {}
}
