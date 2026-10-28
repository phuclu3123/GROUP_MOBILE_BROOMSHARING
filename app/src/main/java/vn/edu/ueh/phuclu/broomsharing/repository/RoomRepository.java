package vn.edu.ueh.phuclu.broomsharing.repository;

import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.model.Building;
import vn.edu.ueh.phuclu.broomsharing.model.PublicHost;
import vn.edu.ueh.phuclu.broomsharing.model.RentalPlace;
import vn.edu.ueh.phuclu.broomsharing.model.Room;
import vn.edu.ueh.phuclu.broomsharing.model.RoomListing;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.util.*;

/** Guest reads share Host's schema/database, with bounded one-shot queries (no listeners). */
/*
   Đọc phòng công khai và ghép Building/PublicHost cho trang chi tiết.
   Kết quả phân trang chỉ nhận dữ liệu server; điều kiện đặt phòng được kiểm lại khi ghi.
*/
public class RoomRepository {
    public static final int PAGE_SIZE = 20;
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();

    public void referencePlaces(ResultCallback<List<RentalPlace>> cb) {
        db.collection("rentalPlaces")
                .limit(100)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        rows -> {
                            List<RentalPlace> values = new ArrayList<>();
                            for (DocumentSnapshot doc : rows) {
                                RentalPlace p = doc.toObject(RentalPlace.class);
                                if (p != null && p.location != null) {
                                    p.id = doc.getId();
                                    values.add(p);
                                }
                            }
                            cb.onSuccess(values);
                        })
                .addOnFailureListener(cb::onError);
    }

    public static final class Page {
        public final List<RoomListing> items;
        public final String cursor;
        public final boolean hasMore;

        Page(List<RoomListing> items, String cursor, boolean hasMore) {
            this.items = items;
            this.cursor = cursor;
            this.hasMore = hasMore;
        }
    }

    private boolean idValid(String id) {
        return id != null && !id.trim().isEmpty() && !id.contains("/");
    }

    // Đọc một trang phòng đã duyệt và đang hoạt động theo document ID, giữ cursor của nguồn trước khi ghép dữ liệu.
    public void page(String after, ResultCallback<Page> cb) {
        Query query =
                db.collection("rooms")
                        .whereEqualTo("approvalStatus", "APPROVED")
                        .whereEqualTo("listingStatus", "ACTIVE")
                        .orderBy(FieldPath.documentId())
                        .limit(PAGE_SIZE);
        if (after != null) query = query.startAfter(after);
        query.get(Source.SERVER)
                .addOnSuccessListener(
                        rows -> {
                            List<DocumentSnapshot> documents = rows.getDocuments();
                            String cursor =
                                    documents.isEmpty()
                                            ? after
                                            : documents.get(documents.size() - 1).getId();
                            enrich(
                                    documents,
                                    new ResultCallback<List<RoomListing>>() {
                                        public void onSuccess(List<RoomListing> items) {
                                            cb.onSuccess(
                                                    new Page(
                                                            items,
                                                            cursor,
                                                            documents.size() == PAGE_SIZE));
                                        }

                                        public void onError(Exception error) {
                                            cb.onError(error);
                                        }
                                    });
                        })
                .addOnFailureListener(cb::onError);
    }

    public void room(String id, ResultCallback<Room> cb) {
        detail(
                id,
                new ResultCallback<RoomListing>() {
                    public void onSuccess(RoomListing listing) {
                        cb.onSuccess(listing.room);
                    }

                    public void onError(Exception e) {
                        cb.onError(e);
                    }
                });
    }

    // Đọc lại một phòng từ server và ghép thông tin chủ/tòa nhà; không dùng thẻ danh sách cũ làm căn cứ đặt chỗ.
    public void detail(String id, ResultCallback<RoomListing> cb) {
        if (!idValid(id)) {
            cb.onError(new Exception("Mã phòng không hợp lệ."));
            return;
        }
        db.collection("rooms")
                .document(id)
                .get(Source.SERVER)
                .addOnSuccessListener(
                        doc -> {
                            enrich(
                                    Collections.singletonList(doc),
                                    new ResultCallback<List<RoomListing>>() {
                                        public void onSuccess(List<RoomListing> items) {
                                            if (items.isEmpty())
                                                cb.onError(
                                                        new Exception(
                                                                "Không gian hiện không được công"
                                                                    + " khai hoặc chủ phòng không"
                                                                    + " còn hoạt động."));
                                            else cb.onSuccess(items.get(0));
                                        }

                                        public void onError(Exception error) {
                                            cb.onError(error);
                                        }
                                    });
                        })
                .addOnFailureListener(cb::onError);
    }

    // Ghép Building và PublicHost, loại mục không còn đáp ứng điều kiện công bố trước khi trả cho giao diện.
    private void enrich(List<DocumentSnapshot> documents, ResultCallback<List<RoomListing>> cb) {
        Map<String, Task<DocumentSnapshot>> owners = new HashMap<>();
        Map<String, Task<DocumentSnapshot>> buildings = new HashMap<>();
        List<Room> rooms = new ArrayList<>();
        try {
            for (DocumentSnapshot doc : documents) {
                try {
                    if (!doc.exists()
                            || !"APPROVED".equals(doc.getString("approvalStatus"))
                            || !"ACTIVE".equals(doc.getString("listingStatus"))) continue;
                    Room room = doc.toObject(Room.class);
                    if (room == null || !idValid(room.getOwnerUid()) || !idValid(room.getBuildingId()))
                        continue;
                    room.setId(doc.getId());
                    rooms.add(room);
                    if (!owners.containsKey(room.getOwnerUid()))
                        owners.put(
                                room.getOwnerUid(),
                                db.collection("publicHosts")
                                        .document(room.getOwnerUid())
                                        .get(Source.SERVER));
                    if (!buildings.containsKey(room.getBuildingId()))
                        buildings.put(
                                room.getBuildingId(),
                                db.collection("buildings")
                                        .document(room.getBuildingId())
                                        .get(Source.SERVER));
                } catch (RuntimeException invalid) {
                    // Bỏ riêng bản ghi hỏng, không làm mất toàn bộ trang phòng hợp lệ.
                    Log.w("RoomRepository", "Invalid room schema");
                }
            }
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        List<Task<?>> tasks = new ArrayList<>(owners.values());
        tasks.addAll(buildings.values());
        Tasks.whenAll(tasks)
                .addOnSuccessListener(
                        ignored -> {
                            try {
                                List<RoomListing> result = new ArrayList<>();
                                for (Room room : rooms) {
                                    try {
                                        PublicHost owner =
                                                owners.get(room.getOwnerUid())
                                                        .getResult()
                                                        .toObject(PublicHost.class);
                                        DocumentSnapshot place =
                                                buildings.get(room.getBuildingId()).getResult();
                                        if (owner == null
                                                || !"ACTIVE".equals(owner.status)
                                                || !place.exists()
                                                || !"ACTIVE".equals(place.getString("status"))
                                                || !room.getOwnerUid()
                                                        .equals(place.getString("ownerUid"))) continue;
                                        Building building = place.toObject(Building.class);
                                        building.setId(place.getId());
                                        result.add(
                                                new RoomListing(
                                                        room, building, owner.name, owner.photoUrl));
                                    } catch (RuntimeException invalid) {
                                        Log.w("RoomRepository", "Invalid listing projection");
                                    }
                                }
                                cb.onSuccess(result);
                            } catch (Exception e) {
                                cb.onError(e);
                            }
                        })
                .addOnFailureListener(cb::onError);
    }
}
