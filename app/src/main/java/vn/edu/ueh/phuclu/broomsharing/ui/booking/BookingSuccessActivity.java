package vn.edu.ueh.phuclu.broomsharing.ui.booking;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityBookingSuccessBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ChatActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.report.RoomReportActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.review.RoomReviewsActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.room.MainActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.room.SpaceDetailActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.BookingCalendar;
import vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;
import vn.edu.ueh.phuclu.broomsharing.utils.TimeUtils;

/**
 * Màn hình chi tiết đơn đặt chỗ: hiển thị trạng thái và các thao tác được phép. Theo dõi
 * BookingViewModel để nhận thay đổi của đơn.
 */
public class BookingSuccessActivity extends SessionActivity {
    // ViewModel tải và theo dõi booking được mở từ màn tạo hoặc danh sách booking.
    private BookingViewModel model;

    // Mã booking nhận qua Intent.
    private String id;

    @Override
    protected int pageLayout() {
        // Layout chi tiết booking; tên Activity có chữ Success nhưng booking vẫn có thể PENDING.
        return R.layout.activity_booking_success;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        // Nối các View trong XML với Activity bằng ViewBinding.
        ActivityBookingSuccessBinding b =
                ActivityBookingSuccessBinding.bind(
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        model = new ViewModelProvider(this).get(BookingViewModel.class);
        // BookingActivity hoặc MyBookingsActivity gửi mã này khi mở trang chi tiết.
        id = getIntent().getStringExtra("bookingId");

        // Đóng màn hình chi tiết.
        b.confirmationBack.setOnClickListener(v -> finish());

        // Backend tạo QR theo snapshot; không gửi số tiền hoặc tự ghi PAID từ Activity.
        b.bookingPayment.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(this, BookingPaymentActivity.class)
                                        .putExtra("bookingId", id)));

        // Mở danh sách booking và dọn các màn hình cũ phía trên khỏi back stack.
        b.btnGoBookings.setOnClickListener(
                v -> {
                    startActivity(
                            new Intent(this, MyBookingsActivity.class)
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                });

        // Quay lại trang khám phá phòng.
        b.btnBackExplore.setOnClickListener(
                v -> {
                    startActivity(
                            new Intent(this, MainActivity.class)
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                });

        // Cho phép đọc lại booking nếu lần theo dõi hiện tại gặp lỗi.
        b.confirmationRetry.setOnClickListener(v -> model.watchDetail(id));

        // Quan sát trạng thái để hiển thị đang tải, lỗi hoặc nội dung booking.
        model.detail.observe(
                this,
                state -> {
                    if (state == null) return;
                    b.confirmationRetry.setVisibility(
                            state.error == null ? View.GONE : View.VISIBLE);
                    b.tvConfirmationTitle.setText(
                            state.loading
                                    ? "Đang kiểm tra đặt chỗ…"
                                    : state.error == null ? "Đặt chỗ của bạn" : state.error);
                    b.confirmationBody.setVisibility(state.data == null ? View.GONE : View.VISIBLE);
                    if (state.data == null) return;

                    // Từ đây đã có dữ liệu booking để đưa lên giao diện.
                    Booking item = state.data;
                    boolean received =
                            "PAID".equals(item.paymentStatus)
                                    || "REVIEW_REQUIRED".equals(item.paymentStatus);
                    b.bookingPayment.setEnabled(
                            received
                                    || ("CONFIRMED".equals(item.status)
                                            && item.startAt != null
                                            && item.startAt.getTime()
                                                    > System.currentTimeMillis()));
                    b.bookingPayment.setText(
                            received ? "Xem giao dịch" : "Thanh toán trả trước 40%");
                    // Calendar Intent không ghi thẳng lịch; Guest xác nhận trong ứng dụng Lịch.
                    b.bookingCalendar.setVisibility(
                            BookingCalendar.canSave(item, System.currentTimeMillis())
                                    ? View.VISIBLE
                                    : View.GONE);
                    b.bookingCalendar.setOnClickListener(v -> BookingCalendar.open(this, item));
                    b.bookingReview.setVisibility(
                            profile.uid.equals(item.guestUid)
                                            && vn.edu.ueh.phuclu.broomsharing.utils.ReviewPolicy
                                                    .canReview(item, System.currentTimeMillis())
                                    ? View.VISIBLE
                                    : View.GONE);
                    b.bookingReview.setOnClickListener(
                            v ->
                                    startActivity(
                                            new Intent(this, RoomReviewsActivity.class)
                                                    .putExtra("roomId", item.roomId)
                                                    .putExtra("bookingId", item.id)));
                    b.bookingReport.setVisibility(
                            profile.uid.equals(item.guestUid) ? View.VISIBLE : View.GONE);
                    b.bookingReport.setOnClickListener(
                            v ->
                                    startActivity(
                                            new Intent(this, RoomReportActivity.class)
                                                    .putExtra(
                                                            SpaceDetailActivity.ROOM_ID,
                                                            item.roomId)
                                                    .putExtra("bookingId", item.id)));
                    b.bookingChat.setEnabled(true);

                    // Mở cuộc trò chuyện gắn với booking và phòng đang thuê.
                    b.bookingChat.setOnClickListener(
                            v ->
                                    startActivity(
                                            new Intent(this, ChatActivity.class)
                                                    .putExtra("bookingId", id)
                                                    .putExtra(
                                                            SpaceDetailActivity.ROOM_ID,
                                                            item.roomId)
                                                    .putExtra("roomName", item.roomName)));

                    // Hiển thị mã, phòng, địa chỉ, khoảng thời gian và trạng thái booking.
                    b.tvConfirmationCode.setText(getString(R.string.booking_code_label, item.id));
                    b.tvConfirmationRoom.setText(item.roomName);
                    b.tvConfirmationAddress.setText(
                            item.roomAddress == null
                                    ? "Địa chỉ chưa được lưu trong booking cũ."
                                    : item.roomAddress);
                    b.tvConfirmationRange.setText(
                            TimeUtils.formatVietnam(item.startAt)
                                    + "\nđến "
                                    + TimeUtils.formatVietnam(item.endAt));
                    b.tvConfirmationMode.setText(
                            FormatUtils.bookingStatus(item.status)
                                    + " • "
                                    + (Room.MODE_HOURLY.equals(item.activeMode)
                                            ? "Thuê theo giờ"
                                            : "Thuê tháng/quý"));

                    // Hiện tiền thuê/phí, tiền cọc trong snapshot và trạng thái thanh toán.
                    b.tvConfirmationTotal.setText(
                            FormatUtils.formatCurrency(item.totalAmount) + " (thuê + phí)");
                    Object deposit =
                            item.priceSnapshot == null
                                    ? null
                                    : item.priceSnapshot.get("depositAmount");
                    b.tvConfirmationDeposit.setText(
                            (deposit instanceof Number
                                            ? FormatUtils.formatCurrency(
                                                    ((Number) deposit).doubleValue())
                                            : "Chưa có snapshot cọc")
                                    + "\n"
                                    + FormatUtils.paymentStatus(item.paymentStatus));
                    b.tvConfirmationNote.setText(
                            "NO_SHOW".equals(item.status)
                                    ? "Host đã xác nhận khách không đến. Nếu kết luận chưa đúng,"
                                            + " bạn có thể gửi phản ánh theo booking.\n"
                                            + "Lý do: "
                                            + item.rejectionReason
                                    : "TERMINATED".equals(item.status)
                                            ? "Lượt thuê kết thúc do sự cố.\nLý do: "
                                                    + item.rejectionReason
                                                    + "\nKhép đơn: "
                                                    + TimeUtils.formatVietnam(item.closedAt)
                                                    + "\n"
                                                    + FormatUtils.paymentStatus(item.paymentStatus)
                                            : "CONFIRMED".equals(item.status)
                                                            && item.endAt != null
                                                            && item.endAt.getTime()
                                                                    <= System.currentTimeMillis()
                                                    ? "Đã hết thời gian thuê, chờ xác nhận tình"
                                                            + " trạng sử dụng. Bạn có thể gửi phản"
                                                            + " ánh nếu Host chưa ghi nhận đúng."
                                                    : "COMPLETED".equals(item.status)
                                                                    && item.usageConfirmedAt == null
                                                            ? "Đơn cũ chưa có xác nhận sử dụng."
                                                                    + " Liên hệ Admin qua phản ánh"
                                                                    + " theo booking để xác minh"
                                                                    + " quyền đánh giá."
                                                            : "EXPIRED".equals(item.status)
                                                                    ? "Yêu cầu chưa được duyệt khi"
                                                                          + " đến giờ bắt đầu. Lịch"
                                                                          + " đã được nhả; booking"
                                                                          + " được giữ trong lịch"
                                                                          + " sử."
                                                                    : ("PENDING".equals(item.status)
                                                                                    ? "Đang giữ"
                                                                                          + " chỗ,"
                                                                                          + " chờ Host"
                                                                                          + " xác nhận.\n"
                                                                                    : "")
                                                                            + CancellationPolicy
                                                                                    .summary(
                                                                                            item,
                                                                                            System
                                                                                                    .currentTimeMillis()));
                });

        // Bắt đầu nhận dữ liệu chi tiết ngay khi observer đã được đăng ký.
        model.watchDetail(id);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Màn hình hiển thị trở lại thì đăng ký listener realtime.
        if (model != null) model.watchDetail(id);
    }

    @Override
    protected void onStop() {
        // Rời màn hình thì gỡ listener để dừng cập nhật realtime.
        if (model != null) model.stopDetail();
        super.onStop();
    }
}
