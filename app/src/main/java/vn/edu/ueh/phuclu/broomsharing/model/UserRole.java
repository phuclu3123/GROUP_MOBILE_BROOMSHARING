package vn.edu.ueh.phuclu.broomsharing.model;

public enum UserRole {
    GUEST, HOST, ADMIN;

    public boolean canRent() {
        return this == GUEST || this == HOST;
    }

    public boolean canHost() {
        return this == HOST;
    }
}
