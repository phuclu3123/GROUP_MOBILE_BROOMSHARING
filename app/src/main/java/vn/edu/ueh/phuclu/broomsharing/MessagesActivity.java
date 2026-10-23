
package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestNavigation;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

public class MessagesActivity extends SessionActivity {

    private LinearLayout conversationList;
    private ProgressBar progress;
    private TextView empty;

    private FirebaseFirestore db;
    private ListenerRegistration conversationListener;
    private String uid;

    @Override
    protected int pageLayout() {
        return R.layout.activity_messages;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db = FirebaseHelper.getFirestore();

        ((MaterialToolbar) findViewById(R.id.messagesToolbar))
                .setTitle("Tin nhắn");

        conversationList = findViewById(R.id.conversationList);
        progress = findViewById(R.id.messagesProgress);
        empty = findViewById(R.id.messagesEmpty);

        findViewById(R.id.openNotifications).setOnClickListener(
                v -> startActivity(
                        new Intent(this, NotificationActivity.class)
                )
        );

        GuestNavigation.attach(this, R.id.nav_messages);

        listenConversations();
    }

    private void listenConversations() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        conversationListener = db.collection("conversations")
                .whereArrayContains("participantUids", uid)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    progress.setVisibility(View.GONE);
                    conversationList.removeAllViews();

                    if (error != null) {
                        empty.setText(
                                "Không tải được cuộc trò chuyện. Kiểm tra schema, Rules và index Firebase."
                        );
                        empty.setVisibility(View.VISIBLE);

                        Toast.makeText(
                                this,
                                "Lỗi tải danh sách trò chuyện.",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) {
                        empty.setText("Bạn chưa có cuộc trò chuyện.");
                        empty.setVisibility(View.VISIBLE);
                        return;
                    }

                    empty.setVisibility(View.GONE);

                    for (DocumentSnapshot document : snapshots.getDocuments()) {
                        addConversation(document);
                    }
                });
    }

    private void addConversation(DocumentSnapshot document) {
        String title = document.getString("title");

        if (title == null || title.trim().isEmpty()) {
            title = "Cuộc trò chuyện với Host";
        }

        String lastMessage = document.getString("lastMessage");

        if (lastMessage == null || lastMessage.trim().isEmpty()) {
            lastMessage = "Bắt đầu trò chuyện";
        }

        Date updatedAt = document.getDate("updatedAt");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 14, 16, 14);
        card.setBackgroundResource(R.drawable.bg_card);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);

        cardParams.setMargins(0, 0, 0, 10);
        conversationList.addView(card, cardParams);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(getColor(R.color.text_primary));
        card.addView(titleView);

        TextView previewView = new TextView(this);

        String preview = lastMessage;

        if (updatedAt != null) {
            preview += "\n" + new SimpleDateFormat(
                    "dd/MM HH:mm",
                    Locale.forLanguageTag("vi-VN")
            ).format(updatedAt);
        }

        previewView.setText(preview);
        previewView.setTextSize(13);
        previewView.setTextColor(getColor(R.color.text_secondary));

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(-1, -2);

        previewParams.topMargin = 6;
        card.addView(previewView, previewParams);

        Button openButton = new Button(this);
        openButton.setText("Mở trò chuyện");

        String conversationTitle = title;

        openButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("conversationId", document.getId());
            intent.putExtra("peerName", conversationTitle);
            startActivity(intent);
        });

        card.addView(openButton);
    }

    @Override
    protected void onDestroy() {
        if (conversationListener != null) {
            conversationListener.remove();
        }

        super.onDestroy();
    }
}
