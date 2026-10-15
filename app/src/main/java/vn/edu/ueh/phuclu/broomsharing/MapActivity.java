package vn.edu.ueh.phuclu.broomsharing;

import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestSectionActivity;

public class MapActivity extends GuestSectionActivity {
    @Override
    protected int tab() {
        return R.id.nav_map;
    }

    @Override
    protected int title() {
        return R.string.nav_map_label;
    }

    @Override
    protected int heading() {
        return R.string.map_pending_title;
    }

    @Override
    protected int description() {
        return R.string.map_pending_body;
    }

    @Override
    protected int icon() {
        return R.drawable.ic_map;
    }
}
