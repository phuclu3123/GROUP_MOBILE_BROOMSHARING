package vn.edu.ueh.phuclu.broomsharing.utils;

import java.net.URI;
import java.util.Arrays;

// Quy ước đường dẫn và giới hạn tệp dùng chung cho upload, hồ sơ và màn Admin.
public final class MediaPolicy {
    public static final int MAX_FILES = 6;
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private MediaPolicy() {}

    public static String segment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Mã tài khoản/tài sản không hợp lệ.");
        return value;
    }

    public static String folder(String kind, String uid, String target) {
        if (!Arrays.asList(
                        "roomMedia", "buildingMedia", "verification", "avatarMedia", "hostIdentity")
                .contains(kind)) throw new IllegalArgumentException("Nhóm tệp không hợp lệ.");
        return "broomsharing/" + kind + "/" + segment(uid) + "/" + segment(target);
    }

    // Nhận dạng bằng nội dung; tên file hoặc MIME do picker trả về không đủ để xác nhận kiểu.
    public static String mime(byte[] data, boolean allowPdf) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES)
            throw new IllegalArgumentException("Mỗi tệp phải có nội dung và tối đa 5 MiB.");
        if (data.length >= 3
                && (data[0] & 255) == 255
                && (data[1] & 255) == 216
                && (data[2] & 255) == 255) return "image/jpeg";
        if (data.length >= 8
                && data[0] == (byte) 137
                && data[1] == 'P'
                && data[2] == 'N'
                && data[3] == 'G'
                && data[4] == 13
                && data[5] == 10
                && data[6] == 26
                && data[7] == 10) return "image/png";
        if (data.length >= 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P') return "image/webp";
        if (allowPdf
                && data.length >= 5
                && data[0] == '%'
                && data[1] == 'P'
                && data[2] == 'D'
                && data[3] == 'F'
                && data[4] == '-') return "application/pdf";
        throw new IllegalArgumentException(
                allowPdf
                        ? "Chỉ nhận ảnh JPEG, PNG, WebP hoặc PDF."
                        : "Chỉ nhận ảnh JPEG, PNG hoặc WebP.");
    }

    public static String extension(String mime) {
        if ("application/pdf".equals(mime)) return "pdf";
        if ("image/png".equals(mime)) return "png";
        if ("image/webp".equals(mime)) return "webp";
        return "jpg";
    }

    public static boolean cloudinaryUrl(
            String url, String cloud, String kind, String owner, String target) {
        try {
            URI parsed = new URI(url);
            String prefix = "/" + segment(cloud) + "/";
            String path = parsed.getRawPath();
            if (!"https".equals(parsed.getScheme())
                    || !"res.cloudinary.com".equals(parsed.getHost())
                    || parsed.getPort() != -1
                    || parsed.getRawUserInfo() != null
                    || parsed.getRawQuery() != null
                    || parsed.getRawFragment() != null
                    || path == null
                    || !path.startsWith(prefix)) return false;
            String asset = path.substring(prefix.length());
            String folder = folder(kind, owner, target);
            return asset.matches(
                    "(image|raw)/upload/v[0-9]+/"
                            + folder
                            + "/[A-Za-z0-9_-]{1,128}[.](jpg|jpeg|png|webp|pdf)");
        } catch (Exception ignored) {
            return false;
        }
    }

    // URL bằng chứng luôn gắn với đúng Host và đúng mã hồ sơ; không tải URL tùy ý từ document.
    public static boolean evidenceUrl(
            String url, String cloud, String owner, String verificationId) {
        return cloudinaryUrl(url, cloud, "verification", owner, verificationId);
    }

    public static boolean avatarUrl(String url, String cloud, String owner) {
        return cloudinaryUrl(url, cloud, "avatarMedia", owner, "avatar")
                && url.contains("/image/upload/")
                && !url.endsWith(".pdf");
    }

    public static boolean hostIdentityUrl(String url, String cloud, String owner) {
        return cloudinaryUrl(url, cloud, "hostIdentity", owner, owner)
                && url.contains("/image/upload/")
                && !url.endsWith(".pdf");
    }
}
