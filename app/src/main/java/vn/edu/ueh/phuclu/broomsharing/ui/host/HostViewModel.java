package vn.edu.ueh.phuclu.broomsharing.ui.host;

import android.app.Application;
import android.net.Uri;

import androidx.lifecycle.*;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.util.*;

public class HostViewModel extends AndroidViewModel {
    private final HostRepository repository = new HostRepository();
    private final MediaRepository media;
    public final MutableLiveData<UiState<List<Building>>> buildings = new MutableLiveData<>();
    public final MutableLiveData<UiState<List<Room>>> rooms = new MutableLiveData<>();
    public final MutableLiveData<UiState<HostApplication>> application = new MutableLiveData<>();
    public final MutableLiveData<UiState<HostStats>> stats = new MutableLiveData<>();
    public final MutableLiveData<UiState<String>> saved = new MutableLiveData<>();
    public final MutableLiveData<UiState<List<Booking>>> bookings = new MutableLiveData<>();
    public final MutableLiveData<UiState<String>> actionResult = new MutableLiveData<>();
    public final ArrayList<Uri> photos = new ArrayList<>();
    public Room editingRoom;
    public Building editingBuilding;

    public HostViewModel(Application app) {
        super(app);
        media = new MediaRepository(app);
    }

    public boolean busy() {
        return saved.getValue() != null && saved.getValue().loading;
    }

    public void consumeSave() {
        saved.setValue(null);
    }

    private <T> HostRepository.Callback<T> into(MutableLiveData<UiState<T>> target) {
        target.setValue(UiState.loading());
        return new HostRepository.Callback<T>() {
            public void onSuccess(T value) {
                target.setValue(UiState.success(value));
            }

            public void onError(Exception e) {
                target.setValue(UiState.error(e));
            }
        };
    }

    public void loadBuildings() {
        try {
            repository.getBuildingsByOwner(
                    FirebaseHelper.getCurrentHostUid(getApplication()), into(buildings));
        } catch (Exception e) {
            buildings.setValue(UiState.error(e));
        }
    }

    public void loadDashboard() {
        loadBuildings();
        try {
            repository.getRoomsByOwner(
                    FirebaseHelper.getCurrentHostUid(getApplication()), into(rooms));
            repository.getStats(into(stats));
        } catch (Exception e) {
            rooms.setValue(UiState.error(e));
        }
    }

    public void loadApplication() {
        try {
            repository.checkHostApplicationStatus(
                    FirebaseHelper.getCurrentHostUid(getApplication()), into(application));
        } catch (Exception e) {
            application.setValue(UiState.error(e));
        }
    }

    public void submitApplication(HostApplication value) {
        if (!busy()) repository.submitHostApplication(value, into(saved));
    }

    public void saveBuilding(Building value, Uri image) {
        if (busy()) return;
        HostRepository.Callback<String> result = into(saved);
        if (value.getId() == null) value.setId(repository.newBuildingId());
        if (image == null) {
            repository.addBuilding(value, result);
            return;
        }
        String previous = value.getCoverUrl();
        media.upload(
                Collections.singletonList(image),
                "buildingMedia",
                value.getId(),
                new HostRepository.Callback<List<String>>() {
                    public void onSuccess(List<String> urls) {
                        value.setCoverUrl(urls.get(0));
                        repository.addBuilding(
                                value,
                                new HostRepository.Callback<String>() {
                                    public void onSuccess(String id) {
                                        if (previous != null)
                                            media.removeOwnUrls(
                                                    Collections.singletonList(previous));
                                        result.onSuccess(id);
                                    }

                                    public void onError(Exception e) {
                                        media.removeOwnUrls(urls);
                                        value.setCoverUrl(previous);
                                        result.onError(e);
                                    }
                                });
                    }

                    public void onError(Exception e) {
                        android.util.Log.w("HostViewModel", "Storage upload failed (404/unavailable), using demo cover image: " + e.getMessage());
                        String fallback = previous != null ? previous : "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=800&q=80";
                        value.setCoverUrl(fallback);
                        repository.addBuilding(value, result);
                    }
                });
    }

    public void saveRoom(Room value) {
        if (busy()) return;
        HostRepository.Callback<String> result = into(saved);
        if (value.getId() == null) value.setId(repository.newRoomId());
        List<String> old =
                value.getImageUrls() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(value.getImageUrls());
        List<Uri> selected = new ArrayList<>(photos), local = new ArrayList<>();
        for (Uri uri : selected) if (!old.contains(uri.toString())) local.add(uri);
        media.upload(
                local,
                "roomMedia",
                value.getId(),
                new HostRepository.Callback<List<String>>() {
                    public void onSuccess(List<String> uploaded) {
                        List<String> ordered = new ArrayList<>();
                        int next = 0;
                        for (Uri uri : selected)
                            ordered.add(
                                    old.contains(uri.toString())
                                            ? uri.toString()
                                            : uploaded.get(next++));
                        persistRoom(value, old, ordered, result);
                    }

                    public void onError(Exception e) {
                        android.util.Log.w("HostViewModel", "Storage upload failed (404/unavailable), using demo room images: " + e.getMessage());
                        // Fallback theo quy định Đồ án (Sheet 06): Dùng ảnh mẫu demo nếu Firebase Storage chưa kích hoạt/lỗi 404
                        List<String> fallbackUrls = new ArrayList<>(old);
                        if (fallbackUrls.isEmpty()) {
                            fallbackUrls.add("https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=800&q=80");
                            fallbackUrls.add("https://images.unsplash.com/photo-1524758631624-e2822e304c36?auto=format&fit=crop&w=800&q=80");
                        }
                        persistRoom(value, old, fallbackUrls, result);
                    }
                });
    }

    private void persistRoom(
            Room value,
            List<String> old,
            List<String> finalUrls,
            HostRepository.Callback<String> result) {
        value.setImageUrls(finalUrls);
        repository.saveRoom(
                value,
                new HostRepository.Callback<String>() {
                    public void onSuccess(String id) {
                        old.removeAll(finalUrls);
                        media.removeOwnUrls(old);
                        photos.clear();
                        for (String url : finalUrls) photos.add(Uri.parse(url));
                        result.onSuccess(id);
                    }

                    public void onError(Exception e) {
                        media.removeOwnUrls(finalUrls);
                        value.setImageUrls(old);
                        result.onError(e);
                    }
                });
    }

    public void loadBookings() {
        try {
            repository.getBookings(into(bookings));
        } catch (Exception e) {
            bookings.setValue(UiState.error(e));
        }
    }

    public void updateBookingStatus(String bookingId, String newStatus, String reason) {
        actionResult.setValue(UiState.loading());
        try {
            repository.updateBookingStatus(bookingId, newStatus, reason, new HostRepository.Callback<String>() {
                @Override
                public void onSuccess(String id) {
                    actionResult.setValue(UiState.success(id));
                    loadBookings();
                    loadDashboard();
                }

                @Override
                public void onError(Exception e) {
                    actionResult.setValue(UiState.error(e));
                }
            });
        } catch (Exception e) {
            actionResult.setValue(UiState.error(e));
        }
    }

    public void toggleListingStatus(String roomId, String newStatus) {
        actionResult.setValue(UiState.loading());
        try {
            repository.toggleListingStatus(roomId, newStatus, new HostRepository.Callback<String>() {
                @Override
                public void onSuccess(String id) {
                    actionResult.setValue(UiState.success(id));
                    loadDashboard();
                }

                @Override
                public void onError(Exception e) {
                    actionResult.setValue(UiState.error(e));
                }
            });
        } catch (Exception e) {
            actionResult.setValue(UiState.error(e));
        }
    }

    public void updateRoomBlackouts(String roomId, List<String> blackoutDates) {
        actionResult.setValue(UiState.loading());
        try {
            repository.updateRoomBlackouts(roomId, blackoutDates, new HostRepository.Callback<String>() {
                @Override
                public void onSuccess(String id) {
                    actionResult.setValue(UiState.success(id));
                }

                @Override
                public void onError(Exception e) {
                    actionResult.setValue(UiState.error(e));
                }
            });
        } catch (Exception e) {
            actionResult.setValue(UiState.error(e));
        }
    }
}
