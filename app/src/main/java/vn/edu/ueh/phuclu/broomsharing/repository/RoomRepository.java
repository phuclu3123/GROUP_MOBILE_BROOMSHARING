package vn.edu.ueh.phuclu.broomsharing.repository;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.*;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.util.*;

/** Guest reads share Host's schema/database, with bounded one-shot queries (no listeners). */
public class RoomRepository {
    public static final int PAGE_SIZE = 20;
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();

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

    private void enrich(List<DocumentSnapshot> documents, ResultCallback<List<RoomListing>> cb) {
        Map<String, Task<DocumentSnapshot>> owners = new HashMap<>();
        Map<String, Task<DocumentSnapshot>> buildings = new HashMap<>();
        List<Room> rooms = new ArrayList<>();
        try {
            for (DocumentSnapshot doc : documents) {
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
                            db.collection("users").document(room.getOwnerUid()).get(Source.SERVER));
                if (!buildings.containsKey(room.getBuildingId()))
                    buildings.put(
                            room.getBuildingId(),
                            db.collection("buildings")
                                    .document(room.getBuildingId())
                                    .get(Source.SERVER));
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
                                    DocumentSnapshot owner =
                                            owners.get(room.getOwnerUid()).getResult();
                                    DocumentSnapshot place =
                                            buildings.get(room.getBuildingId()).getResult();
                                    if (!"HOST".equals(owner.getString("role"))
                                            || !"ACTIVE".equals(owner.getString("status"))
                                            || !place.exists()
                                            || !"ACTIVE".equals(place.getString("status"))
                                            || !room.getOwnerUid()
                                                    .equals(place.getString("ownerUid"))) continue;
                                    Building building = place.toObject(Building.class);
                                    building.setId(place.getId());
                                    result.add(
                                            new RoomListing(
                                                    room, building, owner.getString("name")));
                                }
                                cb.onSuccess(result);
                            } catch (Exception e) {
                                cb.onError(e);
                            }
                        })
                .addOnFailureListener(cb::onError);
    }
}
