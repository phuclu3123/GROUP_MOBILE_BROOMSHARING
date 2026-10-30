package vn.edu.ueh.phuclu.broomsharing;

import android.app.Application;

import vn.edu.ueh.phuclu.broomsharing.repository.OfflineRepository;

// Chỉ giữ Application Context; SQLite được mở trên luồng nền khi cần đọc/ghi.
public class BroomApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        OfflineRepository.initialize(this);
    }
}
