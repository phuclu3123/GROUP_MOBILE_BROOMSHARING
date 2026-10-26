package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Application;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.lifecycle.*;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import org.json.*;
import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.host.PropertyVerificationViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.room.SpaceDetailActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;
import vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/*
   Nghiệm thu có bật thủ công: ảnh Unsplash thật, ViewModel thật, Cloudinary/Firestore thật.
   Chỉ tạo bộ demo có tiền tố riêng; không thay ảnh hay thông tin của phòng đang có.
   Giữ tài khoản và bộ demo cho nhóm quay video, không dùng giấy tờ hay liên hệ thật.
*/
public class CloudMediaDemoAcceptanceTest {
    private static final String PREFIX = "media_demo_20261026_";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();
    private final Properties credentials = new Properties();
    private final JSONObject receipt = new JSONObject();
    private final JSONArray assets = new JSONArray(),
            documents = new JSONArray(),
            checks = new JSONArray();
    private final ViewModelStore store = new ViewModelStore();
    private JSONArray manifest;
    private File pack;
    private String owner;
    private HostViewModel host;

    private void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    // Quan sát đúng kênh LiveData của màn hình, chờ kết quả cuối rồi tháo observer.
    private String save(MutableLiveData<UiState<String>> state, Runnable operation)
            throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        Observer<UiState<String>> observer =
                value -> {
                    if (value == null || value.loading) return;
                    if (value.error != null)
                        result.completeExceptionally(new IOException(value.error));
                    else if (value.data != null) result.complete(value.data);
                };
        main(
                () -> {
                    state.setValue(null);
                    state.observeForever(observer);
                    operation.run();
                });
        try {
            return result.get(150, TimeUnit.SECONDS);
        } finally {
            main(() -> state.removeObserver(observer));
        }
    }

    private static class Answer<T> implements ResultCallback<T> {
        final CompletableFuture<T> result = new CompletableFuture<>();

        public void onSuccess(T value) {
            result.complete(value);
        }

        public void onError(Exception error) {
            result.completeExceptionally(error);
        }

        T get() throws Exception {
            return result.get(60, TimeUnit.SECONDS);
        }
    }

    private void signIn(String alias, String role) throws Exception {
        Tasks.await(
                auth.signInWithEmailAndPassword(
                        credentials.getProperty(alias + ".email"),
                        credentials.getProperty(alias + ".password")),
                45,
                TimeUnit.SECONDS);
        DocumentSnapshot profile = read("users", auth.getUid());
        assertEquals(role, profile.getString("role"));
        assertEquals("ACTIVE", profile.getString("status"));
    }

    private DocumentSnapshot read(String collection, String id) throws Exception {
        return Tasks.await(
                db.collection(collection).document(id).get(Source.SERVER), 45, TimeUnit.SECONDS);
    }

    private List<JSONObject> files(String group, String target) throws Exception {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < manifest.length(); i++) {
            JSONObject row = manifest.getJSONObject(i);
            if (group.equals(row.getString("group")) && target.equals(row.getString("target")))
                result.add(row);
        }
        return result;
    }

    private Uri uri(JSONObject row) throws Exception {
        File file = new File(pack, row.getString("file"));
        assertTrue(file.isFile());
        assertEquals(row.getString("sha256"), sha(java.nio.file.Files.readAllBytes(file.toPath())));
        return Uri.fromFile(file);
    }

    private static String sha(byte[] bytes) throws Exception {
        StringBuilder value = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
            value.append(String.format(Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }

    // Đọc URL provider thật, đối chiếu nội dung và giải mã ảnh, không dựa vào phản hồi mock.
    private void verifyUrls(String group, String target, String id, List<String> urls)
            throws Exception {
        List<JSONObject> sources = files(group, target);
        assertEquals(sources.size(), urls.size());
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            assertTrue(
                    url.startsWith(
                            "https://res.cloudinary.com/"
                                    + BuildConfig.CLOUDINARY_CLOUD_NAME
                                    + "/image/upload/v"));
            assertTrue(url.contains("/broomsharing/" + group + "/" + owner + "/" + id + "/"));
            HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
            byte[] bytes;
            try {
                request.setConnectTimeout(15000);
                request.setReadTimeout(30000);
                request.setInstanceFollowRedirects(false);
                assertEquals(200, request.getResponseCode());
                try (InputStream input = request.getInputStream()) {
                    bytes = CloudinaryClient.read(input, MediaPolicy.MAX_BYTES);
                }
            } finally {
                request.disconnect();
            }
            JSONObject source = sources.get(i);
            assertEquals(source.getString("sha256"), sha(bytes));
            BitmapFactory.Options image = new BitmapFactory.Options();
            image.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, image);
            assertEquals(source.getInt("width"), image.outWidth);
            assertEquals(source.getInt("height"), image.outHeight);
            assets.put(
                    new JSONObject()
                            .put("group", group)
                            .put("documentId", id)
                            .put("sourceId", source.getString("id"))
                            .put("sourcePage", source.getString("sourcePage"))
                            .put("url", url)
                            .put("bytes", bytes.length)
                            .put("sha256", sha(bytes))
                            .put("width", image.outWidth)
                            .put("height", image.outHeight)
                            .put("deliveryStatus", 200));
        }
        checkpoint();
    }

    private void checkpoint() throws Exception {
        receipt.put("assets", assets)
                .put("documents", documents)
                .put("checks", checks)
                .put("project", "broom-sharing-firebase-mobile")
                .put("database", "broomsharing")
                .put("ownerUid", owner)
                .put("prefix", PREFIX)
                .put("existingDocumentsModified", false);
        File file =
                new File(
                        context.getExternalFilesDir(null), "cloudinary-photo-demo-acceptance.json");
        try (Writer out =
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            out.write(receipt.toString(2));
        }
    }

    private void buildings() throws Exception {
        String[] names = {"Broom Central", "Broom Riverside", "Broom Creative Hub"};
        String[] districts = {"Quận 1", "Bình Thạnh", "Quận 3"};
        double[] lat = {10.7769, 10.8014, 10.7843}, lon = {106.7009, 106.7106, 106.6841};
        for (int i = 0; i < 3; i++) {
            String key = "b0" + (i + 1), id = PREFIX + key;
            DocumentSnapshot old = read("buildings", id);
            if (!old.exists()) {
                Building building =
                        new Building(
                                owner,
                                "[DEMO ẢNH] " + names[i],
                                "[DEMO] Khu vực " + districts[i] + ", TP.HCM; vị trí minh họa",
                                districts[i],
                                "0900000900",
                                lat[i],
                                lon[i],
                                "Địa điểm mô phỏng cho đồ án, không phải địa chỉ cho thuê thật.");
                building.setId(id);
                building.setContactName("[DEMO] Hoàng Nam");
                assertEquals(
                        id,
                        save(
                                host.saved,
                                () -> {
                                    try {
                                        host.saveBuilding(
                                                building, uri(files("buildingMedia", key).get(0)));
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }));
            } else assertEquals(owner, old.getString("ownerUid"));
            DocumentSnapshot saved = read("buildings", id);
            verifyUrls(
                    "buildingMedia",
                    key,
                    id,
                    Collections.singletonList(saved.getString("coverUrl")));
            documents.put("buildings/" + id);
            checkpoint();
        }
        checks.put("3 building covers uploaded by HostViewModel and saved to Firestore");
    }

    private void rooms() throws Exception {
        String[] names = {
            "Phòng họp Sen",
            "Phòng họp Vista",
            "Bàn làm việc Light",
            "Văn phòng Worknest",
            "Mặt bằng Studio",
            "Không gian Creative"
        };
        String[] types = {
            "Phòng họp", "Phòng họp", "Bàn làm việc", "Phòng riêng", "Mặt bằng", "Phòng họp"
        };
        int[] capacities = {8, 12, 1, 4, 20, 6},
                areas = {25, 40, 5, 18, 80, 30},
                building = {1, 2, 1, 2, 3, 3};
        int[] hourly = {120000, 200000, 30000, 0, 0, 100000};
        for (int i = 0; i < 6; i++) {
            String key = "r0" + (i + 1), id = PREFIX + key;
            DocumentSnapshot old = read("rooms", id);
            if (!old.exists()) {
                Room room = new Room();
                room.setId(id);
                room.setBuildingId(PREFIX + "b0" + building[i]);
                room.setRoomName("[DEMO ẢNH] " + names[i]);
                room.setRoomType(types[i]);
                room.setCapacity(capacities[i]);
                room.setArea(areas[i]);
                room.setFloor("2");
                room.setAddress("[DEMO] TP.HCM; ảnh và địa điểm chỉ minh họa cho đồ án");
                boolean fixed = i == 3 || i == 4;
                room.setActiveMode(fixed ? Room.MODE_FIXED : Room.MODE_HOURLY);
                room.setPricePerHour(hourly[i]);
                room.setPricePerMonth(fixed ? i == 3 ? 5000000 : 12000000 : 0);
                room.setPricePerQuarter(fixed ? i == 3 ? 14000000 : 34000000 : 0);
                room.setFixedStartMonth(fixed ? YearMonth.now().plusMonths(1).toString() : "");
                room.setOpenTime("08:00");
                room.setCloseTime("22:00");
                room.setAmenities(Arrays.asList("Wi-Fi", "Điều hòa", "Bàn ghế", "Ổ cắm điện"));
                room.setRules(
                        "Dữ liệu demo cho đồ án. Ảnh Unsplash minh họa, không phải ảnh xác thực của"
                                + " một địa điểm.");
                room.setRentalPolicy(
                        "Thuê độc quyền theo hình thức đã niêm yết. Giá và thông tin chỉ phục vụ"
                                + " demo.");
                room.setRenewalPolicy("Liên hệ Host trước khi hết hạn, tùy lịch trống.");
                room.setApprovalStatus(Room.STATUS_PENDING_REVIEW);
                List<Uri> selected = new ArrayList<>();
                for (JSONObject row : files("roomMedia", key)) selected.add(uri(row));
                assertEquals(
                        id,
                        save(
                                host.saved,
                                () -> {
                                    host.photos.clear();
                                    host.photos.addAll(selected);
                                    host.saveRoom(room);
                                }));
            } else assertEquals(owner, old.getString("ownerUid"));
            Room saved = read("rooms", id).toObject(Room.class);
            assertNotNull(saved);
            assertEquals(0, saved.activeBookingCount);
            verifyUrls("roomMedia", key, id, saved.getImageUrls());
            documents.put("rooms/" + id);
            checkpoint();
        }
        checks.put(
                "6 rooms with 4 ordered photos each saved through HostViewModel; 4 hourly, 2 fixed"
                        + " monthly/quarterly");
    }

    private void verifications() throws Exception {
        Set<String> existing = new HashSet<>();
        QuerySnapshot owned =
                Tasks.await(
                        db.collection("propertyVerifications")
                                .whereEqualTo("ownerUid", owner)
                                .get(Source.SERVER),
                        45,
                        TimeUnit.SECONDS);
        for (DocumentSnapshot row : owned) existing.add(row.getId());
        for (int i = 0; i < 3; i++) {
            String key = "v0" + (i + 1), id = PREFIX + key;
            if (!existing.contains(id)) {
                PropertyVerificationViewModel vm =
                        new PropertyVerificationViewModel(
                                (Application) context.getApplicationContext());
                main(() -> store.put(key, vm));
                PropertyVerification item = new PropertyVerification();
                item.verificationType = i == 1 ? "PHOTOS" : "LOCATION";
                item.roomId = i == 2 ? "" : PREFIX + (i == 0 ? "r01" : "r04");
                item.buildingId = PREFIX + (i == 2 ? "b03" : i == 0 ? "b01" : "b02");
                item.description =
                        "[DEMO] Ảnh Unsplash minh họa thao tác gửi và xem minh chứng; không dùng để"
                                + " xác nhận địa điểm có thật.";
                Uri image = uri(files("verification", key).get(0));
                assertEquals(
                        id,
                        save(
                                vm.action,
                                () -> {
                                    vm.draftId = id;
                                    vm.selectEvidence(Collections.singletonList(image));
                                    vm.submit(item);
                                }));
            }
            DocumentSnapshot saved = read("propertyVerifications", id);
            @SuppressWarnings("unchecked")
            List<String> refs = (List<String>) saved.get("evidenceRefs");
            verifyUrls("verification", key, id, refs);
            documents.put("propertyVerifications/" + id);
            checkpoint();
        }
        checks.put(
                "3 demonstration evidence photos uploaded/saved by PropertyVerificationViewModel;"
                        + " no IDENTITY files");
    }

    @Test
    public void prepareAndVerifyThirtyRealPhotosThroughAppLayers() throws Exception {
        assumeTrue(
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("runCloudMediaDemo")));
        assertTrue("Requires cloud build", BuildConfig.FIRESTORE_EMULATOR_HOST.isEmpty());
        pack = new File(context.getExternalFilesDir(null), "demo-media");
        try (InputStream input =
                new FileInputStream(
                        new File(
                                context.getExternalFilesDir(null),
                                "media-demo-credentials.properties"))) {
            credentials.load(input);
        }
        manifest =
                new JSONObject(
                                new String(
                                        java.nio.file.Files.readAllBytes(
                                                new File(pack, "manifest.json").toPath()),
                                        StandardCharsets.UTF_8))
                        .getJSONArray("files");
        assertEquals(30, manifest.length());
        try {
            signIn("host", "HOST");
            owner = auth.getUid();
            main(
                    () -> {
                        host = new HostViewModel((Application) context.getApplicationContext());
                        store.put("host", host);
                    });
            buildings();
            rooms();
            verifications();
            signIn("admin", "ADMIN");
            for (int i = 1; i <= 6; i++) {
                String id = PREFIX + "r0" + i;
                if (Room.STATUS_PENDING_REVIEW.equals(
                        read("rooms", id).getString("approvalStatus"))) {
                    Answer<String> answer = new Answer<>();
                    new AdminRepository()
                            .review(
                                    new ReviewItem("ROOM", id, "", ""),
                                    true,
                                    "[DEMO] Duyệt tin minh họa cho video đồ án, không xác minh tài"
                                            + " sản thực tế.",
                                    false,
                                    answer);
                    assertEquals(id, answer.get());
                }
            }
            for (int i = 1; i <= 3; i++) {
                String id = PREFIX + "v0" + i;
                @SuppressWarnings("unchecked")
                List<String> refs =
                        (List<String>) read("propertyVerifications", id).get("evidenceRefs");
                Answer<byte[]> answer = new Answer<>();
                new AdminRepository().evidence(id, refs.get(0), answer);
                assertEquals(
                        files("verification", "v0" + i).get(0).getString("sha256"),
                        sha(answer.get()));
            }
            checks.put(
                    "Admin approved each demo room through repository; Admin evidence viewer"
                            + " downloader read all 3 saved files");
            signIn("guest", "GUEST");
            for (int i = 1; i <= 6; i++) {
                Answer<RoomListing> answer = new Answer<>();
                new RoomRepository().detail(PREFIX + "r0" + i, answer);
                RoomListing listing = answer.get();
                assertEquals(4, listing.room.getImageUrls().size());
                assertNotNull(listing.building.getCoverUrl());
                assertFalse(listing.hostName.isEmpty());
            }
            checks.put(
                    "Guest RoomRepository loaded all 6 approved listings with 4 photos and"
                            + " building/host projection");
            guestScreen();
            assertEquals(30, assets.length());
            receipt.put("passed", true);
            checkpoint();
        } catch (Throwable error) {
            receipt.put("passed", false).put("failureType", error.getClass().getSimpleName());
            checkpoint();
            throw error;
        } finally {
            main(store::clear);
            auth.signOut();
        }
    }

    // Bài đọc riêng để kiểm tra gallery trên bộ demo đã tạo, không ghi cloud.
    @Test
    public void guestCanViewPreparedPhotoGallery() throws Exception {
        assumeTrue(
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("runCloudMediaGallery")));
        try (InputStream input =
                new FileInputStream(
                        new File(
                                context.getExternalFilesDir(null),
                                "media-demo-credentials.properties"))) {
            credentials.load(input);
        }
        try {
            signIn("guest", "GUEST");
            guestScreen();
        } finally {
            auth.signOut();
        }
    }

    // Xác nhận màn hình thật hiển thị ảnh bitmap và tên phòng, giữ screenshot làm bằng chứng.
    private void guestScreen() throws Exception {
        Intent intent =
                new Intent(context, SpaceDetailActivity.class)
                        .putExtra(SpaceDetailActivity.ROOM_ID, PREFIX + "r01");
        try (ActivityScenario<SpaceDetailActivity> screen = ActivityScenario.launch(intent)) {
            AtomicBoolean ready = new AtomicBoolean();
            long deadline = android.os.SystemClock.uptimeMillis() + 35000;
            while (!ready.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                screen.onActivity(
                        activity -> {
                            ImageView cover = activity.findViewById(R.id.cover);
                            TextView title = activity.findViewById(R.id.title);
                            RecyclerView gallery = activity.findViewById(R.id.gallery);
                            boolean thumbnails = gallery != null && gallery.getChildCount() >= 3;
                            if (thumbnails)
                                for (int i = 0; i < gallery.getChildCount(); i++) {
                                    ImageView preview =
                                            gallery.getChildAt(i).findViewById(R.id.ivPreview);
                                    thumbnails &=
                                            preview != null
                                                    && preview.getDrawable()
                                                            instanceof BitmapDrawable;
                                }
                            ready.set(
                                    cover != null
                                            && cover.getDrawable() instanceof BitmapDrawable
                                            && thumbnails
                                            && title != null
                                            && title.getText().toString().contains("[DEMO ẢNH]"));
                        });
                if (!ready.get()) Thread.sleep(250);
            }
            assertTrue("Guest detail must display actual Cloudinary photo and title", ready.get());
            // Chọn ảnh thứ ba rồi quay về ảnh bìa như thao tác trên màn hình người dùng.
            for (int position : new int[] {2, 0}) {
                java.util.concurrent.atomic.AtomicReference<Bitmap> before =
                        new java.util.concurrent.atomic.AtomicReference<>();
                screen.onActivity(
                        activity -> {
                            ImageView cover = activity.findViewById(R.id.cover);
                            before.set(((BitmapDrawable) cover.getDrawable()).getBitmap());
                            RecyclerView gallery = activity.findViewById(R.id.gallery);
                            RecyclerView.ViewHolder item =
                                    gallery.findViewHolderForAdapterPosition(position);
                            assertNotNull(item);
                            item.itemView.performClick();
                        });
                AtomicBoolean changed = new AtomicBoolean();
                long changeDeadline = android.os.SystemClock.uptimeMillis() + 20000;
                while (!changed.get() && android.os.SystemClock.uptimeMillis() < changeDeadline) {
                    screen.onActivity(
                            activity -> {
                                ImageView cover = activity.findViewById(R.id.cover);
                                changed.set(
                                        cover.getDrawable() instanceof BitmapDrawable
                                                && ((BitmapDrawable) cover.getDrawable())
                                                                .getBitmap()
                                                        != before.get());
                            });
                    if (!changed.get()) Thread.sleep(200);
                }
                assertTrue("Gallery selection must change the cover bitmap", changed.get());
            }
            Bitmap capture =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            try (OutputStream out =
                    new FileOutputStream(
                            new File(
                                    context.getExternalFilesDir(null),
                                    "cloudinary-guest-detail.png"))) {
                capture.compress(Bitmap.CompressFormat.PNG, 100, out);
            } finally {
                capture.recycle();
            }
            checks.put(
                    "Real Guest detail Activity displayed Cloudinary bitmap and room title;"
                            + " screenshot captured");
        }
    }
}
