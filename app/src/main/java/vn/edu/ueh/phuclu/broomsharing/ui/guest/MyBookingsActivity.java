package vn.edu.ueh.phuclu.broomsharing.ui.guest;

import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.BookingAdapter;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.repository.BookingRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestNavigation;

public class MyBookingsActivity extends SessionActivity {

    private final BookingRepository repository = new BookingRepository();
    private final List<Booking> bookingList = new ArrayList<>();
    private BookingAdapter adapter;

    private RecyclerView rvBookings;
    private ProgressBar progressBar;
    private TextView tvEmptyState;

    @Override
    protected int pageLayout() {
        return R.layout.activity_my_bookings_real;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        rvBookings = findViewById(R.id.rvBookings);
        progressBar = findViewById(R.id.progressBar);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        rvBookings.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BookingAdapter(bookingList, this::confirmCancelBooking);
        rvBookings.setAdapter(adapter);

        GuestNavigation.attach(this, R.id.nav_bookings);
        loadBookings();
    }

    private void loadBookings() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);

        repository.getUserBookings(new ResultCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                progressBar.setVisibility(View.GONE);
                bookingList.clear();
                bookingList.addAll(result);
                adapter.notifyDataSetChanged();

                if (bookingList.isEmpty()) {
                    tvEmptyState.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Exception error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MyBookingsActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmCancelBooking(Booking booking) {
        new AlertDialog.Builder(this)
                .setTitle("Hủy đặt phòng")
                .setMessage("Bạn có chắc chắn muốn hủy đơn đặt phòng " + booking.id + " không?")
                .setPositiveButton("Hủy đơn", (dialog, which) -> {
                    progressBar.setVisibility(View.VISIBLE);
                    repository.cancelBooking(booking.id, new ResultCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(MyBookingsActivity.this, "Đã hủy đặt phòng.", Toast.LENGTH_SHORT).show();
                            loadBookings();
                        }

                        @Override
                        public void onError(Exception error) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(MyBookingsActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Bỏ qua", null)
                .show();
    }
}