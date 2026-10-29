package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

/** Read receipts belong only to their recipient; event content is derived from real source data. */
/*
    Theo dõi mốc đã đọc trong subcollection của tài khoản hiện tại.
    Không dùng collection notifications làm nguồn thông báo booking của app.
 */
public class NoticeRepository {
    private final FirebaseFirestore db=FirebaseHelper.getFirestore();
    // Nghe mốc đọc dưới users/{uid}/noticeReads của đúng tài khoản đang dùng.
    public ListenerRegistration watch(String uid,ResultCallback<Set<String>> cb){
        return db.collection("users").document(uid).collection("noticeReads").addSnapshotListener(MetadataChanges.INCLUDE,(rows,e)->{
            if(!uid.equals(FirebaseAuth.getInstance().getUid()))return;
            if(e!=null){cb.onError(e);return;}if(rows==null||rows.getMetadata().isFromCache()||rows.getMetadata().hasPendingWrites())return;
            Set<String> ids=new HashSet<>();for(DocumentSnapshot d:rows)ids.add(d.getId());cb.onSuccess(ids);
        });
    }
    // Lưu thời điểm đọc bằng server timestamp cho định danh sự kiện dẫn xuất, không sao chép nội dung thông báo.
    public void read(String uid,String id,ResultCallback<String> cb){
        if(uid==null||!uid.equals(FirebaseAuth.getInstance().getUid())||id==null||!id.matches("[A-Za-z0-9:_-]{1,256}")){cb.onError(new Exception("Phiên thông báo không hợp lệ."));return;}
        db.collection("users").document(uid).collection("noticeReads").document(id).set(Collections.singletonMap("readAt",FieldValue.serverTimestamp())).addOnSuccessListener(v->cb.onSuccess(id)).addOnFailureListener(cb::onError);
    }
}
