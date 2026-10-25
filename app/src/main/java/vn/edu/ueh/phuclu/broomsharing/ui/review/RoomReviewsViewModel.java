package vn.edu.ueh.phuclu.broomsharing.ui.review;

import androidx.lifecycle.*;

import com.google.firebase.firestore.ListenerRegistration;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

import java.util.List;

// Giữ kết quả gửi khi xoay màn hình, và chỉ nghe danh sách khi View đang hiển thị.
/*
   Giữ trạng thái xem/gửi nhận xét khi xoay màn hình.
   Không gửi lại khi đang xử lý; callback listener cũ bị loại khi View ngừng quan sát.
*/
public class RoomReviewsViewModel extends ViewModel {
    private final ReviewRepository repository = new ReviewRepository();
    private ListenerRegistration listener;
    private int generation;
    public final MutableLiveData<UiState<List<RoomReview>>> rows = new MutableLiveData<>();
    public final MutableLiveData<UiState<String>> saved = new MutableLiveData<>();

    public void watch(String roomId) {
        stop();
        rows.setValue(UiState.loading());
        final int request = generation;
        try {
            listener =
                    repository.watch(
                            roomId,
                            new ResultCallback<List<RoomReview>>() {
                                public void onSuccess(List<RoomReview> value) {
                                    if (request == generation)
                                        rows.setValue(UiState.success(value));
                                }

                                public void onError(Exception e) {
                                    if (request == generation) rows.setValue(UiState.error(e));
                                }
                            });
        } catch (Exception e) {
            rows.setValue(UiState.error(e));
        }
    }

    public void submit(String bookingId, int rating, String comment) {
        if (saved.getValue() != null && saved.getValue().loading) return;
        saved.setValue(UiState.loading());
        repository.submit(
                bookingId,
                rating,
                comment,
                new ResultCallback<String>() {
                    public void onSuccess(String id) {
                        saved.setValue(UiState.success(id));
                    }

                    public void onError(Exception e) {
                        saved.setValue(UiState.error(e));
                    }
                });
    }

    public void stop() {
        generation++;
        if (listener != null) listener.remove();
        listener = null;
    }

    @Override
    protected void onCleared() {
        stop();
    }
}
