package vn.edu.ueh.phuclu.broomsharing.ui.common;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import java.util.*;
import vn.edu.ueh.phuclu.broomsharing.*;
import vn.edu.ueh.phuclu.broomsharing.model.BookingNotice;

/** Device-local, opt-in notifications; no FCM and no background delivery promise. */
public final class BookingNotifier {
    private static final String CHANNEL="booking_updates";
    private BookingNotifier(){}
    public static android.content.SharedPreferences preferences(Context c,String uid){return c.getSharedPreferences("booking_notices_"+uid,Context.MODE_PRIVATE);}
    public static void deliver(Context c,String uid,List<BookingNotice> notices){
        android.content.SharedPreferences prefs=preferences(c,uid);
        if(!prefs.getBoolean("enabled",false))return;
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        if(!NotificationManagerCompat.from(c).areNotificationsEnabled())return;
        int count=0;BookingNotice newest=null;
        for(BookingNotice n:notices)if(!prefs.getBoolean("delivered:"+n.id,false)){count++;if(newest==null)newest=n;}
        if(count==0)return;
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Cập nhật đặt chỗ",NotificationManager.IMPORTANCE_DEFAULT));
        Intent target=new Intent(c,BookingNoticesActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(c,uid.hashCode(),target,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder=new NotificationCompat.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_booking)
            .setContentTitle(count==1?newest.title:count+" cập nhật đặt chỗ")
            .setContentText("Mở ứng dụng để xem trạng thái và chính sách hiện tại.")
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        NotificationManagerCompat.from(c).notify(uid.hashCode(),builder.build());
        android.content.SharedPreferences.Editor edit=prefs.edit();for(BookingNotice n:notices)edit.putBoolean("delivered:"+n.id,true);edit.apply();
    }
}
