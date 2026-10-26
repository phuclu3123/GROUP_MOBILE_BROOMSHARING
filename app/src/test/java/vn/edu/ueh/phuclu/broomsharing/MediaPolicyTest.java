package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;

import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy;

// Kiểm tra chính sách tệp/đường dẫn mà không cần mạng, tài khoản hoặc Cloudinary preset thật.
public class MediaPolicyTest {
    private final String root =
            "https://res.cloudinary.com/qa_cloud/image/upload/v123/broomsharing/verification/host123/case123/file123.jpg";

    @Test public void avatarAndIdentityAreSeparated() {
        String avatar = root.replace("verification/host123/case123", "avatarMedia/host123/avatar");
        String identity = root.replace("verification/host123/case123", "hostIdentity/host123/host123");
        assertTrue(MediaPolicy.avatarUrl(avatar,"qa_cloud","host123"));
        assertTrue(MediaPolicy.hostIdentityUrl(identity,"qa_cloud","host123"));
        assertFalse(MediaPolicy.avatarUrl(identity,"qa_cloud","host123"));
        assertFalse(MediaPolicy.hostIdentityUrl(avatar,"qa_cloud","host123"));
        assertFalse(MediaPolicy.hostIdentityUrl(identity,"qa_cloud","other"));
        assertFalse(MediaPolicy.avatarUrl(avatar.replace(".jpg",".pdf"),"qa_cloud","host123"));
        assertFalse(MediaPolicy.hostIdentityUrl(identity.replace("/image/","/raw/"),"qa_cloud","host123"));
    }

    @Test
    public void ownerAndCasePath() {
        assertTrue(MediaPolicy.evidenceUrl(root, "qa_cloud", "host123", "case123"));
    }

    @Test
    public void foreignOwnerDenied() {
        assertFalse(MediaPolicy.evidenceUrl(root, "qa_cloud", "other", "case123"));
    }

    @Test
    public void foreignCaseDenied() {
        assertFalse(MediaPolicy.evidenceUrl(root, "qa_cloud", "host123", "other"));
    }

    @Test
    public void foreignCloudDenied() {
        assertFalse(MediaPolicy.evidenceUrl(root, "other", "host123", "case123"));
    }

    @Test
    public void queryAndFragmentDenied() {
        assertFalse(MediaPolicy.evidenceUrl(root + "?x=y", "qa_cloud", "host123", "case123"));
        assertFalse(MediaPolicy.evidenceUrl(root + "#file", "qa_cloud", "host123", "case123"));
    }

    @Test
    public void phishingAndPlainHttpDenied() {
        assertFalse(
                MediaPolicy.evidenceUrl(
                        root.replace("res.cloudinary.com", "res.cloudinary.com.evil.test"),
                        "qa_cloud",
                        "host123",
                        "case123"));
        assertFalse(
                MediaPolicy.evidenceUrl(
                        root.replace("https:", "http:"), "qa_cloud", "host123", "case123"));
    }

    @Test
    public void encodedTraversalDenied() {
        assertFalse(
                MediaPolicy.evidenceUrl(
                        root.replace("file123", "%2e%2e%2ffile123"),
                        "qa_cloud",
                        "host123",
                        "case123"));
    }

    @Test
    public void rawPdfAccepted() {
        assertTrue(
                MediaPolicy.evidenceUrl(
                        root.replace("/image/", "/raw/").replace(".jpg", ".pdf"),
                        "qa_cloud",
                        "host123",
                        "case123"));
    }

    @Test
    public void jpegRecognized() {
        assertEquals(
                "image/jpeg",
                MediaPolicy.mime(new byte[] {(byte) 255, (byte) 216, (byte) 255}, false));
    }

    @Test
    public void pngRecognized() {
        assertEquals(
                "image/png",
                MediaPolicy.mime(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, false));
    }

    @Test
    public void webpRecognized() {
        assertEquals(
                "image/webp",
                MediaPolicy.mime(new byte[] {82, 73, 70, 70, 0, 0, 0, 0, 87, 69, 66, 80}, false));
    }

    @Test
    public void pdfOnlyForEvidence() {
        assertEquals("application/pdf", MediaPolicy.mime(new byte[] {37, 80, 68, 70, 45}, true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void pdfNotRoomPhoto() {
        MediaPolicy.mime(new byte[] {37, 80, 68, 70, 45}, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyDenied() {
        MediaPolicy.mime(new byte[0], true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void oversizeDenied() {
        MediaPolicy.mime(new byte[MediaPolicy.MAX_BYTES + 1], true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void htmlDenied() {
        MediaPolicy.mime("<html>not an image</html>".getBytes(), true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsafeTargetDenied() {
        MediaPolicy.folder("verification", "host123", "../case");
    }

    @Test
    public void foldersAreSeparated() {
        assertEquals(
                "broomsharing/roomMedia/host123/room123",
                MediaPolicy.folder("roomMedia", "host123", "room123"));
    }
}
