package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.GeoPoint;
import java.util.Date;

/** Public-source map reference, not a Host listing and never bookable. */
/*
    Địa điểm từ nguồn tham khảo, kèm nguồn và giới hạn của tọa độ.
    Không tự bổ sung giá thuê hoặc chủ sở hữu từ dữ liệu này.
 */
public class RentalPlace {
    public String id="",name="",address="",sourceUrl="",locationNote="",mapSourceUrl="",sourceCoordinateLabel="";
    public GeoPoint location;
    public Date checkedAt;
    public RentalPlace(){}
}
