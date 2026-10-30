# BroomSharing

Ứng dụng Android chia sẻ không gian làm việc, xây dựng bằng **Java + XML, MVVM + Repository + Model**. Firebase Authentication quản lý đăng nhập; Cloud Firestore lưu dữ liệu nghiệp vụ trong database `broomsharing`.

## Thành phần

| Thư mục | Nội dung |
| --- | --- |
| `app/` | Ứng dụng Android, tài nguyên XML và các bộ kiểm thử Java |
| `backend/payments/` | HTTP server Java 17, Firebase Admin SDK và payOS |
| `tools/payments/` | Ba script PowerShell chạy, khởi động lại server và kết nối webhook relay |
| `tools/dataset/` | Hai công cụ Java cho dữ liệu demo và địa điểm tham khảo |
| `data/` | Dữ liệu demo, ảnh minh họa và nguồn ảnh |
| `docs/` | Hướng dẫn kiến trúc, tích hợp và kiểm tra bản cuối |
| `Latex/` | Nguồn báo cáo và bản `main.pdf` |

## Chạy Android

Mở dự án bằng Android Studio. Chuẩn bị `app/google-services.json`, `local.properties` và root `.env` theo [.env.example](.env.example). Những tệp cấu hình riêng và khóa server không được commit.

```powershell
.\gradlew.bat :app:assembleDebug
```

APK được tạo tại `app/build/outputs/apk/debug/app-debug.apk`. Các giá trị `.env` của Android được đọc khi build; sau khi thay URL backend cần build và cài lại app. Mặc định kết nối Firebase thật. Chỉ bản Debug hỗ trợ Firestore emulator qua `-PfirestoreEmulatorHost=10.0.2.2`.

## Luồng chính

- Người thuê tìm/lọc không gian, xem bản đồ, lưu yêu thích, đặt giờ hoặc tháng/quý, chat, theo dõi lượt thuê, đánh giá và báo cáo.
- Host quản lý tòa nhà/phòng, giá và lịch, minh chứng, check-in, hoàn tất lượt thuê và ghi nhận khách không đến.
- Admin duyệt quyền Host, bài đăng và minh chứng; xử lý báo cáo, khóa quyền, xử lý booking ngoại lệ và tra cứu nhật ký.
- Booking mới của Host payOS demo mở QR ngay, giữ lịch tối đa 5 phút. Backend xác minh nhận đủ khoản trả trước 40% đúng hạn mới xác nhận; không cần Host duyệt booking mới. Hết hạn được đối soát để nhả lịch và giữ lịch sử thất bại. Không triển khai API hoàn tiền tự động.
- Cloudinary lưu ảnh/tệp DEMO. MapLibre hiển thị bản đồ; MapTiler và openrouteservice cung cấp dữ liệu bản đồ/tuyến đường.
- SQLite lưu các trang hồ sơ, phòng và booking đã tải để xem lại khi mất mạng. Đặt chỗ, thanh toán và các thao tác ghi cần kết nối mạng.

Backend Java và tunnel phải chạy khi demo thanh toán. Xem [hướng dẫn thanh toán](backend/payments/README.md), [tài liệu tích hợp](docs/README.md) và [kiểm tra bản cuối](docs/testing/README.md).

## Kiểm tra local

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
.\gradlew.bat -p backend/payments test
```

Các bài Android thao tác cloud cần cấu hình demo riêng; không chạy hàng loạt trên dữ liệu đang dùng để quay video. Tệp build, log, credentials và `_local_archive/` không thuộc bản phát hành. Lịch sử thử nghiệm được giữ local để khôi phục, không đặt lẫn trong cây mã nguồn GitHub.
