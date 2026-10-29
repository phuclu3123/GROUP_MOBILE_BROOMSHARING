package vn.edu.ueh.phuclu.broomsharing.repository;

import org.json.JSONObject;

import vn.edu.ueh.phuclu.broomsharing.model.MediaAsset;
import vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

// HTTP Java chạy trên worker của MediaRepository. Không dùng secret, không phụ thuộc Firebase
// Storage.
public class CloudinaryClient implements Closeable {
    private final String cloud, preset;
    private volatile okhttp3.Call active;
    private final okhttp3.OkHttpClient http =
            new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(75, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build();
    private volatile boolean closed;

    public CloudinaryClient(String cloud, String preset) {
        this.cloud = cloud == null ? "" : cloud.trim();
        this.preset = preset == null ? "" : preset.trim();
    }

    public void configured() {
        if (!cloud.matches("[A-Za-z0-9_-]{1,128}") || !preset.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalStateException(
                    "Chưa cấu hình Cloudinary. Điền CLOUDINARY_CLOUD_NAME và"
                            + " CLOUDINARY_UPLOAD_PRESET trong .env rồi build lại.");
    }

    public MediaAsset upload(byte[] bytes, String mime, String folder, String leaf)
            throws Exception {
        configured();
        if (!folder.matches(
                        "broomsharing/(roomMedia|buildingMedia|verification|avatarMedia|hostIdentity)/[A-Za-z0-9_-]{1,128}/[A-Za-z0-9_-]{1,128}")
                || !leaf.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IOException("Đường dẫn upload không hợp lệ.");
        if (!MediaPolicy.mime(bytes, folder.startsWith("broomsharing/verification/")).equals(mime))
            throw new IOException("Nội dung tệp không khớp định dạng.");
        String type = "application/pdf".equals(mime) ? "raw" : "image";
        String requestedId = folder + "/" + leaf + ("raw".equals(type) ? ".pdf" : "");
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("upload_preset", preset);
        parameters.put("public_id", requestedId);
        parameters.put("asset_folder", folder);
        String response =
                post(
                        "https://api.cloudinary.com/v1_1/" + cloud + "/" + type + "/upload",
                        parameters,
                        bytes,
                        leaf + "." + MediaPolicy.extension(mime),
                        mime);
        JSONObject body = getJSON(response);
        String id = body.optString("public_id"), url = body.optString("secure_url");
        String resource = body.optString("resource_type"),
                format = body.optString("format", "raw".equals(type) ? "pdf" : "");
        String expectedFormat = MediaPolicy.extension(mime);
        if (!requestedId.equals(id)
                || !type.equals(resource)
                || body.optLong("bytes") <= 0
                || body.optLong("bytes") > MediaPolicy.MAX_BYTES
                || !(expectedFormat.equals(format)
                        || "jpg".equals(expectedFormat) && "jpeg".equals(format))
                || !url.equals(
                        "https://res.cloudinary.com/"
                                + cloud
                                + "/"
                                + type
                                + "/upload/v"
                                + body.optLong("version")
                                + "/"
                                + id
                                + ("raw".equals(type) ? "" : "." + format)))
            throw new IOException(
                    "Cloudinary trả dữ liệu không đúng đường dẫn/định dạng. Kiểm tra preset không"
                            + " ghi đè public_id, folder hoặc chuyển định dạng.");
        return new MediaAsset(
                id, url, resource, format, body.getLong("bytes"), body.optString("delete_token"));
    }

    public boolean deleteRecent(String token) throws Exception {
        if (token == null || token.isEmpty()) return false;
        String response =
                post(
                        "https://api.cloudinary.com/v1_1/" + cloud + "/delete_by_token",
                        Collections.singletonMap("token", token),
                        null,
                        null,
                        null);
        return "ok".equals(getJSON(response).optString("result"));
    }

    // Điểm tiêm transport cho test; bản ứng dụng luôn gọi HTTPS cố định của Cloudinary.
    protected String post(
            String endpoint, Map<String, String> fields, byte[] file, String name, String mime)
            throws Exception {
        if (closed || Thread.currentThread().isInterrupted())
            throw new IOException("Đã dừng tải tệp.");
        okhttp3.MultipartBody.Builder multipart =
                new okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM);
        for (Map.Entry<String, String> field : fields.entrySet())
            multipart.addFormDataPart(field.getKey(), field.getValue());
        if (file != null)
            multipart.addFormDataPart(
                    "file", name, okhttp3.RequestBody.create(file, okhttp3.MediaType.get(mime)));
        okhttp3.Call call =
                http.newCall(
                        new okhttp3.Request.Builder()
                                .url(endpoint)
                                .post(multipart.build())
                                .build());
        active = call;
        try (okhttp3.Response result = call.execute()) {
            int code = result.code();
            String response;
            if (result.body() == null) response = "";
            else
                try (InputStream in = result.body().byteStream()) {
                    response = new String(read(in, 128 * 1024), StandardCharsets.UTF_8);
                }
            if (!result.isSuccessful()) {
                String message = "Dịch vụ ảnh chưa xử lý được yêu cầu (HTTP " + code + ").";
                if (code == 400 || code == 401 || code == 403)
                    message +=
                            " Kiểm tra cloud name, unsigned preset và định dạng được phép trong"
                                + " Cloudinary Console.";
                if (code == 413) message = "Tệp vượt giới hạn dung lượng của preset.";
                if (code == 429)
                    message = "Dịch vụ ảnh đã giới hạn lượt tải. Vui lòng thử lại sau.";
                throw new IOException(message);
            }
            return response;
        } finally {
            active = null;
        }
    }

    // getJSON áp dụng JSONObject để đọc phản hồi API thật, không ghép JSON bằng chuỗi.
    private JSONObject getJSON(String response) throws org.json.JSONException {
        return new JSONObject(response);
    }

    public static byte[] read(InputStream in, int limit) throws IOException {
        if (in == null) throw new IOException("Không đọc được dữ liệu tệp.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > limit)
                throw new IOException("Dữ liệu vượt giới hạn cho phép.");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    public void close() {
        closed = true;
        okhttp3.Call value = active;
        if (value != null) value.cancel();
    }
}
