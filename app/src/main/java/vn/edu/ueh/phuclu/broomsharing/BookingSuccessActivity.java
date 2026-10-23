
package vn.edu.ueh.phuclu.broomsharing;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class BookingSuccessActivity extends AppCompatActivity {

    private String bookingId;
    private String roomName;
    private String address;
    private String mode;

    private long startMillis;
    private long endMillis;

    private double total;
    private double deposit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_success);

        bookingId = getIntent().getStringExtra("bookingId");
        roomName = getIntent().getStringExtra("roomName");
        address = getIntent().getStringExtra("roomAddress");
        mode = getIntent().getStringExtra("mode");

        startMillis = getIntent().getLongExtra("startAt", 0L);
        endMillis = getIntent().getLongExtra("endAt", 0L);

        total = getIntent().getDoubleExtra("totalAmount", 0.0);
        deposit = getIntent().getDoubleExtra("depositAmount", 0.0);

        findViewById(R.id.btnAddCalendar)
                .setOnClickListener(v -> addToCalendar());

        findViewById(R.id.btnGoBookings).setOnClickListener(v -> {
            Intent intent = new Intent(this, MyBookingsActivity.class);

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnBackExplore).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            startActivity(intent);
            finish();
        });

        ((TextView) findViewById(R.id.tvConfirmationTitle))
                .setText(getString(R.string.booking_success_title));

        ((TextView) findViewById(R.id.tvConfirmationCode))
                .setText(getString(
                        R.string.booking_code_label,
                        safe(bookingId)
                ));

        ((TextView) findViewById(R.id.tvConfirmationRoom))
                .setText(safe(roomName));

        ((TextView) findViewById(R.id.tvConfirmationAddress))
                .setText(safe(address));

        ((TextView) findViewById(R.id.tvConfirmationRange))
                .setText(
                        formatDateTime(startMillis)
                                + "\nđến "
                                + formatDateTime(endMillis)
                );

        ((TextView) findViewById(R.id.tvConfirmationMode))
                .setText(modeLabel(mode));

        ((TextView) findViewById(R.id.tvConfirmationTotal))
                .setText(FormatUtils.formatCurrency(total));

        ((TextView) findViewById(R.id.tvConfirmationDeposit))
                .setText(FormatUtils.formatCurrency(deposit));
    }

    private void addToCalendar() {
        if (startMillis <= 0L || endMillis <= startMillis) {
            Toast.makeText(
                    this,
                    "Thời gian đặt chỗ không hợp lệ.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setData(CalendarContract.Events.CONTENT_URI);

        intent.putExtra(
                CalendarContract.Events.TITLE,
                "Broom Sharing - " + safe(roomName)
        );

        intent.putExtra(
                CalendarContract.Events.EVENT_LOCATION,
                safe(address)
        );

        intent.putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Mã đặt chỗ: " + safe(bookingId)
                        + "\nHình thức: " + modeLabel(mode)
                        + "\nTiền thuê dự kiến: "
                        + FormatUtils.formatCurrency(total)
                        + "\nTiền cọc dự kiến: "
                        + FormatUtils.formatCurrency(deposit)
                        + "\nTrạng thái: Chờ thanh toán"
        );

        intent.putExtra(
                CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                startMillis
        );

        intent.putExtra(
                CalendarContract.EXTRA_EVENT_END_TIME,
                endMillis
        );

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            try {
                Intent webCalendar = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                                "https://calendar.google.com/calendar/u/0/r/eventedit"
                        )
                );

                startActivity(webCalendar);
            } catch (Exception ignored) {
                Toast.makeText(
                        this,
                        "Không tìm thấy ứng dụng Lịch tương thích.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private String safe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Chưa cập nhật";
        }

        return value;
    }

    private String modeLabel(String value) {
        if ("HOURLY".equals(value)) {
            return "Thuê theo giờ";
        }

        if ("QUARTERLY".equals(value)) {
            return "Thuê theo quý";
        }

        return "Thuê theo tháng";
    }

    private String formatDateTime(long millis) {
        if (millis <= 0L) {
            return "Chưa cập nhật";
        }

        SimpleDateFormat format = new SimpleDateFormat(
                "HH:mm · EEEE, dd/MM/yyyy",
                new Locale("vi", "VN")
        );

        format.setTimeZone(
                TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        );

        return format.format(new Date(millis));
    }
}
