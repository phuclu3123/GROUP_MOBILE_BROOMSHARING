package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.GeoPoint;
import java.util.Date;

/** Public-source map reference, not a Host listing and never bookable. */
public class RentalPlace {
    public String id="",name="",address="",sourceUrl="",locationNote="",mapSourceUrl="",sourceCoordinateLabel="";
    public GeoPoint location;
    public Date checkedAt;
    public RentalPlace(){}
}
