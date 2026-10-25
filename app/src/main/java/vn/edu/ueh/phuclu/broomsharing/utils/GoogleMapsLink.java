package vn.edu.ueh.phuclu.broomsharing.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import vn.edu.ueh.phuclu.broomsharing.model.MapPoint;

public final class GoogleMapsLink {
    private GoogleMapsLink() {}

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String coordinates(MapPoint point) {
        return point.latitude + "," + point.longitude;
    }

    public static String route(MapPoint from, MapPoint to) {
        return "https://www.google.com/maps/dir/?api=1&origin="
                + encode(coordinates(from))
                + "&destination="
                + encode(coordinates(to))
                + "&travelmode=driving";
    }

    public static String search(MapPoint center, String query) {
        return "https://www.google.com/maps/search/?api=1&query="
                + encode(
                        (query == null || query.trim().isEmpty()
                                ? coordinates(center)
                                : query.trim() + ", TP.HCM"));
    }
}
