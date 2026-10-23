
package vn.edu.ueh.phuclu.broomsharing;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;

public class ChatActivity extends SessionActivity {

    private String conversationId;
    private String uid;

    private FirebaseFirestore db;
    private ListenerRegistration messageListener;

    private LinearLayout messageList;
    private ScrollView messageScroll;
    private EditText input;
    private TextView status;

    @Override
    protected int pageLayout() {
        return R.layout.activity_chat;
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db = FirebaseHelper.getFirestore();

        conversationId = getIntent().getStringExtra("conversationId");

        if (conversationId == null || conversationId.trim().isEmpty()) {
            Toast.makeText(this, "Không tìm thấy cuộc trò chuyện.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.chatToolbar);

        String peerName = getIntent().getStringExtra("peerName");

        if (peerName == null || peerName.trim().isEmpty()) {
            peerName = "Trò chuyện với Host";
        }

        toolbar.setTitle(peerName);

        messageList = findViewById(R.id.chatMessageList);
        messageScroll = findViewById(R.id.chatScroll);
        input = findViewById(R.id.chatInput);
        status = findViewById(R.id.chatStatus);

        findViewById(R.id.chatSend).setOnClickListener(v -> sendMessage());

        loadMessages();
    }

    private void loadMessages() {
        status.setText("Đang tải tin nhắn...");

        messageListener = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(200)
                .addSnapshotListener((snapshots, error) -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (error != null) {
                        status.setText("Không tải được tin nhắn. Hãy kiểm tra kết nối và quyền Firebase.");
                        return;
                    }

                    messageList.removeAllViews();

                    if (snapshots == null || snapshots.isEmpty()) {
                        status.setText("Chưa có tin nhắn. Hãy gửi lời chào đầu tiên.");
                        return;
                    }

                    status.setText("");

                    for (DocumentSnapshot document : snapshots.getDocuments()) {
                        addMessage(document);
                    }

                    messageScroll.post(
                            () -> messageScroll.fullScroll(View.FOCUS_DOWN)
                    );
                });
    }

    private void addMessage(DocumentSnapshot document) {
        String text = document.getString("text");

        if (text == null) {
            text = "";
        }

        String senderUid = document.getString("senderUid");
        boolean isMine = uid.equals(senderUid);

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(15);
        bubble.setPadding(14, 10, 14, 10);

        if (isMine) {
            bubble.setTextColor(getColor(android.R.color.white));
            bubble.setBackgroundResource(R.drawable.bg_circle_teal);
        } else {
            bubble.setTextColor(getColor(R.color.text_primary));
            bubble.setBackgroundResource(R.drawable.bg_card);
        }

        int maxWidth = (int) (
                getResources().getDisplayMetrics().widthPixels * 0.75
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(maxWidth, -2);

        params.gravity = isMine ? Gravity.END : Gravity.START;
        params.setMargins(0, 4, 0, 4);

        messageList.addView(bubble, params);
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();

        if (text.isEmpty()) {
            input.setError("Vui lòng nhập tin nhắn.");
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("senderUid", uid);
        message.put("text", text);
        message.put("createdAt", FieldValue.serverTimestamp());

        input.setEnabled(false);

        db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document()
                .set(message)
                .addOnSuccessListener(unused -> {

                    input.setText("");
                    input.setEnabled(true);

                    db.collection("conversations")
                            .document(conversationId)
                            .update(
                                    "lastMessage", text,
                                    "updatedAt", FieldValue.serverTimestamp()
                            );
                })
                .addOnFailureListener(error -> {
                    input.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Gửi tin nhắn thất bại. Vui lòng thử lại.",
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    @Override
    protected void onDestroy() {
        if (messageListener != null) {
            messageListener.remove();
        }

        super.onDestroy();
    }
}
