package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Room implements Serializable {
    public static final String MODE_HOURLY = "HOURLY";
    public static final String MODE_FIXED = "FIXED";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    private long schemaVersion = 1;
    private double pricePerQuarter;
    private String rejectionReason = "";
    private Date updatedAt;

    private String id;
    private String buildingId;
    private String buildingName;
    private String ownerUid;
    private String roomName;
    private String roomType; // Bàn làm việc, Phòng riêng, Phòng họp, Mặt bằng
    private int capacity;
    private double area;
    private String floor;
    private String address;

    private String activeMode; // HOURLY hoặc FIXED
    private double pricePerHour;
    private double pricePerDay;
    private double pricePerMonth;
    private double deposit;

    private String openTime;
    private String closeTime;

    private List<String> amenities;
    private String rules;
    private String approvalStatus; // DRAFT, PENDING_REVIEW, APPROVED
    private String listingStatus; // ACTIVE, INACTIVE, SUSPENDED
    private List<String> imageUrls;
    private List<String> blackoutDates; // Danh sách các ngày khóa lịch YYYY-MM-DD
    private List<Integer> workingDays = new ArrayList<>(java.util.Arrays.asList(1, 2, 3, 4, 5, 6, 7));
    private int slotMinutes = 60;
    private int minBookingMinutes = 60;
    private String fixedStartMonth = "";
    private String renewalPolicy = "";
    private String rentalPolicy = "";
    private String locationVerification = "UNVERIFIED";
    private String photosVerification = "UNVERIFIED";
    public String getLocationVerification() { return locationVerification; }
    public void setLocationVerification(String value) { locationVerification = value; }
    public String getPhotosVerification() { return photosVerification; }
    public void setPhotosVerification(String value) { photosVerification = value; }
    private List<RoomBlackout> blackoutRanges = new ArrayList<>();

    public List<Integer> getWorkingDays() { return workingDays; }
    public void setWorkingDays(List<Integer> value) { workingDays = value; }
    public int getSlotMinutes() { return slotMinutes; }
    public void setSlotMinutes(int value) { slotMinutes = value; }
    public int getMinBookingMinutes() { return minBookingMinutes; }
    public void setMinBookingMinutes(int value) { minBookingMinutes = value; }
    public String getFixedStartMonth() { return fixedStartMonth; }
    public void setFixedStartMonth(String value) { fixedStartMonth = value; }
    public String getRenewalPolicy() { return renewalPolicy; }
    public void setRenewalPolicy(String value) { renewalPolicy = value; }
    public String getRentalPolicy() { return rentalPolicy; }
    public void setRentalPolicy(String value) { rentalPolicy = value; }
    public List<RoomBlackout> getBlackoutRanges() { return blackoutRanges; }
    public void setBlackoutRanges(List<RoomBlackout> value) { blackoutRanges = value; }

    @ServerTimestamp private Date createdAt;

    public Room() {
        this.activeMode = MODE_HOURLY;
        this.approvalStatus = STATUS_DRAFT;
        this.listingStatus = "ACTIVE";
        this.amenities = new ArrayList<>();
        this.imageUrls = new ArrayList<>();
        this.blackoutDates = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public void setBuildingName(String buildingName) {
        this.buildingName = buildingName;
    }

    public String getOwnerUid() {
        return ownerUid;
    }

    public void setOwnerUid(String ownerUid) {
        this.ownerUid = ownerUid;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public double getArea() {
        return area;
    }

    public void setArea(double area) {
        this.area = area;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getActiveMode() {
        return activeMode;
    }

    public void setActiveMode(String activeMode) {
        this.activeMode = activeMode;
    }

    public double getPricePerHour() {
        return pricePerHour;
    }

    public void setPricePerHour(double pricePerHour) {
        this.pricePerHour = pricePerHour;
    }

    public double getPricePerDay() {
        return pricePerDay;
    }

    public void setPricePerDay(double pricePerDay) {
        this.pricePerDay = pricePerDay;
    }

    public double getPricePerMonth() {
        return pricePerMonth;
    }

    public void setPricePerMonth(double pricePerMonth) {
        this.pricePerMonth = pricePerMonth;
    }

    public double getDeposit() {
        return deposit;
    }

    public void setDeposit(double deposit) {
        this.deposit = deposit;
    }

    public String getOpenTime() {
        return openTime;
    }

    public void setOpenTime(String openTime) {
        this.openTime = openTime;
    }

    public String getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(String closeTime) {
        this.closeTime = closeTime;
    }

    public List<String> getAmenities() {
        return amenities;
    }

    public void setAmenities(List<String> amenities) {
        this.amenities = amenities;
    }

    public String getRules() {
        return rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getListingStatus() {
        return listingStatus;
    }

    public void setListingStatus(String listingStatus) {
        this.listingStatus = listingStatus;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
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

    public double getPricePerQuarter() {
        return pricePerQuarter;
    }

    public void setPricePerQuarter(double value) {
        pricePerQuarter = value;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String value) {
        rejectionReason = value;
    }

    @ServerTimestamp
    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date value) {
        updatedAt = value;
    }

    public List<String> getBlackoutDates() {
        if (blackoutDates == null) blackoutDates = new ArrayList<>();
        return blackoutDates;
    }

    public void setBlackoutDates(List<String> blackoutDates) {
        this.blackoutDates = blackoutDates;
    }
}
