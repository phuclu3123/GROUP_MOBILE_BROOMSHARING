package vn.edu.ueh.phuclu.broomsharing;

import android.content.Intent;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityBookingSuccessBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.BookingViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

/** Displays the persisted booking, never client-supplied prices or success extras. */
public class BookingSuccessActivity extends SessionActivity {
    @Override protected int pageLayout(){return R.layout.activity_booking_success;}
    @Override protected void onProfileReady(UserProfile profile){
        ActivityBookingSuccessBinding b=ActivityBookingSuccessBinding.bind(((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0));
        BookingViewModel model=new ViewModelProvider(this).get(BookingViewModel.class);
        String id=getIntent().getStringExtra("bookingId");
        b.confirmationBack.setOnClickListener(v->finish());
        b.btnGoBookings.setOnClickListener(v->{startActivity(new Intent(this,MyBookingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();});
        b.btnBackExplore.setOnClickListener(v->{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();});
        b.confirmationRetry.setOnClickListener(v->model.loadDetail(id));
        model.detail.observe(this,state->{
            if(state==null)return;
            b.confirmationRetry.setVisibility(state.error==null?View.GONE:View.VISIBLE);
            b.tvConfirmationTitle.setText(state.loading?"Đang kiểm tra đặt chỗ…":state.error==null?"Đặt chỗ của bạn":state.error);
            b.confirmationBody.setVisibility(state.data==null?View.GONE:View.VISIBLE);
            if(state.data==null)return;
            Booking item=state.data;
            b.tvConfirmationCode.setText(getString(R.string.booking_code_label,item.id));
            b.tvConfirmationRoom.setText(item.roomName);
            b.tvConfirmationAddress.setText(item.roomAddress==null?"Địa chỉ chưa được lưu trong booking cũ.":item.roomAddress);
            b.tvConfirmationRange.setText(TimeUtils.formatVietnam(item.startAt)+"\nđến "+TimeUtils.formatVietnam(item.endAt));
            b.tvConfirmationMode.setText(FormatUtils.bookingStatus(item.status)+" • "+(Room.MODE_HOURLY.equals(item.activeMode)?"Thuê theo giờ":"Thuê tháng/quý"));
            b.tvConfirmationTotal.setText(FormatUtils.formatCurrency(item.totalAmount)+" (thuê + phí)");
            Object deposit=item.priceSnapshot==null?null:item.priceSnapshot.get("depositAmount");
            b.tvConfirmationDeposit.setText((deposit instanceof Number?FormatUtils.formatCurrency(((Number)deposit).doubleValue()):"Chưa có snapshot cọc")+"\n"+FormatUtils.paymentStatus(item.paymentStatus));
            b.tvConfirmationNote.setText("PENDING".equals(item.status)?"Đang giữ chỗ, chờ Host xác nhận. Cọc và thanh toán vẫn là demo.":"Trạng thái lấy từ booking trên server. Không có QR/PIN xác thực ra vào hoặc giao dịch tiền thật.");
        });
        model.loadDetail(id);
    }
}
