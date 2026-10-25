package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.model.Favorite;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

/*
    Lưu yêu thích dưới UID đang đăng nhập và đọc bổ sung thông tin phòng.
    Snapshot tên giúp giữ nội dung danh sách; phòng phải được kiểm lại khi mở/đặt.
 */
public class FavoritesRepository {
    private CollectionReference mine() {
        String uid=FirebaseAuth.getInstance().getUid();
        if(uid==null)throw new IllegalStateException("Vui lòng đăng nhập lại.");
        return FirebaseHelper.getFirestore().collection("users").document(uid).collection("favorites");
    }
    public void load(ResultCallback<List<Favorite>> cb) {
        try {mine().limit(100).get(Source.SERVER).addOnSuccessListener(rows->{
            List<Favorite> list=new ArrayList<>();for(DocumentSnapshot doc:rows){Favorite item=doc.toObject(Favorite.class);if(item!=null){item.roomId=doc.getId();list.add(item);}}
            list.sort(Comparator.comparing((Favorite item)->item.createdAt,Comparator.nullsLast(Comparator.reverseOrder())));cb.onSuccess(list);
        }).addOnFailureListener(cb::onError);}catch(Exception e){cb.onError(e);}
    }
    public void set(String roomId,String name,boolean saved,ResultCallback<Boolean> cb) {
        try {
            if(roomId==null||!roomId.matches("[a-zA-Z0-9_-]{1,128}"))throw new IllegalArgumentException("Mã phòng không hợp lệ.");
            DocumentReference ref=mine().document(roomId);
            if(!saved){ref.delete().addOnSuccessListener(v->cb.onSuccess(false)).addOnFailureListener(cb::onError);return;}
            Map<String,Object> data=new HashMap<>();data.put("roomId",roomId);data.put("roomName",name==null?"":name);data.put("createdAt",FieldValue.serverTimestamp());
            ref.set(data).addOnSuccessListener(v->cb.onSuccess(true)).addOnFailureListener(cb::onError);
        }catch(Exception e){cb.onError(e);}
    }
}
