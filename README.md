# BROOM Sharing

Ứng dụng Android chia sẻ/thuê không gian làm việc, viết bằng **Java + XML**, theo **MVVM + Repository + Model**. Database chính là **Firestore `broomsharing`** trong project Firebase hiện có.

## Cấu trúc

```text
app/src/main/java/.../
  model/          Dữ liệu Firestore và trạng thái màn hình
  repository/     Firebase Android SDK và HTTP bản đồ
  ui/             Activity và ViewModel chia theo chức năng
  adapter/        Hiển thị danh sách
  utils/          Kiểm tra dữ liệu, lịch, giá và cấu hình dùng chung
app/src/main/res/ XML và tài nguyên Android
app/src/test/     Unit test Java
app/src/androidTest/ Kiểm thử Android Java
docs/            Thiết kế, tính năng và kiểm thử hiện tại
```

Không có Cloud Functions/backend riêng, Node, JavaScript hoặc DTO. Gradle Kotlin DSL (`.kts`) là cấu hình build Android có sẵn, không phải mã ứng dụng Kotlin.

Xem [cây package và vị trí từng màn hình](docs/design/source-structure.md) để đọc code theo luồng Activity → ViewModel → Repository → Firebase. Model biểu diễn dữ liệu trao đổi giữa các lớp.

## Chạy

Mở bằng Android Studio và giữ `app/google-services.json`, `local.properties` của máy. Không commit thông tin cấu hình riêng. Root `.env` có `MapTiler_API_KEY`, `HeiGIT_API_KEY`; xem `.env.example`.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Mặc định dùng Firestore thật; chỉ bản Debug được bật emulator bằng `-PfirestoreEmulatorHost=10.0.2.2`.

## Phạm vi hiện tại

- Auth/phiên đăng nhập, quên mật khẩu bằng liên kết email Firebase, hồ sơ Host, tòa nhà/phòng và duyệt Admin.
- Guest xem/lọc phòng, lưu quan tâm, đặt giờ/tháng/quý, xem yêu cầu và hủy.
- Transaction Java giữ chỗ, giá/snapshot, xác nhận/từ chối, ghi nhận vào phòng/hoàn tất thủ công và audit; chưa có QR/PIN ra vào.
- Dữ liệu cloud có [dataset TP.HCM](data/demo/README.md): thêm 10 tài khoản demo, 8 tòa nhà, 24 phòng, 28 booking cùng lịch giữ chỗ, yêu thích và hàng chờ quản trị; giữ lại hai phòng demo trước đó.
- Bản đồ TP.HCM: GPS/nhập địa chỉ/chọn điểm → tìm trong 3 km → chọn marker và vẽ tuyến thật bằng MapLibre + MapTiler + openrouteservice; Google Maps dự phòng khi lỗi dịch vụ. Có thêm [8 địa chỉ cho thuê công khai](data/reference/README.md), tách thành dữ liệu tham khảo chưa nhận booking.
- Chat văn bản Guest–Host qua Firestore, inbox/badge chưa đọc và lịch sử đồng bộ khi mở lại app. Liên hệ điện thoại mở trình quay số, không gọi trong app.
- Booking mới lưu chính sách hủy v1 và trả trước dự kiến 40%. Guest hủy thông thường không cần Admin; Host hủy phải có lý do. Hủy và nhả lịch cùng transaction, không gộp với việc hoàn tiền.
- Trang “Cập nhật của bạn” dùng chung Guest/Host, tổng hợp booking và tin nhắn mới nhất, lưu đã đọc trên Firestore. Notification Android cục bộ khi bật quyền; chưa có push khi app đóng.
- Báo cáo thông tin phòng gửi trực tiếp tới hàng chờ Admin; kết luận có lý do, có thể ẩn phòng hoặc khóa Host cùng nhật ký trong transaction. Bằng chứng tệp vẫn hoãn.
- Menu tiện ích quanh điểm chọn dùng MapTiler hiện có: lọc nhóm từ 10 POI gần nhất trong 1 km, có cache, ghi công và Google Maps dự phòng; không phải danh sách đầy đủ mọi dịch vụ.
- Theo quyết định người dùng, thanh toán/hoàn tiền hiện hiển thị “Chức năng đang phát triển”, không ghi nhận thu/hoàn tiền giả. Lưu/xem bằng chứng ảnh/PDF riêng tư để sau khi chốt dịch vụ lưu tệp.

Firestore Rules cập nhật cho database cloud `broomsharing` ngày 20/10/2026 và đọc lại xác nhận khớp file local, bao gồm chat, chính sách hủy, địa chỉ tham khảo, trạng thái đã đọc và báo cáo. Bản ghép giữ quyền Host đã có trên cloud; UI của nhánh thành viên khác chưa được tích hợp trong lượt Phúc. Xem [nghiệm thu giai đoạn 3](data/demo/phuc-phase3-verification.json). Các lần sửa tiếp theo cần phát hành lại. Chỉ giữ `firestore.rules`, `storage.rules` và `firestore.indexes.json` với đường dẫn trong `firebase.json`. Không thêm Functions hoặc yêu cầu nâng gói trong phạm vi này.

Đọc [mục lục tài liệu](docs/README.md), đặc biệt [các giới hạn kiến trúc](docs/design/architecture.md) và [kết quả kiểm thử](docs/testing/verification.md).

Tài liệu phân công `.xlsx` và slide gốc vẫn được giữ tại đường dẫn người dùng cung cấp. Cache/build/log không thuộc mã nguồn và có thể sinh lại khi chạy kiểm tra.
