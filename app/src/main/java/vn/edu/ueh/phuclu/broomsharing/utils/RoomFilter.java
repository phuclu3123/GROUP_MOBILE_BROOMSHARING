package vn.edu.ueh.phuclu.broomsharing.utils;

import vn.edu.ueh.phuclu.broomsharing.model.RoomListing;

import java.text.Normalizer;
import java.util.Locale;

/** Local search on loaded pages, using building district rather than guessed coordinates. */
public final class RoomFilter {
    private RoomFilter() {}

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .trim();
    }

    public static boolean matches(
            RoomListing item, String search, String area, String type, String mode) {
        if (!type.isEmpty() && !type.equals(item.room.getRoomType())) return false;
        if (!mode.isEmpty() && !mode.equals(item.room.getActiveMode())) return false;
        String text =
                normalize(item.room.getRoomName())
                        + " "
                        + normalize(item.room.getAddress())
                        + " "
                        + normalize(item.building.getName())
                        + " "
                        + normalize(item.building.getDistrict());
        return text.contains(normalize(search))
                && (normalize(item.building.getDistrict())
                                + " "
                                + normalize(item.building.getAddress()))
                        .contains(normalize(area));
    }
}
