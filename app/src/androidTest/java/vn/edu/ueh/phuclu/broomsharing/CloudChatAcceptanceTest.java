package vn.edu.ueh.phuclu.broomsharing;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.*;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.ChatMessage;
import vn.edu.ueh.phuclu.broomsharing.model.Conversation;
import vn.edu.ueh.phuclu.broomsharing.repository.ChatRepository;
import vn.edu.ueh.phuclu.broomsharing.repository.ResultCallback;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Opt-in real-cloud test. No credentials or message content in evidence reports. */
public class CloudChatAcceptanceTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<FirebaseApp> apps=new ArrayList<>();
    private final Properties credentials=new Properties();
    private final Map<String,FirebaseFirestore> databases=new HashMap<>();
    private static class Answer<T> implements ResultCallback<T>{
        final CompletableFuture<T> result=new CompletableFuture<>();
        public void onSuccess(T value){result.complete(value);}
        public void onError(Exception e){result.completeExceptionally(e);}
        T get()throws Exception{return result.get(45,TimeUnit.SECONDS);}
    }
    private ChatRepository session(String alias)throws Exception{
        FirebaseApp app=FirebaseApp.initializeApp(context,FirebaseApp.getInstance().getOptions(),"chat-"+alias+"-"+UUID.randomUUID());apps.add(app);
        FirebaseAuth auth=FirebaseAuth.getInstance(app);
        Tasks.await(auth.signInWithEmailAndPassword(credentials.getProperty(alias+".email"),credentials.getProperty(alias+".password")),45,TimeUnit.SECONDS);
        FirebaseFirestore db=FirebaseFirestore.getInstance(app,"broomsharing");
        db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());databases.put(alias,db);
        return new ChatRepository(db,auth);
    }
    private Conversation read(String alias,String id)throws Exception{return Tasks.await(databases.get(alias).collection("conversations").document(id).get(Source.SERVER),30,TimeUnit.SECONDS).toObject(Conversation.class);}
    private void denied(Task<?> task)throws Exception{try{Tasks.await(task,30,TimeUnit.SECONDS);fail("Expected permission denied");}catch(ExecutionException e){assertTrue(e.getCause() instanceof FirebaseFirestoreException);assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED,((FirebaseFirestoreException)e.getCause()).getCode());}}
    @Test public void realtimeReceiptsRetryConcurrencyAndPrivacy()throws Exception{
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runChatChecks")));
        try(InputStream in=new FileInputStream(new File(context.getExternalFilesDir(null),"demo-credentials.properties"))){credentials.load(in);}
        ListenerRegistration live=null;
        try{
            ChatRepository guest=session("guest1"),host=session("host1");session("guest2");session("admin");
            QuerySnapshot rooms=Tasks.await(databases.get("guest1").collection("rooms").whereEqualTo("approvalStatus","APPROVED").whereEqualTo("listingStatus","ACTIVE").get(Source.SERVER),30,TimeUnit.SECONDS);
            String roomId=null;for(DocumentSnapshot r:rooms)if(host.uid().equals(r.getString("ownerUid"))){roomId=r.getId();break;}assertNotNull(roomId);
            Answer<String> opened=new Answer<>();guest.openRoom(roomId,opened);String id=opened.get();
            Answer<String> again=new Answer<>();guest.openRoom(roomId,again);assertEquals(id,again.get());
            Conversation before=read("host1",id);
            String messageId=UUID.randomUUID().toString(),text="[QA chat] "+messageId;
            CompletableFuture<Boolean> delivered=new CompletableFuture<>();
            live=host.messages(id,50,new ResultCallback<List<ChatMessage>>(){public void onSuccess(List<ChatMessage> rows){for(ChatMessage m:rows)if(messageId.equals(m.id))delivered.complete(true);}public void onError(Exception e){delivered.completeExceptionally(e);}});
            Answer<String> sent=new Answer<>();guest.send(id,messageId,text,roomId,sent);sent.get();assertTrue(delivered.get(40,TimeUnit.SECONDS));
            Answer<String> retry=new Answer<>();guest.send(id,messageId,text,roomId,retry);retry.get();
            Conversation after=read("host1",id);assertEquals(before.sequence+1,after.sequence);assertEquals(before.hostReceived+1,after.hostReceived);assertEquals(before.guestReceived,after.guestReceived);
            Answer<String> left=new Answer<>(),right=new Answer<>();
            guest.send(id,UUID.randomUUID().toString(),"[QA chat] Concurrent guest",roomId,left);
            host.send(id,UUID.randomUUID().toString(),"[QA chat] Concurrent host",roomId,right);left.get();right.get();
            Conversation concurrent=read("host1",id);assertEquals(after.sequence+2,concurrent.sequence);
            assertEquals(after.hostReceived+1,concurrent.hostReceived);assertEquals(after.guestReceived+1,concurrent.guestReceived);
            Answer<String> receipt=new Answer<>();host.markRead(id,concurrent.hostReceived,receipt);receipt.get();assertEquals(0,read("host1",id).unread(host.uid()));
            denied(databases.get("guest2").collection("conversations").document(id).get(Source.SERVER));
            denied(databases.get("admin").collection("conversations").document(id).collection("messages").get(Source.SERVER));
            denied(databases.get("guest1").collection("conversations").document(id).update("hostRead",concurrent.hostReceived+1));
            denied(databases.get("host1").collection("conversations").document(id).update("hostRead",concurrent.hostReceived+100));
            denied(databases.get("guest1").collection("conversations").document(id).collection("messages").document(messageId).update("text","tampered"));
            Answer<String> guestRead=new Answer<>();guest.markRead(id,concurrent.guestReceived,guestRead);guestRead.get();
            JSONObject report=new JSONObject();report.put("verifiedAt",Instant.now().toString());
            report.put("checks",new JSONArray(Arrays.asList("cloud realtime with independent guest-host sessions","one thread per pair","idempotent retry","concurrent sends preserve counters","recipient read receipts","stranger and admin denied private chat","peer read and forged receipts denied","messages immutable")));
            try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(context.getExternalFilesDir(null),"chat-verification.json")),StandardCharsets.UTF_8)){out.write(report.toString(2));}
        }finally{if(live!=null)live.remove();for(FirebaseFirestore db:databases.values())try{Tasks.await(db.terminate(),10,TimeUnit.SECONDS);}catch(Exception ignored){}for(FirebaseApp app:apps)app.delete();}
    }
}
