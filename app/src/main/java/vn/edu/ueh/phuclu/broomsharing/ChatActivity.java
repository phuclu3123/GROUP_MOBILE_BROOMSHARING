package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import androidx.core.view.*;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import vn.edu.ueh.phuclu.broomsharing.adapter.ChatMessageAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityChatBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.ChatViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;

public class ChatActivity extends SessionActivity {
    private ActivityChatBinding b; private ChatViewModel model;
    private String id, roomId, draft=""; private long lastSequence;
    @Override protected int pageLayout() { return R.layout.activity_chat; }
    @Override protected void onCreate(Bundle state) { if(state!=null)draft=state.getString("chat.draft","");super.onCreate(state); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("chat.draft",b==null?draft:b.chatInput.getText().toString());super.onSaveInstanceState(out); }
    @Override protected void onProfileReady(UserProfile profile) {
        if(profile.getUserRole()==UserRole.ADMIN){finish();return;}
        b=ActivityChatBinding.bind(((ViewGroup)findViewById(android.R.id.content)).getChildAt(0));
        model=new ViewModelProvider(this).get(ChatViewModel.class);
        id=getIntent().getStringExtra("conversationId"); roomId=getIntent().getStringExtra(SpaceDetailActivity.ROOM_ID);
        b.chatToolbar.setNavigationOnClickListener(v->finish());
        ViewCompat.setOnApplyWindowInsetsListener(b.workspaceRoot,(view,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        }); ViewCompat.requestApplyInsets(b.workspaceRoot);
        ChatMessageAdapter adapter=new ChatMessageAdapter(profile.uid,this::openRoom);
        LinearLayoutManager layout=new LinearLayoutManager(this);layout.setStackFromEnd(true);
        b.messageList.setLayoutManager(layout); b.messageList.setAdapter(adapter);
        b.messageList.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener(){
            @Override public void onScrolled(androidx.recyclerview.widget.RecyclerView view,int dx,int dy){model.viewingLatest(!view.canScrollVertically(1));}
        });
        b.chatInput.setText(draft); b.chatSend.setEnabled(false);
        b.chatSend.setOnClickListener(v->model.send(b.chatInput.getText().toString(),roomId));
        b.chatOlder.setOnClickListener(v->model.older());
        b.chatRetry.setOnClickListener(v->{
            if(id!=null){model.stop();model.watch(id);}
            else if(getIntent().getStringExtra("bookingId")!=null)model.openBooking(getIntent().getStringExtra("bookingId"));
            else if(roomId!=null)model.openRoom(roomId);
        });
        model.opened.observe(this,value->{if(value!=null){id=value;getIntent().putExtra("conversationId",id);model.watch(id);}});
        model.conversation.observe(this,c->{
            b.chatToolbar.setTitle(c.peerName(profile.uid));
            if(roomId==null)roomId=c.roomId;
            b.chatRoom.setText(roomId.equals(c.roomId)?c.roomName:getIntent().getStringExtra("roomName")==null?"Xem phòng đang trao đổi":getIntent().getStringExtra("roomName"));
            b.chatRoom.setOnClickListener(v->openRoom(roomId));b.chatSend.setEnabled(true);
        });
        model.messages.observe(this,state->{
            if(state==null)return;
            b.chatStatus.setText(state.loading?getString(R.string.chat_loading):state.error==null?"":state.error);
            b.chatRetry.setVisibility(state.error==null?View.GONE:View.VISIBLE);
            if(state.error!=null)b.chatSend.setEnabled(false);
            if(state.data!=null){
                boolean atBottom=!b.messageList.canScrollVertically(1);
                adapter.submit(state.data);b.chatSend.setEnabled(model.conversation.getValue()!=null);
                b.chatOlder.setVisibility(model.canLoadOlder(state.data.size())?View.VISIBLE:View.GONE);
                long sequence=state.data.isEmpty()?0:state.data.get(state.data.size()-1).sequence;
                if(atBottom&&sequence!=lastSequence)b.messageList.scrollToPosition(Math.max(0,adapter.getItemCount()-1));
                lastSequence=sequence;
                b.messageList.post(()->{if(getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))model.viewingLatest(!b.messageList.canScrollVertically(1));});
            }
        });
        model.action.observe(this,state->{
            if(state==null)return;
            b.chatSend.setEnabled(!state.loading&&model.conversation.getValue()!=null);
            b.chatInput.setEnabled(!state.loading);
            b.chatStatus.setText(state.loading?"Đang xử lý…":state.error==null?"":state.error+" Bạn có thể thử lại.");
            b.chatRetry.setVisibility(state.error==null?View.GONE:View.VISIBLE);
            if(state.data!=null){b.chatInput.setText("");model.action.setValue(null);}
        });
        if(id!=null)model.watch(id);
        else if(getIntent().getStringExtra("bookingId")!=null)model.openBooking(getIntent().getStringExtra("bookingId"));
        else if(roomId!=null)model.openRoom(roomId);
        else{b.chatStatus.setText("Thiếu hội thoại. Hãy mở từ Tin nhắn hoặc chi tiết phòng.");}
    }
    private void openRoom(String room){startActivity(new Intent(this,SpaceDetailActivity.class).putExtra(SpaceDetailActivity.ROOM_ID,room));}
    @Override protected void onStart(){super.onStart();if(model!=null&&id!=null)model.watch(id);}
    @Override protected void onStop(){if(model!=null)model.stop();super.onStop();}
}
