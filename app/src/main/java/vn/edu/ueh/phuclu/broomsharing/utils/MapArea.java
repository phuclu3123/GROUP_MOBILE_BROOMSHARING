package vn.edu.ueh.phuclu.broomsharing.utils;

import vn.edu.ueh.phuclu.broomsharing.model.MapPoint;

/** Pilot search window in HCMC, not an administrative boundary of the city. */
public final class MapArea {
    public static final double WEST = 106.45, SOUTH = 10.60, EAST = 106.95, NORTH = 10.95;
    public static final double RADIUS_METERS = 3000;
    public static final MapPoint CENTER = new MapPoint(10.7769, 106.6908, "Điểm thử TP.HCM");

    private MapArea() {}

    public static boolean contains(MapPoint point) {
        return point != null
                && point.latitude >= SOUTH
                && point.latitude <= NORTH
                && point.longitude >= WEST
                && point.longitude <= EAST;
    }

    public static double distance(MapPoint from, MapPoint to) {
        double lat = Math.toRadians(to.latitude - from.latitude),
                lon = Math.toRadians(to.longitude - from.longitude);
        double a =
                Math.sin(lat / 2) * Math.sin(lat / 2)
                        + Math.cos(Math.toRadians(from.latitude))
                                * Math.cos(Math.toRadians(to.latitude))
                                * Math.sin(lon / 2)
                                * Math.sin(lon / 2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(Math.min(1, a)), Math.sqrt(Math.max(0, 1 - a)));
    }

    public static boolean nearby(MapPoint origin, MapPoint destination) {
        return contains(destination) && distance(origin, destination) <= RADIUS_METERS;
    }
}
