package vn.edu.ueh.phuclu.broomsharing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.RoomListing;
import vn.edu.ueh.phuclu.broomsharing.utils.RoomFilter;
import static org.junit.Assert.*;

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
    @Test public void sortKeepsDifferentPriceUnitsInSeparateGroups() {
        RoomListing hour=item(),month=item(),quarter=item();
        hour.room.setActiveMode("HOURLY");hour.room.setPricePerHour(100000);
        month.room.setPricePerMonth(3000000);
        quarter.room.setPricePerMonth(0);quarter.room.setPricePerQuarter(8000000);
        List<RoomListing> rows=new ArrayList<>(Arrays.asList(quarter,month,hour));
        rows.sort(RoomFilter.order(1));
        assertEquals(Arrays.asList(hour,month,quarter),rows);
    }
    @Test public void sortUsesCurrentModePriceAndSupportsDescending() {
        RoomListing low=item(),high=item();low.room.setPricePerMonth(1000000);high.room.setPricePerMonth(3000000);
        low.room.setPricePerHour(9999999);high.room.setPricePerHour(1);
        assertTrue(RoomFilter.order(1).compare(low,high)<0);
        assertTrue(RoomFilter.order(2).compare(low,high)>0);
    }
}
