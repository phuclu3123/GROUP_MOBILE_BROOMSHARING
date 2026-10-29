package vn.edu.ueh.phuclu.broomsharing.ui.booking;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.repository.BookingRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;
import vn.edu.ueh.phuclu.broomsharing.repository.RoomRepository;

import java.util.*;

/**
 * Quản lý trạng thái đặt chỗ, lịch trống và chi tiết đơn cho giao diện.
 * Gọi BookingRepository để làm việc với dữ liệu; trả kết quả qua LiveData.
 */
public class BookingViewModel extends ViewModel {
    // Lớp làm việc với dữ liệu booking và phòng; ViewModel chuyển tiếp yêu cầu xuống đây.
    private final BookingRepository repository = new BookingRepository();

    // Trạng thái tải thông tin phòng.
    public final MutableLiveData<UiState<Room>> room = new MutableLiveData<>();

    // Trạng thái báo giá; báo giá chỉ để xem trước, chưa tạo booking.
    public final MutableLiveData<UiState<Booking>> quote = new MutableLiveData<>();

    // Trạng thái tạo/cập nhật booking, thường chứa mã booking khi thành công.
    public final MutableLiveData<UiState<String>> saved = new MutableLiveData<>();

    // Danh sách booking của người dùng hiện tại.
    public final MutableLiveData<UiState<List<Booking>>> bookings = new MutableLiveData<>();

    // Mã dùng để nhận diện cùng một lần gửi yêu cầu, hỗ trợ chống tạo trùng.
    public String requestKey = UUID.randomUUID().toString();

    // Chuỗi mô tả lựa chọn gần nhất: phòng, thời gian và thời hạn thuê.
    public String lastRequest = "";

    // Các khoảng booking dùng để hiển thị tình trạng lịch của phòng.
    public final MutableLiveData<UiState<List<Booking>>> availability = new MutableLiveData<>();

    // Trạng thái booking đang được xem chi tiết.
    public final MutableLiveData<UiState<Booking>> detail = new MutableLiveData<>();

    // Vị trí bộ lọc đang chọn trong danh sách booking.
    public int filterPosition;

    // Tăng sau mỗi lần tải lịch để nhận biết và bỏ qua phản hồi đã cũ.
    private int availabilityRequest;

    // Listener realtime cho danh sách của tôi và booking chi tiết.
    private ListenerRegistration watcher;
    private ListenerRegistration detailWatcher;

    // Theo dõi booking chi tiết realtime để nhận cập nhật từ Firestore.
    public void watchDetail(String id) {
        // Hủy listener cũ trước khi theo dõi một booking khác.
        stopDetail();
        detail.setValue(UiState.loading());
        detailWatcher =
                repository.watchOne(
                        id,
                        new ResultCallback<Booking>() {
                            public void onSuccess(Booking value) {
                                // Đóng gói dữ liệu thành công để Activity quan sát.
                                detail.setValue(UiState.success(value));
                            }

                            public void onError(Exception e) {
                                // Đưa lỗi vào trạng thái để Activity hiển thị.
                                detail.setValue(UiState.error(e));
                            }
                        });
    }

    public void stopDetail() {
        // remove() ngừng nhận các lần cập nhật realtime tiếp theo.
        if (detailWatcher != null) detailWatcher.remove();
        detailWatcher = null;
    }

    // Tải chi tiết một lần; khác watchDetail() là không giữ listener realtime.
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

    private final List<ListenerRegistration> calendarListeners = new ArrayList<>();
    private int availabilityReadRequest;
    public void stopAvailability() {
        ++availabilityRequest;
        ++availabilityReadRequest;
        for (ListenerRegistration listener : calendarListeners) listener.remove();
        calendarListeners.clear();
    }
    public void loadAvailability(String roomId, long from, long to) {
        stopAvailability();
        final int generation = availabilityRequest;
        java.time.ZoneId zone = vn.edu.ueh.phuclu.broomsharing.utils.HostSchedule.ZONE;
        java.time.YearMonth first = java.time.YearMonth.from(java.time.Instant.ofEpochMilli(from).atZone(zone));
        java.time.YearMonth last = java.time.YearMonth.from(java.time.Instant.ofEpochMilli(to - 1).atZone(zone));
        availability.setValue(UiState.loading());
        for (java.time.YearMonth month = first; month.compareTo(last) <= 0; month = month.plusMonths(1)) {
            String key = vn.edu.ueh.phuclu.broomsharing.utils.BookingPolicy.monthKey(roomId, month);
            calendarListeners.add(vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper.getFirestore()
                    .collection("availabilityMonths").document(key).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (row, error) -> {
                        if (generation != availabilityRequest) return;
                        if (error != null) { availability.setValue(UiState.error(error)); return; }
                        if (row != null && !row.getMetadata().isFromCache()) refreshAvailability(roomId, from, to, generation);
                    }));
        }
    }
    private void refreshAvailability(String roomId, long from, long to, int token) {
        // Mỗi lần kiểm tra có một mã; bỏ kết quả cũ nếu người dùng đã đổi thời gian.
        final int read = ++availabilityReadRequest;
        repository.availability(
                roomId,
                from,
                to,
                new ResultCallback<List<Booking>>() {
                    public void onSuccess(List<Booking> value) {
                            // Chỉ nhận kết quả nếu đây vẫn là lần tải mới nhất.
                        if (token == availabilityRequest && read == availabilityReadRequest)
                            availability.setValue(UiState.success(value));
                    }

                    public void onError(Exception e) {
                            // Lỗi từ lần tải cũ cũng không được ghi đè trạng thái mới.
                        if (token == availabilityRequest && read == availabilityReadRequest) availability.setValue(UiState.error(e));
                    }
                });
    }

    public void watchMine(String uid) {
        // Tránh giữ đồng thời listener của người dùng hoặc màn hình trước đó.
        stopWatching();
        bookings.setValue(UiState.loading());
        watcher =
                repository.watchMine(
                        uid,
                        new ResultCallback<List<Booking>>() {
                            public void onSuccess(List<Booking> value) {
                                // Mỗi thay đổi realtime sẽ cập nhật danh sách Activity đang quan sát.
                                bookings.setValue(UiState.success(value));
                            }

                            public void onError(Exception e) {
                                bookings.setValue(UiState.error(e));
                            }
                        });
    }

    public void stopWatching() {
        // Ngừng theo dõi Firestore để tránh giữ listener khi màn hình không còn dùng.
        if (watcher != null) {
            watcher.remove();
            watcher = null;
        }
    }

    @Override
    protected void onCleared() {
        stopAvailability();
        // ViewModel sắp bị hủy: dọn listener và làm mất hiệu lực phản hồi lịch đang chờ.
        stopWatching();
        stopDetail();
        availabilityRequest++;
    }

    public boolean busy() {
        // Đang bận nếu đang báo giá hoặc đang lưu/cập nhật booking.
        return saved.getValue() != null && saved.getValue().loading
                || quote.getValue() != null && quote.getValue().loading;
    }

    public void loadRoom(String roomId) {
        // Báo trạng thái tải trước, sau đó nhận dữ liệu hoặc lỗi qua callback.
        room.setValue(UiState.loading());
        new RoomRepository()
                .room(
                        roomId,
                        new ResultCallback<Room>() {
                            public void onSuccess(Room value) {
                                    // Gửi dữ liệu phòng mới về LiveData.
                                room.setValue(UiState.success(value));
                            }

                            public void onError(Exception e) {
                                    // Gửi lỗi về LiveData để giao diện xử lý.
                                room.setValue(UiState.error(e));
                            }
                        });
    }

    public Booking request(String roomId, long start, long end, int months) {
        // Tạo đối tượng Booking làm dữ liệu đầu vào; đoạn này chưa ghi lên Firestore.
        Booking data = new Booking();
        data.roomId = roomId;
        data.startAt = new Date(start);
        data.endAt = end == 0 ? null : new Date(end);
        data.rentalMonths = months;

        // Tạo chữ ký từ lựa chọn hiện tại để biết Guest có đổi yêu cầu hay chưa.
        String signature = roomId + ":" + start + ":" + end + ":" + months;

        // Nếu lựa chọn khác lần trước, cấp mã mới; nếu giống thì giữ mã để retry an toàn.
        if (!lastRequest.isEmpty() && !lastRequest.equals(signature))
            requestKey = UUID.randomUUID().toString();
        lastRequest = signature;
        data.requestKey = requestKey;
        return data;
    }

    public void quote(Booking request) {
        // Không khởi động thêm thao tác khi đang có báo giá/lưu booking khác.
        if (busy()) return;
        quote.setValue(UiState.loading());
        repository.quote(
                request,
                new ResultCallback<Booking>() {
                    public void onSuccess(Booking value) {
                            // value chứa kết quả báo giá để Activity hiển thị.
                        quote.setValue(UiState.success(value));
                    }

                    public void onError(Exception e) {
                            // Chuyển lỗi thành trạng thái quan sát được.
                        quote.setValue(UiState.error(e));
                    }
                });
    }

    public void create(Booking request) {
        // Chặn thao tác trùng khi ViewModel đang xử lý.
        if (busy()) return;
        saved.setValue(UiState.loading());
        Room selected = room.getValue() == null ? null : room.getValue().data;
        if (selected == null) { saved.setValue(UiState.error(new Exception("Chưa tải được phòng."))); return; }
        new vn.edu.ueh.phuclu.broomsharing.repository.PaymentRepository().checkChannel(
                selected.getOwnerUid(), new ResultCallback<String>() {
                    public void onSuccess(String owner) {
                        repository.create(request, new ResultCallback<String>() {
                            public void onSuccess(String value) { saved.setValue(UiState.success(value)); }
                            public void onError(Exception error) { saved.setValue(UiState.error(error)); }
                        });
                    }
                    public void onError(Exception error) { saved.setValue(UiState.error(error)); }
                });
    }

    public void loadMine(String uid) {
        // Tải danh sách một lần; watchMine() dùng khi cần nhận cập nhật realtime.
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
        // Hủy không truyền phần trăm dự kiến; overload dưới xử lý trường hợp cần đối chiếu.
        cancel(bookingId, uid, null);
    }

    public void cancel(String bookingId, String uid, Integer expectedPercent) {
        // Dùng trạng thái CANCELLED; quyền và điều kiện hủy được Repository kiểm tra lại.
        if (busy()) return;
        saved.setValue(UiState.loading());
        repository.transition(
                bookingId,
                Booking.STATUS_CANCELLED,
                "",
                expectedPercent,
                new ResultCallback<String>() {
                    public void onSuccess(String id) {
                            // Báo hủy thành công rồi tải lại danh sách booking của Guest.
                        saved.setValue(UiState.success(id));
                        loadMine(uid);
                    }

                    public void onError(Exception e) {
                        saved.setValue(UiState.error(e));
                    }
                });
    }
}
