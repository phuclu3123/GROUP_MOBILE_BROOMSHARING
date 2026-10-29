package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.model.Building;

/*
    Hiển thị tòa nhà của Host và chuyển thao tác chỉnh sửa về Activity.
    Adapter chỉ giữ dữ liệu để bind View, không tự ghi Firestore.
 */
public class BuildingAdapter extends RecyclerView.Adapter<BuildingAdapter.ViewHolder> {

    public interface OnBuildingClickListener {
        void onClick(Building building);
    }

    private final List<Building> buildingList;
    private final OnBuildingClickListener listener;

    public BuildingAdapter(List<Building> buildingList, OnBuildingClickListener listener) {
        this.buildingList = buildingList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_building, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Building b = buildingList.get(position);
        holder.tvBuildingName.setText(b.getName());
        holder.tvBuildingDistrict.setText(
                b.getDistrict() != null ? b.getDistrict() : "Chưa cập nhật quận");
        holder.tvBuildingAddress.setText(
                b.getAddress() != null ? b.getAddress() : "Chưa có địa chỉ");
        holder.tvBuildingPhone.setText(
                "Hotline: " + (b.getContactPhone() != null ? b.getContactPhone() : "N/A"));

        holder.itemView.setOnClickListener(
                v -> {
                    if (listener != null) listener.onClick(b);
                });
    }

    @Override
    public int getItemCount() {
        return buildingList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBuildingName, tvBuildingDistrict, tvBuildingAddress, tvBuildingPhone;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBuildingName = itemView.findViewById(R.id.tvBuildingName);
            tvBuildingDistrict = itemView.findViewById(R.id.tvBuildingDistrict);
            tvBuildingAddress = itemView.findViewById(R.id.tvBuildingAddress);
            tvBuildingPhone = itemView.findViewById(R.id.tvBuildingPhone);
        }
    }
}
