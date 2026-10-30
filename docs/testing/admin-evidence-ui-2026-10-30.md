# Admin: chi tiết hồ sơ và minh chứng

## Phạm vi thay đổi

- Chi tiết hồ sơ dùng bottom sheet có các mục thông tin, vùng cuộn và thanh thao tác riêng.
- Nút xem minh chứng mở nội dung tệp trực tiếp. Hai ảnh hồ sơ Host có tab Mặt trước/Mặt sau; hồ sơ khác có tab theo từng tệp.
- Ảnh hỗ trợ phóng to, kéo và đưa về vừa khung. PDF có điều khiển trang.
- ViewModel giữ dữ liệu tệp qua xoay màn hình và bỏ kết quả tải cũ khi chuyển tệp.
- Chỉ ghi nhận đã xem khi nội dung ảnh/PDF được giải mã và hiển thị thành công. Giữ kiểm tra quyền Admin, quyền đọc tệp và FLAG_SECURE.

## Chọn lọc thay đổi của Hoàng

Đã fetch `origin/lam-hoang` tại commit `4f6aaa3`. Chỉ lấy ba file UI: `HostDashboardActivity.java`, `layout/activity_host_dashboard.xml` và `layout-land/activity_host_dashboard.xml`. Các thay đổi sử dụng NestedScrollView, tắt cuộn lồng của hai danh sách và đưa vị trí cuộn về đầu khi chuyển mục. Không lấy cấu hình IDE/build hoặc thay đổi API/nghiệp vụ.

## Kiểm tra

Lệnh cuối: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`.

Kết quả: BUILD SUCCESSFUL; 146 kiểm thử đơn vị, 0 failure và 0 error; lintDebug hoàn tất. `git diff --check` không báo lỗi khoảng trắng trong các file Admin thay đổi.

Đã thao tác trực tiếp mở chi tiết nhật ký Admin trên Pixel 6 Pro API 31 và quan sát các mục thông tin. Sau đó giới hạn thêm chiều cao nội dung bottom sheet để giữ thanh thao tác trong vùng hiển thị; thay đổi cuối đã build và kiểm tra lại. Người dùng cũng báo đã chạy thử thấy ổn. Không coi những kiểm tra này là bằng chứng đã thử mọi tệp ảnh/PDF, mọi kích thước màn hình hoặc mọi nguồn Storage cũ. Trong lượt này không thay dữ liệu cloud, Rules, index hoặc trạng thái xét duyệt hồ sơ.
