package vn.edu.ueh.phuclu.broomsharing;

import android.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import vn.edu.ueh.phuclu.broomsharing.adapter.BookingAdapter;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.BookingViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestNavigation;

import java.util.ArrayList;
import java.util.List;

public class MyBookingsActivity extends SessionActivity {

    private TextView feedback;
    private android.widget.ProgressBar progress;
    private Spinner filter;

    private BookingViewModel model;
    private BookingAdapter adapter;

    private List<Booking> allBookings = new ArrayList<>();
    private String[] filters;

    @Override
    protected int pageLayout() {
        return R.layout.activity_my_bookings;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        feedback = findViewById(R.id.tvBookingsFeedback);
        progress = findViewById(R.id.progressBookings);
        filter = findViewById(R.id.spinnerBookingFilter);

        RecyclerView recycler =
                findViewById(R.id.recyclerBookings);

        recycler.setLayoutManager(
                new LinearLayoutManager(this));

        adapter = new BookingAdapter(this::confirmCancel);
        recycler.setAdapter(adapter);

        model = new ViewModelProvider(this)
                .get(BookingViewModel.class);

        filters = getResources().getStringArray(
                R.array.booking_filter_options);

        filter.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        filters));

        filter.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
                        renderBookings();
                    }

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent) {
                    }
                });

        findViewById(R.id.btnRefreshBookings)
                .setOnClickListener(v -> model.loadBookings());

        model.bookings().observe(this, this::showBookingsState);

        model.cancellation().observe(this, state -> {
            if (state == null) {
                return;
            }

            progress.setVisibility(
                    state.loading ? View.VISIBLE : View.GONE);

            if (state.loading) {
                return;
            }

            if (state.error != null) {
                feedback.setText(state.error);
            } else {
                feedback.setText("Đã hủy yêu cầu đặt chỗ.");
                model.clearCancellation();
                model.loadBookings();
            }
        });

        GuestNavigation.attach(this, R.id.nav_bookings);

        model.startWatchingBookings();
        model.loadBookings();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (model != null) {
            model.startWatchingBookings();
        }
    }

    @Override
    protected void onStop() {
        if (model != null) {
            model.stopWatchingBookings();
        }

        super.onStop();
    }

    private void showBookingsState(
            UiState<List<Booking>> state) {

        if (state == null) {
            return;
        }

        progress.setVisibility(
                state.loading ? View.VISIBLE : View.GONE);

        if (state.error != null) {
            feedback.setText(state.error);
            return;
        }

        if (state.data != null) {
            allBookings = state.data;
            renderBookings();
        } else if (state.loading) {
            feedback.setText("Đang tải danh sách đặt chỗ...");
        }
    }

    private void renderBookings() {
        if (adapter == null || filters == null) {
            return;
        }

        String selected =
                filter == null || filter.getSelectedItem() == null
                        ? filters[0]
                        : filter.getSelectedItem().toString();

        List<Booking> visible = new ArrayList<>();

        for (Booking booking : allBookings) {
            if (matchesFilter(booking.status, selected)) {
                visible.add(booking);
            }
        }

        adapter.setItems(visible);

        if (visible.isEmpty()) {
            feedback.setText(
                    "Chưa có đặt chỗ ở trạng thái này.");
        } else {
            feedback.setText(
                    "Có " + visible.size() + " đặt chỗ.");
        }
    }

    private boolean matchesFilter(
            String status,
            String selected) {

        if (filters[0].equals(selected)) {
            return true;
        }

        if (filters[1].equals(selected)) {
            return "PENDING".equals(status);
        }

        if (filters[2].equals(selected)) {
            return "CONFIRMED".equals(status);
        }

        if (filters[3].equals(selected)) {
            return "CANCELLED".equals(status);
        }

        if (filters[4].equals(selected)) {
            return "COMPLETED".equals(status);
        }

        if (filters[5].equals(selected)) {
            return "REJECTED".equals(status);
        }

        return true;
    }

    private void confirmCancel(Booking booking) {
        new AlertDialog.Builder(this)
                .setTitle("Hủy yêu cầu đặt chỗ?")
                .setMessage(
                        "Chỉ yêu cầu đang chờ xác nhận mới có thể hủy. "
                                + "Nếu hủy thành công, khung giờ đã khóa "
                                + "sẽ được giải phóng.")
                .setNegativeButton(
                        "Giữ lại",
                        (dialog, which) -> dialog.dismiss())
                .setPositiveButton(
                        "Hủy đặt chỗ",
                        (dialog, which) ->
                                model.cancel(booking.id))
                .show();
    }
}