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
    private ListenerRegistration readListener,chatListener;
    private final NoticeRepository receipts=new NoticeRepository();
    private String sessionUid;
    private Set<String> readIds=Collections.emptySet();
    private boolean readsReady,bookingsReady,ownersReady;
    private int generation;
    private List<Conversation> conversations=Collections.emptyList();
    public final MutableLiveData<String> readError=new MutableLiveData<>();
    private boolean host;
    private List<Booking> ownerBookings=Collections.emptyList();
    private List<Booking> bookings=Collections.emptyList();
    private final Runnable tick=new Runnable(){public void run(){render();handler.postDelayed(this,30000);}};
    private final Runnable syncTimeout=()->{if(!readsReady||!bookingsReady||!ownersReady)notices.setValue(UiState.error(new Exception("Chưa nhận được xác nhận mới từ hệ thống. Kiểm tra mạng rồi thử đồng bộ lại.")));};
    public void start(){
        start(false);
    }
    public void resume(){start(host);}
    public void start(boolean isHost){
        host=isHost;
        if(listener!=null)return;
        String uid=FirebaseAuth.getInstance().getUid();if(uid==null)return;
        final int run=++generation;readsReady=bookingsReady=false;ownersReady=!host;
        if(!uid.equals(sessionUid)){bookings=Collections.emptyList();ownerBookings=Collections.emptyList();conversations=Collections.emptyList();readIds=Collections.emptySet();}sessionUid=uid;
        notices.setValue(UiState.loading());
        handler.removeCallbacks(syncTimeout);handler.postDelayed(syncTimeout,15000);
        readListener=receipts.watch(uid,new ResultCallback<Set<String>>(){public void onSuccess(Set<String> ids){if(run!=generation)return;readsReady=true;readIds=ids;render();}public void onError(Exception e){if(run==generation)notices.setValue(UiState.error(new Exception("Chưa đồng bộ được trạng thái đã đọc. Hãy thử đồng bộ lại.")));}});
        readError.setValue(null);
        try{chatListener=new ChatRepository().inbox(new ResultCallback<List<Conversation>>(){public void onSuccess(List<Conversation> rows){if(run==generation&&uid.equals(FirebaseAuth.getInstance().getUid())){conversations=rows;render();}}public void onError(Exception e){if(run==generation)readError.setValue("Chưa tải được cập nhật tin nhắn.");}});}catch(Exception e){if(run==generation)readError.setValue("Chưa tải được cập nhật tin nhắn.");}
        listener=repository.watchMine(uid,new ResultCallback<List<Booking>>(){
            public void onSuccess(List<Booking> values){if(run!=generation||!uid.equals(FirebaseAuth.getInstance().getUid()))return;bookingsReady=true;bookings=values;render();handler.removeCallbacks(tick);handler.postDelayed(tick,30000);}
            public void onError(Exception e){if(run!=generation)return;handler.removeCallbacks(tick);notices.setValue(UiState.error(e));}
        });
        if(host)ownerListener=repository.watchOwner(uid,new ResultCallback<List<Booking>>(){
            public void onSuccess(List<Booking> values){if(run==generation&&uid.equals(FirebaseAuth.getInstance().getUid())){ownersReady=true;ownerBookings=values;render();}}
            public void onError(Exception e){if(run==generation)notices.setValue(UiState.error(e));}
        });
    }
    private void render(){
        if(sessionUid==null||!sessionUid.equals(FirebaseAuth.getInstance().getUid())||!readsReady||!bookingsReady||!ownersReady)return;
        handler.removeCallbacks(syncTimeout);
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
        for(BookingNotice n:list)n.unread=!readIds.contains(n.id);
        for(Conversation c:conversations){
            if(c.sequence==0)continue;
            BookingNotice n=new BookingNotice("chat:"+c.id+":"+c.sequence,"Tin nhắn · "+c.peerName(sessionUid),c.lastText,c.bookingId);
            n.conversationId=c.id;n.unread=c.unread(sessionUid)>0;list.add(0,n);
        }
        notices.setValue(UiState.success(list));
    }
    public void markRead(BookingNotice n){if(!n.conversationId.isEmpty())return;receipts.read(sessionUid,n.id,new ResultCallback<String>(){public void onSuccess(String id){readError.setValue(null);}public void onError(Exception e){readError.setValue("Chưa lưu được trạng thái đã đọc. Hãy thử lại khi có mạng.");}});}
    public void retry(){stop();start(host);}
    public void stop(){generation++;if(listener!=null)listener.remove();if(ownerListener!=null)ownerListener.remove();if(readListener!=null)readListener.remove();if(chatListener!=null)chatListener.remove();listener=null;ownerListener=null;readListener=null;chatListener=null;handler.removeCallbacks(tick);handler.removeCallbacks(syncTimeout);}
    @Override protected void onCleared(){stop();}
}
