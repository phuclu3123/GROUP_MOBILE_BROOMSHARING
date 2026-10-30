# Firestore

Database chính: `broomsharing`, project `broom-sharing-firebase-mobile`. Cấu hình Rules/index nằm ở root và được tham chiếu trong `firebase.json`.

| Nhóm | Đường dẫn |
| --- | --- |
| Tài khoản | `users/{uid}`, `usernames/{username}`, `publicHosts/{uid}` |
| Hồ sơ Host | `hostApplications/{uid}`, `propertyVerifications/{id}` |
| Không gian | `buildings/{id}`, `rooms/{id}` |
| Booking và lịch | `bookings/{id}`, `availabilityMonths/{roomId_yyyyMM}` |
| Chat | `conversations/{guestUid:hostUid}/messages/{messageId}` |
| Yêu thích và đã đọc | `users/{uid}/favorites/{roomId}`, `users/{uid}/noticeReads/{eventId}` |
| Đánh giá/báo cáo/nhật ký | `reviews/{bookingId}`, `reports/{id}`, `auditLogs/{id}` |
| Thanh toán | `payments/{bookingId}`, `paymentOrders/{orderCode}`, `appConfig/payments` |
| Tham khảo bản đồ | `rentalPlaces/{id}` |

ID liên kết được lưu bằng chuỗi. Map/array nhúng không phải subcollection; các liên kết logic không phải khóa ngoại tự động. Tiền và lịch sử giá được đọc từ snapshot booking, không tự tính lại bằng giá phòng mới.

## Giữ lịch và trạng thái

Mỗi phòng có một `activeMode`: HOURLY hoặc FIXED. Thuê giờ dùng khóa kỹ thuật 30 phút; thuê cố định giữ trọn một hoặc ba tháng. Host chỉ đổi chế độ/cấu hình lịch khi không còn booking giữ chỗ và bộ đếm đã được đối soát.

Transaction tạo booking ghi đồng thời booking, lịch tháng, revision/bộ đếm phòng và audit. Yêu cầu cạnh tranh phải kiểm lại trên cùng lịch trong transaction; báo giá phía client không đặt trước chỗ.

Booking mới dùng `paymentFlow=INSTANT_5M`, trạng thái PENDING giữ tạm tối đa 5 phút và không vượt giờ bắt đầu. Backend xác nhận đủ 40% đúng hạn mới chuyển CONFIRMED. Khi chưa trả và hết hạn, backend đối soát QR rồi ghi EXPIRED và nhả đúng khóa. Booking và nhật ký vẫn được giữ. Backend dừng thì công việc đối soát chờ tới khi Java chạy lại.

Lượt thuê đã xác nhận đi qua CHECKED_IN và COMPLETED theo thao tác hợp lệ của Host. NO_SHOW phân biệt khách không sử dụng; TERMINATED dùng cho xử lý ngoại lệ đúng quyền. Đánh giá cần xác nhận sử dụng và trạng thái đủ điều kiện, không chỉ dựa vào ngày kết thúc.

Hủy booking và hoàn tiền là hai thao tác khác nhau. Phiên bản hiện tại chưa có API hoàn tiền tự động; khoản cần xử lý được ghi nhận để đối soát, không đánh dấu đã hoàn. Booking lịch sử có cơ chế tương thích riêng; không tự nâng schema hoặc tạo xác nhận sử dụng giả.

## Quyền và kết nối

Rules kiểm UID, role/status, quan hệ sở hữu, dữ liệu bất biến và các write liên quan trong transaction. Backend Admin SDK sử dụng quyền server, vì vậy phải tự kiểm Firebase ID token, người thuê, Host demo, booking và số tiền. Android không được tự ghi PAID hoặc xác nhận hoàn tiền.

Thông báo trong app được dẫn xuất từ booking/hội thoại/báo cáo; receipt đã đọc ở noticeReads. Notification điện thoại là thông báo cục bộ theo trạng thái ứng dụng, không phải FCM. SQLite là bản xem local theo UID và không cấp thêm quyền trên cloud.
