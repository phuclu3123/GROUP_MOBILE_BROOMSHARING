package vn.edu.ueh.phuclu.broomsharing.model;

import java.util.Collections;
import java.util.List;

/*
    Dữ liệu gom các hồ sơ chờ duyệt cho màn hình Admin.
    Không có collection ReviewItem và không phải nhận xét sau booking.
 */
public class ReviewItem {
    public List<String> imageUrls = Collections.emptyList();
    public final String kind, id, title;
    public String details;
    public String status = "";
    public List<String> evidenceRefs = Collections.emptyList();

    public ReviewItem(String kind, String id, String title, String details) {
        this.kind = kind;
        this.id = id;
        this.title = title;
        this.details = details;
    }
}
