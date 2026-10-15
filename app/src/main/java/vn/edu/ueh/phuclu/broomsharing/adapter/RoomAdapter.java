package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

import java.util.List;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.ViewHolder> {

    public interface OnRoomClickListener {
        void onClick(Room room);
    }

    private final List<Room> roomList;
    private final OnRoomClickListener listener;

    public RoomAdapter(List<Room> roomList, OnRoomClickListener listener) {
        this.roomList = roomList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view =
                LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Room r = roomList.get(position);
        holder.tvRoomName.setText(r.getRoomName());

        String bName =
                (r.getAddress() != null && !r.getAddress().isEmpty())
                        ? r.getAddress()
                        : "Chưa cập nhật địa chỉ";
        holder.tvBuildingName.setText(bName);

        String specs = "Sức chứa: " + r.getCapacity() + " người • " + (int) r.getArea() + " m²";
        holder.tvRoomSpecs.setText(specs);

        if (Room.MODE_FIXED.equals(r.getActiveMode())) {
            holder.tvModeBadge.setText("THÁNG / QUÝ");
            holder.tvRoomPrice.setText(
                    r.getPricePerMonth() > 0
                            ? FormatUtils.formatCurrency(r.getPricePerMonth()) + "/tháng"
                            : FormatUtils.formatCurrency(r.getPricePerQuarter()) + "/quý");
        } else {
            holder.tvModeBadge.setText("THEO GIỜ");
            holder.tvRoomPrice.setText(FormatUtils.formatCurrency(r.getPricePerHour()) + "/giờ");
        }

        // Status Badge
        String status = r.getApprovalStatus();
        if (Room.STATUS_APPROVED.equals(status)) {
            holder.tvStatusBadge.setText(
                    "ACTIVE".equals(r.getListingStatus())
                            ? "ĐÃ DUYỆT • ĐANG HIỂN THỊ"
                            : "ĐÃ DUYỆT • ĐANG ẨN");
            holder.tvStatusBadge.setTextColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.success));
        } else if (Room.STATUS_PENDING_REVIEW.equals(status)) {
            holder.tvStatusBadge.setText("CHỜ DUYỆT");
            holder.tvStatusBadge.setTextColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.warning));
        } else if (Room.STATUS_REJECTED.equals(status)) {
            holder.tvStatusBadge.setText(
                    "TỪ CHỐI: " + (r.getRejectionReason() == null ? "" : r.getRejectionReason()));
            holder.tvStatusBadge.setTextColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.danger));
        } else {
            holder.tvStatusBadge.setText("BẢN NHÁP");
            holder.tvStatusBadge.setTextColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
        }

        // Image
        if (r.getImageUrls() != null && !r.getImageUrls().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(r.getImageUrls().get(0))
                    .centerCrop()
                    .placeholder(R.drawable.home_feature)
                    .into(holder.ivRoomThumb);
        } else {
            holder.ivRoomThumb.setImageResource(R.drawable.home_feature);
        }

        holder.itemView.setOnClickListener(
                v -> {
                    if (listener != null) listener.onClick(r);
                });
    }

    @Override
    public int getItemCount() {
        return roomList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivRoomThumb;
        TextView tvStatusBadge, tvModeBadge, tvRoomName, tvBuildingName, tvRoomSpecs, tvRoomPrice;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivRoomThumb = itemView.findViewById(R.id.ivRoomThumb);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            tvModeBadge = itemView.findViewById(R.id.tvModeBadge);
            tvRoomName = itemView.findViewById(R.id.tvRoomName);
            tvBuildingName = itemView.findViewById(R.id.tvBuildingName);
            tvRoomSpecs = itemView.findViewById(R.id.tvRoomSpecs);
            tvRoomPrice = itemView.findViewById(R.id.tvRoomPrice);
        }
    }
}
