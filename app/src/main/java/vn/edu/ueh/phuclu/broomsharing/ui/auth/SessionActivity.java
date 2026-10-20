package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import vn.edu.ueh.phuclu.broomsharing.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;

/**
 * Shared session screen observes AuthViewModel; no Repository or Firebase calls from Activities.
 */
public abstract class SessionActivity extends AppCompatActivity {
    private AuthViewModel model;
    private boolean shown;
    private vn.edu.ueh.phuclu.broomsharing.ui.ChatViewModel chat;
    private vn.edu.ueh.phuclu.broomsharing.ui.BookingNoticeViewModel notices;

    protected abstract int pageLayout();

    protected void onProfileReady(UserProfile profile) {}

    protected boolean requiresAdmin() {
        return false;
    }

    protected boolean requiresHost() {
        return false;
    }

    protected boolean isGuestHome() {
        return false;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(AuthViewModel.class);
        setContentView(R.layout.activity_session_check);
        findViewById(R.id.retryButton).setOnClickListener(v -> checkSession());
        if (!model.hasSession()) {
            toLogin(0);
            return;
        }
        model.reloadSession();
        model.getState()
                .observe(
                        this,
                        value -> {
                            if (shown || isFinishing() || isDestroyed()) return;
                            findViewById(R.id.authProgress)
                                    .setVisibility(value.loading ? View.VISIBLE : View.GONE);
                            findViewById(R.id.retryButton)
                                    .setVisibility(value.error == null ? View.GONE : View.VISIBLE);
                            ((TextView) findViewById(R.id.authMessage))
                                    .setText(
                                            value.error == null
                                                    ? R.string.auth_busy
                                                    : AuthMessages.resource(value.error));
                            if (value.profile != null) show(value.profile);
                        });
    }

    private void checkSession() {
        if (!model.hasSession()) toLogin(0);
        else model.reloadSession();
    }

    private void show(UserProfile profile) {
        if (requiresAdmin() && profile.getUserRole() != UserRole.ADMIN) {
            toLogin(R.string.auth_role_invalid);
            return;
        }
        if (requiresHost() && profile.getUserRole() != UserRole.HOST) {
            android.widget.Toast.makeText(
                            this,
                            "Chức năng này dành cho tài khoản Host đã được duyệt.",
                            android.widget.Toast.LENGTH_LONG)
                    .show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        if (isGuestHome() && profile.getUserRole() == UserRole.ADMIN) {
            startActivity(
                    new Intent(this, AdminDashboardActivity.class)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }
        shown = true;
        setContentView(pageLayout());
        onProfileReady(profile);
        if (profile.getUserRole() != UserRole.ADMIN && !(this instanceof ChatActivity)) {
            chat = new ViewModelProvider(this).get(vn.edu.ueh.phuclu.broomsharing.ui.ChatViewModel.class);
            chat.inbox.observe(this, state -> {
                if (state == null || state.data == null) return;
                long unread = 0;
                for (Conversation c : state.data) unread += c.unread(profile.uid);
                updateBadge(R.id.bottomNavigation, R.id.nav_messages, unread);
                updateBadge(R.id.managementNavigation, R.id.host_messages, unread);
            });
            chat.watchInbox();
        }
        if (profile.getUserRole()!=UserRole.ADMIN) {
            notices=new ViewModelProvider(this).get(vn.edu.ueh.phuclu.broomsharing.ui.BookingNoticeViewModel.class);
            notices.notices.observe(this,state->{if(!isFinishing()&&model.hasSession()&&model.getState().getValue()!=null&&model.getState().getValue().profile!=null&&state!=null&&state.data!=null)vn.edu.ueh.phuclu.broomsharing.ui.common.BookingNotifier.deliver(this,profile.uid,state.data);});
            notices.start(profile.getUserRole()==UserRole.HOST);
        }
    }

    private void updateBadge(int viewId, int itemId, long unread) {
        com.google.android.material.bottomnavigation.BottomNavigationView nav = findViewById(viewId);
        if (nav == null || nav.getMenu().findItem(itemId) == null) return;
        if (unread == 0) nav.removeBadge(itemId);
        else nav.getOrCreateBadge(itemId).setNumber((int)Math.min(unread, 999));
    }
    @Override protected void onStart() { super.onStart(); if (chat != null) chat.watchInbox(); if(notices!=null)notices.resume(); }
    @Override protected void onStop() { if (chat != null) chat.stop(); if(notices!=null)notices.stop(); super.onStop(); }

    protected void signOut() {
        if(chat!=null)chat.stop();if(notices!=null)notices.stop();
        androidx.core.app.NotificationManagerCompat.from(this).cancelAll();
        model.signOut();
        toLogin(0);
    }

    private void toLogin(int notice) {
        if (notice != 0) model.signOut();
        Intent intent =
                new Intent(this, LoginActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (notice != 0) intent.putExtra("auth_notice", notice);
        startActivity(intent);
        finish();
    }
}
