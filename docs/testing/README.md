# Kiểm tra bản cuối

## Lệnh chạy

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
.\gradlew.bat -p backend/payments test
```

- `app/src/test`: quy tắc lịch/giá, trạng thái booking, hủy, giữ tạm, lọc phòng, media và xác thực. Không kết nối cloud.
- `app/src/androidTest`: kiểm tra SQLite, hợp đồng HTTP, Repository và UI. Một số bài cloud/API cần opt-in và tài khoản demo riêng; không chạy hàng loạt khi đang quay video.
- `backend/payments/src/test`: policy tiền, chữ ký webhook và transaction payment. `PaymentServiceTest` cần Firestore emulator local; thiếu emulator thì suite này được bỏ qua có chủ đích.

## Kết quả chốt ngày 31/10/2026

| Kiểm tra | Kết quả |
| --- | --- |
| Android assembleDebug, lintDebug, assembleDebugAndroidTest | Thành công |
| Android unit test | 146 ca qua, 0 lỗi, 0 bỏ qua |
| Backend với Firestore emulator 127.0.0.1:8085 | 57 ca qua, 0 lỗi, 0 bỏ qua |
| Phụ thuộc hình/input LaTeX sau khi gộp thư mục | Không thiếu tệp |
| Hash PDF trước/sau dọn thư mục | Giữ nguyên |

`assembleDebugAndroidTest` chỉ xác nhận APK kiểm thử biên dịch được, không đồng nghĩa đã chạy lại toàn bộ UI/cloud. Các vòng nghiệm thu thiết bị được ghi riêng trong [responsive](responsive-2026-10-28.md), [SQLite](sqlite-offline-2026-10-30.md) và [Admin](admin-evidence-ui-2026-10-30.md).

## Những gì đã dọn

Script Python theo từng đợt, ảnh/log thử lặp, snapshot kiểm định và bản xuất trùng được chuyển vào `_local_archive/final-20261030/` tại máy, kèm manifest đường dẫn cũ. Không nằm trong cây Git cuối; có thể khôi phục riêng khi cần. IDE, build, credentials và biến môi trường local cũng được loại khỏi Git. Giữ các test Java phục vụ hồi quy và ba script PowerShell cần để chạy demo thanh toán.

Trong lượt demo, đã xác nhận tài khoản cũ thiếu hai trường phone/phoneVerified. Hai trường đã được bổ sung theo thông tin chủ tài khoản cung cấp và đọc lại thành công; không đổi role, mật khẩu, Rules hoặc chính sách booking. Số điện thoại và công cụ sửa dữ liệu không được ghi vào repo.
