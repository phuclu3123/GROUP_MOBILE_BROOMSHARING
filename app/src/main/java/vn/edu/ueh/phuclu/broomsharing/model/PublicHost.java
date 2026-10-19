package vn.edu.ueh.phuclu.broomsharing.model;

/** Public Firestore profile. Email, phone and evidence stay in private documents. */
public class PublicHost {
    public String uid = "";
    public String name = "";
    public String status = "ACTIVE";

    public PublicHost() {}

    public PublicHost(String uid, String name, String status) {
        this.uid = uid;
        this.name = name;
        this.status = status;
    }
}
