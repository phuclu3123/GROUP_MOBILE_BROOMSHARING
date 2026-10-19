package vn.edu.ueh.phuclu.broomsharing.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.*;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.util.*;

/** One chosen search origin, paged public offices and one route. */
public class MapViewModel extends AndroidViewModel {
    private final MapRepository maps;
    private final LocationRepository locations;
    private final RoomRepository rooms = new RoomRepository();
    public final MutableLiveData<MapPoint> origin = new MutableLiveData<>(MapArea.CENTER);
    public final MutableLiveData<MapPoint> destination = new MutableLiveData<>();
    public final MutableLiveData<MapRoute> route = new MutableLiveData<>();
    public final MutableLiveData<List<RoomListing>> nearby =
            new MutableLiveData<>(Collections.emptyList());
    public final MutableLiveData<List<MapPoint>> candidates = new MutableLiveData<>();
    public final MutableLiveData<String> message =
            new MutableLiveData<>(
                    "Điểm thử cố định tại TP.HCM. Chạm để chọn đích; nhấn giữ để đổi điểm xuất"
                        + " phát.");
    public final MutableLiveData<String> roomMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> roomBusy = new MutableLiveData<>(false);
    public final MutableLiveData<String> googleUrl = new MutableLiveData<>();
    private final List<RoomListing> loaded = new ArrayList<>();
    private String cursor, query = "", openedRoomId;
    private boolean started, hasMore = true, fallback, locating;
    private int request;
    private RoomListing selectedRoom;

    public MapViewModel(@NonNull Application app) {
        this(app, new MapRepository());
    }

    MapViewModel(Application app, MapRepository maps) {
        super(app);
        this.maps = maps;
        locations = new LocationRepository(app);
    }

    public void openRoom(String roomId) {
        if (roomId == null || roomId.equals(openedRoomId)) return;
        openedRoomId = roomId;
        rooms.detail(roomId, new ResultCallback<RoomListing>() {
            public void onSuccess(RoomListing room) {
                MapPoint target = point(room);
                if (target == null) { message.setValue("Phòng chưa có tọa độ để chỉ đường."); return; }
                select(target, room);
                directions();
            }
            public void onError(Exception error) { message.setValue("Chưa tải được địa điểm của phòng. Hãy thử lại."); }
        });
    }
    public String styleUrl() {
        return maps.styleUrl();
    }

    public void start() {
        if (!started) {
            started = true;
            loadMore();
        }
    }

    public void allowRetry() {
        fallback = false;
        googleUrl.setValue(null);
    }

    public boolean hasMore() {
        return hasMore;
    }

    public RoomListing selectedRoom() {
        return selectedRoom;
    }

    public void setOrigin(MapPoint point) {
        if (!MapArea.contains(point)) {
            message.setValue("Bản thử chỉ hỗ trợ khu vực TP.HCM. Điểm xuất phát được giữ nguyên.");
            return;
        }
        allowRetry();
        request++;
        busy.setValue(false);
        locations.cancel();
        locating = false;
        selectedRoom = null;
        origin.setValue(point);
        destination.setValue(null);
        route.setValue(null);
        filter();
        message.setValue(
                "Đã chọn điểm xuất phát. Chọn văn phòng hoặc chạm bản đồ để thử tuyến đường.");
    }

    public void select(MapPoint point, RoomListing room) {
        if (!MapArea.contains(point)) {
            message.setValue("Hãy chọn đích trong khu vực thử TP.HCM.");
            return;
        }
        allowRetry();
        request++;
        busy.setValue(false);
        locations.cancel();
        locating = false;
        selectedRoom = room;
        destination.setValue(point);
        route.setValue(null);
        message.setValue(
                room == null
                        ? "Đích chọn trên bản đồ; chưa phải văn phòng đăng trên hệ thống."
                        : "Đã chọn văn phòng: " + point.name);
    }

    public MapPoint point(RoomListing room) {
        com.google.firebase.firestore.GeoPoint location = room.building.getLocation();
        return location == null
                ? null
                : new MapPoint(
                        location.getLatitude(), location.getLongitude(), room.room.getRoomName());
    }

    public void loadMore() {
        if (Boolean.TRUE.equals(roomBusy.getValue()) || !hasMore) return;
        roomBusy.setValue(true);
        roomMessage.setValue("Đang tải văn phòng công khai…");
        rooms.page(
                cursor,
                new ResultCallback<RoomRepository.Page>() {
                    public void onSuccess(RoomRepository.Page page) {
                        loaded.addAll(page.items);
                        cursor = page.cursor;
                        hasMore = page.hasMore;
                        roomBusy.setValue(false);
                        filter();
                    }

                    public void onError(Exception error) {
                        roomBusy.setValue(false);
                        roomMessage.setValue(
                                "Chưa tải được văn phòng. Kiểm tra kết nối dịch vụ rồi thử tải lại;"
                                    + " vẫn có thể thử bản đồ và tuyến đường.");
                    }
                });
    }

    private void filter() {
        List<RoomListing> found = new ArrayList<>();
        int missing = 0;
        for (RoomListing room : loaded) {
            MapPoint point = point(room);
            if (point == null) {
                missing++;
                continue;
            }
            if (MapArea.nearby(origin.getValue(), point)) found.add(room);
        }
        found.sort(
                Comparator.comparingDouble(
                        room -> MapArea.distance(origin.getValue(), point(room))));
        nearby.setValue(found);
        roomMessage.setValue(
                found.size()
                        + " văn phòng trong 3 km / "
                        + loaded.size()
                        + " phòng đã tải"
                        + (missing > 0 ? " · " + missing + " thiếu tọa độ" : "")
                        + (hasMore ? " · Có thể tải tiếp" : ""));
    }

    public void search(String text) {
        if (text == null || text.trim().length() < 3 || text.trim().length() > 200) {
            message.setValue("Nhập địa chỉ từ 3 đến 200 ký tự tại TP.HCM.");
            return;
        }
        locations.cancel();
        locating = false;
        allowRetry();
        query = text.trim();
        final int id = ++request;
        busy.setValue(true);
        message.setValue("Đang tìm địa chỉ tại TP.HCM…");
        maps.geocode(
                query,
                new ResultCallback<List<MapPoint>>() {
                    public void onSuccess(List<MapPoint> points) {
                        if (id != request) return;
                        busy.setValue(false);
                        if (points.isEmpty()) {
                            message.setValue("Không có địa chỉ phù hợp trong khu vực thử TP.HCM.");
                            return;
                        }
                        if (points.size() == 1) setOrigin(points.get(0));
                        else candidates.setValue(points);
                    }

                    public void onError(Exception e) {
                        if (id == request) {
                            busy.setValue(false);
                            fallbackSearch();
                        }
                    }
                });
    }

    public void gps() {
        allowRetry();
        locating = true;
        final int id = ++request;
        busy.setValue(true);
        message.setValue("Đang lấy vị trí thiết bị…");
        locations.current(
                new ResultCallback<MapPoint>() {
                    public void onSuccess(MapPoint point) {
                        if (id == request) {
                            locating = false;
                            busy.setValue(false);
                            setOrigin(point);
                        }
                    }

                    public void onError(Exception e) {
                        if (id == request) {
                            locating = false;
                            busy.setValue(false);
                            message.setValue(
                                    e.getMessage()
                                            + " Có thể nhấn giữ bản đồ để chọn điểm tại TP.HCM.");
                        }
                    }
                });
    }

    public void directions() {
        if (destination.getValue() == null || Boolean.TRUE.equals(busy.getValue())) return;
        allowRetry();
        final int id = ++request;
        busy.setValue(true);
        route.setValue(null);
        if (MapArea.distance(origin.getValue(), destination.getValue()) < 5) {
            busy.setValue(false);
            message.setValue("Điểm xuất phát trùng điểm đến; không cần tính tuyến đường.");
            return;
        }
        message.setValue("Đang tính tuyến ô tô…");
        maps.route(
                origin.getValue(),
                destination.getValue(),
                new ResultCallback<MapRoute>() {
                    public void onSuccess(MapRoute value) {
                        if (id != request) return;
                        busy.setValue(false);
                        route.setValue(value);
                        message.setValue(
                                String.format(
                                        Locale.getDefault(),
                                        "Tuyến ô tô: %.2f km · khoảng %.0f phút (ước tính, không có"
                                            + " giao thông trực tiếp)",
                                        value.distanceMeters / 1000,
                                        Math.ceil(value.durationSeconds / 60)));
                    }

                    public void onError(Exception e) {
                        if (id == request) {
                            busy.setValue(false);
                            mapFailed();
                        }
                    }
                });
    }

    private void fallbackSearch() {
        if (fallback) return;
        fallback = true;
        message.setValue("Dịch vụ tìm địa chỉ gặp lỗi. Đang mở Google Maps…");
        googleUrl.setValue(GoogleMapsLink.search(origin.getValue(), query));
    }

    public void mapFailed() {
        if (fallback) return;
        fallback = true;
        message.setValue("Dịch vụ bản đồ gặp lỗi. Đang mở Google Maps…");
        googleUrl.setValue(
                destination.getValue() == null
                        ? GoogleMapsLink.search(origin.getValue(), "")
                        : GoogleMapsLink.route(origin.getValue(), destination.getValue()));
    }

    public void stopLocation() {
        locations.cancel();
        if (locating) {
            locating = false;
            request++;
            busy.setValue(false);
            message.setValue("Đã dừng lấy vị trí khi rời màn hình.");
        }
    }

    @Override
    protected void onCleared() {
        request++;
        locations.cancel();
        maps.close();
    }
}
