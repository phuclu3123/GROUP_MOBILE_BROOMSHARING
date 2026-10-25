package vn.edu.ueh.phuclu.broomsharing.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.CalendarContract;
import android.widget.Toast;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;

// Ứng dụng Lịch tự xác nhận thêm sự kiện; app không xin quyền đọc/ghi lịch thiết bị.
/*
   Mở biểu mẫu thêm sự kiện trong ứng dụng Lịch bằng Intent, không xin quyền đọc lịch.
   Chỉ đề xuất lưu booking đã xác nhận còn thời gian sử dụng; sự kiện không tự đồng bộ khi hủy.
*/
public final class BookingCalendar {
    private BookingCalendar() {}

    public static boolean canSave(Booking b, long now) {
        return b != null
                && b.startAt != null
                && b.endAt != null
                && b.endAt.after(b.startAt)
                && b.endAt.getTime() > now
                && ("CONFIRMED".equals(b.status) || "CHECKED_IN".equals(b.status));
    }

    public static void open(Activity activity, Booking b) {
        if (!canSave(b, System.currentTimeMillis())) {
            Toast.makeText(
                            activity,
                            "Chỉ lưu lịch cho booking đã xác nhận và chưa kết thúc.",
                            Toast.LENGTH_LONG)
                    .show();
            return;
        }
        try {
            activity.startActivity(eventIntent(b));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(
                            activity,
                            "Thiết bị chưa có ứng dụng Lịch. Bạn vẫn xem lịch thuê trong app.",
                            Toast.LENGTH_LONG)
                    .show();
        }
    }

    // Tạo dữ liệu chuyển giao; ứng dụng Lịch của máy quyết định việc ghi sau khi người dùng xác
    // nhận.
    public static Intent eventIntent(Booking b) {
        return new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, b.startAt.getTime())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, b.endAt.getTime())
                .putExtra(CalendarContract.Events.TITLE, b.roomName)
                .putExtra(CalendarContract.Events.EVENT_LOCATION, b.roomAddress)
                .putExtra(CalendarContract.Events.EVENT_TIMEZONE, "Asia/Ho_Chi_Minh")
                .putExtra(
                        CalendarContract.Events.DESCRIPTION,
                        "Mã đặt chỗ: "
                                + b.id
                                + "\n"
                                + "Kiểm tra trạng thái mới nhất trong BroomSharing. Lịch thiết bị"
                                + " không tự cập nhật khi booking thay đổi.");
    }
}
