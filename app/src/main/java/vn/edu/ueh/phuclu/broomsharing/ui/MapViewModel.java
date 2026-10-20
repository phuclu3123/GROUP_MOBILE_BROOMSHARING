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
    public final MutableLiveData<List<RentalPlace>> referenceNearby = new MutableLiveData<>(Collections.emptyList());
    public final MutableLiveData<String> message =
            new MutableLiveData<>(
                    "Chọn vị trí, nhập địa chỉ hoặc chọn điểm trên bản đồ để tìm trong 3 km.");
    public final MutableLiveData<String> roomMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> roomBusy = new MutableLiveData<>(false);
    public final MutableLiveData<String> googleUrl = new MutableLiveData<>();
    private final List<RoomListing> loaded = new ArrayList<>();
    private final List<RentalPlace> references = new ArrayList<>();
    private RentalPlace selectedReference;
    private boolean roomsFailed, referencesFailed;
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
            loadReferences();
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
    public RentalPlace selectedReference(){return selectedReference;}
    public boolean loadFailed(){return roomsFailed||referencesFailed;}
    public void refreshPlaces(){
        if(Boolean.TRUE.equals(roomBusy.getValue()))return;
        cursor=null;hasMore=true;loaded.clear();references.clear();
        roomsFailed=referencesFailed=false;loadMore();loadReferences();
    }
    private void loadReferences(){
        rooms.referencePlaces(new ResultCallback<List<RentalPlace>>(){
            public void onSuccess(List<RentalPlace> values){referencesFailed=false;references.clear();references.addAll(values);filter();}
            public void onError(Exception e){referencesFailed=true;filter();}
        });
    }
    public MapPoint point(RentalPlace p){return new MapPoint(p.location.getLatitude(),p.location.getLongitude(),p.name);}
    public void selectReference(RentalPlace p){
        select(point(p),null);selectedReference=p;destination.setValue(point(p));directions();
    }

    public void setOrigin(MapPoint point) {
        if (!MapArea.contains(point)) {
            message.setValue("Vị trí nằm ngoài khu vực TP.HCM đang hỗ trợ. Hãy chọn điểm khác trên bản đồ.");
            return;
        }
        allowRetry();
        request++;
        busy.setValue(false);
        locations.cancel();
        locating = false;
        selectedRoom = null;
        selectedReference=null;
        origin.setValue(point);
        destination.setValue(null);
        route.setValue(null);
        filter();
        message.setValue(
                "Đã đổi tâm tìm kiếm. Chọn một địa điểm để xem tuyến đường.");
    }

    public void select(MapPoint point, RoomListing room) {
        if (!MapArea.contains(point)) {
            message.setValue("Hãy chọn đích trong khu vực TP.HCM đang hỗ trợ.");
            return;
        }
        allowRetry();
        request++;
        busy.setValue(false);
        locations.cancel();
        locating = false;
        selectedRoom = room;
        selectedReference=null;
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
                        roomsFailed=false;
                        loaded.addAll(page.items);
                        cursor = page.cursor;
                        hasMore = page.hasMore;
                        roomBusy.setValue(false);
                        filter();
                        if(hasMore)loadMore();
                    }

                    public void onError(Exception error) {
                        roomBusy.setValue(false);
                        roomsFailed=true;filter();
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
        List<RentalPlace> publicFound=new ArrayList<>();
        for(RentalPlace p:references)if(MapArea.nearby(origin.getValue(),point(p)))publicFound.add(p);
        publicFound.sort(Comparator.comparingDouble(p->MapArea.distance(origin.getValue(),point(p))));
        referenceNearby.setValue(publicFound);
        roomMessage.setValue(
                found.size()
                        + " không gian · "+publicFound.size()+" tham khảo trong 3 km"
                        + (hasMore ? " · Đang tải tiếp" : "")
                        + (loadFailed()?" · Chưa tải đủ dữ liệu, hãy thử lại":""));
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
                origin.getValue(),
                new ResultCallback<List<MapPoint>>() {
                    public void onSuccess(List<MapPoint> points) {
                        if (id != request) return;
                        busy.setValue(false);
                        if (points.isEmpty()) {
                            message.setValue("Chưa tìm thấy địa chỉ phù hợp. Thêm tên đường, khu vực hoặc chọn điểm trên bản đồ.");
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
