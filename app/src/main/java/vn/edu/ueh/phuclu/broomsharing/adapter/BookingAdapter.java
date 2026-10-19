package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import vn.edu.ueh.phuclu.broomsharing.databinding.ItemGuestBookingBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.utils.*;
import java.util.*;
import java.util.function.Consumer;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.Holder> {
    private final List<Booking> items = new ArrayList<>();
    private final Consumer<Booking> cancel;
    private boolean busy;
    public BookingAdapter(Consumer<Booking> cancel) { this.cancel = cancel; }
    public void setData(List<Booking> data) { items.clear(); items.addAll(data); notifyDataSetChanged(); }
    public void setBusy(boolean value) { busy = value; notifyDataSetChanged(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemGuestBookingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public int getItemCount() { return items.size(); }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Booking item = items.get(position);
        String deposit = item.priceSnapshot != null && item.priceSnapshot.get("depositAmount") instanceof Number
                ? FormatUtils.formatCurrency(((Number) item.priceSnapshot.get("depositAmount")).doubleValue()) : "Chưa có dữ liệu";
        h.b.info.setText((item.roomName == null ? item.roomId : item.roomName) + "\nMã: " + item.id + "\n"
                + TimeUtils.formatVietnam(item.startAt) + " – " + TimeUtils.formatVietnam(item.endAt)
                + "\n" + FormatUtils.bookingStatus(item.status) + " • " + ("FIXED".equals(item.activeMode) ? "Thuê tháng/quý" : "Thuê giờ")
                + "\nThuê/phí: " + FormatUtils.formatCurrency(item.totalAmount) + " • Cọc: " + deposit
                + "\nThanh toán: " + FormatUtils.paymentStatus(item.paymentStatus)
                + (item.rejectionReason == null || item.rejectionReason.isEmpty() ? "" : "\nLý do: " + item.rejectionReason));
        h.b.cancel.setVisibility(item.startAt != null && item.startAt.after(new Date())
                && (Booking.STATUS_PENDING.equals(item.status) || Booking.STATUS_CONFIRMED.equals(item.status)) ? View.VISIBLE : View.GONE);
        h.b.cancel.setEnabled(!busy); h.b.cancel.setOnClickListener(v -> { if (!busy) cancel.accept(item); });
    }
    static class Holder extends RecyclerView.ViewHolder {
        final ItemGuestBookingBinding b;
        Holder(ItemGuestBookingBinding binding) { super(binding.getRoot()); b = binding; }
    }
}
