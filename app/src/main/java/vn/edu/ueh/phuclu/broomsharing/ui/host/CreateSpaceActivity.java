package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;

import com.google.android.material.chip.Chip;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.SelectedImageAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityCreateSpaceBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.RoomBlackout;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;
import vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule;
import vn.edu.ueh.phuclu.broomsharing.utils.HostValidation;
import vn.edu.ueh.phuclu.broomsharing.utils.TimeUtils;
import vn.edu.ueh.phuclu.broomsharing.utils.UiForms;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Màn hình Host tạo hoặc chỉnh sửa phòng cho thuê.
 * Mapping dữ liệu nhập, chọn ảnh và lịch hoạt động; gửi yêu cầu qua HostViewModel.
 */
public class CreateSpaceActivity extends SessionActivity {
    private ActivityCreateSpaceBinding binding;
    private HostViewModel model;
    private final List<Building> buildings = new ArrayList<>();
    private SelectedImageAdapter photos;
    private String selectedType = "Bàn làm việc";
    private Bundle restored;
    private HostScheduleEditor scheduleEditor;
    private final ActivityResultLauncher<String[]> picker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenMultipleDocuments(),
                    uris -> {
                        if (model == null || model.busy()) return;
                        for (Uri uri : uris) {
                            if (model.photos.contains(uri)) continue;
                            if (model.photos.size() >= 6) {
                                binding.tvFeedback.setText("Tối đa 6 ảnh.");
                                break;
                            }
                            try {
                                getContentResolver()
                                        .takePersistableUriPermission(
                                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException ignored) {
                            }
                            model.photos.add(uri);
                        }
                        showPhotos();
                    });

    @Override
    protected int pageLayout() {
        return R.layout.activity_create_space;
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
                ActivityCreateSpaceBinding.bind(
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        model = new ViewModelProvider(this).get(HostViewModel.class);
        if (model.editingRoom == null) {
            model.editingRoom = (Room) getIntent().getSerializableExtra("EXTRA_ROOM");
            if (model.editingRoom == null) model.editingRoom = new Room();
            if (model.editingRoom.getImageUrls() != null)
                for (String url : model.editingRoom.getImageUrls())
                    model.photos.add(Uri.parse(url));
            if (restored != null && restored.containsKey("photos")) {
                model.photos.clear();
                model.photos.addAll(restored.getParcelableArrayList("photos"));
            }
            if (restored != null && restored.containsKey("blackouts")) {
                model.editingRoom.setBlackoutRanges(
                        (ArrayList<RoomBlackout>) restored.getSerializable("blackouts"));
                model.editingRoom.setBlackoutDates(restored.getStringArrayList("blackoutDates"));
            }
        }
        Room r = model.editingRoom;
        scheduleEditor = new HostScheduleEditor(this, r, restored);
        findViewById(R.id.saveListing)
                .setEnabled(r.getId() != null && !"SUSPENDED".equals(r.getListingStatus()));
        findViewById(R.id.saveListing)
                .setOnClickListener(
                        v -> {
                            if (r.getId() == null || "SUSPENDED".equals(r.getListingStatus()))
                                return;
                            new AlertDialog.Builder(this)
                                    .setMessage(R.string.host_listing_pause_help)
                                    .setPositiveButton(
                                            android.R.string.ok,
                                            (d, w) ->
                                                    model.toggleListingStatus(
                                                            r.getId(),
                                                            binding.rbStatusActive.isChecked()
                                                                    ? "ACTIVE"
                                                                    : "INACTIVE"))
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show();
                        });
        model.actionResult.observe(
                this,
                state -> {
                    if (state == null) return;
                    UiForms.enabled(binding.getRoot(), !state.loading);
                    pricingMode();
                    binding.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
                    findViewById(R.id.saveListing)
                            .setEnabled(
                                    !state.loading
                                            && r.getId() != null
                                            && !"SUSPENDED".equals(r.getListingStatus()));
                    if (state.error != null) binding.tvFeedback.setText(state.error);
                    else if (state.data != null) {
                        model.actionResult.setValue(null);
                        setResult(RESULT_OK);
                        finish();
                    }
                });
        binding.etSpaceName.setText(r.getRoomName());
        binding.etAddress.setText(r.getAddress());
        binding.etFloor.setText(r.getFloor());
        binding.etArea.setText(r.getArea() > 0 ? String.valueOf(r.getArea()) : "");
        binding.etCapacity.setText(r.getCapacity() > 0 ? String.valueOf(r.getCapacity()) : "");
        binding.etPriceHour.setText(
                r.getPricePerHour() > 0
                        ? String.format(Locale.US, "%.0f", r.getPricePerHour())
                        : "");
        binding.etPriceMonth.setText(
                r.getPricePerMonth() > 0
                        ? String.format(Locale.US, "%.0f", r.getPricePerMonth())
                        : "");
        binding.etPriceQuarter.setText(
                r.getPricePerQuarter() > 0
                        ? String.format(Locale.US, "%.0f", r.getPricePerQuarter())
                        : "");
        binding.etDescription.setText(r.getRules());
        binding.tvOpenTime.setText(r.getOpenTime() == null ? "08:00" : r.getOpenTime());
        binding.tvCloseTime.setText(r.getCloseTime() == null ? "22:00" : r.getCloseTime());
        if (r.getId() != null && r.getAmenities() != null) {
            binding.cbWifi.setChecked(r.getAmenities().contains("Wi-Fi"));
            binding.cbAirConditioner.setChecked(r.getAmenities().contains("Máy lạnh"));
            binding.cbProjector.setChecked(r.getAmenities().contains("Máy chiếu"));
            binding.cbParking.setChecked(r.getAmenities().contains("Bãi giữ xe"));
        }
        binding.rgRentalMode.check(
                Room.MODE_FIXED.equals(r.getActiveMode()) ? R.id.rbFixed : R.id.rbHourly);
        binding.rgRentalMode.setOnCheckedChangeListener((group, id) -> pricingMode());
        pricingMode();
        binding.tvDraftBadge.setText(FormatUtils.reviewStatus(r.getApprovalStatus()));
        binding.tvFeedback.setText(r.getRejectionReason());
        TextView[] chips = {
            binding.chipDesk, binding.chipPrivateRoom, binding.chipMeetingRoom, binding.chipFloor
        };
        if (r.getRoomType() != null) selectedType = r.getRoomType();
        if (restored != null) selectedType = restored.getString("selectedType", selectedType);
        for (TextView chip : chips)
            chip.setOnClickListener(
                    v -> {
                        selectedType = chip.getText().toString();
                        highlight(chips);
                    });
        highlight(chips);
        binding.btnBack.setOnClickListener(
                v -> {
                    if (!model.busy()) finish();
                });
        binding.tvOpenTime.setOnClickListener(v -> time(binding.tvOpenTime));
        binding.tvCloseTime.setOnClickListener(v -> time(binding.tvCloseTime));
        photos =
                new SelectedImageAdapter(
                        model.photos,
                        position -> {
                            if (!model.busy() && position >= 0 && position < model.photos.size()) {
                                model.photos.remove(position);
                                showPhotos();
                            }
                        });
        binding.rvSpaceImages.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvSpaceImages.setAdapter(photos);
        new ItemTouchHelper(
                        new ItemTouchHelper.SimpleCallback(
                                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
                            public boolean onMove(
                                    RecyclerView rv,
                                    RecyclerView.ViewHolder from,
                                    RecyclerView.ViewHolder to) {
                                int a = from.getBindingAdapterPosition(),
                                        z = to.getBindingAdapterPosition();
                                if (model.busy() || a < 0 || z < 0) return false;
                                Uri moved = model.photos.remove(a);
                                model.photos.add(z, moved);
                                photos.notifyItemMoved(a, z);
                                return true;
                            }

                            public void onSwiped(RecyclerView.ViewHolder holder, int direction) {}
                        })
                .attachToRecyclerView(binding.rvSpaceImages);
        binding.btnPickPhotos.setOnClickListener(
                v -> picker.launch(new String[] {"image/jpeg", "image/png", "image/webp"}));
        showPhotos();

        // Trạng thái nhận khách (H17)
        boolean isActive =
                !"SUSPENDED".equalsIgnoreCase(r.getListingStatus())
                        && !"INACTIVE".equalsIgnoreCase(r.getListingStatus());
        setListingStatusSelection(isActive);
        binding.cardStatusActive.setOnClickListener(v -> setListingStatusSelection(true));
        binding.rbStatusActive.setOnClickListener(v -> setListingStatusSelection(true));
        binding.cardStatusSuspended.setOnClickListener(v -> setListingStatusSelection(false));
        binding.rbStatusSuspended.setOnClickListener(v -> setListingStatusSelection(false));

        // Khóa lịch & Ngày nghỉ (H10)
        renderBlackoutDates(r);
        binding.btnAddBlackout.setOnClickListener(v -> showDatePickerForBlackout(r));
        binding.btnClearBlackouts.setOnClickListener(
                v -> {
                    r.getBlackoutDates().clear();
                    r.getBlackoutRanges().clear();
                    renderBlackoutDates(r);
                    Toast.makeText(this, "Đã xóa toàn bộ ngày khóa lịch.", Toast.LENGTH_SHORT)
                            .show();
                });

        binding.btnSaveDraft.setOnClickListener(v -> save(false));
        binding.btnSubmitApproval.setOnClickListener(v -> save(true));
        model.buildings.observe(
                this,
                state -> {
                    if (state.error != null) {
                        binding.tvFeedback.setText(state.error);
                        return;
                    }
                    if (state.data == null) return;
                    buildings.clear();
                    buildings.addAll(state.data);
                    List<String> names = new ArrayList<>();
                    names.add("Chọn tòa nhà của bạn");
                    int selected = 0;
                    for (int i = 0; i < buildings.size(); i++) {
                        Building item = buildings.get(i);
                        names.add(item.getName());
                        if (item.getId().equals(r.getBuildingId())) selected = i + 1;
                    }
                    binding.spBuilding.setAdapter(
                            new ArrayAdapter<>(
                                    this, android.R.layout.simple_spinner_dropdown_item, names));
                    binding.spBuilding.setSelection(selected);
                });
        binding.spBuilding.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onNothingSelected(AdapterView<?> parent) {}

                    public void onItemSelected(
                            AdapterView<?> parent, View view, int index, long id) {
                        if (index > 0 && index <= buildings.size()) {
                            Building b = buildings.get(index - 1);
                            r.setBuildingId(b.getId());
                            if (UiForms.text(binding.etAddress).isEmpty())
                                binding.etAddress.setText(b.getAddress());
                        } else r.setBuildingId(null);
                    }
                });
        if (restored != null) {
            SparseArray<Parcelable> form = restored.getSparseParcelableArray("form");
            if (form != null) binding.getRoot().restoreHierarchyState(form);
        }
        if ("SUSPENDED".equals(r.getListingStatus())) {
            binding.rbStatusActive.setEnabled(false);
            binding.cardStatusActive.setEnabled(false);
        }
        model.saved.observe(
                this,
                state -> {
                    if (state == null) return;
                    UiForms.enabled(binding.getRoot(), !state.loading);
                    pricingMode();
                    binding.progressBar.setVisibility(state.loading ? View.VISIBLE : View.GONE);
                    if (state.error != null) binding.tvFeedback.setText(state.error);
                    if (state.data != null) {
                        model.consumeSave();
                        Toast.makeText(this, "Đã lưu không gian.", Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    }
                });
        model.loadBuildings();
    }

    private void highlight(TextView[] chips) {
        for (TextView chip : chips) {
            boolean selected = chip.getText().toString().equals(selectedType);
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(
                    ContextCompat.getColor(this, selected ? R.color.white : R.color.text_primary));
        }
    }

    private void pricingMode() {
        boolean hourly = binding.rbHourly.isChecked(), idle = model == null || !model.busy();
        binding.etPriceHour.setEnabled(hourly && idle);
        binding.etPriceMonth.setEnabled(!hourly && idle);
        binding.etPriceQuarter.setEnabled(!hourly && idle);
    }

    private void showPhotos() {
        if (photos == null) return;
        photos.notifyDataSetChanged();
        binding.rvSpaceImages.setVisibility(model.photos.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvPhotoCount.setText("Đã chọn " + model.photos.size() + "/6 ảnh");
    }

    private void time(TextView view) {
        String[] parts = view.getText().toString().replaceAll("[^0-9:]", "").split(":");
        int h = 8, m = 0;
        try {
            h = Integer.parseInt(parts[0]);
            m = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
        }
        new TimePickerDialog(
                        this,
                        (picker, hour, minute) ->
                                view.setText(String.format(Locale.US, "%02d:%02d", hour, minute)),
                        h,
                        m,
                        true)
                .show();
    }

    private void save(boolean submit) {
        if (model.busy()) return;
        Room r = model.editingRoom;
        r.setRoomName(UiForms.text(binding.etSpaceName));
        r.setAddress(UiForms.text(binding.etAddress));
        r.setFloor(UiForms.text(binding.etFloor));
        r.setRoomType(selectedType);
        r.setRules(UiForms.text(binding.etDescription));
        r.setOpenTime(binding.tvOpenTime.getText().toString().replaceAll("[^0-9:]", ""));
        r.setCloseTime(binding.tvCloseTime.getText().toString().replaceAll("[^0-9:]", ""));
        try {
            scheduleEditor.read(r);
            r.setArea(UiForms.number(binding.etArea));
            double capacity = UiForms.number(binding.etCapacity);
            if (capacity != Math.rint(capacity) || capacity < 0 || capacity > 10000)
                throw new NumberFormatException();
            r.setCapacity((int) capacity);
            r.setPricePerHour(
                    binding.rbHourly.isChecked() ? UiForms.number(binding.etPriceHour) : 0);
            r.setPricePerMonth(
                    binding.rbFixed.isChecked() ? UiForms.number(binding.etPriceMonth) : 0);
            r.setPricePerQuarter(
                    binding.rbFixed.isChecked() ? UiForms.number(binding.etPriceQuarter) : 0);
        } catch (NumberFormatException e) {
            binding.tvFeedback.setText(
                    "Kiểm tra các ô số; sức chứa phải là số nguyên từ 0 đến 10.000.");
            return;
        }
        List<String> amenities = new ArrayList<>();
        if (binding.cbWifi.isChecked()) amenities.add("Wi-Fi");
        if (binding.cbAirConditioner.isChecked()) amenities.add("Máy lạnh");
        if (binding.cbProjector.isChecked()) amenities.add("Máy chiếu");
        if (binding.cbParking.isChecked()) amenities.add("Bãi giữ xe");
        r.setAmenities(amenities);

        r.setPricePerDay(0);
        r.setActiveMode(binding.rbHourly.isChecked() ? Room.MODE_HOURLY : Room.MODE_FIXED);
        // SUSPENDED is an Admin restriction; Host pauses using INACTIVE.
        if (!"SUSPENDED".equals(r.getListingStatus()))
            r.setListingStatus(binding.rbStatusActive.isChecked() ? "ACTIVE" : "INACTIVE");
        r.setApprovalStatus(submit ? Room.STATUS_PENDING_REVIEW : Room.STATUS_DRAFT);
        List<String> old = r.getImageUrls();
        List<String> preview = new ArrayList<>();
        for (Uri uri : model.photos) preview.add(uri.toString());
        r.setImageUrls(preview);
        String error = HostValidation.room(r, submit);
        r.setImageUrls(old);
        if (error != null) {
            binding.tvFeedback.setText(error);
            return;
        }
        binding.tvFeedback.setText("");
        model.saveRoom(r);
    }

    private void setListingStatusSelection(boolean active) {
        binding.rbStatusActive.setChecked(active);
        binding.rbStatusSuspended.setChecked(!active);
        binding.cardStatusActive.setBackgroundResource(
                active ? R.drawable.bg_status_card_selected : R.drawable.bg_status_card_unselected);
        binding.cardStatusSuspended.setBackgroundResource(
                !active
                        ? R.drawable.bg_status_card_selected
                        : R.drawable.bg_status_card_unselected);
    }

    private void renderBlackoutDates(Room r) {
        List<String> dates = r.getBlackoutDates();
        binding.chipGroupBlackouts.removeAllViews();

        if (dates == null || dates.isEmpty()) {
            binding.layoutEmptyBlackouts.setVisibility(View.VISIBLE);
            binding.btnClearBlackouts.setVisibility(View.GONE);
            binding.tvBlackoutCountBadge.setText("0 ngày khóa");
            binding.tvBlackoutCountBadge.setTextColor(
                    ContextCompat.getColor(this, R.color.text_secondary));
            binding.tvBlackoutCountBadge.setBackgroundResource(R.drawable.bg_soft_panel);
        } else {
            binding.layoutEmptyBlackouts.setVisibility(View.GONE);
            binding.btnClearBlackouts.setVisibility(View.VISIBLE);
            binding.tvBlackoutCountBadge.setText("Đang khóa " + dates.size() + " ngày");
            binding.tvBlackoutCountBadge.setTextColor(ContextCompat.getColor(this, R.color.danger));
            binding.tvBlackoutCountBadge.setBackgroundResource(R.drawable.bg_date_chip_badge);

            for (String dateStr : dates) {
                Chip chip = new Chip(this);
                chip.setText(dateStr);
                chip.setChipIconVisible(false);
                chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FEE2E2")));
                chip.setTextColor(ContextCompat.getColor(this, R.color.danger));
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                chip.setCloseIconVisible(true);
                chip.setCloseIconTint(
                        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.danger)));
                chip.setOnCloseIconClickListener(
                        v -> {
                            r.getBlackoutDates().remove(dateStr);
                            renderBlackoutDates(r);
                        });
                binding.chipGroupBlackouts.addView(chip);
            }
        }
        if (r.getBlackoutRanges() != null)
            for (RoomBlackout range : r.getBlackoutRanges()) {
                Chip chip = new Chip(this);
                chip.setText(
                        TimeUtils.formatVietnam(range.getStartAt())
                                + " – "
                                + TimeUtils.formatVietnam(range.getEndAt())
                                + " • "
                                + range.getReason());
                chip.setCloseIconVisible(true);
                chip.setOnCloseIconClickListener(
                        v -> {
                            if (model.busy()) return;
                            r.getBlackoutRanges().remove(range);
                            renderBlackoutDates(r);
                        });
                binding.chipGroupBlackouts.addView(chip);
            }
        int total =
                dates.size() + (r.getBlackoutRanges() == null ? 0 : r.getBlackoutRanges().size());
        binding.layoutEmptyBlackouts.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        binding.btnClearBlackouts.setVisibility(total == 0 ? View.GONE : View.VISIBLE);
        binding.tvBlackoutCountBadge.setText(total + " khung khóa");
    }

    private void showDatePickerForBlackout(Room r) {
        if (model.busy()) return;
        LocalDate now = LocalDate.now(HostSchedule.ZONE);
        DatePickerDialog date =
                new DatePickerDialog(
                        this,
                        (picker, year, month, day) -> {
                            LocalDate selected = LocalDate.of(year, month + 1, day);
                            LinearLayout form = new LinearLayout(this);
                            form.setOrientation(LinearLayout.VERTICAL);
                            int padding = (int) (20 * getResources().getDisplayMetrics().density);
                            form.setPadding(padding, padding, padding, padding);
                            CheckBox allDay = new CheckBox(this);
                            allDay.setText("Khóa cả ngày");
                            allDay.setChecked(true);
                            TextView start = new Button(this), end = new Button(this);
                            start.setText("08:00");
                            end.setText("09:00");
                            start.setEnabled(false);
                            end.setEnabled(false);
                            start.setOnClickListener(v -> time(start));
                            end.setOnClickListener(v -> time(end));
                            allDay.setOnCheckedChangeListener(
                                    (v, checked) -> {
                                        start.setEnabled(!checked);
                                        end.setEnabled(!checked);
                                    });
                            EditText reason = new EditText(this);
                            reason.setHint("Lý do khóa lịch (bắt buộc)");
                            reason.setInputType(
                                    InputType.TYPE_CLASS_TEXT
                                            | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                            form.addView(allDay);
                            form.addView(start);
                            form.addView(end);
                            form.addView(reason);
                            AlertDialog dialog =
                                    new AlertDialog.Builder(this)
                                            .setTitle("Khóa lịch " + selected)
                                            .setView(form)
                                            .setPositiveButton("Thêm", null)
                                            .setNegativeButton("Hủy", null)
                                            .create();
                            dialog.setOnShowListener(
                                    d ->
                                            dialog.getButton(-1)
                                                    .setOnClickListener(
                                                            v -> {
                                                                String why =
                                                                        reason.getText()
                                                                                .toString()
                                                                                .trim();
                                                                if (why.isEmpty()) {
                                                                    reason.setError(
                                                                            "Cần nhập lý do");
                                                                    return;
                                                                }
                                                                ZonedDateTime from =
                                                                        allDay.isChecked()
                                                                                ? selected
                                                                                        .atStartOfDay(
                                                                                                HostSchedule
                                                                                                        .ZONE)
                                                                                : selected.atTime(
                                                                                                LocalTime
                                                                                                        .parse(
                                                                                                                start
                                                                                                                        .getText()))
                                                                                        .atZone(
                                                                                                HostSchedule
                                                                                                        .ZONE);
                                                                ZonedDateTime to =
                                                                        allDay.isChecked()
                                                                                ? selected.plusDays(
                                                                                                1)
                                                                                        .atStartOfDay(
                                                                                                HostSchedule
                                                                                                        .ZONE)
                                                                                : selected.atTime(
                                                                                                LocalTime
                                                                                                        .parse(
                                                                                                                end
                                                                                                                        .getText()))
                                                                                        .atZone(
                                                                                                HostSchedule
                                                                                                        .ZONE);
                                                                if (!from.isBefore(to)
                                                                        || to.toInstant()
                                                                                .isBefore(
                                                                                        Instant
                                                                                                .now())) {
                                                                    reason.setError(
                                                                            "Giờ kết thúc phải sau bắt đầu, không thuộc quá khứ");
                                                                    return;
                                                                }
                                                                if (r.getBlackoutRanges() == null)
                                                                    r.setBlackoutRanges(
                                                                            new ArrayList<>());
                                                                r.getBlackoutRanges()
                                                                        .add(
                                                                                new RoomBlackout(
                                                                                        Date.from(
                                                                                                from
                                                                                                        .toInstant()),
                                                                                        Date.from(
                                                                                                to
                                                                                                        .toInstant()),
                                                                                        why));
                                                                renderBlackoutDates(r);
                                                                dialog.dismiss();
                                                            }));
                            dialog.show();
                        },
                        now.getYear(),
                        now.getMonthValue() - 1,
                        now.getDayOfMonth());
        date.getDatePicker()
                .setMinDate(now.atStartOfDay(HostSchedule.ZONE).toInstant().toEpochMilli());
        date.show();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (binding != null && model != null) {
            SparseArray<Parcelable> form = new SparseArray<>();
            binding.getRoot().saveHierarchyState(form);
            state.putSparseParcelableArray("form", form);
            state.putParcelableArrayList("photos", new ArrayList<>(model.photos));
            state.putString("selectedType", selectedType);
            state.putSerializable(
                    "blackouts", new ArrayList<>(model.editingRoom.getBlackoutRanges()));
            state.putStringArrayList(
                    "blackoutDates", new ArrayList<>(model.editingRoom.getBlackoutDates()));
            if (scheduleEditor != null) scheduleEditor.saveState(state);
        }
        super.onSaveInstanceState(state);
    }
}
