
package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;

import vn.edu.ueh.phuclu.broomsharing.model.RoomListing;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;
import vn.edu.ueh.phuclu.broomsharing.repository.RoomRepository;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestNavigation;

public class MapActivity extends SessionActivity {

    private LinearLayout roomContainer;
    private ProgressBar progress;
    private TextView feedback;

    private final RoomRepository repository = new RoomRepository();

    @Override
    protected int pageLayout() {
        return R.layout.activity_map;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        ((MaterialToolbar) findViewById(R.id.mapToolbar))
                .setTitle("Bản đồ phòng");

        roomContainer = findViewById(R.id.mapRoomContainer);
        progress = findViewById(R.id.mapProgress);
        feedback = findViewById(R.id.mapFeedback);

        findViewById(R.id.mapRefresh)
                .setOnClickListener(v -> loadRooms());

        findViewById(R.id.mapChooseArea)
                .setOnClickListener(v -> openAreaSearch());

        GuestNavigation.attach(this, R.id.nav_map);

        loadRooms();
    }

    private void loadRooms() {
        progress.setVisibility(View.VISIBLE);
        feedback.setVisibility(View.VISIBLE);
        feedback.setText("Đang tải danh sách phòng...");
        roomContainer.removeAllViews();

        repository.page(null, new ResultCallback<RoomRepository.Page>() {

            @Override
            public void onSuccess(RoomRepository.Page page) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                progress.setVisibility(View.GONE);

                int count = 0;

                for (RoomListing item : page.items) {
                    if (item.building == null) {
                        continue;
                    }

                    if (item.building.getLocation() == null) {
                        continue;
                    }

                    count++;
                    addRoom(item);
                }

                if (count == 0) {
                    feedback.setText(
                            "Chưa có phòng được duyệt có tọa độ. Bạn có thể chọn khu vực để tìm trên Google Maps."
                    );
                } else {
                    feedback.setText(
                            "Tìm thấy " + count
                                    + " phòng có tọa độ. Chọn Chỉ đường để xem đường đi."
                    );
                }
            }

            @Override
            public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                progress.setVisibility(View.GONE);

                feedback.setText(
                        "Không tải được danh sách phòng. Hãy kiểm tra kết nối rồi thử lại."
                );

                Toast.makeText(
                        MapActivity.this,
                        error.getMessage() == null
                                ? "Lỗi tải dữ liệu phòng."
                                : error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void addRoom(RoomListing item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 14, 16, 14);
        card.setBackgroundResource(R.drawable.bg_card);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);

        cardParams.setMargins(0, 0, 0, 12);
        roomContainer.addView(card, cardParams);

        TextView title = new TextView(this);

        String roomName = item.room.getRoomName();

        if (roomName == null || roomName.trim().isEmpty()) {
            roomName = "Không gian cho thuê";
        }

        title.setText(roomName);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(getColor(R.color.text_primary));

        card.addView(title);

        TextView address = new TextView(this);

        String addressText = item.building.getAddress();

        if (addressText == null) {
            addressText = "";
        }

        String district = item.building.getDistrict();

        if (district != null && !district.trim().isEmpty()) {
            addressText += ", " + district;
        }

        address.setText(addressText);
        address.setTextSize(13);
        address.setTextColor(getColor(R.color.text_secondary));

        LinearLayout.LayoutParams addressParams =
                new LinearLayout.LayoutParams(-1, -2);

        addressParams.topMargin = 5;
        card.addView(address, addressParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);

        Button detailButton = new Button(this);
        detailButton.setText("Chi tiết");

        detailButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SpaceDetailActivity.class);
            intent.putExtra(
                    SpaceDetailActivity.ROOM_ID,
                    item.room.getId()
            );
            startActivity(intent);
        });

        Button directionsButton = new Button(this);
        directionsButton.setText("Chỉ đường");

        directionsButton.setOnClickListener(
                v -> openDirections(item)
        );

        actions.addView(detailButton);
        actions.addView(directionsButton);
        card.addView(actions);
    }

    private void openDirections(RoomListing item) {
        if (item.building == null
                || item.building.getLocation() == null) {

            Toast.makeText(
                    this,
                    "Phòng này chưa có tọa độ.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String destination =
                item.building.getLatitude()
                        + ","
                        + item.building.getLongitude();

        Uri uri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1"
                        + "&destination=" + Uri.encode(destination)
                        + "&travelmode=driving"
        );

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);

        try {
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    "Không thể mở Google Maps trên thiết bị.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void openAreaSearch() {
        Uri uri = Uri.parse(
                "https://www.google.com/maps/search/?api=1&query="
                        + Uri.encode("phòng cho thuê gần tôi")
        );

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    "Không thể mở bản đồ.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}
