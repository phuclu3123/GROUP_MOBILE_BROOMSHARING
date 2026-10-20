package vn.edu.ueh.phuclu.broomsharing.repository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;
public class ReportRepository {
    private final FirebaseFirestore db=FirebaseHelper.getFirestore();
    public static final List<String> REASONS=Arrays.asList("Sai thông tin","Ảnh không khớp","Nghi ngờ gian lận","Khác");
    public void submit(String id,String roomId,String reason,String description,ResultCallback<String> cb){
        String uid=FirebaseAuth.getInstance().getUid(),note=description==null?"":description.trim();
        try{BookingPolicy.require(uid!=null,"Vui lòng đăng nhập lại.");BookingPolicy.id(id);BookingPolicy.id(roomId);BookingPolicy.require(REASONS.contains(reason)&&note.length()>=20&&note.length()<=2000,"Chọn loại báo cáo và mô tả từ 20 đến 2.000 ký tự.");}catch(Exception e){cb.onError(e);return;}
        db.runTransaction(tx->{
            DocumentReference ref=db.collection("reports").document(id);DocumentSnapshot old=tx.get(ref),actor=tx.get(db.collection("users").document(uid));
            BookingPolicy.require(uid.equals(FirebaseAuth.getInstance().getUid())&&"ACTIVE".equals(actor.getString("status"))&&Arrays.asList("GUEST","HOST").contains(actor.getString("role")),"Tài khoản không được gửi báo cáo.");
            if(old.exists()){BookingPolicy.require(uid.equals(old.getString("reporterUid"))&&roomId.equals(old.getString("roomId")),"Mã báo cáo đã được dùng.");return id;}
            DocumentSnapshot room=tx.get(db.collection("rooms").document(roomId));
            BookingPolicy.require(room.exists()&&"APPROVED".equals(room.getString("approvalStatus"))&&"ACTIVE".equals(room.getString("listingStatus"))&&!uid.equals(room.getString("ownerUid")),"Phòng không còn nhận báo cáo mới hoặc là phòng của bạn.");
            RoomReport r=new RoomReport();r.id=id;r.reporterUid=uid;r.roomId=roomId;r.ownerUid=room.getString("ownerUid");r.reason=reason;r.description=note;tx.set(ref,r);return id;
        }).addOnSuccessListener(cb::onSuccess).addOnFailureListener(cb::onError);
    }
    public void resolve(String id,String status,String action,String reason,ResultCallback<String> cb){
        String uid=FirebaseAuth.getInstance().getUid(),note=reason==null?"":reason.trim();
        try{BookingPolicy.require(uid!=null,"Vui lòng đăng nhập lại.");BookingPolicy.id(id);BookingPolicy.require(Arrays.asList("RESOLVED","DISMISSED").contains(status)&&Arrays.asList("NO_ACTION","SUSPEND_ROOM","BLOCK_HOST").contains(action)&&(!"DISMISSED".equals(status)||"NO_ACTION".equals(action))&&note.length()>=10&&note.length()<=1000,"Kết luận cần từ 10 đến 1.000 ký tự và hành động hợp lệ.");}catch(Exception e){cb.onError(e);return;}
        db.runTransaction(tx->{
            DocumentSnapshot admin=tx.get(db.collection("users").document(uid));DocumentReference ref=db.collection("reports").document(id);DocumentSnapshot report=tx.get(ref);
            BookingPolicy.require(uid.equals(FirebaseAuth.getInstance().getUid())&&"ADMIN".equals(admin.getString("role"))&&"ACTIVE".equals(admin.getString("status")),"Bạn không có quyền Admin.");BookingPolicy.require(report.exists()&&"OPEN".equals(report.getString("status")),"Báo cáo đã được xử lý. Hãy tải lại.");
            DocumentReference roomRef=null,hostRef=null;DocumentSnapshot room=null,owner=null;
            if(!"NO_ACTION".equals(action)){
                roomRef=db.collection("rooms").document(BookingPolicy.id(report.getString("roomId")));room=tx.get(roomRef);String ownerUid=BookingPolicy.id(report.getString("ownerUid"));hostRef=db.collection("users").document(ownerUid);owner=tx.get(hostRef);
                BookingPolicy.require(room.exists()&&ownerUid.equals(room.getString("ownerUid"))&&"HOST".equals(owner.getString("role")),"Không xác định được phòng/chủ từ báo cáo.");
            }
            Map<String,Object> changes=new HashMap<>();changes.put("status",status);changes.put("action",action);changes.put("resolution",note);changes.put("reviewedBy",uid);changes.put("reviewedAt",FieldValue.serverTimestamp());changes.put("updatedAt",FieldValue.serverTimestamp());tx.update(ref,changes);
            if("SUSPEND_ROOM".equals(action))tx.update(roomRef,"listingStatus","SUSPENDED","moderationReason",note,"moderatedBy",uid,"updatedAt",FieldValue.serverTimestamp());
            if("BLOCK_HOST".equals(action)){tx.update(hostRef,"status","BLOCKED","moderationReason",note,"moderatedBy",uid,"updatedAt",FieldValue.serverTimestamp());tx.set(db.collection("publicHosts").document(owner.getId()),new PublicHost(owner.getId(),owner.getString("name"),"BLOCKED"));}
            Map<String,Object> after=new HashMap<>();after.put("status",status);after.put("action",action);
            tx.set(db.collection("auditLogs").document(),new AuditLog(uid,ref.getPath(),"RESOLVE_REPORT",Collections.singletonMap("status","OPEN"),after,note));
            return "Đã xử lý báo cáo và lưu nhật ký.";
        }).addOnSuccessListener(cb::onSuccess).addOnFailureListener(cb::onError);
    }
}
