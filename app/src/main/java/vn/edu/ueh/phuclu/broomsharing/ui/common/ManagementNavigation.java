package vn.edu.ueh.phuclu.broomsharing.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.function.IntConsumer;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.model.UserRole;
import vn.edu.ueh.phuclu.broomsharing.ui.admin.AdminDashboardActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ConversationsActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostBookingsActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.host.HostDashboardActivity;

/** Role navigation only; permissions remain in SessionActivity and Firestore rules. */
public final class ManagementNavigation {
    public static final String SECTION = "workspace_section";
    private ManagementNavigation() {}

    public static void attach(Activity activity, UserRole role, int current, IntConsumer localTab) {
        BottomNavigationView nav = activity.findViewById(R.id.managementNavigation);
        nav.getMenu().clear();
        nav.inflateMenu(role == UserRole.ADMIN ? R.menu.menu_admin_navigation : R.menu.menu_host_navigation);
        nav.setSelectedItemId(current);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (localTab != null && isLocal(activity, id)) {
                localTab.accept(id);
                return true;
            }
            if (id == current) return true;
            Class<?> target;
            if (id == R.id.host_account || id == R.id.admin_account) target = RoleAccountActivity.class;
            else if (id == R.id.host_messages) target = ConversationsActivity.class;
            else if (role == UserRole.ADMIN) target = AdminDashboardActivity.class;
            else if (id == R.id.host_bookings) target = HostBookingsActivity.class;
            else target = HostDashboardActivity.class;
            activity.startActivity(new Intent(activity, target).putExtra(SECTION, id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            if (!(activity instanceof HostDashboardActivity) && !(activity instanceof AdminDashboardActivity)) activity.finish();
            return false;
        });
    }

    private static boolean isLocal(Activity activity, int id) {
        if (activity instanceof HostDashboardActivity)
            return id == R.id.host_overview || id == R.id.host_spaces;
        return activity instanceof AdminDashboardActivity && id != R.id.admin_account;
    }

    public static void select(Activity activity, int id) {
        BottomNavigationView nav = activity.findViewById(R.id.managementNavigation);
        nav.getMenu().findItem(id).setChecked(true);
    }

    public static void applyInsets(Activity activity) {
        View root = activity.findViewById(R.id.workspaceRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, window) -> {
            Insets bars = window.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return window;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
