package vn.edu.ueh.phuclu.broomsharing.ui.favorites;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import vn.edu.ueh.phuclu.broomsharing.model.Favorite;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.repository.FavoritesRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;

import java.util.List;

/**
 * Quản lý danh sách phòng yêu thích và kết quả thêm hoặc bỏ lưu.
 * Gọi FavoritesRepository để đọc và cập nhật dữ liệu.
 */
public class FavoritesViewModel extends ViewModel {
    private final FavoritesRepository repository = new FavoritesRepository();
    public final MutableLiveData<UiState<List<Favorite>>> items = new MutableLiveData<>();
    public final MutableLiveData<UiState<Boolean>> saved = new MutableLiveData<>();

    public boolean busy() {
        return saved.getValue() != null && saved.getValue().loading;
    }

    // Kiểm tra phòng đã nằm trong danh sách yêu thích đang tải hay chưa.
    public boolean contains(String id) {
        if (id == null) return false;
        UiState<List<Favorite>> state = items.getValue();
        if (state != null && state.data != null)
            for (Favorite item : state.data) if (id.equals(item.roomId)) return true;
        return false;
    }

    public void load() {
        items.setValue(UiState.loading());
        repository.load(
                new ResultCallback<List<Favorite>>() {
                    public void onSuccess(List<Favorite> value) {
                        items.setValue(UiState.success(value));
                    }

                    public void onError(Exception e) {
                        items.setValue(UiState.error(e));
                    }
                });
    }

    // Cập nhật yêu thích; tải lại danh sách sau khi Firebase xác nhận thành công.
    public void set(String id, String name, boolean value) {
        if (busy()) return;
        saved.setValue(UiState.loading());
        repository.set(
                id,
                name,
                value,
                new ResultCallback<Boolean>() {
                    public void onSuccess(Boolean v) {
                        saved.setValue(UiState.success(v));
                        load();
                    }

                    public void onError(Exception e) {
                        saved.setValue(UiState.error(e));
                    }
                });
    }
}
