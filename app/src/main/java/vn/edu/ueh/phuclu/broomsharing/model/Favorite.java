package vn.edu.ueh.phuclu.broomsharing.model;
import java.util.Date;
/** users/{uid}/favorites/{roomId}; title is a snapshot from the time of saving. */
public class Favorite {
    public String roomId,roomName;
    public Date createdAt;
    public Favorite() {}
}
