package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.HostBookingAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityHostBookingsBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ChatActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;
import vn.edu.ueh.phuclu.broomsharing.ui.room.SpaceDetailActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;
import vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
   Màn hình Host theo dõi booking, lịch và các thao tác sử dụng phòng.
   Booking INSTANT_5M do backend xác nhận khi nhận tiền; quyền xử lý lịch sử không cấp quyền duyệt lượt mới.
*/
public class HostBookingsActivity extends SessionActivity {

    private ActivityHostBookingsBinding binding;
    private HostViewModel viewModel;
    private HostBookingAdapter adapter;

    private List<Booking> fullList = new ArrayList<>();
    private String currentFilter = "ALL"; // ALL, PENDING, CONFIRMED, REJECTED

    private Bundle restored;
    private LocalDate scheduleDay = LocalDate.now(HostSchedule.ZONE);
    private Room scheduleRoom;
    private String roomId;
    private boolean bookingsLoaded;
    private final List<Room> calendarRooms = new ArrayList<>();
    private HostMonthCalendarDialog calendarDialog;

    @Override
    protected int pageLayout() {
        return R.layout.activity_host_bookings;
    }

    @Override
    protected boolean requiresHost() {
        return true;
    }

    @Override
    protected void onCreate(Bundle state) {
        restored = state;
        super.onCreate(state);
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        binding =
                ActivityHostBookingsBinding.bind(
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        roomId = getIntent().getStringExtra("ROOM_ID");
        ManagementNavigation.applyInsets(this);
        ManagementNavigation.attach(this, UserRole.HOST, R.id.host_bookings, null);
        binding.btnBack.setVisibility(roomId == null ? View.GONE : View.VISIBLE);
        if (restored != null) {
            currentFilter = restored.getString("filter", "ALL");
            scheduleDay = LocalDate.parse(restored.getString("day", scheduleDay.toString()));
        }
        viewModel = new ViewModelProvider(this).get(HostViewModel.class);

        setupTopBar();
        setupRecyclerView();
        setupFilterChips();
        observeViewModel();
        binding.btnMonthCalendar.setEnabled(false);
        viewModel.rooms.observe(
                this,
                state -> {
                    calendarRooms.clear();
                    if (state.data != null) calendarRooms.addAll(state.data);
                    binding.btnMonthCalendar.setEnabled(bookingsLoaded && !calendarRooms.isEmpty());
                });
        binding.btnMonthCalendar.setOnClickListener(
                v ->
                        calendarDialog =
                                HostMonthCalendarDialog.show(
                                        this, calendarRooms, fullList, roomId));

        binding.btnRefreshBookings.setOnClickListener(v -> refresh());
        binding.btnScheduleDay.setOnClickListener(
                v ->
                        new DatePickerDialog(
                                        this,
                                        (picker, y, m, d) -> {
                                            scheduleDay = LocalDate.of(y, m + 1, d);
                                            renderSchedule();
                                        },
                                        scheduleDay.getYear(),
                                        scheduleDay.getMonthValue() - 1,
                                        scheduleDay.getDayOfMonth())
                                .show());
        if (roomId != null) {
            binding.schedulePanel.setVisibility(View.VISIBLE);
            viewModel.hostRoom.observe(
                    this,
                    state -> {
                        if (state == null) return;
                        if (state.error != null) binding.tvSchedule.setText(state.error);
                        else if (state.loading)
                            binding.tvSchedule.setText(R.string.booking_loading);
                        else if (state.data != null) {
                            scheduleRoom = state.data;
                            renderSchedule();
                        }
                    });
        }
        TextView selected =
                "PENDING".equals(currentFilter)
                        ? binding.chipPending
                        : "CONFIRMED".equals(currentFilter)
                                ? binding.chipConfirmed
                                : "REJECTED".equals(currentFilter)
                                        ? binding.chipRejected
                                        : "CANCELLED".equals(currentFilter)
                                                ? binding.chipCancelled
                                                : "CHECKED_IN".equals(currentFilter)
                                                        ? binding.chipCheckedIn
                                                        : "COMPLETED".equals(currentFilter)
                                                                ? binding.chipCompleted
                                                                : binding.chipAll;
        if ("NO_SHOW".equals(currentFilter)) selected = binding.chipNoShow;
        if ("TERMINATED".equals(currentFilter)) selected = binding.chipTerminated;
        setFilter(currentFilter, selected);
        refresh();
    }

    private void refresh() {
        viewModel.loadBookings();
        viewModel.loadRooms();
        if (roomId != null) viewModel.loadHostRoom(roomId);
    }

    private void renderSchedule() {
        if (scheduleRoom != null && bookingsLoaded)
            binding.tvSchedule.setText(
                    HostSchedule.daySummary(scheduleRoom, fullList, scheduleDay));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null && viewModel != null) refresh();
    }

    @Override
    protected void onStop() {
        if (calendarDialog != null) calendarDialog.dismiss();
        if (viewModel != null) viewModel.stopBookings();
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        boolean changedRoom = !Objects.equals(roomId, intent.getStringExtra("ROOM_ID"));
        setIntent(intent);
        if (changedRoom) recreate();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("filter", currentFilter);
        state.putString("day", scheduleDay.toString());
        super.onSaveInstanceState(state);
    }

    private void setupTopBar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        binding.rvBookings.setLayoutManager(new LinearLayoutManager(this));
        adapter =
                new HostBookingAdapter(
                        new HostBookingAdapter.OnBookingActionListener() {
                            @Override
                            public void onCancel(Booking booking) {
                                showReasonDialog(booking, "CANCELLED");
                            }

                            @Override
                            public void onChat(Booking booking) {
                                startActivity(
                                        new Intent(HostBookingsActivity.this, ChatActivity.class)
                                                .putExtra("bookingId", booking.id)
                                                .putExtra(
                                                        SpaceDetailActivity.ROOM_ID, booking.roomId)
                                                .putExtra("roomName", booking.roomName));
                            }

                            @Override
                            public void onAccept(Booking booking) {
                                confirmAcceptDialog(booking);
                            }

                            @Override
                            public void onReject(Booking booking) {
                                showRejectDialog(booking);
                            }

                            @Override
                            public void onProgress(Booking booking, String status) {
                                // Chưa check-in không đồng nghĩa khách vắng; Host phải xác nhận
                                // thực tế.
                                if ("CONFIRMED".equals(booking.status)
                                        && "COMPLETED".equals(status)) {
                                    new AlertDialog.Builder(HostBookingsActivity.this)
                                            .setTitle("Xác nhận tình trạng lượt thuê")
                                            .setItems(
                                                    new String[] {
                                                        "Khách đã sử dụng, bổ sung xác nhận",
                                                        "Khách không đến"
                                                    },
                                                    (dialog, which) ->
                                                            showReasonDialog(
                                                                    booking,
                                                                    which == 0
                                                                            ? "COMPLETED"
                                                                            : "NO_SHOW"))
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show();
                                    return;
                                }
                                new AlertDialog.Builder(HostBookingsActivity.this)
                                        .setTitle("Cập nhật lượt thuê")
                                        .setMessage(
                                                FormatUtils.bookingStatus(status)
                                                        + "\n"
                                                        + "Thao tác này không xác nhận thu/hoàn"
                                                        + " tiền thật.")
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .setPositiveButton(
                                                android.R.string.ok,
                                                (dialog, which) ->
                                                        viewModel.updateBookingStatus(
                                                                booking.id, status, ""))
                                        .show();
                            }
                        });
        binding.rvBookings.setAdapter(adapter);
    }

    private void setupFilterChips() {
        binding.chipAll.setOnClickListener(v -> setFilter("ALL", binding.chipAll));
        binding.chipPending.setOnClickListener(v -> setFilter("PENDING", binding.chipPending));
        binding.chipConfirmed.setOnClickListener(
                v -> setFilter("CONFIRMED", binding.chipConfirmed));
        binding.chipCheckedIn.setOnClickListener(
                v -> setFilter("CHECKED_IN", binding.chipCheckedIn));
        binding.chipCompleted.setOnClickListener(
                v -> setFilter("COMPLETED", binding.chipCompleted));
        binding.chipCancelled.setOnClickListener(
                v -> setFilter("CANCELLED", binding.chipCancelled));
        binding.chipRejected.setOnClickListener(v -> setFilter("REJECTED", binding.chipRejected));
        binding.chipNoShow.setOnClickListener(v -> setFilter("NO_SHOW", binding.chipNoShow));
        binding.chipTerminated.setOnClickListener(
                v -> setFilter("TERMINATED", binding.chipTerminated));
    }

    private void setFilter(String filter, TextView selectedChip) {
        currentFilter = filter;
        TextView[] chips =
                new TextView[] {
                    binding.chipAll,
                    binding.chipPending,
                    binding.chipConfirmed,
                    binding.chipCheckedIn,
                    binding.chipCompleted,
                    binding.chipRejected,
                    binding.chipCancelled,
                    binding.chipNoShow,
                    binding.chipTerminated
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
            String status =
                    b.getStatus() != null ? b.getStatus().toUpperCase(java.util.Locale.ROOT) : Booking.STATUS_PENDING;
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
        viewModel.bookings.observe(
                this,
                state -> {
                    if (state == null) return;
                    if (state.loading || state.error != null) {
                        bookingsLoaded = false;
                        binding.btnMonthCalendar.setEnabled(false);
                        if (calendarDialog != null) calendarDialog.dismiss();
                    }
                    binding.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
                    binding.btnRefreshBookings.setEnabled(!state.loading);
                    binding.btnRefreshBookings.setText(
                            state.error == null
                                    ? R.string.workspace_refresh
                                    : R.string.workspace_retry);
                    if (state.loading) {
                        bookingsLoaded = false;
                        binding.tvSchedule.setText(R.string.booking_loading);
                        binding.layoutEmpty.setVisibility(View.GONE);
                    }

                    if (state.error != null) {
                        fullList.clear();
                        adapter.setData(fullList);
                        binding.layoutEmpty.setVisibility(View.VISIBLE);
                        binding.tvEmptyMessage.setText(state.error);
                        binding.tvSchedule.setText(state.error);
                        Toast.makeText(this, "Lỗi tải booking: " + state.error, Toast.LENGTH_SHORT)
                                .show();
                    } else if (state.data != null) {
                        bookingsLoaded = true;
                        fullList = new ArrayList<>();
                        for (Booking item : state.data)
                            if (roomId == null || roomId.equals(item.getRoomId()))
                                fullList.add(item);
                        renderSchedule();
                        applyFilter();
                        binding.btnMonthCalendar.setEnabled(!calendarRooms.isEmpty());
                        if (calendarDialog != null) calendarDialog.update(fullList);
                    }
                });

        viewModel.actionResult.observe(
                this,
                state -> {
                    if (state == null) return;
                    adapter.setBusy(state.loading);
                    binding.btnRefreshBookings.setEnabled(!state.loading);
                    if (state.loading) {
                        binding.progressBar.setVisibility(View.VISIBLE);
                    } else {
                        binding.progressBar.setVisibility(View.GONE);
                        if (state.error != null) {
                            Toast.makeText(
                                            this,
                                            "Thao tác thất bại: " + state.error,
                                            Toast.LENGTH_LONG)
                                    .show();
                        } else if (state.data != null) {
                            Toast.makeText(
                                            this,
                                            "Cập nhật yêu cầu đặt phòng thành công!",
                                            Toast.LENGTH_SHORT)
                                    .show();
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
                .setPositiveButton(
                        "Chấp nhận",
                        (dialog, which) -> {
                            viewModel.updateBookingStatus(
                                    booking.getId(), Booking.STATUS_CONFIRMED, null);
                        })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showRejectDialog(Booking booking) {
        showReasonDialog(booking, "REJECTED");
    }

    private void showReasonDialog(Booking booking, String target) {
        final EditText etReason = new EditText(this);
        etReason.setHint(
                "COMPLETED".equals(target)
                        ? "Ghi rõ căn cứ xác nhận khách đã sử dụng dù chưa ghi nhận check-in."
                        : "NO_SHOW".equals(target)
                                ? "Ghi rõ việc liên hệ và xác nhận khách không đến; khách vẫn có"
                                      + " thể gửi phản ánh."
                                : "Nhập lý do (bắt buộc). Thao tác không xác nhận thu/hoàn tiền.");
        etReason.setPadding(40, 30, 40, 30);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "COMPLETED".equals(target)
                                        ? "Xác nhận khách đã sử dụng"
                                        : "NO_SHOW".equals(target)
                                                ? "Xác nhận khách không đến"
                                                : "CANCELLED".equals(target)
                                                        ? "Hủy đặt chỗ của khách"
                                                        : "Từ chối yêu cầu")
                        .setView(etReason)
                        .setPositiveButton("Xác nhận", null)
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
        dialog.setOnShowListener(
                d ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            String reason = etReason.getText().toString().trim();
                                            if (reason.isEmpty() || reason.length() > 500) {
                                                etReason.setError(
                                                        getString(R.string.host_reason_required));
                                                return;
                                            }
                                            viewModel.updateBookingStatus(
                                                    booking.getId(), target, reason);
                                            dialog.dismiss();
                                        }));
        dialog.show();
    }
}
