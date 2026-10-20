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
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.*;
import org.maplibre.android.maps.*;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.expressions.Expression;
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
    private final Map<String, List<RoomListing>> offices = new HashMap<>();
    private final Map<String,RentalPlace> referenceMarkers=new HashMap<>();
    private MapOfficeAdapter officeAdapter;
    private com.google.android.material.bottomsheet.BottomSheetDialog resultsDialog;
    private boolean pickMode, firstRender=true;
    private String renderedMarkers, renderedRadius;
    private MapRoute renderedRoute;
    private final ActivityResultLauncher<Intent> nearbyPicker=registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),result->{
                Intent data=result.getData();if(result.getResultCode()!=RESULT_OK||data==null)return;
                getIntent().putExtra("placeLat",data.getDoubleExtra("placeLat",Double.NaN)).putExtra("placeLon",data.getDoubleExtra("placeLon",Double.NaN)).putExtra("placeName",data.getStringExtra("placeName"))
                        .putExtra("originLat",data.getDoubleExtra("originLat",Double.NaN)).putExtra("originLon",data.getDoubleExtra("originLon",Double.NaN));
                consumePlaceSelection();
            });
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
        officeAdapter = new MapOfficeAdapter(this::chooseOffice,p->{closeResults();model.selectReference(p);});
        binding.mapList.setOnClickListener(v->showResults());
        binding.mapToolbar.setOnMenuItemClickListener(item->{
            if(item.getItemId()==R.id.map_reload){model.refreshPlaces();return true;}
            if(item.getItemId()==R.id.map_places){
                MapPoint center=model.destination.getValue()==null?model.origin.getValue():model.destination.getValue();
                nearbyPicker.launch(NearbyPlacesActivity.intent(this,center).putExtra("originLat",model.origin.getValue().latitude).putExtra("originLon",model.origin.getValue().longitude));return true;
            }return false;
        });
        binding.mapPick.setOnClickListener(v->{pickMode=!pickMode;binding.mapPick.setText(pickMode?"Hủy chọn điểm":getString(R.string.map_pick));binding.mapMessage.setText(pickMode?"Chạm vào vị trí muốn tìm. Bạn có thể kéo và phóng to bản đồ trước khi chọn.":model.message.getValue());});
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
        binding.mapMore.setOnClickListener(v -> model.refreshPlaces());
        binding.mapRetry.setOnClickListener(
                v -> {
                    model.allowRetry();
                    loadStyle();
                });
        binding.mapDetail.setOnClickListener(
                v -> {
                    RoomListing selected = model.selectedRoom();
                    RentalPlace reference=model.selectedReference();
                    if(reference!=null){
                        new AlertDialog.Builder(this).setTitle(reference.name).setMessage(reference.address+"\n\n"+reference.locationNote+"\n\n"+getString(R.string.map_reference_note))
                            .setPositiveButton("Mở tin nguồn",(d,w)->{if(reference.sourceUrl.startsWith("https://"))openUrl(reference.sourceUrl,false);})
                            .setNegativeButton(android.R.string.cancel,null).show();return;
                    }
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
                    pickMode=false;binding.mapPick.setText(R.string.map_pick);
                    binding.mapOrigin.setText(
                            String.format(
                                    Locale.getDefault(),
                                    "Tìm quanh: %s",
                                    value.name));
                    render();
                    if (map != null)
                        centerRadius(value);
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
        model.roomMessage.observe(this, value -> {binding.mapRoomMessage.setText(value);binding.mapMore.setVisibility(model.loadFailed()?View.VISIBLE:View.GONE);});
        model.busy.observe(this, value -> controls());
        model.roomBusy.observe(
                this,
                value -> {
                    binding.mapMore.setEnabled(!Boolean.TRUE.equals(value));
                    binding.mapMore.setVisibility(model.loadFailed() ? View.VISIBLE : View.GONE);
                });
        model.nearby.observe(
                this,
                value -> {
                    updateResults();
                    render();
                });
        model.referenceNearby.observe(this,value->{updateResults();render();});
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
        consumePlaceSelection();
        createMap();
        model.start();
        model.openRoom(getIntent().getStringExtra(SpaceDetailActivity.ROOM_ID));
    }

    private void consumePlaceSelection(){
        if(model!=null&&getIntent().hasExtra("placeLat")){
            MapPoint point=new MapPoint(getIntent().getDoubleExtra("placeLat",Double.NaN),getIntent().getDoubleExtra("placeLon",Double.NaN),getIntent().getStringExtra("placeName"));
            MapPoint origin=new MapPoint(getIntent().getDoubleExtra("originLat",Double.NaN),getIntent().getDoubleExtra("originLon",Double.NaN),"Điểm xuất phát đã chọn");
            if(MapArea.contains(origin))model.setOrigin(origin);
            if(MapArea.contains(point)){model.select(point,null);model.directions();}
            getIntent().removeExtra("placeLat");
            getIntent().removeExtra(SpaceDetailActivity.ROOM_ID);
        }
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
        binding.mapSelection.setVisibility(model.destination.getValue()==null?View.GONE:View.VISIBLE);
        binding.mapDetail.setVisibility(model.selectedRoom() == null&&model.selectedReference()==null ? View.GONE : View.VISIBLE);
        binding.mapDetail.setText(model.selectedReference()==null?R.string.map_view_room:R.string.map_view_source);
        binding.mapReferenceNote.setVisibility(model.selectedReference()==null?View.GONE:View.VISIBLE);
    }
    private void updateResults(){
        List<RoomListing> rooms=model.nearby.getValue();List<RentalPlace> refs=model.referenceNearby.getValue();
        if(rooms==null)rooms=Collections.emptyList();if(refs==null)refs=Collections.emptyList();
        officeAdapter.submit(rooms,refs,model.origin.getValue());binding.mapList.setEnabled(!rooms.isEmpty()||!refs.isEmpty());
    }
    private void showResults(){
        if(resultsDialog!=null)return;
        View view=getLayoutInflater().inflate(R.layout.view_map_results,null);
        androidx.recyclerview.widget.RecyclerView list=view.findViewById(R.id.mapRooms);
        list.setLayoutManager(new LinearLayoutManager(this));list.setAdapter(officeAdapter);
        resultsDialog=new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        resultsDialog.setContentView(view);resultsDialog.setOnDismissListener(d->{list.setAdapter(null);resultsDialog=null;});resultsDialog.show();
    }
    private void closeResults(){if(resultsDialog!=null)resultsDialog.dismiss();}
    private void chooseOffice(RoomListing room){closeResults();model.select(model.point(room),room);model.directions();}
    private void centerRadius(MapPoint origin){
        if(map==null||canvas==null)return;
        if(canvas.getWidth()==0){canvas.post(()->centerRadius(origin));return;}
        double lat=3000d/111320,lon=lat/Math.cos(Math.toRadians(origin.latitude));
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(new LatLngBounds.Builder().include(new LatLng(origin.latitude-lat,origin.longitude-lon)).include(new LatLng(origin.latitude+lat,origin.longitude+lon)).build(),32));
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
            canvas.setMaximumFps(30);
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
                        if(mapState==null)centerRadius(model.origin.getValue());
                        map.addOnMapClickListener(
                                point -> {
                                    if(!pickMode)return selectMarker(point);
                                    model.setOrigin(
                                            new MapPoint(
                                                    point.getLatitude(),
                                                    point.getLongitude(),
                                                    "Vị trí đã chọn trên bản đồ"));
                                    pickMode=false;binding.mapPick.setText(R.string.map_pick);
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
                renderedMarkers=renderedRadius=null;renderedRoute=null;firstRender=true;
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
        renderRadius();renderMarkers();
        MapRoute route = model.route.getValue();
        Style style = map.getStyle();
        GeoJsonSource source = style.getSourceAs("pilot-route-source");
        if(source!=null&&renderedRoute==route)return;
        renderedRoute=route;
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
        if (source == null) {
            style.addSource(new GeoJsonSource("pilot-route-source", geometry));
            style.addLayerBelow(
                    new LineLayer("pilot-route-line", "pilot-route-source")
                            .withProperties(
                                    PropertyFactory.lineColor("#0069D2"),
                                            PropertyFactory.lineWidth(5f),
                                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)), "pilot-office-dots");
        } else source.setGeoJson(geometry);
        if (route != null && (!firstRender||mapState==null)) {
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            for (MapPoint point : route.points) bounds.include(latLng(point));
            MapPoint origin=model.origin.getValue();double lat=3000d/111320,lon=lat/Math.cos(Math.toRadians(origin.latitude));
            bounds.include(new LatLng(origin.latitude-lat,origin.longitude-lon)).include(new LatLng(origin.latitude+lat,origin.longitude+lon));
            int padding = Math.round(56 * getResources().getDisplayMetrics().density);
            int card= Math.min(binding.mapSelection.getHeight()+Math.round(32*getResources().getDisplayMetrics().density),canvas.getHeight()/3);
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),padding,padding,padding,Math.max(padding,card)));
        }
        firstRender=false;
    }

    private boolean selectMarker(LatLng position){
        android.graphics.PointF pixel=map.getProjection().toScreenLocation(position);float tolerance=16*getResources().getDisplayMetrics().density;
        List<Feature> hits=map.queryRenderedFeatures(new android.graphics.RectF(pixel.x-tolerance,pixel.y-tolerance,pixel.x+tolerance,pixel.y+tolerance),"pilot-office-dots","pilot-reference-dots");
        if(hits.isEmpty())return false;
        Feature nearest=Collections.min(hits,Comparator.comparingDouble(feature->{Point p=(Point)feature.geometry();return new LatLng(p.latitude(),p.longitude()).distanceTo(position);}));
        String id=nearest.getStringProperty("placeId");RentalPlace reference=referenceMarkers.get(id);
        if(reference!=null){model.selectReference(reference);return true;}
        List<RoomListing> rooms=offices.get(id);if(rooms==null)return false;
        if(rooms.size()==1)chooseOffice(rooms.get(0));
        else{String[] labels=new String[rooms.size()];for(int i=0;i<labels.length;i++)labels[i]=rooms.get(i).room.getRoomName();
            new AlertDialog.Builder(this).setTitle("Chọn không gian trong tòa nhà").setItems(labels,(d,index)->chooseOffice(rooms.get(index))).setNegativeButton(android.R.string.cancel,null).show();}
        return true;
    }
    private Feature marker(MapPoint point,String id,String kind){Feature f=Feature.fromGeometry(Point.fromLngLat(point.longitude,point.latitude));f.addStringProperty("placeId",id);f.addStringProperty("kind",kind);return f;}
    private String key(MapPoint p){return p==null?"":p.latitude+","+p.longitude;}
    private void renderMarkers(){
        List<RoomListing> rooms=model.nearby.getValue();List<RentalPlace> refs=model.referenceNearby.getValue();
        StringBuilder signature=new StringBuilder(key(model.origin.getValue())).append(':').append(key(model.destination.getValue()));
        if(rooms!=null)for(RoomListing r:rooms)signature.append('|').append(r.room.getId()).append(key(model.point(r)));
        if(refs!=null)for(RentalPlace p:refs)signature.append('|').append(p.id).append(key(model.point(p)));
        if(signature.toString().equals(renderedMarkers))return;
        renderedMarkers=signature.toString();offices.clear();referenceMarkers.clear();List<Feature> features=new ArrayList<>();
        features.add(marker(model.origin.getValue(),"origin","origin"));
        Map<String,List<RoomListing>> grouped=new LinkedHashMap<>();
        if(rooms!=null)for(RoomListing r:rooms)grouped.computeIfAbsent(r.building.getId(),id->new ArrayList<>()).add(r);
        for(List<RoomListing> group:grouped.values()){
            RoomListing r=group.get(0);String id="room:"+r.building.getId();features.add(marker(model.point(r),id,"office"));offices.put(id,group);
        }
        if(refs!=null)for(RentalPlace p:refs){String id="reference:"+p.id;features.add(marker(model.point(p),id,"reference"));referenceMarkers.put(id,p);}
        MapPoint end=model.destination.getValue();if(end!=null)features.add(marker(end,"destination","destination"));
        Style style=map.getStyle();GeoJsonSource source=style.getSourceAs("pilot-marker-source");FeatureCollection geometry=FeatureCollection.fromFeatures(features);
        if(source==null){
            style.addSource(new GeoJsonSource("pilot-marker-source",geometry));
            String[] kinds={"office","reference","origin","destination"};String[] colors={"#2F7D78","#D97924","#1677D2","#A83540"};
            for(int i=0;i<kinds.length;i++)style.addLayer(new CircleLayer("pilot-"+kinds[i]+"-dots","pilot-marker-source")
                .withFilter(Expression.eq(Expression.get("kind"),Expression.literal(kinds[i])))
                .withProperties(PropertyFactory.circleColor(colors[i]),PropertyFactory.circleRadius(i==3?9f:8f),PropertyFactory.circleStrokeColor("#FFFFFF"),PropertyFactory.circleStrokeWidth(2f)));
        }else source.setGeoJson(geometry);
    }
    private void renderRadius(){
        MapPoint origin=model.origin.getValue();if(key(origin).equals(renderedRadius))return;renderedRadius=key(origin);
        List<Point> ring=new ArrayList<>();double lat=Math.toRadians(origin.latitude),lon=Math.toRadians(origin.longitude),distance=3000d/6371000;
        for(int i=0;i<64;i++){
            double bearing=2*Math.PI*i/64;
            double nextLat=Math.asin(Math.sin(lat)*Math.cos(distance)+Math.cos(lat)*Math.sin(distance)*Math.cos(bearing));
            double nextLon=lon+Math.atan2(Math.sin(bearing)*Math.sin(distance)*Math.cos(lat),Math.cos(distance)-Math.sin(lat)*Math.sin(nextLat));
            ring.add(Point.fromLngLat(Math.toDegrees(nextLon),Math.toDegrees(nextLat)));
        }ring.add(ring.get(0));
        FeatureCollection geometry=FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(org.maplibre.geojson.Polygon.fromLngLats(Collections.singletonList(ring)))});
        Style style=map.getStyle();GeoJsonSource source=style.getSourceAs("pilot-radius-source");
        if(source==null){
            style.addSource(new GeoJsonSource("pilot-radius-source",geometry));
            style.addLayer(new FillLayer("pilot-radius-fill","pilot-radius-source").withProperties(PropertyFactory.fillColor("#2F7D78"),PropertyFactory.fillOpacity(0.08f)));
            style.addLayer(new LineLayer("pilot-radius-line","pilot-radius-source").withProperties(PropertyFactory.lineColor("#2F7D78"),PropertyFactory.lineWidth(1.5f)));
        }else source.setGeoJson(geometry);
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
        closeResults();
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
