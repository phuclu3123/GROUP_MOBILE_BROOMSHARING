package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.Looper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.MediaAsset;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

// Auth thật của tài khoản demo, file cache Android thật; provider được tiêm fixture và không ghi
// cloud.
public class MediaRepositoryAcceptanceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static class Provider extends CloudinaryClient {
        int uploads, failAt, deletes;
        boolean changeSession;
        final List<String> folders = new ArrayList<>();
        final CountDownLatch deleted = new CountDownLatch(1);

        Provider() {
            super("qa_cloud", "qa_preset");
        }

        @Override
        public MediaAsset upload(byte[] bytes, String mime, String folder, String leaf)
                throws Exception {
            uploads++;
            folders.add(folder);
            if (uploads == failAt) throw new IOException("[fixture] Upload thất bại");
            MediaAsset result =
                    new MediaAsset(
                            folder + "/" + leaf,
                            "https://res.cloudinary.com/qa_cloud/image/upload/v1/"
                                    + folder
                                    + "/"
                                    + leaf
                                    + ".png",
                            "image",
                            "png",
                            bytes.length,
                            "fixture-token");
            if (changeSession) FirebaseAuth.getInstance().signOut();
            return result;
        }

        @Override
        public boolean deleteRecent(String token) {
            deletes++;
            deleted.countDown();
            return true;
        }
    }

    private List<String> upload(MediaRepository media, List<Uri> files, String kind, String target)
            throws Exception {
        CompletableFuture<List<String>> done = new CompletableFuture<>();
        media.upload(
                files,
                kind,
                target,
                new ResultCallback<List<String>>() {
                    public void onSuccess(List<String> value) {
                        assertEquals(Looper.getMainLooper(), Looper.myLooper());
                        done.complete(value);
                    }

                    public void onError(Exception error) {
                        done.completeExceptionally(error);
                    }
                });
        return done.get(20, TimeUnit.SECONDS);
    }

    private void denied(MediaRepository media, List<Uri> files) throws Exception {
        try {
            upload(media, files, "roomMedia", "qa_target");
            fail("Invalid media accepted");
        } catch (ExecutionException expected) {
            assertNotNull(expected.getCause());
        }
    }

    @Test
    public void orderedUploadsRollbackConfigurationAndSessionChanges() throws Exception {
        assumeTrue(
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("runMediaRepositoryChecks")));
        Properties credentials = new Properties();
        try (InputStream in =
                new FileInputStream(
                        new File(
                                context.getExternalFilesDir(null),
                                "media-credentials.properties"))) {
            credentials.load(in);
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        Tasks.await(
                auth.signInWithEmailAndPassword(
                        credentials.getProperty("host.email"),
                        credentials.getProperty("host.password")),
                40,
                TimeUnit.SECONDS);
        String owner = auth.getUid();
        assertNotNull(owner);
        List<File> temporary = new ArrayList<>();
        try {
            List<Uri> files = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                File file = File.createTempFile("media-test-", ".png", context.getCacheDir());
                temporary.add(file);
                Bitmap bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(i == 0 ? Color.BLUE : Color.GREEN);
                try (OutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                } finally {
                    bitmap.recycle();
                }
                files.add(Uri.fromFile(file));
            }
            Provider provider = new Provider();
            try (MediaRepository media = new MediaRepository(context, provider)) {
                List<String> urls = upload(media, files, "roomMedia", "qa_target");
                assertEquals(2, urls.size());
                assertEquals(2, provider.uploads);
                assertEquals(
                        Arrays.asList(
                                "broomsharing/roomMedia/" + owner + "/qa_target",
                                "broomsharing/roomMedia/" + owner + "/qa_target"),
                        provider.folders);
                assertNotEquals(urls.get(0), urls.get(1));
                media.removeOwnUrls(Collections.singletonList(urls.get(0)));
                assertTrue(provider.deleted.await(10, TimeUnit.SECONDS));
                assertEquals(1, provider.deletes);
            }
            Provider partial = new Provider();
            partial.failAt = 2;
            try (MediaRepository media = new MediaRepository(context, partial)) {
                denied(media, files);
                assertEquals(2, partial.uploads);
                assertEquals(1, partial.deletes);
            }
            File invalid = File.createTempFile("media-bad-", ".jpg", context.getCacheDir());
            temporary.add(invalid);
            try (FileWriter writer = new FileWriter(invalid)) {
                writer.write("<html>not an image</html>");
            }
            Provider invalidProvider = new Provider();
            try (MediaRepository media = new MediaRepository(context, invalidProvider)) {
                denied(media, Collections.singletonList(Uri.fromFile(invalid)));
                assertEquals(0, invalidProvider.uploads);
                denied(
                        media,
                        Arrays.asList(
                                files.get(0),
                                files.get(0),
                                files.get(0),
                                files.get(0),
                                files.get(0),
                                files.get(0),
                                files.get(0)));
            }
            try (MediaRepository media =
                    new MediaRepository(context, new CloudinaryClient("", ""))) {
                assertTrue(
                        upload(media, Collections.emptyList(), "roomMedia", "qa_target").isEmpty());
                denied(media, files);
            }
            Provider switched = new Provider();
            switched.changeSession = true;
            try (MediaRepository media = new MediaRepository(context, switched)) {
                denied(media, Collections.singletonList(files.get(0)));
                assertEquals(1, switched.deletes);
            }
        } finally {
            auth.signOut();
            for (File file : temporary) file.delete();
        }
    }
}
