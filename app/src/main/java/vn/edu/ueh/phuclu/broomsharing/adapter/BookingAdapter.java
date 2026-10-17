package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class BookingAdapter
        extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

    public interface OnCancelListener {
        void onCancel(Booking booking);
    }

    private final List<Booking> items = new ArrayList<>();
    private final OnCancelListener cancelListener;

    public BookingAdapter(OnCancelListener listener) {
        cancelListener = listener;
    }

    public void setItems(List<Booking> values) {
        items.clear();

        if (values != null) {
            items.addAll(values);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_booking, parent, false);

        return new BookingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull BookingViewHolder holder,
            int position) {

        Booking booking = items.get(position);

        holder.roomName.setText(safe(booking.roomName));

        holder.status.setText(
                statusLabel(booking.status)
                        + " · "
                        + paymentLabel(booking.paymentStatus));

        holder.address.setText(safe(booking.roomAddress));

        holder.range.setText(
                formatDateTime(booking.startAt)
                        + "\nđến "
                        + formatDateTime(booking.endAt));

        holder.code.setText("Mã đặt chỗ: " + safe(booking.id));

        holder.total.setText(
                "Tổng dự kiến: "
                        + FormatUtils.formatCurrency(booking.totalAmount));

        if (booking.depositAmount > 0) {
            holder.deposit.setText(
                    "Tiền cọc: "
                            + FormatUtils.formatCurrency(booking.depositAmount)
                            + " ("
                            + paymentLabel(booking.paymentStatus)
                            + ")");
        } else {
            holder.deposit.setText("Không yêu cầu tiền cọc");
        }

        boolean canCancel = "PENDING".equals(booking.status);

        holder.cancel.setVisibility(
                canCancel ? View.VISIBLE : View.GONE);

        holder.cancel.setOnClickListener(v -> {
            if (cancelListener != null) {
                cancelListener.onCancel(booking);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class BookingViewHolder extends RecyclerView.ViewHolder {

        final TextView roomName;
        final TextView status;
        final TextView address;
        final TextView range;
        final TextView code;
        final TextView total;
        final TextView deposit;
        final MaterialButton cancel;

        BookingViewHolder(@NonNull View itemView) {
            super(itemView);

            roomName = itemView.findViewById(R.id.tvBookingRoomName);
            status = itemView.findViewById(R.id.tvBookingStatus);
            address = itemView.findViewById(R.id.tvBookingAddress);
            range = itemView.findViewById(R.id.tvBookingRange);
            code = itemView.findViewById(R.id.tvBookingCode);
            total = itemView.findViewById(R.id.tvBookingTotal);
            deposit = itemView.findViewById(R.id.tvBookingDeposit);
            cancel = itemView.findViewById(R.id.btnCancelBooking);
        }
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Chưa cập nhật";
        }

        return value;
    }

    private static String statusLabel(String status) {
        if ("PENDING".equals(status)) {
            return "Chờ chủ phòng xác nhận";
        }

        if ("CONFIRMED".equals(status)) {
            return "Đã xác nhận";
        }

        if ("CANCELLED".equals(status)) {
            return "Đã hủy";
        }

        if ("COMPLETED".equals(status)) {
            return "Hoàn tất";
        }

        if ("REJECTED".equals(status)) {
            return "Bị từ chối";
        }

        return safe(status);
    }

    private static String paymentLabel(String status) {
        if ("UNPAID".equals(status) || status == null) {
            return "Chưa thanh toán";
        }

        if ("DEMO_PENDING".equals(status)) {
            return "Thanh toán demo đang chờ";
        }

        if ("SANDBOX_PAID".equals(status)) {
            return "Đã thanh toán sandbox";
        }

        if ("PAID".equals(status)) {
            return "Đã thanh toán";
        }

        return status;
    }

    private static String formatDateTime(Date date) {
        if (date == null) {
            return "Chưa cập nhật";
        }

        SimpleDateFormat format = new SimpleDateFormat(
                "HH:mm · EEEE, dd/MM/yyyy",
                new Locale("vi", "VN"));

        format.setTimeZone(
                TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        return format.format(date);
    }
}