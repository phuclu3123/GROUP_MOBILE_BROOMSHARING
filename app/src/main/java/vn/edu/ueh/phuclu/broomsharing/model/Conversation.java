package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.*;

/** One private renter/host thread. Receipt counters count incoming messages only. */
public class Conversation {
    public String id = "", guestUid = "", hostUid = "", guestName = "", hostName = "";
    public List<String> memberUids = new ArrayList<>();
    public String roomId = "", roomName = "", lastText = "", lastSenderUid = "", lastMessageId = "";
    public long sequence, guestReceived, hostReceived, guestRead, hostRead;
    public String bookingId = "";
    @ServerTimestamp public Date createdAt;
    @ServerTimestamp public Date updatedAt;
    public Conversation() {}
    @Exclude public long unread(String uid) {
        return Math.max(0, guestUid.equals(uid) ? guestReceived - guestRead : hostReceived - hostRead);
    }
    @Exclude public long received(String uid) { return guestUid.equals(uid) ? guestReceived : hostReceived; }
    @Exclude public String peerName(String uid) { return guestUid.equals(uid) ? hostName : guestName; }
}
