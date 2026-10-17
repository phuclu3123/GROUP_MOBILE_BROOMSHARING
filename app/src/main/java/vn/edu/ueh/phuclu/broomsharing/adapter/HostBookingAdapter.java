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
    }

    private final List<Booking> list = new ArrayList<>();
    private final OnBookingActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public HostBookingAdapter(OnBookingActionListener listener) {
        this.listener = listener;
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
                    break;
                default:
                    binding.tvStatusBadge.setText(status);
                    binding.layoutActions.setVisibility(View.GONE);
                    binding.tvRejectionReason.setVisibility(View.GONE);
                    break;
            }

            // Click events
            binding.btnAccept.setOnClickListener(v -> {
                if (listener != null) listener.onAccept(item);
            });

            binding.btnReject.setOnClickListener(v -> {
                if (listener != null) listener.onReject(item);
            });
        }
    }
}
