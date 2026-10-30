package vn.edu.ueh.phuclu.broomsharing.ui.room;

import androidx.lifecycle.*;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.firestore.ListenerRegistration;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.repository.PublicCalendarRepository;
import vn.edu.ueh.phuclu.broomsharing.utils.FixedRentalBadge;

import vn.edu.ueh.phuclu.broomsharing.model.RoomListing;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;
import vn.edu.ueh.phuclu.broomsharing.repository.RoomRepository;
import vn.edu.ueh.phuclu.broomsharing.utils.RoomFilter;
import vn.edu.ueh.phuclu.broomsharing.repository.OfflineRepository;
import vn.edu.ueh.phuclu.broomsharing.data.local.OfflineDatabase;

import java.util.*;

/**
 * Quản lý danh sách phòng, bộ lọc và trạng thái tải của trang chủ.
 * Gọi RoomRepository để lấy dữ liệu cho giao diện.
 */
/*
    Quản lý danh sách phòng, phân trang, bộ lọc và trạng thái chi tiết.
    View quan sát kết quả; Firebase và việc ghép dữ liệu nằm trong Repository.
 */
public class RoomViewModel extends ViewModel {
    private final RoomRepository repository = new RoomRepository();
    private final LinkedHashMap<String, RoomListing> loaded = new LinkedHashMap<>();
    private final MutableLiveData<UiState<List<RoomListing>>> rooms = new MutableLiveData<>();
    private final MutableLiveData<UiState<RoomListing>> detail = new MutableLiveData<>();

    public LiveData<UiState<List<RoomListing>>> rooms() {
        return rooms;
    }

    public LiveData<UiState<RoomListing>> detail() {
        return detail;
    }

    public String search = "", area = "", type = "", mode = "";
    public int minCapacity, sort;
    public long maxPrice;
    private String cursor, detailId;
    private boolean loading, hasMore = true;
    private int request;
    private final PublicCalendarRepository calendarRepository = new PublicCalendarRepository();
    private final Map<String, ListenerRegistration> calendarListeners = new HashMap<>();
    private final Map<String, PublicCalendarRepository.Calendar> calendars = new HashMap<>();
    private final Set<String> calendarErrors = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean calendarsActive;
    private long offlineSavedAt;
    private int detailRequest;
    private int calendarGeneration;
    private final Runnable tick = new Runnable() {
        public void run() { if (calendarsActive) { publish(); handler.postDelayed(this, 60000); } }
    };

    public boolean hasMore() {
        return hasMore;
    }

    public boolean isLoading() {
        return loading;
    }

    public int loadedCount() {
        return loaded.size();
    }

    public void ensureLoaded() {
        UiState<List<RoomListing>> current = rooms.getValue();
        if (current == null || current.offline == OfflineRepository.online()) load();
    }

    // Lưu bộ lọc và tải tiếp các trang cần thiết để tìm kiếm không chỉ giới hạn ở trang đầu.
    public void filter(String search, String area, String type, String mode) {
        this.search = search;
        this.area = area;
        this.type = type;
        this.mode = mode;
        publish();
        // Tìm kiếm/lọc toàn bộ tập phòng công khai, không chỉ trang đầu đã tải.
        if (hasFilters() && hasMore && !loading) fetch(false);
    }

    private boolean hasFilters() {
        return !search.trim().isEmpty() || !area.trim().isEmpty() || !type.isEmpty()
                || !mode.isEmpty() || minCapacity > 0 || maxPrice > 0 || sort > 0;
    }

    private void publish() {
        List<RoomListing> result = new ArrayList<>();
        for (RoomListing item : loaded.values())
            if (RoomFilter.matches(item, search, area, type, mode)
                    && item.room.getCapacity() >= minCapacity
                    && (maxPrice <= 0 || RoomFilter.unitPrice(item) <= maxPrice)) {
                if (Room.MODE_FIXED.equals(item.room.getActiveMode())) {
                    PublicCalendarRepository.Calendar calendar = calendars.get(item.room.getId());
                    result.add(item.withAvailability(offlineSavedAt != 0
                            ? new FixedRentalBadge("Ngoại tuyến", "Kết nối mạng để kiểm tra kỳ thuê.", 0)
                            : calendar == null
                            ? FixedRentalBadge.unknown(!calendarErrors.contains(item.room.getId()))
                            : FixedRentalBadge.from(item.room, calendar.windows, calendar.until, System.currentTimeMillis())));
                } else result.add(item);
            }
        if (sort > 0) result.sort(RoomFilter.order(sort));
        rooms.setValue(offlineSavedAt == 0 ? UiState.success(result) : UiState.cached(result, offlineSavedAt));
    }

    public void load() {
        if (!loading) fetch(true);
    }

    public void more() {
        if (!loading && hasMore) fetch(false);
    }

    // Đọc trang kế tiếp qua Repository, quản lý cursor và cờ đang tải để tránh request trùng.
    private void fetch(boolean refresh) {
        loading = true;
        final int token = ++request;
        rooms.setValue(UiState.loading());
        if (!OfflineRepository.online()) { readSavedRooms(token); return; }
        repository.page(
                refresh ? null : cursor,
                new ResultCallback<RoomRepository.Page>() {
                    public void onSuccess(RoomRepository.Page page) {
                        if (token != request) return;
                        offlineSavedAt = 0;
                        if (refresh) { stopCalendarListeners(); loaded.clear(); }
                        for (RoomListing item : page.items) loaded.put(item.room.getId(), item);
                        cursor = page.cursor;
                        hasMore = page.hasMore;
                        loading = false;
                        OfflineRepository.saveRooms(loaded.values());
                        watchCalendars();
                        if ((hasFilters() || loaded.isEmpty()) && hasMore) { fetch(false); return; }
                        publish();
                    }

                    public void onError(Exception error) {
                        if (token != request) return;
                        if (OfflineRepository.networkFailure(error)) { readSavedRooms(token); return; }
                        loading = false;
                        rooms.setValue(UiState.error(error));
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void readSavedRooms(int token) {
        OfflineRepository.read("rooms", "list", ArrayList.class,
                new ResultCallback<OfflineDatabase.Saved<ArrayList>>() {
                    public void onSuccess(OfflineDatabase.Saved<ArrayList> saved) {
                        if (token != request) return;
                        stopCalendarListeners(); loaded.clear();
                        for (Object row : saved.data) if (row instanceof RoomListing) {
                            RoomListing item = (RoomListing) row;
                            loaded.put(item.room.getId(), item);
                        }
                        offlineSavedAt = saved.savedAt;
                        hasMore = false; loading = false;
                        publish();
                    }
                    public void onError(Exception error) {
                        if (token != request) return;
                        loading = false;
                        rooms.setValue(UiState.error(error));
                    }
                });
    }

    public void resumeCalendars() {
        if (calendarsActive) return;
        calendarsActive = true;
        watchCalendars();
        handler.post(tick);
    }

    // Đăng ký lịch công khai cho các phòng cần hiển thị nhãn tháng/quý, không đọc chi tiết khách khác.
    private void watchCalendars() {
        if (!calendarsActive || offlineSavedAt != 0 || !OfflineRepository.online()) return;
        final int run = calendarGeneration;
        for (RoomListing listing : loaded.values()) {
            String id = listing.room.getId();
            if (!Room.MODE_FIXED.equals(listing.room.getActiveMode()) || calendarListeners.containsKey(id)) continue;
            calendarListeners.put(id, calendarRepository.watch(listing.room, new ResultCallback<PublicCalendarRepository.Calendar>() {
                public void onSuccess(PublicCalendarRepository.Calendar value) {
                    if (run != calendarGeneration) return;
                    calendars.put(id, value); calendarErrors.remove(id); publish();
                }
                public void onError(Exception error) {
                    if (run != calendarGeneration) return;
                    calendars.remove(id); calendarErrors.add(id); publish();
                }
            }));
        }
    }

    private void stopCalendarListeners() {
        ++calendarGeneration;
        for (ListenerRegistration listener : calendarListeners.values()) listener.remove();
        calendarListeners.clear(); calendars.clear(); calendarErrors.clear();
    }

    // Ngừng nguồn lịch khi rời màn hình; quay lại sẽ nghe dữ liệu hiện tại thay vì dùng nhãn cũ mãi.
    public void pauseCalendars() {
        calendarsActive = false;
        handler.removeCallbacks(tick);
        stopCalendarListeners();
    }

    public void ensureDetail(String id) {
        UiState<RoomListing> current = detail.getValue();
        if (!Objects.equals(id, detailId) || current == null || current.offline == OfflineRepository.online()) load(id);
    }

    public void load(String id) {
        detailId = id;
        final int token = ++detailRequest;
        detail.setValue(UiState.loading());
        if (!OfflineRepository.online()) { readSavedDetail(id, token); return; }
        repository.detail(
                id,
                new ResultCallback<RoomListing>() {
                    public void onSuccess(RoomListing value) {
                        if (Objects.equals(id, detailId) && token == detailRequest) {
                            OfflineRepository.saveRoom(value);
                            detail.setValue(UiState.success(value));
                        }
                    }

                    public void onError(Exception e) {
                        if (Objects.equals(id, detailId) && token == detailRequest) {
                            if (OfflineRepository.networkFailure(e)) readSavedDetail(id, token);
                            else detail.setValue(UiState.error(e));
                        }
                    }
                });
    }

    private void readSavedDetail(String id, int token) {
        OfflineRepository.read("room", id, RoomListing.class,
                new ResultCallback<OfflineDatabase.Saved<RoomListing>>() {
                    public void onSuccess(OfflineDatabase.Saved<RoomListing> saved) {
                        if (token == detailRequest) detail.setValue(UiState.cached(saved.data, saved.savedAt));
                    }
                    public void onError(Exception error) {
                        if (token == detailRequest) detail.setValue(UiState.error(error));
                    }
                });
    }

    @Override
    protected void onCleared() {
        pauseCalendars();
        request++;
        detailId = null;
        detailRequest++;
    }
}
