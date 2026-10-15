BROOM SHARING - UI PROTOTYPE

Project có prototype giao diện XML và module Auth mới. Xem docs/auth-dot-1.md để biết phạm vi và cách kết nối Firebase.

Màn hình khởi động: ui/auth/LoginActivity.
Đã thêm UI Đăng nhập / Đăng ký / Quên mật khẩu, điều hướng Auth, validation và luồng Firebase Auth + Firestore cho username hoặc email + mật khẩu.
Chưa có google-services.json thì xem được UI nhưng không đăng nhập/đăng ký thật.
Google, số điện thoại và gửi email quên mật khẩu xử lý sau.

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

MainActivity, ProfileActivity, chi tiết phòng, các màn Host và Admin kiểm tra phiên/role. Trang chủ/chi tiết đọc phòng APPROVED + ACTIVE; Host/Admin đã nối ViewModel và Repository. Booking, Maps và Messages còn thuộc các đợt tiếp theo.
Ảnh phòng/tòa nhà có picker và Firebase Storage; drawable chỉ làm placeholder. Hướng dẫn tự chạy: docs/manual-check-dot-1.md; phạm vi: docs/integration-dot-1.md.

Guest Trúc: RoomViewModel + RoomRepository + RoomListing dùng chung Room/Building; tìm kiếm và lọc các trang đã tải, phân trang 20 document, card có nguồn nhãn duyệt, chi tiết thật và layout ngang. Xem docs/integration-truc.md; không đồng nghĩa các luồng Firebase/thiết bị đã được kiểm tra thực tế.
