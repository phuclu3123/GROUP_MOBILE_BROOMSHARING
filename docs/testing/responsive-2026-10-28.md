# Responsive và ảnh báo cáo, 28/10/2026

Phạm vi: bố cục điện thoại/tablet, giữ trạng thái khi xoay và các ảnh thuộc phần Phúc trong Chương 5–6. Kiểm tra bắt đầu 28/10, tiếp tục ngày 29/10 bằng thao tác UI trên AVD, không dùng dữ liệu emulator thay cho cloud trong ảnh báo cáo. Không mở rộng nghiệp vụ booking hoặc thay quyền cloud trong lượt này.

## Lỗi đã tái hiện và xử lý

| Vị trí | Bằng chứng và nguyên nhân | Xử lý / tình trạng |
| --- | --- | --- |
| Admin trên điện thoại ngang | Vùng chọn nhóm hẹp, nhãn tab bị cắt và thẻ dọc chiếm chiều cao khiến nút xét duyệt khó truy cập | Đưa tab lên trên, thẻ thông tin/nút thành hai cột. Đã mở hồ sơ và đổi tab trực tiếp |
| Admin trên tablet | Cần phân biệt với ảnh điện thoại xoay ngang | Resource `sw600dp` và `sw600dp-land`; đã kiểm tra tablet 1920×1200/1200×1920, ba tab và nhóm nút hiển thị, giữ tab khi xoay |
| Trang chủ Guest xoay ngang | NPE `MainActivity.updateFilterLabel`: layout ngang thiếu `activeFilters`, trong khi Activity gọi qua ViewBinding. Java vẫn biên dịch do field nullable giữa các variant | Bổ sung đúng TextView, bỏ panel lọc cũ không còn dùng vì hiện sử dụng dialog. Đã mở lại ngang, tìm PAYOS, mở dialog và xoay hai chiều, giữ từ khóa; không thêm null-check để che phần UI bị thiếu |
| Bản đồ ngang | Các nút tìm kiếm xếp dọc phía trên khiến vùng bản đồ thấp | Layout ngang chia rail điều khiển có cuộn và vùng bản đồ; giữ toàn bộ ID và handler nghiệp vụ. Tablet API31 đã tải, xoay, chọn phòng và mở chi tiết. Pixel 6 Pro cũng đã tải 17 phòng, cuộn rail, mở đủ danh sách, chọn phòng và xoay dọc giữ đích; không có crash app trong crash buffer cuối |
| Bản đồ trên Small_Tablet API29 x86 | Crash native SIGSEGV trong `libmaplibre.so`, khác lỗi NPE của Main. Tái hiện cả TextureView và phép thử SurfaceView | Đã hoàn nguyên thử nghiệm renderer vì không giải quyết crash. Trên Broom_Tablet_API31 x86_64, cùng renderer gốc đã tải 17 không gian và xoay dọc/ngang không tái hiện crash trong các bước đã thử. Vẫn giữ lỗi tương thích AVD API29 x86 này là chưa khép, không nói đổi sang 64-bit đã chữa mọi thiết bị |
| Bảng Danh sách của Map | Tablet ngang mở bottom sheet ở trạng thái thu gọn, chỉ thấy tiêu đề; danh sách có chiều cao cố định 360dp | Giới hạn chiều cao theo viewport, RecyclerView dùng phần còn lại và mở thẳng EXPANDED. APK cuối đã mở đủ danh sách, chọn Văn phòng theo tháng, nhận tuyến ô tô 1,63 km, bấm Xem phòng mở đúng chi tiết; quay lại/xoay dọc vẫn giữ đích và tuyến |
| Dashboard Host | Tải GET ban đầu trùng với bộ listener onResume; có thể làm số liệu tạm bị thay bằng kết quả nhánh khác | `resumeDashboard` chỉ gắn khi chưa có listener; tải lại chủ động dùng cùng nguồn snapshot. APK cuối đã tải 8 phòng/1 chờ duyệt/4 hiển thị/2 đơn chờ của Host dataset, giữ tab tòa nhà khi xoay |
| Lịch ngày của Host trên điện thoại ngang | Header, bộ lọc và panel lịch ngày dọc chiếm gần hết chiều cao; thông báo rỗng bị đẩy xuống chạm bottom navigation | Thêm `layout-land/activity_host_bookings`: rail cuộn chứa lọc/lịch ngày, vùng phải dành cho booking; header gọn. Đã mở lại lịch phòng trên Pixel 6 Pro: ngày/khung trống và thông báo rỗng hiển thị đúng hai vùng, không chạm nav |

Các variant layout đã được parse và đối chiếu ID với layout mặc định; không còn ID mặc định bị thiếu trong các variant hiện tại. Đây là kiểm tra tĩnh bổ sung, không thay cho thao tác xoay màn hình.

## Thiết bị và bước đã thử

- Điện thoại Pixel 6 Pro API31: 1440×3120 và 3120×1440. Admin đã kiểm tra thao tác mở thông tin, các tab và nhóm nút ngang. APK cuối đã kiểm tra Host tổng quan, phòng, cuộn đến nút lịch đặt, lịch ngày ngang không đè nav; chế độ thuê mở trang chủ ngang, Map/danh sách/chọn phòng và xoay dọc giữ đích. Các bước này không tương đương nghiệm thu mọi màn/tất cả thao tác ghi.
- Small_Tablet API29 x86: 1920×1200 và 1200×1920, sw600dp; Admin, Guest home và bộ lọc đã kiểm tra. Map native có lỗi nêu trên.
- Broom_Tablet_API31 x86_64: tạo AVD riêng theo cấu hình small_tablet 1920×1200/320dpi với system image Android12 có sẵn, không dùng `wm size` để kéo giãn điện thoại. Đã kiểm tra Host tổng quan, hai tab không gian/tòa nhà, booking, lịch tháng; Map trong chế độ thuê của Host đã tải và xoay được. Chưa dùng kết quả này để nói chạy iPad/iOS hoặc tablet vật lý.

Ảnh Map có tiền tố `user-` mô tả giao diện thuê; tài khoản Host đang chuyển sang chế độ thuê, vì Host được quyền sử dụng chức năng thuê như Guest. Manifest vẫn ghi role HOST đúng tài khoản, không giả định đã đăng nhập bằng Guest khác.

Stacktrace trước sửa nằm trong `output/verification/responsive-2026-10-28/tablet-before-fix-crashes.log`. Hai log `tablet31-after-check.log` và `phone-after-check.log` giữ crash buffer cuối: tablet có lỗi uiautomator lúc hệ thống chưa boot hoàn tất, không có crash app/Map tương ứng; không che hoặc xóa log để ghi kết quả đạt. Ảnh native và nguồn cloud được ghi trong `docs/report/phuc-chapter5-6-captures.json`; ảnh User/Host hỗ trợ nằm ở `Resources/chapter5/members/`. Chỉ giao ảnh màn hoàn chỉnh, không dùng ảnh crash/tải làm minh chứng chức năng đã vận hành.

APK cuối build thành công và 146 unit test Android qua, không failure/error/skipped. Có 8 variant layout được parse và đối chiếu đủ ID với layout mặc định. Bộ kiểm thử backend trước đó là 57 ca qua, tách khỏi nghiệm thu giao diện. Không chạy lại chuyển tiền hoặc sửa trạng thái booking cloud trong lượt responsive.

Tài liệu đối chiếu: [MapLibre textureMode](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map-options/texture-mode.html), [issue tương tự về emulator/rotation](https://github.com/maplibre/maplibre-native/issues/2371). Những issue này chỉ gợi ý hướng cô lập môi trường, không chứng minh cùng nguyên nhân với stacktrace tại dự án.

## Báo cáo

Phần viết giữ đúng 5.1, 5.2, 5.3.8, phần Admin của 5.4 và 6.4. Từng nhóm ảnh có đoạn mô tả gắn cùng hình, không chỉ caption. Main PDF đã được biên dịch/render và xuất đủ toàn bài; bài thành viên còn chờ ghép, không viết thay hoặc coi phần chưa nhận đã hoàn tất.
