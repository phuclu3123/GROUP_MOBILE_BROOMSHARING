package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

import java.util.*;

// Đọc đánh giá công khai và ghi một lần cho mỗi booking; không tự cộng điểm trên client.
/*
   Mỗi booking đã hoàn tất chỉ được tạo một nhận xét của chính Guest.
   Transaction kiểm lại trạng thái và chủ booking, retry cùng nội dung trả về ID cũ.
*/
public class ReviewRepository {
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();

    public ListenerRegistration watch(String roomId, ResultCallback<List<RoomReview>> cb) {
        BookingPolicy.id(roomId);
        final String uid = FirebaseAuth.getInstance().getUid();
        BookingPolicy.require(uid != null, "Vui lòng đăng nhập lại.");
        return db.collection("reviews")
                .whereEqualTo("roomId", roomId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (rows, error) -> {
                            if (!uid.equals(FirebaseAuth.getInstance().getUid())) return;
                            if (error != null) {
                                cb.onError(error);
                                return;
                            }
                            if (rows == null
                                    || rows.getMetadata().isFromCache()
                                    || rows.getMetadata().hasPendingWrites()) return;
                            List<RoomReview> result = new ArrayList<>();
                            for (DocumentSnapshot doc : rows)
                                result.add(doc.toObject(RoomReview.class));
                            cb.onSuccess(result);
                        });
    }

    public void submit(String bookingId, int rating, String text, ResultCallback<String> cb) {
        final String uid = FirebaseAuth.getInstance().getUid();
        final String comment = text == null ? "" : text.trim();
        try {
            BookingPolicy.id(bookingId);
            BookingPolicy.require(uid != null, "Vui lòng đăng nhập.");
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        db.runTransaction(
                        tx -> {
                            // Đọc đủ trước khi ghi để chống hai lần nhấn hoặc hai thiết bị gửi cùng
                            // lúc.
                            DocumentReference ref = db.collection("reviews").document(bookingId);
                            DocumentSnapshot old = tx.get(ref);
                            Booking booking =
                                    tx.get(db.collection("bookings").document(bookingId))
                                            .toObject(Booking.class);
                            DocumentSnapshot user = tx.get(db.collection("users").document(uid));
                            BookingPolicy.require(
                                    uid.equals(FirebaseAuth.getInstance().getUid())
                                            && "ACTIVE".equals(user.getString("status")),
                                    "Phiên đăng nhập không còn hợp lệ.");
                            ReviewPolicy.validate(
                                    booking, uid, rating, comment, System.currentTimeMillis());
                            if (old.exists()) {
                                BookingPolicy.require(
                                        uid.equals(old.getString("guestUid"))
                                                && Objects.equals(
                                                        old.getLong("rating"), (long) rating)
                                                && comment.equals(old.getString("comment")),
                                        "Booking đã được đánh giá; không thể gửi nội dung khác.");
                                return bookingId;
                            }
                            RoomReview review = new RoomReview();
                            review.bookingId = bookingId;
                            review.roomId = booking.roomId;
                            review.guestUid = uid;
                            review.guestName = user.getString("name");
                            review.rating = rating;
                            review.comment = comment;
                            tx.set(ref, review);
                            return bookingId;
                        })
                .addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(cb::onError);
    }
}
