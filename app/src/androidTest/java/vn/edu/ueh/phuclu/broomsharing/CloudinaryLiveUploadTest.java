package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.graphics.*;
import android.graphics.pdf.PdfDocument;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.*;
import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.MediaAsset;
import vn.edu.ueh.phuclu.broomsharing.repository.CloudinaryClient;
import vn.edu.ueh.phuclu.broomsharing.utils.MediaPolicy;

import java.io.*;
import java.net.*;
import java.util.*;

// Chỉ chạy khi được bật và đã cấu hình tài khoản nhóm; file sinh ra hoàn toàn là dữ liệu demo.
public class CloudinaryLiveUploadTest {
    @Test
    public void actualProviderUploadsAndServesDemoPngAndPdf() throws Exception {
        assumeTrue(
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("runCloudinaryLive")));
        assertFalse(
                "Điền CLOUDINARY_CLOUD_NAME rồi build lại",
                BuildConfig.CLOUDINARY_CLOUD_NAME.isEmpty());
        assertFalse(
                "Điền CLOUDINARY_UPLOAD_PRESET rồi build lại",
                BuildConfig.CLOUDINARY_UPLOAD_PRESET.isEmpty());
        CloudinaryClient client =
                new CloudinaryClient(
                        BuildConfig.CLOUDINARY_CLOUD_NAME, BuildConfig.CLOUDINARY_UPLOAD_PRESET);
        JSONArray files = new JSONArray();
        Bitmap image = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.rgb(24, 130, 130));
        byte[] png;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
            png = out.toByteArray();
        } finally {
            image.recycle();
        }
        byte[] pdf;
        PdfDocument document = new PdfDocument();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfDocument.Page page =
                    document.startPage(new PdfDocument.PageInfo.Builder(200, 120, 1).create());
            Paint paint = new Paint();
            paint.setTextSize(14);
            page.getCanvas().drawText("BroomSharing DEMO", 10, 35, paint);
            document.finishPage(page);
            document.writeTo(out);
            pdf = out.toByteArray();
        } finally {
            document.close();
        }
        String target = UUID.randomUUID().toString();
        boolean passed = true;
        try {
            for (int i = 0; i < 2; i++) {
                byte[] bytes = i == 0 ? png : pdf;
                String mime = i == 0 ? "image/png" : "application/pdf";
                String folder =
                        MediaPolicy.folder(
                                i == 0 ? "roomMedia" : "verification", "demo_acceptance", target);
                MediaAsset asset = client.upload(bytes, mime, folder, UUID.randomUUID().toString());
                boolean removed = false;
                JSONObject result =
                        new JSONObject()
                                .put("publicId", asset.publicId)
                                .put("url", asset.secureUrl)
                                .put("bytes", asset.bytes)
                                .put("mime", mime);
                try {
                    HttpURLConnection request =
                            (HttpURLConnection) new URL(asset.secureUrl).openConnection();
                    byte[] actual;
                    try {
                        request.setConnectTimeout(15000);
                        request.setReadTimeout(30000);
                        request.setInstanceFollowRedirects(false);
                        int status = request.getResponseCode();
                        result.put("deliveryStatus", status);
                        result.put("deliveryError", request.getHeaderField("X-Cld-Error"));
                        boolean matches = false;
                        if (status == 200) {
                            try (InputStream in = request.getInputStream()) {
                                actual = CloudinaryClient.read(in, MediaPolicy.MAX_BYTES);
                            }
                            matches = Arrays.equals(bytes, actual);
                        }
                        result.put("contentMatches", matches);
                        passed &= status == 200 && matches;
                    } finally {
                        request.disconnect();
                    }
                } finally {
                    if (!asset.deleteToken.isEmpty())
                        removed = client.deleteRecent(asset.deleteToken);
                    files.put(
                            result.put("deletedWithToken", removed)
                                    .put("manualCleanupRequired", !removed));
                }
            }
            JSONObject receipt =
                    new JSONObject()
                            .put("passed", passed)
                            .put("provider", "Cloudinary real account")
                            .put("files", files)
                            .put("firebaseDocumentWrites", 0);
            File output =
                    new File(
                            InstrumentationRegistry.getInstrumentation()
                                    .getTargetContext()
                                    .getExternalFilesDir(null),
                            "cloudinary-live-acceptance.json");
            try (FileWriter writer = new FileWriter(output)) {
                writer.write(receipt.toString(2));
            }
            assertTrue(
                    "URL Cloudinary phải đọc được và giữ đúng nội dung; xem biên bản từng tệp",
                    passed);
        } finally {
            client.close();
        }
    }
}
