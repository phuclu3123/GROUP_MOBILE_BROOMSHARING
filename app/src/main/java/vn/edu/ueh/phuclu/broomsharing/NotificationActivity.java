
package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

public class NotificationActivity extends SessionActivity {

    private FirebaseFirestore db;
    private ListenerRegistration notificationListener;

    private String uid;

    private LinearLayout notificationList;
    private ProgressBar progress;
    private TextView empty;

    @Override
    protected int pageLayout() {
        return R.layout.activity_notifications;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db = FirebaseHelper.getFirestore();

        ((MaterialToolbar) findViewById(R.id.notificationsToolbar))
                .setTitle("Thông báo");

        notificationList = findViewById(R.id.notificationsList);
        progress = findViewById(R.id.notificationsProgress);
        empty = findViewById(R.id.notificationsEmpty);

        findViewById(R.id.notificationsRefresh)
                .setOnClickListener(v -> loadNotifications());

        loadNotifications();
    }

    private void loadNotifications() {
        if (notificationListener != null) {
            notificationListener.remove();
        }

        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        notificationListener = db.collection("notifications")
                .whereEqualTo("recipientUid", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((snapshots, error) -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    progress.setVisibility(View.GONE);
                    notificationList.removeAllViews();

                    if (error != null) {
                        empty.setText(
                                "Không tải được thông báo. Kiểm tra schema, Rules và index Firebase."
                        );
                        empty.setVisibility(View.VISIBLE);

                        Toast.makeText(
                                this,
                                "Lỗi tải thông báo.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) {
                        empty.setText("Bạn chưa có thông báo.");
                        empty.setVisibility(View.VISIBLE);
                        return;
                    }

                    empty.setVisibility(View.GONE);

                    for (DocumentSnapshot document : snapshots.getDocuments()) {
                        addNotification(document);
                    }
                });
    }

    private void addNotification(DocumentSnapshot document) {
        String title = document.getString("title");

        if (title == null || title.trim().isEmpty()) {
            title = "Thông báo Broom Sharing";
        }

        String body = document.getString("body");

        if (body == null) {
            body = "";
        }

        boolean isRead =
                Boolean.TRUE.equals(document.getBoolean("read"));

        Date createdAt = document.getDate("createdAt");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 14, 16, 14);
        card.setBackgroundResource(R.drawable.bg_card);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);

        cardParams.setMargins(0, 0, 0, 10);
        notificationList.addView(card, cardParams);

        TextView titleView = new TextView(this);

        titleView.setText((isRead ? "" : "●  ") + title);
        titleView.setTextSize(15);
        titleView.setTextColor(getColor(R.color.text_primary));
        titleView.setTypeface(
                null,
                isRead ? Typeface.NORMAL : Typeface.BOLD
        );

        card.addView(titleView);

        TextView bodyView = new TextView(this);
        bodyView.setText(body);
        bodyView.setTextSize(13);
        bodyView.setTextColor(getColor(R.color.text_secondary));

        LinearLayout.LayoutParams bodyParams =
                new LinearLayout.LayoutParams(-1, -2);

        bodyParams.topMargin = 6;
        card.addView(bodyView, bodyParams);

        if (createdAt != null) {
            TextView timeView = new TextView(this);

            timeView.setText(
                    new SimpleDateFormat(
                            "dd/MM/yyyy HH:mm",
                            Locale.forLanguageTag("vi-VN")
                    ).format(createdAt)
            );

            timeView.setTextSize(11);
            timeView.setTextColor(getColor(R.color.text_secondary));
            card.addView(timeView);
        }

        card.setOnClickListener(v -> openNotification(document, isRead));
    }

    private void openNotification(
            DocumentSnapshot document,
            boolean isRead
    ) {
        if (!isRead) {
            db.collection("notifications")
                    .document(document.getId())
                    .update(
                            "read", true,
                            "readAt", FieldValue.serverTimestamp()
                    )
                    .addOnFailureListener(error ->
                            Toast.makeText(
                                    this,
                                    "Không thể cập nhật trạng thái đã đọc.",
                                    Toast.LENGTH_SHORT
                            ).show()
                    );
        }

        String type = document.getString("type");
        String relatedId = document.getString("relatedId");

        if ("CHAT".equals(type)) {
            if (relatedId == null || relatedId.trim().isEmpty()) {
                Toast.makeText(
                        this,
                        "Thông báo chưa có mã cuộc trò chuyện.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("conversationId", relatedId);
            startActivity(intent);

        } else if ("BOOKING".equals(type)) {
            startActivity(new Intent(this, MyBookingsActivity.class));
        }
    }

    @Override
    protected void onDestroy() {
        if (notificationListener != null) {
            notificationListener.remove();
        }

        super.onDestroy();
    }
}
