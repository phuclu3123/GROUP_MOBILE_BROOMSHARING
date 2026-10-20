package vn.edu.ueh.phuclu.broomsharing.ui;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

import java.util.*;

public class BookingViewModel extends ViewModel {
    private final BookingRepository repository = new BookingRepository();
    public final MutableLiveData<UiState<Room>> room = new MutableLiveData<>();
    public final MutableLiveData<UiState<Booking>> quote = new MutableLiveData<>();
    public final MutableLiveData<UiState<String>> saved = new MutableLiveData<>();
    public final MutableLiveData<UiState<List<Booking>>> bookings = new MutableLiveData<>();
    public String requestKey = UUID.randomUUID().toString();
    public String lastRequest = "";
    public final MutableLiveData<UiState<List<Booking>>> availability = new MutableLiveData<>();
    public final MutableLiveData<UiState<Booking>> detail = new MutableLiveData<>();
    public int filterPosition;
    private int availabilityRequest;
    private com.google.firebase.firestore.ListenerRegistration watcher;
    private com.google.firebase.firestore.ListenerRegistration detailWatcher;
    public void watchDetail(String id) {
        stopDetail();detail.setValue(UiState.loading());
        detailWatcher=repository.watchOne(id,new ResultCallback<Booking>(){
            public void onSuccess(Booking value){detail.setValue(UiState.success(value));}
            public void onError(Exception e){detail.setValue(UiState.error(e));}
        });
    }
    public void stopDetail(){if(detailWatcher!=null)detailWatcher.remove();detailWatcher=null;}

    public void loadDetail(String id) {
        detail.setValue(UiState.loading());
        repository.get(
                id,
                new ResultCallback<Booking>() {
                    public void onSuccess(Booking value) {
                        detail.setValue(UiState.success(value));
                    }

                    public void onError(Exception e) {
                        detail.setValue(UiState.error(e));
                    }
                });
    }

    public void loadAvailability(String roomId, long from, long to) {
        final int token = ++availabilityRequest;
        availability.setValue(UiState.loading());
        repository.availability(
                roomId,
                from,
                to,
                new ResultCallback<List<Booking>>() {
                    public void onSuccess(List<Booking> value) {
                        if (token == availabilityRequest)
                            availability.setValue(UiState.success(value));
                    }

                    public void onError(Exception e) {
                        if (token == availabilityRequest) availability.setValue(UiState.error(e));
                    }
                });
    }

    public void watchMine(String uid) {
        stopWatching();
        bookings.setValue(UiState.loading());
        watcher =
                repository.watchMine(
                        uid,
                        new ResultCallback<List<Booking>>() {
                            public void onSuccess(List<Booking> value) {
                                bookings.setValue(UiState.success(value));
                            }

                            public void onError(Exception e) {
                                bookings.setValue(UiState.error(e));
                            }
                        });
    }

    public void stopWatching() {
        if (watcher != null) {
            watcher.remove();
            watcher = null;
        }
    }

    @Override
    protected void onCleared() {
        stopWatching();
        stopDetail();
        availabilityRequest++;
    }

    public boolean busy() {
        return saved.getValue() != null && saved.getValue().loading
                || quote.getValue() != null && quote.getValue().loading;
    }

    public void loadRoom(String roomId) {
        room.setValue(UiState.loading());
        new RoomRepository()
                .room(
                        roomId,
                        new ResultCallback<Room>() {
                            public void onSuccess(Room value) {
                                room.setValue(UiState.success(value));
                            }

                            public void onError(Exception e) {
                                room.setValue(UiState.error(e));
                            }
                        });
    }

    public Booking request(String roomId, long start, long end, int months) {
        Booking data = new Booking();
        data.roomId = roomId;
        data.startAt = new Date(start);
        data.endAt = end == 0 ? null : new Date(end);
        data.rentalMonths = months;
        String signature = roomId + ":" + start + ":" + end + ":" + months;
        if (!lastRequest.isEmpty() && !lastRequest.equals(signature))
            requestKey = UUID.randomUUID().toString();
        lastRequest = signature;
        data.requestKey = requestKey;
        return data;
    }

    public void quote(Booking request) {
        if (busy()) return;
        quote.setValue(UiState.loading());
        repository.quote(
                request,
                new ResultCallback<Booking>() {
                    public void onSuccess(Booking value) {
                        quote.setValue(UiState.success(value));
                    }

                    public void onError(Exception e) {
                        quote.setValue(UiState.error(e));
                    }
                });
    }

    public void create(Booking request) {
        if (busy()) return;
        saved.setValue(UiState.loading());
        repository.create(
                request,
                new ResultCallback<String>() {
                    public void onSuccess(String value) {
                        saved.setValue(UiState.success(value));
                    }

                    public void onError(Exception e) {
                        saved.setValue(UiState.error(e));
                    }
                });
    }

    public void loadMine(String uid) {
        bookings.setValue(UiState.loading());
        repository.mine(
                uid,
                new ResultCallback<List<Booking>>() {
                    public void onSuccess(List<Booking> list) {
                        bookings.setValue(UiState.success(list));
                    }

                    public void onError(Exception e) {
                        bookings.setValue(UiState.error(e));
                    }
                });
    }

    public void cancel(String bookingId, String uid) {
        cancel(bookingId, uid, null);
    }
    public void cancel(String bookingId, String uid, Integer expectedPercent) {
        if (busy()) return;
        saved.setValue(UiState.loading());
        repository.transition(
                bookingId,
                Booking.STATUS_CANCELLED,
                "",
                expectedPercent,
                new ResultCallback<String>() {
                    public void onSuccess(String id) {
                        saved.setValue(UiState.success(id));
                        loadMine(uid);
                    }

                    public void onError(Exception e) {
                        saved.setValue(UiState.error(e));
                    }
                });
    }
}
