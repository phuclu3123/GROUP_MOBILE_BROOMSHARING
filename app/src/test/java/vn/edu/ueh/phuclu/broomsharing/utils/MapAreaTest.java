package vn.edu.ueh.phuclu.broomsharing.utils;

import static org.junit.Assert.*;

import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.MapPoint;

public class MapAreaTest {
    @Test public void q8SourceAddressesAreFilteredAroundChosenOrigin(){
        MapPoint origin=new MapPoint(10.737316559278344,106.65579043328762,"Bùi Minh Trực");
        assertTrue(MapArea.contains(origin));
        assertTrue(MapArea.nearby(origin,new MapPoint(10.735189689411078,106.66512687390558,"849 Tạ Quang Bửu")));
        assertFalse(MapArea.nearby(origin,new MapPoint(10.706057560491558,106.6201187731683,"3086 Phạm Thế Hiển")));
    }
    @Test
    public void pilotIsHcmcAndExcludesOtherCities() {
        assertTrue(MapArea.contains(MapArea.CENTER));
        assertFalse(MapArea.contains(new MapPoint(21.0285, 105.8542, "Hà Nội")));
        assertFalse(MapArea.contains(null));
    }

    @Test
    public void radiusIsThreeKilometersOfStraightLineDistance() {
        MapPoint near = new MapPoint(10.7725, 106.698, "Đích thử");
        assertEquals(0, MapArea.distance(MapArea.CENTER, MapArea.CENTER), 0.001);
        assertTrue(MapArea.distance(MapArea.CENTER, near) > 800);
        assertTrue(MapArea.distance(MapArea.CENTER, near) < 1000);
        assertTrue(MapArea.nearby(MapArea.CENTER, near));
        assertFalse(MapArea.nearby(MapArea.CENTER, new MapPoint(10.82, 106.69, "Ngoài 3 km")));
    }

    @Test
    public void googleFallbackPreservesOriginDestinationAndEscapesAddress() {
        MapPoint end = new MapPoint(10.7725, 106.698, "Đích");
        String url = GoogleMapsLink.route(MapArea.CENTER, end);
        assertTrue(url.contains("origin=10.7769%2C106.6908"));
        assertTrue(url.contains("destination=10.7725%2C106.698"));
        assertTrue(url.contains("api=1"));
        assertFalse(url.contains("key="));
        String search = GoogleMapsLink.search(MapArea.CENTER, "A&B #1");
        assertFalse(search.contains("A&B"));
        assertTrue(search.contains("%26"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCoordinateRejected() {
        new MapPoint(Double.NaN, 106.7, "Không hợp lệ");
    }
}
