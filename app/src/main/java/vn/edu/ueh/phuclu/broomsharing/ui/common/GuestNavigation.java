package vn.edu.ueh.phuclu.broomsharing.ui.common;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import vn.edu.ueh.phuclu.broomsharing.*;

/** Shared menu and tab navigation for all guest/host-as-renter screens. */
public final class GuestNavigation {
    private GuestNavigation() {}

    public static void attach(Activity activity, int current) {
        BottomNavigationView nav = activity.findViewById(R.id.bottomNavigation);
        nav.setSelectedItemId(current);
        nav.setOnItemSelectedListener(
                item -> {
                    if (item.getItemId() == current) return true;
                    Class<?> target;
                    if (item.getItemId() == R.id.nav_home) target = MainActivity.class;
                    else if (item.getItemId() == R.id.nav_map) target = MapActivity.class;
                    else if (item.getItemId() == R.id.nav_bookings)
                        target = MyBookingsActivity.class;
                    else if (item.getItemId() == R.id.nav_messages) target = MessagesActivity.class;
                    else if (item.getItemId() == R.id.nav_profile) target = ProfileActivity.class;
                    else return false;
                    activity.startActivity(
                            new Intent(activity, target)
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    return false;
                });
    }
}
