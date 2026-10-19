package vn.edu.ueh.phuclu.broomsharing.model;
import java.io.Serializable;
/** Read model for guest UI; never written as a room document. */
public final class RoomListing {
    public final Room room;
    public final Building building;
    public final String hostName;

    public RoomListing(Room room, Building building, String hostName) {
        this.room = room;
        this.building = building;
        this.hostName = hostName == null ? "" : hostName;
    }
}
