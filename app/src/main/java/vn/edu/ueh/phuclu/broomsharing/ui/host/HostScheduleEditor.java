package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.chip.Chip;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ViewHostScheduleBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Room;

/** Form controls only; the shared ViewModel persists the room. */
public final class HostScheduleEditor {
    private final ViewHostScheduleBinding b;
    private final Chip[] days;
    private String month;

    public HostScheduleEditor(AppCompatActivity activity, Room room, Bundle restored) {
        b = ViewHostScheduleBinding.bind(activity.findViewById(R.id.hostSchedule));
        days = new Chip[] {b.day1, b.day2, b.day3, b.day4, b.day5, b.day6, b.day7};
        for (int i = 0; i < days.length; i++)
            days[i].setChecked(room.getWorkingDays() != null && room.getWorkingDays().contains(i + 1));
        b.slotGroup.check(room.getSlotMinutes() == 30 ? R.id.slot30 : R.id.slot60);
        b.minMinutes.setText(String.valueOf(room.getMinBookingMinutes()));
        b.renewalPolicy.setText(room.getRenewalPolicy());
        b.rentalPolicy.setText(room.getRentalPolicy());
        month = restored == null ? room.getFixedStartMonth() : restored.getString("fixedMonth", room.getFixedStartMonth());
        showMonth();
        b.fixedMonth.setOnClickListener(v -> {
            LocalDate now = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            DatePickerDialog picker = new DatePickerDialog(activity, (p, y, m, day) -> {
                month = YearMonth.of(y, m + 1).toString();
                showMonth();
            }, now.getYear(), now.getMonthValue() - 1, 1);
            picker.show();
        });
        b.viewSchedule.setEnabled(room.getId() != null);
        b.viewSchedule.setOnClickListener(v -> activity.startActivity(
                new Intent(activity, HostBookingsActivity.class).putExtra("ROOM_ID", room.getId())));
    }

    private void showMonth() {
        b.fixedMonth.setText(month == null || month.isEmpty()
                ? "Chọn tháng bắt đầu nhận thuê" : "Nhận thuê từ ngày 01/" + month.substring(5) + "/" + month.substring(0, 4));
    }

    public void saveState(Bundle state) { state.putString("fixedMonth", month); }

    public void read(Room room) {
        ArrayList<Integer> selected = new ArrayList<>();
        for (int i = 0; i < days.length; i++) if (days[i].isChecked()) selected.add(i + 1);
        room.setWorkingDays(selected);
        room.setSlotMinutes(b.slot30.isChecked() ? 30 : 60);
        room.setMinBookingMinutes(Integer.parseInt(b.minMinutes.getText().toString().trim()));
        room.setFixedStartMonth(month);
        room.setRenewalPolicy(b.renewalPolicy.getText().toString().trim());
        room.setRentalPolicy(b.rentalPolicy.getText().toString().trim());
    }
}
