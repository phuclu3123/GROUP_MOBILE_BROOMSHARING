package vn.edu.ueh.phuclu.broomsharing.model;

/** Read model for guest UI; never written as a room document. */
/*
   Kết quả ghép dữ liệu phòng với tòa nhà và Host cho giao diện.
   Các trường bổ sung không tạo thành một document RoomListing riêng.
*/
public final class RoomListing {
    public final Room room;
    public final Building building;
    public final String hostName;
    public final String hostPhotoUrl;

    public RoomListing(Room room, Building building, String hostName) {
        this(room, building, hostName, "");
    }

    public RoomListing(Room room, Building building, String hostName, String hostPhotoUrl) {
        this.room = room;
        this.building = building;
        this.hostName = hostName == null ? "" : hostName;
        this.hostPhotoUrl = hostPhotoUrl == null ? "" : hostPhotoUrl;
    }
}
