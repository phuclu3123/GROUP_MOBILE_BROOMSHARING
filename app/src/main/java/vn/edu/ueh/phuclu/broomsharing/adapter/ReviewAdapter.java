package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.*;

import androidx.recyclerview.widget.RecyclerView;

import vn.edu.ueh.phuclu.broomsharing.databinding.ItemReviewBinding;
import vn.edu.ueh.phuclu.broomsharing.model.ReviewItem;

import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.Holder> {
    public interface Action {
        void select(ReviewItem item, boolean approve);
    }

    private final List<ReviewItem> items;
    private final Action action;
    private final java.util.function.Consumer<ReviewItem> evidence;

    public ReviewAdapter(List<ReviewItem> items, Action action, java.util.function.Consumer<ReviewItem> evidence) {
        this.items = items;
        this.action = action;
        this.evidence = evidence;
    }

    public Holder onCreateViewHolder(ViewGroup parent, int type) {
        return new Holder(
                ItemReviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    public void onBindViewHolder(Holder h, int position) {
        ReviewItem item = items.get(position);
        h.b.title.setText(item.title);
        h.b.details.setText(item.details);
        h.b.viewDetails.setOnClickListener(v -> {
            android.widget.TextView body = new android.widget.TextView(v.getContext());
            int padding = (int) (20 * v.getResources().getDisplayMetrics().density);
            body.setPadding(padding, padding, padding, padding);
            body.setTextSize(16);
            body.setTextColor(androidx.core.content.ContextCompat.getColor(v.getContext(), vn.edu.ueh.phuclu.broomsharing.R.color.text_primary));
            body.setText(item.details);
            body.setTextIsSelectable(true);
            android.widget.ScrollView scroll = new android.widget.ScrollView(v.getContext());
            scroll.addView(body);
            new androidx.appcompat.app.AlertDialog.Builder(v.getContext()).setTitle(item.title)
                    .setView(scroll).setPositiveButton(android.R.string.ok, null).show();
        });
        h.b.evidence.setVisibility("VERIFICATION".equals(item.kind) ? View.VISIBLE : View.GONE);
        h.b.evidence.setOnClickListener(v -> evidence.accept(item));
        boolean readonly = "AUDIT".equals(item.kind);
        h.b.approve.setVisibility(readonly ? View.GONE : View.VISIBLE);
        h.b.reject.setVisibility(readonly || item.kind.endsWith("_STATUS") ? View.GONE : View.VISIBLE);
        h.b.cover.setVisibility(item.imageUrls.isEmpty() ? View.GONE : View.VISIBLE);
        if (!item.imageUrls.isEmpty()) {
            com.bumptech.glide.Glide.with(h.itemView).load(item.imageUrls.get(0)).into(h.b.cover);
            int[] image = {0};
            h.b.cover.setContentDescription(
                    "Ảnh 1/" + item.imageUrls.size() + "; chạm để xem ảnh tiếp theo");
            h.b.cover.setOnClickListener(
                    v -> {
                        image[0] = (image[0] + 1) % item.imageUrls.size();
                        com.bumptech.glide.Glide.with(h.itemView)
                                .load(item.imageUrls.get(image[0]))
                                .into(h.b.cover);
                        h.b.cover.setContentDescription(
                                "Ảnh " + (image[0] + 1) + "/" + item.imageUrls.size());
                    });
        }
        h.b.approve.setText("REPORT".equals(item.kind) ? "Đã xử lý" : "Duyệt");
        h.b.reject.setText("REPORT".equals(item.kind) ? "Bỏ qua" : "Từ chối");
        if ("HOST_STATUS".equals(item.kind)) h.b.approve.setText("BLOCKED".equals(item.status) ? "Mở khóa Host" : "Khóa Host");
        if ("ROOM_STATUS".equals(item.kind)) h.b.approve.setText("SUSPENDED".equals(item.status) ? "Gỡ khóa về ngừng rao" : "Khóa phòng");
        if ("VERIFICATION".equals(item.kind)) h.b.approve.setText("Duyệt nội bộ");
        boolean locking = "HOST_STATUS".equals(item.kind) && !"BLOCKED".equals(item.status)
                || "ROOM_STATUS".equals(item.kind) && !"SUSPENDED".equals(item.status);
        h.b.approve.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(h.itemView.getContext(), locking
                        ? vn.edu.ueh.phuclu.broomsharing.R.color.danger : vn.edu.ueh.phuclu.broomsharing.R.color.teal)));
        h.b.approve.setOnClickListener(v -> action.select(item, true));
        h.b.reject.setOnClickListener(v -> action.select(item, false));
    }

    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemReviewBinding b;

        Holder(ItemReviewBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
