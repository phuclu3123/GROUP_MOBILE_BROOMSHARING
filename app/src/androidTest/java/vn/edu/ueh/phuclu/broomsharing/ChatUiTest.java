package vn.edu.ueh.phuclu.broomsharing;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.Conversation;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.LoginActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingNoticesActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ChatActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ConversationsActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.BookingNotifier;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostDashboardActivity;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/*
   Kiểm thử sendNavigateBadgesReadAndLocalBookingNotification.
*/
public class ChatUiTest {
    private final Instrumentation device=InstrumentationRegistry.getInstrumentation();
    private final Context context=device.getTargetContext();
    private final Properties credentials=new Properties();
    private <T extends Activity> void await(Class<T> type,Predicate<T> ready)throws Exception{
        AtomicBoolean done=new AtomicBoolean();
        for(int i=0;i<180&&!done.get();i++){device.runOnMainSync(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(type.isInstance(a)&&a.hasWindowFocus())done.set(ready.test(type.cast(a)));});if(!done.get())SystemClock.sleep(250);}
        assertTrue("Screen ready: "+type.getSimpleName(),done.get());
    }
    private <T extends Activity> void act(Class<T> type,Consumer<T> action){device.runOnMainSync(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(type.isInstance(a)){action.accept(type.cast(a));return;}throw new AssertionError("Missing activity "+type);});}
    private void open(Class<?> type,Intent extras){device.runOnMainSync(()->context.startActivity(extras.setClass(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)));}
    private void login(String alias)throws Exception{
        FirebaseAuth auth=FirebaseAuth.getInstance();auth.signOut();
        Tasks.await(auth.signInWithEmailAndPassword(credentials.getProperty(alias+".email"),credentials.getProperty(alias+".password")),45,TimeUnit.SECONDS);
    }
    private void capture(String name)throws Exception{SystemClock.sleep(400);Bitmap bitmap=device.getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    @Test public void sendNavigateBadgesReadAndLocalBookingNotification()throws Exception{
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runChatUiChecks")));
        try(InputStream in=new FileInputStream(new File(context.getExternalFilesDir(null),"demo-credentials.properties"))){credentials.load(in);}
        String host=credentials.getProperty("host1.uid"),guest=credentials.getProperty("guest1.uid"),id=guest+":"+host;
        try{
            login("guest1");open(ConversationsActivity.class,new Intent().putExtra("renterMode",true));
            await(ConversationsActivity.class,a->a.findViewById(R.id.conversationList)!=null&&((RecyclerView)a.findViewById(R.id.conversationList)).getAdapter().getItemCount()>0);capture("chat-guest-inbox");
            open(ChatActivity.class,new Intent().putExtra("conversationId",id));
            await(ChatActivity.class,a->a.findViewById(R.id.chatSend)!=null&&a.findViewById(R.id.chatSend).isEnabled());
            Espresso.onView(ViewMatchers.withId(R.id.chatInput)).perform(ViewActions.click(),ViewActions.typeText("[QA UI] Guest asks Host about room availability."));
            Espresso.onView(ViewMatchers.withId(R.id.chatSend)).check(ViewAssertions.matches(ViewMatchers.isCompletelyDisplayed()));
            capture("chat-keyboard");
            Espresso.closeSoftKeyboard();
            act(ChatActivity.class,a->a.findViewById(R.id.chatSend).performClick());
            await(ChatActivity.class,a->a.findViewById(R.id.chatSend).isEnabled()&&((TextView)a.findViewById(R.id.chatInput)).getText().length()==0);capture("chat-thread");
            act(ChatActivity.class,a->{((TextView)a.findViewById(R.id.chatInput)).setText("Draft retained");a.recreate();});
            await(ChatActivity.class,a->a.findViewById(R.id.chatInput)!=null&&"Draft retained".contentEquals(((TextView)a.findViewById(R.id.chatInput)).getText())&&a.findViewById(R.id.chatSend).isEnabled());
            open(LoginActivity.class,new Intent());login("host1");open(HostDashboardActivity.class,new Intent());
            await(HostDashboardActivity.class,a->{BottomNavigationView nav=a.findViewById(R.id.managementNavigation);return nav!=null&&nav.getBadge(R.id.host_messages)!=null&&nav.getBadge(R.id.host_messages).getNumber()>0;});capture("chat-host-badge");
            act(HostDashboardActivity.class,a->((BottomNavigationView)a.findViewById(R.id.managementNavigation)).setSelectedItemId(R.id.host_messages));
            await(ConversationsActivity.class,a->a.findViewById(R.id.conversationList)!=null);capture("chat-host-inbox");
            open(ChatActivity.class,new Intent().putExtra("conversationId",id));await(ChatActivity.class,a->a.findViewById(R.id.chatSend)!=null&&a.findViewById(R.id.chatSend).isEnabled());
            boolean read=false;for(int i=0;i<60&&!read;i++){Conversation c=Tasks.await(FirebaseHelper.getFirestore().collection("conversations").document(id).get(Source.SERVER),15,TimeUnit.SECONDS).toObject(Conversation.class);read=c.unread(host)==0;if(!read)SystemClock.sleep(250);}assertTrue("Host sees messages and acknowledges read",read);
            act(ChatActivity.class,a->{((TextView)a.findViewById(R.id.chatInput)).setText("[QA UI] Host reply from app.");a.findViewById(R.id.chatSend).performClick();});
            await(ChatActivity.class,a->a.findViewById(R.id.chatSend).isEnabled()&&((TextView)a.findViewById(R.id.chatInput)).getText().length()==0);
            open(LoginActivity.class,new Intent());login("guest1");open(BookingNoticesActivity.class,new Intent());
            await(BookingNoticesActivity.class,a->a.findViewById(R.id.noticeList)!=null&&((RecyclerView)a.findViewById(R.id.noticeList)).getAdapter().getItemCount()>0);
            if(Build.VERSION.SDK_INT>=33)device.getUiAutomation().grantRuntimePermission(context.getPackageName(),Manifest.permission.POST_NOTIFICATIONS);
            BookingNotifier.preferences(context,guest).edit().clear().apply();
            act(BookingNoticesActivity.class,a->a.findViewById(R.id.noticePermission).performClick());
            SystemClock.sleep(500);assertTrue(context.getSystemService(NotificationManager.class).getActiveNotifications().length>0);capture("booking-notices");
        }finally{
            context.getSystemService(NotificationManager.class).cancelAll();BookingNotifier.preferences(context,guest).edit().putBoolean("enabled",false).apply();
            FirebaseAuth.getInstance().signOut();open(LoginActivity.class,new Intent());
        }
    }
}
