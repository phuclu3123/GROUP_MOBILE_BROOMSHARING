package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ItemPropertyVerificationBinding;
import vn.edu.ueh.phuclu.broomsharing.model.PropertyVerification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Tái sử dụng thẻ hồ sơ từ nhánh Hoàng; chỉ hiển thị kết quả xét duyệt đã lưu.
public class PropertyVerificationAdapter
        extends RecyclerView.Adapter<PropertyVerificationAdapter.ViewHolder> {
    private final List<PropertyVerification> list = new ArrayList<>();
    private static final SimpleDateFormat DATE_FORMAT;

    static {
        DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        DATE_FORMAT.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    public PropertyVerificationAdapter() {}

    public void setData(List<PropertyVerification> data) {
        list.clear();
        if (data != null) list.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(
                ItemPropertyVerificationBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemPropertyVerificationBinding b;

        ViewHolder(ItemPropertyVerificationBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(PropertyVerification item) {
            String title =
                    item.roomName != null && !item.roomName.isEmpty()
                            ? item.roomName
                            : item.buildingName != null && !item.buildingName.isEmpty()
                                    ? item.buildingName
                                    : "Hồ sơ BĐS #"
                                            + (item.id != null
                                                    ? item.id.substring(
                                                            0, Math.min(8, item.id.length()))
                                                    : "");
            b.tvTargetTitle.setText(title);

            String typeText =
                    "LOCATION".equalsIgnoreCase(item.verificationType)
                            ? "Xác minh địa điểm"
                            : "PHOTOS".equalsIgnoreCase(item.verificationType)
                                    ? "Xác minh Ảnh thực tế không gian"
                                    : "Xác minh định danh";
            b.tvVerificationType.setText(typeText);

            b.tvDescription.setText(
                    item.description != null && !item.description.isEmpty()
                            ? item.description
                            : "Không có mô tả.");
            int count = item.evidenceRefs != null ? item.evidenceRefs.size() : 0;
            b.tvEvidenceCount.setText("Tệp đính kèm: " + count + " tệp");

            if (item.createdAt != null) {
                b.tvCreatedAt.setText("Gửi lúc: " + DATE_FORMAT.format(item.createdAt));
            } else {
                b.tvCreatedAt.setText("Thời gian: Vừa xong");
            }

            String status = item.status != null ? item.status.toUpperCase(java.util.Locale.ROOT) : "PENDING";
            if ("VERIFIED".equals(status) || "APPROVED".equals(status)) {
                b.tvStatusBadge.setText("ĐÃ DUYỆT");
                b.tvStatusBadge.setBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.teal));
                b.tvRejectionReason.setVisibility(View.GONE);
            } else if ("REJECTED".equals(status)) {
                b.tvStatusBadge.setText("TỪ CHỐI");
                b.tvStatusBadge.setBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.danger));
                if (item.rejectionReason != null && !item.rejectionReason.isEmpty()) {
                    b.tvRejectionReason.setText("Lý do từ chối: " + item.rejectionReason);
                    b.tvRejectionReason.setVisibility(View.VISIBLE);
                } else {
                    b.tvRejectionReason.setVisibility(View.GONE);
                }
            } else {
                b.tvStatusBadge.setText("CHỜ DUYỆT");
                b.tvStatusBadge.setBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.warning));
                b.tvRejectionReason.setVisibility(View.GONE);
            }
        }
    }
}
