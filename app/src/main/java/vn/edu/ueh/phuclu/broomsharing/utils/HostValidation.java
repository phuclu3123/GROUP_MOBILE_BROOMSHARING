package vn.edu.ueh.phuclu.broomsharing.utils;

import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.model.Room;

/** Business validation shared by UI and repository. Returns null when valid. */
public final class HostValidation {
    private HostValidation() {}

    public static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean money(double value) {
        return Double.isFinite(value) && value == Math.rint(value) && value >= 0 && value <= 1_000_000_000_000d;
    }

    public static boolean phone(String value) {
        return value != null && value.replaceAll("[ +().-]", "").matches("[0-9]{8,15}");
    }

    public static String building(Building b) {
        if (empty(b.getName()) || empty(b.getAddress()) || empty(b.getDistrict()))
            return "Vui lòng nhập tên, địa chỉ và quận/huyện.";
        if (empty(b.getContactName())) return "Vui lòng nhập tên người liên hệ.";
        if (!phone(b.getContactPhone())) return "Số điện thoại cần 8–15 chữ số.";
        if (!Double.isFinite(b.getLatitude())
                || !Double.isFinite(b.getLongitude())
                || Math.abs(b.getLatitude()) > 90
                || Math.abs(b.getLongitude()) > 180) return "Tọa độ không hợp lệ.";
        return null;
    }

    public static String room(Room r, boolean submit) {
        String scheduleError = HostSchedule.validate(r);
        if (scheduleError != null) return scheduleError;
        if (submit && Room.MODE_FIXED.equals(r.getActiveMode())
                && (empty(r.getFixedStartMonth()) || empty(r.getRenewalPolicy())))
            return "Gửi duyệt thuê cố định cần tháng bắt đầu và điều khoản gia hạn.";
        if (empty(r.getRoomName())) return "Vui lòng nhập tên không gian.";
        if (empty(r.getBuildingId())) return "Vui lòng tạo và chọn tòa nhà của bạn trước.";
        if (!money(r.getDeposit())
                || !money(r.getPricePerHour())
                || !money(r.getPricePerMonth())
                || !money(r.getPricePerQuarter()))
            return "Giá và tiền cọc phải từ 0 đến 1.000 tỷ đồng.";
        if (!Room.MODE_HOURLY.equals(r.getActiveMode())
                && !Room.MODE_FIXED.equals(r.getActiveMode()))
            return "Vui lòng chọn hình thức thuê.";
        if (r.getCapacity() < 0
                || r.getCapacity() > 10000
                || !Double.isFinite(r.getArea())
                || r.getArea() < 0
                || r.getArea() > 1000000) return "Sức chứa hoặc diện tích không hợp lệ.";
        if (r.getImageUrls() != null && r.getImageUrls().size() > 6)
            return "Tối đa 6 ảnh không gian.";
        if (submit) {
            if (empty(r.getAddress())
                    || empty(r.getFloor())
                    || r.getCapacity() <= 0
                    || r.getArea() <= 0)
                return "Gửi duyệt cần địa chỉ, tầng, diện tích và sức chứa lớn hơn 0.";
            if (Room.MODE_HOURLY.equals(r.getActiveMode()) && r.getPricePerHour() <= 0)
                return "Giá theo giờ phải lớn hơn 0.";
            if (Room.MODE_FIXED.equals(r.getActiveMode())
                    && r.getPricePerMonth() <= 0
                    && r.getPricePerQuarter() <= 0)
                return "Nhập ít nhất một giá tháng hoặc quý lớn hơn 0.";
            if (r.getImageUrls() == null || r.getImageUrls().isEmpty())
                return "Gửi duyệt cần ít nhất một ảnh không gian.";
        }
        if (r.getOpenTime() == null
                || r.getCloseTime() == null
                || !r.getOpenTime().matches("([01][0-9]|2[0-3]):[0-5][0-9]")
                || !r.getCloseTime().matches("([01][0-9]|2[0-3]):[0-5][0-9]")
                || r.getOpenTime().compareTo(r.getCloseTime()) >= 0)
            return "Giờ mở cửa phải trước giờ đóng cửa trong cùng ngày.";
        return null;
    }
}
