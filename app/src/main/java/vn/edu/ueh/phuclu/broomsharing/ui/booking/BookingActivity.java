package vn.edu.ueh.phuclu.broomsharing.ui.booking;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityBookingBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.RoomBlackout;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;
import vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule;
import vn.edu.ueh.phuclu.broomsharing.utils.UiForms;

import java.time.*;
import java.util.*;

/**
 * Màn hình đặt chỗ: chọn ngày, giờ và thông tin thuê phòng.
 * Chuyển yêu cầu sang BookingViewModel và hiển thị kết quả kiểm tra.
 */
public class BookingActivity extends SessionActivity {
    // Tên key để nhận mã phòng từ Intent của màn hình trước.
    public static final String ROOM_ID = "ROOM_ID";

    // Binding giúp truy cập các View trong activity_booking.xml bằng b.tenView.
    private ActivityBookingBinding b;

    // ViewModel nhận yêu cầu và giữ các trạng thái mà giao diện cần hiển thị.
    private BookingViewModel model;

    // Thông tin phòng được tải từ ViewModel.
    private Room room;

    // Ngày và giờ khách đang chọn.
    private LocalDate day;
    private LocalTime start = LocalTime.of(9, 0), end = LocalTime.of(10, 0);

    // Dữ liệu lưu lại khi Android dựng lại màn hình.
    private Bundle restored;

    // true nghĩa là lựa chọn hiện tại đã có báo giá hợp lệ.
    private boolean quoted;

    // Số tháng tương ứng với từng mục trong Spinner: 1=tháng, 3=quý.
    private final List<Integer> periods = new ArrayList<>();

    // Trả về layout mà SessionActivity sẽ nạp cho màn đặt chỗ.
    @Override
    protected int pageLayout() {
        return R.layout.activity_booking;
    }

    @Override
    protected void onCreate(Bundle state) {
        // Lưu state trước super vì lớp cha có thể gọi onProfileReady() ngay trong lúc khởi tạo.
        restored = state;
        super.onCreate(state);
    }

    // Đọc mã phòng mà màn hình trước gửi qua Intent.
    private String roomId() {
        return getIntent().getStringExtra(ROOM_ID);
    }

    // Kiểm tra phòng hiện tại có cho thuê theo giờ không.
    private boolean hourly() {
        return room != null && Room.MODE_HOURLY.equals(room.getActiveMode());
    }

    // Được gọi sau khi SessionActivity xác nhận người dùng đã có phiên đăng nhập.
    @Override
    protected void onProfileReady(UserProfile profile) {
        // Gắn Binding vào layout đã được lớp cha nạp; sau đó có thể dùng b.dateButton, b.tvRoomName...
        b = ActivityBookingBinding.bind(
                ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));

        // Lấy ViewModel gắn với Activity này.
        model = new ViewModelProvider(this).get(BookingViewModel.class);

        // Khôi phục ngày, giờ và thông tin nhận diện yêu cầu sau khi Activity được dựng lại.
        if (restored != null) {
            if (restored.getString("day") != null) day = LocalDate.parse(restored.getString("day"));
            start = LocalTime.parse(restored.getString("start", "09:00"));
            end = LocalTime.parse(restored.getString("end", "10:00"));

            // Giữ cùng một mã yêu cầu để ViewModel nhận ra lần gửi lại của cùng yêu cầu.
            model.requestKey = restored.getString("requestKey", model.requestKey);
            model.lastRequest = restored.getString("signature", model.lastRequest);
        }

        // Nút mũi tên đóng màn hình; không đóng khi ViewModel đang xử lý.
        b.toolbar.setNavigationOnClickListener(
                v -> {
                    if (!model.busy()) finish();
                });

        // Các nút này chỉ mở hộp thoại chọn ngày/giờ.
        b.dateButton.setOnClickListener(v -> chooseDate());
        b.startTimeButton.setOnClickListener(v -> chooseTime(true));
        b.endTimeButton.setOnClickListener(v -> chooseTime(false));

        // Kiểm tra lịch và lấy báo giá dự kiến; thao tác này chưa tạo booking.
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

        // Chỉ gửi yêu cầu tạo booking sau khi đã có báo giá cho lựa chọn hiện tại.
        b.btnSubmitBooking.setOnClickListener(
                v -> {
                    if (!quoted) return;
                    try {
                        model.create(request());
                    } catch (Exception e) {
                        b.tvBookingFeedback.setText(e.getMessage());
                    }
                });

        // Quan sát trạng thái tải phòng: đang tải, lỗi, hoặc đã có dữ liệu.
        model.room.observe(this, state -> {
                    if (state == null) return;
                    b.tvBookingFeedback.setText(
                            state.loading
                                    ? "Đang tải phòng…"
                                    : state.error == null ? "" : state.error);
                    if (state.data != null) {
                        room = state.data;

                        // Hiển thị thông tin phòng và bật đúng nhóm trường theo hình thức thuê.
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

                        // Chỉ cho chọn những kỳ mà phòng có khai báo giá.
                        if (room.getPricePerMonth() > 0) {
                            periods.add(1);
                            labels.add(getString(R.string.booking_mode_monthly));
                        }
                        if (room.getPricePerQuarter() > 0) {
                            periods.add(3);
                            labels.add(getString(R.string.booking_mode_quarterly));
                        }

                        // ArrayAdapter chuyển danh sách nhãn thành các dòng trong Spinner.
                        b.modeSpinner.setAdapter(
                                new ArrayAdapter<>(
                                        this,
                                        android.R.layout.simple_spinner_dropdown_item,
                                        labels));
                        // Nếu trước đó đang chọn quý, đặt Spinner về lại mục quý sau khi dựng lại.
                        if (restored != null
                                && restored.getInt("months", 1) == 3
                                && periods.contains(3))
                            b.modeSpinner.setSelection(periods.indexOf(3));
                        // Khi đổi tháng/quý, cần tính lại lịch và bỏ báo giá cũ.
                        b.modeSpinner.setOnItemSelectedListener(
                                new AdapterView.OnItemSelectedListener() {
                                    public void onNothingSelected(AdapterView<?> parent) {}

                                    public void onItemSelected(
                                            AdapterView<?> parent,
                                            View view,
                                            int position,
                                            long id) {
                                        // position là vị trí mục vừa chọn trong Spinner.
                                        changed();
                                    }
                                });
                        selection();
                        loadAvailability();
                    }
                    enabled();
                });
        // Quan sát lịch để hiển thị khoảng đã giữ chỗ hoặc bị chủ phòng khóa.
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
                            // Thuê tháng/quý: kiểm tra lịch cho toàn bộ kỳ đã chọn.
                            LocalDate endDay = day.plusMonths(months());
                            boolean held = false;
                            Date from = Date.from(day.atStartOfDay(HostSchedule.ZONE).toInstant()),
                                    to =
                                            Date.from(
                                                    endDay.atStartOfDay(HostSchedule.ZONE)
                                                            .toInstant());
                            // Nếu booking hiện có giao với kỳ đang xét thì kỳ đó đã bận.
                            for (Booking item : state.data)
                                if (HostSchedule.overlaps(from, to, item.startAt, item.endAt))
                                    held = true;
                            // Kiểm tra các khoảng thời gian chủ phòng chủ động khóa.
                            for (RoomBlackout range : room.getBlackoutRanges())
                                if (HostSchedule.overlaps(
                                        from, to, range.getStartAt(), range.getEndAt()))
                                    held = true;
                            // Kiểm tra thêm các ngày lẻ bị khóa.
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

        // Quan sát báo giá để hiển thị tiền và điều khoản của lựa chọn hiện tại.
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
                        // Snapshot là Map gồm tên khoản tiền và giá trị tương ứng.
                        Map<String, Object> p = state.data.priceSnapshot;
                        b.tvRentalAmount.setText(amount(p, "rentalAmount"));
                        b.tvDepositAmount.setText(amount(p, "depositAmount"));
                        b.tvTotalAmount.setText(amount(p, "totalAmount"));
                        b.tvPaymentNote.setText(
                                "Phí áp dụng: "
                                        + amount(p, "feeAmount")
                                        + "\nThuê + phí: "
                                        + amount(p, "totalAmount")
                                        + "\n"
                                        + "Trả trước dự kiến 40%, được trừ vào tiền thuê. Chức năng thanh toán đang phát triển; chưa thu tiền.\n"
                                        + CancellationPolicy.terms(state.data.activeMode)
                                        + "\n"
                                        + p.get("rentalPolicy"));
                    }
                    enabled();
                });

        // Quan sát kết quả gửi booking; khi lưu thành công sẽ mở màn chi tiết.
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
                        // Xóa kết quả thành công vừa xử lý để không điều hướng lặp lại.
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
        // Lấy một khoản tiền trong snapshot và định dạng; nếu thiếu thì hiển thị "Chưa có".
        Object n = p.get(key);
        return n instanceof Number
                ? FormatUtils.formatCurrency(((Number) n).doubleValue())
                : "Chưa có";
    }

    private int months() {
        // Đổi vị trí được chọn thành số tháng tương ứng trong periods.
        int index = b.modeSpinner.getSelectedItemPosition();
        return index >= 0 && index < periods.size() ? periods.get(index) : 1;
    }

    private void changed() {
        // Ngày, giờ hoặc kỳ thuê đổi thì báo giá trước đó không còn hợp lệ.
        quoted = false;
        model.quote.setValue(null);
        selection();
        loadAvailability();
    }

    private void loadAvailability() {
        if (room == null || day == null) return;
        // Tải một ngày cho thuê giờ; tải trọn kỳ một/tháng hoặc ba/tháng cho thuê cố định.
        model.loadAvailability(
                roomId(),
                day.atStartOfDay(HostSchedule.ZONE).toInstant().toEpochMilli(),
                (hourly() ? day.plusDays(1) : day.plusMonths(months()))
                        .atStartOfDay(HostSchedule.ZONE)
                        .toInstant()
                        .toEpochMilli());
    }

    private void enabled() {
        // Khóa form khi đang tải/xử lý; nút gửi cần thêm báo giá hợp lệ.
        boolean loading = model.room.getValue() != null && model.room.getValue().loading;
        UiForms.enabled(b.getRoot(), !model.busy() && !loading);
        b.btnSubmitBooking.setEnabled(!model.busy() && !loading && quoted);
        b.progressBooking.setVisibility(model.busy() || loading ? View.VISIBLE : View.GONE);
    }

    private void selection() {
        // Đồng bộ ngày và giờ trong biến với chữ đang hiển thị trên các nút.
        b.dateButton.setText(
                day == null ? getString(R.string.booking_choose_date) : day.toString());
        b.startTimeButton.setText(start.toString());
        b.endTimeButton.setText(end.toString());
    }

    private void chooseDate() {
        // Mở lịch ở ngày đang chọn (hoặc hôm nay) và không cho chọn ngày quá khứ.
        LocalDate current = day == null ? LocalDate.now(HostSchedule.ZONE) : day;
        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (p, y, m, d) -> {
                            // Tháng DatePicker bắt đầu từ 0 nên cộng 1 để tạo LocalDate.
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
        // from=true chọn giờ bắt đầu; from=false chọn giờ kết thúc.
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
        // Kiểm tra lựa chọn rồi chuyển ngày giờ thành dữ liệu yêu cầu Booking.
        if (room == null || day == null)
            throw new IllegalStateException("Chọn ngày và chờ tải phòng.");
        if (!hourly() && day.getDayOfMonth() != 1)
            throw new IllegalStateException("Thuê tháng/quý bắt đầu ngày 1; hãy chọn đúng ngày.");
        long from =
                // Đổi thời điểm bắt đầu sang timestamp theo múi giờ dùng trong lịch phòng.
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

        // Thuê giờ gửi cả from/to; thuê cố định gửi số tháng để nghiệp vụ tính ngày kết thúc.
        return model.request(roomId(), from, to, hourly() ? 0 : months());
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (b != null) {
            // Lưu lựa chọn để phục hồi nếu Android hủy rồi tạo lại Activity.
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
