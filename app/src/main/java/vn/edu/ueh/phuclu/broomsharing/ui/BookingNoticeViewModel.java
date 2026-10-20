package vn.edu.ueh.phuclu.broomsharing.ui;

import android.os.*;
import androidx.lifecycle.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

/** Foreground reminders derived from persisted bookings; never drives booking/payment state. */
public class BookingNoticeViewModel extends ViewModel {
    private final BookingRepository repository=new BookingRepository();
    public final MutableLiveData<UiState<List<BookingNotice>>> notices=new MutableLiveData<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ListenerRegistration listener;
    private ListenerRegistration ownerListener;
    private boolean host;
    private List<Booking> ownerBookings=Collections.emptyList();
    private List<Booking> bookings=Collections.emptyList();
    private final Runnable tick=new Runnable(){public void run(){render();handler.postDelayed(this,30000);}};
    public void start(){
        start(false);
    }
    public void resume(){start(host);}
    public void start(boolean isHost){
        host=isHost;
        if(listener!=null)return;
        String uid=FirebaseAuth.getInstance().getUid();if(uid==null)return;
        notices.setValue(UiState.loading());
        listener=repository.watchMine(uid,new ResultCallback<List<Booking>>(){
            public void onSuccess(List<Booking> values){bookings=values;render();handler.removeCallbacks(tick);handler.postDelayed(tick,30000);}
            public void onError(Exception e){handler.removeCallbacks(tick);notices.setValue(UiState.error(e));}
        });
        if(host)ownerListener=repository.watchOwner(uid,new ResultCallback<List<Booking>>(){
            public void onSuccess(List<Booking> values){ownerBookings=values;render();}
            public void onError(Exception e){notices.setValue(UiState.error(e));}
        });
    }
    private void render(){
        long now=System.currentTimeMillis();List<BookingNotice> list=new ArrayList<>();
        for(Booking b:bookings){
            if(b.endAt==null||b.endAt.getTime()<now-7L*86400000)continue;
            list.add(new BookingNotice(b.id+":status:"+b.status,FormatUtils.bookingStatus(b.status),b.roomName+" · "+TimeUtils.formatVietnam(b.startAt),b.id));
            if(!CancellationPolicy.versioned(b)||b.startAt==null||b.startAt.getTime()<=now||!Arrays.asList("PENDING","CONFIRMED").contains(b.status))continue;
            int rate=CancellationPolicy.percent(b,now);long remaining=b.startAt.getTime()-now;
            long boundary=rate==100?CancellationPolicy.fullWindow(b.activeMode):CancellationPolicy.partialWindow(b.activeMode);
            boolean soon=rate>0&&remaining-boundary<=2L*3600000;
            String title=soon?"Sắp hết mốc hoàn "+rate+"%":"Mức hoàn hiện tại: "+rate+"%";
            String body=b.roomName+"\n"+(soon?"Mốc thay đổi: "+TimeUtils.formatVietnam(new Date(b.startAt.getTime()-boundary))+". ":"")
                +"Tính trên tiền trả trước thực nhận. Booking này chưa ghi nhận thanh toán. Bạn vẫn có thể hủy trước khi bắt đầu.";
            list.add(new BookingNotice(b.id+":policy:"+rate+(soon?":soon":""),title,body,b.id));
        }
        for(Booking b:ownerBookings){
            if(b.endAt==null||b.endAt.getTime()<now-7L*86400000)continue;
            BookingNotice n=new BookingNotice(b.id+":host:"+b.status,FormatUtils.bookingStatus(b.status),b.roomName+" · Khách: "+b.guestName+"\n"+TimeUtils.formatVietnam(b.startAt),b.id);
            n.forHost=true;list.add(n);
        }
        notices.setValue(UiState.success(list));
    }
    public void stop(){if(listener!=null)listener.remove();if(ownerListener!=null)ownerListener.remove();listener=null;ownerListener=null;handler.removeCallbacks(tick);}
    @Override protected void onCleared(){stop();}
}
