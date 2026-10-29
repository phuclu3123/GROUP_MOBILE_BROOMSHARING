# Ảnh hỗ trợ phần User/Host

Ảnh native từ app đang nối Firestore cloud, không kéo giãn hoặc dựng giao diện giả. Chưa được chèn vào phần viết của Trúc/Hoàng. Tài khoản Host có thể chuyển sang chế độ thuê; tên file `user-` chỉ giao diện thuê, role và thiết bị thực tế xem manifest `docs/report/phuc-chapter5-6-captures.json`.

| Nhóm tên file | Nội dung có thể mô tả khi sử dụng |
| --- | --- |
| `host-tablet-overview-*` | Bốn chỉ số của tài khoản Host và lối vào nghiệp vụ; nội dung dưới màn hình có thể cuộn. Hai hướng cùng tài khoản, không phải hai bộ dữ liệu |
| `host-tablet-spaces-landscape` | Cột chọn phòng/tòa nhà bên trái và grid phòng bên phải; mỗi thẻ có trạng thái, giá, sửa thông tin và lịch đặt |
| `host-tablet-buildings-portrait` | Tab Tòa nhà được giữ sau khi xoay; danh sách ba địa điểm của Host |
| `host-tablet-bookings-landscape` | Bộ lọc trạng thái và một booking lịch sử của Host; không dùng booking đã hủy làm bằng chứng khách đã sử dụng |
| `host-tablet-calendar-landscape` | Lịch tháng, chọn phòng/tháng, chú giải ngày có giữ chỗ hoặc khóa; nội dung chi tiết ngày có thể cuộn |
| `user-tablet-home-landscape` | Rail tìm kiếm/bộ lọc và danh sách phòng theo grid; vùng danh sách cuộn độc lập |
| `user-tablet-bookings-landscape` | Lịch sử booking hiển thị hết hạn thanh toán và lý do nhả lịch; không phải đặt thành công |
| `user-tablet-map-portrait`, `user-tablet-map-landscape` | Cùng chức năng bản đồ với bố cục theo hai hướng; ngang chuyển các nút sang rail có cuộn để tăng vùng bản đồ |
| `user-tablet-map-results-landscape` | Bảng kết quả mở đầy đủ, danh sách phòng có thể cuộn, phân biệt địa điểm trong app và nguồn tham khảo |
| `user-tablet-map-selected-landscape` | Điểm đến đã chọn, tuyến ô tô từ API và nút Xem phòng mở đúng chi tiết; thời gian là ước tính |
| `host-phone-spaces-landscape` | Thẻ phòng sau khi cuộn, nút Chỉnh sửa/Lịch đặt đều truy cập được ở điện thoại ngang |
| `host-phone-schedule-landscape` | Lịch ngày/bộ lọc cột trái, trạng thái booking cột phải; đã sửa phần nội dung chạm thanh điều hướng |
| `user-phone-map-results-landscape` | Bảng kết quả Map mở rộng trong chiều cao điện thoại ngang, các phòng nằm trong danh sách có thể cuộn |

Các ảnh chỉ nên đưa vào cùng đoạn mô tả màn hình/quy trình tương ứng, không dán liên tục mà thiếu giải thích. Ảnh trang chủ dọc bắt trúng trạng thái tải đã được loại khỏi bộ giao. Kiểm tra giới hạn AVD Android10 x86 tại `docs/testing/responsive-2026-10-28.md`; không gọi ảnh tablet Android là ảnh iPad/iOS.
