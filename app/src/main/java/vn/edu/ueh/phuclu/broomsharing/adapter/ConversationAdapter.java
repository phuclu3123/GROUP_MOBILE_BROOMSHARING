package vn.edu.ueh.phuclu.broomsharing.adapter;

import android.view.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
import java.util.function.Consumer;
import vn.edu.ueh.phuclu.broomsharing.databinding.ItemConversationBinding;
import vn.edu.ueh.phuclu.broomsharing.model.Conversation;

/*
    Hiển thị hội thoại, tin cuối và số chưa đọc của đúng thành viên.
    Khi chọn một dòng, Activity mở hội thoại bằng ID đã lưu.
 */
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.Holder> {
    private final List<Conversation> rows = new ArrayList<>();
    private final String uid; private final Consumer<Conversation> open;
    public ConversationAdapter(String uid, Consumer<Conversation> open) { this.uid = uid; this.open = open; }
    public void submit(List<Conversation> values) { rows.clear(); rows.addAll(values); notifyDataSetChanged(); }
    @Override public int getItemCount() { return rows.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p,int type) {
        return new Holder(ItemConversationBinding.inflate(LayoutInflater.from(p.getContext()),p,false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h,int position) {
        Conversation c = rows.get(position);
        h.b.conversationName.setText(c.peerName(uid));
        h.b.conversationPreview.setText(c.lastText.isEmpty() ? "Chưa có tin nhắn" : (uid.equals(c.lastSenderUid) ? "Bạn: " : "") + c.lastText);
        h.b.conversationUnread.setVisibility(c.unread(uid) == 0 ? View.GONE : View.VISIBLE);
        h.b.conversationUnread.setText(c.unread(uid) + " tin chưa đọc");
        h.b.getRoot().setOnClickListener(v -> open.accept(c));
    }
    static class Holder extends RecyclerView.ViewHolder {
        final ItemConversationBinding b;
        Holder(ItemConversationBinding b) { super(b.getRoot()); this.b=b; }
    }
}
