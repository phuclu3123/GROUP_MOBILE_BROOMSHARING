package vn.edu.ueh.phuclu.broomsharing.model;

public class BookingNotice {
    public final String id, title, body, bookingId;
    public boolean forHost;
    public BookingNotice(String id,String title,String body,String bookingId){this.id=id;this.title=title;this.body=body;this.bookingId=bookingId;}
}
