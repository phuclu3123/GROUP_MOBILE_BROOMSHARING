package vn.edu.ueh.phuclu.broomsharing.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.repository.BookingRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;

import java.util.List;

public class BookingViewModel extends ViewModel {

    private final BookingRepository repository = new BookingRepository();

    private final MutableLiveData<UiState<String>> submission =
            new MutableLiveData<>();

    private final MutableLiveData<UiState<List<Booking>>> bookings =
            new MutableLiveData<>();

    private final MutableLiveData<UiState<String>> cancellation =
            new MutableLiveData<>();

    private ListenerRegistration watchRegistration;
    private boolean submitting;

    public LiveData<UiState<String>> submission() {
        return submission;
    }

    public LiveData<UiState<List<Booking>>> bookings() {
        return bookings;
    }

    public LiveData<UiState<String>> cancellation() {
        return cancellation;
    }

    public boolean isSubmitting() {
        return submitting;
    }

    public void create(
            String requestKey,
            String roomId,
            String mode,
            java.util.Date startAt,
            java.util.Date endAt,
            String unitLabel) {

        if (submitting) {
            return;
        }

        submitting = true;
        submission.setValue(UiState.loading());

        repository.create(
                requestKey,
                roomId,
                mode,
                startAt,
                endAt,
                unitLabel,
                new ResultCallback<String>() {
                    @Override
                    public void onSuccess(String id) {
                        submitting = false;
                        submission.postValue(UiState.success(id));
                    }

                    @Override
                    public void onError(Exception error) {
                        submitting = false;
                        submission.postValue(UiState.error(error));
                    }
                });
    }

    public void clearSubmission() {
        submission.setValue(null);
    }

    public void loadBookings() {
        bookings.setValue(UiState.loading());

        repository.listForGuest(new ResultCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> items) {
                bookings.postValue(UiState.success(items));
            }

            @Override
            public void onError(Exception error) {
                bookings.postValue(UiState.error(error));
            }
        });
    }

    public void startWatchingBookings() {
        if (watchRegistration != null) {
            return;
        }

        watchRegistration = repository.watchForGuest(
                new ResultCallback<List<Booking>>() {
                    @Override
                    public void onSuccess(List<Booking> items) {
                        bookings.postValue(UiState.success(items));
                    }

                    @Override
                    public void onError(Exception error) {
                        bookings.postValue(UiState.error(error));
                    }
                });
    }

    public void stopWatchingBookings() {
        if (watchRegistration != null) {
            watchRegistration.remove();
            watchRegistration = null;
        }
    }

    public void cancel(String bookingId) {
        cancellation.setValue(UiState.loading());

        repository.cancel(bookingId, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                cancellation.postValue(UiState.success(bookingId));
            }

            @Override
            public void onError(Exception error) {
                cancellation.postValue(UiState.error(error));
            }
        });
    }

    public void clearCancellation() {
        cancellation.setValue(null);
    }

    @Override
    protected void onCleared() {
        stopWatchingBookings();
        super.onCleared();
    }
}