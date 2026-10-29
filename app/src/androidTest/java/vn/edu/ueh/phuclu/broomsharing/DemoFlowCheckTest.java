package vn.edu.ueh.phuclu.broomsharing;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.Test;
import org.maplibre.android.maps.MapView;
import vn.edu.ueh.phuclu.broomsharing.model.MapPoint;
import vn.edu.ueh.phuclu.broomsharing.model.UserProfile;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.repository.AuthError;
import vn.edu.ueh.phuclu.broomsharing.repository.AuthRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.FirebaseAuthRepository;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.LoginActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostDashboardActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.map.MapActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.map.MapViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.room.SpaceDetailActivity;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Opt-in, read-only cloud checks using private credentials uploaded separately, never in APK. */
public class DemoFlowCheckTest {
    private final Instrumentation device=InstrumentationRegistry.getInstrumentation();
    private final Context context=device.getTargetContext();
    private final Properties credentials=new Properties();
    private final FirebaseAuthRepository auth=new FirebaseAuthRepository(context);

    private UserProfile login(String alias) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);UserProfile[] profile=new UserProfile[1];AuthError[] failure=new AuthError[1];
        device.runOnMainSync(() -> auth.login("demo_"+alias,credentials.getProperty(alias+".password"),new AuthRepository.Callback(){
            public void onSuccess(UserProfile value){profile[0]=value;latch.countDown();}
            public void onError(AuthError error){failure[0]=error;latch.countDown();}
        }));
        assertTrue("Demo login callback timed out",latch.await(45,TimeUnit.SECONDS));
        assertNull("Demo login failed for "+alias,failure[0]);
        assertEquals("Demo UID mismatch",credentials.getProperty(alias+".uid"),profile[0].uid);
        return profile[0];
    }
    private <T extends Activity> void waitFor(ActivityScenario<T> screen,Predicate<T> ready,String reason)throws Exception{
        AtomicBoolean done=new AtomicBoolean();
        for(int i=0;i<180&&!done.get();i++){screen.onActivity(activity->done.set(ready.test(activity)));if(!done.get())SystemClock.sleep(250);}
        assertTrue(reason,done.get());
    }
    private void screenshot(String name)throws Exception{
        Bitmap bitmap=device.getUiAutomation().takeScreenshot();assertNotNull("Screenshot failed",bitmap);
        try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    @Test public void actualDemoLoginDetailsAndInAppRoadRoute()throws Exception{
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runDemoUiChecks")));
        assertTrue("This test requires the default cloud build",BuildConfig.FIRESTORE_EMULATOR_HOST.isEmpty());
        try(InputStream in=new FileInputStream(new File(context.getExternalFilesDir(null),"demo-credentials.properties"))){credentials.load(in);}
        auth.logout();
        try {
            try(ActivityScenario<LoginActivity> form=ActivityScenario.launch(LoginActivity.class)){
                form.onActivity(activity->{
                    ((TextView)activity.findViewById(R.id.identifierInput)).setText(credentials.getProperty("host1.email"));
                    ((TextView)activity.findViewById(R.id.passwordInput)).setText(credentials.getProperty("host1.password"));
                    activity.findViewById(R.id.submitButton).performClick();
                });
                AtomicBoolean opened=new AtomicBoolean();
                for(int i=0;i<180&&!opened.get();i++){
                    device.runOnMainSync(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof HostDashboardActivity)opened.set(true);});
                    if(!opened.get())SystemClock.sleep(250);
                }
                // Clear password before any failure can produce a diagnostic hierarchy.
                if(!opened.get())form.onActivity(activity->((TextView)activity.findViewById(R.id.passwordInput)).setText(""));
                assertTrue("Host login form failed to open the signed-in home",opened.get());
            }
            assertEquals(UserRole.ADMIN,login("admin").getUserRole());
            assertEquals(UserRole.GUEST,login("guest1").getUserRole());
            try(ActivityScenario<SpaceDetailActivity> detail=ActivityScenario.launch(new Intent(context,SpaceDetailActivity.class).putExtra(SpaceDetailActivity.ROOM_ID,"dataset_hcm_v1_room_02"))){
                waitFor(detail,a->a.findViewById(R.id.btnBook).isEnabled(),"Cloud room detail failed to load");
                detail.onActivity(activity->{
                    Rect button=new Rect();assertTrue("Booking action must stay visible",activity.findViewById(R.id.btnBook).getGlobalVisibleRect(button));
                    assertTrue(((TextView)activity.findViewById(R.id.price)).getText().length()>0);
                    assertEquals(View.VISIBLE,activity.findViewById(R.id.detailToolbar).getVisibility());
                });
                screenshot("check-detail-top.png");
                detail.onActivity(activity->((ScrollView)activity.findViewById(R.id.detailScroll)).fullScroll(View.FOCUS_DOWN));
                SystemClock.sleep(400);screenshot("check-detail-bottom.png");
            }
            try(ActivityScenario<MapActivity> map=ActivityScenario.launch(new Intent(context,MapActivity.class).putExtra(SpaceDetailActivity.ROOM_ID,"dataset_hcm_v1_room_02"))){
                waitFor(map,a->{MapViewModel m=new ViewModelProvider(a).get(MapViewModel.class);return m.message.getValue()!=null&&m.message.getValue().contains("trùng")&&a.findViewById(R.id.mapProgress).getVisibility()==View.GONE;},"Same-point directions must stay in the app");
                SystemClock.sleep(1500);screenshot("check-map-same-point.png");
                map.onActivity(activity->{MapViewModel m=new ViewModelProvider(activity).get(MapViewModel.class);assertNull(m.googleUrl.getValue());m.select(new MapPoint(10.7725,106.698,"Đích thử TP.HCM"),null);m.directions();});
                waitFor(map,a->new ViewModelProvider(a).get(MapViewModel.class).route.getValue()!=null,"Real Directions API failed to draw route in app");
                AtomicBoolean drawn=new AtomicBoolean();
                for(int attempt=0;attempt<160&&!drawn.get();attempt++){
                    map.onActivity(activity->{
                        MapView canvas=activity.findViewById(R.id.mapCanvas);
                        canvas.getMapAsync(nativeMap->drawn.set(!nativeMap.queryRenderedFeatures(new RectF(0,0,canvas.getWidth(),canvas.getHeight()),"pilot-route-line").isEmpty()));
                    });
                    if(!drawn.get())SystemClock.sleep(250);
                }
                assertTrue("Directions returned but MapLibre did not render route geometry",drawn.get());
                Rect mapBounds = new Rect();
                map.onActivity(activity -> activity.findViewById(R.id.mapCanvas).getGlobalVisibleRect(mapBounds));
                // Native geometry queries can precede presentation of the GL frame to Android.
                boolean visibleRoute = false;
                for (int attempt = 0; attempt < 40 && !visibleRoute; attempt++) {
                    Bitmap frame = device.getUiAutomation().takeScreenshot();
                    int blue = 0;
                    for (int y = Math.max(0, mapBounds.top); y < Math.min(frame.getHeight(), mapBounds.bottom); y += 2)
                        for (int x = Math.max(0, mapBounds.left); x < Math.min(frame.getWidth(), mapBounds.right); x += 2) {
                            int color = frame.getPixel(x, y);
                            if (Color.red(color) < 40 && Color.green(color) >= 70
                                    && Color.green(color) <= 150 && Color.blue(color) > 170) blue++;
                        }
                    frame.recycle();
                    visibleRoute = blue > 500;
                    if (!visibleRoute) SystemClock.sleep(500);
                }
                screenshot("check-map-road-route.png");
                assertTrue("Route geometry exists but the blue road line is not visible in the presented frame", visibleRoute);
            }
        }finally{auth.logout();}
    }
}
