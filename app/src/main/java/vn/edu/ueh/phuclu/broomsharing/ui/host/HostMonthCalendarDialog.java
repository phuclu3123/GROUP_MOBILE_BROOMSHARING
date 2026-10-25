package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.app.Activity;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.DialogHostMonthCalendarBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Giữ bố cục lịch tháng của Hoàng; dữ liệu giữ lịch và múi giờ lấy từ contract chung.
public final class HostMonthCalendarDialog {
    private final Activity activity;
    private final DialogHostMonthCalendarBinding b;
    private final List<Room> rooms;
    private final List<Booking> bookings;
    private Room selected;
    private YearMonth month = YearMonth.now(HostSchedule.ZONE);
    private LocalDate day = LocalDate.now(HostSchedule.ZONE);
    private AlertDialog dialog;

    private HostMonthCalendarDialog(
            Activity activity, List<Room> rooms, List<Booking> bookings, String roomId) {
        this.activity = activity;
        this.rooms = new ArrayList<>(rooms);
        this.bookings = new ArrayList<>(bookings);
        b = DialogHostMonthCalendarBinding.inflate(activity.getLayoutInflater());
        for (Room room : rooms) if (room.getId().equals(roomId)) selected = room;
        if (selected == null && !rooms.isEmpty()) selected = rooms.get(0);
    }

    public static HostMonthCalendarDialog show(
            Activity activity, List<Room> rooms, List<Booking> bookings, String roomId) {
        if (rooms.isEmpty()) {
            Toast.makeText(
                            activity,
                            "Chưa tải được danh sách phòng. Vui lòng thử lại.",
                            Toast.LENGTH_LONG)
                    .show();
            return null;
        }
        HostMonthCalendarDialog calendar =
                new HostMonthCalendarDialog(activity, rooms, bookings, roomId);
        AlertDialog dialog =
                new AlertDialog.Builder(activity).setView(calendar.b.getRoot()).create();
        calendar.dialog = dialog;
        calendar.b.btnCloseCalendar.setOnClickListener(v -> dialog.dismiss());
        calendar.b.btnPrevMonth.setOnClickListener(
                v -> {
                    calendar.month = calendar.month.minusMonths(1);
                    calendar.day = calendar.month.atDay(1);
                    calendar.render();
                });
        calendar.b.btnNextMonth.setOnClickListener(
                v -> {
                    calendar.month = calendar.month.plusMonths(1);
                    calendar.day = calendar.month.atDay(1);
                    calendar.render();
                });
        calendar.b.btnChangeRoom.setOnClickListener(
                v -> {
                    String[] labels = new String[calendar.rooms.size()];
                    for (int i = 0; i < labels.length; i++)
                        labels[i] = calendar.rooms.get(i).getRoomName();
                    new AlertDialog.Builder(activity)
                            .setTitle("Chọn phòng")
                            .setItems(
                                    labels,
                                    (d, index) -> {
                                        calendar.selected = calendar.rooms.get(index);
                                        calendar.render();
                                    })
                            .show();
                });
        calendar.render();
        dialog.show();
        return calendar;
    }

    public void update(List<Booking> rows) {
        bookings.clear();
        bookings.addAll(rows);
        if (dialog != null && dialog.isShowing()) render();
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    private void render() {
        b.tvCalendarRoomName.setText(selected.getRoomName());
        b.tvCalendarMonthTitle.setText(
                String.format(
                        Locale.ROOT, "Tháng %02d / %d", month.getMonthValue(), month.getYear()));
        b.gridCalendarDays.removeAllViews();
        int before = month.atDay(1).getDayOfWeek().getValue() - 1;
        for (int index = 0; index < before + month.lengthOfMonth(); index++) {
            TextView cell = new TextView(activity);
            android.widget.GridLayout.LayoutParams lp =
                    new android.widget.GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = (int) (52 * activity.getResources().getDisplayMetrics().density);
            lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
            lp.setMargins(2, 2, 2, 2);
            cell.setLayoutParams(lp);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(12);
            if (index >= before) {
                LocalDate date = month.atDay(index - before + 1);
                List<Booking> held = HostCalendarPolicy.heldOnDay(bookings, selected.getId(), date);
                boolean closed = HostCalendarPolicy.hasClosure(selected, date);
                int fill =
                        android.graphics.Color.parseColor(
                                !held.isEmpty() ? "#D8EFEA" : closed ? "#E8ECEF" : "#FFFFFF");
                cell.setText(
                        date.getDayOfMonth() + (held.isEmpty() ? "" : "\n" + held.size() + " đơn"));
                cell.setTextColor(activity.getColor(R.color.text_primary));
                cell.setBackgroundColor(fill);
                if (date.equals(day)) {
                    android.graphics.drawable.GradientDrawable background =
                            new android.graphics.drawable.GradientDrawable();
                    background.setColor(fill);
                    background.setStroke(3, activity.getColor(R.color.teal));
                    cell.setBackground(background);
                }
                cell.setContentDescription(
                        date
                                + ": "
                                + held.size()
                                + " đơn giữ chỗ"
                                + (closed ? ", có khoảng khóa hoặc nghỉ" : ""));
                cell.setOnClickListener(
                        v -> {
                            day = date;
                            render();
                        });
            }
            b.gridCalendarDays.addView(cell);
        }
        b.tvSelectedDayTitle.setText("Lịch giữ chỗ ngày " + day);
        b.layoutSelectedDayBookings.removeAllViews();
        b.tvEmptyDayDetail.setVisibility(View.GONE);
        TextView summary = new TextView(activity);
        summary.setText(
                HostSchedule.daySummary(
                        selected,
                        HostCalendarPolicy.heldOnDay(bookings, selected.getId(), day),
                        day));
        summary.setTextColor(activity.getColor(R.color.text_primary));
        summary.setPadding(12, 8, 12, 8);
        b.layoutSelectedDayBookings.addView(summary);
        DateTimeFormatter format =
                DateTimeFormatter.ofPattern("dd/MM HH:mm", new Locale("vi", "VN"));
        for (Booking item : HostCalendarPolicy.heldOnDay(bookings, selected.getId(), day)) {
            TextView row = new TextView(activity);
            row.setText(
                    item.startAt.toInstant().atZone(HostSchedule.ZONE).format(format)
                            + " → "
                            + item.endAt.toInstant().atZone(HostSchedule.ZONE).format(format)
                            + "\n"
                            + FormatUtils.bookingStatus(item.status)
                            + " · "
                            + item.guestName);
            row.setTextColor(activity.getColor(R.color.text_primary));
            row.setPadding(12, 8, 12, 8);
            b.layoutSelectedDayBookings.addView(row);
        }
    }
}
