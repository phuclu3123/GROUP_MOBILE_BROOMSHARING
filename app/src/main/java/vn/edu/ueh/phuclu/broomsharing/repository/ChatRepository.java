package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.model.Booking;
import vn.edu.ueh.phuclu.broomsharing.model.ChatMessage;
import vn.edu.ueh.phuclu.broomsharing.model.Conversation;
import vn.edu.ueh.phuclu.broomsharing.model.PublicHost;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.utils.BookingPolicy;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.util.*;

/** Android SDK only. Every send atomically appends an immutable message and updates its inbox. */
/*
   Quản lý hội thoại và tin nhắn bằng Firebase Android SDK.
   Gửi tin phải ghi message, preview, sequence và received trong cùng transaction.
   Chỉ bên nhận cập nhật mốc đọc của chính mình; Admin không mặc định đọc chat.
*/
public class ChatRepository {
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public ChatRepository() {
        this(FirebaseHelper.getFirestore(), FirebaseAuth.getInstance());
    }

    public ChatRepository(FirebaseFirestore db, FirebaseAuth auth) {
        this.db = db;
        this.auth = auth;
    }

    public String uid() {
        return auth.getUid();
    }

    private String actor() {
        BookingPolicy.require(uid() != null, "Vui lòng đăng nhập lại.");
        return uid();
    }

    private DocumentReference thread(String id) {
        BookingPolicy.require(
                id != null && id.matches("[A-Za-z0-9_-]{1,128}:[A-Za-z0-9_-]{1,128}"),
                "Hội thoại không hợp lệ.");
        return db.collection("conversations").document(id);
    }

    public void openRoom(String roomId, ResultCallback<String> cb) {
        final String uid;
        try {
            uid = actor();
            BookingPolicy.id(roomId);
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        db.runTransaction(
                        tx -> {
                            Room room =
                                    tx.get(db.collection("rooms").document(roomId))
                                            .toObject(Room.class);
                            BookingPolicy.require(
                                    room != null
                                            && "APPROVED".equals(room.getApprovalStatus())
                                            && "ACTIVE".equals(room.getListingStatus()),
                                    "Phòng hiện không nhận liên hệ mới.");
                            BookingPolicy.require(
                                    !uid.equals(room.getOwnerUid()),
                                    "Bạn đang xem phòng của mình.");
                            String id = uid + ":" + room.getOwnerUid();
                            DocumentReference ref = thread(id);
                            DocumentSnapshot existing = tx.get(ref);
                            if (existing.exists()) return id;
                            DocumentSnapshot guest = tx.get(db.collection("users").document(uid));
                            PublicHost host =
                                    tx.get(
                                                    db.collection("publicHosts")
                                                            .document(room.getOwnerUid()))
                                            .toObject(PublicHost.class);
                            BookingPolicy.require(
                                    host != null && "ACTIVE".equals(host.status),
                                    "Host hiện không nhận liên hệ.");
                            Conversation c = new Conversation();
                            c.id = id;
                            c.guestUid = uid;
                            c.hostUid = room.getOwnerUid();
                            c.memberUids = Arrays.asList(uid, c.hostUid);
                            c.guestName = guest.getString("name");
                            c.hostName = host.name;
                            c.roomId = roomId;
                            c.roomName = room.getRoomName();
                            tx.set(ref, c);
                            return id;
                        })
                .addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(cb::onError);
    }

    public ListenerRegistration inbox(ResultCallback<List<Conversation>> cb) {
        return db.collection("conversations")
                .whereArrayContains("memberUids", actor())
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (rows, e) -> {
                            if (e != null) {
                                cb.onError(e);
                                return;
                            }
                            if (rows == null
                                    || rows.getMetadata().isFromCache()
                                    || rows.getMetadata().hasPendingWrites()) return;
                            List<Conversation> result = new ArrayList<>();
                            if (rows != null)
                                for (DocumentSnapshot doc : rows)
                                    result.add(doc.toObject(Conversation.class));
                            result.sort(
                                    Comparator.comparing(
                                            (Conversation c) -> c.updatedAt,
                                            Comparator.nullsLast(Comparator.reverseOrder())));
                            cb.onSuccess(result);
                        });
    }

    public void openBooking(String bookingId, ResultCallback<String> cb) {
        final String uid;
        try {
            uid = actor();
            BookingPolicy.id(bookingId);
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        db.runTransaction(
                        tx -> {
                            Booking b =
                                    tx.get(db.collection("bookings").document(bookingId))
                                            .toObject(Booking.class);
                            BookingPolicy.require(
                                    b != null && (uid.equals(b.guestUid) || uid.equals(b.ownerUid)),
                                    "Bạn không thuộc booking này.");
                            String id = b.guestUid + ":" + b.ownerUid;
                            DocumentReference ref = thread(id);
                            if (tx.get(ref).exists()) return id;
                            PublicHost host =
                                    tx.get(db.collection("publicHosts").document(b.ownerUid))
                                            .toObject(PublicHost.class);
                            BookingPolicy.require(
                                    host != null && "ACTIVE".equals(host.status),
                                    "Host hiện không nhận liên hệ.");
                            Conversation c = new Conversation();
                            c.id = id;
                            c.guestUid = b.guestUid;
                            c.hostUid = b.ownerUid;
                            c.memberUids = Arrays.asList(c.guestUid, c.hostUid);
                            c.guestName = b.guestName;
                            c.hostName = host.name;
                            c.roomId = b.roomId;
                            c.roomName = b.roomName;
                            c.bookingId = bookingId;
                            tx.set(ref, c);
                            return id;
                        })
                .addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(cb::onError);
    }

    public ListenerRegistration conversation(String id, ResultCallback<Conversation> cb) {
        return thread(id)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (doc, e) -> {
                            if (e != null) cb.onError(e);
                            else if (doc != null
                                    && (doc.getMetadata().isFromCache()
                                            || doc.getMetadata().hasPendingWrites())) return;
                            else if (doc == null || !doc.exists())
                                cb.onError(new Exception("Không tìm thấy hội thoại."));
                            else cb.onSuccess(doc.toObject(Conversation.class));
                        });
    }

    public ListenerRegistration messages(
            String id, int limit, ResultCallback<List<ChatMessage>> cb) {
        return thread(id)
                .collection("messages")
                .orderBy("sequence", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (rows, e) -> {
                            if (e != null) {
                                cb.onError(e);
                                return;
                            }
                            if (rows == null
                                    || rows.getMetadata().isFromCache()
                                    || rows.getMetadata().hasPendingWrites()) return;
                            List<ChatMessage> result = new ArrayList<>();
                            if (rows != null)
                                for (DocumentSnapshot doc : rows)
                                    result.add(doc.toObject(ChatMessage.class));
                            Collections.reverse(result);
                            cb.onSuccess(result);
                        });
    }

    public void send(
            String id, String messageId, String text, String roomId, ResultCallback<String> cb) {
        // Giữ nguyên messageId khi retry để cùng một thao tác không tạo hai tin.
        send(id, messageId, text, roomId, cb, true);
    }

    private void send(
            String id,
            String messageId,
            String text,
            String roomId,
            ResultCallback<String> cb,
            boolean retryFresh) {
        final String uid, body;
        final DocumentReference ref;
        try {
            uid = actor();
            body = text == null ? "" : text.trim();
            ref = thread(id);
            BookingPolicy.id(messageId);
            BookingPolicy.require(
                    !body.isEmpty() && body.length() <= 2000, "Tin nhắn cần từ 1 đến 2.000 ký tự.");
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        db.runTransaction(
                        tx -> {
                            Conversation c = tx.get(ref).toObject(Conversation.class);
                            BookingPolicy.require(
                                    c != null && c.memberUids.contains(uid),
                                    "Bạn không thuộc hội thoại này.");
                            DocumentReference msgRef =
                                    ref.collection("messages").document(messageId);
                            DocumentSnapshot previous = tx.get(msgRef);
                            // Đã ghi đúng người gửi/nội dung thì trả ID cũ, không tăng sequence và
                            // received lần nữa.
                            if (previous.exists()) {
                                BookingPolicy.require(
                                        uid.equals(previous.getString("senderUid"))
                                                && body.equals(previous.getString("text")),
                                        "Mã tin nhắn đã được sử dụng.");
                                return messageId;
                            }
                            Room room = null;
                            // Đọc nốt ngữ cảnh phòng trước lần ghi đầu tiên để đúng quy tắc
                            // transaction của SDK.
                            if (roomId != null && !roomId.isEmpty()) {
                                room =
                                        tx.get(
                                                        db.collection("rooms")
                                                                .document(BookingPolicy.id(roomId)))
                                                .toObject(Room.class);
                                BookingPolicy.require(
                                        room != null && c.hostUid.equals(room.getOwnerUid()),
                                        "Phòng không thuộc Host này.");
                            }
                            ChatMessage msg = new ChatMessage();
                            msg.id = messageId;
                            msg.senderUid = uid;
                            msg.text = body;
                            msg.sequence = c.sequence + 1;
                            if (room != null) {
                                msg.roomId = roomId;
                                msg.roomName = room.getRoomName();
                            }
                            tx.set(msgRef, msg);
                            // Message và preview/counter đi cùng nhau, tránh inbox báo có tin nhưng
                            // chưa có message.
                            Map<String, Object> update = new HashMap<>();
                            update.put("sequence", msg.sequence);
                            update.put("lastText", body);
                            update.put("lastSenderUid", uid);
                            update.put("lastMessageId", messageId);
                            update.put(
                                    uid.equals(c.guestUid) ? "hostReceived" : "guestReceived",
                                    uid.equals(c.guestUid)
                                            ? c.hostReceived + 1
                                            : c.guestReceived + 1);
                            update.put("updatedAt", FieldValue.serverTimestamp());
                            tx.update(ref, update);
                            return messageId;
                        })
                .addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(
                        e -> {
                            if (retryFresh
                                    && e instanceof FirebaseFirestoreException
                                    && ((FirebaseFirestoreException) e).getCode()
                                            == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                                send(id, messageId, text, roomId, cb, false);
                            else cb.onError(e);
                        });
    }

    public void markRead(String id, long seenReceived, ResultCallback<String> cb) {
        // Chỉ nâng mốc đọc của chính mình, không vượt số đã nhận hoặc giảm mốc khi callback cũ về
        // sau.
        final String uid;
        final DocumentReference ref;
        try {
            uid = actor();
            ref = thread(id);
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        db.runTransaction(
                        tx -> {
                            Conversation c = tx.get(ref).toObject(Conversation.class);
                            BookingPolicy.require(
                                    c != null && c.memberUids.contains(uid),
                                    "Hội thoại không hợp lệ.");
                            String key = uid.equals(c.guestUid) ? "guestRead" : "hostRead";
                            long old = uid.equals(c.guestUid) ? c.guestRead : c.hostRead;
                            long next = Math.max(old, Math.min(seenReceived, c.received(uid)));
                            if (next != old) tx.update(ref, key, next);
                            return id;
                        })
                .addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(cb::onError);
    }
}
