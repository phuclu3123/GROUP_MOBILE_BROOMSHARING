package vn.edu.ueh.phuclu.broomsharing.repository;

import android.net.Uri;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import vn.edu.ueh.phuclu.broomsharing.BuildConfig;
import vn.edu.ueh.phuclu.broomsharing.model.MapPoint;
import vn.edu.ueh.phuclu.broomsharing.model.NearbyPlace;
import vn.edu.ueh.phuclu.broomsharing.utils.MapArea;

/** Bounded MapTiler reverse POI lookup using the existing client map key. */
public class NearbyPlacesRepository {
    public static final int RADIUS=1000,MAX_RESULTS=10;
    private static final Map<String,List<NearbyPlace>> cache=new LinkedHashMap<>();
    private static final Map<String,Long> fetched=new HashMap<>();
    private static long lastRequest=-30000;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    public void nearby(MapPoint center,ResultCallback<List<NearbyPlace>> cb){
        if(!MapArea.contains(center)){cb.onError(new Exception("Chọn địa điểm trong TP.HCM."));return;}
        if(BuildConfig.MAPTILER_API_KEY.isEmpty()){cb.onError(new Exception("Chưa cấu hình key MapTiler cho tiện ích."));return;}
        String key=center.latitude+","+center.longitude;
        synchronized(cache){long now=SystemClock.elapsedRealtime();
            if(cache.containsKey(key)&&now-fetched.get(key)<300000){cb.onSuccess(new ArrayList<>(cache.get(key)));return;}
            if(now-lastRequest<30000){cb.onError(new Exception("Chờ 30 giây giữa hai lần tìm tiện ích."));return;}lastRequest=now;}
        worker.execute(()->{
            HttpURLConnection connection=null;
            try{
                String url=Uri.parse("https://api.maptiler.com/geocoding/"+center.longitude+","+center.latitude+".json").buildUpon().appendQueryParameter("types","poi").appendQueryParameter("limit",String.valueOf(MAX_RESULTS)).appendQueryParameter("key",BuildConfig.MAPTILER_API_KEY).build().toString();
                connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent","BroomSharing-Android/1.0");
                if(connection.getResponseCode()!=200)throw new IOException("HTTP_"+connection.getResponseCode());
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] part=new byte[8192];int n;while((n=in.read(part))!=-1){if(bytes.size()+n>524288)throw new IOException();bytes.write(part,0,n);}}
                List<NearbyPlace> result=parse(new JSONObject(bytes.toString("UTF-8")),center);
                synchronized(cache){if(cache.size()>=8){String first=cache.keySet().iterator().next();cache.remove(first);fetched.remove(first);}cache.put(key,result);fetched.put(key,SystemClock.elapsedRealtime());}
                main.post(()->cb.onSuccess(result));
            }catch(Exception e){Log.w("NearbyPlaces","Provider failure: "+e.getClass().getSimpleName()+(e instanceof IOException&&e.getMessage()!=null&&e.getMessage().matches("HTTP_[0-9]{3}")?" "+e.getMessage():""));main.post(()->cb.onError(new Exception("Chưa tải được tiện ích MapTiler. Kiểm tra mạng hoặc hạn mức dịch vụ rồi thử lại.")));}
            finally{if(connection!=null)connection.disconnect();}
        });
    }
    static List<NearbyPlace> parse(JSONObject json,MapPoint center)throws Exception{
        JSONArray elements=json.getJSONArray("features");List<NearbyPlace> list=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(int i=0;i<Math.min(MAX_RESULTS,elements.length());i++){
            JSONObject e=elements.getJSONObject(i),properties=e.optJSONObject("properties"),geometry=e.optJSONObject("geometry");if(properties==null||geometry==null||!"Point".equals(geometry.optString("type")))continue;
            JSONObject tags=properties.optJSONObject("feature_tags");if(tags==null)continue;String type=category(tags);if(type==null)continue;
            String ref=properties.optString("ref");if(!ref.matches("osm:[nwr][1-9][0-9]*"))continue;
            String kind=ref.charAt(4)=='n'?"node":ref.charAt(4)=='w'?"way":"relation",id=kind+"/"+ref.substring(5);
            String name=e.optString("text",type).trim();if(name.isEmpty())name=type;JSONArray coords=geometry.getJSONArray("coordinates");if(coords.length()<2)continue;
            MapPoint point=new MapPoint(coords.optDouble(1,Double.NaN),coords.optDouble(0,Double.NaN),name);double distance=MapArea.distance(center,point);
            if(!MapArea.contains(point)||distance>RADIUS||!ids.add(id))continue;
            list.add(new NearbyPlace(id,name,type,point,distance));
        }
        list.sort(Comparator.comparingDouble(p->p.distance));return new ArrayList<>(list.subList(0,Math.min(MAX_RESULTS,list.size())));
    }
    private static String category(JSONObject t){switch(t.optString("amenity")){case "cafe":return "Cà phê";case "restaurant":case "fast_food":return "Ăn uống";case "parking":return "Bãi đỗ xe";case "bus_station":return "Bến xe";}if("bus_stop".equals(t.optString("highway")))return "Trạm xe buýt";return "convenience".equals(t.optString("shop"))?"Cửa hàng tiện lợi":null;}
    public void close(){worker.shutdownNow();}
}
