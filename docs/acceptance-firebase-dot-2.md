# Nghiệm thu tích hợp Firebase Đợt 2 — 19/10/2026

Biên bản này ghi lượt Host/Phúc trước khi ghép Trúc. Lượt kiểm tra Guest sau đó nằm trong `review-truc-dot-2.md`; các file kết quả tạm trong `build/acceptance/` được dùng lại cho lượt mới, không phải bản lưu bất biến của lượt cũ.

Phạm vi: Firebase Auth thật và Firestore thật của project `broom-sharing-firebase-mobile`, database `broomsharing`; 10 callable chạy trên Functions Emulator tại máy. Không nâng Blaze, không triển khai Functions hoặc thay rules đang chạy trên cloud.

## Kết quả service

17 ca tích hợp đã qua:

- Từ chối yêu cầu chưa đăng nhập; Guest không được duyệt phòng.
- Admin duyệt phòng đang chờ; đọc thông tin Host chỉ trả dữ liệu công khai.
- Báo giá dùng giá phía service.
- Hai Guest thật tranh cùng khung giờ: chỉ một booking thành công.
- Gửi lại cùng mã yêu cầu trả cùng booking.
- Guest không được xác nhận thay Host.
- Host xác nhận; Firestore lưu giá đúng và `DEMO_PENDING`.
- Guest hủy, nhả lịch; người khác đặt lại được.
- Host từ chối phải có lý do.
- Admin khóa phòng; Host không tự mở lại được; gỡ khóa trả về ngừng rao.
- Host sửa phòng và gửi duyệt lại; Admin duyệt được.
- Khóa/mở Host cập nhật vòng đời phòng và thông tin công khai.
- Thuê tháng và quý tranh kỳ thuê: chỉ một yêu cầu thành công.
- Guest không được yêu cầu xem bằng chứng riêng.
- Đối chiếu `updateTime` xác nhận dữ liệu có sẵn không thay đổi.

Nhật ký chi tiết: `build/acceptance/live-checks.log`. Harness: `functions/test/live-acceptance.cjs`. Không chạy bộ integration của project demo lên Firebase thật.

## Android

Máy ảo Pixel 4, bản Debug bật `-PacceptanceFunctionsHost=10.0.2.2`. Bài `LiveBookingAcceptanceTest` dùng Firebase Android SDK để đăng nhập Guest/Host/Admin thật, báo giá/tạo booking, tải danh sách đặt chỗ của Guest và Host, xác nhận booking, đọc lại trạng thái từ server, khóa/gỡ khóa phòng và tải audit của Admin.

Kết quả lần chạy cuối: **1 test qua**, 109,537 giây. Một lần chạy lại trước đó gặp timeout khi Functions Emulator nạp lại source; đã khởi động lại với `FUNCTIONS_DISCOVERY_TIMEOUT=60` rồi chạy lại thành công. Đây không phải phép đo hiệu năng production.

Ảnh chụp được lưu trong `build/acceptance/android-guest.png` và `android-host.png`. Đây là kiểm tra tích hợp SDK và màn hình danh sách; không phải tự động bấm hết mọi form, kiểm thử mọi kích thước màn hình hoặc nghiệm thu toàn bộ UX.

Cấu hình emulator được gom tại `FirebaseConfig.functions()`. Mặc định host rỗng; chỉ bản Debug có thể bật bằng Gradle property. HTTP cục bộ chỉ được cho phép trong manifest Debug với các địa chỉ localhost/Android Emulator.

Kiểm tra bàn giao sau khi trả về cấu hình mặc định: `assembleDebug`, `testDebugUnitTest` (32 test, không lỗi), `lintDebug` đều qua. Đã xác nhận `FUNCTIONS_EMULATOR_HOST = ""` trong BuildConfig mặc định. Máy ảo và Functions Emulator dùng cho lượt thử đã được tắt.

## Giới hạn đã xác định

- Bucket Storage cấu hình trong project trả HTTP 404 khi kiểm tra bằng tài khoản hiện tại. Chưa nghiệm thu đọc ảnh/PDF bằng chứng từ Storage thật và màn hình preview. Không tạo bucket hoặc đổi billing.
- Rules mới chưa được triển khai. Quyền service đã được kiểm tra; bảo vệ client trực tiếp trên rules mới chỉ có bằng chứng từ bộ test Firestore Emulator trước đó.
- Chưa kiểm chứng Functions được host trên cloud, IAM/runtime Node 22 thực tế, độ trễ/khả năng chịu tải hoặc luồng thanh toán thật. Emulator dùng Node 25 trên máy.
- Check-in/hoàn tất theo thời gian đã có test emulator; lần nghiệm thu thật này dùng lịch tương lai, không sửa đồng hồ hoặc bóp méo thời gian để chuyển trạng thái.

## Dữ liệu thử và chạy lại

Harness tạo 4 tài khoản Auth và các document mang mã `acceptance_...`. Trước khi tạo, nó ghi danh sách đường dẫn và `updateTime` của dữ liệu có sẵn. Cleanup chỉ xóa document thuộc UID/phòng của lượt thử, không gọi xóa collection hoặc `recursiveDelete`. Tài khoản thử cũng được xóa; sau đó so sánh lại dữ liệu có sẵn.

Cleanup thực tế đã hoàn tất: xóa **49 document thử và 4 tài khoản Auth**; đối chiếu lại **6 document có sẵn**, đường dẫn và `updateTime` giữ nguyên. Lượt thử: `acceptance_1792389985332`. Emulator Functions được dừng sau nghiệm thu.

Manifest chứa thông tin đăng nhập tạm chỉ nằm trong thư mục `build/` bị Git bỏ qua; không gửi hoặc commit manifest. Sau cleanup, mật khẩu/token của tài khoản thử được bỏ khỏi manifest. Kết quả không chứa mật khẩu/token nằm ở `build/acceptance/results.json`.

Trình tự chạy thủ công khi cần nghiệm thu lại:

1. Đăng nhập Firebase CLI hiện có.
2. Chạy `functions/node_modules/.bin/firebase.cmd emulators:start --only functions --project broom-sharing-firebase-mobile --config firebase.acceptance.json`.
3. Chạy `node functions/test/live-acceptance.cjs prepare`, rồi `verify` và tùy chọn `evidence`.
4. Build `assembleDebug assembleDebugAndroidTest` với `"-PacceptanceFunctionsHost=10.0.2.2"`; chạy máy ảo Android rồi `node functions/test/run-android-acceptance.cjs`.
5. Luôn chạy `node functions/test/live-acceptance.cjs cleanup` sau khi hoàn tất, kể cả khi test lỗi; kiểm tra kết quả cleanup trước khi kết luận.

Không thể kết luận toàn bộ Đợt 2 đã nghiệm thu production chỉ từ kết quả này. Luồng booking/Admin chính đã có bằng chứng tích hợp với Auth/Firestore thật; phần triển khai cloud, rules mới và bằng chứng Storage còn giới hạn nêu trên.
