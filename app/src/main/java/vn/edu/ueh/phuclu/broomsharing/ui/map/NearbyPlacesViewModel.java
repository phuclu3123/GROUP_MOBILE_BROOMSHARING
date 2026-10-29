package vn.edu.ueh.phuclu.broomsharing.ui.map;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import vn.edu.ueh.phuclu.broomsharing.model.MapPoint;
import vn.edu.ueh.phuclu.broomsharing.model.NearbyPlace;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.repository.NearbyPlacesRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;

import java.util.List;

/**
 * Quản lý trạng thái tìm các địa điểm lân cận.
 * Gọi NearbyPlacesRepository và trả dữ liệu hoặc lỗi qua LiveData.
 */
public class NearbyPlacesViewModel extends ViewModel {
    private final NearbyPlacesRepository repository = new NearbyPlacesRepository();
    public final MutableLiveData<UiState<List<NearbyPlace>>> places = new MutableLiveData<>();
    private boolean closed;
    public boolean fallbackOpened;

    public void load(MapPoint center) {
        // Đang tìm địa điểm thì bỏ qua thao tác nhấn tải lặp.
        if (places.getValue() != null && places.getValue().loading) return;
        fallbackOpened = false;
        places.setValue(UiState.loading());

        repository.nearby(
                center,
                new ResultCallback<List<NearbyPlace>>() {
                    public void onSuccess(List<NearbyPlace> p) {
                        if (!closed) places.setValue(UiState.success(p));
                    }

                    public void onError(Exception e) {
                        if (!closed) places.setValue(UiState.error(e));
                    }
                });
    }

    // Chỉ tải lần đầu; khi Activity được tạo lại, dùng trạng thái đang có.
    public void ensure(MapPoint center) {
        if (places.getValue() == null) load(center);
    }

    @Override
    protected void onCleared() {
        closed = true;
        repository.close();
    }
}
