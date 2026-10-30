# Responsive điện thoại và tablet

Bản ứng dụng có các layout mặc định, `land`, `sw600dp` và `sw600dp-land` tùy màn hình. Khả năng cuộn, số cột và trạng thái ViewModel được xử lý theo kích thước; không dùng một ảnh điện thoại kéo giãn làm bằng chứng tablet.

Các vòng thao tác đã thực hiện trên Pixel 6 Pro API 31 và Android tablet API 31 x86_64 1920×1200/1200×1920: chuyển tab, cuộn danh sách, mở lịch, tìm/lọc phòng, mở chi tiết và xoay hai chiều. Host có thể chuyển sang chế độ thuê; ảnh giao diện thuê từ tài khoản Host không được ghi thành tài khoản Guest khác.

Đã sửa layout ngang Guest thiếu `activeFilters`, phân bố tab/nút quản trị và vùng cuộn màn Host. Các kiểm tra này bao phủ những màn đã thao tác, không phải nghiệm thu mọi kích thước thiết bị hoặc mọi thao tác ghi. Bản Android cần được chạy riêng trên thiết bị mục tiêu khi thay layout.

Ảnh được chọn cho báo cáo nằm tại `Latex/Resources/chapter5/`. Các ảnh/log thử lặp và nhật ký thiết bị được lưu local trong `_local_archive/`, không đưa lại lên GitHub.
