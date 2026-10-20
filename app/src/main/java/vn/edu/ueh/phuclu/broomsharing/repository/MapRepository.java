package vn.edu.ueh.phuclu.broomsharing.repository;

import android.os.Handler;
import android.os.Looper;

import org.json.*;

import vn.edu.ueh.phuclu.broomsharing.BuildConfig;
import vn.edu.ueh.phuclu.broomsharing.model.*;
import vn.edu.ueh.phuclu.broomsharing.utils.MapArea;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Only provider I/O. No Activity, DTO or additional network framework. */
public class MapRepository {
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String,MapRoute> routeCache=Collections.synchronizedMap(new LinkedHashMap<String,MapRoute>(16,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,MapRoute> e){return size()>12;}
    });
    private final Map<String,List<MapPoint>> searchCache=Collections.synchronizedMap(new LinkedHashMap<String,List<MapPoint>>(16,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,List<MapPoint>> e){return size()>12;}
    });

    private static String encode(String text) throws UnsupportedEncodingException {
        return URLEncoder.encode(text, StandardCharsets.UTF_8.name()).replace("+","%20");
    }

    public String styleUrl() {
        if (BuildConfig.MAPTILER_API_KEY.isEmpty())
            throw new IllegalStateException("Thiếu cấu hình bản đồ.");
        return "https://api.maptiler.com/maps/streets-v2/style.json?key="
                + BuildConfig.MAPTILER_API_KEY;
    }

    private JSONObject json(String url, JSONObject payload, String authorization) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json, application/geo+json");
        connection.setRequestProperty("User-Agent", "BroomSharing-Android/1.0");
        try {
            if (authorization != null)
                connection.setRequestProperty("Authorization", authorization);
            if (payload != null) {
                byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(data);
                }
            }
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Dịch vụ bản đồ trả mã " + status + ".");
            try (InputStream input = connection.getInputStream();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192];
                int count;
                while ((count = input.read(bytes)) != -1) {
                    if (buffer.size() + count > 2 * 1024 * 1024)
                        throw new IOException("Dữ liệu bản đồ quá lớn.");
                    buffer.write(bytes, 0, count);
                }
                return new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {
            connection.disconnect();
        }
    }

    public void geocode(String query, ResultCallback<List<MapPoint>> callback) {
        geocode(query,MapArea.CENTER,callback);
    }
    public void geocode(String query, MapPoint bias, ResultCallback<List<MapPoint>> callback) {
        worker.execute(
                () -> {
                    try {
                        if (BuildConfig.MAPTILER_API_KEY.isEmpty()) throw new IOException();
                        String text = query == null ? "" : query.trim();
                        if (text.length() < 3 || text.length() > 200)
                            throw new IllegalArgumentException();
                        String cacheKey=text.toLowerCase(Locale.ROOT)+":"+bias.latitude+":"+bias.longitude;
                        List<MapPoint> cached=searchCache.get(cacheKey);
                        if(cached!=null){main.post(()->callback.onSuccess(new ArrayList<>(cached)));return;}
                        String url =
                                "https://api.maptiler.com/geocoding/"
                                        + encode(text)
                                        + ".json?key="
                                        + BuildConfig.MAPTILER_API_KEY
                                        + "&country=vn&language=vi&limit=5&autocomplete=false&proximity="+bias.longitude+","+bias.latitude+"&bbox="
                                        + MapArea.WEST
                                        + ","
                                        + MapArea.SOUTH
                                        + ","
                                        + MapArea.EAST
                                        + ","
                                        + MapArea.NORTH;
                        JSONArray features = json(url, null, null).getJSONArray("features");
                        List<MapPoint> result = new ArrayList<>();
                        for (int i = 0; i < features.length(); i++) {
                            JSONObject feature = features.getJSONObject(i);
                            JSONArray coordinate =
                                    feature.getJSONObject("geometry").getJSONArray("coordinates");
                            MapPoint point =
                                    new MapPoint(
                                            coordinate.getDouble(1),
                                            coordinate.getDouble(0),
                                            feature.optString("place_name", text));
                            if (MapArea.contains(point)) result.add(point);
                        }
                        if(!result.isEmpty())searchCache.put(cacheKey,new ArrayList<>(result));
                        main.post(() -> callback.onSuccess(result));
                    } catch (Exception error) {
                        // Raw exceptions / provider URLs can contain API keys; never forward or log
                        // them.
                        main.post(
                                () ->
                                        callback.onError(
                                                new Exception(
                                                        "Chưa tìm được địa chỉ qua dịch vụ bản"
                                                            + " đồ.")));
                    }
                });
    }

    public void route(MapPoint origin, MapPoint destination, ResultCallback<MapRoute> callback) {
        worker.execute(
                () -> {
                    try {
                        if (BuildConfig.HEIGIT_API_KEY.isEmpty()
                                || !MapArea.contains(origin)
                                || !MapArea.contains(destination)) throw new IOException();
                        String cacheKey=origin.latitude+","+origin.longitude+":"+destination.latitude+","+destination.longitude;
                        MapRoute cached=routeCache.get(cacheKey);
                        if(cached!=null){main.post(()->callback.onSuccess(cached));return;}
                        JSONArray coordinates =
                                new JSONArray()
                                        .put(
                                                new JSONArray()
                                                        .put(origin.longitude)
                                                        .put(origin.latitude))
                                        .put(
                                                new JSONArray()
                                                        .put(destination.longitude)
                                                        .put(destination.latitude));
                        JSONObject payload =
                                new JSONObject()
                                        .put("coordinates", coordinates)
                                        .put("instructions", false);
                        JSONObject feature =
                                json(
                                                "https://api.openrouteservice.org/v2/directions/driving-car/geojson",
                                                payload,
                                                BuildConfig.HEIGIT_API_KEY)
                                        .getJSONArray("features")
                                        .getJSONObject(0);
                        JSONArray geometry =
                                feature.getJSONObject("geometry").getJSONArray("coordinates");
                        List<MapPoint> points = new ArrayList<>();
                        for (int i = 0; i < geometry.length(); i++) {
                            JSONArray point = geometry.getJSONArray(i);
                            points.add(new MapPoint(point.getDouble(1), point.getDouble(0), ""));
                        }
                        JSONObject summary =
                                feature.getJSONObject("properties").getJSONObject("summary");
                        MapRoute route =
                                new MapRoute(
                                        points,
                                        summary.getDouble("distance"),
                                        summary.getDouble("duration"));
                        routeCache.put(cacheKey,route);
                        main.post(() -> callback.onSuccess(route));
                    } catch (Exception error) {
                        main.post(
                                () ->
                                        callback.onError(
                                                new Exception(
                                                        "Chưa tìm được tuyến đường từ dịch vụ chỉ"
                                                            + " đường.")));
                    }
                });
    }

    public void close() {
        worker.shutdownNow();
    }
}
