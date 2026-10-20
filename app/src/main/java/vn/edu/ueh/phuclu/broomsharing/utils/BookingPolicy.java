package vn.edu.ueh.phuclu.broomsharing.utils;

import vn.edu.ueh.phuclu.broomsharing.model.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Plain Java rules, no Firebase I/O or UI. Repository always reads current room data. */
public final class BookingPolicy {
    private BookingPolicy() {}

    public static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }

    public static String id(String id) {
        require(id != null && id.matches("[a-zA-Z0-9_-]{1,128}"), "Mã tài liệu không hợp lệ.");
        return id;
    }

    public static String hash(String text) {
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public static String monthKey(String roomId, YearMonth month) {
        return id(roomId) + "_" + month.format(DateTimeFormatter.ofPattern("uuuuMM"));
    }

    public static String fingerprint(Booking request) {
        return hash(
                request.roomId
                        + ":"
                        + request.startAt.getTime()
                        + ":"
                        + (request.endAt == null ? 0 : request.endAt.getTime())
                        + ":"
                        + request.rentalMonths);
    }

    public static Booking plan(Room room, Booking request, long now) {
        String error = HostValidation.room(room, true);
        require(error == null, error);
        require(request.startAt != null, "Chọn thời gian bắt đầu.");
        long start = request.startAt.getTime();
        require(
                start > now && start <= now + 366L * 86400000,
                "Thời gian bắt đầu phải ở tương lai, tối đa một năm.");
        ZonedDateTime local = request.startAt.toInstant().atZone(HostSchedule.ZONE);
        require(local.getSecond() == 0 && local.getNano() == 0, "Thời gian phải tròn phút.");
        Booking booking = new Booking();
        booking.schemaVersion = 2;
        booking.roomId = id(room.getId());
        booking.roomName = room.getRoomName();
        booking.roomAddress = room.getAddress();
        booking.ownerUid = room.getOwnerUid();
        booking.activeMode = room.getActiveMode();
        booking.startAt = new Date(start);
        booking.rentalMonths = request.rentalMonths;
        double rental;
        YearMonth first = YearMonth.from(local);
        if (Room.MODE_HOURLY.equals(room.getActiveMode())) {
            require(
                    request.rentalMonths == 0 && request.endAt != null,
                    "Chọn khung giờ thuê hợp lệ.");
            ZonedDateTime end = request.endAt.toInstant().atZone(HostSchedule.ZONE);
            long midnight =
                    local.toLocalDate().atStartOfDay(HostSchedule.ZONE).toInstant().toEpochMilli();
            long from = (start - midnight) / 60000,
                    to = (request.endAt.getTime() - midnight) / 60000;
            int step = room.getSlotMinutes();
            require(
                    request.endAt.getTime() > start
                            && request.endAt.getTime() <= midnight + 86400000L
                            && end.getSecond() == 0
                            && end.getNano() == 0,
                    "Khung giờ phải nằm trong một ngày.");
            require(
                    from % step == 0 && to % step == 0 && to - from >= room.getMinBookingMinutes(),
                    "Khung giờ chưa khớp bước giờ/thời lượng tối thiểu.");
            require(
                    room.getWorkingDays().contains(local.getDayOfWeek().getValue())
                            && from >= minutes(room.getOpenTime())
                            && to <= minutes(room.getCloseTime()),
                    "Phòng đóng cửa trong khung giờ đã chọn.");
            booking.endAt = request.endAt;
            booking.slotStart = (local.getDayOfMonth() - 1) * 48 + (int) from / 30;
            booking.slotCount = (int) (to - from) / 30;
            for (int i = 0; i < booking.slotCount; i++) booking.slotKeys.add(booking.slotStart + i);
            booking.monthKeys.add(monthKey(room.getId(), first));
            rental = Math.round(room.getPricePerHour() * (to - from) / 60d);
        } else {
            require(
                    request.rentalMonths == 1 || request.rentalMonths == 3,
                    "Chỉ hỗ trợ thuê một tháng hoặc một quý.");
            require(
                    local.getDayOfMonth() == 1 && local.toLocalTime().equals(LocalTime.MIDNIGHT),
                    "Thuê cố định bắt đầu 00:00 ngày 1, giờ Việt Nam.");
            require(
                    !HostValidation.empty(room.getFixedStartMonth())
                            && first.compareTo(YearMonth.parse(room.getFixedStartMonth())) >= 0,
                    "Chưa đến tháng nhận thuê.");
            booking.endAt = Date.from(local.plusMonths(request.rentalMonths).toInstant());
            for (int i = 0; i < request.rentalMonths; i++)
                booking.monthKeys.add(monthKey(room.getId(), first.plusMonths(i)));
            rental =
                    request.rentalMonths == 1 ? room.getPricePerMonth() : room.getPricePerQuarter();
        }
        require(
                rental > 0
                        && HostValidation.money(rental)
                        && HostValidation.money(room.getDeposit()),
                "Giá thuê/cọc không hợp lệ.");
        validateBlackouts(room, booking.startAt, booking.endAt);
        booking.totalAmount = rental;
        booking.paymentStatus = "UNAVAILABLE";
        booking.priceSnapshot.put("currency", "VND");
        booking.priceSnapshot.put("rentalAmount", rental);
        booking.priceSnapshot.put("feeAmount", 0L);
        booking.priceSnapshot.put("depositAmount", Math.round(rental * 0.4));
        booking.priceSnapshot.put("advancePercent", 40L);
        booking.priceSnapshot.put("cancellationPolicyVersion", CancellationPolicy.VERSION);
        booking.priceSnapshot.put("totalAmount", rental);
        booking.priceSnapshot.put("paymentDueAmount", Math.round(rental * 0.4));
        booking.priceSnapshot.put("rentalPolicy", room.getRentalPolicy());
        booking.priceSnapshot.put("renewalPolicy", room.getRenewalPolicy());
        return booking;
    }

    private static int minutes(String value) {
        LocalTime time = LocalTime.parse(value);
        return time.getHour() * 60 + time.getMinute();
    }

    public static void validateBlackouts(Room room, Date start, Date end) {
        for (String value : room.getBlackoutDates()) {
            Date day =
                    Date.from(
                            HostSchedule.blackoutDate(value)
                                    .atStartOfDay(HostSchedule.ZONE)
                                    .toInstant());
            require(
                    !HostSchedule.overlaps(start, end, day, new Date(day.getTime() + 86400000)),
                    "Phòng đã khóa ngày trong kỳ thuê.");
        }
        for (RoomBlackout range : room.getBlackoutRanges())
            require(
                    !HostSchedule.overlaps(start, end, range.getStartAt(), range.getEndAt()),
                    "Trùng khung khóa lịch: " + range.getReason());
    }

    public static void reserve(AvailabilityMonth month, Booking booking) {
        require(month.fixedBookingId.isEmpty(), "Tháng thuê đã được giữ chỗ.");
        if (Room.MODE_FIXED.equals(booking.activeMode)) {
            require(
                    month.slotsBusy.isEmpty() && month.hourlyBookings.isEmpty(),
                    "Tháng đã có đặt chỗ theo giờ.");
            month.fixedBookingId = booking.id;
        } else {
            require(
                    Collections.disjoint(month.slotsBusy, booking.slotKeys),
                    "Khung giờ đã được giữ chỗ.");
            month.slotsBusy.addAll(booking.slotKeys);
            month.hourlyBookings.put(booking.id, new ArrayList<>(booking.slotKeys));
        }
        month.lastBookingId = booking.id;
        month.updatedAt = null;
    }

    public static void release(AvailabilityMonth month, Booking booking) {
        if (Room.MODE_FIXED.equals(booking.activeMode)) {
            require(booking.id.equals(month.fixedBookingId), "Giữ chỗ không còn khớp.");
            month.fixedBookingId = "";
        } else {
            require(
                    booking.slotKeys.equals(month.hourlyBookings.get(booking.id))
                            && month.slotsBusy.containsAll(booking.slotKeys),
                    "Giữ chỗ không còn khớp.");
            month.slotsBusy.removeAll(booking.slotKeys);
            month.hourlyBookings.remove(booking.id);
        }
        month.lastBookingId = booking.id;
        month.updatedAt = null;
    }

    public static void transition(Booking booking, String target, String reason, long now) {
        require(
                Arrays.asList("CONFIRMED", "REJECTED", "CANCELLED", "CHECKED_IN", "COMPLETED")
                        .contains(target),
                "Trạng thái không hợp lệ.");
        require(
                reason != null
                        && reason.length() <= 500
                        && (!"REJECTED".equals(target) || !reason.trim().isEmpty()),
                "Từ chối cần lý do, tối đa 500 ký tự.");
        if (target.equals(booking.status)) return;
        List<String> allowed =
                "CANCELLED".equals(target)
                        ? Arrays.asList("PENDING", "CONFIRMED")
                        : "CHECKED_IN".equals(target)
                                ? Collections.singletonList("CONFIRMED")
                                : "COMPLETED".equals(target)
                                        ? Arrays.asList("CONFIRMED", "CHECKED_IN")
                                        : Collections.singletonList("PENDING");
        require(allowed.contains(booking.status), "Booking đã đổi trạng thái. Vui lòng tải lại.");
        if ("CHECKED_IN".equals(target))
            require(
                    booking.startAt.getTime() <= now && now < booking.endAt.getTime(),
                    "Chỉ ghi nhận vào phòng trong thời gian thuê.");
        else if ("COMPLETED".equals(target))
            require(booking.endAt.getTime() <= now, "Chưa kết thúc thời gian thuê.");
        else require(booking.startAt.getTime() > now, "Không được đổi yêu cầu đã bắt đầu.");
    }

    public static void validateRoomChange(
            Room previous, Room next, List<AvailabilityMonth> months, long now) {
        for (AvailabilityMonth month : months) {
            YearMonth ym = YearMonth.of(month.monthIndex / 100, month.monthIndex % 100);
            if (!month.fixedBookingId.isEmpty()) {
                require(
                        previous.getActiveMode().equals(next.getActiveMode())
                                && previous.getBuildingId().equals(next.getBuildingId()),
                        "Còn lịch giữ chỗ, chưa thể đổi hình thức thuê/tòa nhà.");
                require(
                        !HostValidation.empty(next.getFixedStartMonth())
                                && YearMonth.parse(next.getFixedStartMonth()).compareTo(ym) <= 0,
                        "Tháng nhận thuê mới xung đột lịch giữ chỗ.");
                validateBlackouts(
                        next,
                        Date.from(ym.atDay(1).atStartOfDay(HostSchedule.ZONE).toInstant()),
                        Date.from(
                                ym.plusMonths(1)
                                        .atDay(1)
                                        .atStartOfDay(HostSchedule.ZONE)
                                        .toInstant()));
            }
            for (int slot : month.slotsBusy) {
                ZonedDateTime at =
                        ym.atDay(1).atStartOfDay(HostSchedule.ZONE).plusMinutes(slot * 30L);
                Date start = Date.from(at.toInstant()), end = new Date(start.getTime() + 1800000);
                if (end.getTime() <= now) continue;
                require(
                        previous.getActiveMode().equals(next.getActiveMode())
                                && previous.getBuildingId().equals(next.getBuildingId())
                                && previous.getSlotMinutes() == next.getSlotMinutes()
                                && previous.getMinBookingMinutes() == next.getMinBookingMinutes(),
                        "Còn lịch giữ chỗ, chưa thể đổi tòa nhà/hình thức/bước giờ/thời lượng tối"
                            + " thiểu.");
                require(
                        next.getWorkingDays().contains(at.getDayOfWeek().getValue())
                                && at.getHour() * 60 + at.getMinute() >= minutes(next.getOpenTime())
                                && at.getHour() * 60 + at.getMinute() + 30
                                        <= minutes(next.getCloseTime()),
                        "Giờ/ngày hoạt động mới xung đột lịch giữ chỗ.");
                validateBlackouts(next, start, end);
            }
        }
    }
}
