package vn.edu.ueh.phuclu.broomsharing.model;



/*
    Địa điểm lân cận dùng để hiển thị tiện ích quanh vị trí được chọn.
    Không đồng nhất địa điểm tham khảo với phòng do Host đăng.
 */
public class NearbyPlace {
    public final String id,name,type,sourceUrl;
    public final MapPoint point;
    public final double distance;
    public NearbyPlace(String id,String name,String type,MapPoint point,double distance){this.id=id;this.name=name;this.type=type;this.sourceUrl="https://www.openstreetmap.org/"+id;this.point=point;this.distance=distance;}
}
