package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.functions.*;
import com.google.android.gms.tasks.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;
import java.util.*;

/** Read queues through Firestore; trusted writes and private evidence through Firebase Functions. */
public class AdminRepository {
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();
    private final FirebaseFunctions functions = vn.edu.ueh.phuclu.broomsharing.utils.FirebaseConfig.functions();

    public void load(String kind, ResultCallback<List<ReviewItem>> cb) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) { cb.onError(new Exception("Vui lòng đăng nhập lại.")); return; }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("users").document(uid).get(Source.SERVER).addOnSuccessListener(user -> {
            if (!"ADMIN".equals(user.getString("role")) || !"ACTIVE".equals(user.getString("status"))) {
                cb.onError(new Exception("Bạn không có quyền Admin.")); return;
            }
            final Query query;
            switch (kind) {
                case "HOST": query = db.collection("hostApplications").whereEqualTo("status", "PENDING").limit(100); break;
                case "ROOM": query = db.collection("rooms").whereEqualTo("approvalStatus", "PENDING_REVIEW").limit(100); break;
                case "VERIFICATION": query = db.collection("propertyVerifications").whereEqualTo("status", "PENDING").limit(100); break;
                case "HOST_STATUS": query = db.collection("users").whereEqualTo("role", "HOST").limit(200); break;
                case "ROOM_STATUS": query = db.collection("rooms").limit(200); break;
                case "AUDIT": query = db.collection("auditLogs").orderBy("createdAt", Query.Direction.DESCENDING).limit(100); break;
                case "REPORT": query = db.collection("reports").whereEqualTo("status", "OPEN").limit(100); break;
                default: cb.onError(new Exception("Nhóm quản trị không hợp lệ.")); return;
            }
            query.get(Source.SERVER).addOnSuccessListener(rows -> {
                try {
                    List<ReviewItem> result = new ArrayList<>();
                    List<Task<?>> extra = new ArrayList<>();
                    for (DocumentSnapshot d : rows) {
                        ReviewItem item = item(kind, d); result.add(item);
                        if (("ROOM".equals(kind) || "ROOM_STATUS".equals(kind)) && d.getString("buildingId") != null) {
                            extra.add(db.collection("buildings").document(d.getString("buildingId")).get(Source.SERVER)
                                .addOnSuccessListener(building -> {
                                    if (building.exists()) item.details += "\nTòa nhà: " + text(building,"name")
                                            + "\nĐịa chỉ: " + text(building,"address") + "\nQuận: " + text(building,"district")
                                            + "\nTọa độ do chủ cung cấp: " + building.get("latitude") + ", " + building.get("longitude")
                                            + "\nHướng dẫn vào: " + text(building,"directionGuide");
                                }));
                        }
                    }
                    Tasks.whenAll(extra).addOnSuccessListener(ignored -> cb.onSuccess(result)).addOnFailureListener(cb::onError);
                } catch (Exception e) { cb.onError(e); }
            }).addOnFailureListener(cb::onError);
        }).addOnFailureListener(cb::onError);
    }

    private String text(DocumentSnapshot d, String key) { Object v = d.get(key); return v == null ? "" : String.valueOf(v); }

    @SuppressWarnings("unchecked")
    private ReviewItem item(String kind, DocumentSnapshot d) {
        String title, details, status = text(d, "status");
        if ("HOST".equals(kind)) {
            title = text(d,"fullName");
            details = "Người nộp: " + text(d,"applicantUid") + "\n" + text(d,"phone") + " • " + text(d,"email")
                + "\n" + text(d,"organizationType") + "\nGiấy tờ mô tả (demo): " + text(d,"identityDoc");
        } else if ("ROOM".equals(kind) || "ROOM_STATUS".equals(kind)) {
            Room r = d.toObject(Room.class); title = r.getRoomName(); status = r.getListingStatus();
            details = "Chủ: " + r.getOwnerUid() + "\n" + r.getAddress() + " • Tầng " + r.getFloor()
                + "\n" + r.getCapacity() + " người • " + r.getArea() + " m²"
                + "\nDuyệt: " + FormatUtils.reviewStatus(r.getApprovalStatus()) + " • Rao: " + status
                + "\nHình thức: " + r.getActiveMode() + " • Giờ mở: " + r.getOpenTime() + "–" + r.getCloseTime()
                + "\nNgày hoạt động: " + r.getWorkingDays() + " • Bước: " + r.getSlotMinutes() + " phút"
                + "\nGiờ: " + FormatUtils.formatCurrency(r.getPricePerHour()) + " • Tháng: " + FormatUtils.formatCurrency(r.getPricePerMonth())
                + " • Quý: " + FormatUtils.formatCurrency(r.getPricePerQuarter()) + "\nCọc: " + FormatUtils.formatCurrency(r.getDeposit())
                + "\nTiện ích: " + r.getAmenities() + "\nNội quy: " + r.getRules() + "\nChính sách thuê: " + r.getRentalPolicy()
                + "\nGia hạn: " + r.getRenewalPolicy() + "\nTrạng thái giấy tờ: " + r.getLocationVerification() + " / " + r.getPhotosVerification()
                + "\nLý do quản trị: " + text(d,"moderationReason");
        } else if ("HOST_STATUS".equals(kind)) {
            title = text(d,"name");
            details = "UID: " + d.getId() + "\n" + text(d,"email") + " • " + text(d,"phone")
                + "\nTrạng thái: " + status + "\nLý do: " + text(d,"moderationReason")
                + "\nKhóa chủ chặn đặt mới; giữ nguyên lịch sử booking.";
        } else if ("VERIFICATION".equals(kind)) {
            title = "Xác minh " + verificationType(text(d,"verificationType"));
            details = "Chủ: " + text(d,"ownerUid") + "\nPhòng: " + text(d,"roomId") + "\nTòa nhà: " + text(d,"buildingId")
                + "\nMô tả: " + text(d,"description") + "\nChỉ xét duyệt nội bộ, không gán nhãn đối tác.";
        } else if ("AUDIT".equals(kind)) {
            title = action(text(d,"action"));
            details = "Người xử lý: " + text(d,"actorUid") + "\nĐối tượng: " + text(d,"targetPath")
                + "\nTrước: " + text(d,"before") + "\nSau: " + text(d,"after") + "\nLý do: " + text(d,"reason");
        } else { title = text(d,"reason"); details = text(d,"description"); }
        ReviewItem item = new ReviewItem(kind,d.getId(),title == null || title.isEmpty() ? d.getId() : title,
                details + "\n" + TimeUtils.formatVietnam(d.getDate("createdAt")));
        item.status = status;
        if ("ROOM".equals(kind) || "ROOM_STATUS".equals(kind)) {
            Object urls = d.get("imageUrls"); if (urls instanceof List) item.imageUrls = (List<String>) urls;
        }
        if ("VERIFICATION".equals(kind)) {
            Object refs = d.get("evidenceRefs");
            if (refs instanceof List) item.evidenceRefs = (List<String>) refs;
            else if (d.getString("evidencePath") != null) item.evidenceRefs = Collections.singletonList(d.getString("evidencePath"));
        }
        return item;
    }

    private String verificationType(String s) {
        if ("IDENTITY".equals(s)) return "danh tính"; if ("LOCATION".equals(s)) return "địa điểm";
        if ("PHOTOS".equals(s)) return "ảnh phòng"; return "chưa rõ loại";
    }
    private String action(String s) {
        switch(s) {
            case "APPROVE": return "Duyệt hồ sơ"; case "REJECT": return "Từ chối hồ sơ";
            case "BLOCK_HOST": return "Khóa Host"; case "UNBLOCK_HOST": return "Mở khóa Host";
            case "SUSPEND_ROOM": return "Khóa phòng"; case "UNSUSPEND_ROOM": return "Gỡ khóa phòng";
            case "CREATE": return "Tạo booking"; case "CONFIRMED": return "Xác nhận booking";
            case "CANCELLED": return "Hủy booking"; case "REJECTED": return "Từ chối booking";
            case "CHECKED_IN": return "Ghi nhận vào phòng demo"; case "COMPLETED": return "Hoàn tất booking";
            case "SAVE_ROOM": return "Sửa thông tin phòng"; case "SET_LISTING": return "Đổi trạng thái nhận đặt";
            default: return s;
        }
    }

    public void review(ReviewItem item, boolean approve, String reason, boolean evidenceReviewed, ResultCallback<String> cb) {
        Map<String,Object> data = new HashMap<>(); data.put("reason", reason == null ? "" : reason.trim());
        String function;
        if ("HOST_STATUS".equals(item.kind)) {
            function = "moderateHost"; data.put("uid",item.id); data.put("status","BLOCKED".equals(item.status)?"ACTIVE":"BLOCKED");
        } else if ("ROOM_STATUS".equals(item.kind)) {
            function = "moderateRoom"; data.put("roomId",item.id); data.put("status","SUSPENDED".equals(item.status)?"INACTIVE":"SUSPENDED");
        } else {
            function = "reviewAdminItem"; data.put("kind",item.kind); data.put("id",item.id);
            data.put("approve",approve); data.put("evidenceReviewed",evidenceReviewed);
        }
        call(function,data,new ResultCallback<Map<String,Object>>() {
            public void onSuccess(Map<String,Object> result) { cb.onSuccess(item.id); }
            public void onError(Exception e) { cb.onError(e); }
        });
    }

    public void evidence(String id, String path, ResultCallback<byte[]> cb) {
        Map<String,Object> data = new HashMap<>(); data.put("id",id); data.put("path",path);
        call("getVerificationEvidence",data,new ResultCallback<Map<String,Object>>() {
            public void onSuccess(Map<String,Object> result) {
                try { cb.onSuccess(android.util.Base64.decode((String)result.get("base64"),android.util.Base64.DEFAULT)); }
                catch (Exception e) { cb.onError(new Exception("Nội dung bằng chứng không hợp lệ.",e)); }
            }
            public void onError(Exception e) { cb.onError(e); }
        });
    }

    @SuppressWarnings("unchecked")
    private void call(String name, Map<String,Object> data, ResultCallback<Map<String,Object>> cb) {
        functions.getHttpsCallable(name).call(data).addOnSuccessListener(result -> {
            if (result.getData() instanceof Map) cb.onSuccess((Map<String,Object>)result.getData());
            else cb.onError(new Exception("Phản hồi quản trị không hợp lệ."));
        }).addOnFailureListener(e -> {
            if (e instanceof FirebaseFunctionsException && ((FirebaseFunctionsException)e).getCode() == FirebaseFunctionsException.Code.NOT_FOUND)
                cb.onError(new Exception("Dịch vụ quản trị chưa sẵn sàng; thao tác chưa được xác nhận."));
            else cb.onError(e);
        });
    }
}