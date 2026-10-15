package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;

import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.RoomFilter;

public class RoomFilterTest {
    private RoomListing item() {
        Room room = new Room();
        room.setRoomName("Phòng họp Ánh Dương");
        room.setAddress("Nguyễn Tri Phương");
        room.setRoomType("Phòng họp");
        room.setActiveMode("FIXED");
        Building building = new Building();
        building.setName("Tòa nhà BRoom");
        building.setDistrict("Quận 10");
        building.setAddress("279 Nguyễn Tri Phương");
        return new RoomListing(room, building, "Host");
    }

    @Test
    public void searchIgnoresAccentsAndCase() {
        assertTrue(RoomFilter.matches(item(), " ANH DUONG ", "quan 10", "Phòng họp", "FIXED"));
    }

    @Test
    public void searchIncludesBuilding() {
        assertTrue(RoomFilter.matches(item(), "broom", "", "", ""));
    }

    @Test
    public void allFiltersMustMatch() {
        assertFalse(RoomFilter.matches(item(), "anh duong", "quan 1", "Bàn làm việc", "HOURLY"));
    }

    @Test
    public void modeIsNotInferredFromPrice() {
        assertFalse(RoomFilter.matches(item(), "", "", "", "HOURLY"));
    }

    @Test
    public void emptyFiltersIncludeMissingOptionalText() {
        RoomListing i = item();
        i.building.setDistrict(null);
        i.room.setAddress(null);
        assertTrue(RoomFilter.matches(i, "", "", "", ""));
    }
}
