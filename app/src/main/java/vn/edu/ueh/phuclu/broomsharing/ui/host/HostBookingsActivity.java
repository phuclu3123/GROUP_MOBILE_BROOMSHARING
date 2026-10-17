package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.HostBookingAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityHostBookingsBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;

import java.util.ArrayList;
import java.util.List;

public class HostBookingsActivity extends AppCompatActivity {

    private ActivityHostBookingsBinding binding;
    private HostViewModel viewModel;
    private HostBookingAdapter adapter;

    private List<Booking> fullList = new ArrayList<>();
    private String currentFilter = "ALL"; // ALL, PENDING, CONFIRMED, REJECTED

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHostBookingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(HostViewModel.class);

        setupTopBar();
        setupRecyclerView();
        setupFilterChips();
        observeViewModel();

        viewModel.loadBookings();
    }

    private void setupTopBar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        binding.rvBookings.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HostBookingAdapter(new HostBookingAdapter.OnBookingActionListener() {
            @Override
            public void onAccept(Booking booking) {
                confirmAcceptDialog(booking);
            }

            @Override
            public void onReject(Booking booking) {
                showRejectDialog(booking);
            }
        });
        binding.rvBookings.setAdapter(adapter);
    }

    private void setupFilterChips() {
        binding.chipAll.setOnClickListener(v -> setFilter("ALL", binding.chipAll));
        binding.chipPending.setOnClickListener(v -> setFilter("PENDING", binding.chipPending));
        binding.chipConfirmed.setOnClickListener(v -> setFilter("CONFIRMED", binding.chipConfirmed));
        binding.chipRejected.setOnClickListener(v -> setFilter("REJECTED", binding.chipRejected));
    }

    private void setFilter(String filter, TextView selectedChip) {
        currentFilter = filter;
        TextView[] chips = new TextView[]{
                binding.chipAll, binding.chipPending, binding.chipConfirmed, binding.chipRejected
        };
        for (TextView chip : chips) {
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        }
        selectedChip.setBackgroundResource(R.drawable.bg_chip_selected);
        selectedChip.setTextColor(ContextCompat.getColor(this, R.color.white));

        applyFilter();
    }

    private void applyFilter() {
        List<Booking> filtered = new ArrayList<>();
        for (Booking b : fullList) {
            String status = b.getStatus() != null ? b.getStatus().toUpperCase() : Booking.STATUS_PENDING;
            if ("ALL".equals(currentFilter)) {
                filtered.add(b);
            } else if ("PENDING".equals(currentFilter) && Booking.STATUS_PENDING.equals(status)) {
                filtered.add(b);
            } else if ("CONFIRMED".equals(currentFilter) && Booking.STATUS_CONFIRMED.equals(status)) {
                filtered.add(b);
            } else if ("REJECTED".equals(currentFilter) && (Booking.STATUS_REJECTED.equals(status) || Booking.STATUS_CANCELLED.equals(status))) {
                filtered.add(b);
            }
        }

        adapter.setData(filtered);
        binding.layoutEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void observeViewModel() {
        viewModel.bookings.observe(this, state -> {
            if (state == null) return;
            binding.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);

            if (state.error != null) {
                Toast.makeText(this, "Lỗi tải booking: " + state.error, Toast.LENGTH_SHORT).show();
            } else if (state.data != null) {
                fullList = state.data;
                applyFilter();
            }
        });

        viewModel.actionResult.observe(this, state -> {
            if (state == null) return;
            if (state.loading) {
                binding.progressBar.setVisibility(View.VISIBLE);
            } else {
                binding.progressBar.setVisibility(View.GONE);
                if (state.error != null) {
                    Toast.makeText(this, "Thao tác thất bại: " + state.error, Toast.LENGTH_LONG).show();
                } else if (state.data != null) {
                    Toast.makeText(this, "Cập nhật yêu cầu đặt phòng thành công!", Toast.LENGTH_SHORT).show();
                    viewModel.actionResult.setValue(null);
                    viewModel.loadBookings();
                }
            }
        });
    }

    private void confirmAcceptDialog(Booking booking) {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận chấp nhận đặt phòng")
                .setMessage("Bạn có chắc chắn muốn duyệt yêu cầu đặt phòng này không?")
                .setPositiveButton("Chấp nhận", (dialog, which) -> {
                    viewModel.updateBookingStatus(booking.getId(), Booking.STATUS_CONFIRMED, null);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showRejectDialog(Booking booking) {
        final EditText etReason = new EditText(this);
        etReason.setHint("Nhập lý do từ chối (bắt buộc)...");
        etReason.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(this)
                .setTitle("Từ chối yêu cầu đặt phòng")
                .setMessage("Vui lòng cung cấp lý do cho khách hàng:")
                .setView(etReason)
                .setPositiveButton("Từ chối", (dialog, which) -> {
                    String reason = etReason.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, "Vui lòng nhập lý do từ chối", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.updateBookingStatus(booking.getId(), Booking.STATUS_REJECTED, reason);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}
