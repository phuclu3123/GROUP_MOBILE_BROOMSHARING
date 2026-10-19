package vn.edu.ueh.phuclu.broomsharing.model;

import java.util.List;

/** Directions geometry and its distance/time, without a second transport representation. */
public class MapRoute {
    public final List<MapPoint> points;
    public final double distanceMeters, durationSeconds;

    public MapRoute(List<MapPoint> points, double distanceMeters, double durationSeconds) {
        if (points == null
                || points.size() < 2
                || !Double.isFinite(distanceMeters)
                || !Double.isFinite(durationSeconds)
                || distanceMeters <= 0
                || durationSeconds <= 0)
            throw new IllegalArgumentException("Tuyến đường không hợp lệ.");
        this.points = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(points));
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
    }
}
