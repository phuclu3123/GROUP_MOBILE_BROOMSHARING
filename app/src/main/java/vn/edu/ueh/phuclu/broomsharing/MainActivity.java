package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.edu.ueh.phuclu.broomsharing.adapter.GuestRoomAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityMainBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.RoomViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestNavigation;

/** Activity navigation shell adapted from Truc; Auth/Host/Admin retain their shared guards. */
public class MainActivity extends SessionActivity {
    private ActivityMainBinding b;
    private RoomViewModel model;
    private Bundle restored;
    private Parcelable pendingScroll;

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
                        ((android.view.ViewGroup) findViewById(android.R.id.content))
                                .getChildAt(0));
        model = new ViewModelProvider(this).get(RoomViewModel.class);
        if (restored != null) {
            model.search = restored.getString("guest.search", model.search);
            model.area = restored.getString("guest.area", model.area);
            model.type = restored.getString("guest.type", model.type);
            model.mode = restored.getString("guest.mode", model.mode);
            model.minCapacity=restored.getInt("guest.capacity",model.minCapacity);
            model.maxPrice=restored.getLong("guest.price",model.maxPrice);
            model.sort=restored.getInt("guest.sort",model.sort);
            pendingScroll = restored.getParcelable("guest.scroll");
        }
        b.search.setText(model.search);
        b.area.setText(model.area);
        b.capacityFilter.setText(model.minCapacity>0?String.valueOf(model.minCapacity):"");
        b.priceFilter.setText(model.maxPrice>0?String.valueOf(model.maxPrice):"");
        b.sortFilter.setSelection(model.sort);
        String[] types = getResources().getStringArray(R.array.guest_types);
        for (int i = 1; i < types.length; i++)
            if (types[i].equals(model.type)) b.typeFilter.setSelection(i);
        b.modeFilter.setSelection(
                Room.MODE_HOURLY.equals(model.mode)
                        ? 1
                        : Room.MODE_FIXED.equals(model.mode) ? 2 : 0);
        LinearLayoutManager layout = new LinearLayoutManager(this);
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
        b.clearFilters.setOnClickListener(
                v -> {
                    b.search.setText("");
                    b.area.setText("");
                    b.typeFilter.setSelection(0);
                    b.modeFilter.setSelection(0);
                    b.capacityFilter.setText("");b.priceFilter.setText("");b.sortFilter.setSelection(0);
                });
        GuestNavigation.attach(this, R.id.nav_home);
        b.filterPanel.setVisibility(
                restored != null && restored.getBoolean("guest.filtersExpanded")
                        ? View.VISIBLE
                        : View.GONE);
        b.toggleFilters.setOnClickListener(
                v ->
                        b.filterPanel.setVisibility(
                                b.filterPanel.getVisibility() == View.VISIBLE
                                        ? View.GONE
                                        : View.VISIBLE));
        updateFilterLabel();
        TextWatcher watcher =
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        applyFilters();
                    }

                    public void afterTextChanged(Editable text) {}
                };
        b.search.addTextChangedListener(watcher);
        b.area.addTextChangedListener(watcher);
        b.capacityFilter.addTextChangedListener(watcher);b.priceFilter.addTextChangedListener(watcher);
        AdapterView.OnItemSelectedListener selected =
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        applyFilters();
                    }

                    public void onNothingSelected(AdapterView<?> parent) {}
                };
        b.typeFilter.setOnItemSelectedListener(selected);
        b.modeFilter.setOnItemSelectedListener(selected);
        b.sortFilter.setOnItemSelectedListener(selected);
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
                                    && state.data != null
                                    && state.data.isEmpty()) {
                                boolean filtered =
                                        !model.search.trim().isEmpty()
                                                || !model.area.trim().isEmpty()
                                                || !model.type.isEmpty()
                                                || !model.mode.isEmpty() || model.minCapacity>0 || model.maxPrice>0;
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
    }

    private void updateFilterLabel() {
        int count =
                (model.area.trim().isEmpty() ? 0 : 1)
                        + (model.type.isEmpty() ? 0 : 1)
                        + (model.mode.isEmpty() ? 0 : 1) + (model.minCapacity>0?1:0) + (model.maxPrice>0?1:0) + (model.sort>0?1:0);
        b.toggleFilters.setText(
                count == 0
                        ? getString(R.string.home_filter)
                        : getString(R.string.home_filter_count, count));
        java.util.List<String> labels=new java.util.ArrayList<>();
        if(!model.area.trim().isEmpty())labels.add("Khu vực: "+model.area);
        if(!model.type.isEmpty())labels.add(model.type);
        if(!model.mode.isEmpty())labels.add(Room.MODE_HOURLY.equals(model.mode)?"Thuê giờ":"Thuê cố định");
        if(model.minCapacity>0)labels.add("Từ "+model.minCapacity+" người");
        if(model.maxPrice>0)labels.add("Tối đa "+vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils.formatCurrency(model.maxPrice)+" / đơn vị trên thẻ");
        if(model.sort>0)labels.add(getResources().getStringArray(R.array.guest_sort_options)[model.sort]);
        b.activeFilters.setText(android.text.TextUtils.join(" • ",labels));b.activeFilters.setVisibility(labels.isEmpty()?View.GONE:View.VISIBLE);
    }

    private void applyFilters() {
        String search = b.search.getText().toString(), area = b.area.getText().toString();
        String type =
                b.typeFilter.getSelectedItemPosition() <= 0
                        ? ""
                        : b.typeFilter.getSelectedItem().toString();
        String mode =
                b.modeFilter.getSelectedItemPosition() == 1
                        ? Room.MODE_HOURLY
                        : b.modeFilter.getSelectedItemPosition() == 2 ? Room.MODE_FIXED : "";
        int capacity=(int)number(b.capacityFilter.getText().toString());
        long price=number(b.priceFilter.getText().toString());int sort=b.sortFilter.getSelectedItemPosition();
        if (capacity==model.minCapacity && price==model.maxPrice && sort==model.sort && search.equals(model.search)
                && area.equals(model.area)
                && type.equals(model.type)
                && mode.equals(model.mode)) return;
        pendingScroll = null;
        model.minCapacity=capacity;model.maxPrice=price;model.sort=sort;
        model.filter(search, area, type, mode);
        updateFilterLabel();
        b.publicRooms.scrollToPosition(0);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        if (model != null) {
            out.putBoolean("guest.filtersExpanded", b.filterPanel.getVisibility() == View.VISIBLE);
            out.putString("guest.search", model.search);
            out.putString("guest.area", model.area);
            out.putString("guest.type", model.type);
            out.putString("guest.mode", model.mode);
            out.putInt("guest.capacity",model.minCapacity);out.putLong("guest.price",model.maxPrice);out.putInt("guest.sort",model.sort);
            out.putParcelable(
                    "guest.scroll",
                    pendingScroll != null
                            ? pendingScroll
                            : b.publicRooms.getLayoutManager().onSaveInstanceState());
        } else if (restored != null) out.putAll(restored);
        super.onSaveInstanceState(out);
    }
    private long number(String value) {try{return Long.parseLong(value);}catch(NumberFormatException e){return 0;}}
}
