package vn.edu.ueh.broomsharing.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

// Java HTTP server nhỏ, chỉ làm cầu nối Firebase Auth, Firestore và payOS; không dùng Spring.
public final class PaymentServer {
    public static void main(String[] args) throws Exception {
        Config config = new Config(Path.of(".env"));
        PaymentPolicy.require(
                config.get("FIRESTORE_EMULATOR_HOST", "").isBlank()
                        && config.get("FIREBASE_AUTH_EMULATOR_HOST", "").isBlank(),
                "Không chạy server payOS thật cùng cấu hình Firebase emulator.");
        String base = config.required("PUBLIC_BASE_URL");
        URI uri = URI.create(base);
        PaymentPolicy.require(
                "https".equals(uri.getScheme())
                        && uri.getHost() != null
                        && uri.getQuery() == null
                        && uri.getFragment() == null
                        && uri.getUserInfo() == null
                        && (uri.getPath().isEmpty() || uri.getPath().equals("/")),
                "PUBLIC_BASE_URL phải là địa chỉ HTTPS gốc.");
        String credentialPath = config.get("GOOGLE_APPLICATION_CREDENTIALS", "");
        GoogleCredentials credentials;
        if (credentialPath.isBlank()) credentials = GoogleCredentials.getApplicationDefault();
        else
            try (InputStream input = Files.newInputStream(Path.of(credentialPath))) {
                credentials = GoogleCredentials.fromStream(input);
            }
        FirebaseApp app =
                FirebaseApp.initializeApp(
                        FirebaseOptions.builder()
                                .setCredentials(credentials)
                                .setProjectId(
                                        config.get(
                                                "FIREBASE_PROJECT_ID",
                                                "broom-sharing-firebase-mobile"))
                                .build());
        Firestore database =
                FirestoreClient.getFirestore(
                        app, config.get("FIRESTORE_DATABASE_ID", "broomsharing"));
        String hostUid = config.required("PAYMENT_DEMO_HOST_UID");
        // Chỉ báo server sẵn sàng sau khi đã đọc đúng Host trên Firestore và Auth thật.
        var hostProfile =
                database.collection("users").document(hostUid).get().get(30, TimeUnit.SECONDS);
        PaymentPolicy.require(
                "HOST".equals(hostProfile.getString("role"))
                        && "ACTIVE".equals(hostProfile.getString("status")),
                "PAYMENT_DEMO_HOST_UID phải trỏ đến Host ACTIVE.");
        PaymentPolicy.require(
                !FirebaseAuth.getInstance(app).getUser(hostUid).isDisabled(),
                "Tài khoản Firebase Auth của Host đã bị vô hiệu hóa.");
        PaymentService service =
                new PaymentService(
                        database, new PayOSGateway(config), hostUid, base.replaceAll("/$", ""));
        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(
                                config.get("BIND_HOST", "127.0.0.1"),
                                Integer.parseInt(config.get("PORT", "8090"))),
                        50);
        ObjectMapper json = new ObjectMapper();
        server.createContext(
                "/",
                exchange -> {
                    try {
                        exchange.getResponseHeaders().set("Cache-Control", "no-store");
                        String path = exchange.getRequestURI().getPath(),
                                method = exchange.getRequestMethod();
                        if (path.equals("/health") && method.equals("GET")) {
                            reply(exchange, 200, json.writeValueAsBytes(Map.of("status", "ok")));
                            return;
                        }
                        if (path.startsWith("/payment/") && method.equals("GET")) {
                            reply(
                                    exchange,
                                    200,
                                    json.writeValueAsBytes(
                                            Map.of(
                                                    "message",
                                                    "Vui lòng trở lại BroomSharing để kiểm tra giao"
                                                        + " dịch. Đường dẫn quay lại không xác nhận"
                                                        + " đã thanh toán.")));
                            return;
                        }
                        if (path.equals("/api/payos/webhook") && method.equals("POST")) {
                            service.webhook(body(exchange));
                            reply(exchange, 200, "{}".getBytes());
                            return;
                        }
                        String token = exchange.getRequestHeaders().getFirst("Authorization");
                        if (token == null || !token.startsWith("Bearer ")) {
                            reply(
                                    exchange,
                                    401,
                                    json.writeValueAsBytes(
                                            Map.of("message", "Vui lòng đăng nhập lại.")));
                            return;
                        }
                        String uid;
                        try {
                            uid =
                                    FirebaseAuth.getInstance(app)
                                            .verifyIdToken(token.substring(7), true)
                                            .getUid();
                        } catch (Exception invalid) {
                            reply(
                                    exchange,
                                    401,
                                    json.writeValueAsBytes(
                                            Map.of("message", "Phiên đăng nhập không hợp lệ.")));
                            return;
                        }
                        String[] parts = path.split("/");
                        if (parts.length == 5
                                && parts[1].equals("api")
                                && parts[2].equals("bookings")
                                && parts[4].equals("payment")) {
                            Object result;
                            if (method.equals("POST")) {
                                body(exchange);
                                result = service.checkout(uid, parts[3]);
                            } else if (method.equals("GET"))
                                result = service.refresh(uid, parts[3]);
                            else {
                                reply(exchange, 405, "{}".getBytes());
                                return;
                            }
                            reply(exchange, 200, json.writeValueAsBytes(result));
                            return;
                        }
                        reply(exchange, 404, "{}".getBytes());
                    } catch (Exception error) {
                        Throwable cause = error;
                        while (cause.getCause() != null) cause = cause.getCause();
                        boolean expected =
                                cause instanceof IllegalArgumentException
                                        || cause instanceof IllegalStateException;
                        // Không in token, checksum key hoặc payload ngân hàng vào log.
                        System.err.println(
                                "Payment request failed: " + cause.getClass().getSimpleName());
                        reply(
                                exchange,
                                expected ? 409 : 502,
                                json.writeValueAsBytes(
                                        Map.of(
                                                "message",
                                                expected
                                                        ? cause.getMessage()
                                                        : "Chưa kết nối được dịch vụ thanh toán."
                                                                + " Vui lòng thử lại.")));
                    } finally {
                        exchange.close();
                    }
                });
        server.setExecutor(
                new ThreadPoolExecutor(
                        4,
                        16,
                        60,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(100),
                        new ThreadPoolExecutor.CallerRunsPolicy()));
        server.start();
        System.out.println(
                "BroomSharing Java payment server listening on " + server.getAddress().getPort());
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    server.stop(2);
                                    app.delete();
                                }));
    }

    private static byte[] body(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(65_537);
        PaymentPolicy.require(bytes.length <= 65_536, "Yêu cầu vượt kích thước cho phép.");
        return bytes;
    }

    private static void reply(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
