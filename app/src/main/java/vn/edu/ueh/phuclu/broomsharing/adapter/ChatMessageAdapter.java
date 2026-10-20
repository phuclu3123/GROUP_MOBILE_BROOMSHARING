package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.*;
import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
import java.util.function.Consumer;
import vn.edu.ueh.phuclu.broomsharing.databinding.ItemChatMessageBinding;
import vn.edu.ueh.phuclu.broomsharing.model.ChatMessage;
import vn.edu.ueh.phuclu.broomsharing.utils.TimeUtils;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.Holder> {
    private final List<ChatMessage> rows = new ArrayList<>();
    private final String uid; private final Consumer<String> openRoom;
    public ChatMessageAdapter(String uid, Consumer<String> openRoom) { this.uid=uid; this.openRoom=openRoom; }
    public void submit(List<ChatMessage> values) { rows.clear(); rows.addAll(values); notifyDataSetChanged(); }
    @Override public int getItemCount() { return rows.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p,int type) {
        return new Holder(ItemChatMessageBinding.inflate(LayoutInflater.from(p.getContext()),p,false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h,int position) {
        ChatMessage m = rows.get(position); boolean mine=uid.equals(m.senderUid);
        h.b.getRoot().setGravity(mine ? Gravity.END : Gravity.START);
        h.b.messageBody.setText(m.text);
        h.b.messageBody.setBackgroundResource(mine ? vn.edu.ueh.phuclu.broomsharing.R.drawable.bg_chat_outgoing : vn.edu.ueh.phuclu.broomsharing.R.drawable.bg_chat_incoming);
        h.b.messageBody.setTextColor(Color.parseColor("#183B53"));
        h.b.messageTime.setText(m.createdAt == null ? "Đang đồng bộ…" : TimeUtils.formatVietnam(m.createdAt));
        boolean context=!m.roomId.isEmpty()&&(position==0||!m.roomId.equals(rows.get(position-1).roomId));
        h.b.messageContext.setVisibility(context ? View.VISIBLE : View.GONE);
        h.b.messageContext.setText("Về phòng: " + m.roomName);
        h.b.messageContext.setOnClickListener(v -> openRoom.accept(m.roomId));
    }
    static class Holder extends RecyclerView.ViewHolder {
        final ItemChatMessageBinding b;
        Holder(ItemChatMessageBinding b) { super(b.getRoot()); this.b=b; }
    }
}
