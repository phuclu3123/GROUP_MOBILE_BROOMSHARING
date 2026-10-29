# Nghiệm thu avatar và hồ sơ Host ngày 26/10/2026

## Phạm vi

Đã bổ sung avatar từ thư viện/URL cho Guest và Host/Admin, đồng bộ PublicHost, đơn Host có hai ảnh giấy tờ giả lập và vòng Admin từ chối/gửi lại/duyệt. Java + XML, MVVM + Repository + Model được giữ. Picasso hiển thị ảnh; OkHttp thực hiện GET và multipart; JSONObject kiểm phản hồi upload. [Thiết kế và thao tác](../integrations/profile-host-verification.md).

Không tải CCCD của người khác. Fixture `data/demo/host-identity` gồm hình học/chữ ghi rõ DEMO/NOT VALID, không phải giấy tờ định danh. Bản công khai chỉ dùng dữ liệu giả lập và không được coi là eKYC hoặc nơi lưu CCCD thật.

## Kiểm thử đã có kết quả

| Kiểm tra | Kết quả |
| --- | --- |
| Unit Java | 122 test, 0 thất bại/lỗi/bỏ qua |
| CloudinaryContractTest trên Android API 29 | 15 test đạt sau khi chuyển multipart sang OkHttp; kiểm request, JSON phản hồi, lỗi 400/429 và redirect |
| Rules avatar + đơn Host | 37 ca đạt trong database emulator riêng `profile_identity_qa` |
| Hồi quy Rules | 34 ca minh chứng Cloudinary, 14 ca hồ sơ cũ, 44 ca booking đều đạt |
| Lint debug | 0 lỗi, 602 cảnh báo của dự án tại lượt kiểm tra |
| Build | Debug, AndroidTest và release unsigned thành công |
| Tài khoản cloud thật | `ProfileIdentityAcceptanceTest.realAvatarAndHostReviewLifecycle`: OK (1 test), 43,024 giây |
| UI Admin và form Host | `actualAdminEvidenceScreensAndHostForm`: OK (1 test), 141,846 giây sau cold boot AVD; Admin mở đủ hai ảnh, FLAG_SECURE bật, trả đúng kết quả đã xem; lựa chọn ảnh còn sau recreate |

## Nghiệm thu cloud thật

Tạo tài khoản demo mới qua `FirebaseAuthRepository.register()`, không nâng role tài khoản thành viên hoặc tài khoản demo đã có. File thông tin đăng nhập được giữ local và Git bỏ qua: `data/demo/profile-identity-credentials.local.properties`.

1. Tài khoản mới ở role GUEST upload avatar từ URI MediaStore thật. Firestore lưu đúng URL; màn ProfileActivity hiển thị bitmap bằng Picasso và chụp screenshot.
2. Đổi avatar bằng HTTPS URL: OkHttp tải nội dung từ Cloudinary thật rồi upload vào avatarMedia đúng UID. Thử gỡ ảnh lưu chuỗi rỗng, thêm lại và từ chối URL của UID khác.
3. Thiếu hai mặt bị từ chối. Với mặt trước từ MediaStore và mặt sau từ HTTPS URL, upload/lưu đơn PENDING thành công, role vẫn GUEST. Thử ghi đè đơn đang chờ bị chặn.
4. Tài khoản Guest khác không đọc được đơn. Repository không cho Admin duyệt khi thông tin xem đủ ảnh chưa được cung cấp. Admin từ chối kèm lý do; người nộp gửi lại và giữ nguyên URL đã upload, không upload lần nữa.
5. Admin tải và giải mã được cả hai mặt giả lập từ URL đúng hồ sơ. Duyệt cập nhật đơn APPROVED, identityReviewedAt, role HOST, PublicHost và audit trong transaction.
6. Host mới thay avatar bằng URL; `users.photoUrl` khớp `publicHosts.photoUrl`. Màn RoleAccountActivity hiển thị ảnh bằng Picasso.

Các ca cloud dùng provider thật, không dùng fixture transport. Fixture chỉ là nội dung tệp giả lập; thao tác đăng ký, upload và ghi/đọc dữ liệu đều thực. Không kiểm chứng căn cước với cơ quan hay dịch vụ nhận dạng nào.

[Biên bản cloud](evidence/profile-identity-cloud-2026-10-26.json), [37 ca Rules](evidence/profile-identity-rules-2026-10-26.json), [triển khai Rules](evidence/profile-identity-rules-deployment-2026-10-26.json) và [tổng hợp kiểm thử/APK](evidence/profile-identity-2026-10-26.json) phân biệt rõ các loại chứng cứ. Avatar đã được xem bằng screenshot ở `output/verification/profile-identity-2026-10-26/guest-avatar.png` và `host-avatar.png`. Màn xem giấy tờ bật FLAG_SECURE nên nghiệm thu bằng bitmap được giải mã và Activity result, không tắt cơ chế này để chụp ảnh.

APK dùng thử: `output/releases/profile-identity-2026-10-26/BroomSharing-profile-identity-debug.apk`. Bản release unsigned ở cùng thư mục cần ký trước khi phân phối. Chưa push phần media/avatar/hồ sơ trong lượt này.

## Triển khai Rules

Người dùng đã phê duyệt triển khai Rules avatar và đơn Host mới. Release áp dụng cho project `broom-sharing-firebase-mobile`, database `broomsharing`, ruleset `1f2343de-4bbd-4399-958b-1eb807056f05` lúc 19:14:46 ngày 26/10/2026 (UTC+7). Nguồn đọc lại khớp SHA-256 `39d201e0c8e8518c0f703de4037ed0fb471d77a26e61199293f5502ccefed7ea`.

Script triển khai chỉ thay Rules, không ghi document và không sửa index; bảy index vẫn READY. Document mới chỉ được tạo bởi bài nghiệm thu app được mô tả riêng bên trên. Quyền Host đã cấp trước đó không bị thu hồi. Đơn Host mới phải có hai ảnh DEMO; đơn cũ thiếu ảnh cần bị từ chối với lý do bổ sung rồi gửi lại. Cần cài APK mới cùng đợt.

## Giới hạn và tài liệu

- UI đánh dấu ảnh đã mở sau khi giải mã thành công; Rules kiểm role/contract/transaction, không tự biết Admin đã nhìn ảnh bằng mắt.
- URL Cloudinary hiện là công khai. Không dùng file thật hoặc giấy tờ của người khác. Firestore giữ quyền riêng cho đơn nhưng không biến URL công khai thành tệp riêng tư.
- Gỡ URL khỏi hồ sơ không bảo đảm xóa file provider nếu không có delete token. Tệp demo giữ lại cho nhóm quay video, có thể dọn bằng Console sau.
- Không khẳng định đã tự động kiểm thử thao tác chọn file ở mọi phiên bản Android. Luồng cloud đã dùng URI content thật; kiểm thử trên AVD Android API 29.

Log local: `tmp/profile-identity-final-build.log`, `tmp/profile-cloudinary-contract-device.log`, `tmp/profile-identity-cloud-device.log`, `tmp/profile-identity-rules-deployment.log`. Biên bản tổng hợp và APK được lưu cùng phần evidence/release của lượt này.
