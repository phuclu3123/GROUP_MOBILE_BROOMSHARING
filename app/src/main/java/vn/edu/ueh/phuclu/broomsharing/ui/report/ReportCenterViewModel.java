package vn.edu.ueh.phuclu.broomsharing.ui.report;

import androidx.lifecycle.*;

import com.google.firebase.firestore.ListenerRegistration;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

import java.util.List;

// Giữ chế độ xem và chặn callback của tab cũ cập nhật tab mới.
/*
   Tách báo cáo đã gửi và báo cáo nhận về tài sản của Host.
   Generation loại callback cũ khi đổi tab; thao tác phản hồi không tự đóng báo cáo.
*/
public class ReportCenterViewModel extends ViewModel {
    private final ReportRepository repository = new ReportRepository();
    private ListenerRegistration listener;
    private int generation;
    public boolean received;
    public final MutableLiveData<UiState<List<RoomReport>>> rows = new MutableLiveData<>();
    public final MutableLiveData<UiState<String>> action = new MutableLiveData<>();

    public void watch() {
        stop();
        int run = generation;
        rows.setValue(UiState.loading());
        try {
            listener =
                    repository.watch(
                            received,
                            new ResultCallback<List<RoomReport>>() {
                                public void onSuccess(List<RoomReport> value) {
                                    if (run == generation) rows.setValue(UiState.success(value));
                                }

                                public void onError(Exception e) {
                                    if (run == generation) rows.setValue(UiState.error(e));
                                }
                            });
        } catch (Exception e) {
            rows.setValue(UiState.error(e));
        }
    }

    public void reply(String id, String text) {
        if (action.getValue() != null && action.getValue().loading) return;
        action.setValue(UiState.loading());
        repository.reply(
                id,
                text,
                new ResultCallback<String>() {
                    public void onSuccess(String value) {
                        action.setValue(UiState.success(value));
                    }

                    public void onError(Exception e) {
                        action.setValue(UiState.error(e));
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
