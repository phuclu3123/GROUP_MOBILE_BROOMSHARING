package vn.edu.ueh.phuclu.broomsharing.repository;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;

import androidx.core.content.ContextCompat;

import vn.edu.ueh.phuclu.broomsharing.model.MapPoint;

/** Foreground, one-shot location request, cancelled on screen stop. */
public class LocationRepository {
    private final Context context;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationListener listener;
    private Runnable timeout;

    public LocationRepository(Context context) {
        this.context = context.getApplicationContext();
        manager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    public void current(ResultCallback<MapPoint> callback) {
        cancel();
        boolean fine =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
        boolean coarse =
                ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            callback.onError(new Exception("Chưa có quyền vị trí."));
            return;
        }
        try {
            String provider =
                    fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                            ? LocationManager.GPS_PROVIDER
                            : manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                                    ? LocationManager.NETWORK_PROVIDER
                                    : null;
            if (provider == null) {
                callback.onError(new Exception("Vị trí đang tắt trên thiết bị."));
                return;
            }
            Location cached = manager.getLastKnownLocation(provider);
            if (cached != null
                    && SystemClock.elapsedRealtimeNanos() - cached.getElapsedRealtimeNanos()
                            <= 120_000_000_000L) {
                callback.onSuccess(point(cached));
                return;
            }
            listener =
                    new LocationListener() {
                        @Override
                        public void onLocationChanged(Location value) {
                            if (listener != this) return;
                            cancel();
                            callback.onSuccess(point(value));
                        }

                        @Override
                        public void onProviderDisabled(String value) {
                            if (listener != this) return;
                            cancel();
                            callback.onError(new Exception("Vị trí đang tắt trên thiết bị."));
                        }

                        @Override
                        public void onProviderEnabled(String value) {}

                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {}
                    };
            manager.requestLocationUpdates(provider, 1000, 0, listener, Looper.getMainLooper());
            timeout =
                    () -> {
                        cancel();
                        callback.onError(
                                new Exception(
                                        "Chưa lấy được vị trí hiện tại. Thử tại nơi thoáng hơn."));
                    };
            handler.postDelayed(timeout, 15000);
        } catch (SecurityException error) {
            cancel();
            callback.onError(new Exception("Quyền vị trí đã thay đổi."));
        } catch (Exception error) {
            cancel();
            callback.onError(new Exception("Chưa lấy được vị trí hiện tại."));
        }
    }

    private MapPoint point(Location value) {
        return new MapPoint(
                value.getLatitude(),
                value.getLongitude(),
                "Vị trí thiết bị (±" + Math.round(value.getAccuracy()) + " m)");
    }

    public void cancel() {
        if (timeout != null) {
            handler.removeCallbacks(timeout);
            timeout = null;
        }
        if (listener != null) {
            try {
                manager.removeUpdates(listener);
            } catch (SecurityException ignored) {
            }
            listener = null;
        }
    }
}
