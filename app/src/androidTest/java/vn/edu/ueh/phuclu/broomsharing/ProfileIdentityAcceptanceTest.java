package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Application;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.ImageView;

import androidx.lifecycle.*;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import org.json.*;
import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.ui.admin.EvidencePreviewActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.RoleAccountActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostApplicationViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostApplyActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.profile.ProfileActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.profile.ProfilePhotoViewModel;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/*
   Bật thủ công: dùng tài khoản mới riêng của nghiệm thu, Cloudinary và Firestore thật.
   Hồ sơ dùng hai ảnh chữ/hình học ghi DEMO, không chứa căn cước hay chân dung người khác.
   Không đổi role tài khoản nhóm có sẵn; giữ tài khoản mới và URL làm dữ liệu demo.
*/
public class ProfileIdentityAcceptanceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseHelper.getFirestore();
    private final Properties accounts = new Properties();
    private final ViewModelStore store = new ViewModelStore();
    private final JSONArray checks = new JSONArray();
    private final JSONObject receipt = new JSONObject();
    private final List<Uri> temporary = new ArrayList<>();
    private String uid;
    private File root;

    private void main(Runnable task) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
    }

    private static class Answer<T> implements ResultCallback<T> {
        CompletableFuture<T> done = new CompletableFuture<>();

        public void onSuccess(T v) {
            done.complete(v);
        }

        public void onError(Exception e) {
            done.completeExceptionally(e);
        }

        T get() throws Exception {
            return done.get(75, TimeUnit.SECONDS);
        }
    }

    private DocumentSnapshot read(String collection, String id) throws Exception {
        return Tasks.await(
                db.collection(collection).document(id).get(Source.SERVER), 45, TimeUnit.SECONDS);
    }

    private String action(MutableLiveData<UiState<String>> state, Runnable call) throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        Observer<UiState<String>> observer =
                v -> {
                    if (v == null || v.loading) return;
                    if (v.error != null) result.completeExceptionally(new IOException(v.error));
                    else if (v.data != null) result.complete(v.data);
                };
        main(
                () -> {
                    state.setValue(null);
                    state.observeForever(observer);
                    call.run();
                });
        try {
            return result.get(150, TimeUnit.SECONDS);
        } finally {
            main(() -> state.removeObserver(observer));
        }
    }

    private void denied(Consumer<Answer<String>> operation) throws Exception {
        Answer<String> answer = new Answer<>();
        operation.accept(answer);
        try {
            answer.get();
            fail("Invalid operation accepted");
        } catch (ExecutionException expected) {
            assertNotNull(expected.getCause());
        }
    }

    private void login(String alias) throws Exception {
        Tasks.await(
                auth.signInWithEmailAndPassword(
                        accounts.getProperty(alias + ".email"),
                        accounts.getProperty(alias + ".password")),
                45,
                TimeUnit.SECONDS);
    }

    private Uri gallery(String filename) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "Broom-" + UUID.randomUUID() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BroomSharing-Host-Demo");
        Uri uri =
                context.getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        assertNotNull(uri);
        temporary.add(uri);
        try (InputStream input = new FileInputStream(new File(root, filename));
                OutputStream out = context.getContentResolver().openOutputStream(uri)) {
            assertNotNull(out);
            byte[] b = new byte[8192];
            int n;
            while ((n = input.read(b)) != -1) out.write(b, 0, n);
        }
        return uri;
    }

    private String onlineSource(String filename, String name) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(new File(root, filename).toPath());
        try (CloudinaryClient client =
                new CloudinaryClient(
                        BuildConfig.CLOUDINARY_CLOUD_NAME, BuildConfig.CLOUDINARY_UPLOAD_PRESET)) {
            return client.upload(
                            bytes,
                            "image/jpeg",
                            "broomsharing/verification/" + uid + "/demo_url_sources",
                            name + "_" + UUID.randomUUID())
                    .secureUrl;
        }
    }

    private HostApplication application() {
        return new HostApplication(
                uid,
                "[DEMO] Chủ nhà thử nghiệm",
                "0900000900",
                accounts.getProperty("applicant.email"),
                "INDIVIDUAL",
                "Giấy tờ giả lập DEMO dùng kiểm thử môn học; không có giá trị định danh.");
    }

    @Test
    public void realAvatarAndHostReviewLifecycle() throws Exception {
        assumeTrue(
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("runProfileIdentity")));
        assertTrue(BuildConfig.FIRESTORE_EMULATOR_HOST.isEmpty());
        root = new File(context.getExternalFilesDir(null), "host-identity-demo");
        try (InputStream in =
                new FileInputStream(
                        new File(
                                context.getExternalFilesDir(null),
                                "profile-identity-credentials.properties"))) {
            accounts.load(in);
        }
        try {
            // Đăng ký bằng Repository app để kiểm luôn contract tạo User và username.
            CompletableFuture<UserProfile> registered = new CompletableFuture<>();
            new FirebaseAuthRepository(context)
                    .register(
                            "[DEMO] Chủ nhà thử nghiệm",
                            accounts.getProperty("applicant.username"),
                            accounts.getProperty("applicant.email"),
                            "+84900000900",
                            accounts.getProperty("applicant.password"),
                            new AuthRepository.Callback() {
                                public void onSuccess(UserProfile p) {
                                    registered.complete(p);
                                }

                                public void onError(AuthError e) {
                                    registered.completeExceptionally(
                                            new IOException("Registration: " + e.name()));
                                }
                            });
            UserProfile user = registered.get(60, TimeUnit.SECONDS);
            uid = user.uid;
            receipt.put("applicantUid", uid);
            checks.put("New demo account registered through app repository; role GUEST");
            ProfilePhotoViewModel avatar =
                    new ProfilePhotoViewModel((Application) context.getApplicationContext());
            main(
                    () -> {
                        store.put("avatar", avatar);
                        avatar.bind(uid, user.photoUrl);
                    });
            Uri localAvatar = gallery("avatar-guest.jpg");
            String first = action(avatar.photo, () -> avatar.change(localAvatar));
            assertEquals(first, read("users", uid).getString("photoUrl"));
            assertTrue(first.contains("/avatarMedia/" + uid + "/avatar/"));
            receipt.put("guestAvatarUrl", first);
            checks.put("Guest avatar from actual MediaStore content URI uploaded and saved");
            avatarScreen(ProfileActivity.class, "guest-avatar.png");
            String online = onlineSource("avatar-host.jpg", "avatar_source");
            String second = action(avatar.photo, () -> avatar.change(Uri.parse(online)));
            assertNotEquals(first, second);
            assertEquals(second, read("users", uid).getString("photoUrl"));
            checks.put(
                    "Avatar HTTPS URL downloaded with OkHttp, re-uploaded to own folder, Firestore"
                            + " updated");
            denied(
                    a ->
                            new ProfileRepository()
                                    .setPhoto(
                                            uid,
                                            second.replace(
                                                    "/" + uid + "/avatar/", "/another/ avatar/"),
                                            a));
            assertEquals("", action(avatar.photo, () -> avatar.change(null)));
            assertEquals("", read("users", uid).getString("photoUrl"));
            action(avatar.photo, () -> avatar.change(localAvatar));
            checks.put("Avatar replacement and removal persisted; foreign URL rejected");

            HostApplicationViewModel host =
                    new HostApplicationViewModel((Application) context.getApplicationContext());
            main(() -> store.put("host", host));
            try {
                action(host.saved, () -> host.submitApplication(application()));
                fail("Missing two sides accepted");
            } catch (ExecutionException expected) {
            }
            Uri front = gallery("front.jpg");
            String backSource = onlineSource("back.jpg", "back_source");
            main(
                    () -> {
                        host.select(0, front);
                        host.select(1, Uri.parse(backSource));
                    });
            assertEquals(uid, action(host.saved, () -> host.submitApplication(application())));
            DocumentSnapshot pending = read("hostApplications", uid);
            assertEquals("PENDING", pending.getString("status"));
            assertEquals("GUEST", read("users", uid).getString("role"));
            @SuppressWarnings("unchecked")
            List<String> refs = (List<String>) pending.get("identityImageUrls");
            assertEquals(2, refs.size());
            receipt.put("identityImageUrls", new JSONArray(refs));
            checks.put(
                    "Two distinct sides: content URI front and HTTPS back uploaded; submission"
                            + " PENDING without promotion");
            try {
                action(host.saved, () -> host.submitApplication(application()));
                fail("Pending application replaced");
            } catch (ExecutionException expected) {
            }
            login("guest");
            try {
                read("hostApplications", uid);
                fail("Peer read another application");
            } catch (ExecutionException expected) {
            }
            login("admin");
            ReviewItem item = new ReviewItem("HOST", uid, "Demo", "");
            item.evidenceRefs = refs;
            denied(a -> new AdminRepository().review(item, true, "", false, a));
            Answer<String> reject = new Answer<>();
            new AdminRepository()
                    .review(
                            item,
                            false,
                            "[DEMO] Bổ sung hình rõ mặt sau để kiểm thử gửi lại.",
                            false,
                            reject);
            assertEquals(uid, reject.get());
            checks.put(
                    "Peer read denied; approve-before-view denied; Admin rejection with reason"
                            + " persisted");
            login("applicant");
            assertEquals("REJECTED", read("hostApplications", uid).getString("status"));
            assertEquals(uid, action(host.saved, () -> host.submitApplication(application())));
            assertEquals(refs, read("hostApplications", uid).get("identityImageUrls"));
            checks.put("Rejected applicant resubmitted cached uploaded URLs without re-upload");
            login("admin");
            for (String ref : refs) {
                Answer<byte[]> image = new Answer<>();
                new AdminRepository().evidence(uid, ref, image);
                assertNotNull(BitmapFactory.decodeByteArray(image.get(), 0, image.get().length));
            }
            Answer<String> approve = new Answer<>();
            new AdminRepository()
                    .review(item, true, "[DEMO] Đã kiểm tra đủ hai mặt giả lập.", true, approve);
            assertEquals(uid, approve.get());
            assertEquals("APPROVED", read("hostApplications", uid).getString("status"));
            assertNotNull(read("hostApplications", uid).getTimestamp("identityReviewedAt"));
            assertEquals("HOST", read("users", uid).getString("role"));
            assertEquals(
                    read("users", uid).getString("photoUrl"),
                    read("publicHosts", uid).getString("photoUrl"));
            checks.put(
                    "Admin reads both files; approved application + HOST role + public projection"
                            + " saved atomically");
            login("applicant");
            String hostPhoto = action(avatar.photo, () -> avatar.change(Uri.parse(online)));
            assertEquals(hostPhoto, read("publicHosts", uid).getString("photoUrl"));
            avatarScreen(RoleAccountActivity.class, "host-avatar.png");
            checks.put(
                    "Promoted Host changes avatar; users/PublicHost URLs match; Picasso visible in"
                            + " Host account");
            receipt.put("hostAvatarUrl", hostPhoto).put("passed", true);
        } catch (Throwable error) {
            receipt.put("passed", false).put("failure", error.getClass().getSimpleName());
            throw error;
        } finally {
            receipt.put("checks", checks)
                    .put("fixture", "Fictional geometric DEMO documents, no real identity data")
                    .put("cloud", "Cloudinary and Firestore actual account")
                    .put("existingAccountsPromoted", false);
            try (Writer out =
                    new OutputStreamWriter(
                            new FileOutputStream(
                                    new File(
                                            context.getExternalFilesDir(null),
                                            "profile-identity-acceptance.json")),
                            StandardCharsets.UTF_8)) {
                out.write(receipt.toString(2));
            }
            main(store::clear);
            auth.signOut();
            for (Uri uri : temporary) context.getContentResolver().delete(uri, null, null);
        }
    }

    private void avatarScreen(Class<? extends android.app.Activity> type, String file)
            throws Exception {
        try (ActivityScenario<?> screen = ActivityScenario.launch(new Intent(context, type))) {
            java.util.concurrent.atomic.AtomicBoolean ready =
                    new java.util.concurrent.atomic.AtomicBoolean();
            long deadline = android.os.SystemClock.uptimeMillis() + 35000;
            while (!ready.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                screen.onActivity(
                        a -> {
                            ImageView view = a.findViewById(R.id.avatarPhoto);
                            ready.set(view != null && view.getDrawable() instanceof BitmapDrawable);
                        });
                if (!ready.get()) Thread.sleep(200);
            }
            assertTrue("Picasso avatar must render in actual account Activity", ready.get());
            Bitmap capture =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            try (OutputStream out =
                    new FileOutputStream(new File(context.getExternalFilesDir(null), file))) {
                capture.compress(Bitmap.CompressFormat.PNG, 100, out);
            } finally {
                capture.recycle();
            }
        }
    }

    @Test
    public void actualAdminEvidenceScreensAndHostForm() throws Exception {
        assumeTrue(
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("runProfileIdentityUi")));
        try (InputStream in =
                new FileInputStream(
                        new File(
                                context.getExternalFilesDir(null),
                                "profile-identity-credentials.properties"))) {
            accounts.load(in);
        }
        JSONObject previous =
                new JSONObject(
                        new String(
                                java.nio.file.Files.readAllBytes(
                                        new File(
                                                        context.getExternalFilesDir(null),
                                                        "profile-identity-acceptance.json")
                                                .toPath()),
                                StandardCharsets.UTF_8));
        String id = previous.getString("applicantUid");
        JSONArray urls = previous.getJSONArray("identityImageUrls");
        try {
            login("admin");
            for (int side = 0; side < 2; side++) {
                String path = urls.getString(side);
                Intent intent =
                        new Intent(context, EvidencePreviewActivity.class)
                                .putExtra("ID", id)
                                .putExtra("PATH", path);
                try (ActivityScenario<EvidencePreviewActivity> screen =
                        ActivityScenario.launchActivityForResult(intent)) {
                    java.util.concurrent.atomic.AtomicBoolean loaded =
                            new java.util.concurrent.atomic.AtomicBoolean();
                    long deadline = android.os.SystemClock.uptimeMillis() + 35000;
                    while (!loaded.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                        screen.onActivity(
                                a -> {
                                    ImageView image = a.findViewById(R.id.preview);
                                    loaded.set(
                                            image != null
                                                    && image.getDrawable()
                                                            instanceof BitmapDrawable);
                                });
                        if (!loaded.get()) Thread.sleep(200);
                    }
                    assertTrue("Admin preview must decode side " + side, loaded.get());
                    screen.onActivity(
                            a -> {
                                assertTrue(
                                        (a.getWindow().getAttributes().flags
                                                        & android.view.WindowManager.LayoutParams
                                                                .FLAG_SECURE)
                                                != 0);
                                a.findViewById(R.id.close).performClick();
                            });
                    assertEquals(
                            android.app.Activity.RESULT_OK, screen.getResult().getResultCode());
                    assertEquals(path, screen.getResult().getResultData().getStringExtra("PATH"));
                }
            }
            login("guest");
            try (ActivityScenario<HostApplyActivity> screen =
                    ActivityScenario.launch(new Intent(context, HostApplyActivity.class))) {
                java.util.concurrent.atomic.AtomicBoolean ready =
                        new java.util.concurrent.atomic.AtomicBoolean();
                long deadline = android.os.SystemClock.uptimeMillis() + 25000;
                while (!ready.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                    screen.onActivity(
                            a -> {
                                android.view.View view = a.findViewById(R.id.btnIdentityFrontUrl);
                                ready.set(view != null && view.isEnabled());
                            });
                    if (!ready.get()) Thread.sleep(200);
                }
                assertTrue("Host form must expose gallery and URL controls", ready.get());
                for (int side = 0; side < 2; side++) {
                    final int index = side;
                    screen.onActivity(
                            a -> {
                                HostApplicationViewModel vm =
                                        new ViewModelProvider(a)
                                                .get(HostApplicationViewModel.class);
                                vm.select(index, Uri.parse(urls.optString(index)));
                            });
                }
                screen.recreate();
                screen.onActivity(
                        a -> {
                            HostApplicationViewModel vm =
                                    new ViewModelProvider(a).get(HostApplicationViewModel.class);
                            assertNotNull(vm.selected[0]);
                            assertNotNull(vm.selected[1]);
                        });
            }
            JSONObject ui =
                    new JSONObject()
                            .put("passed", true)
                            .put("adminPreviewSides", 2)
                            .put("secureWindow", true)
                            .put("viewedResultsReturned", true)
                            .put("hostFormRecreateRetainsUris", true)
                            .put("cloudWrites", 0);
            try (Writer out =
                    new FileWriter(
                            new File(
                                    context.getExternalFilesDir(null),
                                    "profile-identity-ui.json"))) {
                out.write(ui.toString(2));
            }
        } finally {
            auth.signOut();
        }
    }
}
