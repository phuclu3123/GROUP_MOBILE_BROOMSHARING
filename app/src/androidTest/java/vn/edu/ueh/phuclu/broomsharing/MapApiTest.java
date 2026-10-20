package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.repository.*;
import vn.edu.ueh.phuclu.broomsharing.utils.MapArea;

import java.util.*;
import java.util.concurrent.*;

/** Opt-in real provider test, Java only. Fixed pilot coordinates in Ho Chi Minh City. */
@RunWith(AndroidJUnit4.class)
public class MapApiTest {
    @Test
    public void actualGeocodingAndRoadRouteAtHcmc() throws Exception {
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runMapApis")));
        MapRepository maps = new MapRepository();
        CountDownLatch done = new CountDownLatch(2);
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());
        try {
            maps.geocode(
                    "279 Nguyen Tri Phuong, Ho Chi Minh City",
                    new ResultCallback<List<MapPoint>>() {
                        public void onSuccess(List<MapPoint> points) {
                            if (points.isEmpty()
                                    || points.stream().anyMatch(point -> !MapArea.contains(point)))
                                errors.add(new Exception("Geocoding outside pilot"));
                            done.countDown();
                        }

                        public void onError(Exception e) {
                            errors.add(new Exception("Geocoding: "+e.getMessage()));
                            done.countDown();
                        }
                    });
            maps.route(
                    MapArea.CENTER,
                    new MapPoint(10.7725, 106.698, "Đích thử TP.HCM"),
                    new ResultCallback<MapRoute>() {
                        public void onSuccess(MapRoute route) {
                            if (route.distanceMeters <= 0 || route.points.size() < 3)
                                errors.add(new Exception("Invalid route"));
                            done.countDown();
                        }

                        public void onError(Exception e) {
                            errors.add(new Exception("Directions: "+e.getMessage()));
                            done.countDown();
                        }
                    });
            assertTrue(done.await(60, TimeUnit.SECONDS));
            assertTrue("Provider request failed: "+errors, errors.isEmpty());
        } finally {
            maps.close();
        }
    }
}
