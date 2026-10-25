package vn.edu.ueh.phuclu.broomsharing.model;

import com.google.firebase.firestore.Exclude;

/** Firestore users/{uid}. Passwords are managed exclusively by Firebase Auth. */
/*
    Hồ sơ app gắn cùng UID với Firebase Authentication.
    Role và status phục vụ phân quyền; mật khẩu và token không thuộc model này.
 */
public class UserProfile {
    public long schemaVersion = 1;
    public String uid;
    public String name;
    public String username;
    public String email;
    public String phone = "";
    public boolean phoneVerified = false;
    public String role;
    public String status;

    public UserProfile() { }

    public UserProfile(String uid, String name, String username, String email, String phone) {
        this.uid = uid;
        this.name = name;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.role = UserRole.GUEST.name();
        this.status = "ACTIVE";
    }

    @Exclude
    public UserRole getUserRole() {
        try {
            return UserRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
