# Cloudinary cho bản đồ án Android

Ứng dụng giữ Java + XML, MVVM + Repository + Model. Upload dùng HTTPS multipart từ `MediaRepository`, không dùng Spring Boot, API Secret hay Firebase Storage cho tệp mới. Firestore `broomsharing` tiếp tục lưu thông tin khách hàng và nghiệp vụ; Cloudinary lưu file, không phải database có bảng khách hàng.

## Cấu hình một lần

1. Tạo tài khoản Cloudinary, dùng sản phẩm Image & Video APIs và gói Free.
2. Trong Console, lấy **Cloud name** của product environment đang dùng.
3. Mở Settings → Upload → Upload presets, tạo một preset, ví dụ `broomsharing_demo`, với Signing mode là **Unsigned**.
4. Cho phép `jpg`, `jpeg`, `png`, `webp`, `pdf`; giới hạn mỗi file 5 MiB. Dùng Dynamic folders. Không đặt cố định `public_id`, `public_id_prefix`, `asset_folder` hoặc `folder` trong preset; tắt `disallow_public_id` và `use_asset_folder_as_public_id_prefix`, không đặt incoming transformation đổi định dạng. App tự truyền thư mục và public ID đầy đủ để phân biệt Host/tài sản/hồ sơ, tránh bị thêm tiền tố thư mục hai lần.
5. Nếu dùng PDF, trong Product environment Security bật **Allow delivery of PDF and ZIP files**. Gói Free mặc định có thể chặn việc tải PDF. Đây là cấu hình delivery, không phải yêu cầu mua gói trả phí.
6. Nếu preset hỗ trợ cấu hình `return_delete_token=true`, bật để app dọn upload vừa tạo trong giới hạn token. Đây là tham số của preset, không truyền trong unsigned upload request. Không có token thì việc gỡ liên kết trong app vẫn hoạt động, file cần dọn qua Console.

Điền vào `.env` ở thư mục gốc:

```dotenv
CLOUDINARY_CLOUD_NAME=cloud_name_cua_nhom
CLOUDINARY_UPLOAD_PRESET=broomsharing_demo
```

Build/cài lại APK sau khi đổi `.env`. Các giá trị này là cấu hình client công khai và sẽ được nhúng trong APK. **Không cần API Key hoặc API Secret** cho luồng unsigned này. Không thêm secret vào `BuildConfig`, source Java hoặc tài liệu Git.

`.env.example` có hai khóa mẫu; `.env` thật được Git bỏ qua. Chỉ điền `.env` sẽ chưa đủ nếu preset chưa được tạo/config đúng trên Cloudinary. Khi chưa cấu hình, app báo rõ lỗi lúc upload; sửa nội dung và giữ ảnh cũ không bắt buộc có cấu hình media.

## Phân nhóm lưu trữ

| Dữ liệu | Cloudinary public ID/thư mục | Document Firestore |
| --- | --- | --- |
| Ảnh phòng | `broomsharing/roomMedia/{uid}/{roomId}/{file}` | `rooms/{roomId}.imageUrls` |
| Ảnh bìa tòa nhà | `broomsharing/buildingMedia/{uid}/{buildingId}/{file}` | `buildings/{buildingId}.coverUrl` |
| Minh chứng địa điểm/ảnh demo | `broomsharing/verification/{uid}/{verificationId}/{file}` | `propertyVerifications/{verificationId}.evidenceRefs` |
| Ảnh đại diện | `broomsharing/avatarMedia/{uid}/avatar/{file}` | `users/{uid}.photoUrl`, Host đồng bộ `publicHosts/{uid}.photoUrl` |
| Hai mặt giấy tờ giả lập | `broomsharing/hostIdentity/{uid}/{uid}/{file}` | `hostApplications/{uid}.identityImageUrls`, chỉ scope DEMO_PUBLIC |
| Thông tin khách hàng | Không đưa lên Cloudinary | `users/{uid}` và document nghiệp vụ hiện có |

Thư mục được tạo theo lần upload đầu tiên, không cần tạo database Cloudinary hay bảng trùng với Firestore. `MediaAsset` là model kết quả upload, không phải collection mới. Ảnh cũ từ Firebase/nguồn ngoài vẫn được giữ và hiển thị; không tự tải lại hoặc xóa dữ liệu cũ.

## Phạm vi demo và thao tác

- Tệp Cloudinary mới ở chế độ công khai theo URL. Đăng nhập Console chỉ bảo vệ tài khoản quản lý, không làm URL file trở thành riêng tư. Chỉ dùng dữ liệu giả lập cho minh chứng; chưa chuyển giấy tờ IDENTITY thật sang luồng này.
- Phòng: chọn tối đa 6 ảnh, đổi thứ tự hoặc gỡ ảnh khỏi tin. Tòa nhà: chọn ảnh bìa. Upload xong mới lưu document; nếu lỗi thì giữ thông báo lỗi và không giả nhận đã lưu.
- Hồ sơ: chọn tối đa 6 ảnh/PDF, mỗi file tối đa 5 MiB. Nhấn giữ nút chọn để bỏ lựa chọn. Nếu upload đã thành công nhưng Firestore chưa lưu, ViewModel giữ URL trong draft để retry mà không upload lần nữa; có giữ draft khi xoay màn hình.
- Admin: kiểm tra quyền và đường dẫn đúng hồ sơ trước khi mở ảnh/PDF trong màn xem có sẵn. Quyền đọc document Firestore vẫn giữ; URL công khai không được xem là cơ chế bảo vệ file.
- Gỡ ảnh khỏi tin cập nhật URL trong Firestore. Xóa vật lý bằng unsigned delete token chỉ khả thi trong khoảng 10 phút sau upload nếu provider trả token. File cũ hoặc file không có token cần xóa bằng Console; app không tuyên bố đã xóa vĩnh viễn các file đó. Token không được ghi Firestore hoặc log.
- Người biết unsigned preset có thể gọi upload ngoài app. Giới hạn file và định dạng phải đặt cả trên preset. Bản này không cung cấp phân quyền media phía server; sẽ nâng cấp khi ra khỏi phạm vi đồ án.

Ảnh đại diện và đơn Host có thêm hai cách nhập ảnh từ thư viện/URL. [Luồng avatar và xét duyệt Host](profile-host-verification.md). Nhánh hostIdentity chỉ nhận giấy tờ giả lập; không thay đổi việc loại trừ CCCD thật khỏi lưu trữ công khai.

## Nghiệm thu

Unit test kiểm kiểu file, dung lượng và URL đúng cloud/Host/hồ sơ. `CloudinaryContractTest` kiểm parser và HTTP multipart thật qua socket loopback, lỗi 400/429 và redirect; phản hồi provider trong các ca parser là fixture được ghi rõ, không phải chứng cứ upload Cloudinary thật.

Khi đã có cấu hình, chạy `CloudinaryLiveUploadTest` với opt-in `runCloudinaryLive=true` để upload PNG/PDF demo vào tài khoản của nhóm và đọc lại URL. Chưa có cấu hình thì bước nghiệm thu provider thật còn chờ, không tính việc build thành công hoặc test fixture là kết nối Cloudinary thật đã đạt.

Nếu máy ảo/điện thoại đã kết nối, có thể chạy bằng PowerShell từ thư mục dự án:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=vn.edu.ueh.phuclu.broomsharing.CloudinaryLiveUploadTest" "-Pandroid.testInstrumentationRunnerArguments.runCloudinaryLive=true"
```

Rules cho URL minh chứng demo đã được triển khai và xác minh ngày 26/10/2026. [Kết quả kiểm tra và trạng thái triển khai](../testing/cloudinary-media-2026-10-26.md). Bài test thật chỉ upload file demo sinh trên thiết bị, không sửa dữ liệu khách hàng hoặc document Firestore.

Nguồn: [Unsigned uploads](https://cloudinary.com/documentation/client_side_uploading), [tham số Upload API](https://cloudinary.com/documentation/image_upload_api_reference#unsigned_upload_parameters), [PDF và giới hạn delivery](https://cloudinary.com/documentation/image_delivery_options).
