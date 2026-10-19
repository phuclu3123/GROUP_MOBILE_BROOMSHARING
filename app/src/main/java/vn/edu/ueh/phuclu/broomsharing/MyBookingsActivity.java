package vn.edu.ueh.phuclu.broomsharing;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.app.AlertDialog;
import vn.edu.ueh.phuclu.broomsharing.adapter.BookingAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityMyBookingsLiveBinding;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.ui.BookingViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestNavigation;

public class MyBookingsActivity extends SessionActivity {
    private ActivityMyBookingsLiveBinding b;
    private BookingViewModel model;
    private String uid;
    @Override protected int pageLayout() { return R.layout.activity_my_bookings_live; }
    @Override protected void onProfileReady(UserProfile profile) {
        uid = profile.uid;
        b = ActivityMyBookingsLiveBinding.bind(((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        model = new ViewModelProvider(this).get(BookingViewModel.class);
        BookingAdapter adapter = new BookingAdapter(item -> new AlertDialog.Builder(this)
                .setTitle(R.string.booking_cancel).setMessage(R.string.booking_cancel_help)
                .setPositiveButton(R.string.booking_cancel, (d, w) -> model.cancel(item.id, uid))
                .setNegativeButton(android.R.string.cancel, null).show());
        b.bookings.setLayoutManager(new LinearLayoutManager(this)); b.bookings.setAdapter(adapter);
        b.refresh.setOnClickListener(v -> model.loadMine(uid));
        model.bookings.observe(this, state -> {
            if (state == null) return;
            b.refresh.setEnabled(!state.loading && !model.busy());
            b.feedback.setText(state.loading ? getString(R.string.booking_loading) : state.error == null ? "" : state.error);
            if (state.data != null) {
                adapter.setData(state.data);
                if (state.data.isEmpty()) b.feedback.setText(R.string.booking_empty);
            }
        });
        model.saved.observe(this, state -> {
            if (state == null) return;
            adapter.setBusy(state.loading); b.refresh.setEnabled(!state.loading);
            b.feedback.setText(state.loading ? getString(R.string.booking_sending) : state.error == null ? getString(R.string.booking_updated) : state.error);
            if (state.data != null) model.saved.setValue(null);
        });
        GuestNavigation.attach(this, R.id.nav_bookings);
        model.loadMine(uid);
    }
    @Override protected void onResume() { super.onResume(); if (model != null && uid != null && !model.busy()) model.loadMine(uid); }
}
