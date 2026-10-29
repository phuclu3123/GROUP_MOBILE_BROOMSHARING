package vn.edu.ueh.phuclu.broomsharing.model;

/** Public Firestore profile. Email, phone and evidence stay in private documents. */
/*
   Bản chiếu tối thiểu tên và trạng thái của Host để người thuê đọc.
   Không chứa email/số điện thoại riêng; trạng thái phải đồng bộ khi Admin khóa Host.
*/
public class PublicHost {
    public String uid = "";
    public String name = "";
    public String status = "ACTIVE";
    public String photoUrl = "";

    public PublicHost() {}

    public PublicHost(String uid, String name, String status) {
        this(uid, name, status, "");
    }

    public PublicHost(String uid, String name, String status, String photoUrl) {
        this.uid = uid;
        this.name = name;
        this.status = status;
        this.photoUrl = photoUrl == null ? "" : photoUrl;
    }
}
