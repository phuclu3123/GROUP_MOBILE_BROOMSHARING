package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class ChatMessage {
    public String id = "", senderUid = "", text = "", roomId = "", roomName = "";
    public long sequence;
    @ServerTimestamp public Date createdAt;
    public ChatMessage() {}
}
