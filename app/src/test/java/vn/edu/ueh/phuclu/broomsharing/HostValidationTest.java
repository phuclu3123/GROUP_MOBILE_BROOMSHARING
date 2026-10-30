package vn.edu.ueh.phuclu.broomsharing;

import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.utils.HostValidation;
import vn.edu.ueh.phuclu.broomsharing.utils.TimeUtils;
import static org.junit.Assert.*;

// Kiểm tra dữ liệu tòa nhà/phòng trước khi gửi duyệt, gồm giá, lịch, ảnh và số liệu ngoài giới hạn.
public class HostValidationTest {
    private Room complete() {
        Room r = new Room();
        r.setRoomName("Meeting room");
        r.setBuildingId("building-1");
        r.setAddress("Address");
        r.setFloor("3");
        r.setArea(25);
        r.setCapacity(10);
        r.setOpenTime("08:00");
        r.setCloseTime("22:00");
        r.setPricePerHour(100000);
        r.setFixedStartMonth("2026-11");
        r.setRenewalPolicy("Gia hạn theo thỏa thuận trước khi hết kỳ.");
        r.setImageUrls(Arrays.asList("https://example.com/photo.jpg"));
        return r;
    }

    @Test
    public void requiresOwnedBuildingReferenceEvenForDraft() {
        Room r = complete();
        r.setBuildingId("");
        assertNotNull(HostValidation.room(r, false));
    }

    @Test
    public void draftCanOmitPublishDetails() {
        Room r = complete();
        r.setFloor("");
        r.setArea(0);
        r.setCapacity(0);
        r.setImageUrls(null);
        assertNull(HostValidation.room(r, false));
        assertNotNull(HostValidation.room(r, true));
    }

    @Test
    public void fixedPriceMustUseMonthOrQuarter() {
        Room r = complete();
        r.setActiveMode(Room.MODE_FIXED);
        r.setPricePerDay(500000);
        assertNotNull(HostValidation.room(r, true));
        r.setPricePerMonth(4000000);
        assertNull(HostValidation.room(r, true));
        r.setPricePerMonth(0);
        r.setPricePerQuarter(11000000);
        assertNull(HostValidation.room(r, true));
    }

    @Test
    public void rejectsInvalidMoneyAndNumericOverflow() {
        for (double value :
                new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, 1_000_000_000_001d}) {
            Room r = complete();
            r.setDeposit(value);
            assertNotNull(HostValidation.room(r, true));
        }
        assertTrue(HostValidation.money(0));
        assertTrue(HostValidation.money(1_000_000_000_000d));
    }

    @Test
    public void rejectsInvalidCapacityAndArea() {
        Room r = complete();
        r.setCapacity(10001);
        assertNotNull(HostValidation.room(r, false));
        r = complete();
        r.setArea(Double.NaN);
        assertNotNull(HostValidation.room(r, false));
    }

    @Test
    public void openingHoursAreValidated() {
        for (String time : new String[] {"24:00", "8:00", "08:60", "22:00", "23:00"}) {
            Room r = complete();
            r.setOpenTime(time);
            assertNotNull(HostValidation.room(r, true));
        }
    }

    @Test
    public void requiresPhotosAndLimitsGallery() {
        Room r = complete();
        r.setImageUrls(null);
        assertNotNull(HostValidation.room(r, true));
        r.setImageUrls(Arrays.asList("1", "2", "3", "4", "5", "6", "7"));
        assertNotNull(HostValidation.room(r, false));
    }

    @Test
    public void buildingRejectsInvalidCoordinatesAndPhone() {
        Building b = new Building();
        b.setName("Building");
        b.setAddress("Address");
        b.setDistrict("District");
        b.setContactPhone("0901234567");
        b.setContactName("Test contact");
        assertNull(HostValidation.building(b)); // No map selection is required yet.
        b.setLatitude(91);
        assertNotNull(HostValidation.building(b));
        b.setLatitude(0);
        b.setLongitude(Double.NaN);
        assertNotNull(HostValidation.building(b));
        b.setLongitude(0);
        b.setContactPhone("abc");
        assertNotNull(HostValidation.building(b));
    }

    @Test
    public void timezoneDoesNotDependOnDeviceSetting() {
        TimeZone old = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertEquals("01/01/2026 07:00", TimeUtils.formatVietnam(new Date(1767225600000L)));
        } finally {
            TimeZone.setDefault(old);
        }
    }
}
