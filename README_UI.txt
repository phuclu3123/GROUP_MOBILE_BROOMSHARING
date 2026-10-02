BROOM SHARING - UI PROTOTYPE

Project chỉ tập trung vào giao diện XML. Không có xử lý nghiệp vụ, API, database hay điều hướng giữa các màn hình.

Các màn hình:
1. activity_main.xml                - Trang chủ / khám phá không gian
2. activity_space_detail.xml        - Chi tiết không gian
3. activity_map.xml                 - Bản đồ không gian
4. activity_booking_success.xml     - Xác nhận đặt chỗ + QR tĩnh
5. activity_create_space.xml        - Đăng không gian cho thuê/chia sẻ
6. activity_my_bookings.xml         - Danh sách đặt chỗ
7. activity_messages.xml            - Danh sách trao đổi người thuê/chủ không gian
8. activity_profile.xml             - Hồ sơ + quản lý không gian
9. activity_admin_dashboard.xml     - Dashboard quản trị và duyệt bài

Các Activity Java chỉ có onCreate + setContentView để mở layout; không có logic chức năng.
Ảnh dùng file tĩnh trong res/drawable, lấy từ prototype mà bạn cung cấp.
