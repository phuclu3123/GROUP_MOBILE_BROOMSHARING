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
    private final List<RentalPlace> references=new ArrayList<>();
    private final Consumer<RentalPlace> referenceClick;
    private MapPoint origin = MapArea.CENTER;

    public MapOfficeAdapter(Consumer<RoomListing> click,Consumer<RentalPlace> referenceClick) {
        this.click = click;
        this.referenceClick=referenceClick;
    }

    public void submit(List<RoomListing> rooms, List<RentalPlace> publicPlaces, MapPoint point) {
        items.clear();
        items.addAll(rooms);
        references.clear();references.addAll(publicPlaces);
        origin = point;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(
                LayoutInflater.from(parent.getContext())
                        .inflate(vn.edu.ueh.phuclu.broomsharing.R.layout.item_map_office, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        if(position>=items.size()){
            RentalPlace p=references.get(position-items.size());
            double meters=MapArea.distance(origin,new MapPoint(p.location.getLatitude(),p.location.getLongitude(),p.name));
            holder.title.setText(p.name);
            holder.subtitle.setText(String.format(Locale.getDefault(),"%.0f m · %s\nTham khảo từ tin công khai · Chưa xác nhận còn trống",meters,p.address));
            holder.itemView.setOnClickListener(v->referenceClick.accept(p));return;
        }
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
        return items.size()+references.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title, subtitle;

        Holder(View view) {
            super(view);
            title = view.findViewById(vn.edu.ueh.phuclu.broomsharing.R.id.mapOfficeTitle);
            subtitle = view.findViewById(vn.edu.ueh.phuclu.broomsharing.R.id.mapOfficeSubtitle);
            view.setFocusable(true);
        }
    }
}
