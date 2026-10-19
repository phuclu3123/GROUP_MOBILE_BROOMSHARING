package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class BookingSuccessActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_success);

        String bookingId =
                getIntent().getStringExtra("bookingId");

        String roomName =
                getIntent().getStringExtra("roomName");

        String address =
                getIntent().getStringExtra("roomAddress");

        String mode =
                getIntent().getStringExtra("mode");

        long startMillis =
                getIntent().getLongExtra("startAt", 0L);

        long endMillis =
                getIntent().getLongExtra("endAt", 0L);

        double total =
                getIntent().getDoubleExtra("totalAmount", 0.0);

        double deposit =
                getIntent().getDoubleExtra("depositAmount", 0.0);

        findViewById(R.id.btnGoBookings).setOnClickListener(v -> {
            Intent intent =
                    new Intent(this, MyBookingsActivity.class);

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnBackExplore).setOnClickListener(v -> {
            Intent intent =
                    new Intent(this, MainActivity.class);

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            startActivity(intent);
            finish();
        });

        ((TextView) findViewById(R.id.tvConfirmationTitle))
                .setText(getString(R.string.booking_success_title));

        ((TextView) findViewById(R.id.tvConfirmationCode))
                .setText(getString(
                        R.string.booking_code_label,
                        safe(bookingId)));

        ((TextView) findViewById(R.id.tvConfirmationRoom))
                .setText(safe(roomName));

        ((TextView) findViewById(R.id.tvConfirmationAddress))
                .setText(safe(address));

        ((TextView) findViewById(R.id.tvConfirmationRange))
                .setText(
                        formatDateTime(startMillis)
                                + "\nđến "
                                + formatDateTime(endMillis));

        ((TextView) findViewById(R.id.tvConfirmationMode))
                .setText(modeLabel(mode));

        ((TextView) findViewById(R.id.tvConfirmationTotal))
                .setText(FormatUtils.formatCurrency(total));

        ((TextView) findViewById(R.id.tvConfirmationDeposit))
                .setText(FormatUtils.formatCurrency(deposit));
    }

    private String safe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Chưa cập nhật";
        }

        return value;
    }

    private String modeLabel(String mode) {
        if ("HOURLY".equals(mode)) {
            return "Thuê theo giờ";
        }

        if ("QUARTERLY".equals(mode)) {
            return "Thuê theo quý";
        }

        return "Thuê theo tháng";
    }

    private String formatDateTime(long millis) {
        if (millis <= 0) {
            return "Chưa cập nhật";
        }

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "HH:mm · EEEE, dd/MM/yyyy",
                        new Locale("vi", "VN"));

        format.setTimeZone(
                TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        return format.format(new Date(millis));
    }
}