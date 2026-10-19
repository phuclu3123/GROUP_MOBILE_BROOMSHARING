package vn.edu.ueh.phuclu.broomsharing.ui;

import static org.junit.Assert.*;
import android.app.Application;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.utils.MapArea;
import java.util.Arrays;

public class MapPriorityTest {
    static class Provider extends MapRepository {
        int attempts; boolean fail;
        @Override public void route(MapPoint from, MapPoint to, ResultCallback<MapRoute> callback) {
            attempts++;
            if (fail) callback.onError(new Exception("Test provider unavailable"));
            else callback.onSuccess(new MapRoute(Arrays.asList(from,to),900,180));
        }
    }
    @Test public void samePointStaysInAppAndFailureFallsBackOnlyAfterProviderAttempt() {
        Application app=(Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Provider provider=new Provider(); MapViewModel model=new MapViewModel(app,provider);
            try {
                model.select(MapArea.CENTER,null); model.directions();
                assertEquals(0,provider.attempts); assertNull(model.googleUrl.getValue());
                assertTrue(model.message.getValue().contains("trùng"));
                MapPoint other=new MapPoint(10.7725,106.698,"Đích thử");
                model.select(other,null); model.directions();
                assertEquals(1,provider.attempts); assertNotNull(model.route.getValue());
                assertNull(model.googleUrl.getValue());
                provider.fail=true;model.directions();
                assertEquals(2,provider.attempts);assertNull(model.route.getValue());
                assertTrue(model.googleUrl.getValue().contains("google.com/maps/dir/"));
                model.setOrigin(other);
                assertNull(model.googleUrl.getValue());
            } finally {model.onCleared();}
        });
    }
}
