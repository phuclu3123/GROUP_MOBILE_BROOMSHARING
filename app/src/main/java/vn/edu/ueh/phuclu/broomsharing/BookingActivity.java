package vn.edu.ueh.phuclu.broomsharing;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.auth.FirebaseAuth;

import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityBookingBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.RoomListing;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.BookingViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.RoomViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import android.widget.Toast;

public class BookingActivity extends SessionActivity {

    public static final String ROOM_ID = "ROOM_ID";

    private ActivityBookingBinding binding;
    private RoomViewModel roomModel;
    private BookingViewModel bookingModel;

    private Room room;
    private RoomListing listing;

    private Calendar selectedDate;

    private int startHour = 9;
    private int startMinute = 0;
    private int endHour = 10;
    private int endMinute = 0;

    private boolean submitting;
    private String bookingRequestKey;

    private String pendingBookingId;
    private String pendingMode;
    private long pendingStartAt;
    private long pendingEndAt;
    private double pendingTotal;
    private double pendingDeposit;

    private final TimeZone vietnam =
            TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

    @Override
    protected int pageLayout() {
        return R.layout.activity_booking;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        selectedDate = Calendar.getInstance(
                vietnam, Locale.getDefault());

        if (state == null) {
            bookingRequestKey = UUID.randomUUID().toString();
        } else {
            bookingRequestKey =
                    state.getString("booking.requestKey");

            selectedDate.setTimeInMillis(
                    state.getLong(
                            "booking.date",
                            selectedDate.getTimeInMillis()));

            startHour = state.getInt("booking.startHour", 9);
            startMinute = state.getInt("booking.startMinute", 0);
            endHour = state.getInt("booking.endHour", 10);
            endMinute = state.getInt("booking.endMinute", 0);

            pendingBookingId =
                    state.getString("booking.pendingId");

            pendingMode = state.getString("booking.pendingMode");

            pendingStartAt =
                    state.getLong("booking.pendingStart", 0L);

            pendingEndAt =
                    state.getLong("booking.pendingEnd", 0L);

            pendingTotal =
                    state.getDouble("booking.pendingTotal", 0.0);

            pendingDeposit =
                    state.getDouble("booking.pendingDeposit", 0.0);
        }

        if (bookingRequestKey == null
                || bookingRequestKey.trim().isEmpty()) {
            bookingRequestKey = UUID.randomUUID().toString();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putString("booking.requestKey", bookingRequestKey);
        out.putLong("booking.date", selectedDate.getTimeInMillis());

        out.putInt("booking.startHour", startHour);
        out.putInt("booking.startMinute", startMinute);
        out.putInt("booking.endHour", endHour);
        out.putInt("booking.endMinute", endMinute);

        out.putString("booking.pendingId", pendingBookingId);
        out.putString("booking.pendingMode", pendingMode);

        out.putLong("booking.pendingStart", pendingStartAt);
        out.putLong("booking.pendingEnd", pendingEndAt);

        out.putDouble("booking.pendingTotal", pendingTotal);
        out.putDouble("booking.pendingDeposit", pendingDeposit);

        super.onSaveInstanceState(out);
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        View root = ((android.view.ViewGroup)
                findViewById(android.R.id.content)).getChildAt(0);

        binding = ActivityBookingBinding.bind(root);

        roomModel = new ViewModelProvider(this)
                .get(RoomViewModel.class);

        bookingModel = new ViewModelProvider(this)
                .get(BookingViewModel.class);

        binding.toolbar.setNavigationOnClickListener(
                v -> finish());

        binding.dateButton.setOnClickListener(
                v -> chooseDate());

        binding.startTimeButton.setOnClickListener(
                v -> chooseTime(true));

        binding.endTimeButton.setOnClickListener(
                v -> chooseTime(false));

        binding.btnSubmitBooking.setOnClickListener(
                v -> submitBooking());

        updateDateAndTimes();

        roomModel.detail().observe(this, state -> {
            if (state == null) {
                return;
            }

            if (state.loading) {
                binding.progressBooking.setVisibility(View.VISIBLE);
                binding.btnSubmitBooking.setEnabled(false);
                return;
            }

            binding.progressBooking.setVisibility(View.GONE);

            if (state.error != null) {
                binding.tvBookingFeedback.setText(state.error);
                binding.btnSubmitBooking.setEnabled(false);
                return;
            }

            if (state.data == null) {
                return;
            }

            listing = state.data;
            room = listing.room;

            if (room == null) {
                binding.tvBookingFeedback.setText(
                        "Không tìm thấy thông tin phòng.");
                binding.btnSubmitBooking.setEnabled(false);
                return;
            }

            binding.tvRoomName.setText(room.getRoomName());
            binding.tvRoomAddress.setText(room.getAddress());

            if (Room.MODE_HOURLY.equals(room.getActiveMode())) {
                binding.tvMode.setText(
                        R.string.booking_mode_hourly);

                binding.timeFields.setVisibility(View.VISIBLE);
                binding.modeSpinner.setVisibility(View.GONE);

                binding.tvUnitPrice.setText(
                        FormatUtils.formatCurrency(
                                room.getPricePerHour()) + " / giờ");

            } else {
                binding.tvMode.setText(
                        R.string.booking_mode_fixed);

                binding.timeFields.setVisibility(View.GONE);
                binding.modeSpinner.setVisibility(View.VISIBLE);

                List<String> options = new ArrayList<>();

                if (room.getPricePerMonth() > 0) {
                    options.add(
                            getString(R.string.booking_mode_monthly));
                }

                if (room.getPricePerQuarter() > 0) {
                    options.add(
                            getString(R.string.booking_mode_quarterly));
                }

                if (options.isEmpty()) {
                    binding.tvBookingFeedback.setText(
                            "Phòng chưa có giá thuê tháng hoặc quý.");

                    binding.btnSubmitBooking.setEnabled(false);
                    return;
                }

                ArrayAdapter<String> adapter =
                        new ArrayAdapter<>(
                                this,
                                android.R.layout.simple_spinner_dropdown_item,
                                options);

                binding.modeSpinner.setAdapter(adapter);

                binding.modeSpinner.setOnItemSelectedListener(
                        new android.widget.AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(
                                    android.widget.AdapterView<?> parent,
                                    View view,
                                    int position,
                                    long id) {
                                updateEstimate();
                            }

                            @Override
                            public void onNothingSelected(
                                    android.widget.AdapterView<?> parent) {
                            }
                        });

                binding.tvUnitPrice.setText(
                        "Chọn tháng hoặc quý để xem giá thuê.");
            }

            updateDateAndTimes();
            updateEstimate();
            finishConfirmationIfReady();
        });

        bookingModel.submission().observe(this, state -> {
            if (state == null) {
                return;
            }

            submitting = state.loading;

            binding.progressBooking.setVisibility(
                    state.loading ? View.VISIBLE : View.GONE);

            binding.btnSubmitBooking.setEnabled(
                    !state.loading && room != null);

            if (state.loading) {
                binding.tvBookingFeedback.setText(
                        "Đang gửi yêu cầu đặt chỗ...");
                return;
            }

            if (state.error != null) {
                binding.tvBookingFeedback.setText(state.error);
                bookingModel.clearSubmission();
                return;
            }

            if (state.data != null) {
                pendingBookingId = state.data;
                bookingModel.clearSubmission();
                finishConfirmationIfReady();
            }
        });

        String roomId = getIntent().getStringExtra(ROOM_ID);

        if (roomId == null || roomId.trim().isEmpty()) {
            binding.tvBookingFeedback.setText(
                    "Không tìm thấy mã phòng.");

            binding.btnSubmitBooking.setEnabled(false);
        } else {
            roomModel.ensureDetail(roomId);
        }
    }

    private void chooseDate() {
        Calendar current =
                Calendar.getInstance(vietnam, Locale.getDefault());

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    selectedDate.set(year, month, day, 0, 0, 0);
                    selectedDate.set(Calendar.MILLISECOND, 0);

                    updateDateAndTimes();
                    updateEstimate();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH));

        dialog.getDatePicker().setMinDate(
                current.getTimeInMillis());

        dialog.show();
    }

    private void chooseTime(boolean start) {
        int hour = start ? startHour : endHour;
        int minute = start ? startMinute : endMinute;

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (picker, selectedHour, selectedMinute) -> {
                    if (start) {
                        startHour = selectedHour;
                        startMinute = selectedMinute;
                    } else {
                        endHour = selectedHour;
                        endMinute = selectedMinute;
                    }

                    updateDateAndTimes();
                    updateEstimate();
                },
                hour,
                minute,
                true);

        dialog.show();
    }

    private void updateDateAndTimes() {
        if (binding == null) {
            return;
        }

        SimpleDateFormat dateFormat =
                new SimpleDateFormat(
                        "EEEE, dd/MM/yyyy",
                        new Locale("vi", "VN"));

        binding.dateButton.setText(
                dateFormat.format(selectedDate.getTime()));

        binding.startTimeButton.setText(
                String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        startHour,
                        startMinute));

        binding.endTimeButton.setText(
                String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        endHour,
                        endMinute));
    }

    private String selectedFixedMode() {
        Object selected = binding.modeSpinner.getSelectedItem();

        if (selected == null) {
            return "MONTHLY";
        }

        if (getString(R.string.booking_mode_quarterly)
                .equals(selected.toString())) {
            return "QUARTERLY";
        }

        return "MONTHLY";
    }

    private Date[] selectedRange() {
        Calendar start = Calendar.getInstance(
                vietnam, Locale.getDefault());

        start.setTime(selectedDate.getTime());
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();

        if (room != null
                && Room.MODE_HOURLY.equals(room.getActiveMode())) {

            start.set(Calendar.HOUR_OF_DAY, startHour);
            start.set(Calendar.MINUTE, startMinute);

            end.set(Calendar.HOUR_OF_DAY, endHour);
            end.set(Calendar.MINUTE, endMinute);

        } else {
            start.set(Calendar.DAY_OF_MONTH, 1);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);

            end.setTime(start.getTime());

            if ("QUARTERLY".equals(selectedFixedMode())) {
                end.add(Calendar.MONTH, 3);
            } else {
                end.add(Calendar.MONTH, 1);
            }
        }

        return new Date[]{
                start.getTime(),
                end.getTime()
        };
    }

    private void updateEstimate() {
        if (binding == null || room == null) {
            return;
        }

        Date[] range = selectedRange();

        double rent;
        String unitLabel;

        if (Room.MODE_HOURLY.equals(room.getActiveMode())) {
            double hours =
                    (range[1].getTime() - range[0].getTime())
                            / 3600000.0;

            rent = hours * room.getPricePerHour();
            unitLabel = "giờ";

            binding.tvUnitPrice.setText(
                    FormatUtils.formatCurrency(
                            room.getPricePerHour()) + " / giờ");

            if (hours > 0) {
                binding.tvRentalAmount.setText(
                        FormatUtils.formatCurrency(rent)
                                + " ("
                                + String.format(
                                Locale.getDefault(),
                                "%.1f",
                                hours)
                                + " giờ)");
            } else {
                binding.tvRentalAmount.setText("—");
            }

        } else if ("QUARTERLY".equals(selectedFixedMode())) {
            rent = room.getPricePerQuarter();
            unitLabel = "quý";

            binding.tvUnitPrice.setText(
                    FormatUtils.formatCurrency(rent) + " / quý");

            binding.tvRentalAmount.setText(
                    FormatUtils.formatCurrency(rent));

        } else {
            rent = room.getPricePerMonth();
            unitLabel = "tháng";

            binding.tvUnitPrice.setText(
                    FormatUtils.formatCurrency(rent) + " / tháng");

            binding.tvRentalAmount.setText(
                    FormatUtils.formatCurrency(rent));
        }

        binding.tvDepositAmount.setText(
                FormatUtils.formatCurrency(room.getDeposit()));

        binding.tvTotalAmount.setText(
                FormatUtils.formatCurrency(
                        Math.max(0, rent)
                                + Math.max(0, room.getDeposit())));

        binding.tvPaymentNote.setText(
                "Số tiền dự kiến gồm tiền thuê và tiền cọc. "
                        + "Chưa có thanh toán thật; trạng thái cọc "
                        + "sẽ là chưa thanh toán.");
    }

    private void submitBooking() {
        if (submitting || room == null || listing == null) {
            return;
        }

        Date[] range = selectedRange();

        Date startAt = range[0];
        Date endAt = range[1];

        if (!startAt.before(endAt)) {
            binding.tvBookingFeedback.setText(
                    "Thời gian kết thúc phải sau thời gian bắt đầu.");
            return;
        }

        if (Room.MODE_HOURLY.equals(room.getActiveMode())) {
            Calendar now = Calendar.getInstance(
                    vietnam, Locale.getDefault());

            if (startAt.before(now.getTime())) {
                binding.tvBookingFeedback.setText(
                        "Không thể đặt khung giờ trong quá khứ.");
                return;
            }

            if (startMinute % 30 != 0 || endMinute % 30 != 0) {
                binding.tvBookingFeedback.setText(
                        "Khung giờ cần theo bước 30 phút.");
                return;
            }

            if (!isWithinOpeningHours(startAt, endAt)) {
                binding.tvBookingFeedback.setText(
                        "Khung giờ đã chọn nằm ngoài giờ mở cửa.");
                return;
            }

        } else {
            Calendar startCalendar = Calendar.getInstance(
                    vietnam, Locale.getDefault());

            startCalendar.setTime(startAt);

            if (startCalendar.get(Calendar.DAY_OF_MONTH) != 1) {
                binding.tvBookingFeedback.setText(
                        "Ngày bắt đầu thuê cố định phải là ngày 1.");
                return;
            }

            Calendar now = Calendar.getInstance(
                    vietnam, Locale.getDefault());

            if (startAt.before(now.getTime())) {
                binding.tvBookingFeedback.setText(
                        "Không thể thuê kỳ cố định đã bắt đầu.");
                return;
            }
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            binding.tvBookingFeedback.setText(
                    "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
            return;
        }

        String mode;
        String unitLabel;
        double rent;

        if (Room.MODE_HOURLY.equals(room.getActiveMode())) {
            mode = "HOURLY";
            unitLabel = "giờ";

            double hours =
                    (endAt.getTime() - startAt.getTime())
                            / 3600000.0;

            rent = hours * room.getPricePerHour();

        } else if ("QUARTERLY".equals(selectedFixedMode())) {
            mode = "QUARTERLY";
            unitLabel = "quý";
            rent = room.getPricePerQuarter();

        } else {
            mode = "MONTHLY";
            unitLabel = "tháng";
            rent = room.getPricePerMonth();
        }

        if (rent <= 0) {
            binding.tvBookingFeedback.setText(
                    "Giá thuê của hình thức này chưa được cập nhật.");
            return;
        }

        pendingMode = mode;
        pendingStartAt = startAt.getTime();
        pendingEndAt = endAt.getTime();
        pendingTotal = rent + room.getDeposit();
        pendingDeposit = room.getDeposit();

        submitting = true;

        binding.btnSubmitBooking.setEnabled(false);
        binding.progressBooking.setVisibility(View.VISIBLE);
        binding.tvBookingFeedback.setText(
                "Đang gửi yêu cầu đặt chỗ...");

        bookingModel.create(
                bookingRequestKey,
                room.getId(),
                mode,
                startAt,
                endAt,
                unitLabel);
    }

    private boolean isWithinOpeningHours(
            Date startAt,
            Date endAt) {

        try {
            String open = room.getOpenTime();
            String close = room.getCloseTime();

            if (open == null || close == null
                    || open.length() < 5 || close.length() < 5) {
                return true;
            }

            int openMinutes =
                    Integer.parseInt(open.substring(0, 2)) * 60
                            + Integer.parseInt(open.substring(3, 5));

            int closeMinutes =
                    Integer.parseInt(close.substring(0, 2)) * 60
                            + Integer.parseInt(close.substring(3, 5));

            Calendar start = Calendar.getInstance(
                    vietnam, Locale.getDefault());

            start.setTime(startAt);

            Calendar end = Calendar.getInstance(
                    vietnam, Locale.getDefault());

            end.setTime(endAt);

            int startMinutes =
                    start.get(Calendar.HOUR_OF_DAY) * 60
                            + start.get(Calendar.MINUTE);

            int endMinutes =
                    end.get(Calendar.HOUR_OF_DAY) * 60
                            + end.get(Calendar.MINUTE);

            return startMinutes >= openMinutes
                    && endMinutes <= closeMinutes;

        } catch (Exception e) {
            return true;
        }
    }

    private void finishConfirmationIfReady() {
        if (pendingBookingId == null
                || room == null
                || pendingStartAt <= 0
                || pendingEndAt <= 0) {
            return;
        }

        Intent intent = new Intent(
                this, BookingSuccessActivity.class);

        intent.putExtra("bookingId", pendingBookingId);
        intent.putExtra("roomName", room.getRoomName());
        intent.putExtra("roomAddress", room.getAddress());
        intent.putExtra("startAt", pendingStartAt);
        intent.putExtra("endAt", pendingEndAt);
        intent.putExtra("mode", pendingMode);
        intent.putExtra("totalAmount", pendingTotal);
        intent.putExtra("depositAmount", pendingDeposit);

        pendingBookingId = null;

        startActivity(intent);
        finish();
    }
    // 1. Kiểm tra ngày bắt đầu Thuê Cố Định (bắt buộc ngày 01 của tháng lịch)
private boolean validateFixedBooking(Calendar startDate) {
    if (startDate.get(Calendar.DAY_OF_MONTH) != 1) {
        Toast.makeText(this, "Thuê cố định (Tháng/Quý) bắt buộc bắt đầu từ ngày 01 của tháng", Toast.LENGTH_LONG).show();
        return false;
    }
    return true;
}

// 2. Kiểm tra giờ thuê HOURLY (phải nằm trong khung openTime - closeTime của phòng)
private boolean validateHourlyBooking(String selectedStartTime, String selectedEndTime, String openTime, String closeTime) {
    try {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Date start = sdf.parse(selectedStartTime);
        Date end = sdf.parse(selectedEndTime);
        Date open = sdf.parse(openTime);
        Date close = sdf.parse(closeTime);

        if (start == null || end == null || open == null || close == null) {
            Toast.makeText(this, "Thời gian chọn không hợp lệ", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (start.before(open) || end.after(close)) {
            Toast.makeText(this, "Giờ đặt nằm ngoài khung giờ hoạt động (" + openTime + " - " + closeTime + ")", Toast.LENGTH_LONG).show();
            return false;
        }

        if (!start.before(end)) {
            Toast.makeText(this, "Giờ kết thúc phải sau giờ bắt đầu", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    } catch (Exception e) {
        e.printStackTrace();
        return false;
    }
}
}