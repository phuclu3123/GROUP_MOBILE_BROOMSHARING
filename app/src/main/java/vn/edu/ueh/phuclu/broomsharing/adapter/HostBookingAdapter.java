package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ItemHostBookingBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HostBookingAdapter extends RecyclerView.Adapter<HostBookingAdapter.BookingViewHolder> {

    public interface OnBookingActionListener {
        void onAccept(Booking booking);
        void onReject(Booking booking);
        void onProgress(Booking booking, String status);
        void onChat(Booking booking);
        void onCancel(Booking booking);
        void onCheckIn(Booking booking);
        void onViewReceipt(Booking booking);
    }

    private final List<Booking> list = new ArrayList<>();
    private final OnBookingActionListener listener;
    private boolean busy;
    public void setBusy(boolean value) { busy = value; notifyDataSetChanged(); }
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public HostBookingAdapter(OnBookingActionListener listener) {
        this.listener = listener;
        dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    public void setData(List<Booking> data) {
        list.clear();
        if (data != null) {
            list.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHostBookingBinding binding = ItemHostBookingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new BookingViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        holder.bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class BookingViewHolder extends RecyclerView.ViewHolder {
        private final ItemHostBookingBinding binding;

        BookingViewHolder(ItemHostBookingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Booking item) {
            binding.btnChatGuest.setOnClickListener(v -> listener.onChat(item));
            binding.btnViewReceipt.setOnClickListener(v -> listener.onViewReceipt(item));
            binding.btnCancelByHost.setVisibility(Booking.STATUS_CONFIRMED.equals(item.status) && item.startAt != null && item.startAt.after(new java.util.Date()) ? View.VISIBLE : View.GONE);
            binding.btnCancelByHost.setEnabled(!busy);
            binding.btnCancelByHost.setOnClickListener(v -> { if (!busy) listener.onCancel(item); });
            binding.btnAccept.setEnabled(!busy);
            binding.btnReject.setEnabled(!busy);
            binding.btnProgress.setVisibility(View.GONE);
            binding.btnProgress.setEnabled(!busy);
            binding.btnCheckIn.setVisibility(View.GONE);
            binding.btnCheckIn.setEnabled(!busy);
            binding.layoutCancellationInfo.setVisibility(View.GONE);

            String roomTitle = item.getRoomName() != null && !item.getRoomName().isEmpty()
                    ? item.getRoomName()
                    : "Mã phòng: " + (item.getRoomId() != null ? item.getRoomId() : "---");
            binding.tvRoomName.setText(roomTitle);

            String guest = item.getGuestName() != null && !item.getGuestName().isEmpty()
                    ? item.getGuestName()
                    : (item.getGuestUid() != null ? item.getGuestUid() : "Khách ẩn danh");
            binding.tvGuestName.setText(guest);

            if (item.getGuestPhone() != null && !item.getGuestPhone().isEmpty()) {
                binding.tvGuestPhone.setVisibility(View.VISIBLE);
                binding.tvGuestPhone.setText("(" + item.getGuestPhone() + ")");
            } else {
                binding.tvGuestPhone.setVisibility(View.GONE);
            }

            // Time range
            String timeText = "";
            if (item.getStartAt() != null && item.getEndAt() != null) {
                timeText = dateFormat.format(item.getStartAt()) + " - " + dateFormat.format(item.getEndAt());
            } else if (item.getStartAt() != null) {
                timeText = "Bắt đầu: " + dateFormat.format(item.getStartAt());
            } else {
                timeText = "Thời gian thỏa thuận";
            }
            binding.tvBookingTime.setText(timeText);

            // Active mode
            if ("FIXED".equalsIgnoreCase(item.getActiveMode())) {
                binding.tvModeBadge.setText("Thuê cố định");
            } else {
                binding.tvModeBadge.setText("Thuê theo giờ");
            }

            // Total amount
            binding.tvTotalAmount.setText(currencyFormat.format(item.getTotalAmount()));
            String deposit = "Chưa có snapshot cọc";
            String policy = "";
            if (item.priceSnapshot != null) {
                Object amount = item.priceSnapshot.get("depositAmount");
                if (amount instanceof Number) deposit = currencyFormat.format(((Number) amount).doubleValue());
                Object terms = item.priceSnapshot.get("rentalPolicy");
                if (terms instanceof String) policy = (String) terms;
            }
            binding.tvPriceSnapshot.setText("Cọc (40%): " + deposit + "\nThanh toán: "
                    + vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils.paymentStatus(item.paymentStatus)
                    + (policy.isEmpty() ? "" : "\nChính sách lúc đặt: " + policy));

            // Status Badge & Action buttons
            String status = item.getStatus() != null ? item.getStatus().toUpperCase() : Booking.STATUS_PENDING;
            switch (status) {
                case Booking.STATUS_PENDING:
                    binding.tvStatusBadge.setText("CHỜ DUYỆT");
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.warning));
                    binding.layoutActions.setVisibility(View.VISIBLE);
                    binding.tvRejectionReason.setVisibility(View.GONE);
                    break;
                case Booking.STATUS_CONFIRMED:
                    binding.tvStatusBadge.setText("ĐÃ CHẤP NHẬN");
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.teal));
                    binding.layoutActions.setVisibility(View.GONE);
                    binding.tvRejectionReason.setVisibility(View.GONE);
                    binding.btnCheckIn.setVisibility(View.VISIBLE);
                    binding.btnCheckIn.setOnClickListener(v -> { if (!busy) listener.onCheckIn(item); });
                    break;
                case Booking.STATUS_CHECKED_IN:
                    binding.tvStatusBadge.setText("ĐÃ CHECK-IN");
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.teal_dark));
                    binding.layoutActions.setVisibility(View.GONE);
                    binding.tvRejectionReason.setVisibility(View.GONE);
                    break;
                case Booking.STATUS_COMPLETED:
                    binding.tvStatusBadge.setText("HOÀN TẤT");
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.teal));
                    binding.layoutActions.setVisibility(View.GONE);
                    binding.tvRejectionReason.setVisibility(View.GONE);
                    break;
                case Booking.STATUS_REJECTED:
                    binding.tvStatusBadge.setText("ĐÃ TỪ CHỐI");
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.danger));
                    binding.layoutActions.setVisibility(View.GONE);
                    if (item.getRejectionReason() != null && !item.getRejectionReason().isEmpty()) {
                        binding.tvRejectionReason.setVisibility(View.VISIBLE);
                        binding.tvRejectionReason.setText("Lý do từ chối: " + item.getRejectionReason());
                    } else {
                        binding.tvRejectionReason.setVisibility(View.GONE);
                    }
                    break;
                case Booking.STATUS_CANCELLED:
                    binding.tvStatusBadge.setText("ĐÃ HỦY");
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
                    binding.layoutActions.setVisibility(View.GONE);
                    binding.tvRejectionReason.setVisibility(View.GONE);
                    binding.layoutCancellationInfo.setVisibility(View.VISIBLE);
                    boolean byHost = item.cancelledByUid != null && item.cancelledByUid.equals(item.ownerUid);
                    String who = byHost ? "Chủ phòng (Host) đã hủy" : "Khách thuê đã hủy";
                    int rate = item.cancellationRefundPercent;
                    String refundText = rate == 100 ? "Hoàn 100% cọc (Hủy trước hạn quy định)"
                            : rate == 50 ? "Hoàn 50% cọc (Hủy cận ngày)"
                            : "0% cọc (Hủy sát giờ / không hoàn tiền trả trước)";
                    StringBuilder body = new StringBuilder();
                    body.append("Người thực hiện: ").append(who);
                    if (item.cancelledAt != null) {
                        body.append("\nThời điểm hủy: ").append(dateFormat.format(item.cancelledAt));
                    }
                    body.append("\nMức hoàn theo chính sách: ").append(refundText);
                    if (item.getRejectionReason() != null && !item.getRejectionReason().isEmpty()) {
                        body.append("\nLý do: ").append(item.getRejectionReason());
                    }
                    body.append("\nTrạng thái lịch: Đã giải phóng giữ chỗ.");
                    binding.tvCancellationBody.setText(body.toString());
                    break;
                default:
                    binding.tvStatusBadge.setText(vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils.bookingStatus(status));
                    binding.layoutActions.setVisibility(View.GONE);
                    binding.tvRejectionReason.setVisibility(View.GONE);
                    break;
            }

            // Check if can complete after check-in or after end time
            long now = System.currentTimeMillis();
            if ((Booking.STATUS_CONFIRMED.equals(status) || Booking.STATUS_CHECKED_IN.equals(status))
                    && item.endAt != null && item.endAt.getTime() <= now) {
                binding.btnProgress.setVisibility(View.VISIBLE);
                binding.btnProgress.setText("Hoàn tất lượt thuê (COMPLETED)");
                binding.btnProgress.setOnClickListener(v -> { if (!busy) listener.onProgress(item, Booking.STATUS_COMPLETED); });
            }

            binding.btnAccept.setOnClickListener(v -> {
                if (listener != null) listener.onAccept(item);
            });

            binding.btnReject.setOnClickListener(v -> {
                if (listener != null) listener.onReject(item);
            });
        }
    }
}
