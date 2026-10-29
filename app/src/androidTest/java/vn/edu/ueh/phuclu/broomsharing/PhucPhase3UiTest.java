package vn.edu.ueh.phuclu.broomsharing;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import org.json.JSONObject;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.BookingNotice;
import vn.edu.ueh.phuclu.broomsharing.model.MapPoint;
import vn.edu.ueh.phuclu.broomsharing.model.MapRoute;
import vn.edu.ueh.phuclu.broomsharing.model.NearbyPlace;
import vn.edu.ueh.phuclu.broomsharing.model.ReviewItem;
import vn.edu.ueh.phuclu.broomsharing.model.UiState;
import vn.edu.ueh.phuclu.broomsharing.ui.admin.AdminDashboardActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.admin.AdminViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingNoticeViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.BookingNoticesActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.ManagementNavigation;
import vn.edu.ueh.phuclu.broomsharing.ui.map.MapActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.map.MapViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.map.NearbyPlacesActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.map.NearbyPlacesViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.report.ReportViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.report.RoomReportActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.room.SpaceDetailActivity;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/*
   Kiểm thử reportFormAdministratorDecisionAndNoticeReadState, nearbyRealDataRecreationAndMapResult, mapPickerReturnsToSameMap.
*/
public class PhucPhase3UiTest {
    private final Instrumentation device=InstrumentationRegistry.getInstrumentation();
    private final Context context=device.getTargetContext();private Properties p;
    private void setup()throws Exception{assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runPhase3Ui")));p=new Properties();try(var in=new FileInputStream(new File(context.getExternalFilesDir(null),"phase3-fixture.properties"))){p.load(in);}}
    private void login(String alias)throws Exception{FirebaseAuth.getInstance().signOut();Tasks.await(FirebaseAuth.getInstance().signInWithEmailAndPassword(p.getProperty(alias+".email"),p.getProperty(alias+".password")),40,TimeUnit.SECONDS);}
    private <T extends Activity>void waitFor(ActivityScenario<T> s,Predicate<T> condition,String label){AtomicBoolean ready=new AtomicBoolean();for(int i=0;i<240&&!ready.get();i++){final boolean retry=i==60||i==120;s.onActivity(a->{ready.set(condition.test(a));if(!ready.get()&&retry){View button=a.findViewById(R.id.retryButton);if(button!=null&&button.getVisibility()==View.VISIBLE&&button.isEnabled())button.performClick();}});if(!ready.get())SystemClock.sleep(250);}assertTrue(label,ready.get());}
    private void capture(String name)throws Exception{Bitmap b=null;for(int i=0;i<3&&b==null;i++){SystemClock.sleep(500);b=device.getUiAutomation().takeScreenshot();}assertNotNull(b);try(var out=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
    private RecyclerView list(View v){if(v instanceof RecyclerView)return (RecyclerView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){RecyclerView r=list(((ViewGroup)v).getChildAt(i));if(r!=null)return r;}return null;}
    @Test public void reportFormAdministratorDecisionAndNoticeReadState()throws Exception{
        setup();String room=p.getProperty("qa.roomId");AtomicReference<String> reportId=new AtomicReference<>();
        try{
            login("guest1");
            try(ActivityScenario<RoomReportActivity> s=ActivityScenario.launch(new Intent(context,RoomReportActivity.class).putExtra(SpaceDetailActivity.ROOM_ID,room))){
                waitFor(s,a->a.findViewById(R.id.reportSubmit)!=null,"Report form ready");
                onView(withId(R.id.reportSubmit)).perform(scrollTo(),click());onView(withText("Mô tả cần ít nhất 20 ký tự.")).check(matches(isDisplayed()));
                s.onActivity(a->{((EditText)a.findViewById(R.id.reportDescription)).setText("[QA Phase3 UI] Inspect the listing information and record a reason.");((Spinner)a.findViewById(R.id.reportReason)).setSelection(3);a.findViewById(R.id.reportSubmit).performClick();});
                waitFor(s,a->{UiState<String> r=new ViewModelProvider(a).get(ReportViewModel.class).result.getValue();if(r!=null&&r.data!=null)reportId.set(r.data);return reportId.get()!=null;},"Report acknowledged by cloud");capture("phase3-report-submitted");
            }
            JSONObject report=new JSONObject();report.put("reportId",reportId.get());try(var out=new FileWriter(new File(context.getExternalFilesDir(null),"phase3-ui-report.json"))){out.write(report.toString());}
            login("admin");
            try(ActivityScenario<AdminDashboardActivity> s=ActivityScenario.launch(new Intent(context,AdminDashboardActivity.class).putExtra(ManagementNavigation.SECTION,R.id.admin_reports))){
                AtomicInteger position=new AtomicInteger(-1);
                waitFor(s,a->{UiState<List<ReviewItem>> rows=new ViewModelProvider(a).get(AdminViewModel.class).items.getValue();if(rows!=null&&rows.data!=null)for(int i=0;i<rows.data.size();i++)if(rows.data.get(i).id.equals(reportId.get()))position.set(i);return position.get()>=0;},"Admin sees actual submitted report");
                s.onActivity(a->list(a.findViewById(R.id.workspaceRoot)).scrollToPosition(position.get()));SystemClock.sleep(750);
                onView(allOf(withId(R.id.approve),withParent(withParent(hasDescendant(withText(containsString("[QA Phase3 UI]"))))))).perform(click());
                onView(withText("Đóng báo cáo, ghi kết luận")).perform(click());onView(withText("Xác nhận")).perform(click());onView(isAssignableFrom(EditText.class)).check(matches(hasErrorText("Nhập từ 10 đến 1.000 ký tự.")));
                onView(isAssignableFrom(EditText.class)).perform(replaceText("[QA Phase3 UI] Reviewed and closed without sanction."));Espresso.closeSoftKeyboard();onView(withText("Xác nhận")).perform(click());
                waitFor(s,a->{UiState<List<ReviewItem>> rows=new ViewModelProvider(a).get(AdminViewModel.class).items.getValue();return rows!=null&&rows.data!=null&&rows.data.stream().noneMatch(r->r.id.equals(reportId.get()));},"Resolved report leaves open queue");capture("phase3-admin-reports");
            }
            login("guest1");
            try(ActivityScenario<BookingNoticesActivity> s=ActivityScenario.launch(BookingNoticesActivity.class)){
                AtomicReference<BookingNotice> chosen=new AtomicReference<>();
                waitFor(s,a->{UiState<List<BookingNotice>> n=new ViewModelProvider(a).get(BookingNoticeViewModel.class).notices.getValue();if(n!=null&&n.data!=null)for(BookingNotice item:n.data)if(item.conversationId.isEmpty()){chosen.set(item);return true;}return false;},"Notice inbox synced");
                s.onActivity(a->new ViewModelProvider(a).get(BookingNoticeViewModel.class).markRead(chosen.get()));
                waitFor(s,a->new ViewModelProvider(a).get(BookingNoticeViewModel.class).notices.getValue().data.stream().anyMatch(n->n.id.equals(chosen.get().id)&&!n.unread),"Cloud read receipt reflected in UI");s.recreate();
                waitFor(s,a->{BookingNoticeViewModel m=new ViewModelProvider(a).get(BookingNoticeViewModel.class);return a.findViewById(R.id.noticeList)!=null&&m.notices.getValue()!=null&&m.notices.getValue().data!=null&&m.notices.getValue().data.stream().anyMatch(n->n.id.equals(chosen.get().id)&&!n.unread);},"Read state survives recreation");capture("phase3-notice-read");
            }
        }finally{FirebaseAuth.getInstance().signOut();}
    }
    @Test public void nearbyRealDataRecreationAndMapResult()throws Exception{
        setup();login("guest1");MapPoint center=new MapPoint(10.73731656,106.65579043,"Q8");JSONObject report=new JSONObject();
        try(ActivityScenario<NearbyPlacesActivity> s=ActivityScenario.launchActivityForResult(NearbyPlacesActivity.intent(context,center).putExtra("originLat",center.latitude).putExtra("originLon",center.longitude))){
            waitFor(s,a->{UiState<List<NearbyPlace>> state=new ViewModelProvider(a).get(NearbyPlacesViewModel.class).places.getValue();return state!=null&&!state.loading;},"Live POI lookup finished");
            AtomicReference<NearbyPlace> first=new AtomicReference<>();AtomicInteger count=new AtomicInteger();
            s.onActivity(a->{NearbyPlacesViewModel m=new ViewModelProvider(a).get(NearbyPlacesViewModel.class);UiState<List<NearbyPlace>> state=m.places.getValue();assertNull("Actual provider must work",state.error);assertNotNull(state.data);assertFalse(state.data.isEmpty());assertTrue(state.data.size()<=10);count.set(state.data.size());first.set(state.data.get(0));assertFalse(m.fallbackOpened);});
            s.recreate();waitFor(s,a->a.findViewById(R.id.placesList)!=null&&((RecyclerView)a.findViewById(R.id.placesList)).getChildCount()>0,"Live list restored after recreation");capture("phase3-nearby");
            s.onActivity(a->((RecyclerView)a.findViewById(R.id.placesList)).getChildAt(0).performClick());onView(withId(android.R.id.button1)).perform(click());
            Instrumentation.ActivityResult result=s.getResult();assertEquals(Activity.RESULT_OK,result.getResultCode());Intent data=result.getResultData();assertEquals(first.get().point.latitude,data.getDoubleExtra("placeLat",0),0.0000001);assertEquals(center.longitude,data.getDoubleExtra("originLon",0),0.0000001);
            report.put("uiPassed",true);report.put("providerAvailable",true);report.put("count",count.get());report.put("mapResultPassed",true);report.put("verifiedAt",Instant.now().toString());
        }finally{FirebaseAuth.getInstance().signOut();}
        try(var out=new FileWriter(new File(context.getExternalFilesDir(null),"phase3-nearby-ui.json"))){out.write(report.toString(2));}
    }
    @Test public void mapPickerReturnsToSameMap()throws Exception{
        setup();login("guest1");MapPoint center=new MapPoint(10.73731656,106.65579043,"Q8");Instrumentation.ActivityMonitor monitor=device.addMonitor(NearbyPlacesActivity.class.getName(),null,false);AtomicReference<Double> routeDistance=new AtomicReference<>();
        try(ActivityScenario<MapActivity> s=ActivityScenario.launch(MapActivity.class)){
            waitFor(s,a->a.findViewById(R.id.mapCanvas)!=null&&a.findViewById(R.id.mapProgress).getVisibility()==View.GONE&&a.hasWindowFocus(),"Map rendered before opening picker");AtomicReference<View> original=new AtomicReference<>();
            s.onActivity(a->{original.set(a.findViewById(R.id.mapCanvas));new ViewModelProvider(a).get(MapViewModel.class).setOrigin(center);((Toolbar)a.findViewById(R.id.mapToolbar)).getMenu().performIdentifierAction(R.id.map_places,0);});
            NearbyPlacesActivity picker=(NearbyPlacesActivity)monitor.waitForActivityWithTimeout(20000);assertNotNull(picker);AtomicBoolean ready=new AtomicBoolean();AtomicReference<NearbyPlace> first=new AtomicReference<>();
            for(int i=0;i<240&&!ready.get();i++){device.runOnMainSync(()->{UiState<List<NearbyPlace>> state=new ViewModelProvider(picker).get(NearbyPlacesViewModel.class).places.getValue();RecyclerView list=picker.findViewById(R.id.placesList);if(state!=null&&state.data!=null&&!state.data.isEmpty()&&list!=null&&list.getChildCount()>0){first.set(state.data.get(0));ready.set(true);}});if(!ready.get())SystemClock.sleep(250);}assertTrue("Picker has actual POIs",ready.get());
            device.runOnMainSync(()->((RecyclerView)picker.findViewById(R.id.placesList)).getChildAt(0).performClick());onView(withId(android.R.id.button1)).perform(click());
            waitFor(s,a->{MapViewModel m=new ViewModelProvider(a).get(MapViewModel.class);return m.destination.getValue()!=null&&a.hasWindowFocus();},"Selection returns to existing map");
            s.onActivity(a->{MapViewModel m=new ViewModelProvider(a).get(MapViewModel.class);assertSame("No second native MapView",original.get(),a.findViewById(R.id.mapCanvas));assertEquals(center.longitude,m.origin.getValue().longitude,0.0000001);assertEquals(first.get().point.latitude,m.destination.getValue().latitude,0.0000001);});
            waitFor(s,a->{MapRoute r=new ViewModelProvider(a).get(MapViewModel.class).route.getValue();if(r!=null&&r.distanceMeters>0&&r.points.size()>2&&a.hasWindowFocus()){routeDistance.set(r.distanceMeters);return true;}return false;},"Actual road route to MapTiler POI");SystemClock.sleep(1000);capture("phase3-map-picker-return");
        }finally{device.removeMonitor(monitor);FirebaseAuth.getInstance().signOut();}
        JSONObject report=new JSONObject();report.put("passed",true);report.put("sameMapView",true);report.put("routeDistanceMeters",routeDistance.get());report.put("verifiedAt",Instant.now().toString());try(var out=new FileWriter(new File(context.getExternalFilesDir(null),"phase3-map-picker.json"))){out.write(report.toString(2));}
    }
    @Test public void noticeReadStateVisibleAfterRecreation()throws Exception{
        setup();login("guest1");
        try(ActivityScenario<BookingNoticesActivity> s=ActivityScenario.launch(BookingNoticesActivity.class)){
            AtomicReference<BookingNotice> chosen=new AtomicReference<>();
            waitFor(s,a->{UiState<List<BookingNotice>> state=new ViewModelProvider(a).get(BookingNoticeViewModel.class).notices.getValue();if(a.findViewById(R.id.noticeList)!=null&&state!=null&&state.data!=null)for(BookingNotice n:state.data)if(n.conversationId.isEmpty()&&n.unread){chosen.set(n);return true;}return false;},"Visible inbox contains unread booking update");
            s.onActivity(a->new ViewModelProvider(a).get(BookingNoticeViewModel.class).markRead(chosen.get()));
            waitFor(s,a->{UiState<List<BookingNotice>> state=new ViewModelProvider(a).get(BookingNoticeViewModel.class).notices.getValue();return state!=null&&state.data!=null&&state.data.stream().anyMatch(n->n.id.equals(chosen.get().id)&&!n.unread);},"Read state acknowledged by cloud");
            s.recreate();
            waitFor(s,a->{UiState<List<BookingNotice>> state=new ViewModelProvider(a).get(BookingNoticeViewModel.class).notices.getValue();RecyclerView list=a.findViewById(R.id.noticeList);return list!=null&&list.isShown()&&state!=null&&state.data!=null&&state.data.stream().anyMatch(n->n.id.equals(chosen.get().id)&&!n.unread);},"Actual inbox view restored, not just retained ViewModel");
            s.onActivity(a->{List<BookingNotice> rows=new ViewModelProvider(a).get(BookingNoticeViewModel.class).notices.getValue().data;for(int i=0;i<rows.size();i++)if(rows.get(i).id.equals(chosen.get().id))((RecyclerView)a.findViewById(R.id.noticeList)).scrollToPosition(i);});capture("phase3-notice-read");
        }finally{FirebaseAuth.getInstance().signOut();}
        JSONObject report=new JSONObject();report.put("passed",true);report.put("unreadToRead",true);report.put("visibleAfterRecreation",true);report.put("verifiedAt",Instant.now().toString());try(var out=new FileWriter(new File(context.getExternalFilesDir(null),"phase3-notice-ui.json"))){out.write(report.toString(2));}
    }

}
