package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.*;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.MapArea;

import java.util.*;
import java.util.function.Consumer;

/** Compact rows leave room for the map on a small phone. */
public class MapOfficeAdapter extends RecyclerView.Adapter<MapOfficeAdapter.Holder> {
    private final List<RoomListing> items = new ArrayList<>();
    private final Consumer<RoomListing> click;
    private MapPoint origin = MapArea.CENTER;

    public MapOfficeAdapter(Consumer<RoomListing> click) {
        this.click = click;
    }

    public void submit(List<RoomListing> rooms, MapPoint point) {
        items.clear();
        items.addAll(rooms);
        origin = point;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(
                LayoutInflater.from(parent.getContext())
                        .inflate(android.R.layout.simple_list_item_2, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        RoomListing room = items.get(position);
        com.google.firebase.firestore.GeoPoint coordinate = room.building.getLocation();
        double meters =
                MapArea.distance(
                        origin,
                        new MapPoint(coordinate.getLatitude(), coordinate.getLongitude(), ""));
        holder.title.setText(room.room.getRoomName());
        holder.subtitle.setText(
                String.format(Locale.getDefault(), "%.0f m · %s", meters, room.room.getAddress()));
        holder.itemView.setOnClickListener(v -> click.accept(room));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title, subtitle;

        Holder(View view) {
            super(view);
            title = view.findViewById(android.R.id.text1);
            subtitle = view.findViewById(android.R.id.text2);
            view.setFocusable(true);
        }
    }
}
