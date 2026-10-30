package vn.edu.ueh.phuclu.broomsharing.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import vn.edu.ueh.phuclu.broomsharing.R;
import vn.edu.ueh.phuclu.broomsharing.ui.booking.MyBookingsActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.chat.ConversationsActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.map.MapActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.profile.ProfileActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.room.MainActivity;

/** Shared menu and tab navigation for all guest/host-as-renter screens. */
public final class GuestNavigation {
    private GuestNavigation() {}

    public static void attach(Activity activity, int current) {
        BottomNavigationView nav = activity.findViewById(R.id.bottomNavigation);
        nav.setSelectedItemId(current);
        View root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        nav.setOnItemSelectedListener(
                item -> {
                    if (item.getItemId() == current) return true;
                    if (!vn.edu.ueh.phuclu.broomsharing.repository.OfflineRepository.online()
                            && item.getItemId() != R.id.nav_home && item.getItemId() != R.id.nav_bookings) {
                        if (item.getItemId() == R.id.nav_profile && activity instanceof vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity)
                            new androidx.appcompat.app.AlertDialog.Builder(activity)
                                    .setTitle("Chế độ ngoại tuyến")
                                    .setMessage("Bạn có thể xem phòng và đặt chỗ đã lưu. Đăng xuất sẽ xóa bản lưu của tài khoản trên thiết bị này.")
                                    .setNegativeButton("Đóng", null)
                                    .setPositiveButton("Đăng xuất", (dialog, which) -> ((vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity) activity).signOutOffline()).show();
                        else android.widget.Toast.makeText(activity, "Chức năng này cần kết nối mạng.", android.widget.Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    Class<?> target;
                    if (item.getItemId() == R.id.nav_home) target = MainActivity.class;
                    else if (item.getItemId() == R.id.nav_map) target = MapActivity.class;
                    else if (item.getItemId() == R.id.nav_bookings)
                        target = MyBookingsActivity.class;
                    else if (item.getItemId() == R.id.nav_profile) target = ProfileActivity.class;
                    else if (item.getItemId() == R.id.nav_messages) target = ConversationsActivity.class;
                    else return false;
                    activity.startActivity(
                            new Intent(activity, target)
                                    .putExtra("renterMode", true)
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    if (!(activity instanceof MainActivity)) activity.finish();
                    return false;
                });
    }
}
