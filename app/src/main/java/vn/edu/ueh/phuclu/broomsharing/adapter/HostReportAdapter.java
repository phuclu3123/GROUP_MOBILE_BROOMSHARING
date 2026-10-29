package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.*;

import androidx.recyclerview.widget.RecyclerView;

import vn.edu.ueh.phuclu.broomsharing.databinding.ItemHostReportBinding;
import vn.edu.ueh.phuclu.broomsharing.model.RoomReport;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

import java.util.*;
import java.util.function.Consumer;

// Dùng thẻ giải trình của Hoàng với RoomReport, không ghép dữ liệu vào ReviewItem Admin.
public class HostReportAdapter extends RecyclerView.Adapter<HostReportAdapter.Holder> {
    private final List<RoomReport> rows = new ArrayList<>();
    private final Consumer<RoomReport> reply;
    private boolean busy;

    public HostReportAdapter(Consumer<RoomReport> reply) {
        this.reply = reply;
    }

    public void setData(List<RoomReport> values) {
        rows.clear();
        if (values != null) rows.addAll(values);
        notifyDataSetChanged();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        notifyDataSetChanged();
    }

    public Holder onCreateViewHolder(ViewGroup parent, int type) {
        return new Holder(
                ItemHostReportBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
    }

    public void onBindViewHolder(Holder holder, int position) {
        holder.bind(rows.get(position));
    }

    public int getItemCount() {
        return rows.size();
    }

    class Holder extends RecyclerView.ViewHolder {
        final ItemHostReportBinding b;

        Holder(ItemHostReportBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(RoomReport row) {
            b.tvReportTitle.setText(row.reason);
            b.tvReportStatus.setText(FormatUtils.reportStatus(row.status));
            b.tvReportDetails.setText(
                    row.description
                            + "\nPhòng: "
                            + row.roomId
                            + (row.hostResponse == null || row.hostResponse.isEmpty()
                                    ? ""
                                    : "\nPhản hồi: " + row.hostResponse)
                            + (row.resolution == null || row.resolution.isEmpty()
                                    ? ""
                                    : "\nKết luận: " + row.resolution));
            b.btnExplain.setVisibility("OPEN".equals(row.status) ? View.VISIBLE : View.GONE);
            b.btnExplain.setEnabled(!busy);
            b.btnExplain.setOnClickListener(v -> reply.accept(row));
        }
    }
}
