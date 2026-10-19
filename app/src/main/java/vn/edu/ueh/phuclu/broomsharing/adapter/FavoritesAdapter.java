package vn.edu.ueh.phuclu.broomsharing.adapter;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import vn.edu.ueh.phuclu.broomsharing.databinding.ItemFavoriteBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Favorite;
import java.util.*;
import java.util.function.Consumer;
public class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.Holder> {
    private final List<Favorite> items=new ArrayList<>();
    private final Consumer<Favorite> open,remove;
    private boolean busy;
    public FavoritesAdapter(Consumer<Favorite> open,Consumer<Favorite> remove){this.open=open;this.remove=remove;}
    public void setData(List<Favorite> value){items.clear();items.addAll(value);notifyDataSetChanged();}
    public void setBusy(boolean value){busy=value;notifyDataSetChanged();}
    @Override public int getItemCount(){return items.size();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemFavoriteBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder h,int position){Favorite item=items.get(position);h.b.title.setText(item.roomName==null||item.roomName.isEmpty()?item.roomId:item.roomName);h.b.open.setOnClickListener(v->open.accept(item));h.b.remove.setEnabled(!busy);h.b.remove.setOnClickListener(v->{if(!busy)remove.accept(item);});}
    static class Holder extends RecyclerView.ViewHolder {final ItemFavoriteBinding b;Holder(ItemFavoriteBinding b){super(b.getRoot());this.b=b;}}
}
