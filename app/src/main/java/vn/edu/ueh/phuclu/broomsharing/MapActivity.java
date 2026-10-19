package vn.edu.ueh.phuclu.broomsharing;

import android.Manifest;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.*;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.*;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.*;
import org.maplibre.android.maps.*;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.*;

import vn.edu.ueh.phuclu.broomsharing.adapter.MapOfficeAdapter;
import vn.edu.ueh.phuclu.broomsharing.databinding.ActivityMapBinding;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.ui.MapViewModel;
import vn.edu.ueh.phuclu.broomsharing.ui.auth.SessionActivity;
import vn.edu.ueh.phuclu.broomsharing.ui.common.GuestNavigation;
import vn.edu.ueh.phuclu.broomsharing.utils.MapArea;

import java.util.*;

/** View only: native map lifecycle/rendering, input and external navigation. */
@SuppressWarnings("deprecation")
public class MapActivity extends SessionActivity {
    private ActivityMapBinding binding;
    private MapViewModel model;
    private MapView canvas;
    private MapLibreMap map;
    private Bundle mapState;
    private boolean mapStarted, mapResumed, mapLoaded;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable mapTimeout = this::failed;
    private final Map<Long, RoomListing> offices = new HashMap<>();
    private final ActivityResultLauncher<String[]> permission =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (model == null) return;
                        if (Boolean.TRUE.equals(
                                        result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                                || Boolean.TRUE.equals(
                                        result.get(Manifest.permission.ACCESS_COARSE_LOCATION)))
                            model.gps();
                        else
                            model.message.setValue(
                                    "Chưa có quyền vị trí. Nhấn giữ bản đồ để chọn điểm xuất phát"
                                            + " tại TP.HCM.");
                    });

    @Override
    protected int pageLayout() {
        return R.layout.activity_map;
    }

    @Override
    protected void onCreate(Bundle state) {
        mapState = state == null ? null : state.getBundle("map.state");
        super.onCreate(state);
    }

    @Override
    protected void onProfileReady(UserProfile profile) {
        binding =
                ActivityMapBinding.bind(
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0));
        model = new ViewModelProvider(this).get(MapViewModel.class);
        GuestNavigation.attach(this, R.id.nav_map);
        binding.mapCredits.setText(
                android.text.Html.fromHtml(
                        "<a href=\"https://www.maptiler.com/copyright/\">© MapTiler</a> · <a"
                            + " href=\"https://www.openstreetmap.org/copyright\">© OpenStreetMap"
                            + " contributors</a>",
                        android.text.Html.FROM_HTML_MODE_LEGACY));
        binding.mapCredits.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        MapOfficeAdapter adapter =
                new MapOfficeAdapter(room -> model.select(model.point(room), room));
        binding.mapRooms.setLayoutManager(new LinearLayoutManager(this));
        binding.mapRooms.setAdapter(adapter);
        binding.mapSearch.setOnClickListener(v -> search());
        binding.mapAddress.setOnEditorActionListener(
                (v, action, event) -> {
                    if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                        search();
                        return true;
                    }
                    return false;
                });
        binding.mapGps.setOnClickListener(
                v ->
                        permission.launch(
                                new String[] {
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                }));
        binding.mapRoute.setOnClickListener(v -> model.directions());
        binding.mapMore.setOnClickListener(v -> model.loadMore());
        binding.mapRetry.setOnClickListener(
                v -> {
                    model.allowRetry();
                    loadStyle();
                });
        binding.mapDetail.setOnClickListener(
                v -> {
                    RoomListing selected = model.selectedRoom();
                    if (selected != null)
                        startActivity(
                                new Intent(this, SpaceDetailActivity.class)
                                        .putExtra(
                                                SpaceDetailActivity.ROOM_ID,
                                                selected.room.getId()));
                });
        binding.mapTilerLogo.setOnClickListener(v -> openUrl("https://www.maptiler.com/", false));
        model.origin.observe(
                this,
                value -> {
                    binding.mapOrigin.setText(
                            String.format(
                                    Locale.getDefault(),
                                    "Xuất phát: %s (%.5f, %.5f)",
                                    value.name,
                                    value.latitude,
                                    value.longitude));
                    render();
                    if (map != null)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng(value), 14));
                });
        model.destination.observe(
                this,
                value -> {
                    binding.mapDestination.setText(value == null ? "" : "Đích: " + value.name);
                    controls();
                    render();
                });
        model.route.observe(this, value -> render());
        model.message.observe(this, value -> binding.mapMessage.setText(value));
        model.roomMessage.observe(this, value -> binding.mapRoomMessage.setText(value));
        model.busy.observe(this, value -> controls());
        model.roomBusy.observe(
                this,
                value -> {
                    binding.mapMore.setEnabled(!Boolean.TRUE.equals(value));
                    binding.mapMore.setVisibility(model.hasMore() ? View.VISIBLE : View.GONE);
                });
        model.nearby.observe(
                this,
                value -> {
                    adapter.submit(value, model.origin.getValue());
                    binding.mapRooms.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
                    render();
                });
        model.candidates.observe(
                this,
                points -> {
                    if (points == null || points.isEmpty()) return;
                    model.candidates.setValue(null);
                    String[] labels = new String[points.size()];
                    for (int i = 0; i < labels.length; i++) labels[i] = points.get(i).name;
                    new AlertDialog.Builder(this)
                            .setTitle("Chọn địa chỉ xuất phát tại TP.HCM")
                            .setItems(labels, (dialog, index) -> model.setOrigin(points.get(index)))
                            .setNegativeButton("Đóng", null)
                            .show();
                });
        model.googleUrl.observe(this, value -> consumeFallback());
        createMap();
        model.start();
        model.openRoom(getIntent().getStringExtra(SpaceDetailActivity.ROOM_ID));
    }

    private void search() {
        android.view.inputmethod.InputMethodManager keyboard =
                (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(binding.mapAddress.getWindowToken(), 0);
        model.search(binding.mapAddress.getText().toString());
    }

    private void controls() {
        boolean busy = Boolean.TRUE.equals(model.busy.getValue());
        binding.mapSearch.setEnabled(!busy);
        binding.mapGps.setEnabled(!busy);
        binding.mapRoute.setEnabled(!busy && model.destination.getValue() != null);
        binding.mapDetail.setVisibility(model.selectedRoom() == null ? View.GONE : View.VISIBLE);
    }

    private LatLng latLng(MapPoint point) {
        return new LatLng(point.latitude, point.longitude);
    }

    private void createMap() {
        try {
            MapLibre.getInstance(this);
            org.maplibre.android.log.Logger.setVerbosity(org.maplibre.android.log.Logger.NONE);
            canvas = (MapView) ((ViewStub) findViewById(R.id.mapStub)).inflate();
            canvas.onCreate(mapState);
            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                canvas.onStart();
                mapStarted = true;
            }
            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                canvas.onResume();
                mapResumed = true;
            }
            // Individual tile/glyph/sprite failures do not mean the displayed map is unusable.
            canvas.addOnDidFailLoadingMapListener(error -> { if (!mapLoaded) failed(); });
            canvas.addOnRenderErrorListener(() -> runOnUiThread(this::failed));
            canvas.addOnDidFinishLoadingMapListener(
                    () -> {
                        mapLoaded = true;
                        handler.removeCallbacks(mapTimeout);
                        binding.mapProgress.setVisibility(View.GONE);
                    });
            canvas.getMapAsync(
                    value -> {
                        if (isDestroyed()) return;
                        map = value;
                        map.setLatLngBoundsForCameraTarget(
                                new LatLngBounds.Builder()
                                        .include(new LatLng(MapArea.SOUTH, MapArea.WEST))
                                        .include(new LatLng(MapArea.NORTH, MapArea.EAST))
                                        .build());
                        map.setMinZoomPreference(10.5);
                        map.setMaxZoomPreference(18);
                        map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                        latLng(model.origin.getValue()), 14));
                        map.addOnMapClickListener(
                                point -> {
                                    model.select(
                                            new MapPoint(
                                                    point.getLatitude(),
                                                    point.getLongitude(),
                                                    "Điểm chọn tại TP.HCM"),
                                            null);
                                    return true;
                                });
                        map.addOnMapLongClickListener(
                                point -> {
                                    model.setOrigin(
                                            new MapPoint(
                                                    point.getLatitude(),
                                                    point.getLongitude(),
                                                    "Điểm xuất phát đã chọn"));
                                    return true;
                                });
                        map.setOnMarkerClickListener(
                                marker -> {
                                    RoomListing room = offices.get(marker.getId());
                                    if (room != null) model.select(model.point(room), room);
                                    return true;
                                });
                        loadStyle();
                    });
        } catch (RuntimeException | LinkageError error) {
            failed();
        }
    }

    private void loadStyle() {
        if (map == null) {
            failed();
            return;
        }
        mapLoaded = false;
        binding.mapRetry.setVisibility(View.GONE);
        binding.mapProgress.setVisibility(View.VISIBLE);
        handler.removeCallbacks(mapTimeout);
        handler.postDelayed(mapTimeout, 30000);
        try {
            map.setStyle(model.styleUrl(), style -> {
                mapLoaded = true;
                handler.removeCallbacks(mapTimeout);
                binding.mapProgress.setVisibility(View.GONE);
                render();
            });
        } catch (RuntimeException error) {
            failed();
        }
    }

    private void failed() {
        if (binding == null || isDestroyed()) return;
        handler.removeCallbacks(mapTimeout);
        binding.mapProgress.setVisibility(View.GONE);
        binding.mapRetry.setVisibility(View.VISIBLE);
        model.mapFailed();
    }

    private void render() {
        if (map == null || map.getStyle() == null || !map.getStyle().isFullyLoaded()) return;
        map.clear();
        offices.clear();
        map.addMarker(
                new MarkerOptions()
                        .position(latLng(model.origin.getValue()))
                        .title("Xuất phát: " + model.origin.getValue().name));
        List<RoomListing> rooms = model.nearby.getValue();
        if (rooms != null)
            for (RoomListing room : rooms) {
                Marker marker =
                        map.addMarker(
                                new MarkerOptions()
                                        .position(latLng(model.point(room)))
                                        .title(room.room.getRoomName())
                                        .snippet("Văn phòng trong 3 km"));
                offices.put(marker.getId(), room);
            }
        MapPoint end = model.destination.getValue();
        if (end != null)
            map.addMarker(new MarkerOptions().position(latLng(end)).title("Đích: " + end.name));
        MapRoute route = model.route.getValue();
        List<Point> points = new ArrayList<>();
        if (route != null)
            for (MapPoint point : route.points)
                points.add(Point.fromLngLat(point.longitude, point.latitude));
        FeatureCollection geometry =
                route == null
                        ? FeatureCollection.fromFeatures(new Feature[0])
                        : FeatureCollection.fromFeatures(
                                new Feature[] {
                                    Feature.fromGeometry(LineString.fromLngLats(points))
                                });
        Style style = map.getStyle();
        GeoJsonSource source = style.getSourceAs("pilot-route-source");
        if (source == null) {
            style.addSource(new GeoJsonSource("pilot-route-source", geometry));
            style.addLayer(
                    new LineLayer("pilot-route-line", "pilot-route-source")
                            .withProperties(
                                    PropertyFactory.lineColor("#0069D2"),
                                            PropertyFactory.lineWidth(5f),
                                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)));
        } else source.setGeoJson(geometry);
        if (route != null) {
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            for (MapPoint point : route.points) bounds.include(latLng(point));
            int padding = Math.round(56 * getResources().getDisplayMetrics().density);
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), padding));
        }
    }

    private void consumeFallback() {
        if (model == null || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))
            return;
        String url = model.googleUrl.getValue();
        if (url == null) return;
        model.googleUrl.setValue(null);
        openUrl(url, true);
    }

    private void openUrl(String url, boolean google) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (google) intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException first) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException second) {
                Toast.makeText(
                                this,
                                "Thiết bị chưa có ứng dụng mở liên kết bản đồ.",
                                Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (canvas != null && !mapStarted) {
            canvas.onStart();
            mapStarted = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (canvas != null && !mapResumed) {
            canvas.onResume();
            mapResumed = true;
        }
        // Lifecycle reaches RESUMED after this callback; deliver a pending link next loop.
        handler.post(this::consumeFallback);
        if (map != null && !mapLoaded && binding.mapRetry.getVisibility() != View.VISIBLE)
            handler.postDelayed(mapTimeout, 30000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(mapTimeout);
        if (canvas != null && mapResumed) {
            canvas.onPause();
            mapResumed = false;
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (model != null) model.stopLocation();
        if (canvas != null && mapStarted) {
            canvas.onStop();
            mapStarted = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(mapTimeout);
        if (canvas != null) canvas.onDestroy();
        canvas = null;
        map = null;
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (canvas != null) canvas.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        if (canvas != null) {
            Bundle saved = new Bundle();
            canvas.onSaveInstanceState(saved);
            out.putBundle("map.state", saved);
        }
        super.onSaveInstanceState(out);
    }
}
