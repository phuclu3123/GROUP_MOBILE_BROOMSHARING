# BROOM Sharing

Ứng dụng Android chia sẻ/thuê không gian làm việc, viết bằng **Java + XML**, theo **MVVM + Repository + Model**. Database chính là **Firestore `broomsharing`** trong project Firebase hiện có.

## Cấu trúc

```text
app/src/main/java/.../
  model/          Dữ liệu Firestore và trạng thái màn hình
  repository/     Firebase Android SDK và HTTP bản đồ
  ui/             ViewModel, màn Auth và các phần UI dùng chung
  adapter/        Hiển thị danh sách
  utils/          Kiểm tra dữ liệu, lịch, giá và cấu hình dùng chung
  *Activity.java  Màn hình, quan sát ViewModel
app/src/main/res/ XML và tài nguyên Android
app/src/test/     Unit test Java
app/src/androidTest/ Kiểm thử Android Java
docs/            Thiết kế, tính năng và kiểm thử hiện tại
```

Không có Cloud Functions/backend riêng, Node, JavaScript hoặc DTO. Gradle Kotlin DSL (`.kts`) là cấu hình build Android có sẵn, không phải mã ứng dụng Kotlin.

## Chạy

Mở bằng Android Studio và giữ `app/google-services.json`, `local.properties` của máy. Không commit thông tin cấu hình riêng. Root `.env` có `MapTiler_API_KEY`, `HeiGIT_API_KEY`; xem `.env.example`.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Mặc định dùng Firestore thật; chỉ bản Debug được bật emulator bằng `-PfirestoreEmulatorHost=10.0.2.2`.

## Phạm vi hiện tại

- Auth/phiên đăng nhập, hồ sơ Host, tòa nhà/phòng và duyệt Admin.
- Guest xem/lọc phòng, lưu quan tâm, đặt giờ/tháng/quý, xem yêu cầu và hủy.
- Transaction Java giữ chỗ, giá/snapshot, xác nhận/từ chối, check-in/hoàn tất demo và audit.
- Dữ liệu cloud có [dataset TP.HCM](data/demo/README.md): thêm 10 tài khoản demo, 8 tòa nhà, 24 phòng, 28 booking cùng lịch giữ chỗ, yêu thích và hàng chờ quản trị; giữ lại hai phòng demo trước đó.
- Bản đồ thử TP.HCM: MapLibre + MapTiler + LocationManager + openrouteservice; lỗi dịch vụ tự mở Google Maps.
- Thanh toán mới là demo; Storage/bằng chứng cần nghiệm thu trên bucket thật. Chat/thông báo chưa hoàn thiện.

Firestore Rules đã phát hành cho database cloud `broomsharing` ngày 19/10/2026 và đọc lại xác nhận khớp file local. Các lần sửa tiếp theo cần phát hành lại. Chỉ giữ `firestore.rules`, `storage.rules` và `firestore.indexes.json` với đường dẫn trong `firebase.json`. Không cần nâng Blaze để chạy phần nghiệp vụ Java/Firestore này.

Đọc [mục lục tài liệu](docs/README.md), đặc biệt [các giới hạn kiến trúc](docs/design/architecture.md) và [kết quả kiểm thử](docs/testing/verification.md).

Tài liệu phân công `.xlsx` và slide gốc vẫn được giữ tại đường dẫn người dùng cung cấp. Cache/build/log không thuộc mã nguồn và có thể sinh lại khi chạy kiểm tra.
