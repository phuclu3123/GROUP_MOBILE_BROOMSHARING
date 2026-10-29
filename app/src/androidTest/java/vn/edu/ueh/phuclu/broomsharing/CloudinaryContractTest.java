package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

import vn.edu.ueh.phuclu.broomsharing.model.MediaAsset;
import vn.edu.ueh.phuclu.broomsharing.repository.CloudinaryClient;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

// Mô phỏng phản hồi provider có ghi rõ; không upload vào tài khoản Cloudinary công cộng của người
// khác.
public class CloudinaryContractTest {
    private static final byte[] PNG = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final String FOLDER = "broomsharing/roomMedia/qa_host/qa_room";

    private static class Fixture extends CloudinaryClient {
        String endpoint;
        Map<String, String> parameters;
        String mutation = "";
        int calls;

        Fixture() {
            super("qa_cloud", "qa_preset");
        }

        @Override
        protected String post(
                String url, Map<String, String> fields, byte[] file, String name, String mime)
                throws Exception {
            calls++;
            endpoint = url;
            parameters = new HashMap<>(fields);
            if (url.endsWith("delete_by_token")) return "{\"result\":\"ok\"}";
            if ("json".equals(mutation)) return "not json";
            String type = url.contains("/raw/") ? "raw" : "image", id = fields.get("public_id");
            String format = "raw".equals(type) ? "pdf" : "png";
            if ("id".equals(mutation)) id = "unexpected/" + id;
            String secure =
                    "https://res.cloudinary.com/qa_cloud/"
                            + type
                            + "/upload/v123/"
                            + id
                            + ("raw".equals(type) ? "" : "." + format);
            if ("host".equals(mutation))
                secure = secure.replace("res.cloudinary.com", "other.example");
            if ("http".equals(mutation)) secure = secure.replace("https:", "http:");
            JSONObject body =
                    new JSONObject()
                            .put("public_id", id)
                            .put("secure_url", secure)
                            .put("version", 123)
                            .put("resource_type", type)
                            .put("format", format)
                            .put("bytes", "size".equals(mutation) ? 6000000 : file.length)
                            .put("delete_token", "test-only-delete-token");
            return body.toString();
        }
    }

    @Test
    public void imageUsesUnsignedPresetAndOwnerFolder() throws Exception {
        Fixture client = new Fixture();
        MediaAsset asset = client.upload(PNG, "image/png", FOLDER, "file123");
        assertEquals("https://api.cloudinary.com/v1_1/qa_cloud/image/upload", client.endpoint);
        assertEquals("qa_preset", client.parameters.get("upload_preset"));
        assertEquals(FOLDER, client.parameters.get("asset_folder"));
        assertEquals(FOLDER + "/file123", asset.publicId);
        assertTrue(asset.secureUrl.endsWith("/file123.png"));
        assertFalse(client.parameters.containsKey("api_secret"));
        assertFalse(client.parameters.containsKey("api_key"));
        assertFalse(
                client.parameters.containsKey(
                        "return_delete_token")); // Cấu hình này thuộc preset, không thuộc unsigned
                                                 // request.
    }

    @Test
    public void evidencePdfUsesRawEndpointAndExtension() throws Exception {
        Fixture client = new Fixture();
        MediaAsset asset =
                client.upload(
                        "%PDF-1.4\n".getBytes(),
                        "application/pdf",
                        "broomsharing/verification/qa_host/qa_case",
                        "file123");
        assertTrue(client.endpoint.endsWith("/raw/upload"));
        assertTrue(asset.publicId.endsWith(".pdf"));
        assertEquals("pdf", asset.format);
    }

    @Test
    public void missingConfigurationDoesNotUpload() throws Exception {
        try {
            new CloudinaryClient("", "").upload(PNG, "image/png", FOLDER, "f");
            fail();
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(".env"));
        }
    }

    @Test
    public void privateKeyNeverNeededForRecentDelete() throws Exception {
        Fixture client = new Fixture();
        assertTrue(client.deleteRecent("token"));
        assertEquals(Collections.singletonMap("token", "token"), client.parameters);
    }

    @Test
    public void unsafePathNeverReachesNetwork() throws Exception {
        Fixture client = new Fixture();
        try {
            client.upload(PNG, "image/png", FOLDER, "../other");
            fail();
        } catch (IOException expected) {
        }
        assertEquals(0, client.calls);
    }

    @Test
    public void pdfCannotBeRoomPhoto() throws Exception {
        Fixture client = new Fixture();
        try {
            client.upload("%PDF-1.4".getBytes(), "application/pdf", FOLDER, "file");
            fail();
        } catch (IllegalArgumentException expected) {
        }
        assertEquals(0, client.calls);
    }

    @Test
    public void providerPathOverridesAreRejected() throws Exception {
        reject("id");
    }

    @Test
    public void providerForeignHostIsRejected() throws Exception {
        reject("host");
    }

    @Test
    public void providerPlainHttpIsRejected() throws Exception {
        reject("http");
    }

    @Test
    public void providerOversizeIsRejected() throws Exception {
        reject("size");
    }

    @Test
    public void providerMalformedJsonIsRejected() throws Exception {
        reject("json");
    }

    private void reject(String mutation) throws Exception {
        Fixture client = new Fixture();
        client.mutation = mutation;
        try {
            client.upload(PNG, "image/png", FOLDER, "file");
            fail("Invalid response accepted");
        } catch (IOException | org.json.JSONException expected) {
        }
    }

    private static class HttpClient extends CloudinaryClient {
        HttpClient() {
            super("qa_cloud", "qa_preset");
        }

        String request(String url) throws Exception {
            return super.post(
                    url,
                    Collections.singletonMap("upload_preset", "qa_preset"),
                    PNG,
                    "file.png",
                    "image/png");
        }
    }

    // Kiểm multipart thực qua socket loopback trên Android, gồm cả lỗi HTTP và redirect.
    private String http(int status, String response) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(10000);
            Future<String> request =
                    worker.submit(
                            () -> {
                                try (Socket socket = server.accept()) {
                                    socket.setSoTimeout(10000);
                                    InputStream in = socket.getInputStream();
                                    ByteArrayOutputStream headers = new ByteArrayOutputStream();
                                    int c;
                                    while ((c = in.read()) != -1) {
                                        headers.write(c);
                                        if (headers.toString("UTF-8").endsWith("\r\n\r\n")) break;
                                        if (headers.size() > 16384)
                                            throw new IOException("Headers too big");
                                    }
                                    String text = headers.toString("UTF-8");
                                    int length = 0;
                                    for (String line : text.split("\r\n"))
                                        if (line.toLowerCase(Locale.ROOT)
                                                .startsWith("content-length:"))
                                            length = Integer.parseInt(line.split(":", 2)[1].trim());
                                    assertTrue(length > 0);
                                    byte[] body = new byte[length];
                                    new DataInputStream(in).readFully(body);
                                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                                    String extra =
                                            status == 302
                                                    ? "Location: http://127.0.0.1:1/not-allowed\r\n"
                                                    : "";
                                    socket.getOutputStream()
                                            .write(
                                                    ("HTTP/1.1 "
                                                                    + status
                                                                    + " Test\r\n"
                                                                    + extra
                                                                    + "Content-Type:"
                                                                    + " application/json\r\n"
                                                                    + "Content-Length: "
                                                                    + bytes.length
                                                                    + "\r\n"
                                                                    + "Connection: close\r\n\r\n")
                                                            .getBytes(StandardCharsets.UTF_8));
                                    socket.getOutputStream().write(bytes);
                                    socket.getOutputStream().flush();
                                    return text + new String(body, StandardCharsets.UTF_8);
                                }
                            });
            HttpClient client = new HttpClient();
            try {
                String result =
                        client.request("http://127.0.0.1:" + server.getLocalPort() + "/upload");
                assertEquals(200, status);
                assertEquals(response, result);
            } catch (IOException error) {
                assertNotEquals(200, status);
                assertTrue(
                        error.getMessage().contains("HTTP " + status)
                                || error.getMessage().contains("giới hạn"));
            } finally {
                client.close();
            }
            return request.get(15, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    public void realMultipartContainsFileAndNoSecrets() throws Exception {
        String wire = http(200, "{\"ok\":true}");
        assertTrue(wire.contains("multipart/form-data; boundary="));
        assertTrue(wire.contains("name=\"upload_preset\""));
        assertTrue(wire.contains("filename=\"file.png\""));
        assertTrue(wire.contains("Content-Type: image/png"));
        assertFalse(wire.contains("api_secret"));
        assertFalse(wire.contains("Authorization:"));
    }

    @Test
    public void http400HasActionableError() throws Exception {
        http(400, "{\"error\":{\"message\":\"bad preset\"}}");
    }

    @Test
    public void redirectIsNotFollowed() throws Exception {
        http(302, "");
    }

    @Test
    public void http429IsReported() throws Exception {
        http(429, "{}");
    }
}
