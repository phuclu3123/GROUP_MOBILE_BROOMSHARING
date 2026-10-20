package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import vn.edu.ueh.phuclu.broomsharing.databinding.DialogHostMonthCalendarBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.Room;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private YearMonth calendarYearMonth = YearMonth.now(vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.ZONE);
    private LocalDate selectedCalendarDate = LocalDate.now(vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.ZONE);
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
        View.OnClickListener openCalendarListener = v -> {
            if (scheduleRoom == null && roomId == null) {
                promptSelectRoomForSchedule();
            } else {
                showMonthCalendarDialog();
            }
        };
        binding.btnOpenMonthCalendar.setOnClickListener(openCalendarListener);
        binding.schedulePanel.setOnClickListener(openCalendarListener);

        if (roomId != null) {
            viewModel.hostRoom.observe(this, state -> {
                if (state == null) return;
                if (state.data != null) { scheduleRoom = state.data; renderSchedule(); }
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
        if (binding == null) return;
        if (scheduleRoom != null) {
            binding.tvScheduleRoomTitle.setText("Lịch phòng: " + scheduleRoom.getRoomName());
            binding.tvScheduleCurrentDate.setText("Phòng: " + scheduleRoom.getRoomName() + " (Bấm để xem lịch hoặc đổi phòng)");
        } else {
            binding.tvScheduleRoomTitle.setText("Cuốn lịch đặt phòng (Theo tháng)");
            binding.tvScheduleCurrentDate.setText("Bấm để chọn phòng & mở cuốn lịch");
        }
    }
    private void promptSelectRoomForSchedule() {
        viewModel.rooms.observe(this, new androidx.lifecycle.Observer<vn.edu.ueh.phuclu.broomsharing.model.UiState<List<Room>>>() {
            @Override
            public void onChanged(vn.edu.ueh.phuclu.broomsharing.model.UiState<List<Room>> state) {
                if (state != null && state.data != null && !state.data.isEmpty()) {
                    List<Room> hostRooms = state.data;
                    String[] names = new String[hostRooms.size()];
                    for (int i = 0; i < hostRooms.size(); i++) names[i] = hostRooms.get(i).getRoomName();
                    new AlertDialog.Builder(HostBookingsActivity.this)
                            .setTitle("Chọn phòng để xem cuốn lịch")
                            .setItems(names, (dialog, which) -> {
                                scheduleRoom = hostRooms.get(which);
                                roomId = scheduleRoom.getId();
                                binding.btnBack.setVisibility(View.VISIBLE);
                                renderSchedule();
                                applyFilter();
                                showMonthCalendarDialog();
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                    viewModel.rooms.removeObserver(this);
                } else if (state != null && state.data != null && state.data.isEmpty()) {
                    Toast.makeText(HostBookingsActivity.this, "Bạn chưa đăng phòng nào.", Toast.LENGTH_SHORT).show();
                    viewModel.rooms.removeObserver(this);
                }
            }
        });
        viewModel.loadDashboard();
    }

    private void promptChangeRoomInCalendar(DialogHostMonthCalendarBinding cal) {
        viewModel.rooms.observe(this, new androidx.lifecycle.Observer<vn.edu.ueh.phuclu.broomsharing.model.UiState<List<Room>>>() {
            @Override
            public void onChanged(vn.edu.ueh.phuclu.broomsharing.model.UiState<List<Room>> state) {
                if (state != null && state.data != null && !state.data.isEmpty()) {
                    List<Room> hostRooms = state.data;
                    String[] names = new String[hostRooms.size()];
                    for (int i = 0; i < hostRooms.size(); i++) names[i] = hostRooms.get(i).getRoomName();
                    new AlertDialog.Builder(HostBookingsActivity.this)
                            .setTitle("Chọn phòng khác")
                            .setItems(names, (dialog, which) -> {
                                scheduleRoom = hostRooms.get(which);
                                roomId = scheduleRoom.getId();
                                cal.tvCalendarRoomName.setText("Phòng: " + scheduleRoom.getRoomName());
                                renderSchedule();
                                applyFilter();
                                renderMonthCalendarGrid(cal);
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                    viewModel.rooms.removeObserver(this);
                } else if (state != null && state.data != null && state.data.isEmpty()) {
                    Toast.makeText(HostBookingsActivity.this, "Bạn chưa đăng phòng nào.", Toast.LENGTH_SHORT).show();
                    viewModel.rooms.removeObserver(this);
                }
            }
        });
        viewModel.loadDashboard();
    }

    private void showMonthCalendarDialog() {
        DialogHostMonthCalendarBinding cal = DialogHostMonthCalendarBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(cal.getRoot())
                .create();

        String roomName = scheduleRoom != null ? scheduleRoom.getRoomName() : "Tất cả phòng";
        cal.tvCalendarRoomName.setText("Phòng: " + roomName);

        cal.btnChangeRoom.setOnClickListener(v -> promptChangeRoomInCalendar(cal));
        cal.btnCloseCalendar.setOnClickListener(v -> dialog.dismiss());

        cal.btnPrevMonth.setOnClickListener(v -> {
            calendarYearMonth = calendarYearMonth.minusMonths(1);
            renderMonthCalendarGrid(cal);
        });

        cal.btnNextMonth.setOnClickListener(v -> {
            calendarYearMonth = calendarYearMonth.plusMonths(1);
            renderMonthCalendarGrid(cal);
        });

        renderMonthCalendarGrid(cal);
        dialog.show();
    }

    private void renderMonthCalendarGrid(DialogHostMonthCalendarBinding cal) {
        cal.tvCalendarMonthTitle.setText(String.format(Locale.getDefault(), "Tháng %02d / %d", calendarYearMonth.getMonthValue(), calendarYearMonth.getYear()));
        cal.gridCalendarDays.removeAllViews();

        int daysInMonth = calendarYearMonth.lengthOfMonth();
        LocalDate firstDay = calendarYearMonth.atDay(1);
        int startDow = firstDay.getDayOfWeek().getValue(); // 1 = Monday, 7 = Sunday

        for (int i = 1; i < startDow; i++) {
            android.view.View empty = new android.view.View(this);
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = (int) (52 * getResources().getDisplayMetrics().density);
            lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
            empty.setLayoutParams(lp);
            cal.gridCalendarDays.addView(empty);
        }

        for (int day = 1; day <= daysInMonth; day++) {
            final LocalDate currentDay = calendarYearMonth.atDay(day);

            List<Booking> dayBookings = new ArrayList<>();
            int confirmedCount = 0;
            int pendingCount = 0;

            for (Booking b : fullList) {
                if (scheduleRoom != null && b.roomId != null && !b.roomId.equals(scheduleRoom.getId())) continue;
                if (b.startAt == null || b.endAt == null) continue;
                if ("CANCELLED".equals(b.status) || "REJECTED".equals(b.status)) continue;

                LocalDate s = b.startAt.toInstant().atZone(vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.ZONE).toLocalDate();
                LocalDate e = b.endAt.toInstant().atZone(vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.ZONE).toLocalDate();

                if (!currentDay.isBefore(s) && !currentDay.isAfter(e)) {
                    dayBookings.add(b);
                    if ("CONFIRMED".equals(b.status) || "CHECKED_IN".equals(b.status) || "COMPLETED".equals(b.status)) {
                        confirmedCount++;
                    } else if ("PENDING".equals(b.status)) {
                        pendingCount++;
                    }
                }
            }

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER);

            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = (int) (52 * getResources().getDisplayMetrics().density);
            lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
            lp.setMargins(2, 2, 2, 2);
            cell.setLayoutParams(lp);

            TextView tvDayNum = new TextView(this);
            tvDayNum.setText(String.valueOf(day));
            tvDayNum.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            tvDayNum.setTypeface(null, android.graphics.Typeface.BOLD);
            tvDayNum.setGravity(android.view.Gravity.CENTER);

            TextView tvBadge = new TextView(this);
            tvBadge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9);
            tvBadge.setGravity(android.view.Gravity.CENTER);
            tvBadge.setMaxLines(1);

            boolean isBlocked = isDayBlocked(currentDay);

            if (confirmedCount > 0) {
                cell.setBackgroundColor(android.graphics.Color.parseColor("#E0F2F1"));
                tvDayNum.setTextColor(ContextCompat.getColor(this, R.color.teal_dark));
                tvBadge.setText(confirmedCount + " đã đặt");
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.teal_dark));
                tvBadge.setVisibility(View.VISIBLE);
            } else if (pendingCount > 0) {
                cell.setBackgroundColor(android.graphics.Color.parseColor("#FFF8E1"));
                tvDayNum.setTextColor(android.graphics.Color.parseColor("#E65100"));
                tvBadge.setText("Chờ");
                tvBadge.setTextColor(android.graphics.Color.parseColor("#E65100"));
                tvBadge.setVisibility(View.VISIBLE);
            } else if (isBlocked) {
                cell.setBackgroundColor(android.graphics.Color.parseColor("#ECEFF1"));
                tvDayNum.setTextColor(android.graphics.Color.parseColor("#78909C"));
                tvBadge.setVisibility(View.GONE);
            } else {
                cell.setBackgroundColor(android.graphics.Color.WHITE);
                tvDayNum.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvBadge.setVisibility(View.GONE);
            }

            if (currentDay.equals(selectedCalendarDate)) {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                if (confirmedCount > 0) gd.setColor(android.graphics.Color.parseColor("#E0F2F1"));
                else if (pendingCount > 0) gd.setColor(android.graphics.Color.parseColor("#FFF8E1"));
                else if (isBlocked) gd.setColor(android.graphics.Color.parseColor("#ECEFF1"));
                else gd.setColor(android.graphics.Color.WHITE);
                gd.setStroke((int) (2 * getResources().getDisplayMetrics().density), ContextCompat.getColor(this, R.color.teal));
                gd.setCornerRadius(8);
                cell.setBackground(gd);
            }

            cell.addView(tvDayNum);
            cell.addView(tvBadge);

            final List<Booking> currentDayBookings = dayBookings;
            cell.setOnClickListener(v -> {
                selectedCalendarDate = currentDay;
                renderMonthCalendarGrid(cal);
                renderDayDetails(cal, currentDay, currentDayBookings);
            });

            cal.gridCalendarDays.addView(cell);
        }

        renderDayDetails(cal, selectedCalendarDate, getBookingsForDate(selectedCalendarDate));
    }

    private boolean isDayBlocked(LocalDate date) {
        if (scheduleRoom == null) return false;
        if (scheduleRoom.getWorkingDays() != null && !scheduleRoom.getWorkingDays().isEmpty()) {
            if (!scheduleRoom.getWorkingDays().contains(date.getDayOfWeek().getValue())) {
                return true;
            }
        }
        if (scheduleRoom.getBlackoutDates() != null) {
            for (String val : scheduleRoom.getBlackoutDates()) {
                try {
                    if (vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.blackoutDate(val).equals(date)) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }
        if (scheduleRoom.getBlackoutRanges() != null) {
            java.util.Date start = java.util.Date.from(date.atStartOfDay(vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.ZONE).toInstant());
            java.util.Date end = java.util.Date.from(date.plusDays(1).atStartOfDay(vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.ZONE).toInstant());
            for (vn.edu.ueh.phuclu.broomsharing.model.RoomBlackout b : scheduleRoom.getBlackoutRanges()) {
                if (vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.overlaps(start, end, b.getStartAt(), b.getEndAt())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Booking> getBookingsForDate(LocalDate date) {
        List<Booking> result = new ArrayList<>();
        for (Booking b : fullList) {
            if (scheduleRoom != null && b.roomId != null && !b.roomId.equals(scheduleRoom.getId())) continue;
            if (b.startAt == null || b.endAt == null) continue;
            if ("CANCELLED".equals(b.status) || "REJECTED".equals(b.status)) continue;
            LocalDate s = b.startAt.toInstant().atZone(vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.ZONE).toLocalDate();
            LocalDate e = b.endAt.toInstant().atZone(vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.ZONE).toLocalDate();
            if (!date.isBefore(s) && !date.isAfter(e)) {
                result.add(b);
            }
        }
        return result;
    }

    private void renderDayDetails(DialogHostMonthCalendarBinding cal, LocalDate date, List<Booking> bookings) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", new Locale("vi", "VN"));
        cal.tvSelectedDayTitle.setText("Chi tiết ngày " + date.format(dtf) + ":");
        cal.layoutSelectedDayBookings.removeAllViews();

        boolean isBlocked = isDayBlocked(date);

        if (bookings == null || bookings.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(isBlocked ? "Ngày nghỉ / khóa lịch của phòng - Chưa có đơn đặt nào." : "Phòng trống cả ngày - Chưa có đơn đặt nào.");
            empty.setPadding(12, 12, 12, 12);
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            cal.layoutSelectedDayBookings.addView(empty);
            return;
        }

        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
        java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

        for (Booking b : bookings) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(16, 10, 16, 10);
            item.setBackgroundResource(R.drawable.bg_card);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 4, 0, 8);
            item.setLayoutParams(lp);

            TextView tvTime = new TextView(this);
            String timeText = (b.startAt != null ? timeFormat.format(b.startAt) : "") + " - " + (b.endAt != null ? timeFormat.format(b.endAt) : "");
            tvTime.setText("Khung giờ: " + timeText);
            tvTime.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTime.setTextColor(ContextCompat.getColor(this, R.color.teal_dark));
            tvTime.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);

            TextView tvGuest = new TextView(this);
            tvGuest.setText("Khách: " + (b.guestName != null ? b.guestName : "Khách ẩn danh") + " (Mã: #" + (b.id != null ? b.id.substring(0, Math.min(8, b.id.length())) : "") + ")");
            tvGuest.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvGuest.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);

            TextView tvStatus = new TextView(this);
            String st = "CONFIRMED".equals(b.status) ? "ĐÃ DUYỆT ĐẶT" : "PENDING".equals(b.status) ? "CHỜ DUYỆT" : "CHECKED_IN".equals(b.status) ? "ĐÃ NHẬN PHÒNG" : b.status;
            tvStatus.setText("Trạng thái: " + st + " | Tiền cọc: " + nf.format(b.totalAmount * 0.4));
            tvStatus.setTextColor(ContextCompat.getColor(this, "CONFIRMED".equals(b.status) ? R.color.teal : R.color.warning));
            tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);

            item.addView(tvTime);
            item.addView(tvGuest);
            item.addView(tvStatus);

            cal.layoutSelectedDayBookings.addView(item);
        }
    }
    @Override protected void onResume() { super.onResume(); if (binding != null && viewModel != null) refresh(); }
    @Override protected void onStop() { if(viewModel!=null)viewModel.stopBookings();super.onStop(); }
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
            @Override public void onCancel(Booking booking) { showReasonDialog(booking,"CANCELLED"); }
            @Override public void onChat(Booking booking) {
                startActivity(new android.content.Intent(HostBookingsActivity.this,vn.edu.ueh.phuclu.broomsharing.ChatActivity.class).putExtra("bookingId",booking.id).putExtra(vn.edu.ueh.phuclu.broomsharing.SpaceDetailActivity.ROOM_ID,booking.roomId).putExtra("roomName",booking.roomName));
            }
            @Override
            public void onAccept(Booking booking) {
                confirmAcceptDialog(booking);
            }

            @Override
            public void onReject(Booking booking) {
                showRejectDialog(booking);
            }
            @Override public void onCheckIn(Booking booking) {
                showCheckInDialog(booking);
            }
            @Override public void onViewReceipt(Booking booking) {
                showReceiptDialog(booking);
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
            if (state.loading) { bookingsLoaded = false; binding.tvScheduleCurrentDate.setText(R.string.booking_loading); binding.layoutEmpty.setVisibility(View.GONE); }

            if (state.error != null) {
                fullList.clear(); adapter.setData(fullList); binding.layoutEmpty.setVisibility(View.VISIBLE); binding.tvEmptyMessage.setText(state.error); binding.tvScheduleCurrentDate.setText(state.error);
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
        showReasonDialog(booking,"REJECTED");
    }
    private void showReasonDialog(Booking booking,String target) {
        final EditText etReason = new EditText(this);
        etReason.setHint("Nhập lý do (bắt buộc). Host hủy sẽ nhả lịch; booking chưa thanh toán không phát sinh hoàn tiền.");
        etReason.setPadding(40, 30, 40, 30);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("CANCELLED".equals(target)?"Hủy đặt chỗ của khách":"Từ chối yêu cầu").setView(etReason)
                .setPositiveButton("Xác nhận", null)
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = etReason.getText().toString().trim();
            if (reason.isEmpty() || reason.length() > 500) { etReason.setError(getString(R.string.host_reason_required)); return; }
            viewModel.updateBookingStatus(booking.getId(), target, reason); dialog.dismiss();
        }));
        dialog.show();
    }

    private void showCheckInDialog(Booking booking) {
        final String pin = booking.getId() != null && booking.getId().length() >= 6
                ? booking.getId().substring(booking.getId().length() - 6).toUpperCase()
                : "PIN123";
        final EditText etPin = new EditText(this);
        etPin.setHint("Nhập mã 6 ký tự (" + pin + ")");
        etPin.setPadding(40, 30, 40, 30);
        etPin.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Xác nhận Check-in khách vào phòng")
                .setMessage("Khách thuê: " + (booking.getGuestName() != null ? booking.getGuestName() : "Khách")
                        + "\nPhòng: " + booking.getRoomName()
                        + "\nThời gian: " + (booking.getStartAt() != null ? vn.edu.ueh.phuclu.broomsharing.utils.TimeUtils.formatVietnam(booking.getStartAt()) : "")
                        + "\n\nVui lòng yêu cầu khách cung cấp mã Check-in trên ứng dụng (hoặc mã gợi ý demo: " + pin + "):")
                .setView(etPin)
                .setPositiveButton("Xác nhận vào phòng", null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String input = etPin.getText().toString().trim().toUpperCase();
            if (input.isEmpty()) {
                etPin.setError("Vui lòng nhập mã check-in");
                return;
            }
            if (!pin.equalsIgnoreCase(input)) {
                etPin.setError("Mã check-in không đúng, vui lòng kiểm tra lại");
                return;
            }
            long now = System.currentTimeMillis();
            if (booking.startAt != null && now < booking.startAt.getTime() - 3600000L) {
                Toast.makeText(this, "Chưa đến giờ check-in (chỉ hỗ trợ trước giờ bắt đầu tối đa 60 phút)", Toast.LENGTH_LONG).show();
                return;
            }
            if (booking.endAt != null && now >= booking.endAt.getTime()) {
                Toast.makeText(this, "Lượt thuê này đã quá giờ kết thúc", Toast.LENGTH_LONG).show();
                return;
            }
            viewModel.updateBookingStatus(booking.getId(), Booking.STATUS_CHECKED_IN, "");
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showReceiptDialog(Booking booking) {
        String pin = booking.getId() != null && booking.getId().length() >= 6
                ? booking.getId().substring(booking.getId().length() - 6).toUpperCase()
                : "---";
        java.text.NumberFormat curr = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("vi", "VN"));
        String total = curr.format(booking.getTotalAmount());
        String deposit = "---";
        String policy = vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy.terms(booking.getActiveMode());
        if (booking.priceSnapshot != null) {
            Object dep = booking.priceSnapshot.get("depositAmount");
            if (dep instanceof Number) deposit = curr.format(((Number) dep).doubleValue());
            Object pol = booking.priceSnapshot.get("rentalPolicy");
            if (pol instanceof String && !((String) pol).isEmpty()) policy = (String) pol;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("• Mã booking: ").append(booking.getId() != null ? booking.getId() : "---").append("\n");
        sb.append("• Mã Check-in (PIN): ").append(pin).append("\n\n");
        sb.append("• Khách thuê: ").append(booking.getGuestName() != null ? booking.getGuestName() : "Khách").append(" (").append(booking.getGuestPhone() != null ? booking.getGuestPhone() : "").append(")\n");
        sb.append("• Không gian: ").append(booking.getRoomName()).append("\n");
        sb.append("• Hình thức: ").append("FIXED".equalsIgnoreCase(booking.getActiveMode()) ? "Thuê cố định" : "Thuê theo giờ").append("\n\n");
        sb.append("• Tổng tiền thuê: ").append(total).append("\n");
        sb.append("• Tiền cọc tạm tính (40%): ").append(deposit).append("\n");
        sb.append("• Trạng thái cọc: ").append(vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils.paymentStatus(booking.paymentStatus)).append("\n\n");
        sb.append("------------------------------------\n");
        sb.append("LƯU Ý SANDBOX DEMO:\n");
        sb.append("- Đồ án môn học mô phỏng tiền cọc, không trừ tiền thẻ thật.\n");
        sb.append("- Chính sách hoàn hủy v1: ").append(policy);

        new AlertDialog.Builder(this)
                .setTitle("Biên nhận đặt cọc (Sandbox Demo)")
                .setMessage(sb.toString())
                .setPositiveButton("Đóng", null)
                .show();
    }
}
