package vn.edu.ueh.phuclu.broomsharing.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class UserPreferences {
    private static final String PREF_NAME = "guest_user_prefs";
    private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_AREA = "area_filter";
    private static final String KEY_RENT_MODE = "rent_mode";

    private final SharedPreferences prefs;

    public UserPreferences(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveFilters(String search, String area, String mode) {
        prefs.edit()
                .putString(KEY_SEARCH_QUERY, search)
                .putString(KEY_AREA, area)
                .putString(KEY_RENT_MODE, mode)
                .apply();
    }

    public String getSearchQuery() {
        return prefs.getString(KEY_SEARCH_QUERY, "");
    }

    public String getArea() {
        return prefs.getString(KEY_AREA, "");
    }

    public String getRentMode() {
        return prefs.getString(KEY_RENT_MODE, "");
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}