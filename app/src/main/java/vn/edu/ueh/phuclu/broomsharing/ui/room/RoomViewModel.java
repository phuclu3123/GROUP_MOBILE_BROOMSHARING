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
        if (rooms.getValue() == null) load();
    }

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
                    result.add(item.withAvailability(calendar == null
                            ? FixedRentalBadge.unknown(!calendarErrors.contains(item.room.getId()))
                            : FixedRentalBadge.from(item.room, calendar.windows, calendar.until, System.currentTimeMillis())));
                } else result.add(item);
            }
        if (sort > 0) result.sort(RoomFilter.order(sort));
        rooms.setValue(UiState.success(result));
    }

    public void load() {
        if (!loading) fetch(true);
    }

    public void more() {
        if (!loading && hasMore) fetch(false);
    }

    private void fetch(boolean refresh) {
        loading = true;
        final int token = ++request;
        rooms.setValue(UiState.loading());
        repository.page(
                refresh ? null : cursor,
                new ResultCallback<RoomRepository.Page>() {
                    public void onSuccess(RoomRepository.Page page) {
                        if (token != request) return;
                        if (refresh) { stopCalendarListeners(); loaded.clear(); }
                        for (RoomListing item : page.items) loaded.put(item.room.getId(), item);
                        cursor = page.cursor;
                        hasMore = page.hasMore;
                        loading = false;
                        watchCalendars();
                        if ((hasFilters() || loaded.isEmpty()) && hasMore) { fetch(false); return; }
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

    private void watchCalendars() {
        if (!calendarsActive) return;
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

    public void pauseCalendars() {
        calendarsActive = false;
        handler.removeCallbacks(tick);
        stopCalendarListeners();
    }

    public void ensureDetail(String id) {
        if (!Objects.equals(id, detailId) || detail.getValue() == null) load(id);
    }

    public void load(String id) {
        detailId = id;
        detail.setValue(UiState.loading());
        repository.detail(
                id,
                new ResultCallback<RoomListing>() {
                    public void onSuccess(RoomListing value) {
                        if (Objects.equals(id, detailId)) detail.setValue(UiState.success(value));
                    }

                    public void onError(Exception e) {
                        if (Objects.equals(id, detailId)) detail.setValue(UiState.error(e));
                    }
                });
    }

    @Override
    protected void onCleared() {
        pauseCalendars();
        request++;
        detailId = null;
    }
}
