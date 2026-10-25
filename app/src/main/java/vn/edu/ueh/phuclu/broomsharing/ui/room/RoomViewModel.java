package vn.edu.ueh.phuclu.broomsharing.ui.room;

import androidx.lifecycle.*;

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
    }

    private void publish() {
        List<RoomListing> result = new ArrayList<>();
        for (RoomListing item : loaded.values())
            if (RoomFilter.matches(item, search, area, type, mode)
                    && item.room.getCapacity() >= minCapacity
                    && (maxPrice <= 0 || RoomFilter.unitPrice(item) <= maxPrice)) result.add(item);
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
                        if (refresh) loaded.clear();
                        for (RoomListing item : page.items) loaded.put(item.room.getId(), item);
                        cursor = page.cursor;
                        hasMore = page.hasMore;
                        loading = false;
                        publish();
                    }

                    public void onError(Exception error) {
                        if (token != request) return;
                        loading = false;
                        rooms.setValue(UiState.error(error));
                    }
                });
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
        request++;
        detailId = null;
    }
}
