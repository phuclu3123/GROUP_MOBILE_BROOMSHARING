package vn.edu.ueh.phuclu.broomsharing.model;

/** Read model for guest UI; never written as a room document. */
/*
   Kết quả ghép dữ liệu phòng với tòa nhà và Host cho giao diện.
   Các trường bổ sung không tạo thành một document RoomListing riêng.
*/
public final class RoomListing implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public final Room room;
    public final Building building;
    public final String hostName;
    public final String hostPhotoUrl;
    public final transient vn.edu.ueh.phuclu.broomsharing.utils.FixedRentalBadge availability;

    public RoomListing(Room room, Building building, String hostName) {
        this(room, building, hostName, "");
    }

    public RoomListing(Room room, Building building, String hostName, String hostPhotoUrl) {
        this(room, building, hostName, hostPhotoUrl, null);
    }

    public RoomListing(Room room, Building building, String hostName, String hostPhotoUrl,
                       vn.edu.ueh.phuclu.broomsharing.utils.FixedRentalBadge availability) {
        this.room = room;
        this.building = building;
        this.hostName = hostName == null ? "" : hostName;
        this.hostPhotoUrl = hostPhotoUrl == null ? "" : hostPhotoUrl;
        this.availability = availability;
    }

    public RoomListing withAvailability(vn.edu.ueh.phuclu.broomsharing.utils.FixedRentalBadge value) {
        return new RoomListing(room, building, hostName, hostPhotoUrl, value);
    }
}
