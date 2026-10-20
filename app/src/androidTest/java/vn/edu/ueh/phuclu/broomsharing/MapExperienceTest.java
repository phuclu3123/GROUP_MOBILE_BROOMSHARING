package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.action.ViewActions.click;
import static org.hamcrest.Matchers.startsWith;
import android.content.Intent;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import org.junit.Test;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.geometry.LatLng;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.MapViewModel;
import vn.edu.ueh.phuclu.broomsharing.utils.*;

/** Opt-in cloud/provider/UI test. GPS uses emulator's LocationManager at a real Q8 street point. */
public class MapExperienceTest {
    private final android.app.Instrumentation device=InstrumentationRegistry.getInstrumentation();
    private final android.content.Context context=device.getTargetContext();
    private final MapPoint bui=new MapPoint(10.737316559278344,106.65579043328762,"Bùi Minh Trực");
    private void waitFor(ActivityScenario<MapActivity> screen,Predicate<MapActivity> condition,String label){
        AtomicBoolean done=new AtomicBoolean();for(int i=0;i<240&&!done.get();i++){screen.onActivity(a->done.set(condition.test(a)));if(!done.get())SystemClock.sleep(250);}
        if(!done.get())screen.onActivity(a->{MapViewModel m=model(a);System.out.println("MAP_DIAGNOSTIC phase="+label+" hasMore="+m.hasMore()+" roomBusy="+m.roomBusy.getValue()+" roomMessage="+m.roomMessage.getValue()+" message="+m.message.getValue()+" progress="+(a.findViewById(R.id.mapProgress)==null?"no-map":a.findViewById(R.id.mapProgress).getVisibility()));});
        assertTrue(label,done.get());
    }
    private MapViewModel model(MapActivity a){return new ViewModelProvider(a).get(MapViewModel.class);}
    private void capture(String name)throws Exception{Bitmap b=null;for(int i=0;i<3&&b==null;i++){SystemClock.sleep(750);b=device.getUiAutomation().takeScreenshot();}assertNotNull("Emulator screenshot available: "+name,b);try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
    private void tap(ActivityScenario<MapActivity> screen,MapPoint target){
        AtomicReference<PointF> pixel=new AtomicReference<>();int[] offset=new int[2];
        screen.onActivity(a->{MapView view=a.findViewById(R.id.mapCanvas);view.getLocationOnScreen(offset);view.getMapAsync(map->pixel.set(map.getProjection().toScreenLocation(new LatLng(target.latitude,target.longitude))));});
        assertNotNull(pixel.get());long now=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,offset[0]+pixel.get().x,offset[1]+pixel.get().y,0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);device.getUiAutomation().injectInputEvent(e,true);e.recycle();}
    }
    private int renderedCount(MapActivity a,String layer){
        AtomicInteger count=new AtomicInteger();MapView view=a.findViewById(R.id.mapCanvas);if(view==null)return 0;
        view.getMapAsync(map->count.set(map.queryRenderedFeatures(new RectF(0,0,view.getWidth(),view.getHeight()),layer).size()));return count.get();
    }
    @Test public void q8SearchAllNearbyMarkersRoutePickPointAndGps()throws Exception{
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runMapExperience")));
        Properties credentials=new Properties();try(InputStream in=new FileInputStream(new File(context.getExternalFilesDir(null),"demo-credentials.properties"))){credentials.load(in);}
        FirebaseAuth auth=FirebaseAuth.getInstance();Tasks.await(auth.signInWithEmailAndPassword(credentials.getProperty("guest1.email"),credentials.getProperty("guest1.password")),45,TimeUnit.SECONDS);
        device.getUiAutomation().grantRuntimePermission(context.getPackageName(),android.Manifest.permission.ACCESS_FINE_LOCATION);
        device.getUiAutomation().grantRuntimePermission(context.getPackageName(),android.Manifest.permission.ACCESS_COARSE_LOCATION);
        List<String> checks=new ArrayList<>();
        try(ActivityScenario<MapActivity> screen=ActivityScenario.launch(new Intent(context,MapActivity.class))){
            waitFor(screen,a->a.findViewById(R.id.mapSearch)!=null&&a.findViewById(R.id.mapProgress).getVisibility()==View.GONE&&!model(a).hasMore(),"Map style and automatic room pagination");
            screen.onActivity(a->{((TextView)a.findViewById(R.id.mapAddress)).setText("Bùi Minh Trực, Quận 8, Hồ Chí Minh");a.findViewById(R.id.mapSearch).performClick();});
            waitFor(screen,a->!Boolean.TRUE.equals(model(a).busy.getValue()),"Geocoding completes");
            onView(withText(startsWith("Bùi Minh Trực"))).inRoot(isDialog()).perform(click());
            waitFor(screen,a->MapArea.distance(model(a).origin.getValue(),bui)<100&&model(a).referenceNearby.getValue().size()>=5,"Q8 origin and source addresses loaded");
            AtomicInteger expected=new AtomicInteger();AtomicReference<RentalPlace> target=new AtomicReference<>();
            screen.onActivity(a->{List<RentalPlace> refs=model(a).referenceNearby.getValue();expected.set(refs.size());for(RentalPlace p:refs){assertTrue(MapArea.nearby(model(a).origin.getValue(),model(a).point(p)));assertFalse(p.id.contains("3086"));if(p.id.endsWith("ta_quang_buu_849"))target.set(p);}assertNotNull(target.get());});
            waitFor(screen,a->renderedCount(a,"pilot-reference-dots")==expected.get(),"Every nearby reference rendered on native map");capture("map-q8-nearby");
            AtomicInteger mapHeight=new AtomicInteger();screen.onActivity(a->mapHeight.set(a.findViewById(R.id.mapCanvas).getHeight()));
            checks.add("MapTiler geocodes Bui Minh Truc Q8; all nearby source addresses inside 3000m are rendered; distant 3086 Pham The Hien excluded");
            tap(screen,new MapPoint(target.get().location.getLatitude(),target.get().location.getLongitude(),target.get().name));
            waitFor(screen,a->model(a).selectedReference()!=null&&model(a).route.getValue()!=null&&renderedCount(a,"pilot-route-line")>0,"Marker tap draws actual ORS road route inside app");
            waitFor(screen,a->renderedCount(a,"pilot-reference-dots")==expected.get(),"Routing keeps all nearby markers");capture("map-q8-road-route");
            screen.onActivity(a->assertEquals("Destination card must not resize native map surface",mapHeight.get(),a.findViewById(R.id.mapCanvas).getHeight()));
            checks.add("Native marker tap chooses actual source location, road route rendered, all nearby markers retained");
            screen.onActivity(a->{MapView view=a.findViewById(R.id.mapCanvas);view.getMapAsync(map->map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new LatLng(bui.latitude,bui.longitude),14)));model(a).route.setValue(model(a).route.getValue());});
            SystemClock.sleep(400);screen.onActivity(a->((MapView)a.findViewById(R.id.mapCanvas)).getMapAsync(map->assertEquals(14,map.getCameraPosition().zoom,0.01)));
            checks.add("Repeated render preserves user camera instead of refitting route on every update");
            screen.onActivity(a->a.findViewById(R.id.mapList).performClick());SystemClock.sleep(500);capture("map-q8-list");pressBack();
            // Restore a broad view so a second search center is visibly tappable.
            screen.onActivity(a->model(a).setOrigin(bui));SystemClock.sleep(1000);
            MapPoint chosen=new MapPoint(10.7415,106.668,"Điểm chọn trên bản đồ");
            screen.onActivity(a->((MapView)a.findViewById(R.id.mapCanvas)).getMapAsync(map->{map.cancelTransitions();map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new LatLng(chosen.latitude,chosen.longitude),15));}));
            SystemClock.sleep(1000);
            screen.onActivity(a->a.findViewById(R.id.mapPick).performClick());tap(screen,chosen);
            waitFor(screen,a->MapArea.distance(model(a).origin.getValue(),chosen)<20&&model(a).destination.getValue()==null,"Map selection changes search origin and clears prior route");
            checks.add("Choose-on-map changes search center, recalculates 3km results and clears stale route");
            screen.onActivity(a->a.findViewById(R.id.mapGps).performClick());
            waitFor(screen,a->!Boolean.TRUE.equals(model(a).busy.getValue())&&MapArea.distance(model(a).origin.getValue(),bui)<50,"LocationManager uses emulator GPS Q8");capture("map-q8-gps");
            checks.add("Android LocationManager GPS returns Q8 position and re-filters nearby places");
            screen.recreate();waitFor(screen,a->a.findViewById(R.id.mapCanvas)!=null&&MapArea.distance(model(a).origin.getValue(),bui)<50&&renderedCount(a,"pilot-reference-dots")==expected.get(),"Map recreation retains search position and markers");
            screen.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);screen.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            waitFor(screen,a->renderedCount(a,"pilot-reference-dots")==expected.get(),"Map stop/resume remains responsive");
            checks.add("Destination card keeps native map size; recreate and stop/resume preserve position and all markers");
            org.json.JSONObject report=new org.json.JSONObject();report.put("passed",true);report.put("verifiedAt",java.time.Instant.now().toString());report.put("nearbyReferences",expected.get());report.put("checks",new org.json.JSONArray(checks));
            try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(context.getExternalFilesDir(null),"map-experience-verification.json")),java.nio.charset.StandardCharsets.UTF_8)){out.write(report.toString(2));}
        }finally{auth.signOut();}
    }
}
