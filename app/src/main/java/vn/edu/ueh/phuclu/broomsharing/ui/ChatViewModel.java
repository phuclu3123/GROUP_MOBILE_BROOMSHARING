package vn.edu.ueh.phuclu.broomsharing.ui;

import androidx.lifecycle.*;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

public class ChatViewModel extends ViewModel {
    private final ChatRepository repository = new ChatRepository();
    private final String sessionUid=repository.uid();
    public final MutableLiveData<UiState<List<Conversation>>> inbox = new MutableLiveData<>();
    public final MutableLiveData<UiState<List<ChatMessage>>> messages = new MutableLiveData<>();
    public final MutableLiveData<Conversation> conversation = new MutableLiveData<>();
    public final MutableLiveData<UiState<String>> action = new MutableLiveData<>();
    public final MutableLiveData<String> opened = new MutableLiveData<>();
    private ListenerRegistration inboxListener, threadListener, messageListener;
    private String threadId, pendingId, pendingText;
    private long seenReceived;
    private int limit = 50;
    private boolean viewingLatest;
    public void viewingLatest(boolean value) { viewingLatest=value; if(value)acknowledgeIfVisible(); }
    public String uid() { return sessionUid; }
    public void watchInbox() {
        if (inboxListener != null) return;
        if(sessionUid==null||!sessionUid.equals(repository.uid()))return;
        inbox.setValue(UiState.loading());
        inboxListener = repository.inbox(new ResultCallback<List<Conversation>>() {
            public void onSuccess(List<Conversation> value) { inbox.setValue(UiState.success(value)); }
            public void onError(Exception e) { inbox.setValue(UiState.error(e)); }
        });
    }
    public void openRoom(String roomId) {
        action.setValue(UiState.loading());
        repository.openRoom(roomId, new ResultCallback<String>() {
            public void onSuccess(String id) { action.setValue(null); opened.setValue(id); }
            public void onError(Exception e) { action.setValue(UiState.error(e)); }
        });
    }
    public void openBooking(String bookingId) {
        action.setValue(UiState.loading());
        repository.openBooking(bookingId,new ResultCallback<String>(){
            public void onSuccess(String id){action.setValue(null);opened.setValue(id);}
            public void onError(Exception e){action.setValue(UiState.error(e));}
        });
    }
    public void watch(String id) {
        if (threadListener != null) return;
        threadId = id;
        try {
            threadListener = repository.conversation(id, new ResultCallback<Conversation>() {
                public void onSuccess(Conversation c) { conversation.setValue(c); acknowledgeIfVisible(); }
                public void onError(Exception e) { messages.setValue(UiState.error(e)); }
            });
            watchMessages();
        } catch (Exception e) { messages.setValue(UiState.error(e)); }
    }
    private void watchMessages() {
        if (messageListener != null) messageListener.remove();
        messages.setValue(UiState.loading());
        messageListener = repository.messages(threadId, limit, new ResultCallback<List<ChatMessage>>() {
            public void onSuccess(List<ChatMessage> value) { messages.setValue(UiState.success(value)); acknowledgeIfVisible(); }
            public void onError(Exception e) { messages.setValue(UiState.error(e)); }
        });
    }
    private void acknowledgeIfVisible() {
        Conversation c = conversation.getValue(); UiState<List<ChatMessage>> rows = messages.getValue();
        if (!viewingLatest || sessionUid==null || !sessionUid.equals(repository.uid()) || c == null || rows == null || rows.data == null || rows.data.isEmpty()) return;
        // Do not acknowledge a message whose inbox update arrived before its message snapshot.
        if (rows.data.get(rows.data.size()-1).sequence < c.sequence) return;
        long received = c.received(uid());
        if (received <= seenReceived || c.unread(uid()) == 0) return;
        seenReceived = received;
        repository.markRead(threadId, received, new ResultCallback<String>() {
            public void onSuccess(String ignored) {}
            public void onError(Exception e) { seenReceived = 0; action.setValue(UiState.error(e)); }
        });
    }
    public void older() { limit += 50; watchMessages(); }
    public boolean canLoadOlder(int size) { return size >= limit; }
    public void send(String body, String roomId) {
        if(sessionUid==null||!sessionUid.equals(repository.uid())){action.setValue(UiState.error(new Exception("Phiên đăng nhập đã đổi. Hãy mở lại hội thoại.")));return;}
        UiState<String> state = action.getValue(); if (state != null && state.loading) return;
        String text = body.trim();
        if (!text.equals(pendingText)) { pendingId = UUID.randomUUID().toString(); pendingText = text; }
        action.setValue(UiState.loading());
        repository.send(threadId, pendingId, text, roomId, new ResultCallback<String>() {
            public void onSuccess(String id) { pendingId = null; pendingText = null; action.setValue(UiState.success(id)); }
            public void onError(Exception e) { action.setValue(UiState.error(e)); }
        });
    }
    public void stop() {
        viewingLatest=false;
        if (inboxListener != null) inboxListener.remove();
        if (threadListener != null) threadListener.remove();
        if (messageListener != null) messageListener.remove();
        inboxListener = threadListener = messageListener = null;
    }
    @Override protected void onCleared() { stop(); }
}
