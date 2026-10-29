package vn.edu.ueh.phuclu.broomsharing.model;

import java.io.Serializable;
import java.util.Date;

/** A local-time selection stored as UTC instants, with an exclusive end. */
/*
    Khoảng khóa lịch nhúng trong rooms.blackoutRanges.
    Khoảng thời gian sử dụng đầu bao gồm, cuối loại trừ như lịch booking.
 */
public class RoomBlackout implements Serializable {
    private Date startAt;
    private Date endAt;
    private String reason;

    public RoomBlackout() {}
    public RoomBlackout(Date start, Date end, String reason) {
        this.startAt = start;
        this.endAt = end;
        this.reason = reason;
    }
    public Date getStartAt() { return startAt; }
    public void setStartAt(Date value) { startAt = value; }
    public Date getEndAt() { return endAt; }
    public void setEndAt(Date value) { endAt = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
}
