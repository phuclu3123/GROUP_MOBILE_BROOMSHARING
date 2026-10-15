package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.util.Date;

public class Building implements Serializable {
    private long schemaVersion = 1;
    private boolean locationSet;
    private Date updatedAt;
    private String id;
    private String ownerUid;
    private String name;
    private String address;
    private String district;
    private String contactPhone;
    private String contactName;
    private double latitude;
    private double longitude;
    private String directionGuide;
    private String coverUrl;
    private String status;

    @ServerTimestamp private Date createdAt;

    public Building() {
        this.status = "ACTIVE";
    }

    public Building(
            String ownerUid,
            String name,
            String address,
            String district,
            String contactPhone,
            double latitude,
            double longitude,
            String directionGuide) {
        this.ownerUid = ownerUid;
        this.name = name;
        this.address = address;
        this.district = district;
        this.contactPhone = contactPhone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.directionGuide = directionGuide;
        this.status = "ACTIVE";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerUid() {
        return ownerUid;
    }

    public void setOwnerUid(String ownerUid) {
        this.ownerUid = ownerUid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String value) {
        contactName = value;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getDirectionGuide() {
        return directionGuide;
    }

    public void setDirectionGuide(String directionGuide) {
        this.directionGuide = directionGuide;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @ServerTimestamp
    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public long getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(long value) {
        schemaVersion = value;
    }

    public com.google.firebase.firestore.GeoPoint getLocation() {
        return (locationSet || latitude != 0 || longitude != 0)
                        && Double.isFinite(latitude)
                        && Double.isFinite(longitude)
                        && Math.abs(latitude) <= 90
                        && Math.abs(longitude) <= 180
                ? new com.google.firebase.firestore.GeoPoint(latitude, longitude)
                : null;
    }

    public void setLocation(com.google.firebase.firestore.GeoPoint value) {
        locationSet = value != null;
        if (value != null) {
            latitude = value.getLatitude();
            longitude = value.getLongitude();
        }
    }

    @ServerTimestamp
    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date value) {
        updatedAt = value;
    }

    @Override
    public String toString() {
        return name != null ? name : "Tòa nhà chưa đặt tên";
    }
}
