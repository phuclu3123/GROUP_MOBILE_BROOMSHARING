package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import java.io.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.repository.FirebaseAuthRepository;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.LoginActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.RoleAccountActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostBookingsActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostDashboardActivity;

/** Opt-in cloud UI navigation check. Reads existing demo data; never approves or edits it. */
public class RoleWorkspaceTest {
    private final android.app.Instrumentation device = InstrumentationRegistry.getInstrumentation();
    private final android.content.Context context = device.getTargetContext();
    private final Properties credentials = new Properties();
    private final FirebaseAuthRepository auth = new FirebaseAuthRepository(context);

    private <T extends Activity> void await(Class<T> type, Predicate<T> ready, String label) {
        AtomicBoolean done = new AtomicBoolean();
        for (int i = 0; i < 180 && !done.get(); i++) {
            device.runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if (type.isInstance(activity) && activity.hasWindowFocus()) done.set(ready.test(type.cast(activity)));
            });
            if (!done.get()) SystemClock.sleep(250);
        }
        assertTrue(label, done.get());
    }

    private <T extends Activity> void act(Class<T> type, Consumer<T> action) {
        AtomicBoolean found = new AtomicBoolean();
        device.runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                if (type.isInstance(activity)) { found.set(true); action.accept(type.cast(activity)); break; }
        });
        assertTrue("Expected active screen " + type.getSimpleName(), found.get());
    }

    private void openLogin() {
        device.runOnMainSync(() -> context.startActivity(new Intent(context, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)));
    }

    private void login(String alias) {
        auth.logout(); openLogin();
        await(LoginActivity.class, a -> a.findViewById(R.id.submitButton) != null, "Login did not open");
        act(LoginActivity.class, a -> {
            ((TextView) a.findViewById(R.id.identifierInput)).setText("demo_" + alias);
            ((TextView) a.findViewById(R.id.passwordInput)).setText(credentials.getProperty(alias + ".password"));
            a.findViewById(R.id.submitButton).performClick();
        });
    }

    private void tab(Activity activity, int id) {
        ((BottomNavigationView) activity.findViewById(R.id.managementNavigation)).setSelectedItemId(id);
    }

    private void capture(String name) throws Exception {
        SystemClock.sleep(500);
        Bitmap bitmap = device.getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try (OutputStream out = new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { bitmap.recycle(); }
    }

    @Test public void guestFiltersBookingsAndProfileStayInRenterArea() throws Exception {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runRoleUiChecks")));
        try (InputStream in = new FileInputStream(new File(context.getExternalFilesDir(null), "demo-credentials.properties"))) {
            credentials.load(in);
        }
        try {
            login("guest1");
            await(MainActivity.class, a -> a.findViewById(R.id.publicRooms) != null
                    && ((androidx.recyclerview.widget.RecyclerView) a.findViewById(R.id.publicRooms)).getAdapter().getItemCount() > 0, "Guest public rooms");
            capture("role-guest-home");
            act(MainActivity.class, a -> a.findViewById(R.id.toggleFilters).performClick());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.capacityFilter))
                    .perform(androidx.test.espresso.action.ViewActions.replaceText("9999"));
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.click());
            await(MainActivity.class, a -> a.findViewById(R.id.tvFeedback).getVisibility() == View.VISIBLE, "Filter empty state");
            act(MainActivity.class, a -> a.findViewById(R.id.toggleFilters).performClick());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.clearFilters))
                    .perform(androidx.test.espresso.action.ViewActions.scrollTo(), androidx.test.espresso.action.ViewActions.click());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.click());
            await(MainActivity.class, a -> a.findViewById(R.id.activeFilters).getVisibility() == View.GONE, "Clear applied filters");
            act(MainActivity.class, Activity::recreate);
            await(MainActivity.class, a -> a.findViewById(R.id.publicRooms) != null, "Restore Guest home");
            act(MainActivity.class, a -> ((BottomNavigationView) a.findViewById(R.id.bottomNavigation)).setSelectedItemId(R.id.nav_bookings));
            await(MyBookingsActivity.class, a -> a.findViewById(R.id.bookings) != null
                    && ((androidx.recyclerview.widget.RecyclerView) a.findViewById(R.id.bookings)).getAdapter().getItemCount() > 0, "Load Guest bookings");
            capture("role-guest-bookings");
            act(MyBookingsActivity.class, a -> ((BottomNavigationView) a.findViewById(R.id.bottomNavigation)).setSelectedItemId(R.id.nav_profile));
            await(ProfileActivity.class, a -> a.findViewById(R.id.hostManagementMenu) != null
                    && a.findViewById(R.id.hostManagementMenu).getVisibility() == View.GONE, "Guest profile cannot show Host management");
            capture("role-guest-account");
            act(ProfileActivity.class, a -> a.findViewById(R.id.btnFavorites).performClick());
            await(FavoritesActivity.class, a -> a.findViewById(R.id.list) != null, "Open saved rooms");
        } finally {
            device.runOnMainSync(() -> {
                for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if (a instanceof LoginActivity && a.findViewById(R.id.passwordInput) != null)
                        ((TextView) a.findViewById(R.id.passwordInput)).setText("");
            });
            auth.logout(); openLogin();
        }
    }

    @Test public void hostAndAdminHaveTheirOwnWorkspaces() throws Exception {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runRoleUiChecks")));
        assertTrue(BuildConfig.FIRESTORE_EMULATOR_HOST.isEmpty());
        try (InputStream in = new FileInputStream(new File(context.getExternalFilesDir(null), "demo-credentials.properties"))) {
            credentials.load(in);
        }
        try {
            login("host1");
            await(HostDashboardActivity.class, a -> a.findViewById(R.id.tvCountRooms) != null
                    && !"—".contentEquals(((TextView) a.findViewById(R.id.tvCountRooms)).getText()), "Host login must open Host overview");
            capture("role-host-overview");
            act(HostDashboardActivity.class, a -> tab(a, R.id.host_spaces));
            await(HostDashboardActivity.class, a -> a.findViewById(R.id.spacesPanel).getVisibility() == View.VISIBLE, "Spaces tab");
            capture("role-host-spaces");
            act(HostDashboardActivity.class, a -> {
                androidx.recyclerview.widget.RecyclerView list = a.findViewById(R.id.rvRooms);
                assertNotNull("Host rooms must have a calendar action", list.findViewHolderForAdapterPosition(0));
                list.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.roomCalendar).performClick();
            });
            await(HostBookingsActivity.class, a -> a.findViewById(R.id.schedulePanel) != null
                    && a.findViewById(R.id.schedulePanel).getVisibility() == View.VISIBLE, "Open calendar for selected room");
            act(HostBookingsActivity.class, a -> tab(a, R.id.host_spaces));
            await(HostDashboardActivity.class, a -> a.findViewById(R.id.spaceTabs) != null, "Return to spaces");
            act(HostDashboardActivity.class, a -> ((TabLayout) a.findViewById(R.id.spaceTabs)).selectTab(
                    ((TabLayout) a.findViewById(R.id.spaceTabs)).getTabAt(1)));
            act(HostDashboardActivity.class, Activity::recreate);
            await(HostDashboardActivity.class, a -> a.findViewById(R.id.spaceTabs) != null
                    && ((TabLayout) a.findViewById(R.id.spaceTabs)).getSelectedTabPosition() == 1
                    && a.findViewById(R.id.spacesPanel).getVisibility() == View.VISIBLE, "Restore Host tab after recreation");
            capture("role-host-buildings");
            act(HostDashboardActivity.class, a -> tab(a, R.id.host_bookings));
            await(HostBookingsActivity.class, a -> a.findViewById(R.id.chipCompleted) != null, "Host bookings navigation");
            act(HostBookingsActivity.class, a -> a.findViewById(R.id.chipCompleted).performClick());
            capture("role-host-bookings");
            act(HostBookingsActivity.class, a -> tab(a, R.id.host_account));
            await(RoleAccountActivity.class, a -> a.findViewById(R.id.roleLabel) != null, "Host account navigation");
            capture("role-host-account");
            act(RoleAccountActivity.class, a -> a.findViewById(R.id.switchToRenter).performClick());
            await(MainActivity.class, a -> a.findViewById(R.id.bottomNavigation) != null, "Host renter switch");

            login("admin");
            await(AdminDashboardActivity.class, a -> a.findViewById(R.id.adminOverview) != null, "Admin login must open Admin overview");
            capture("role-admin-overview");
            act(AdminDashboardActivity.class, a -> tab(a, R.id.admin_review));
            await(AdminDashboardActivity.class, a -> a.findViewById(R.id.adminTabs) != null
                    && ((TabLayout) a.findViewById(R.id.adminTabs)).getTabCount() == 3, "Admin review groups");
            await(AdminDashboardActivity.class, a -> a.findViewById(R.id.progressBar).getVisibility() != View.VISIBLE, "Load review data");
            capture("role-admin-review");
            act(AdminDashboardActivity.class, a -> ((TabLayout) a.findViewById(R.id.adminTabs)).selectTab(
                    ((TabLayout) a.findViewById(R.id.adminTabs)).getTabAt(2)));
            act(AdminDashboardActivity.class, Activity::recreate);
            await(AdminDashboardActivity.class, a -> a.findViewById(R.id.adminTabs) != null
                    && ((TabLayout) a.findViewById(R.id.adminTabs)).getSelectedTabPosition() == 2, "Restore Admin subtab after recreation");
            act(AdminDashboardActivity.class, a -> tab(a, R.id.admin_manage));
            await(AdminDashboardActivity.class, a -> a.findViewById(R.id.progressBar).getVisibility() != View.VISIBLE, "Load management data");
            capture("role-admin-manage");
            act(AdminDashboardActivity.class, a -> tab(a, R.id.admin_reports));
            await(AdminDashboardActivity.class, a -> a.findViewById(R.id.progressBar).getVisibility() != View.VISIBLE, "Load reports");
            capture("role-admin-reports");
            act(AdminDashboardActivity.class, a -> tab(a, R.id.admin_account));
            await(RoleAccountActivity.class, a -> a.findViewById(R.id.switchToRenter) != null
                    && a.findViewById(R.id.switchToRenter).getVisibility() == View.GONE, "Admin account has no renter switch");
            capture("role-admin-account");
            act(RoleAccountActivity.class, a -> {
                assertEquals(a.getString(R.string.workspace_role_admin), ((TextView) a.findViewById(R.id.roleLabel)).getText().toString());
                tab(a, R.id.admin_overview);
            });
            await(AdminDashboardActivity.class, a -> a.findViewById(R.id.adminOverview).getVisibility() == View.VISIBLE, "Return to Admin overview");
            act(AdminDashboardActivity.class, a -> tab(a, R.id.admin_account));
            await(RoleAccountActivity.class, a -> a.findViewById(R.id.accountSignOut) != null, "Open Admin account again");
            act(RoleAccountActivity.class, a -> a.findViewById(R.id.accountSignOut).performClick());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button2))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.click());
            act(RoleAccountActivity.class, a -> a.findViewById(R.id.accountSignOut).performClick());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.click());
            await(LoginActivity.class, a -> a.findViewById(R.id.submitButton) != null, "Sign out must clear the role workspace");
        } finally {
            // No screenshot or hierarchy dump of a populated password field, including failures.
            device.runOnMainSync(() -> {
                for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if (a instanceof LoginActivity && a.findViewById(R.id.passwordInput) != null)
                        ((TextView) a.findViewById(R.id.passwordInput)).setText("");
            });
            auth.logout(); openLogin();
        }
    }
}
