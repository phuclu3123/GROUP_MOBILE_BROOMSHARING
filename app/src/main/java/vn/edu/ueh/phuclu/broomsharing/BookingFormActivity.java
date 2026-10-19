package vn.edu.ueh.phuclu.broomsharing;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityBookingFormBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.BookingViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.*;
import java.time.*;
import java.util.*;

public class BookingFormActivity extends SessionActivity {
    private ActivityBookingFormBinding b;
    private BookingViewModel model;
    private Room room;
    private LocalDate day;
    private LocalTime start = LocalTime.of(8, 0), end = LocalTime.of(9, 0);
    private Bundle restored;
    private boolean quoted;

    @Override protected int pageLayout() { return R.layout.activity_booking_form; }
    @Override protected void onCreate(Bundle state) { restored = state; super.onCreate(state); }

    @Override protected void onProfileReady(UserProfile profile) {
        b = ActivityBookingFormBinding.bind(((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        model = new ViewModelProvider(this).get(BookingViewModel.class);
        if (restored != null) {
            String selected = restored.getString("day");
            if (selected != null) day = LocalDate.parse(selected);
            start = LocalTime.parse(restored.getString("start", "08:00"));
            end = LocalTime.parse(restored.getString("end", "09:00"));
            model.requestKey = restored.getString("requestKey", model.requestKey);
            b.period.check(restored.getBoolean("quarter") ? R.id.quarter : R.id.month);
        }
        b.back.setOnClickListener(v -> { if (!model.busy()) finish(); });
        b.date.setOnClickListener(v -> {
            LocalDate current = day == null ? LocalDate.now(HostSchedule.ZONE) : day;
            DatePickerDialog picker = new DatePickerDialog(this, (p, y, m, d) -> {
                day = LocalDate.of(y, m + 1, d); changed(); renderSelection();
            }, current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth());
            picker.getDatePicker().setMinDate(LocalDate.now(HostSchedule.ZONE).atStartOfDay(HostSchedule.ZONE).toInstant().toEpochMilli());
            picker.show();
        });
        b.start.setOnClickListener(v -> pickTime(true)); b.end.setOnClickListener(v -> pickTime(false));
        b.period.setOnCheckedChangeListener((g, id) -> changed());
        b.quote.setOnClickListener(v -> {
            if (room == null) model.loadRoom(getIntent().getStringExtra("ROOM_ID"));
            else try { model.quote(request()); } catch (Exception e) { b.feedback.setText(e.getMessage()); }
        });
        b.submit.setOnClickListener(v -> {
            if (!quoted) return;
            try { model.create(request()); } catch (Exception e) { b.feedback.setText(e.getMessage()); }
        });
        model.room.observe(this, state -> {
            if (state == null) return;
            b.feedback.setText(state.loading ? "Đang tải phòng…" : state.error == null ? "" : state.error);
            if (state.data != null) {
                room = state.data;
                b.roomInfo.setText(room.getRoomName() + "\n" + room.getAddress() + "\n" + room.getOpenTime() + "–"
                        + room.getCloseTime() + " • Ngày hoạt động: " + room.getWorkingDays()
                        + "\n" + (room.getRentalPolicy() == null ? "" : room.getRentalPolicy())
                        + (Room.MODE_FIXED.equals(room.getActiveMode()) ? "\nNhận thuê từ " + room.getFixedStartMonth()
                        + "\nGia hạn: " + room.getRenewalPolicy() : ""));
                boolean hourly = Room.MODE_HOURLY.equals(room.getActiveMode());
                b.hourControls.setVisibility(hourly ? View.VISIBLE : View.GONE);
                b.period.setVisibility(hourly ? View.GONE : View.VISIBLE);
                b.month.setEnabled(room.getPricePerMonth() > 0); b.quarter.setEnabled(room.getPricePerQuarter() > 0);
                if (!hourly && room.getPricePerMonth() <= 0) b.period.check(R.id.quarter);
                if (!hourly && room.getPricePerQuarter() <= 0) b.period.check(R.id.month);
            }
        });
        model.quote.observe(this, state -> {
            if (state == null) return;
            b.progress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            b.feedback.setText(state.loading ? "Đang kiểm tra lịch và giá…" : state.error == null ? "" : state.error);
            quoted = state.data != null;
            b.summary.setText("");
            if (state.data != null) {
                Map<String, Object> p = state.data.priceSnapshot;
                b.summary.setText(TimeUtils.formatVietnam(state.data.startAt) + " – " + TimeUtils.formatVietnam(state.data.endAt)
                        + "\nTiền thuê dự kiến: " + amount(p, "rentalAmount") + "\nPhí áp dụng: " + amount(p, "feeAmount")
                        + "\nCọc riêng: " + amount(p, "depositAmount") + "\nThuê + phí: " + amount(p, "totalAmount")
                        + "\nThuê/phí và cọc: " + amount(p, "paymentDueAmount")
                        + "\nChưa giữ chỗ cho đến khi gửi thành công. Cọc demo chưa thanh toán."
                        + "\nChính sách: " + p.get("rentalPolicy"));
            }
            enabled();
        });
        model.saved.observe(this, state -> {
            if (state == null) return;
            b.progress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            b.feedback.setText(state.loading ? "Đang gửi yêu cầu…" : state.error == null ? "" : state.error);
            enabled();
            if (state.data != null) {
                model.saved.setValue(null);
                startActivity(new Intent(this, MyBookingsActivity.class)); finish();
            }
        });
        renderSelection();
        if (model.room.getValue() == null) model.loadRoom(getIntent().getStringExtra("ROOM_ID"));
    }

    private String amount(Map<String, Object> p, String key) {
        Object n = p.get(key); return n instanceof Number ? FormatUtils.formatCurrency(((Number) n).doubleValue()) : "Chưa có";
    }
    private void changed() { quoted = false; b.submit.setEnabled(false); b.summary.setText(""); model.quote.setValue(null); }
    private void enabled() {
        UiForms.enabled(b.getRoot(), !model.busy());
        b.submit.setEnabled(!model.busy() && quoted);
        if (room != null) { b.month.setEnabled(room.getPricePerMonth() > 0); b.quarter.setEnabled(room.getPricePerQuarter() > 0); }
    }
    private void renderSelection() {
        if (day != null) b.date.setText(day.toString());
        b.start.setText("Bắt đầu: " + start); b.end.setText("Kết thúc: " + end);
    }
    private void pickTime(boolean from) {
        LocalTime selected = from ? start : end;
        new TimePickerDialog(this, (p, h, m) -> {
            if (from) start = LocalTime.of(h, m); else end = LocalTime.of(h, m);
            changed(); renderSelection();
        }, selected.getHour(), selected.getMinute(), true).show();
    }
    private Map<String, Object> request() {
        if (day == null || room == null) throw new IllegalStateException("Chọn ngày và chờ tải thông tin phòng.");
        boolean hourly = Room.MODE_HOURLY.equals(room.getActiveMode());
        if (!hourly && day.getDayOfMonth() != 1) throw new IllegalStateException("Thuê tháng/quý cần chọn ngày 1.");
        return model.request(getIntent().getStringExtra("ROOM_ID"),
                day.atTime(hourly ? start : LocalTime.MIDNIGHT).atZone(HostSchedule.ZONE).toInstant().toEpochMilli(),
                hourly ? day.atTime(end).atZone(HostSchedule.ZONE).toInstant().toEpochMilli() : 0,
                hourly ? 0 : b.quarter.isChecked() ? 3 : 1);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (b != null) {
            if (day != null) state.putString("day", day.toString());
            state.putString("start", start.toString()); state.putString("end", end.toString());
            state.putBoolean("quarter", b.quarter.isChecked()); state.putString("requestKey", model.requestKey);
        }
        super.onSaveInstanceState(state);
    }
}
