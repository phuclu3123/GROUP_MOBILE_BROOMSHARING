package vn.edu.ueh.phuclu.broomsharing.ui.booking;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;

// Chỉ sống trong phiên thanh toán ngắn đã có QR. Không ghi trạng thái tiền hoặc giữ lịch từ điện thoại.
public final class PaymentReminderService extends Service {
    private static final String CHANNEL = "payment_deadlines";
    private static final int NOTICE = 0x42535041;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PaymentRepository repository = new PaymentRepository();
    private final Set<String> tracked = new HashSet<>();
    private List<Payment> payments = new ArrayList<>();
    private ListenerRegistration listener;
    private String uid;
    private long latestDeadline, lastCheck;
    private boolean started, disposed;
    private FirebaseAuth.AuthStateListener authListener;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (disposed) return;
            render();
            if (System.currentTimeMillis() - lastCheck >= 15000) {
                lastCheck = System.currentTimeMillis();
                for (Payment payment : new ArrayList<>(payments))
                    if (tracked.contains(payment.bookingId) && !payment.received()
                            && ("PENDING".equals(payment.status) || "CREATING".equals(payment.status)))
                        repository.request(payment.bookingId, false, new ResultCallback<Payment>() {
                            public void onSuccess(Payment value) { }
                            public void onError(Exception error) { }
                        });
            }
            if (!disposed) handler.postDelayed(this, 1000);
        }
    };

    // Mặc định bật nhắc thanh toán theo UID, nhưng tôn trọng lựa chọn tắt đã được người dùng lưu.
    public static boolean enabled(Context context) {
        String uid = FirebaseAuth.getInstance().getUid();
        return uid != null && context.getSharedPreferences("payment_reminders_" + uid, MODE_PRIVATE).getBoolean("enabled", true);
    }

    public static boolean shouldAskPermission(Context context) {
        String actor = FirebaseAuth.getInstance().getUid();
        return actor != null && enabled(context) && Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !context.getSharedPreferences("payment_reminders_" + actor, MODE_PRIVATE).getBoolean("permissionAsked", false);
    }

    public static void markPermissionAsked(Context context) {
        String actor = FirebaseAuth.getInstance().getUid();
        if (actor != null) context.getSharedPreferences("payment_reminders_" + actor, MODE_PRIVATE)
                .edit().putBoolean("permissionAsked", true).apply();
    }

    public static void setEnabled(Context context, boolean value) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) context.getSharedPreferences("payment_reminders_" + uid, MODE_PRIVATE).edit().putBoolean("enabled", value).apply();
        if (!value) context.stopService(new Intent(context, PaymentReminderService.class));
    }

    public static boolean allowed(Context context) {
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                && context.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    // Caller là Activity đang hiển thị và đã nhận snapshot giao dịch thật từ server.
    public static void start(Context context, Payment payment) {
        if (!enabled(context) || !allowed(context) || !payment.canPay(System.currentTimeMillis())
                || (payment.qrCode.isEmpty() && payment.checkoutUrl.isEmpty())) return;
        Intent intent = new Intent(context, PaymentReminderService.class)
                .putExtra("bookingId", payment.bookingId).putExtra("expiresAt", payment.expiresAt)
                .putExtra("uid", FirebaseAuth.getInstance().getUid());
        try { ContextCompat.startForegroundService(context, intent); }
        catch (RuntimeException unavailable) { android.util.Log.w("PaymentReminder", "Cannot start reminder while app is not visible"); }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !enabled(this) || !allowed(this)) { stopSelf(); return START_NOT_STICKY; }
        String actor = intent.getStringExtra("uid"), id = intent.getStringExtra("bookingId");
        long deadline = intent.getLongExtra("expiresAt", 0);
        if (actor == null || !actor.equals(FirebaseAuth.getInstance().getUid()) || id == null
                || !id.matches("[A-Za-z0-9_-]{1,120}") || deadline * 1000 <= System.currentTimeMillis()) {
            stopSelf(); return START_NOT_STICKY;
        }
        if (uid != null && !uid.equals(actor)) { stopSelf(); return START_NOT_STICKY; }
        uid = actor; tracked.add(id); latestDeadline = Math.max(latestDeadline, deadline);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Nhắc thanh toán đặt chỗ", NotificationManager.IMPORTANCE_DEFAULT));
        if (!started) {
            startForeground(NOTICE, ongoing(id, deadline, false), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            started = true;
            authListener = auth -> { if (!uid.equals(auth.getUid())) stopSelf(); };
            FirebaseAuth.getInstance().addAuthStateListener(authListener);
            listener = repository.watchMine(new ResultCallback<List<Payment>>() {
                public void onSuccess(List<Payment> value) { if (!disposed) { payments = value; render(); } }
                public void onError(Exception error) {
                    if (FirebaseAuth.getInstance().getUid() == null
                            || error instanceof com.google.firebase.firestore.FirebaseFirestoreException
                            && ((com.google.firebase.firestore.FirebaseFirestoreException) error).getCode()
                            == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) stopSelf();
                }
            });
            handler.post(tick);
        }
        return START_NOT_STICKY;
    }

    private PendingIntent target(String id) {
        Intent intent = new Intent(this, BookingPaymentActivity.class).putExtra("bookingId", id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification ongoing(String id, long deadline, boolean elapsed) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_booking).setContentTitle(elapsed ? "Hết thời hạn • Đang đối soát" : "Đặt chỗ đang chờ thanh toán")
                .setContentText(elapsed ? "Mở ứng dụng để kiểm tra kết quả; không chuyển thêm tiền." : "Thời gian còn lại • Chạm để tiếp tục đúng QR")
                .setContentIntent(target(id)).addAction(0, "Tiếp tục thanh toán", target(id))
                .setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        if (!elapsed) builder.setWhen(deadline * 1000).setUsesChronometer(true).setChronometerCountDown(true);
        return builder.build();
    }

    // Dựng thông báo từ giao dịch đang theo dõi; hết hạn trên đồng hồ không tự xác nhận tiền hoặc tự sửa khóa lịch.
    private void render() {
        if (uid == null || disposed || !enabled(this)) return;
        Payment earliest = null;
        for (Payment payment : payments) {
            if (!tracked.contains(payment.bookingId)) continue;
            if (payment.received() || "EXPIRED".equals(payment.status) || "CANCELLED".equals(payment.status)) {
                String delivered = "result:" + payment.orderCode + ":" + payment.status;
                SharedPreferences preferences = getSharedPreferences("payment_reminders_" + uid, MODE_PRIVATE);
                if (!preferences.getBoolean(delivered, false)) {
                    String title = "PAID".equals(payment.status) ? "Thanh toán thành công • Đã nhận 40%"
                            : "REVIEW_REQUIRED".equals(payment.status) ? "Đã nhận tiền • Cần đối soát"
                            : "EXPIRED".equals(payment.status) ? "Đặt chỗ không thành công • Hết hạn thanh toán" : "Giao dịch thanh toán đã hủy";
                    getSystemService(NotificationManager.class).notify(payment.bookingId.hashCode(), new NotificationCompat.Builder(this, CHANNEL)
                            .setSmallIcon(R.drawable.ic_booking).setContentTitle(title).setContentText("Chạm để xem kết quả đặt chỗ.")
                            .setContentIntent(target(payment.bookingId)).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build());
                    preferences.edit().putBoolean(delivered, true).apply();
                }
                tracked.remove(payment.bookingId);
            } else if (earliest == null || payment.expiresAt < earliest.expiresAt) earliest = payment;
        }
        if (tracked.isEmpty()) { stopSelf(); return; }
        if (earliest != null) getSystemService(NotificationManager.class).notify(NOTICE,
                ongoing(earliest.bookingId, earliest.expiresAt, !earliest.canPay(System.currentTimeMillis())));
        // Nếu server mất mạng thì không giữ service vô hạn và không tự kết luận đã nhả lịch.
        if (System.currentTimeMillis() > latestDeadline * 1000 + 60000) stopSelf();
    }

    @Override public void onDestroy() {
        disposed = true;
        handler.removeCallbacks(tick);
        if (listener != null) listener.remove();
        if (authListener != null) FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
