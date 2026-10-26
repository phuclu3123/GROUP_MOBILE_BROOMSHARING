package vn.edu.ueh.phuclu.broomsharing.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;

import vn.edu.ueh.phuclu.broomsharing.BuildConfig;
import vn.edu.ueh.phuclu.broomsharing.model.MediaAsset;
import vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/*
    Upload trực tiếp Cloudinary bằng unsigned preset, theo thứ tự ảnh người dùng đã chọn.
    URL công khai được lưu trong document nghiệp vụ; đây là chế độ demo không có backend ký.
    Lỗi lưu document chỉ dọn được file còn delete token, không giả nhận đã xóa file cũ.
*/
public class MediaRepository implements AutoCloseable {
    public static final int MAX_IMAGES = MediaPolicy.MAX_FILES;
    public static final int MAX_BYTES = MediaPolicy.MAX_BYTES;
    private final Context context;
    private final CloudinaryClient client;
    private final RemoteImageClient remote = new RemoteImageClient();
    private final SharedPreferences cleanup;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;

    public MediaRepository(Context context) {
        this(
                context,
                new CloudinaryClient(
                        BuildConfig.CLOUDINARY_CLOUD_NAME, BuildConfig.CLOUDINARY_UPLOAD_PRESET));
    }

    // Tiêm transport cho nghiệm thu mà không upload lên tài khoản bên thứ ba.
    public MediaRepository(Context context, CloudinaryClient client) {
        this.context = context.getApplicationContext();
        this.client = client;
        cleanup =
                this.context.getSharedPreferences(
                        "cloudinary_recent_uploads", Context.MODE_PRIVATE);
    }

    public void upload(List<Uri> uris, String kind, String id, ResultCallback<List<String>> cb) {
        final String uid = FirebaseAuth.getInstance().getUid();
        final List<Uri> selected = uris == null ? Collections.emptyList() : new ArrayList<>(uris);
        try {
            if (closed) throw new IllegalStateException("Màn hình đã đóng.");
            if (uid == null)
                throw new IllegalStateException("Vui lòng đăng nhập trước khi tải ảnh.");
            MediaPolicy.folder(kind, uid, id);
            if (selected.size() > MAX_IMAGES)
                throw new IllegalArgumentException("Tối đa 6 tệp mỗi lần.");
            if (!selected.isEmpty()) client.configured();
        } catch (Exception e) {
            cb.onError(e);
            return;
        }
        worker.execute(
                () -> {
                    List<String> urls = new ArrayList<>();
                    try {
                        for (Uri uri : selected) {
                            session(uid);
                            byte[] bytes;
                            if ("https".equalsIgnoreCase(uri.getScheme()))
                                bytes = remote.get(uri.toString());
                            else
                                try (InputStream in =
                                        context.getContentResolver().openInputStream(uri)) {
                                    bytes = CloudinaryClient.read(in, MAX_BYTES);
                                }
                            String mime = MediaPolicy.mime(bytes, "verification".equals(kind));
                            if (mime.startsWith("image/")) {
                                BitmapFactory.Options bounds = new BitmapFactory.Options();
                                bounds.inJustDecodeBounds = true;
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                                if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                                    throw new IOException("Ảnh không hợp lệ hoặc đã hỏng.");
                            }
                            MediaAsset asset =
                                    client.upload(
                                            bytes,
                                            mime,
                                            MediaPolicy.folder(kind, uid, id),
                                            UUID.randomUUID().toString());
                            remember(asset, uid);
                            urls.add(asset.secureUrl);
                            session(uid);
                        }
                        main.post(
                                () -> {
                                    if (closed) return;
                                    if (!uid.equals(FirebaseAuth.getInstance().getUid())) {
                                        removeOwnUrlsFor(uid, urls);
                                        cb.onError(
                                                new IllegalStateException(
                                                        "Phiên đăng nhập đã thay đổi."));
                                    } else cb.onSuccess(urls);
                                });
                    } catch (Exception e) {
                        discardRecent(uid, urls);
                        main.post(
                                () -> {
                                    if (!closed) cb.onError(e);
                                });
                    }
                });
    }

    private void session(String uid) throws IOException {
        if (closed
                || Thread.currentThread().isInterrupted()
                || !uid.equals(FirebaseAuth.getInstance().getUid()))
            throw new IOException("Phiên đăng nhập đã thay đổi hoặc việc tải tệp đã dừng.");
    }

    private void remember(MediaAsset asset, String uid) {
        if (asset.deleteToken == null || asset.deleteToken.isEmpty()) return;
        cleanup.edit()
                .putString(asset.secureUrl + ":token", asset.deleteToken)
                .putString(asset.secureUrl + ":uid", uid)
                .putLong(asset.secureUrl + ":until", System.currentTimeMillis() + 9 * 60000L)
                .apply();
    }

    public void removeOwnUrls(List<String> urls) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) removeOwnUrlsFor(uid, new ArrayList<>(urls));
    }

    private void removeOwnUrlsFor(String uid, List<String> urls) {
        if (!closed) worker.execute(() -> discardRecent(uid, urls));
    }

    private void discardRecent(String uid, List<String> urls) {
        for (String url : urls) {
            if (!uid.equals(cleanup.getString(url + ":uid", null))) continue;
            String token = cleanup.getString(url + ":token", "");
            if (cleanup.getLong(url + ":until", 0) > System.currentTimeMillis()) {
                try {
                    if (!client.deleteRecent(token)) continue;
                } catch (Exception ignored) {
                    continue;
                } // Giữ token để dọn lại trước khi hết hạn.
            }
            cleanup.edit()
                    .remove(url + ":token")
                    .remove(url + ":uid")
                    .remove(url + ":until")
                    .apply();
        }
    }

    @Override
    public void close() {
        closed = true;
        client.close();
        remote.close();
        worker.shutdownNow();
    }
}
