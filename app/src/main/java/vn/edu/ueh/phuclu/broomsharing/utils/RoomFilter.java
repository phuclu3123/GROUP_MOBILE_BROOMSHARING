package vn.edu.ueh.phuclu.broomsharing.utils;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;
import vn.edu.ueh.phuclu.broomsharing.model.RoomListing;

/** Local search on loaded pages, using building district rather than guessed coordinates. */
public final class RoomFilter {
    private RoomFilter() {}

    public static double unitPrice(RoomListing item) {
        return "HOURLY".equals(item.room.getActiveMode()) ? item.room.getPricePerHour()
            : item.room.getPricePerMonth()>0 ? item.room.getPricePerMonth() : item.room.getPricePerQuarter();
    }
    private static int priceGroup(RoomListing item) {
        return "HOURLY".equals(item.room.getActiveMode())?0:item.room.getPricePerMonth()>0?1:2;
    }
    public static Comparator<RoomListing> order(int sort) {
        if(sort==3)return Comparator.comparingInt((RoomListing item)->item.room.getCapacity()).reversed();
        Comparator<RoomListing> price=Comparator.comparingDouble(RoomFilter::unitPrice);
        if(sort==2)price=price.reversed();
        return Comparator.comparingInt(RoomFilter::priceGroup).thenComparing(price);
    }

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
