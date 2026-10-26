package vn.edu.ueh.phuclu.broomsharing.repository;

import okhttp3.*;

import vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/*
   Nhập ảnh bằng URL: GET trên worker, chỉ nhận HTTPS công khai và tối đa 5 MiB.
   Ảnh tải về vẫn đi qua kiểm nội dung rồi upload vào thư mục của tài khoản hiện tại.
   Không lưu URL nguồn có thể hết hạn, không theo redirect hoặc truy cập mạng nội bộ.
*/
public class RemoteImageClient implements AutoCloseable {
    private static final OkHttpClient CLIENT =
            new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(40, TimeUnit.SECONDS)
                    .callTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .dns(
                            host -> {
                                List<InetAddress> addresses = Dns.SYSTEM.lookup(host);
                                for (InetAddress address : addresses) {
                                    byte[] bytes = address.getAddress();
                                    boolean extraPrivate =
                                            bytes.length == 4
                                                    && (bytes[0] & 255) == 100
                                                    && (bytes[1] & 255) >= 64
                                                    && (bytes[1] & 255) <= 127;
                                    if (address.isAnyLocalAddress()
                                            || address.isLoopbackAddress()
                                            || address.isLinkLocalAddress()
                                            || address.isSiteLocalAddress()
                                            || address.isMulticastAddress()
                                            || extraPrivate
                                            || bytes.length == 16 && (bytes[0] & 254) == 252)
                                        throw new UnknownHostException(
                                                "Không nhận địa chỉ mạng nội bộ.");
                                }
                                return addresses;
                            })
                    .build();
    private volatile Call active;
    private volatile boolean closed;

    public static OkHttpClient client() {
        return CLIENT;
    }

    public static HttpUrl imageUrl(String value) {
        HttpUrl url = value == null ? null : HttpUrl.parse(value.trim());
        if (url == null
                || !url.isHttps()
                || !url.username().isEmpty()
                || !url.password().isEmpty()
                || url.port() != 443
                || url.fragment() != null
                || url.host().equalsIgnoreCase("localhost")
                || url.host().endsWith(".local")
                || url.host().endsWith(".localhost"))
            throw new IllegalArgumentException(
                    "Nhập URL ảnh HTTPS công khai, không có tài khoản/mật khẩu trong URL.");
        // IP literal không qua Dns của OkHttp; kiểm riêng để không bỏ lọt mạng nội bộ.
        if (url.host().contains(":") || url.host().matches("[0-9.]+")) {
            try {
                InetAddress address = InetAddress.getByName(url.host());
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()
                        || address.getAddress().length == 16
                                && (address.getAddress()[0] & 254) == 252
                        || address.getAddress().length == 4
                                && (address.getAddress()[0] & 255) == 100
                                && (address.getAddress()[1] & 255) >= 64
                                && (address.getAddress()[1] & 255) <= 127)
                    throw new IllegalArgumentException("Không nhận URL mạng nội bộ.");
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Địa chỉ IP không hợp lệ.");
            }
        }
        return url;
    }

    public byte[] get(String url) throws Exception {
        if (closed) throw new IOException("Đã dừng tải ảnh.");
        Call call = CLIENT.newCall(new Request.Builder().url(imageUrl(url)).get().build());
        active = call;
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null)
                throw new IOException(
                        "Không tải được ảnh từ URL (HTTP "
                                + response.code()
                                + "). Hãy dùng liên kết trực tiếp tới ảnh.");
            if (response.body().contentLength() > MediaPolicy.MAX_BYTES)
                throw new IOException("Ảnh từ URL vượt giới hạn 5 MiB.");
            byte[] bytes;
            try (InputStream in = response.body().byteStream()) {
                bytes = CloudinaryClient.read(in, MediaPolicy.MAX_BYTES);
            }
            MediaPolicy.mime(bytes, false);
            return bytes;
        } finally {
            active = null;
        }
    }

    @Override
    public void close() {
        closed = true;
        Call call = active;
        if (call != null) call.cancel();
    }
}
