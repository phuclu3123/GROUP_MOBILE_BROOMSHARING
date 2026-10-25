package vn.edu.ueh.phuclu.broomsharing.repository;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Only public room/building photographs. Identity documents are not uploaded here. */
public class MediaRepository {
    public static final int MAX_IMAGES = 6;
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private final Context context;
    private final FirebaseStorage storage = FirebaseStorage.getInstance();

    public MediaRepository(Context context) {
        this.context = context.getApplicationContext();
        storage.setMaxUploadRetryTimeMillis(60000);
    }

    public void upload(
            List<Uri> uris, String kind, String id, HostRepository.Callback<List<String>> cb) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null
                || !("roomMedia".equals(kind) || "buildingMedia".equals(kind))
                || id == null
                || id.contains("/")) {
            cb.onError(new Exception("Đăng nhập và lưu đúng mã không gian trước khi tải ảnh."));
            return;
        }
        if (uris.size() > MAX_IMAGES) {
            cb.onError(new Exception("Tối đa 6 ảnh."));
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        List<StorageReference> uploaded = new ArrayList<>();
        uploadNext(
                new ArrayList<>(uris),
                0,
                kind + "/" + uid + "/" + id + "/",
                uid,
                uploaded,
                new ArrayList<>(),
                cb);
    }

    private void uploadNext(
            List<Uri> uris,
            int index,
            String path,
            String uid,
            List<StorageReference> refs,
            List<String> urls,
            HostRepository.Callback<List<String>> cb) {
        if (index == uris.size()) {
            cb.onSuccess(urls);
            return;
        }
        ExecutorService worker = Executors.newSingleThreadExecutor();
        worker.execute(
                () -> {
                    try {
                        byte[] bytes;
                        try (InputStream in =
                                        context.getContentResolver()
                                                .openInputStream(uris.get(index));
                                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                            if (in == null)
                                throw new IOException("Không đọc được ảnh. Vui lòng chọn lại.");
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = in.read(buffer)) != -1) {
                                if (out.size() + length > MAX_BYTES)
                                    throw new IOException("Mỗi ảnh tối đa 5 MiB.");
                                out.write(buffer, 0, length);
                            }
                            bytes = out.toByteArray();
                        }
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                        String mime = options.outMimeType;
                        if (options.outWidth <= 0
                                || options.outHeight <= 0
                                || !("image/jpeg".equals(mime)
                                        || "image/png".equals(mime)
                                        || "image/webp".equals(mime)))
                            throw new IOException("Chỉ hỗ trợ ảnh JPEG, PNG hoặc WebP hợp lệ.");
                        String ext =
                                "image/png".equals(mime)
                                        ? ".png"
                                        : "image/webp".equals(mime) ? ".webp" : ".jpg";
                        new Handler(Looper.getMainLooper())
                                .post(
                                        () -> {
                                            if (FirebaseAuth.getInstance().getCurrentUser() == null
                                                    || !uid.equals(
                                                            FirebaseAuth.getInstance()
                                                                    .getCurrentUser()
                                                                    .getUid())) {
                                                discard(refs);
                                                cb.onError(
                                                        new Exception(
                                                                "Phiên đăng nhập đã thay đổi. Vui"
                                                                        + " lòng thử lại."));
                                                return;
                                            }
                                            StorageReference ref =
                                                    storage.getReference()
                                                            .child(path + UUID.randomUUID() + ext);
                                            refs.add(ref);
                                            ref.putBytes(
                                                            bytes,
                                                            new StorageMetadata.Builder()
                                                                    .setContentType(mime)
                                                                    .build())
                                                    .continueWithTask(
                                                            task -> {
                                                                if (!task.isSuccessful())
                                                                    throw task.getException();
                                                                return ref.getDownloadUrl();
                                                            })
                                                    .addOnSuccessListener(
                                                            url -> {
                                                                urls.add(url.toString());
                                                                uploadNext(
                                                                        uris, index + 1, path, uid,
                                                                        refs, urls, cb);
                                                            })
                                                    .addOnFailureListener(
                                                            e -> {
                                                                discard(refs);
                                                                cb.onError(e);
                                                            });
                                        });
                    } catch (Exception e) {
                        new Handler(Looper.getMainLooper())
                                .post(
                                        () -> {
                                            discard(refs);
                                            cb.onError(e);
                                        });
                    } finally {
                        worker.shutdown();
                    }
                });
    }

    private void discard(List<StorageReference> refs) {
        for (StorageReference ref : refs) ref.delete();
    }

    public void removeOwnUrls(List<String> urls) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        for (String url : urls)
            try {
                StorageReference ref = storage.getReferenceFromUrl(url);
                String path = ref.getPath().replaceFirst("^/", "");
                if (path.startsWith("roomMedia/" + uid + "/")
                        || path.startsWith("buildingMedia/" + uid + "/")) ref.delete();
            } catch (IllegalArgumentException ignored) {
                /* Legacy/external image is never deleted. */
            }
    }
}
