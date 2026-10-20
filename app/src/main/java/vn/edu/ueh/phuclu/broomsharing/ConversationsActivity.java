package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.view.*;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import vn.edu.ueh.phuclu.broomsharing.adapter.ConversationAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityConversationsBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.ChatViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.*;

public class ConversationsActivity extends SessionActivity {
    private ChatViewModel model;
    @Override protected int pageLayout() { return R.layout.activity_conversations; }
    @Override protected void onProfileReady(UserProfile profile) {
        if (profile.getUserRole() == UserRole.ADMIN) { finish(); return; }
        ActivityConversationsBinding b=ActivityConversationsBinding.bind(((ViewGroup)findViewById(android.R.id.content)).getChildAt(0));
        model=new ViewModelProvider(this).get(ChatViewModel.class);
        boolean host=profile.getUserRole()==UserRole.HOST && !getIntent().getBooleanExtra("renterMode",false);
        findViewById(R.id.bottomNavigation).setVisibility(host ? View.GONE : View.VISIBLE);
        findViewById(R.id.managementNavigation).setVisibility(host ? View.VISIBLE : View.GONE);
        if (host) ManagementNavigation.attach(this,UserRole.HOST,R.id.host_messages,null);
        else GuestNavigation.attach(this,R.id.nav_messages);
        ManagementNavigation.applyInsets(this);
        ConversationAdapter adapter=new ConversationAdapter(profile.uid,c -> startActivity(new Intent(this,ChatActivity.class).putExtra("conversationId",c.id)));
        b.conversationList.setLayoutManager(new LinearLayoutManager(this)); b.conversationList.setAdapter(adapter);
        b.chatRetry.setOnClickListener(v -> { model.stop(); model.watchInbox(); });
        model.inbox.observe(this,state -> {
            if (state==null) return;
            b.chatRetry.setVisibility(state.error==null ? View.GONE : View.VISIBLE);
            b.chatStatus.setText(state.loading ? getString(R.string.chat_loading) : state.error!=null ? state.error : state.data==null || state.data.isEmpty() ? getString(R.string.chat_empty) : "Trao đổi trực tiếp với khách và người cho thuê");
            if(state.data!=null) adapter.submit(state.data);
            else if(state.error!=null) adapter.submit(java.util.Collections.emptyList());
        });
        model.watchInbox();
    }
}
