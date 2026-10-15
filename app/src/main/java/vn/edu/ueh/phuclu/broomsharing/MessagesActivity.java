package vn.edu.ueh.phuclu.broomsharing;

import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestSectionActivity;

public class MessagesActivity extends GuestSectionActivity {
    @Override
    protected int tab() {
        return R.id.nav_messages;
    }

    @Override
    protected int title() {
        return R.string.nav_messages_label;
    }

    @Override
    protected int heading() {
        return R.string.messages_pending_title;
    }

    @Override
    protected int description() {
        return R.string.messages_pending_body;
    }

    @Override
    protected int icon() {
        return R.drawable.ic_chat;
    }
}
