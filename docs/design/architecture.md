# Kiến trúc ứng dụng

Ứng dụng sử dụng Java + XML, MVVM + Repository + Model. XML xác định bố cục; Activity nhận thao tác và quan sát LiveData; ViewModel giữ trạng thái; Repository thực hiện I/O và transaction; Model biểu diễn dữ liệu. Các lớp policy trong `utils` kiểm tra lịch, giá, trạng thái và điều kiện nghiệp vụ bằng Java thuần.

Firebase Authentication cung cấp phiên đăng nhập. Firestore database `broomsharing` lưu hồ sơ, không gian, booking, lịch và các chức năng trao đổi/quản trị. Security Rules kiểm quyền trên request Android; việc ẩn nút trong giao diện không thay cho kiểm tra quyền.

`OfflineRepository` truy cập SQLite trên luồng nền. Bản lưu tách theo UID, có thời điểm nhận và chỉ dùng để xem. Khi mất mạng, giao diện đánh dấu dữ liệu đã lưu; khi có mạng, kiểm tra phiên và tải lại dữ liệu. Không xếp hàng thanh toán hoặc thao tác ghi để tự gửi sau.

Bản đồ sử dụng MapLibre Native, MapTiler và openrouteservice. Cloudinary nhận ảnh/tệp DEMO qua unsigned preset; cấu hình client không chứa API secret. Những tệp DEMO công khai không phù hợp để chứa giấy tờ cá nhân thật.

Thanh toán dùng backend Java 17 riêng, HTTP server của JDK, Firebase Admin SDK và SDK payOS. Android gửi booking ID và Firebase ID token; server tự đọc giá/UID và xác minh tiền. Webhook được chuyển tiếp qua WEB_TCC; redirect quay về app không xác nhận tiền. Kênh hiện tại chỉ phục vụ một Host demo, chưa có API hoàn tiền.

Booking mới giữ tạm tối đa 5 phút bằng transaction. Backend xác minh đủ 40% đúng hạn mới xác nhận. Listener cập nhật trạng thái trên Android; đồng hồ tại thiết bị chỉ phục vụ hiển thị. Worker Java chỉ quét khi có việc chờ, không polling Firestore liên tục lúc rảnh. Cần chạy Java/tunnel khi demo thanh toán.

Xem [cấu trúc package](source-structure.md), [mô hình Firestore](firestore.md) và [hướng dẫn backend](../../backend/payments/README.md).
