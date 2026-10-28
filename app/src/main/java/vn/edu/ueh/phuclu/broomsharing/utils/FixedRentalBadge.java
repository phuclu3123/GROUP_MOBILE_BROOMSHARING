package vn.edu.ueh.phuclu.broomsharing.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;

// Trạng thái lịch công khai của phòng tháng/quý; không suy diễn đã nhận tiền từ một khóa lịch cũ.
public final class FixedRentalBadge {
    public final String label, detail;
    public final int tone; // 0 chưa xác định, 1 còn kỳ trống, 2 tạm giữ, 3 có lịch, 4 sắp hết kỳ.
    public FixedRentalBadge(String label, String detail, int tone) { this.label = label; this.detail = detail; this.tone = tone; }
    public static FixedRentalBadge unknown(boolean loading) {
        return new FixedRentalBadge(loading ? "Đang kiểm tra lịch" : "Chưa xác định lịch", "Mở phòng để kiểm tra kỳ thuê cụ thể.", 0);
    }

    // Dựng nhãn từ lịch thực tế, phân biệt giữ tạm, đặt trước và kỳ thuê còn tối đa 3 ngày.
    public static FixedRentalBadge from(Room room, List<Booking> windows, LocalDate loadedUntil, long now) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(HostSchedule.ZONE).toLocalDate();
        int months = room.getPricePerMonth() > 0 ? 1 : 3;
        LocalDate candidate = today.withDayOfMonth(1).plusMonths(1);
        try {
            LocalDate configured = YearMonth.parse(room.getFixedStartMonth()).atDay(1);
            if (configured.isAfter(candidate)) candidate = configured;
        } catch (RuntimeException invalid) { return unknown(false); }
        LocalDate firstCandidate = candidate, free = null;
        for (; !candidate.plusMonths(months).isAfter(loadedUntil); candidate = candidate.plusMonths(1)) {
            if (BookingAvailability.conflict(room, windows, date(candidate), date(candidate.plusMonths(months))) == null) {
                free = candidate; break;
            }
        }
        List<Booking> sorted = new ArrayList<>(windows);
        sorted.sort(Comparator.comparing(b -> b.startAt));
        Booking current = null, upcoming = null;
        for (Booking b : sorted) {
            if (b.startAt == null || b.endAt == null || !HostSchedule.occupies(b)) continue;
            if (b.startAt.getTime() <= now && now < b.endAt.getTime()) current = b;
            if (b.startAt.getTime() > now && upcoming == null) upcoming = b;
        }
        String next = free == null ? "Chưa có kỳ trống trong lịch đến " + format(loadedUntil)
                : "Nhận thuê " + months + " tháng từ " + format(free);
        if (current != null) {
            boolean pending = current.isInstantPayment() && "PENDING".equals(current.status);
            boolean ending = current.endAt.getTime() - now <= 3L * 86400000;
            return new FixedRentalBadge(pending ? "Tạm giữ • Chờ thanh toán" : ending ? "Sắp hết kỳ thuê" : "Đang có lịch thuê",
                    "Kỳ hiện tại đến " + format(current.endAt) + "\n" + next, pending ? 2 : ending ? 4 : 3);
        }
        if (upcoming != null && (free == null || free.isAfter(firstCandidate))) {
            boolean pending = upcoming.isInstantPayment() && "PENDING".equals(upcoming.status);
            return new FixedRentalBadge(pending ? "Tạm giữ • Chờ thanh toán" : "Đã đặt trước",
                    format(upcoming.startAt) + " đến " + format(upcoming.endAt) + "\n" + next, pending ? 2 : 3);
        }
        return new FixedRentalBadge(free == null ? "Chưa có kỳ trống" : "Còn kỳ thuê trống",
                next + (upcoming == null ? "" : "\nĐã có lịch từ " + format(upcoming.startAt)), free == null ? 3 : 1);
    }

    private static Date date(LocalDate day) { return Date.from(day.atStartOfDay(HostSchedule.ZONE).toInstant()); }
    private static String format(LocalDate day) { return day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }
    private static String format(Date date) { return format(date.toInstant().atZone(HostSchedule.ZONE).toLocalDate()); }
    @Override public boolean equals(Object other) {
        if (!(other instanceof FixedRentalBadge)) return false;
        FixedRentalBadge b = (FixedRentalBadge) other;
        return tone == b.tone && label.equals(b.label) && detail.equals(b.detail);
    }
    @Override public int hashCode() { return Objects.hash(label, detail, tone); }
}
