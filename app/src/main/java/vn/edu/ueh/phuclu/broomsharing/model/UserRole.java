package vn.edu.ueh.phuclu.broomsharing.model;



/*
    Ba vai trò ứng dụng trong users.role, không phải ba collection người dùng.
    Host vẫn có thể thực hiện nghiệp vụ thuê phòng theo Rules.
 */
public enum UserRole {
    GUEST, HOST, ADMIN;

    public boolean canRent() {
        return this == GUEST || this == HOST;
    }

    public boolean canHost() {
        return this == HOST;
    }
}
