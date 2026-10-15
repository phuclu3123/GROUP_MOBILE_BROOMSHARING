# Tích hợp đợt 1 — Auth, Host và Admin

Bản tích hợp đợt 1 gồm Auth, Host/Admin từ nhánh Hoàng và phần guest ghép chọn lọc từ Trúc e6ebd15. Nhánh chuẩn bị ban đầu: `integration/dot-1`; phát hành qua `phuc` rồi `main`. Xem integration-truc.md.

## Đã bổ sung code

| Mục | Kết quả |
| --- | --- |
| H01 | Đơn Host theo UID, chặn đơn trùng, xem trạng thái/lý do, nộp lại sau từ chối; duyệt cập nhật role cùng transaction |
| H02 | Dashboard query phòng/tòa nhà/booking theo chủ; tổng/chờ duyệt/hoạt động/yêu cầu booking, có lỗi và nút tải lại |
| H03 | Tòa nhà có ảnh mặt tiền, cập nhật đúng ID cũ, kiểm tra chủ và dữ liệu liên hệ |
| H04 | Phòng bắt buộc thuộc tòa nhà của chủ; loại, tầng, diện tích/sức chứa, tiện ích/nội quy |
| H05 | Chọn HOURLY/FIXED; giá giờ/tháng/quý, cọc và giới hạn; không suy mode hoặc dùng giá ngày như giá tháng |
| H06 | Document picker, kiểm tra file ảnh thật JPEG/PNG/WebP ≤5 MiB, tối đa 6, kéo thứ tự/ảnh bìa, path UID + room ID, giữ gallery cũ |
| H07 | Bỏ tọa độ mẫu; nhập tùy chọn có validation và GeoPoint. Map/API để sau |
| H08 | Nháp/gửi duyệt, duyệt/từ chối/lý do; sửa phòng đã duyệt phải duyệt lại; danh sách/chi tiết công khai lọc APPROVED/ACTIVE |
| P01 | Dùng JSON sẵn có, database broomsharing thống nhất; phần Console do Phúc kiểm tra |
| P02 | MVVM + Repository + Model gọn; xem architecture.md |
| P03–P04 | Giữ Auth username/email, bổ sung lỗi ô trống, chặn màn Host/Admin và kiểm tra quyền trước ghi; HOST vẫn thuê được |
| P05/P09 | Schema có version, ví dụ/indexes, Booking và status chung; dùng POJO làm DTO, ResultCallback/UiState, Timestamp UTC/hiển thị VN |
| P06 | Giữ Rules chính true theo xác nhận. Có firestore.secure.rules riêng và suite thử trên emulator; chưa bật/deploy/chạy suite |
| P07 | MediaRepository và storage.rules; ảnh công khai tách đường dẫn giấy tờ riêng; chưa upload bằng chứng thật |
| P08 | Hoãn SDK/API bản đồ theo yêu cầu |
| P10 | Admin chọn duyệt Host/phòng/báo cáo, phản hồi, auditLogs và kiểm tra role; bỏ số liệu admin mẫu |

## Chưa xác nhận hoàn thành trên cloud

Code cục bộ không chứng minh Auth/Firestore/Storage Console đã bật đúng hoặc toàn bộ luồng thiết bị đã chạy đúng. Lượt Auth thật trước gặp lỗi mạng máy ảo EPERM; sau đó Phúc yêu cầu tự kiểm tra sau, nên không tiếp tục chạy thiết bị/cloud.

Hướng dẫn kiểm tra: **manual-check-dot-1.md**. Schema và phần thuộc đợt booking sau: **schema.md**. Không làm booking engine, thanh toán, Maps, Google/Phone/reset mật khẩu ; phần guest Trúc đã được tích hợp riêng theo integration-truc.md.

Rules true là chế độ demo, không đáp ứng kiểm soát phía server của P06 khi đang bật true. File secure đóng các module đợt sau chưa hoàn thiện Rules; không coi đó là bộ Rules production đầy đủ cho cả 4 đợt.

## Xác minh cục bộ

`assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` thành công. 15 unit test qua; Lint 0 lỗi/460 cảnh báo. Chi tiết và cách tự thử tại manual-check-dot-1.md. Suite thiết bị và Rules emulator chưa chạy ở lượt hoàn thiện này.

Bổ sung Auth: đăng ký có số điện thoại bắt buộc; chuẩn hóa VN sang +84, lưu phone và phoneVerified=false, hiển thị ở hồ sơ và điền sẵn form Host. Chưa gửi OTP/chưa dùng phone để đăng nhập; chưa áp tính duy nhất cho số liên hệ chưa xác minh. Lỗi cấu hình Auth CONFIGURATION_NOT_FOUND đã có thông báo riêng.

Xác minh sau bổ sung số điện thoại: build app và APK test thành công; 22 unit test qua, 0 thất bại. Chưa chạy UI/Firebase thật hoặc SMS.
