package vn.edu.ueh.phuclu.broomsharing.ui.booking;

import android.os.*;

import androidx.lifecycle.*;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.BookingNotice;
import vn.edu.ueh.phuclu.broomsharing.model.Conversation;
import vn.edu.ueh.phuclu.broomsharing.model.RoomReport;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.repository.BookingRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ChatRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.NoticeRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ReportRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;
import vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;
import vn.edu.ueh.phuclu.broomsharing.utils.TimeUtils;

import java.util.*;

/**
 * Quản lý danh sách thông báo, số chưa đọc và trạng thái đã đọc. Giữ listener dữ liệu trong vòng
 * đời ViewModel và giải phóng khi kết thúc.
 */
public class BookingNoticeViewModel extends ViewModel {
    private final BookingRepository repository = new BookingRepository();
    public final MutableLiveData<UiState<List<BookingNotice>>> notices = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ListenerRegistration listener;
    private ListenerRegistration ownerListener;
    private ListenerRegistration readListener, chatListener;
    private final NoticeRepository receipts = new NoticeRepository();
    private String sessionUid;
    private Set<String> readIds = Collections.emptySet();
    private boolean readsReady, bookingsReady, ownersReady;
    private int generation;
    private List<Conversation> conversations = Collections.emptyList();
    public final MutableLiveData<String> readError = new MutableLiveData<>();
    private boolean host;
    private List<Booking> ownerBookings = Collections.emptyList();
    private List<Booking> bookings = Collections.emptyList();
    private ListenerRegistration sentReportListener, receivedReportListener;
    private List<RoomReport> sentReports = Collections.emptyList(),
            receivedReports = Collections.emptyList();
    private boolean sentReady, receivedReady;
    private final Runnable tick =
            new Runnable() {
                public void run() {
                    // Kiểm tra lại giờ bắt đầu ngay cả khi không có snapshot mới từ Firestore.
                    repository.reconcileExpired(bookings, expiryResult);
                    repository.reconcileExpired(ownerBookings, expiryResult);
                    render();
                    handler.postDelayed(this, 30000);
                }
            };
    private final ResultCallback<Object> expiryResult =
            new ResultCallback<Object>() {
                public void onSuccess(Object ignored) {}

                public void onError(Exception e) {
                    readError.setValue("Chưa đồng bộ được đơn quá hạn: " + e.getMessage());
                }
            };
    private final Runnable syncTimeout =
            () -> {
                if (!readsReady || !bookingsReady || !ownersReady)
                    notices.setValue(
                            UiState.error(
                                    new Exception(
                                            "Chưa nhận được xác nhận mới từ hệ thống. Kiểm tra mạng"
                                                    + " rồi thử đồng bộ lại.")));
            };

    // Đăng ký các nguồn sự kiện theo UID; thông báo được dựng từ booking, hội thoại và báo cáo.
    public void start() {
        start(false);
    }

    public void resume() {
        start(host);
    }

    public void start(boolean isHost) {
        host = isHost;
        if (listener != null) return;
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        final int run = ++generation;
        readsReady = bookingsReady = false;
        ownersReady = !host;
        sentReady = false;
        receivedReady = !host;
        sentReports = Collections.emptyList();
        receivedReports = Collections.emptyList();
        if (!uid.equals(sessionUid)) {
            bookings = Collections.emptyList();
            ownerBookings = Collections.emptyList();
            conversations = Collections.emptyList();
            readIds = Collections.emptySet();
        }
        sessionUid = uid;
        // Báo cáo dùng cùng receipt, nhưng không tạo collection notification trùng dữ liệu gốc.
        ReportRepository reports = new ReportRepository();
        sentReportListener = reports.watch(false, reportResult(run, false));
        if (host) receivedReportListener = reports.watch(true, reportResult(run, true));
        notices.setValue(UiState.loading());
        handler.removeCallbacks(syncTimeout);
        handler.postDelayed(syncTimeout, 15000);
        readListener =
                receipts.watch(
                        uid,
                        new ResultCallback<Set<String>>() {
                            public void onSuccess(Set<String> ids) {
                                if (run != generation) return;
                                readsReady = true;
                                readIds = ids;
                                render();
                            }

                            public void onError(Exception e) {
                                if (run == generation)
                                    notices.setValue(
                                            UiState.error(
                                                    new Exception(
                                                            "Chưa đồng bộ được trạng thái đã đọc."
                                                                    + " Hãy thử đồng bộ lại.")));
                            }
                        });
        readError.setValue(null);
        try {
            chatListener =
                    new ChatRepository()
                            .inbox(
                                    new ResultCallback<List<Conversation>>() {
                                        public void onSuccess(List<Conversation> rows) {
                                            if (run == generation
                                                    && uid.equals(
                                                            FirebaseAuth.getInstance().getUid())) {
                                                conversations = rows;
                                                render();
                                            }
                                        }

                                        public void onError(Exception e) {
                                            if (run == generation)
                                                readError.setValue(
                                                        "Chưa tải được cập nhật tin nhắn.");
                                        }
                                    });
        } catch (Exception e) {
            if (run == generation) readError.setValue("Chưa tải được cập nhật tin nhắn.");
        }
        listener =
                repository.watchMine(
                        uid,
                        new ResultCallback<List<Booking>>() {
                            public void onSuccess(List<Booking> values) {
                                if (run != generation
                                        || !uid.equals(FirebaseAuth.getInstance().getUid())) return;
                                bookingsReady = true;
                                bookings = values;
                                render();
                                handler.removeCallbacks(tick);
                                handler.postDelayed(tick, 30000);
                            }

                            public void onError(Exception e) {
                                if (run != generation) return;
                                handler.removeCallbacks(tick);
                                notices.setValue(UiState.error(e));
                            }
                        });
        if (host)
            ownerListener =
                    repository.watchOwner(
                            uid,
                            new ResultCallback<List<Booking>>() {
                                public void onSuccess(List<Booking> values) {
                                    if (run == generation
                                            && uid.equals(FirebaseAuth.getInstance().getUid())) {
                                        ownersReady = true;
                                        ownerBookings = values;
                                        render();
                                    }
                                }

                                public void onError(Exception e) {
                                    if (run == generation) notices.setValue(UiState.error(e));
                                }
                            });
    }

    // Hợp nhất nguồn thành sự kiện có định danh ổn định, áp dụng mốc đọc của tài khoản để tính số chưa đọc.
    private void render() {
        if (sessionUid == null
                || !sessionUid.equals(FirebaseAuth.getInstance().getUid())
                || !readsReady
                || !bookingsReady
                || !ownersReady
                || !sentReady
                || !receivedReady) return;
        handler.removeCallbacks(syncTimeout);
        long now = System.currentTimeMillis();
        List<BookingNotice> list = new ArrayList<>();
        for (Booking b : bookings) {
            if (b.endAt == null || b.endAt.getTime() < now - 7L * 86400000) continue;
            list.add(
                    new BookingNotice(
                            b.id + ":status:" + b.status + ":" + b.paymentStatus,
                            FormatUtils.bookingStatus(b),
                            b.roomName + " · " + TimeUtils.formatVietnam(b.startAt),
                            b.id));
            if (b.isInstantPayment() && "PENDING".equals(b.status)) continue;
            if (!CancellationPolicy.versioned(b)
                    || b.startAt == null
                    || b.startAt.getTime() <= now
                    || !Arrays.asList("PENDING", "CONFIRMED").contains(b.status)) continue;
            int rate = CancellationPolicy.percent(b, now);
            long remaining = b.startAt.getTime() - now;
            long boundary =
                    rate == 100
                            ? CancellationPolicy.fullWindow(b.activeMode)
                            : CancellationPolicy.partialWindow(b.activeMode);
            boolean soon = rate > 0 && remaining - boundary <= 2L * 3600000;
            String title =
                    soon ? "Sắp hết mốc hoàn " + rate + "%" : "Mức hoàn hiện tại: " + rate + "%";
            String body =
                    b.roomName
                            + "\n"
                            + (soon
                                    ? "Mốc thay đổi: "
                                            + TimeUtils.formatVietnam(
                                                    new Date(b.startAt.getTime() - boundary))
                                            + ". "
                                    : "")
                            + "Tính trên tiền trả trước thực nhận. "
                            + FormatUtils.paymentStatus(b.paymentStatus)
                            + ". Hoàn tiền được xử lý thủ công, chưa có API hoàn tự động.";
            list.add(
                    new BookingNotice(
                            b.id + ":policy:" + rate + (soon ? ":soon" : ""), title, body, b.id));
        }
        for (Booking b : ownerBookings) {
            if (b.endAt == null || b.endAt.getTime() < now - 7L * 86400000) continue;
            BookingNotice n =
                    new BookingNotice(
                            b.id + ":host:" + b.status + ":" + b.paymentStatus,
                            FormatUtils.bookingStatus(b),
                            b.roomName
                                    + " · Khách: "
                                    + b.guestName
                                    + "\n"
                                    + TimeUtils.formatVietnam(b.startAt),
                            b.id);
            n.forHost = true;
            list.add(n);
        }
        for (RoomReport r : sentReports) addReportNotice(list, r, false);
        for (RoomReport r : receivedReports) addReportNotice(list, r, true);
        for (BookingNotice n : list) n.unread = !readIds.contains(n.id);
        for (Conversation c : conversations) {
            if (c.sequence == 0) continue;
            BookingNotice n =
                    new BookingNotice(
                            "chat:" + c.id + ":" + c.sequence,
                            "Tin nhắn · " + c.peerName(sessionUid),
                            c.lastText,
                            c.bookingId);
            n.conversationId = c.id;
            n.unread = c.unread(sessionUid) > 0;
            list.add(0, n);
        }
        notices.setValue(UiState.success(list));
    }

    // Gửi mốc đọc tới noticeReads; không sửa booking chỉ vì người dùng mở thông báo.
    public void markRead(BookingNotice n) {
        if (!n.conversationId.isEmpty()) return;
        receipts.read(
                sessionUid,
                n.id,
                new ResultCallback<String>() {
                    public void onSuccess(String id) {
                        readError.setValue(null);
                    }

                    public void onError(Exception e) {
                        readError.setValue(
                                "Chưa lưu được trạng thái đã đọc. Hãy thử lại khi có mạng.");
                    }
                });
    }

    private ResultCallback<List<RoomReport>> reportResult(int run, boolean received) {
        return new ResultCallback<List<RoomReport>>() {
            public void onSuccess(List<RoomReport> rows) {
                if (run != generation) return;
                if (received) {
                    receivedReports = rows;
                    receivedReady = true;
                } else {
                    sentReports = rows;
                    sentReady = true;
                }
                render();
            }

            public void onError(Exception e) {
                if (run == generation) notices.setValue(UiState.error(e));
            }
        };
    }

    private void addReportNotice(List<BookingNotice> rows, RoomReport r, boolean received) {
        long version = r.updatedAt == null ? 0 : r.updatedAt.getTime();
        BookingNotice n =
                new BookingNotice(
                        "report:" + r.id + ":" + version,
                        received
                                ? "Báo cáo về phòng của bạn"
                                : "Cập nhật báo cáo: " + FormatUtils.reportStatus(r.status),
                        r.reason
                                + "\n"
                                + (r.resolution == null || r.resolution.isEmpty()
                                        ? r.description
                                        : r.resolution),
                        "");
        n.reportNotice = true;
        n.forHost = received;
        rows.add(n);
    }

    public void retry() {
        stop();
        start(host);
    }

    // Tháo các nguồn đang nghe và vô hiệu callback thuộc lần tải cũ.
    public void stop() {
        generation++;
        if (listener != null) listener.remove();
        if (ownerListener != null) ownerListener.remove();
        if (readListener != null) readListener.remove();
        if (chatListener != null) chatListener.remove();
        if (sentReportListener != null) sentReportListener.remove();
        if (receivedReportListener != null) receivedReportListener.remove();
        sentReportListener = receivedReportListener = null;
        listener = null;
        ownerListener = null;
        readListener = null;
        chatListener = null;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(syncTimeout);
    }

    @Override
    protected void onCleared() {
        stop();
    }
}
