package vn.edu.ueh.phuclu.broomsharing.repository;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

import okhttp3.*;

import org.json.JSONObject;

import vn.edu.ueh.phuclu.broomsharing.BuildConfig;
import vn.edu.ueh.phuclu.broomsharing.model.Payment;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

// Gửi Firebase ID token tới backend Java; realtime chỉ đọc payments/{bookingId} từ server.
public final class PaymentRepository {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http =
            new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(75, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build();

    public static boolean configured() {
        return !BuildConfig.PAYMENT_API_BASE_URL.isEmpty();
    }

    public void request(String bookingId, boolean create, ResultCallback<Payment> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null
                || !configured()
                || bookingId == null
                || !bookingId.matches("[A-Za-z0-9_-]{1,120}")) {
            callback.onError(
                    new IllegalStateException(
                            configured()
                                    ? "Vui lòng đăng nhập lại."
                                    : "Kênh thanh toán demo chưa được cấu hình trên bản app này."));
            return;
        }
        String uid = user.getUid();
        user.getIdToken(false)
                .addOnSuccessListener(
                        token -> {
                            if (!uid.equals(auth.getUid())) {
                                callback.onError(new IllegalStateException("Phiên đã thay đổi."));
                                return;
                            }
                            Request.Builder builder =
                                    new Request.Builder()
                                            .url(
                                                    BuildConfig.PAYMENT_API_BASE_URL
                                                            + "/api/bookings/"
                                                            + bookingId
                                                            + "/payment")
                                            .header("Authorization", "Bearer " + token.getToken());
                            if (create)
                                builder.post(
                                        RequestBody.create(
                                                "{}", MediaType.get("application/json")));
                            http.newCall(builder.build())
                                    .enqueue(
                                            new Callback() {
                                                public void onFailure(Call call, IOException e) {
                                                    main.post(
                                                            () ->
                                                                    callback.onError(
                                                                            new IOException(
                                                                                    "Không kết nối"
                                                                                        + " được"
                                                                                        + " server"
                                                                                        + " thanh"
                                                                                        + " toán."
                                                                                        + " Vui lòng"
                                                                                        + " thử lại.")));
                                                }

                                                public void onResponse(
                                                        Call call, Response response) {
                                                    try (Response result = response) {
                                                        if (result.body() == null)
                                                            throw new IOException(
                                                                    "Server chưa trả thông tin giao"
                                                                            + " dịch.");
                                                        JSONObject json =
                                                                new JSONObject(
                                                                        result.body().string());
                                                        if (!result.isSuccessful())
                                                            throw new IOException(
                                                                    json.optString(
                                                                            "message",
                                                                            "Chưa xử lý được giao"
                                                                                    + " dịch."));
                                                        Payment p = decode(json, bookingId);
                                                        main.post(
                                                                () -> {
                                                                    if (uid.equals(auth.getUid()))
                                                                        callback.onSuccess(p);
                                                                    else
                                                                        callback.onError(
                                                                                new IllegalStateException(
                                                                                        "Phiên đã"
                                                                                            + " thay"
                                                                                            + " đổi."));
                                                                });
                                                    } catch (Exception e) {
                                                        main.post(() -> callback.onError(e));
                                                    }
                                                }
                                            });
                        })
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration watch(String id, ResultCallback<Payment> callback) {
        return FirebaseHelper.getFirestore()
                .collection("payments")
                .document(id)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (snapshot, error) -> {
                            if (error != null) {
                                callback.onError(error);
                                return;
                            }
                            if (snapshot == null
                                    || !snapshot.exists()
                                    || snapshot.getMetadata().isFromCache()
                                    || snapshot.getMetadata().hasPendingWrites()) return;
                            callback.onSuccess(snapshot.toObject(Payment.class));
                        });
    }

    static Payment decode(JSONObject json, String expectedId) {
        Payment p = new Payment();
        p.bookingId = json.optString("bookingId");
        p.status = json.optString("status");
        p.orderCode = json.optLong("orderCode");
        p.amount = json.optLong("amount");
        p.paidAmount = json.optLong("paidAmount");
        p.expiresAt = json.optLong("expiresAt");
        p.qrCode = json.optString("qrCode");
        p.checkoutUrl = json.optString("checkoutUrl");
        p.accountName = json.optString("accountName");
        p.accountNumber = json.optString("accountNumber");
        p.bin = json.optString("bin");
        if (!expectedId.equals(p.bookingId)
                || p.orderCode <= 0
                || p.amount <= 0
                || !java.util.Arrays.asList(
                                "CREATING",
                                "PENDING",
                                "PAID",
                                "REVIEW_REQUIRED",
                                "CANCELLED",
                                "EXPIRED")
                        .contains(p.status))
            throw new IllegalArgumentException("Thông tin giao dịch không khớp booking.");
        if (!p.checkoutUrl.isEmpty()) {
            android.net.Uri link = android.net.Uri.parse(p.checkoutUrl);
            if (!"https".equals(link.getScheme())
                    || !"pay.payos.vn".equals(link.getHost())
                    || link.getUserInfo() != null
                    || link.getPort() != -1)
                throw new IllegalArgumentException("Đường dẫn thanh toán không hợp lệ.");
        }
        if (p.received() && p.paidAmount < p.amount)
            throw new IllegalArgumentException("Giao dịch chưa ghi nhận đủ khoản trả trước.");
        return p;
    }
}
