package vn.edu.ueh.phuclu.broomsharing.ui.guest;

import android.content.Intent;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;
import vn.edu.ueh.phuclu.broomsharing.MainActivity;

public class BookingSuccessActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_success);

        String bookingId = getIntent().getStringExtra("EXTRA_BOOKING_ID");
        String roomName = getIntent().getStringExtra("EXTRA_ROOM_NAME");
        long startTime = getIntent().getLongExtra("EXTRA_START_TIME", System.currentTimeMillis());
        long endTime = getIntent().getLongExtra("EXTRA_END_TIME", System.currentTimeMillis() + 3600000);
        double amount = getIntent().getDoubleExtra("EXTRA_AMOUNT", 0.0);

        TextView tvCode = findViewById(R.id.tvBookingCode);
        TextView tvAmount = findViewById(R.id.tvTotalAmount);
        Button btnAddCalendar = findViewById(R.id.btnAddCalendar);
        Button btnHome = findViewById(R.id.btnBackHome);

        if (tvCode != null) tvCode.setText("Mã đặt chỗ: " + (bookingId != null ? bookingId : "N/A"));
        if (tvAmount != null) tvAmount.setText("Tổng tiền: " + FormatUtils.formatCurrency(amount));

        // Slide 02.3: Implicit Intent mở ứng dụng Lịch (Calendar Contract)
        if (btnAddCalendar != null) {
            btnAddCalendar.setOnClickListener(v -> {
                Intent calendarIntent = new Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                        .putExtra(CalendarContract.Events.TITLE, "Lịch thuê phòng: " + (roomName != null ? roomName : "Broom Sharing"))
                        .putExtra(CalendarContract.Events.DESCRIPTION, "Mã đặt chỗ: " + bookingId)
                        .putExtra(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE);
                startActivity(calendarIntent);
            });
        }

        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(BookingSuccessActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }
    }
}