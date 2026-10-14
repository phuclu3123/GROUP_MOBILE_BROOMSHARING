package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import vn.edu.ueh.phuclu.broomsharing.CreateSpaceActivity;
import vn.edu.ueh.phuclu.broomsharing.adapter.BuildingAdapter;
import vn.edu.ueh.phuclu.broomsharing.adapter.RoomAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityHostDashboardBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.model.HostApplication;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.repository.HostRepository;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.List;

public class HostDashboardActivity extends AppCompatActivity {

    private ActivityHostDashboardBinding binding;
    private HostRepository repository;

    private final List<Room> roomList = new ArrayList<>();
    private final List<Building> buildingList = new ArrayList<>();
    private RoomAdapter roomAdapter;
    private BuildingAdapter buildingAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHostDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new HostRepository();

        setupTopBar();
        setupRecyclerViews();
        setupListeners();
    }

    private void setupTopBar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardData();
    }

    private void setupRecyclerViews() {
        // Rooms
        binding.rvRooms.setLayoutManager(new LinearLayoutManager(this));
        roomAdapter = new RoomAdapter(roomList, room -> {
            Intent intent = new Intent(this, CreateSpaceActivity.class);
            intent.putExtra("EXTRA_ROOM", room);
            startActivity(intent);
        });
        binding.rvRooms.setAdapter(roomAdapter);

        // Buildings
        binding.rvBuildings.setLayoutManager(new LinearLayoutManager(this));
        buildingAdapter = new BuildingAdapter(buildingList, building -> {
            Intent intent = new Intent(this, AddBuildingActivity.class);
            intent.putExtra("EXTRA_BUILDING", building);
            startActivity(intent);
        });
        binding.rvBuildings.setAdapter(buildingAdapter);
    }

    private void setupListeners() {
        binding.btnGoApply.setOnClickListener(v -> {
            startActivity(new Intent(this, HostApplyActivity.class));
        });

        binding.btnAddBuilding.setOnClickListener(v -> {
            startActivity(new Intent(this, AddBuildingActivity.class));
        });

        binding.btnAddRoom.setOnClickListener(v -> {
            startActivity(new Intent(this, CreateSpaceActivity.class));
        });
    }

    private void loadDashboardData() {
        String uid = FirebaseHelper.getCurrentHostUid(this);

        // 1. Kiểm tra đơn đăng ký Host
        repository.checkHostApplicationStatus(uid, new HostRepository.Callback<HostApplication>() {
            @Override
            public void onSuccess(HostApplication app) {
                if (app != null) {
                    if (HostApplication.STATUS_APPROVED.equals(app.getStatus())) {
                        binding.tvHostRoleStatus.setText("Trạng thái tài khoản: Đã phê duyệt chính thức (HOST)");
                        binding.btnGoApply.setVisibility(View.GONE);
                    } else if (HostApplication.STATUS_PENDING.equals(app.getStatus())) {
                        binding.tvHostRoleStatus.setText("Trạng thái tài khoản: Đang chờ Admin xét duyệt");
                        binding.btnGoApply.setText("Xem hồ sơ đã gửi");
                    }
                }
            }

            @Override
            public void onError(Exception e) {}
        });

        // 2. Tải danh sách tòa nhà
        repository.getBuildingsByOwner(uid, new HostRepository.Callback<List<Building>>() {
            @Override
            public void onSuccess(List<Building> list) {
                buildingList.clear();
                buildingList.addAll(list);
                buildingAdapter.notifyDataSetChanged();

                binding.tvCountBuildings.setText(String.valueOf(buildingList.size()));
                binding.tvEmptyBuildings.setVisibility(buildingList.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception e) {}
        });

        // 3. Tải danh sách phòng
        repository.getRoomsByOwner(uid, new HostRepository.Callback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> list) {
                roomList.clear();
                roomList.addAll(list);
                roomAdapter.notifyDataSetChanged();

                binding.tvCountRooms.setText(String.valueOf(roomList.size()));

                int pendingCount = 0;
                for (Room r : roomList) {
                    if (Room.STATUS_PENDING_REVIEW.equals(r.getApprovalStatus())) {
                        pendingCount++;
                    }
                }
                binding.tvCountPending.setText(String.valueOf(pendingCount));
                binding.tvEmptyRooms.setVisibility(roomList.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception e) {}
        });
    }
}
