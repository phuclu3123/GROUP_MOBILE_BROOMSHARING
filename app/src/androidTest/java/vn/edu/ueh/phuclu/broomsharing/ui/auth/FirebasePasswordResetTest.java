package vn.edu.ueh.phuclu.broomsharing.ui.auth;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.R;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Opt-in cloud check: one email to the approved recipient; reset only a disposable QA account. */
public class FirebasePasswordResetTest {
    private final Instrumentation device=InstrumentationRegistry.getInstrumentation();
    private final Context context=device.getTargetContext();
    private Properties fixture() throws Exception {
        Properties p = new Properties();
        try (var in = new FileInputStream(new File(context.getExternalFilesDir(null), "firebase-reset-qa.properties"))) { p.load(in); }
        return p;
    }
    private void enabled(){assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runFirebaseReset")));}
    private void capture(String name)throws Exception{
        Bitmap b=null;for(int i=0;i<3&&b==null;i++){SystemClock.sleep(500);b=device.getUiAutomation().takeScreenshot();}assertNotNull(b);
        try(var out=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}
    }
    @Test public void appSendsFirebaseEmailAndKeepsCooldownAfterRecreation()throws Exception{
        enabled();FirebaseAuth.getInstance().signOut();
        String recipient = fixture().getProperty("recipientEmail");
        Intent intent=new Intent(context,ForgotPasswordActivity.class).putExtra("reset_email",recipient);
        try(ActivityScenario<ForgotPasswordActivity> screen=ActivityScenario.launch(intent)){
            onView(withId(R.id.emailInput)).check(matches(withText(recipient)));
            onView(withId(R.id.submitButton)).perform(scrollTo(),click());AtomicBoolean complete=new AtomicBoolean();
            for(int i=0;i<240&&!complete.get();i++){screen.onActivity(a->{AuthViewModel m=new ViewModelProvider(a).get(AuthViewModel.class);complete.set(!m.isBusy()&&m.passwordResetSent());});if(!complete.get())SystemClock.sleep(250);}
            assertTrue("Firebase accepted password-reset email request",complete.get());
            screen.onActivity(a->{assertFalse(a.findViewById(R.id.submitButton).isEnabled());assertTrue(new ViewModelProvider(a).get(AuthViewModel.class).passwordResetWaitSeconds()>0);});
            onView(withText(R.string.auth_reset_sent)).check(matches(isDisplayed()));capture("firebase-reset-sent");
            screen.recreate();screen.onActivity(a->{assertFalse(a.findViewById(R.id.submitButton).isEnabled());assertTrue(new ViewModelProvider(a).get(AuthViewModel.class).passwordResetSent());});
            onView(withId(R.id.emailInput)).check(matches(withText(recipient)));
            onView(withId(R.id.loginButton)).perform(scrollTo(),click());
        }
    }
    @Test public void firebaseLinkChangesOnlyQaPasswordAndRejectsReuse()throws Exception{
        enabled();Properties p=fixture();
        FirebaseAuth auth=FirebaseAuth.getInstance();auth.signOut();String code=p.getProperty("resetCode");
        try{
            assertEquals(p.getProperty("email"),Tasks.await(auth.verifyPasswordResetCode(code),30,TimeUnit.SECONDS));
            try{Tasks.await(auth.verifyPasswordResetCode("invalid-reset-code"),30,TimeUnit.SECONDS);fail("Invalid reset link accepted");}catch(ExecutionException expected){}
            try{Tasks.await(auth.confirmPasswordReset(code,"123"),30,TimeUnit.SECONDS);fail("Weak password accepted");}catch(ExecutionException expected){}
            Tasks.await(auth.confirmPasswordReset(code,p.getProperty("newPassword")),30,TimeUnit.SECONDS);
            try{Tasks.await(auth.confirmPasswordReset(code,p.getProperty("oldPassword")),30,TimeUnit.SECONDS);fail("Used reset link accepted twice");}catch(ExecutionException expected){}
            try{Tasks.await(auth.signInWithEmailAndPassword(p.getProperty("email"),p.getProperty("oldPassword")),30,TimeUnit.SECONDS);fail("Old password still signs in");}catch(ExecutionException expected){}
            assertNotNull(Tasks.await(auth.signInWithEmailAndPassword(p.getProperty("email"),p.getProperty("newPassword")),30,TimeUnit.SECONDS).getUser());
        }finally{auth.signOut();}
    }
}
