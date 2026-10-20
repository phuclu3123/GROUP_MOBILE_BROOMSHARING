package vn.edu.ueh.phuclu.broomsharing.model;

public class NearbyPlace {
    public final String id,name,type,sourceUrl;
    public final MapPoint point;
    public final double distance;
    public NearbyPlace(String id,String name,String type,MapPoint point,double distance){this.id=id;this.name=name;this.type=type;this.sourceUrl="https://www.openstreetmap.org/"+id;this.point=point;this.distance=distance;}
}
