package vn.edu.ueh.phuclu.broomsharing.model;

// Kết quả upload dùng nội bộ Repository. Delete token chỉ lưu riêng trên thiết bị, không ghi
// Firestore.
public final class MediaAsset {
    public final String publicId, secureUrl, resourceType, format, deleteToken;
    public final long bytes;

    public MediaAsset(
            String publicId,
            String secureUrl,
            String resourceType,
            String format,
            long bytes,
            String deleteToken) {
        this.publicId = publicId;
        this.secureUrl = secureUrl;
        this.resourceType = resourceType;
        this.format = format;
        this.bytes = bytes;
        this.deleteToken = deleteToken;
    }
}
