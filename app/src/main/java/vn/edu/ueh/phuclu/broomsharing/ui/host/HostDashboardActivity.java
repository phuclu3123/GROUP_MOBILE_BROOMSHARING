package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.content.Intent;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.edu.ueh.phuclu.broomsharing.CreateSpaceActivity;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.adapter.*;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityHostDashboardBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;

import java.util.*;

public class HostDashboardActivity extends SessionActivity {
    private ActivityHostDashboardBinding binding;
    private HostViewModel model;

    @Override
    protected int pageLayout() {
        return R.layout.activity_host_dashboard;
    }

    @Override
    protected boolean requiresHost() {
        return true;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        binding =
                ActivityHostDashboardBinding.bind(
                        ((android.view.ViewGroup) findViewById(android.R.id.content))
                                .getChildAt(0));
        model = new ViewModelProvider(this).get(HostViewModel.class);
        binding.tvHostRoleStatus.setText("Host đã được duyệt: " + profile.name);
        binding.btnGoApply.setVisibility(View.GONE);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRefresh.setOnClickListener(v -> refresh());
        binding.btnAddBuilding.setOnClickListener(
                v -> startActivity(new Intent(this, AddBuildingActivity.class)));
        binding.btnAddRoom.setOnClickListener(
                v -> startActivity(new Intent(this, CreateSpaceActivity.class)));
        List<Room> rooms = new ArrayList<>();
        List<Building> buildings = new ArrayList<>();
        RoomAdapter roomAdapter =
                new RoomAdapter(
                        rooms,
                        r ->
                                startActivity(
                                        new Intent(this, CreateSpaceActivity.class)
                                                .putExtra("EXTRA_ROOM", r)));
        BuildingAdapter buildingAdapter =
                new BuildingAdapter(
                        buildings,
                        b ->
                                startActivity(
                                        new Intent(this, AddBuildingActivity.class)
                                                .putExtra("EXTRA_BUILDING", b)));
        binding.rvRooms.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRooms.setAdapter(roomAdapter);
        binding.rvBuildings.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBuildings.setAdapter(buildingAdapter);
        model.rooms.observe(
                this,
                state -> {
                    if (state.error != null) binding.tvFeedback.setText(state.error);
                    if (state.data == null) return;
                    rooms.clear();
                    rooms.addAll(state.data);
                    roomAdapter.notifyDataSetChanged();
                    binding.tvCountRooms.setText(String.valueOf(rooms.size()));
                    int pending = 0;
                    for (Room r : rooms)
                        if ("PENDING_REVIEW".equals(r.getApprovalStatus())) pending++;
                    binding.tvCountPending.setText(String.valueOf(pending));
                    binding.tvEmptyRooms.setVisibility(rooms.isEmpty() ? View.VISIBLE : View.GONE);
                });
        model.buildings.observe(
                this,
                state -> {
                    if (state.error != null) binding.tvFeedback.setText(state.error);
                    if (state.data == null) return;
                    buildings.clear();
                    buildings.addAll(state.data);
                    buildingAdapter.notifyDataSetChanged();
                    binding.tvCountBuildings.setText(String.valueOf(buildings.size()));
                    binding.tvEmptyBuildings.setVisibility(
                            buildings.isEmpty() ? View.VISIBLE : View.GONE);
                });
        model.stats.observe(
                this,
                state -> {
                    binding.btnRefresh.setEnabled(!state.loading);
                    if (state.loading) binding.tvExtraStats.setText("Đang tải thống kê…");
                    if (state.error != null) {
                        binding.tvFeedback.setText(state.error);
                        binding.tvExtraStats.setText("Chưa tải được thống kê. Vui lòng thử lại.");
                    }
                    if (state.data != null)
                        binding.tvExtraStats.setText(
                                "Phòng đang hoạt động: "
                                        + state.data.activeRooms
                                        + " • Yêu cầu đặt chỗ: "
                                        + state.data.bookingRequests);
                });
        refresh();
    }

    private void refresh() {
        binding.tvFeedback.setText("");
        model.loadDashboard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) refresh();
    }
}
