package vn.edu.ueh.phuclu.broomsharing;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityBookingBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.BookingViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.time.*;
import java.util.*;

/** Booking form observing the Java BookingViewModel and Repository. */
public class BookingActivity extends SessionActivity {
    public static final String ROOM_ID = "ROOM_ID";
    private ActivityBookingBinding b;
    private BookingViewModel model;
    private Room room;
    private LocalDate day;
    private LocalTime start = LocalTime.of(9, 0), end = LocalTime.of(10, 0);
    private Bundle restored;
    private boolean quoted;
    private final List<Integer> periods = new ArrayList<>();

    @Override
    protected int pageLayout() {
        return R.layout.activity_booking;
    }

    @Override
    protected void onCreate(Bundle state) {
        restored = state;
        super.onCreate(state);
    }

    private String roomId() {
        return getIntent().getStringExtra(ROOM_ID);
    }

    private boolean hourly() {
        return room != null && Room.MODE_HOURLY.equals(room.getActiveMode());
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        b =
                ActivityBookingBinding.bind(
                        ((android.view.ViewGroup) findViewById(android.R.id.content))
                                .getChildAt(0));
        model = new ViewModelProvider(this).get(BookingViewModel.class);
        if (restored != null) {
            if (restored.getString("day") != null) day = LocalDate.parse(restored.getString("day"));
            start = LocalTime.parse(restored.getString("start", "09:00"));
            end = LocalTime.parse(restored.getString("end", "10:00"));
            model.requestKey = restored.getString("requestKey", model.requestKey);
            model.lastRequest = restored.getString("signature", model.lastRequest);
        }
        b.toolbar.setNavigationOnClickListener(
                v -> {
                    if (!model.busy()) finish();
                });
        b.dateButton.setOnClickListener(v -> chooseDate());
        b.startTimeButton.setOnClickListener(v -> chooseTime(true));
        b.endTimeButton.setOnClickListener(v -> chooseTime(false));
        b.btnQuote.setOnClickListener(
                v -> {
                    if (room == null) {
                        model.loadRoom(roomId());
                        return;
                    }
                    try {
                        model.quote(request());
                    } catch (Exception e) {
                        b.tvBookingFeedback.setText(e.getMessage());
                    }
                });
        b.btnSubmitBooking.setOnClickListener(
                v -> {
                    if (!quoted) return;
                    try {
                        model.create(request());
                    } catch (Exception e) {
                        b.tvBookingFeedback.setText(e.getMessage());
                    }
                });
        model.room.observe(
                this,
                state -> {
                    if (state == null) return;
                    b.tvBookingFeedback.setText(
                            state.loading
                                    ? "Đang tải phòng…"
                                    : state.error == null ? "" : state.error);
                    if (state.data != null) {
                        room = state.data;
                        b.tvRoomName.setText(room.getRoomName());
                        b.tvRoomAddress.setText(room.getAddress());
                        b.tvMode.setText(
                                hourly()
                                        ? R.string.booking_mode_hourly
                                        : R.string.booking_mode_fixed);
                        b.timeFields.setVisibility(hourly() ? View.VISIBLE : View.GONE);
                        b.modeSpinner.setVisibility(hourly() ? View.GONE : View.VISIBLE);
                        b.tvUnitPrice.setText(
                                hourly()
                                        ? "Giờ mở: "
                                                + room.getOpenTime()
                                                + "–"
                                                + room.getCloseTime()
                                                + " • Bước "
                                                + room.getSlotMinutes()
                                                + " phút • Tối thiểu "
                                                + room.getMinBookingMinutes()
                                                + " phút"
                                        : "Thuê từ "
                                                + room.getFixedStartMonth()
                                                + " • Bắt đầu ngày 1\nGia hạn: "
                                                + room.getRenewalPolicy());
                        periods.clear();
                        List<String> labels = new ArrayList<>();
                        if (room.getPricePerMonth() > 0) {
                            periods.add(1);
                            labels.add(getString(R.string.booking_mode_monthly));
                        }
                        if (room.getPricePerQuarter() > 0) {
                            periods.add(3);
                            labels.add(getString(R.string.booking_mode_quarterly));
                        }
                        b.modeSpinner.setAdapter(
                                new ArrayAdapter<>(
                                        this,
                                        android.R.layout.simple_spinner_dropdown_item,
                                        labels));
                        if (restored != null
                                && restored.getInt("months", 1) == 3
                                && periods.contains(3))
                            b.modeSpinner.setSelection(periods.indexOf(3));
                        b.modeSpinner.setOnItemSelectedListener(
                                new AdapterView.OnItemSelectedListener() {
                                    public void onNothingSelected(AdapterView<?> parent) {}

                                    public void onItemSelected(
                                            AdapterView<?> parent,
                                            View view,
                                            int position,
                                            long id) {
                                        changed();
                                    }
                                });
                        selection();
                        loadAvailability();
                    }
                    enabled();
                });
        model.availability.observe(
                this,
                state -> {
                    if (state == null) return;
                    b.tvAvailability.setText(
                            state.loading
                                    ? "Đang tải lịch…"
                                    : state.error == null
                                            ? ""
                                            : state.error
                                                    + "\n"
                                                    + "Chưa xác định lịch trống. Chọn lại ngày để"
                                                    + " thử lại.");
                    if (state.data != null && room != null && day != null) {
                        String text =
                                HostSchedule.daySummary(room, state.data, day)
                                        .replace("Đã xác nhận", "Đã giữ chỗ");
                        if (!hourly()) {
                            LocalDate endDay = day.plusMonths(months());
                            boolean held = false;
                            Date from = Date.from(day.atStartOfDay(HostSchedule.ZONE).toInstant()),
                                    to =
                                            Date.from(
                                                    endDay.atStartOfDay(HostSchedule.ZONE)
                                                            .toInstant());
                            for (Booking item : state.data)
                                if (HostSchedule.overlaps(from, to, item.startAt, item.endAt))
                                    held = true;
                            for (RoomBlackout range : room.getBlackoutRanges())
                                if (HostSchedule.overlaps(
                                        from, to, range.getStartAt(), range.getEndAt()))
                                    held = true;
                            for (String value : room.getBlackoutDates()) {
                                LocalDate blocked = HostSchedule.blackoutDate(value);
                                if (!blocked.isBefore(day) && blocked.isBefore(endDay)) held = true;
                            }
                            text =
                                    day
                                            + " – "
                                            + endDay
                                            + ": "
                                            + (held
                                                    ? "Có khoảng đã giữ chỗ/khóa lịch"
                                                    : "Chưa có khoảng giữ chỗ trong kỳ này");
                        }
                        b.tvAvailability.setText(
                                text
                                        + "\n"
                                        + "Lịch có thể thay đổi; service kiểm tra lại khi gửi yêu"
                                        + " cầu.");
                    }
                });
        model.quote.observe(
                this,
                state -> {
                    if (state == null) return;
                    quoted = state.data != null;
                    b.tvBookingFeedback.setText(
                            state.loading
                                    ? "Đang kiểm tra lịch và giá…"
                                    : state.error == null ? "" : state.error);
                    b.tvRentalAmount.setText("—");
                    b.tvDepositAmount.setText("—");
                    b.tvTotalAmount.setText("—");
                    b.tvPaymentNote.setText(
                            "Kiểm tra lịch và giá để xem dự kiến. Chưa giữ chỗ, chưa thanh toán.");
                    if (state.data != null) {
                        Map<String, Object> p = state.data.priceSnapshot;
                        b.tvRentalAmount.setText(amount(p, "rentalAmount"));
                        b.tvDepositAmount.setText(amount(p, "depositAmount"));
                        b.tvTotalAmount.setText(amount(p, "paymentDueAmount"));
                        b.tvPaymentNote.setText(
                                "Phí áp dụng: "
                                        + amount(p, "feeAmount")
                                        + "\nThuê + phí: "
                                        + amount(p, "totalAmount")
                                        + "\n"
                                        + "Tổng dự kiến gồm thuê, phí và cọc riêng. Cọc demo chưa"
                                        + " thanh toán.\n"
                                        + p.get("rentalPolicy"));
                    }
                    enabled();
                });
        model.saved.observe(
                this,
                state -> {
                    if (state == null) return;
                    b.tvBookingFeedback.setText(
                            state.loading
                                    ? "Đang gửi yêu cầu…"
                                    : state.error == null ? "" : state.error);
                    enabled();
                    if (state.data != null) {
                        String id = state.data;
                        model.saved.setValue(null);
                        startActivity(
                                new Intent(this, BookingSuccessActivity.class)
                                        .putExtra("bookingId", id));
                        finish();
                    }
                });
        selection();
        if (model.room.getValue() == null) model.loadRoom(roomId());
    }

    private String amount(Map<String, Object> p, String key) {
        Object n = p.get(key);
        return n instanceof Number
                ? FormatUtils.formatCurrency(((Number) n).doubleValue())
                : "Chưa có";
    }

    private int months() {
        int index = b.modeSpinner.getSelectedItemPosition();
        return index >= 0 && index < periods.size() ? periods.get(index) : 1;
    }

    private void changed() {
        quoted = false;
        model.quote.setValue(null);
        selection();
        loadAvailability();
    }

    private void loadAvailability() {
        if (room == null || day == null) return;
        model.loadAvailability(
                roomId(),
                day.atStartOfDay(HostSchedule.ZONE).toInstant().toEpochMilli(),
                (hourly() ? day.plusDays(1) : day.plusMonths(months()))
                        .atStartOfDay(HostSchedule.ZONE)
                        .toInstant()
                        .toEpochMilli());
    }

    private void enabled() {
        boolean loading = model.room.getValue() != null && model.room.getValue().loading;
        UiForms.enabled(b.getRoot(), !model.busy() && !loading);
        b.btnSubmitBooking.setEnabled(!model.busy() && !loading && quoted);
        b.progressBooking.setVisibility(model.busy() || loading ? View.VISIBLE : View.GONE);
    }

    private void selection() {
        b.dateButton.setText(
                day == null ? getString(R.string.booking_choose_date) : day.toString());
        b.startTimeButton.setText(start.toString());
        b.endTimeButton.setText(end.toString());
    }

    private void chooseDate() {
        LocalDate current = day == null ? LocalDate.now(HostSchedule.ZONE) : day;
        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (p, y, m, d) -> {
                            day = LocalDate.of(y, m + 1, d);
                            changed();
                        },
                        current.getYear(),
                        current.getMonthValue() - 1,
                        current.getDayOfMonth());
        dialog.getDatePicker()
                .setMinDate(
                        LocalDate.now(HostSchedule.ZONE)
                                .atStartOfDay(HostSchedule.ZONE)
                                .toInstant()
                                .toEpochMilli());
        dialog.show();
    }

    private void chooseTime(boolean from) {
        LocalTime selected = from ? start : end;
        new TimePickerDialog(
                        this,
                        (p, h, m) -> {
                            if (from) start = LocalTime.of(h, m);
                            else end = LocalTime.of(h, m);
                            changed();
                        },
                        selected.getHour(),
                        selected.getMinute(),
                        true)
                .show();
    }

    private Booking request() {
        if (room == null || day == null)
            throw new IllegalStateException("Chọn ngày và chờ tải phòng.");
        if (!hourly() && day.getDayOfMonth() != 1)
            throw new IllegalStateException("Thuê tháng/quý bắt đầu ngày 1; hãy chọn đúng ngày.");
        long from =
                day.atTime(hourly() ? start : LocalTime.MIDNIGHT)
                        .atZone(HostSchedule.ZONE)
                        .toInstant()
                        .toEpochMilli();
        long to =
                hourly() ? day.atTime(end).atZone(HostSchedule.ZONE).toInstant().toEpochMilli() : 0;
        if (from <= System.currentTimeMillis())
            throw new IllegalStateException("Thời gian bắt đầu phải ở tương lai.");
        if (hourly() && !start.isBefore(end))
            throw new IllegalStateException("Giờ kết thúc phải sau giờ bắt đầu.");
        return model.request(roomId(), from, to, hourly() ? 0 : months());
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (b != null) {
            if (day != null) state.putString("day", day.toString());
            state.putString("start", start.toString());
            state.putString("end", end.toString());
            state.putInt("months", months());
            state.putString("requestKey", model.requestKey);
            state.putString("signature", model.lastRequest);
        } else if (restored != null) state.putAll(restored);
        super.onSaveInstanceState(state);
    }
}
