package vn.edu.ueh.phuclu.broomsharing.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.*;
import org.json.JSONException;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.MapPoint;
import vn.edu.ueh.phuclu.broomsharing.model.NearbyPlace;
import vn.edu.ueh.phuclu.broomsharing.utils.FirebaseHelper;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Explicit cloud/provider acceptance. Destructive moderation is confined to the private QA fixture. */
public class PhucPhase3AcceptanceTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final FirebaseAuth auth=FirebaseAuth.getInstance();
    private final FirebaseFirestore db=FirebaseHelper.getFirestore();
    private Properties p;
    private void enabled()throws Exception{assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runPhase3")));p=new Properties();try(var in=new FileInputStream(new File(context.getExternalFilesDir(null),"phase3-fixture.properties"))){p.load(in);}}
    private void login(String alias)throws Exception{auth.signOut();Tasks.await(auth.signInWithEmailAndPassword(p.getProperty(alias+".email"),p.getProperty(alias+".password")),40,TimeUnit.SECONDS);}
    private <T>T result(Consumer<ResultCallback<T>> operation)throws Exception{CompletableFuture<T> f=new CompletableFuture<>();operation.accept(new ResultCallback<T>(){public void onSuccess(T value){f.complete(value);}public void onError(Exception e){f.completeExceptionally(e);}});return f.get(45,TimeUnit.SECONDS);}
    private void denied(Task<?> task)throws Exception{try{Tasks.await(task,20,TimeUnit.SECONDS);fail("Forbidden operation succeeded");}catch(ExecutionException e){assertTrue(e.getCause() instanceof FirebaseFirestoreException);assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED,((FirebaseFirestoreException)e.getCause()).getCode());}}
    private void shell(String command)throws Exception{try(var fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);var in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] bytes=new byte[256];while(in.read(bytes)!=-1){}}}
    private void network(boolean enabled)throws Exception{
        shell("svc wifi "+(enabled?"enable":"disable"));shell("svc data "+(enabled?"enable":"disable"));
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        for(int i=0;i<80;i++){if((manager.getActiveNetwork()!=null)==enabled)return;SystemClock.sleep(250);}fail("Emulator network did not change");
    }
    private DocumentSnapshot server(String path)throws Exception{
        for(int i=0;;i++)try{return Tasks.await(db.document(path).get(Source.SERVER),20,TimeUnit.SECONDS);}catch(ExecutionException e){if(i>=10||!(e.getCause() instanceof FirebaseFirestoreException)||((FirebaseFirestoreException)e.getCause()).getCode()!=FirebaseFirestoreException.Code.UNAVAILABLE)throw e;SystemClock.sleep(1000);}
    }
    @Test public void reportsModerationReadReceiptsAndOfflineAcknowledgement()throws Exception{
        enabled();List<String> checks=new ArrayList<>();ReportRepository reports=new ReportRepository();String room=p.getProperty("qa.roomId"),owner=p.getProperty("qa.hostUid"),id=p.getProperty("qa.report.close");
        try{
            login("guest1");String guest=auth.getUid();
            assertEquals(id,this.<String>result(cb->reports.submit(id,room,"Sai thông tin","[QA Phase3] Verify report contract and administrator review.",cb)));
            assertEquals(id,this.<String>result(cb->reports.submit(id,room,"Sai thông tin","[QA Phase3] Retry must not duplicate a report.",cb)));
            DocumentSnapshot created=Tasks.await(db.collection("reports").document(id).get(Source.SERVER),20,TimeUnit.SECONDS);assertEquals(guest,created.getString("reporterUid"));assertEquals(owner,created.getString("ownerUid"));assertNotNull(created.getDate("createdAt"));
            denied(db.collection("reports").document(id).update("status","RESOLVED"));
            Map<String,Object> forged=new HashMap<>(created.getData());forged.put("id",id+"_forged");forged.put("ownerUid",p.getProperty("host1.uid"));forged.put("createdAt",FieldValue.serverTimestamp());forged.put("updatedAt",FieldValue.serverTimestamp());denied(db.collection("reports").document(id+"_forged").set(forged));
            checks.add("Guest submits real server-timestamped report idempotently; forged owner and self-resolution denied");
            String receipt="qa_phase3:"+id;DocumentReference mine=db.collection("users").document(guest).collection("noticeReads").document(receipt);
            Tasks.await(mine.set(Collections.singletonMap("readAt",FieldValue.serverTimestamp())),20,TimeUnit.SECONDS);assertNotNull(Tasks.await(mine.get(Source.SERVER),20,TimeUnit.SECONDS).getTimestamp("readAt"));
            denied(mine.update("forged",true));denied(db.collection("users").document(owner).collection("noticeReads").document(receipt).set(Collections.singletonMap("readAt",FieldValue.serverTimestamp())));
            login("guest2");denied(db.collection("reports").document(id).get(Source.SERVER));denied(mine.get(Source.SERVER));
            checks.add("Read state persists on Firestore; another account cannot read report/receipt or write peer receipt");
            login("admin");this.<String>result(cb->reports.resolve(id,"RESOLVED","NO_ACTION","[QA Phase3] Report checked without a moderation sanction.",cb));
            DocumentSnapshot resolved=Tasks.await(db.collection("reports").document(id).get(Source.SERVER),20,TimeUnit.SECONDS);assertEquals("RESOLVED",resolved.getString("status"));assertNotNull(resolved.getTimestamp("reviewedAt"));assertEquals(auth.getUid(),resolved.getString("reviewedBy"));
            assertFalse(Tasks.await(db.collection("auditLogs").whereEqualTo("targetPath","reports/"+id).get(Source.SERVER),20,TimeUnit.SECONDS).isEmpty());
            try{this.<String>result(cb->reports.resolve(id,"RESOLVED","NO_ACTION","[QA Phase3] Duplicate resolution must fail.",cb));fail("Report processed twice");}catch(ExecutionException expected){}
            checks.add("Admin conclusion requires reason; server reviewer/time and audit are saved atomically; repeat resolution rejected");
            login("guest1");network(false);
            try{this.<String>result(cb->reports.submit(p.getProperty("qa.report.offline"),room,"Khác","[QA Phase3] Must never claim success while offline.",cb));fail("Offline transaction reported success");}catch(ExecutionException expected){}finally{network(true);}
            assertFalse(server("reports/"+p.getProperty("qa.report.offline")).exists());
            checks.add("Offline report transaction fails and creates no document; UI cannot show an acknowledged success");
            String suspend=p.getProperty("qa.report.suspend");this.<String>result(cb->reports.submit(suspend,room,"Ảnh không khớp","[QA Phase3] Moderation suspend test for disposable room only.",cb));
            login("admin");this.<String>result(cb->reports.resolve(suspend,"RESOLVED","SUSPEND_ROOM","[QA Phase3] Suspend disposable room after review.",cb));assertEquals("SUSPENDED",Tasks.await(db.collection("rooms").document(room).get(Source.SERVER),20,TimeUnit.SECONDS).getString("listingStatus"));
            login("guest1");assertEquals(suspend,this.<String>result(cb->reports.submit(suspend,room,"Ảnh không khớp","[QA Phase3] Retry after room becomes private must return the existing report.",cb)));login("admin");
            Tasks.await(db.collection("rooms").document(room).update("listingStatus","ACTIVE","updatedAt",FieldValue.serverTimestamp()),20,TimeUnit.SECONDS);
            login("guest1");String block=p.getProperty("qa.report.block");this.<String>result(cb->reports.submit(block,room,"Nghi ngờ gian lận","[QA Phase3] Moderation block test for disposable Host only.",cb));
            login("admin");this.<String>result(cb->reports.resolve(block,"RESOLVED","BLOCK_HOST","[QA Phase3] Block disposable Host after review.",cb));assertEquals("BLOCKED",Tasks.await(db.collection("users").document(owner).get(Source.SERVER),20,TimeUnit.SECONDS).getString("status"));assertEquals("BLOCKED",Tasks.await(db.collection("publicHosts").document(owner).get(Source.SERVER),20,TimeUnit.SECONDS).getString("status"));
            checks.add("Moderation suspends only QA room or blocks only QA Host with synchronized public status; original owners/bookings untouched");
            JSONObject report=new JSONObject();report.put("passed",true);report.put("verifiedAt",Instant.now().toString());report.put("checks",new JSONArray(checks));
            try(var out=new FileWriter(new File(context.getExternalFilesDir(null),"phase3-cloud-verification.json"))){out.write(report.toString(2));}
        }finally{network(true);auth.signOut();}
    }
    @Test public void nearbyParsingBoundsAndActualQ8Provider()throws Exception{
        enabled();MapPoint center=new MapPoint(10.73731656,106.65579043,"Q8");
        JSONObject fixture=new JSONObject("{\"features\":[{\"text\":\"Nearby\",\"properties\":{\"ref\":\"osm:n1\",\"feature_tags\":{\"amenity\":\"cafe\"}},\"geometry\":{\"type\":\"Point\",\"coordinates\":[106.65579,10.73732]}},{\"properties\":{\"ref\":\"osm:n2\",\"feature_tags\":{\"amenity\":\"cafe\"}},\"geometry\":{\"type\":\"Point\",\"coordinates\":[106.70,10.80]}},{\"properties\":{\"ref\":\"osm:w3\",\"feature_tags\":{\"amenity\":\"parking\"}},\"geometry\":{\"type\":\"Point\",\"coordinates\":[106.656,10.738]}}]}");
        List<NearbyPlace> parsed=NearbyPlacesRepository.parse(fixture,center);assertEquals(2,parsed.size());assertEquals("Nearby",parsed.get(0).name);
        try{NearbyPlacesRepository.parse(new JSONObject("{\"message\":\"Provider error\"}"),center);fail("Malformed provider response accepted as an empty list");}catch(JSONException expected){}
        NearbyPlacesRepository repo=new NearbyPlacesRepository();try{
            List<NearbyPlace> actual=this.<List<NearbyPlace>>result(cb->repo.nearby(center,cb));assertFalse("Actual Q8 POIs",actual.isEmpty());assertTrue(actual.size()<=10);double before=-1;
            for(NearbyPlace place:actual){assertTrue(place.distance<=1000&&place.distance>=before);assertTrue(place.sourceUrl.startsWith("https://www.openstreetmap.org/"));before=place.distance;}
            JSONObject report=new JSONObject();report.put("passed",true);report.put("verifiedAt",Instant.now().toString());report.put("count",actual.size());report.put("radius",1000);report.put("source","MapTiler reverse POI / OpenStreetMap");try(var out=new FileWriter(new File(context.getExternalFilesDir(null),"phase3-places-verification.json"))){out.write(report.toString(2));}
        }finally{repo.close();}
    }
}
