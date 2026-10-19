package vn.edu.ueh.phuclu.broomsharing.model;

import java.io.Serializable;

public class MapPoint implements Serializable {
    public final double latitude, longitude;
    public final String name;

    public MapPoint(double latitude, double longitude, String name) {
        if (!Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90
                || Math.abs(longitude) > 180)
            throw new IllegalArgumentException("Tọa độ không hợp lệ.");
        this.latitude = latitude;
        this.longitude = longitude;
        this.name = name == null ? "Điểm đã chọn" : name;
    }
}
