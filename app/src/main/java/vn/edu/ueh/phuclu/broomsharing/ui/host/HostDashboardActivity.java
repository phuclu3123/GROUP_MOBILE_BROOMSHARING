package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.BuildingAdapter;
import vn.edu.ueh.phuclu.broomsharing.adapter.RoomAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityHostDashboardBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;
import vn.edu.ueh.phuclu.broomsharing.ui.report.ReportCenterActivity;

import java.util.ArrayList;
import java.util.List;

/*
   Hiển thị tổng quan và danh sách tài sản của Host từ LiveData.
   Bắt đầu/ngừng listener theo vòng đời để dữ liệu giữa thiết bị được đồng bộ.
*/
public class HostDashboardActivity extends SessionActivity {
    private ActivityHostDashboardBinding b;
    private HostViewModel model;
    private int selected = R.id.host_overview;
    private int spaceTab;
    private final List<Room> rooms = new ArrayList<>();
    private final List<Building> buildings = new ArrayList<>();

    @Override
    protected int pageLayout() {
        return R.layout.activity_host_dashboard;
    }

    @Override
    protected boolean requiresHost() {
        return true;
    }

    @Override
    protected void onCreate(Bundle state) {
        selected =
                state == null
                        ? getIntent().getIntExtra(ManagementNavigation.SECTION, R.id.host_overview)
                        : state.getInt("selected", R.id.host_overview);
        spaceTab = state == null ? 0 : state.getInt("spaceTab", 0);
        super.onCreate(state);
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        b = ActivityHostDashboardBinding.bind(findViewById(R.id.workspaceRoot));
        ManagementNavigation.applyInsets(this);
        model = new ViewModelProvider(this).get(HostViewModel.class);
        b.tvHostRoleStatus.setText(getString(R.string.workspace_greeting, profile.name));
        b.btnRefresh.setOnClickListener(v -> model.loadDashboard());
        b.cardBookingRequests.setOnClickListener(
                v -> startActivity(new Intent(this, HostBookingsActivity.class)));
        b.btnManageSpaces.setOnClickListener(v -> showTab(R.id.host_spaces));
        b.btnHostReports.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(this, ReportCenterActivity.class)
                                        .putExtra("received", true)));
        b.btnAddBuilding.setOnClickListener(
                v -> startActivity(new Intent(this, AddBuildingActivity.class)));
        b.btnAddRoom.setOnClickListener(
                v -> startActivity(new Intent(this, CreateSpaceActivity.class)));
        RoomAdapter roomAdapter =
                new RoomAdapter(
                        rooms,
                        room ->
                                startActivity(
                                        new Intent(this, CreateSpaceActivity.class)
                                                .putExtra("EXTRA_ROOM", room)),
                        room ->
                                startActivity(
                                        new Intent(this, HostBookingsActivity.class)
                                                .putExtra("ROOM_ID", room.getId())));
        BuildingAdapter buildingAdapter =
                new BuildingAdapter(
                        buildings,
                        building ->
                                startActivity(
                                        new Intent(this, AddBuildingActivity.class)
                                                .putExtra("EXTRA_BUILDING", building)));
        b.rvRooms.setLayoutManager(new LinearLayoutManager(this));
        b.rvRooms.setAdapter(roomAdapter);
        b.rvBuildings.setLayoutManager(new LinearLayoutManager(this));
        b.rvBuildings.setAdapter(buildingAdapter);
        b.spaceTabs.addTab(b.spaceTabs.newTab().setText(R.string.workspace_rooms));
        b.spaceTabs.addTab(b.spaceTabs.newTab().setText(R.string.workspace_buildings));
        b.spaceTabs.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {
                    public void onTabSelected(TabLayout.Tab tab) {
                        spaceTab = tab.getPosition();
                        renderSpaces();
                    }

                    public void onTabUnselected(TabLayout.Tab tab) {}

                    public void onTabReselected(TabLayout.Tab tab) {}
                });
        b.spaceTabs.selectTab(b.spaceTabs.getTabAt(spaceTab));
        ManagementNavigation.attach(this, UserRole.HOST, selected, this::showTab);
        showTab(selected);
        model.rooms.observe(
                this,
                state -> {
                    rooms.clear();
                    if (state.data != null) rooms.addAll(state.data);
                    roomAdapter.notifyDataSetChanged();
                    b.tvCountRooms.setText(
                            state.data == null
                                    ? getString(R.string.workspace_stat_unknown)
                                    : String.valueOf(rooms.size()));
                    int pending = 0;
                    for (Room room : rooms)
                        if (Room.STATUS_PENDING_REVIEW.equals(room.getApprovalStatus())) pending++;
                    b.tvCountPending.setText(
                            state.data == null
                                    ? getString(R.string.workspace_stat_unknown)
                                    : String.valueOf(pending));
                    renderSpaces();
                    renderLoadState();
                });
        model.buildings.observe(
                this,
                state -> {
                    buildings.clear();
                    if (state.data != null) buildings.addAll(state.data);
                    buildingAdapter.notifyDataSetChanged();
                    b.tvCountBuildings.setText(
                            state.data == null
                                    ? ""
                                    : getString(
                                            R.string.workspace_building_count, buildings.size()));
                    renderSpaces();
                    renderLoadState();
                });
        model.stats.observe(
                this,
                state -> {
                    b.tvCountActive.setText(
                            state.data == null
                                    ? getString(R.string.workspace_stat_unknown)
                                    : String.valueOf(state.data.activeRooms));
                    b.tvBadgeBookingCount.setText(
                            state.data == null
                                    ? getString(R.string.workspace_stat_unknown)
                                    : String.valueOf(state.data.bookingRequests));
                    renderLoadState();
                });
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            public void handleOnBackPressed() {
                                if (selected == R.id.host_spaces) showTab(R.id.host_overview);
                                else {
                                    setEnabled(false);
                                    getOnBackPressedDispatcher().onBackPressed();
                                    setEnabled(true);
                                }
                            }
                        });
        model.loadDashboard();
    }

    private void renderSpaces() {
        boolean showRooms = spaceTab == 0;
        b.rvRooms.setVisibility(showRooms ? View.VISIBLE : View.GONE);
        b.rvBuildings.setVisibility(showRooms ? View.GONE : View.VISIBLE);
        b.btnAddRoom.setVisibility(showRooms ? View.VISIBLE : View.GONE);
        b.btnAddBuilding.setVisibility(showRooms ? View.GONE : View.VISIBLE);
        b.tvCountBuildings.setVisibility(showRooms ? View.GONE : View.VISIBLE);
        UiState<List<Room>> roomState = model.rooms.getValue();
        UiState<List<Building>> buildingState = model.buildings.getValue();
        b.tvEmptyRooms.setVisibility(
                showRooms && roomState != null && roomState.data != null && rooms.isEmpty()
                        ? View.VISIBLE
                        : View.GONE);
        b.tvEmptyBuildings.setVisibility(
                !showRooms
                                && buildingState != null
                                && buildingState.data != null
                                && buildings.isEmpty()
                        ? View.VISIBLE
                        : View.GONE);
    }

    private void renderLoadState() {
        UiState<?>[] states = {
            model.rooms.getValue(), model.buildings.getValue(), model.stats.getValue()
        };
        boolean loading = false;
        String error = null;
        for (UiState<?> state : states)
            if (state != null) {
                loading |= state.loading;
                if (state.error != null) error = state.error;
            }
        b.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        b.btnRefresh.setEnabled(!loading);
        b.btnRefresh.setText(error == null ? R.string.workspace_refresh : R.string.workspace_retry);
        b.tvFeedback.setVisibility(error != null || loading ? View.VISIBLE : View.GONE);
        b.tvFeedback.setText(
                error != null ? error : loading ? getString(R.string.workspace_loading) : "");
    }

    private void showTab(int id) {
        selected = id == R.id.host_spaces ? R.id.host_spaces : R.id.host_overview;
        b.overviewPanel.setVisibility(selected == R.id.host_overview ? View.VISIBLE : View.GONE);
        b.spacesPanel.setVisibility(selected == R.id.host_spaces ? View.VISIBLE : View.GONE);
        b.workspaceTitle.setText(
                selected == R.id.host_overview
                        ? R.string.workspace_overview
                        : R.string.workspace_spaces);
        ManagementNavigation.select(this, selected);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        selected = intent.getIntExtra(ManagementNavigation.SECTION, R.id.host_overview);
        if (b != null) showTab(selected);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("selected", selected);
        state.putInt("spaceTab", spaceTab);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (model != null) model.watchDashboard();
    }

    @Override
    protected void onStop() {
        if (model != null) model.stopDashboard();
        super.onStop();
    }
}
