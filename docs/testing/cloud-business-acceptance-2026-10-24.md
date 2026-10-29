# Nghiệm thu booking và nghiệp vụ bổ sung trên cloud, 24/10/2026

Nghiệm thu bằng APK Android kết nối Firebase Authentication thật và Firestore database `broomsharing` của project `broom-sharing-firebase-mobile`. Các thao tác nghiệp vụ dùng Java Repository/SDK với ID token của từng vai trò và Rules đã triển khai. Thiết bị chạy là emulator Pixel 4 API 29, không phải điện thoại vật lý. Đây là kiểm tra chức năng booking và các nghiệp vụ liên quan; đăng nhập chỉ là điều kiện để kiểm quyền.

## Kết quả

Kịch bản tích hợp đạt trong 125,264 giây, gồm 18 checkpoint. Đây là một bài kiểm thử tích hợp nhiều bước, không phải 18 bài JUnit độc lập. Sau khi sửa nhãn tiền trên giao diện, một bài kiểm tra chi tiết booking cloud đạt trong 14,123 giây. Build debug/release unsigned thành công; 71 unit test đạt, lint có 0 lỗi và 503 cảnh báo.

| Nhóm nghiệp vụ | Kiểm tra đã đạt trên cloud |
| --- | --- |
| Công bố phòng | Host tạo tòa nhà/phòng, Admin duyệt, Guest đọc phòng công khai. Guest không sửa được giá phòng hoặc tự nâng role. |
| Tạo booking | Hai Guest thật tranh cùng một giờ, chỉ một người giữ được lịch. Repository tính lại giá từ phòng dù đầu vào gửi giá giả. Retry cùng yêu cầu trả ID cũ. |
| Xác nhận, hủy, từ chối | Guest không tự xác nhận; Host xác nhận được. Sửa lịch/counter khi còn booking bị từ chối cả qua Repository lẫn ghi trực tiếp. Hủy/từ chối nhả đúng slot, không nhả nhầm booking liền kề. |
| Tháng/quý | Booking quý giữ ba tháng; đặt tháng trùng bị từ chối. Hủy quý xong đặt lại tháng đã nhả thành công. |
| Hết hạn, check-in, hoàn tất | Listener chuyển booking chờ quá giờ sang `EXPIRED`, giữ lịch sử và audit, nhả lịch/bộ đếm một lần. Host check-in booking FIXED đang trong tháng và hoàn tất lượt đã check-in sau giờ kết thúc. |
| Đánh giá | Guest của booking `COMPLETED` gửi được đánh giá; retry cùng nội dung không nhân bản. Gửi thay người khác, đánh giá booking chưa hoàn tất, sửa/xóa đánh giá đều bị chặn. |
| Báo cáo | Guest gửi, Host phản hồi, Admin kết luận. Người ngoài không phản hồi thay Host; Host không tự đóng hoặc sửa báo cáo đã kết luận. Báo cáo từ booking đã thuê vẫn gửi được khi phòng bị đình chỉ; giả mạo booking của người khác bị từ chối. |
| Chat/thông báo | Tin nhắn retry không tăng sequence hai lần; mốc đọc đúng bên nhận. Người ngoài và Admin không đọc được chat riêng. Hộp cập nhật nhận dữ liệu server; receipt đúng UID được lưu, khác UID bị chặn. |
| Host bị khóa | Màn Host đang mở thu hồi quyền khi Admin khóa. Admin hủy riêng booking chờ/đã xác nhận với audit; không hủy booking `CHECKED_IN`. Host được mở lại sau test. |
| Lịch và giao diện | Nút lưu lịch của booking thật đã xác nhận chuyển đúng thời gian sang Calendar Intent. Review/report giữ dữ liệu sau xoay màn hình; đã xem ảnh booking, review, report, thông báo và màn bị khóa. |

## Dữ liệu thời gian và lỗi phát hiện

Ba booking lịch sử được chuẩn bị riêng để kiểm hết hạn, check-in tháng hiện tại và hoàn tất sau giờ kết thúc. Bảy lần ghi fixture được ghi nhận qua bridge local chỉ cho phép đúng UID/prefix của lượt thử. Không giả thời gian server. Việc tạo các lịch sử này là bước chuẩn bị có quyền quản trị, không được tính là bằng chứng client tạo booking trong quá khứ thành công. Các thao tác chuyển trạng thái, nhả lịch, audit và đánh giá sau chuẩn bị đều chạy qua Repository/Rules cloud thật.

Lượt đầu bộ test đọc file Properties sai encoding, làm tên Host không khớp hồ sơ; Rules đã từ chối. Đã sửa bộ test đọc UTF-8 và chạy lại đạt. Ba snapshot tên/liên hệ Guest của lịch sử chuẩn bị bị kế thừa từ mẫu cũng đã chỉnh cho khớp UID; các booking tạo qua Repository không có lỗi snapshot này. Việc chuẩn bị fixture đã được sửa tương ứng.

Kiểm tra ảnh phát hiện nhãn tổng tiền đang ghi “Trả trước dự kiến”. Đã đổi thành “Tổng tiền thuê và phí” ở cả form booking và màn chi tiết. APK cuối đã kiểm tra lại với tổng 100.000đ và khoản trả trước 40.000đ; dữ liệu tiền không thay đổi. Các log thất bại ban đầu được giữ riêng, không tính là lượt đạt.

Calendar Intent được ActivityMonitor chặn để đối chiếu dữ liệu và kiểm nút thật; không tự ghi một sự kiện vào lịch của người dùng. Khả năng lưu sự kiện trong từng ứng dụng Lịch cần kiểm thêm trên điện thoại dùng để demo.

## Đối chiếu trạng thái cuối và dữ liệu quay video

Đọc lại bằng ID token của Admin demo xác nhận: 4 tài khoản hoạt động, 3 phòng, 12 booking, 6 document lịch, 1 đánh giá và 2 báo cáo. Bốn booking còn giữ chỗ khớp bộ đếm từng phòng; mọi booking đã kết thúc không còn giữ slot/tháng; audit ứng với trạng thái hiện tại đều tồn tại. Bộ trạng thái cuối gồm `PENDING` 2, `CONFIRMED` 1, `CHECKED_IN` 1, `COMPLETED` 1, `EXPIRED` 1, `REJECTED` 1 và `CANCELLED` 5.

Tài khoản đăng nhập thật được giữ lại, không xóa sau test:

| Vai trò | Username |
| --- | --- |
| Guest chính | `video_guest_20261024` |
| Guest thứ hai để thử tranh lịch | `video_other_20261024` |
| Host | `video_host_20261024` |
| Admin | `video_admin_20261024` |

Email, mật khẩu và UID nằm trong file local [video-credentials.local.properties](../../data/demo/video-credentials.local.properties), được Git bỏ qua. Các email `example.com` là danh tính demo thật trên Firebase Auth, không phải địa chỉ nhận thư của khách hàng. Không dùng cohort này để xác minh gửi email.

Phòng công khai `[DEMO] Phòng họp Ánh Dương` còn booking chờ ngày 04/11/2026 14:00 và booking đã xác nhận ngày 05/11/2026 14:00. Guest xem tại Đặt chỗ của tôi; Host có thể duyệt/từ chối/hủy đơn chờ trong video. Lịch sử hết hạn, từ chối, hủy, hoàn tất/đánh giá, báo cáo và chat cũng được giữ. Booking đã được đánh giá không cho đánh giá lần hai.

APK cài thử mới: `output/releases/business-completion-2026-10-24/BroomSharing-business-2026-10-24-debug.apk`. APK trước sửa nhãn được giữ trong thư mục `before-total-label-fix`. Release vẫn unsigned.

Receipt gọn: [cloud-business-2026-10-24.json](evidence/cloud-business-2026-10-24.json). Log, ảnh và snapshot đối chiếu nằm tại `output/verification/cloud-business-2026-10-24/`; ID booking cụ thể ở `video-business-state.json`. Bridge đã dừng, APK test và bản sao mật khẩu tạm trên emulator đã được gỡ. Dữ liệu ngoài cohort được giữ nguyên.

Phạm vi đã đạt là các ca nghiệp vụ trong bảng trên. Vẫn cần nghiệm thu trên điện thoại thực, mạng chập chờn và bản release có ký. Hết hạn hiện chạy khi app đồng bộ/foreground; chưa có job khi mọi app đều đóng. Thanh toán và tích hợp lưu ảnh tiếp tục để sau; không có thu/hoàn tiền thật trong lượt này.
