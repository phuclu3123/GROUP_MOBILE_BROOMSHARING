package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import vn.edu.ueh.phuclu.broomsharing.R;

import java.util.List;

public class SelectedImageAdapter extends RecyclerView.Adapter<SelectedImageAdapter.ViewHolder> {

    public interface OnRemoveClickListener {
        void onRemove(int position);
    }

    private final List<Uri> imageList;
    private final OnRemoveClickListener listener;
    private final java.util.function.Consumer<Uri> selection;

    public SelectedImageAdapter(List<Uri> images, OnRemoveClickListener listener) {
        this(images, listener, null);
    }
    public SelectedImageAdapter(List<Uri> images, OnRemoveClickListener listener, java.util.function.Consumer<Uri> selection) {
        this.imageList=images; this.listener=listener; this.selection=selection;
    }
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_selected_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Uri uri = imageList.get(position);
        if (selection != null) {
            float density=holder.itemView.getResources().getDisplayMetrics().density;
            ViewGroup.LayoutParams size=holder.itemView.getLayoutParams();size.width=Math.round(96*density);size.height=Math.round(68*density);holder.itemView.setLayoutParams(size);
            holder.itemView.setOnClickListener(v->selection.accept(uri));
        }
        Glide.with(holder.itemView.getContext())
                .load(uri)
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(holder.ivPreview);

        holder.btnRemove.setVisibility(listener == null ? View.GONE : View.VISIBLE);
        holder.btnRemove.setOnClickListener(
                v -> {
                    if (listener != null
                            && holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION)
                        listener.onRemove(holder.getBindingAdapterPosition());
                });
    }

    @Override
    public int getItemCount() {
        return imageList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPreview;
        ImageView btnRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPreview = itemView.findViewById(R.id.ivPreview);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
