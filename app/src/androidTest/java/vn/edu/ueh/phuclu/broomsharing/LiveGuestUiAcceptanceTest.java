package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.espresso.*;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

/** Opt-in actual Guest form and favorite actions against the isolated live fixtures. */
@RunWith(AndroidJUnit4.class)
public class LiveGuestUiAcceptanceTest {
    private final Bundle args=InstrumentationRegistry.getArguments();
    private Activity current(Class<? extends Activity> type) {
        AtomicReference<Activity> result=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(type.isInstance(a))result.set(a);
        });return result.get();
    }
    private Activity await(Class<? extends Activity> type,int id) {
        long deadline=SystemClock.elapsedRealtime()+120000;
        while(SystemClock.elapsedRealtime()<deadline){
            Activity activity=current(type);AtomicReference<Boolean> ready=new AtomicReference<>(false);
            if(activity!=null)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);View view=activity.findViewById(id);ready.set(view!=null&&view.isShown()&&view.isEnabled()&&activity.hasWindowFocus());});
            if(ready.get())return activity;SystemClock.sleep(250);
        }throw new AssertionError("Guest UI did not become ready: "+type.getSimpleName()+" / "+id);
    }
    private void screenshot(String name) throws Exception {
        android.graphics.Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        java.io.File folder=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null);
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(folder,name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}finally{image.recycle();}
    }
    private void favoriteExists(DocumentReference ref,boolean expected) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+60000;
        while(SystemClock.elapsedRealtime()<deadline){if(Tasks.await(ref.get(Source.SERVER),30,TimeUnit.SECONDS).exists()==expected)return;SystemClock.sleep(250);}
        fail("Favorite server state did not update");
    }
    @Test public void guestPicksDateQuotesSubmitsReadsConfirmationAndSavesFavorite() throws Exception {
        assumeTrue("Explicit live acceptance required","true".equals(args.getString("runLiveBooking")));
        for(String command:new String[]{"input keyevent 224","wm dismiss-keyguard"}){
            try(java.io.InputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))){byte[] buffer=new byte[1024];while(stream.read(buffer)!=-1){} }
        }
        FirebaseAuth auth=FirebaseAuth.getInstance();auth.signOut();
        try {
            Tasks.await(auth.signInWithEmailAndPassword(args.getString("hostEmail"),args.getString("hostPassword")),60,TimeUnit.SECONDS);
            java.util.Map<String,Object> listing=new java.util.HashMap<>();listing.put("roomId",args.getString("roomId"));listing.put("status","ACTIVE");
            Tasks.await(FirebaseConfig.functions().getHttpsCallable("setHostListing").call(listing),60,TimeUnit.SECONDS);
            auth.signOut();Tasks.await(auth.signInWithEmailAndPassword(args.getString("guestEmail"),args.getString("guestPassword")),60,TimeUnit.SECONDS);
            FirebaseFirestore db=FirebaseFirestore.getInstance(FirebaseConfig.FIRESTORE_DATABASE_ID);
            Intent form=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),BookingActivity.class).putExtra(BookingActivity.ROOM_ID,args.getString("roomId"));
            try(ActivityScenario<BookingActivity> scenario=ActivityScenario.launch(form)){
                await(BookingActivity.class,R.id.btnQuote);
                onView(withId(R.id.dateButton)).perform(scrollTo(),click());
                LocalDate day=Instant.ofEpochMilli(Long.parseLong(args.getString("startAt"))).atZone(HostSchedule.ZONE).toLocalDate();
                onView(isAssignableFrom(DatePicker.class)).perform(new ViewAction(){
                    public Matcher<View> getConstraints(){return isAssignableFrom(DatePicker.class);}
                    public String getDescription(){return "Select fixture day in DatePicker";}
                    public void perform(UiController controller,View view){((DatePicker)view).updateDate(day.getYear(),day.getMonthValue()-1,day.getDayOfMonth());controller.loopMainThreadUntilIdle();}
                });
                onView(withId(android.R.id.button1)).perform(click());
                onView(withId(R.id.btnQuote)).perform(scrollTo(),click());
                await(BookingActivity.class,R.id.btnSubmitBooking);
                screenshot("acceptance-guest-form");
                onView(withId(R.id.btnSubmitBooking)).perform(scrollTo(),click());
                Activity confirmation=await(BookingSuccessActivity.class,R.id.confirmationBody);
                AtomicReference<String> code=new AtomicReference<>();
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->code.set(((TextView)confirmation.findViewById(R.id.tvConfirmationCode)).getText().toString()));
                java.util.regex.Matcher match=java.util.regex.Pattern.compile("[a-f0-9]{64}").matcher(code.get());assertTrue(match.find());String id=match.group();
                DocumentSnapshot saved=Tasks.await(db.collection("bookings").document(id).get(Source.SERVER),60,TimeUnit.SECONDS);
                assertEquals("PENDING",saved.getString("status"));assertEquals("DEMO_PENDING",saved.getString("paymentStatus"));assertEquals(100000L,saved.getDouble("totalAmount").longValue());assertNotNull(saved.getString("roomAddress"));
                screenshot("acceptance-guest-confirmation");
                InstrumentationRegistry.getInstrumentation().runOnMainSync(confirmation::finish);
            }
            Intent detail=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),SpaceDetailActivity.class).putExtra(SpaceDetailActivity.ROOM_ID,args.getString("roomId"));
            DocumentReference favorite=db.collection("users").document(auth.getUid()).collection("favorites").document(args.getString("roomId"));
            try(ActivityScenario<SpaceDetailActivity> scenario=ActivityScenario.launch(detail)){
                await(SpaceDetailActivity.class,R.id.btnBook);
                await(SpaceDetailActivity.class,R.id.btnFavorite);
                onView(withId(R.id.btnFavorite)).perform(scrollTo(),click());favoriteExists(favorite,true);
            }
            try(ActivityScenario<FavoritesActivity> scenario=ActivityScenario.launch(FavoritesActivity.class)){
                await(FavoritesActivity.class,R.id.remove);screenshot("acceptance-guest-favorites");
                onView(withId(R.id.remove)).perform(click());favoriteExists(favorite,false);
            }
        }finally{auth.signOut();}
    }
}
