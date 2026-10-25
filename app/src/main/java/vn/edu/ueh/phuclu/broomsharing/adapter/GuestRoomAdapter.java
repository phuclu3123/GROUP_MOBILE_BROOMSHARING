package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.Objects;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.RoomListing;
import vn.edu.ueh.phuclu.broomsharing.utils.FormatUtils;

/** Adapted from Truc's RoomAdapter, using the shared Host Room model. */
/*
    Hiển thị phòng ở trang tìm kiếm của người thuê.
    Giá và trạng thái ở đây phục vụ hiển thị; Repository vẫn kiểm lại trước khi đặt.
 */
public class GuestRoomAdapter extends ListAdapter<RoomListing, GuestRoomAdapter.Holder> {
    public interface Click {
        void open(RoomListing room);
    }

    private final Click click;

    public GuestRoomAdapter(Click click) {
        super(
                new DiffUtil.ItemCallback<RoomListing>() {
                    public boolean areItemsTheSame(@NonNull RoomListing a, @NonNull RoomListing b) {
                        return a.room.getId().equals(b.room.getId());
                    }

                    public boolean areContentsTheSame(
                            @NonNull RoomListing a, @NonNull RoomListing b) {
                        Room x = a.room, y = b.room;
                        return Objects.equals(x.getRoomName(), y.getRoomName())
                                && Objects.equals(x.getAddress(), y.getAddress())
                                && Objects.equals(x.getRoomType(), y.getRoomType())
                                && Objects.equals(x.getActiveMode(), y.getActiveMode())
                                && Objects.equals(x.getImageUrls(), y.getImageUrls())
                                && Objects.equals(
                                        a.building.getDistrict(), b.building.getDistrict())
                                && x.getCapacity() == y.getCapacity()
                                && Double.compare(x.getArea(), y.getArea()) == 0
                                && Double.compare(x.getPricePerHour(), y.getPricePerHour()) == 0
                                && Double.compare(x.getPricePerMonth(), y.getPricePerMonth()) == 0
                                && Double.compare(x.getPricePerQuarter(), y.getPricePerQuarter())
                                        == 0;
                    }
                });
        this.click = click;
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    @NonNull
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_room_card, parent, false));
    }

    public void onBindViewHolder(@NonNull Holder h, int position) {
        RoomListing item = getItem(position);
        Room r = item.room;
        h.name.setText(r.getRoomName());
        h.location.setText(
                (item.building.getDistrict() == null ? "" : item.building.getDistrict() + " · ")
                        + r.getAddress());
        int unit =
                Room.MODE_HOURLY.equals(r.getActiveMode())
                        ? R.string.guest_price_hour
                        : r.getPricePerMonth() > 0
                                ? R.string.guest_price_month
                                : R.string.guest_price_quarter;
        double amount =
                Room.MODE_HOURLY.equals(r.getActiveMode())
                        ? r.getPricePerHour()
                        : r.getPricePerMonth() > 0 ? r.getPricePerMonth() : r.getPricePerQuarter();
        h.price.setText(
                h.itemView.getContext().getString(unit, FormatUtils.formatCurrency(amount)));
        h.capacity.setText(
                h.itemView
                        .getContext()
                        .getString(
                                R.string.guest_specs,
                                r.getCapacity(),
                                String.valueOf(r.getArea()),
                                r.getRoomType()));
        String url =
                r.getImageUrls() == null || r.getImageUrls().isEmpty()
                        ? null
                        : r.getImageUrls().get(0);
        Glide.with(h.itemView)
                .load(url)
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(h.image);
        h.itemView.setOnClickListener(v -> click.open(item));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name, location, price, capacity;
        final ImageView image;

        Holder(View view) {
            super(view);
            name = view.findViewById(R.id.tvRoomName);
            location = view.findViewById(R.id.tvLocation);
            price = view.findViewById(R.id.tvPrice);
            capacity = view.findViewById(R.id.tvCapacity);
            image = view.findViewById(R.id.imgRoomThumb);
        }
    }
}
