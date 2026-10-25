package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

/*
    Một tin nhắn trong subcollection messages của hội thoại.
    Tin được tạo cùng cập nhật preview và bộ đếm của conversation trong transaction.
 */
public class ChatMessage {
    public String id = "", senderUid = "", text = "", roomId = "", roomName = "";
    public long sequence;
    @ServerTimestamp public Date createdAt;
    public ChatMessage() {}
}
