package vn.edu.ueh.phuclu.broomsharing.model;

public class ReviewItem {
    public java.util.List<String> imageUrls = java.util.Collections.emptyList();
    public final String kind, id, title, details;

    public ReviewItem(String kind, String id, String title, String details) {
        this.kind = kind;
        this.id = id;
        this.title = title;
        this.details = details;
    }
}
