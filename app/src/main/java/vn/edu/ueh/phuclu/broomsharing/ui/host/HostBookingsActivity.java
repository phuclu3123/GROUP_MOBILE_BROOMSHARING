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

public class HostBookingsActivity extends vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity {

    private ActivityHostBookingsBinding binding;
    private HostViewModel viewModel;
    private HostBookingAdapter adapter;

    private List<Booking> fullList = new ArrayList<>();
    private String currentFilter = "ALL"; // ALL, PENDING, CONFIRMED, REJECTED

    private Bundle restored;
    private java.time.LocalDate scheduleDay = java.time.LocalDate.now(vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.ZONE);
    private vn.edu.ueh.phuclu.broomsharing.model.Room scheduleRoom;
    private String roomId;
    private boolean bookingsLoaded;
    @Override protected int pageLayout() { return R.layout.activity_host_bookings; }
    @Override protected boolean requiresHost() { return true; }
    @Override protected void onCreate(Bundle state) { restored = state; super.onCreate(state); }
    @Override protected void onProfileReady(vn.edu.ueh.phuclu.broomsharing.model.UserProfile profile) {
        binding = ActivityHostBookingsBinding.bind(((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        roomId = getIntent().getStringExtra("ROOM_ID");
        vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation.applyInsets(this);
        vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation.attach(this,
                vn.edu.ueh.phuclu.broomsharing.model.UserRole.HOST, R.id.host_bookings, null);
        binding.btnBack.setVisibility(roomId == null ? View.GONE : View.VISIBLE);
        if (restored != null) {
            currentFilter = restored.getString("filter", "ALL");
            scheduleDay = java.time.LocalDate.parse(restored.getString("day", scheduleDay.toString()));
        }
        viewModel = new ViewModelProvider(this).get(HostViewModel.class);

        setupTopBar();
        setupRecyclerView();
        setupFilterChips();
        observeViewModel();

        binding.btnRefreshBookings.setOnClickListener(v -> refresh());
        binding.btnScheduleDay.setOnClickListener(v -> new android.app.DatePickerDialog(this, (picker,y,m,d) -> {
            scheduleDay = java.time.LocalDate.of(y,m+1,d); renderSchedule();
        },scheduleDay.getYear(),scheduleDay.getMonthValue()-1,scheduleDay.getDayOfMonth()).show());
        if (roomId != null) {
            binding.schedulePanel.setVisibility(View.VISIBLE);
            viewModel.hostRoom.observe(this, state -> {
                if (state == null) return;
                if (state.error != null) binding.tvSchedule.setText(state.error);
                else if (state.loading) binding.tvSchedule.setText(R.string.booking_loading);
                else if (state.data != null) { scheduleRoom = state.data; renderSchedule(); }
            });
        }
        TextView selected = "PENDING".equals(currentFilter) ? binding.chipPending : "CONFIRMED".equals(currentFilter) ? binding.chipConfirmed
            : "REJECTED".equals(currentFilter) ? binding.chipRejected : "CANCELLED".equals(currentFilter) ? binding.chipCancelled
            : "CHECKED_IN".equals(currentFilter) ? binding.chipCheckedIn : "COMPLETED".equals(currentFilter) ? binding.chipCompleted : binding.chipAll;
        setFilter(currentFilter, selected);
        refresh();
    }
    private void refresh() {
        viewModel.loadBookings();
        if (roomId != null) viewModel.loadHostRoom(roomId);
    }
    private void renderSchedule() {
        if (scheduleRoom != null && bookingsLoaded) binding.tvSchedule.setText(vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.daySummary(scheduleRoom, fullList, scheduleDay));
    }
    @Override protected void onResume() { super.onResume(); if (binding != null && viewModel != null) refresh(); }
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        boolean changedRoom = !java.util.Objects.equals(roomId, intent.getStringExtra("ROOM_ID"));
        setIntent(intent);
        if (changedRoom) recreate();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("filter",currentFilter); state.putString("day",scheduleDay.toString()); super.onSaveInstanceState(state);
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
            @Override public void onProgress(Booking booking, String status) {
                new AlertDialog.Builder(HostBookingsActivity.this).setTitle("Cập nhật lượt thuê")
                        .setMessage(vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils.bookingStatus(status)
                                + "\nThao tác này không xác nhận thu/hoàn tiền thật.")
                        .setNegativeButton(android.R.string.cancel,null)
                        .setPositiveButton(android.R.string.ok,(dialog,which)->viewModel.updateBookingStatus(booking.id,status,""))
                        .show();
            }
        });
        binding.rvBookings.setAdapter(adapter);
    }

    private void setupFilterChips() {
        binding.chipAll.setOnClickListener(v -> setFilter("ALL", binding.chipAll));
        binding.chipPending.setOnClickListener(v -> setFilter("PENDING", binding.chipPending));
        binding.chipConfirmed.setOnClickListener(v -> setFilter("CONFIRMED", binding.chipConfirmed));
        binding.chipCheckedIn.setOnClickListener(v -> setFilter("CHECKED_IN", binding.chipCheckedIn));
        binding.chipCompleted.setOnClickListener(v -> setFilter("COMPLETED", binding.chipCompleted));
        binding.chipCancelled.setOnClickListener(v -> setFilter("CANCELLED", binding.chipCancelled));
        binding.chipRejected.setOnClickListener(v -> setFilter("REJECTED", binding.chipRejected));
    }

    private void setFilter(String filter, TextView selectedChip) {
        currentFilter = filter;
        TextView[] chips = new TextView[]{
                binding.chipAll, binding.chipPending, binding.chipConfirmed, binding.chipCheckedIn, binding.chipCompleted, binding.chipRejected, binding.chipCancelled
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
            if (roomId != null && !roomId.equals(b.getRoomId())) continue;
            String status = b.getStatus() != null ? b.getStatus().toUpperCase() : Booking.STATUS_PENDING;
            if ("ALL".equals(currentFilter)) {
                filtered.add(b);
            } else if (currentFilter.equals(status)) {
                filtered.add(b);
            }
        }

        adapter.setData(filtered);
        binding.tvEmptyMessage.setText(R.string.workspace_empty_filter);
        binding.layoutEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void observeViewModel() {
        viewModel.bookings.observe(this, state -> {
            if (state == null) return;
            binding.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            binding.btnRefreshBookings.setEnabled(!state.loading);
            binding.btnRefreshBookings.setText(state.error == null ? R.string.workspace_refresh : R.string.workspace_retry);
            if (state.loading) { bookingsLoaded = false; binding.tvSchedule.setText(R.string.booking_loading); binding.layoutEmpty.setVisibility(View.GONE); }

            if (state.error != null) {
                fullList.clear(); adapter.setData(fullList); binding.layoutEmpty.setVisibility(View.VISIBLE); binding.tvEmptyMessage.setText(state.error); binding.tvSchedule.setText(state.error);
                Toast.makeText(this, "Lỗi tải booking: " + state.error, Toast.LENGTH_SHORT).show();
            } else if (state.data != null) {
                bookingsLoaded = true;
                fullList = new ArrayList<>();
                for (Booking item : state.data) if (roomId == null || roomId.equals(item.getRoomId())) fullList.add(item);
                renderSchedule();
                applyFilter();
            }
        });

        viewModel.actionResult.observe(this, state -> {
            if (state == null) return;
            adapter.setBusy(state.loading);
            binding.btnRefreshBookings.setEnabled(!state.loading);
            if (state.loading) {
                binding.progressBar.setVisibility(View.VISIBLE);
            } else {
                binding.progressBar.setVisibility(View.GONE);
                if (state.error != null) {
                    Toast.makeText(this, "Thao tác thất bại: " + state.error, Toast.LENGTH_LONG).show();
                } else if (state.data != null) {
                    Toast.makeText(this, "Cập nhật yêu cầu đặt phòng thành công!", Toast.LENGTH_SHORT).show();
                    viewModel.actionResult.setValue(null);
                    refresh();
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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.host_rejected).setView(etReason)
                .setPositiveButton(R.string.host_rejected, null)
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = etReason.getText().toString().trim();
            if (reason.isEmpty() || reason.length() > 500) { etReason.setError(getString(R.string.host_reason_required)); return; }
            viewModel.updateBookingStatus(booking.getId(), Booking.STATUS_REJECTED, reason); dialog.dismiss();
        }));
        dialog.show();
    }
}
