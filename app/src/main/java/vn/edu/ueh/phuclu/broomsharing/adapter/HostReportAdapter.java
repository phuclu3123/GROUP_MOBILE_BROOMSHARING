package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.databinding.ItemHostReportBinding;
import vn.edu.ueh.phuclu.broomsharing.model.ReviewItem;

public class HostReportAdapter extends RecyclerView.Adapter<HostReportAdapter.ViewHolder> {
    public interface OnExplainClickListener {
        void onExplain(ReviewItem item);
    }

    private final List<ReviewItem> list = new ArrayList<>();
    private final OnExplainClickListener listener;

    public HostReportAdapter(OnExplainClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<ReviewItem> data) {
        list.clear();
        if (data != null) list.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemHostReportBinding.inflate(
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

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemHostReportBinding b;

        ViewHolder(ItemHostReportBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(ReviewItem item) {
            b.tvReportTitle.setText(item.title != null ? item.title : "Báo cáo #" + item.id);
            b.tvReportDetails.setText(item.details != null ? item.details : "");

            String status = item.status != null ? item.status.toUpperCase() : "OPEN";
            if ("RESOLVED".equals(status)) {
                b.tvReportStatus.setText("ĐÃ GIẢI QUYẾT");
                b.tvReportStatus.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.teal));
            } else if ("DISMISSED".equals(status)) {
                b.tvReportStatus.setText("ĐÃ BÁC BỎ");
                b.tvReportStatus.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            } else {
                b.tvReportStatus.setText("CHỜ XỬ LÝ");
                b.tvReportStatus.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.warning));
            }

            b.btnExplain.setOnClickListener(v -> {
                if (listener != null) listener.onExplain(item);
            });
        }
    }
}
