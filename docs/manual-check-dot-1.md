# Hướng dẫn tự kiểm tra đợt 1

## Chuẩn bị một lần

1. Mở đúng thư mục **BroomSharing** trong Android Studio → Sync Gradle → Run app.
2. `app/google-services.json` phải thuộc project **BRoom-Sharing-Firebase-Mobile**, khớp applicationId `vn.edu.ueh.phuclu.broomsharing`. Không đổi applicationId chỉ để chạy code một thành viên khác.
3. Firebase Console → Authentication → Sign-in method → bật **Email/Password**. Google/Phone/đặt lại mật khẩu và Maps chưa làm trong phạm vi hiện tại.
4. Firestore chọn đúng database **broomsharing**. File `firestore.rules` giữ `true` theo lựa chọn demo. File local không tự đổi Rules trên Console.
5. Storage phải có bucket hoạt động. Xem `storage.rules` cho đường dẫn/quyền/dung lượng; chưa có file nào được deploy tự động. Không dùng giấy tờ thật làm ảnh test.
6. Đăng ký ba tài khoản thử do bạn quản lý: **A** (khách sau này xin Host), **B** (khách khác), **C** (Admin). Cũng có thể dùng emulator/thiết bị khác cho C, hoặc đăng xuất/đăng nhập lần lượt trên một máy.
7. Sau khi C đăng ký thành công, trong Firestore `broomsharing/users/{uid_C}` sửa `role` thành `ADMIN`, giữ `status: ACTIVE`. Đây là bước cấp Admin tin cậy của người quản lý; app không có nút tự đăng ký Admin. Đăng xuất C rồi đăng nhập lại.
8. Nếu query báo chưa tải được, xem Logcat tag `Repository` và kiểm tra index trong Console theo `firestore.indexes.json`. Chưa có index nào được deploy trong lần sửa code.

## 1. Auth

| Làm thử | Kết quả mong đợi |
| --- | --- |
| Đăng ký tài khoản mới | Auth có UID; `users/{uid}` GUEST/ACTIVE và `usernames/{tên_chữ_thường}` cùng UID; không có password trong Firestore |
| Cùng username khác chữ hoa/thường hoặc có khoảng trắng ngoài | Báo tên đã dùng, không tạo hồ sơ mới |
| Email đã có + username mới, kể cả email khác hoa/thường | Báo email đã đăng ký; không tạo alias username mới |
| Bỏ trống lần lượt từng ô | Thông báo đúng ô bắt buộc |
| Username dưới 3/trên 24 ký tự, dấu cách, dấu tiếng Việt, `/`; email sai định dạng | Báo lỗi form, không gửi đăng ký |
| Mật khẩu dưới 6 ký tự; xác nhận trống hoặc không khớp | Báo đúng lỗi; không tự trim mật khẩu |
| Login bằng email, rồi bằng username/chữ hoa | Cùng tài khoản/UID |
| Sai mật khẩu hoặc tài khoản chưa tồn tại | Thông báo chung tài khoản/mật khẩu sai, không mở trang chủ |
| Đóng/mở app khi đang đăng nhập; sau đó đăng xuất và bấm Back | Giữ phiên hợp lệ; sau logout không trở lại màn cần đăng nhập |
| Tắt mạng khi mở trang cần hồ sơ, rồi bật mạng và thử lại | Hiện lỗi kết nối/thử lại; không giả đăng nhập thành công |

## 2. Xin Host và duyệt

1. Đăng nhập A → Hồ sơ → Đăng ký cho thuê. Nhập họ tên, điện thoại 8–15 số, email và mô tả giấy tờ demo.
2. Gửi → trạng thái **PENDING**, A vẫn **GUEST**. Tải lại không được gửi thêm đơn trùng.
3. Đăng nhập C → Duyệt hồ sơ Host → Từ chối, nhập lý do. A mở lại hồ sơ phải thấy **REJECTED + lý do**, được sửa và nộp lại.
4. C duyệt lần tiếp theo → đơn **APPROVED**, user A thành **HOST**, có `auditLogs` ghi hành động. Việc duyệt và nâng role ở cùng transaction.
5. A đăng nhập lại/mở Hồ sơ mới → có menu quản lý Host; vẫn xem danh sách phòng như khách.
6. B vẫn là GUEST, không được tạo phòng/tòa nhà qua app. Không đổi role trực tiếp trên B để “giả lập đã duyệt”.

## 3. Tòa nhà

1. A → Thêm tòa nhà: nhập tên, địa chỉ, quận/huyện, tên người liên hệ/điện thoại, hướng dẫn vào; chọn ảnh mặt tiền nếu có.
2. Tọa độ có thể để trống cả hai. Nhập thiếu một bên, vĩ độ ngoài ±90 hoặc kinh độ ngoài ±180 phải báo lỗi. Không còn tự điền vị trí mẫu. Map picker để sau.
3. Lưu → document có `ownerUid == uid_A`, ID ổn định. Mở lại từ Dashboard, sửa tên → **vẫn cùng document ID, không tăng số tòa nhà**.
4. Thay ảnh mặt tiền → coverUrl mới; kiểm tra path Storage `buildingMedia/{uid_A}/{buildingId}/...`.

## 4. Phòng, giá và ảnh

1. A → Đăng không gian → chọn tòa nhà của A; nhập tên, tầng, địa chỉ, diện tích/sức chứa, tiện ích/nội quy.
2. Chọn **Theo giờ**: nhập giá giờ. Chọn **Theo tháng/quý**: giá giờ vô hiệu, nhập ít nhất một giá tháng/quý. Không lấy giá ngày cũ làm giá tháng.
3. Thử số âm, vượt giới hạn, sức chứa không nguyên, mở cửa ≥ đóng cửa → không lưu sai dữ liệu. Tiền 0–1.000 tỷ, sức chứa ≤10.000, diện tích ≤1.000.000 m²; gửi duyệt cần giá áp dụng/diện tích/sức chứa >0.
4. Chọn 1–6 JPEG/PNG/WebP; thử ảnh >5 MiB hoặc file giả ảnh → báo lỗi. Giữ và kéo để đổi thứ tự; ảnh đầu làm bìa. Xóa/chèn ảnh rồi lưu, mở lại kiểm tra thứ tự cũ/mới.
5. Lưu nháp có thể thiếu chi tiết để đăng, nhưng phải có tên và tòa nhà; chưa có ảnh vẫn được nháp. Gửi duyệt cần đầy đủ trường và ít nhất một ảnh.
6. Storage path phải là `roomMedia/{uid_A}/{roomId}/...`, không có `temp`. Nếu upload/lưu lỗi, đọc thông báo và thử lại; không coi thông báo lỗi là đã lưu.

## 5. Duyệt và công khai

1. Lưu nháp → **DRAFT**. Gửi duyệt → **PENDING_REVIEW**. B mở/tải lại trang chủ: chưa thấy phòng đó.
2. C → Duyệt không gian → từ chối có lý do. A mở lại phòng thấy lý do, chỉnh sửa và gửi lại.
3. C duyệt → **APPROVED**. Nếu `listingStatus == ACTIVE`, B tải lại thấy phòng với dữ liệu/giá thật; mở chi tiết thấy đúng phòng.
4. A sửa phòng đã duyệt rồi lưu nháp/gửi lại → phòng trở về **DRAFT/PENDING_REVIEW**, B tải lại không thấy công khai. Không có nút Host tự duyệt.
5. Thống kê Host: tổng phòng, chờ duyệt, phòng APPROVED/ACTIVE, số booking PENDING có `ownerUid` của A. Không có booking thật thì số yêu cầu bằng 0 là đúng.

## 6. Admin báo cáo và dữ liệu

- Mục Báo cáo đọc `reports` có `status: OPEN`; xử lý thành RESOLVED hoặc bỏ qua DISMISSED, có nhật ký. Nếu chưa có form gửi báo cáo từ Trúc, có thể tự tạo document test trong Console theo schema ví dụ; không kỳ vọng app hiện báo cáo mẫu.
- Giá/ngày cũ không được tự di trú. Đơn Host cũ ID ngẫu nhiên vẫn đọc được, nhưng nên dọn dữ liệu demo trùng sau khi bạn kiểm tra; code không tự xóa dữ liệu cũ.
- Schema/index/sample và điểm ghép nhóm: xem `schema.md`. Các collection chat/payment/verification/booking locks là contract đợt sau, không phải tính năng đã chạy.

## 7. Rules phân quyền tùy chọn

File chính vẫn mở. Để thử file kiểm soát mà không đổi cloud, nếu máy đã có Firebase CLI/Python/Java:

```powershell
firebase emulators:exec --only auth,firestore --project demo-broom-sharing --config firebase.security-test.json "python tests/security/check_rules.py"
```

Script chỉ kết nối `127.0.0.1`, seed dữ liệu trên emulator project demo. Nó kiểm tra: khách không tự thành ADMIN/PAID; Host không sửa phòng người khác/tự duyệt/tự verified; không giả audit; phòng nháp không đọc công khai; chủ được sửa nháp; phòng đã duyệt được đọc công khai.

**Chưa chạy suite Rules trong lượt này.** File kiểm soát đóng các module đợt sau cho đến khi có đầy đủ Rules riêng. Không đổi file chính/deploy chỉ để thử. Public ảnh phòng/tòa nhà là tài sản quảng bá; giấy tờ ở nhánh riêng `verification/{uid}`, không tạo public URL trong code đợt 1.

## Phạm vi xác nhận

Build/unit test/lint chỉ kiểm tra code cục bộ. Bạn cần chạy các bước trên để xác nhận Console, Firebase Auth/Firestore/Storage thật và trải nghiệm thiết bị. Lượt thử Auth trước đã gặp lỗi mạng máy ảo EPERM; không có bằng chứng luồng Firebase thật đã đạt. Không tiếp tục chạy thiết bị/cloud theo yêu cầu mới của bạn.

Kết quả cục bộ ngày 15/10/2026: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` đều thành công. Có **15 unit test, 0 thất bại/0 lỗi**; Lint **0 lỗi, 460 cảnh báo** của code/tài nguyên hiện có. XML, JSON và cú pháp Python của suite Rules đã được kiểm tra. APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

Build dùng Maven cache/file repository tạm trên máy vì vấn đề tải dependency trước đó; không thêm mirror vào cấu hình repository của dự án. Suite thiết bị và suite Rules chỉ được biên dịch/chuẩn bị, chưa được xác nhận chạy đạt.

## Bổ sung kiểm tra số điện thoại

- Form đăng ký có số điện thoại bắt buộc. Thử bỏ trống, chữ, số quá ngắn, ký tự `/`, +0… phải báo lỗi.
- Nhập 0912 345 678 và kiểm tra profile.phone lưu +84912345678, phoneVerified là false. Đây chỉ là kiểm tra định dạng, chưa xác minh SIM và chưa gửi SMS.
- Hồ sơ hiển thị số đã lưu và trạng thái chưa xác minh; form xin Host điền sẵn số liên hệ.
- Email/Password trong Firebase Authentication vẫn cần bật để tạo tài khoản theo luồng hiện tại. Lỗi CONFIGURATION_NOT_FOUND được hiển thị thành hướng dẫn cấu hình Authentication, thay vì lỗi chung.

Xác minh sau bổ sung số điện thoại: build app và APK test thành công; 22 unit test qua, 0 thất bại. Chưa chạy UI/Firebase thật hoặc SMS.
