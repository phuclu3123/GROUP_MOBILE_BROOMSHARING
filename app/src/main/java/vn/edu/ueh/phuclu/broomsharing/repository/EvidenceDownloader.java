package vn.edu.ueh.phuclu.broomsharing.repository;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;

import vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

// Chỉ tải URL demo đã được AdminRepository đối chiếu hồ sơ, không theo redirect sang host khác.
final class EvidenceDownloader {
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(2);

    private EvidenceDownloader() {}

    static void download(String url, String actor, ResultCallback<byte[]> cb) {
        WORKER.execute(
                () -> {
                    try {
                        URI parsed = new URI(url);
                        if (!"https".equals(parsed.getScheme())
                                || !"res.cloudinary.com".equals(parsed.getHost())
                                || parsed.getPort() != -1
                                || parsed.getRawUserInfo() != null
                                || parsed.getRawQuery() != null
                                || parsed.getRawFragment() != null)
                            throw new IOException("Địa chỉ tệp không hợp lệ.");
                        HttpURLConnection connection =
                                (HttpURLConnection) parsed.toURL().openConnection();
                        byte[] bytes;
                        try {
                            connection.setInstanceFollowRedirects(false);
                            connection.setConnectTimeout(15000);
                            connection.setReadTimeout(30000);
                            int code = connection.getResponseCode();
                            if (code != 200)
                                throw new IOException(
                                        "Không tải được tệp Cloudinary (HTTP "
                                                + code
                                                + "). Với PDF, kiểm tra Allow delivery of PDF and"
                                                + " ZIP files trong Console.");
                            try (InputStream in = connection.getInputStream()) {
                                bytes = CloudinaryClient.read(in, MediaPolicy.MAX_BYTES);
                            }
                            MediaPolicy.mime(bytes, true);
                        } finally {
                            connection.disconnect();
                        }
                        new Handler(Looper.getMainLooper())
                                .post(
                                        () -> {
                                            if (actor.equals(FirebaseAuth.getInstance().getUid()))
                                                cb.onSuccess(bytes);
                                            else
                                                cb.onError(
                                                        new IllegalStateException(
                                                                "Phiên đăng nhập đã thay đổi."));
                                        });
                    } catch (Exception e) {
                        new Handler(Looper.getMainLooper()).post(() -> cb.onError(e));
                    }
                });
    }
}
