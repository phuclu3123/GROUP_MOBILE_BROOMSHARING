package vn.edu.ueh.phuclu.broomsharing.ui.guest;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.RoomListing;
import vn.edu.ueh.phuclu.broomsharing.repository.BookingRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;
import vn.edu.ueh.phuclu.broomsharing.R;
public class BookRoomActivity extends SessionActivity {
    public static final String EXTRA_ROOM_LISTING = "EXTRA_ROOM_LISTING";

    private RoomListing roomListing;
    private final Calendar startCalendar = Calendar.getInstance();
    private final Calendar endCalendar = Calendar.getInstance();

    private TextView tvSelectDate, tvSelectStartTime, tvSelectEndTime, tvSummaryPrice, tvFeedback;
    private Button btnConfirmBooking;
    private View progressBar;
    private final BookingRepository bookingRepository = new BookingRepository();

    @Override
    protected int pageLayout() {
        return R.layout.activity_book_room;
    }

    @Override
    protected void onProfileReady(vn.edu.ueh.phuclu.broomsharing.model.UserProfile profile) {
        roomListing = (RoomListing) getIntent().getSerializableExtra(EXTRA_ROOM_LISTING);
        if (roomListing == null) {
            Toast.makeText(this, "Không tìm thấy thông tin phòng.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvSelectDate = findViewById(R.id.tvSelectDate);
        tvSelectStartTime = findViewById(R.id.tvSelectStartTime);
        tvSelectEndTime = findViewById(R.id.tvSelectEndTime);
        tvSummaryPrice = findViewById(R.id.tvSummaryPrice);
        tvFeedback = findViewById(R.id.tvFeedback);
        btnConfirmBooking = findViewById(R.id.btnConfirmBooking);
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Mặc định chọn 2 tiếng từ hiện tại
        startCalendar.add(Calendar.HOUR_OF_DAY, 1);
        endCalendar.setTime(startCalendar.getTime());
        endCalendar.add(Calendar.HOUR_OF_DAY, 2);

        updateDateTimeLabels();
        calculatePrice();

        tvSelectDate.setOnClickListener(v -> showDatePicker());
        tvSelectStartTime.setOnClickListener(v -> showTimePicker(true));
        tvSelectEndTime.setOnClickListener(v -> showTimePicker(false));
        btnConfirmBooking.setOnClickListener(v -> submitBooking());
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            startCalendar.set(Calendar.YEAR, year);
            startCalendar.set(Calendar.MONTH, month);
            startCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            endCalendar.set(Calendar.YEAR, year);
            endCalendar.set(Calendar.MONTH, month);
            endCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            updateDateTimeLabels();
            calculatePrice();
        }, startCalendar.get(Calendar.YEAR), startCalendar.get(Calendar.MONTH), startCalendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void showTimePicker(boolean isStart) {
        Calendar cal = isStart ? startCalendar : endCalendar;
        TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
            cal.set(Calendar.MINUTE, minute);

            if (!isStart && endCalendar.before(startCalendar)) {
                endCalendar.setTime(startCalendar.getTime());
                endCalendar.add(Calendar.HOUR_OF_DAY, 1);
            }

            updateDateTimeLabels();
            calculatePrice();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true);
        dialog.show();
    }

    private void updateDateTimeLabels() {
        tvSelectDate.setText(String.format(Locale.getDefault(), "%02d/%02d/%d",
                startCalendar.get(Calendar.DAY_OF_MONTH), startCalendar.get(Calendar.MONTH) + 1, startCalendar.get(Calendar.YEAR)));
        tvSelectStartTime.setText(String.format(Locale.getDefault(), "%02d:%02d",
                startCalendar.get(Calendar.HOUR_OF_DAY), startCalendar.get(Calendar.MINUTE)));
        tvSelectEndTime.setText(String.format(Locale.getDefault(), "%02d:%02d",
                endCalendar.get(Calendar.HOUR_OF_DAY), endCalendar.get(Calendar.MINUTE)));
    }

    private void calculatePrice() {
        long durationMs = endCalendar.getTimeInMillis() - startCalendar.getTimeInMillis();
        double hours = Math.max(1.0, durationMs / (1000.0 * 60 * 60));
        double pricePerHour = roomListing.room.getPricePerHour();
        double totalRent = hours * pricePerHour;
        double deposit = roomListing.room.getDeposit();
        double grandTotal = totalRent + deposit;

        tvSummaryPrice.setText(String.format(Locale.getDefault(),
                "Thời gian: %.1f giờ\nTiền thuê: %s\nTiền cọc giữ chỗ: %s\nTổng thanh toán dự kiến: %s",
                hours, FormatUtils.formatCurrency(totalRent), FormatUtils.formatCurrency(deposit), FormatUtils.formatCurrency(grandTotal)));
    }

    private void submitBooking() {
        if (endCalendar.before(startCalendar) || endCalendar.equals(startCalendar)) {
            tvFeedback.setText("Giờ kết thúc phải sau giờ bắt đầu.");
            return;
        }

        tvFeedback.setText("");
        progressBar.setVisibility(View.VISIBLE);
        btnConfirmBooking.setEnabled(false);

        Booking booking = new Booking();
        booking.roomId = roomListing.room.getId();
        booking.ownerUid = roomListing.room.getOwnerUid();
        booking.activeMode = roomListing.room.getActiveMode();
        booking.startAt = startCalendar.getTime();
        booking.endAt = endCalendar.getTime();

        long durationMs = endCalendar.getTimeInMillis() - startCalendar.getTimeInMillis();
        double hours = Math.max(1.0, durationMs / (1000.0 * 60 * 60));
        booking.totalAmount = (hours * roomListing.room.getPricePerHour()) + roomListing.room.getDeposit();

        bookingRepository.createBooking(booking, new ResultCallback<String>() {
            @Override
            public void onSuccess(String bookingId) {
                progressBar.setVisibility(View.GONE);
                Intent intent = new Intent(BookRoomActivity.this, BookingSuccessActivity.class);
                intent.putExtra("EXTRA_BOOKING_ID", bookingId);
                intent.putExtra("EXTRA_ROOM_NAME", roomListing.room.getRoomName());
                intent.putExtra("EXTRA_START_TIME", startCalendar.getTimeInMillis());
                intent.putExtra("EXTRA_END_TIME", endCalendar.getTimeInMillis());
                intent.putExtra("EXTRA_AMOUNT", booking.totalAmount);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(Exception error) {
                progressBar.setVisibility(View.GONE);
                btnConfirmBooking.setEnabled(true);
                tvFeedback.setText(error.getMessage());
            }
        });
    }
}