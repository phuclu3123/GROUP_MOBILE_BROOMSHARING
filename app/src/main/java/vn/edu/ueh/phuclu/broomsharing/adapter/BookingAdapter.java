package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
import java.util.function.Consumer;
import vn.edu.ueh.phuclu.broomsharing.databinding.ItemGuestBookingBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;
import vn.edu.ueh.phuclu.broomsharing.utils.TimeUtils;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.Holder> {
    // Dữ liệu mà RecyclerView cần hiển thị, mỗi phần tử tương ứng một booking.
    private final List<Booking> items = new ArrayList<>();

    // Hai callback để Activity xử lý hủy booking hoặc mở chi tiết.
    private final Consumer<Booking> cancel;
    private final Consumer<Booking> open;

    // Dùng để khóa nút hủy trong lúc ViewModel đang cập nhật booking.
    private boolean busy;

    // Activity truyền hành động cho Adapter thay vì để Adapter tự điều hướng hoặc gọi Repository.
    public BookingAdapter(Consumer<Booking> cancel, Consumer<Booking> open) { this.cancel = cancel; this.open = open; }

    // Thay danh sách đang hiển thị rồi báo RecyclerView bind lại các dòng.
    public void setData(List<Booking> data) { items.clear(); items.addAll(data); notifyDataSetChanged(); }

    // Cập nhật trạng thái bận để khóa/mở các nút hủy trong từng thẻ.
    public void setBusy(boolean value) { busy = value; notifyDataSetChanged(); }

    // Tạo một ViewHolder bằng layout của một thẻ booking.
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemGuestBookingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    // Số thẻ RecyclerView cần hiển thị bằng số booking hiện có trong items.
    @Override public int getItemCount() { return items.size(); }

    // Đổ dữ liệu booking tại position vào ViewHolder đang được RecyclerView dùng lại.
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Booking item = items.get(position);
        h.b.open.setOnClickListener(v -> open.accept(item));
        h.b.info.setContentDescription("Xem chi tiết đặt chỗ " + (item.roomName == null ? item.roomId : item.roomName));
        String deposit = item.priceSnapshot != null && item.priceSnapshot.get("depositAmount") instanceof Number
                ? FormatUtils.formatCurrency(((Number) item.priceSnapshot.get("depositAmount")).doubleValue()) : "Chưa có dữ liệu";
        h.b.info.setText(item.roomName == null ? item.roomId : item.roomName);
        h.b.bookingStatus.setText(FormatUtils.bookingStatus(item.status) + " · " + ("FIXED".equals(item.activeMode) ? "Thuê tháng/quý" : "Thuê giờ"));
        h.b.bookingTime.setText(TimeUtils.formatVietnam(item.startAt) + "\nđến " + TimeUtils.formatVietnam(item.endAt));
        h.b.bookingPrice.setText("Thuê và phí: " + FormatUtils.formatCurrency(item.totalAmount) + "\n" + (CancellationPolicy.versioned(item) ? "Trả trước dự kiến (40%): " : "Cọc theo booking cũ: ") + deposit);
        h.b.bookingPayment.setText(FormatUtils.paymentStatus(item.paymentStatus));
        h.b.bookingReason.setVisibility(item.rejectionReason == null || item.rejectionReason.isEmpty() ? View.GONE : View.VISIBLE);
        h.b.bookingReason.setText(item.rejectionReason == null ? "" : "Lý do: " + item.rejectionReason);
        h.b.cancel.setVisibility(item.startAt != null && item.startAt.after(new Date())
                && (Booking.STATUS_PENDING.equals(item.status) || Booking.STATUS_CONFIRMED.equals(item.status)) ? View.VISIBLE : View.GONE);
        h.b.cancel.setEnabled(!busy); h.b.cancel.setOnClickListener(v -> { if (!busy) cancel.accept(item); });
    }

    // Giữ Binding của một thẻ để RecyclerView tái sử dụng các View khi cuộn danh sách.
    static class Holder extends RecyclerView.ViewHolder {
        final ItemGuestBookingBinding b;
        Holder(ItemGuestBookingBinding binding) { super(binding.getRoot()); b = binding; }
    }
}
