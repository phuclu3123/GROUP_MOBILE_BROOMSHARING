package vn.edu.ueh.phuclu.broomsharing.ui.room;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.*;
import android.text.*;
import android.text.TextUtils;
import android.view.*;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.GuestRoomAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityMainBinding;
import vn.edu.ueh.phuclu.broomsharing.databinding.ViewGuestFiltersBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestNavigation;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình trang chủ Guest: tìm kiếm, lọc và chọn phòng.
 * Theo dõi RoomViewModel; dùng GuestNavigation để chuyển giữa các chức năng.
 */
public class MainActivity extends SessionActivity {
    private ActivityMainBinding b;
    private RoomViewModel model;
    private Bundle restored;
    private Parcelable pendingScroll;
    private AlertDialog filterDialog;
    private ViewGuestFiltersBinding filters;

    @Override
    protected void onCreate(Bundle state) {
        restored = state;
        super.onCreate(state);
    }

    @Override
    protected int pageLayout() {
        return R.layout.activity_main;
    }

    @Override
    protected boolean isGuestHome() {
        return true;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        b =
                ActivityMainBinding.bind(
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        model = new ViewModelProvider(this).get(RoomViewModel.class);
        if (restored != null) {
            model.search = restored.getString("guest.search", model.search);
            model.area = restored.getString("guest.area", model.area);
            model.type = restored.getString("guest.type", model.type);
            model.mode = restored.getString("guest.mode", model.mode);
            model.minCapacity = restored.getInt("guest.capacity", model.minCapacity);
            model.maxPrice = restored.getLong("guest.price", model.maxPrice);
            model.sort = restored.getInt("guest.sort", model.sort);
            pendingScroll = restored.getParcelable("guest.scroll");
        }
        b.search.setText(model.search);
        GridLayoutManager layout = new GridLayoutManager(this,
                getResources().getInteger(R.integer.room_grid_columns));
        b.publicRooms.setLayoutManager(layout);
        GuestRoomAdapter adapter =
                new GuestRoomAdapter(
                        item ->
                                startActivity(
                                        new Intent(this, SpaceDetailActivity.class)
                                                .putExtra(
                                                        SpaceDetailActivity.ROOM_ID,
                                                        item.room.getId())));
        b.publicRooms.setAdapter(adapter);
        b.btnRefresh.setOnClickListener(v -> model.load());
        b.loadMore.setOnClickListener(v -> model.more());
        GuestNavigation.attach(this, R.id.nav_home);
        b.toggleFilters.setOnClickListener(v -> showFilters());
        updateFilterLabel();
        b.search.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence text, int start, int count, int after) {}

                    public void afterTextChanged(Editable text) {}

                    public void onTextChanged(CharSequence text, int start, int before, int count) {
                        pendingScroll = null;
                        model.filter(text.toString(), model.area, model.type, model.mode);
                    }
                });
        model.rooms()
                .observe(
                        this,
                        state -> {
                            boolean busy = model.isLoading();
                            b.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
                            b.btnRefresh.setEnabled(!busy);
                            b.loadMore.setEnabled(!busy);
                            b.loadMore.setVisibility(
                                    !busy && state.error == null && model.hasMore()
                                            ? View.VISIBLE
                                            : View.GONE);
                            String feedback =
                                    state.error != null
                                            ? state.error
                                            : busy && adapter.getCurrentList().isEmpty()
                                                    ? getString(R.string.guest_loading)
                                                    : "";
                            if (!busy
                                    && state.error == null
                                    && !model.hasMore()
                                    && state.data != null
                                    && state.data.isEmpty()) {
                                boolean filtered =
                                        !model.search.trim().isEmpty()
                                                || !model.area.trim().isEmpty()
                                                || !model.type.isEmpty()
                                                || !model.mode.isEmpty()
                                                || model.minCapacity > 0
                                                || model.maxPrice > 0;
                                feedback =
                                        getString(
                                                        filtered
                                                                ? R.string.home_no_match
                                                                : R.string.home_no_rooms)
                                                + "\n\n"
                                                + getString(
                                                        filtered
                                                                ? R.string.home_no_match_hint
                                                                : R.string.home_no_rooms_hint);
                            }
                            b.tvFeedback.setText(feedback);
                            b.tvFeedback.setVisibility(
                                    feedback.isEmpty() ? View.GONE : View.VISIBLE);
                            b.publicRooms.setVisibility(
                                    feedback.isEmpty() ? View.VISIBLE : View.INVISIBLE);
                            updateFilterLabel();
                            if (state.data != null) {
                                adapter.submitList(
                                        state.data,
                                        () -> {
                                            if (pendingScroll != null && !state.data.isEmpty()) {
                                                layout.onRestoreInstanceState(pendingScroll);
                                                pendingScroll = null;
                                            }
                                        });
                                b.scope.setVisibility(
                                        state.data.isEmpty() ? View.GONE : View.VISIBLE);
                                b.scope.setText(
                                        getString(
                                                R.string.home_result_count,
                                                state.data.size(),
                                                model.loadedCount()));
                            }
                        });
        model.ensureLoaded();
        model.resumeCalendars();
        if (restored != null && restored.getBoolean("guest.filtersExpanded"))
            b.getRoot().post(this::showFilters);
    }

    @Override protected void onStart() { super.onStart(); if (model != null) model.resumeCalendars(); }

    @Override protected void onStop() { if (model != null) model.pauseCalendars(); super.onStop(); }

    private void updateFilterLabel() {
        int count =
                (model.area.trim().isEmpty() ? 0 : 1)
                        + (model.type.isEmpty() ? 0 : 1)
                        + (model.mode.isEmpty() ? 0 : 1)
                        + (model.minCapacity > 0 ? 1 : 0)
                        + (model.maxPrice > 0 ? 1 : 0)
                        + (model.sort > 0 ? 1 : 0);
        b.toggleFilters.setText(
                count == 0
                        ? getString(R.string.home_filter)
                        : getString(R.string.home_filter_count, count));
        List<String> labels = new ArrayList<>();
        if (!model.area.trim().isEmpty()) labels.add("Khu vực: " + model.area);
        if (!model.type.isEmpty()) labels.add(model.type);
        if (!model.mode.isEmpty())
            labels.add(Room.MODE_HOURLY.equals(model.mode) ? "Thuê giờ" : "Thuê cố định");
        if (model.minCapacity > 0) labels.add("Từ " + model.minCapacity + " người");
        if (model.maxPrice > 0)
            labels.add(
                    "Tối đa " + FormatUtils.formatCurrency(model.maxPrice) + " / đơn vị trên thẻ");
        if (model.sort > 0)
            labels.add(getResources().getStringArray(R.array.guest_sort_options)[model.sort]);
        b.activeFilters.setText(TextUtils.join(" • ", labels));
        b.activeFilters.setVisibility(labels.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showFilters() {
        if (filterDialog != null && filterDialog.isShowing()) return;
        filters = ViewGuestFiltersBinding.inflate(getLayoutInflater());
        boolean draft = restored != null && restored.getBoolean("guest.filtersExpanded");
        filters.area.setText(draft ? restored.getString("draft.area", model.area) : model.area);
        filters.capacityFilter.setText(
                draft
                        ? restored.getString("draft.capacity", "")
                        : model.minCapacity > 0 ? String.valueOf(model.minCapacity) : "");
        filters.priceFilter.setText(
                draft
                        ? restored.getString("draft.price", "")
                        : model.maxPrice > 0 ? String.valueOf(model.maxPrice) : "");
        String[] types = getResources().getStringArray(R.array.guest_types);
        int type = 0;
        for (int i = 1; i < types.length; i++) if (types[i].equals(model.type)) type = i;
        filters.typeFilter.setSelection(draft ? restored.getInt("draft.type", type) : type);
        int mode =
                Room.MODE_HOURLY.equals(model.mode)
                        ? 1
                        : Room.MODE_FIXED.equals(model.mode) ? 2 : 0;
        filters.modeFilter.setSelection(draft ? restored.getInt("draft.mode", mode) : mode);
        filters.sortFilter.setSelection(
                draft ? restored.getInt("draft.sort", model.sort) : model.sort);
        if (restored != null) restored.putBoolean("guest.filtersExpanded", false);
        filters.clearFilters.setOnClickListener(
                v -> {
                    filters.area.setText("");
                    filters.capacityFilter.setText("");
                    filters.priceFilter.setText("");
                    filters.typeFilter.setSelection(0);
                    filters.modeFilter.setSelection(0);
                    filters.sortFilter.setSelection(0);
                });
        filterDialog =
                new AlertDialog.Builder(this)
                        .setTitle(R.string.guest_filter_title)
                        .setView(filters.getRoot())
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.guest_filter_apply, null)
                        .create();
        filterDialog.setOnShowListener(
                dialog ->
                        filterDialog
                                .getButton(DialogInterface.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        view -> {
                                            long capacity =
                                                    number(
                                                            filters.capacityFilter
                                                                    .getText()
                                                                    .toString());
                                            long price =
                                                    number(
                                                            filters.priceFilter
                                                                    .getText()
                                                                    .toString());
                                            if (capacity > 10000) {
                                                filters.capacityFilter.setError(
                                                        getString(
                                                                R.string
                                                                        .guest_filter_capacity_error));
                                                return;
                                            }
                                            if (price > 1_000_000_000_000L) {
                                                filters.priceFilter.setError(
                                                        getString(
                                                                R.string.guest_filter_price_error));
                                                return;
                                            }
                                            model.minCapacity = (int) capacity;
                                            model.maxPrice = price;
                                            model.sort =
                                                    filters.sortFilter.getSelectedItemPosition();
                                            String selectedType =
                                                    filters.typeFilter.getSelectedItemPosition()
                                                                    == 0
                                                            ? ""
                                                            : filters.typeFilter
                                                                    .getSelectedItem()
                                                                    .toString();
                                            int selectedMode =
                                                    filters.modeFilter.getSelectedItemPosition();
                                            pendingScroll = null;
                                            model.filter(
                                                    b.search.getText().toString(),
                                                    filters.area.getText().toString().trim(),
                                                    selectedType,
                                                    selectedMode == 1
                                                            ? Room.MODE_HOURLY
                                                            : selectedMode == 2
                                                                    ? Room.MODE_FIXED
                                                                    : "");
                                            updateFilterLabel();
                                            b.publicRooms.scrollToPosition(0);
                                            filterDialog.dismiss();
                                        }));
        filterDialog.show();
    }

    @Override
    protected void onDestroy() {
        if (filterDialog != null) filterDialog.dismiss();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        if (model != null) {
            boolean open = filterDialog != null && filterDialog.isShowing();
            out.putBoolean("guest.filtersExpanded", open);
            if (open && filters != null) {
                out.putString("draft.area", filters.area.getText().toString());
                out.putString("draft.capacity", filters.capacityFilter.getText().toString());
                out.putString("draft.price", filters.priceFilter.getText().toString());
                out.putInt("draft.type", filters.typeFilter.getSelectedItemPosition());
                out.putInt("draft.mode", filters.modeFilter.getSelectedItemPosition());
                out.putInt("draft.sort", filters.sortFilter.getSelectedItemPosition());
            }
            out.putString("guest.search", model.search);
            out.putString("guest.area", model.area);
            out.putString("guest.type", model.type);
            out.putString("guest.mode", model.mode);
            out.putInt("guest.capacity", model.minCapacity);
            out.putLong("guest.price", model.maxPrice);
            out.putInt("guest.sort", model.sort);
            out.putParcelable(
                    "guest.scroll",
                    pendingScroll != null
                            ? pendingScroll
                            : b.publicRooms.getLayoutManager().onSaveInstanceState());
        } else if (restored != null) out.putAll(restored);
        super.onSaveInstanceState(out);
    }

    private long number(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
