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
    private Integer restoredFilter;
    @Override protected void onCreate(android.os.Bundle state) { if(state!=null)restoredFilter=state.getInt("booking.filter",0);super.onCreate(state); }
    @Override protected void onSaveInstanceState(android.os.Bundle state) { state.putInt("booking.filter",model==null?(restoredFilter==null?0:restoredFilter):model.filterPosition);super.onSaveInstanceState(state); }
    @Override protected int pageLayout() { return R.layout.activity_my_bookings_live; }
    @Override protected void onProfileReady(UserProfile profile) {
        uid = profile.uid;
        b = ActivityMyBookingsLiveBinding.bind(((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        model = new ViewModelProvider(this).get(BookingViewModel.class);
        if(restoredFilter!=null)model.filterPosition=restoredFilter;
        BookingAdapter adapter = new BookingAdapter(item -> {
            long now=System.currentTimeMillis();
            Integer rate=vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy.versioned(item)
                    ? vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy.percent(item,now):null;
            new AlertDialog.Builder(this).setTitle(R.string.booking_cancel)
                .setMessage(vn.edu.ueh.phuclu.broomsharing.utils.CancellationPolicy.preview(item,now))
                .setPositiveButton(R.string.booking_cancel,(d,w)->model.cancel(item.id,uid,rate))
                .setNegativeButton("Giữ đặt chỗ",null).show();
        }, item -> startActivity(new android.content.Intent(this,BookingSuccessActivity.class).putExtra("bookingId",item.id)));
        b.bookings.setLayoutManager(new LinearLayoutManager(this)); b.bookings.setAdapter(adapter);
        b.refresh.setOnClickListener(v -> model.watchMine(uid));
        b.bookingFilter.setSelection(model.filterPosition);
        b.bookingFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                model.filterPosition=position; render(adapter);
            }
        });
        model.bookings.observe(this, state -> {
            if (state == null) return;
            b.refresh.setEnabled(!state.loading && !model.busy());
            b.feedback.setText(state.loading ? getString(R.string.booking_loading) : state.error == null ? "" : state.error);
            if (state.error != null) adapter.setData(java.util.Collections.emptyList());
            if (state.data != null) {
                render(adapter);
            }
        });
        model.saved.observe(this, state -> {
            if (state == null) return;
            adapter.setBusy(state.loading); b.refresh.setEnabled(!state.loading);
            b.feedback.setText(state.loading ? getString(R.string.booking_sending) : state.error == null ? getString(R.string.booking_updated) : state.error);
            if (state.data != null) model.saved.setValue(null);
        });
        GuestNavigation.attach(this, R.id.nav_bookings);
        model.watchMine(uid);
    }
    private void render(BookingAdapter adapter) {
        vn.edu.ueh.phuclu.broomsharing.model.UiState<java.util.List<vn.edu.ueh.phuclu.broomsharing.model.Booking>> state=model.bookings.getValue();
        if(state==null||state.data==null)return;
        String[] statuses={"","PENDING","CONFIRMED","CANCELLED","COMPLETED","REJECTED","CHECKED_IN"};
        String wanted=statuses[Math.max(0, Math.min(model.filterPosition,statuses.length-1))];
        java.util.List<vn.edu.ueh.phuclu.broomsharing.model.Booking> rows=new java.util.ArrayList<>();
        for(vn.edu.ueh.phuclu.broomsharing.model.Booking item:state.data)if(wanted.isEmpty()||wanted.equals(item.status))rows.add(item);
        adapter.setData(rows);b.feedback.setText(rows.isEmpty()?getString(R.string.booking_empty):"");
    }
    @Override protected void onStart() { super.onStart(); if(model!=null&&uid!=null)model.watchMine(uid); }
    @Override protected void onStop() { if(model!=null)model.stopWatching();super.onStop(); }
}
