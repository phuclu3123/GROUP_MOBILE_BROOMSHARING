# Dataset giả định TP.HCM

Đã nạp **157 document** lên Firestore cloud `broom-sharing-firebase-mobile / broomsharing` ngày **19/10/2026**. Đây là dữ liệu mô phỏng cho bài học, không phải khách hàng, giao dịch hoặc văn phòng kinh doanh thật. Tên người/phòng/tòa nhà có nhãn `[DEMO]`; số điện thoại, hồ sơ và mô tả đều giả định. Ảnh minh họa dùng lại từ phòng công khai đã có.

## Dữ liệu đã thêm

| Nhóm | Số lượng |
| --- | ---: |
| Tài khoản Firebase Auth + users | 10 |
| usernames | 10 |
| publicHosts | 4 |
| buildings | 8 |
| rooms | 24 |
| bookings | 28 |
| availabilityMonths | 16 |
| users/{uid}/favorites | 12 |
| hostApplications | 6 |
| reports | 6 |
| propertyVerifications | 4 |
| auditLogs | 29 |

10 tài khoản gồm 1 Admin, 4 Host (1 bị khóa), 5 Guest (1 bị khóa). Phòng có nhiều giá, diện tích, sức chứa, loại phòng, thuê giờ/tháng/quý; có nháp, chờ duyệt, từ chối, đang rao, ngừng rao và bị đình chỉ. Tọa độ mô phỏng ở Quận 1, Quận 3, Phú Nhuận, Bình Thạnh, Thủ Đức, Tân Bình và Quận 7; một số nằm ngoài bán kính 3 km để thử bộ lọc bản đồ.

Booking gồm **6 PENDING, 6 CONFIRMED, 1 CHECKED_IN, 5 COMPLETED, 5 CANCELLED, 5 REJECTED**. Booking đang giữ chỗ có lịch tháng tương ứng; lịch không chứa thông tin liên hệ Guest. Booking kết thúc/hủy/từ chối không giữ lịch. Tiền thuê/cọc/snapshot khớp với phòng; thanh toán là `DEMO_PENDING`. Lịch sử quá khứ và booking tương lai được tính theo giờ TP.HCM tại ngày sinh dataset.

Bốn hồ sơ xác minh có mô tả giả định và chưa có tệp bằng chứng; không tạo đường dẫn Storage không tồn tại. Muốn thực hành duyệt bằng chứng cần bổ sung tệp và xử lý quyền Storage đã ghi trong tài liệu thiết kế.

## Đăng nhập để trình diễn

Username dùng được ngay trong app:

- `demo_admin`: màn quản trị.
- `demo_host1`, `demo_host2`, `demo_host3`: Host đang hoạt động.
- `demo_host4`: tình huống Host bị khóa.
- `demo_guest1` đến `demo_guest4`: Guest hoạt động, mỗi người có 7 booking.
- `demo_guest5`: tình huống Guest bị khóa.

**Mật khẩu riêng từng tài khoản nằm ở `credentials.local.properties` ngay trong thư mục này**, tra `<alias>.password`, ví dụ `admin.password`, `host1.password`, `guest1.password`. File cũng có email và UID. Ngày 20/10/2026 đã kiểm tra lại cả 10 tài khoản với Firebase Auth, username và hồ sơ Firestore; kết quả ở [login-verification.json](login-verification.json). File mật khẩu đã được ghi ở dạng copy trực tiếp sau dấu `=`, không còn dấu gạch chéo thừa trước dấu chấm than; mật khẩu trên Firebase không đổi. File được Git bỏ qua; không đưa vào APK, dataset JSON hoặc tài liệu chia sẻ. Nếu cần bàn giao tài khoản, chuyển file riêng cho thành viên được phép.

## File và cách nạp lại

- `firestore-dataset.json`: snapshot có đường dẫn document và trường Firestore đúng kiểu (Timestamp, số, list, map), không có mật khẩu/token.
- `verification.json`: đã đối chiếu toàn bộ 157 document, kiểm tra giá/thời gian/liên kết/khóa lịch và quyền Admin/Guest thật. Đã chạy lại `apply`: dữ liệu hiện hữu được bỏ qua, không tạo trùng.
- [FirestoreDataset.java](../../tools/dataset/FirestoreDataset.java): công cụ Java sinh/nạp/kiểm tra riêng, không phải backend, không chạy trong Android và không thêm dependency vào app.

Tại thư mục gốc dự án, dùng JDK 17 và Gson đã có trong Gradle cache:

```powershell
$javaBin = 'C:\Program Files\Java\jdk-17\bin'
$gson = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson\2.11.0" -Recurse -Filter 'gson-2.11.0.jar' | Select-Object -First 1).FullName
$classes = Join-Path $env:TEMP 'broom-dataset-classes'
New-Item -ItemType Directory -Force $classes | Out-Null
& "$javaBin\javac.exe" -encoding UTF-8 -cp $gson -d $classes tools\dataset\FirestoreDataset.java
$env:BROOM_DATASET_TOKEN_FILE = 'DUONG_DAN_FILE_TOKEN_QUAN_TRI_NAM_NGOAI_REPO'
& "$javaBin\java.exe" -cp "$classes;$gson" FirestoreDataset apply
# Verify có thể dùng tài khoản demo local, không cần token quản trị:
Remove-Item Env:\BROOM_DATASET_TOKEN_FILE -ErrorAction SilentlyContinue
& "$javaBin\java.exe" -cp "$classes;$gson" FirestoreDataset verify
```

Token quản trị chỉ được đọc từ file ngoài repo; không truyền token qua tham số lệnh, không lưu vào dataset. Công cụ kiểm tra đúng project/database trước khi ghi. `apply` chỉ tạo khi toàn bộ đường dẫn dataset chưa tồn tại, dùng precondition `exists=false` và commit nguyên tử. Nếu tất cả đã có thì bỏ qua; nếu chỉ có một phần thì dừng để tránh làm sai lịch booking. Không có lệnh tự xóa/ghi đè dữ liệu đang dùng.

`verify` tự đăng nhập Admin demo khi không có token quản trị. Với Favorites riêng tư, công cụ dùng đúng tài khoản demo sở hữu từng document; không mở rộng Rules cho Admin và không ghi cloud. Ngày 20/10/2026 đã đọc lại đủ 157 document, toàn bộ trường vẫn khớp snapshot sau các lượt nghiệm thu QA; Admin đọc được 13 users, Guest1 đọc 7 booking của mình và bị chặn đọc hồ sơ riêng Admin.

`generate` đã chạy cho snapshot này và từ chối ghi đè file JSON. Không xóa snapshot hoặc sửa ngày/UID tùy tiện sau khi đã dùng dữ liệu: cần giữ booking, giá và lịch giữ chỗ nhất quán. Snapshot này bổ sung vào dữ liệu trước đó; các tài khoản và document cũ không bị đổi role hoặc ghi đè.

## Nghiệm thu booking cloud ngày 20/10/2026

[cloud-booking-verification.json](cloud-booking-verification.json) ghi kết quả chạy Java Repository trên Firestore thật. Fixture dùng ID cố định `qa_dot2_host1_building`, `qa_dot2_host1_hourly`, `qa_dot2_host1_fixed`, tên có nhãn DEMO/NGHIỆM THU. Tái sử dụng ảnh demo có sẵn, không tạo URL ảnh giả hoặc tài khoản mới. Các booking/audit thử được giữ làm dấu vết; toàn bộ giữ chỗ đã nhả, hai phòng về INACTIVE nên không xuất hiện trong danh sách công khai. Không sửa role hoặc booking thuộc bộ snapshot 157 document ban đầu.

Bài `CloudBookingAcceptanceTest` chỉ chạy khi bật `runCloudBookingChecks=true`, dùng file tài khoản riêng đưa tạm vào thiết bị. Không đóng gói mật khẩu vào APK, không thay Rules cho test, không chạy trình giả lập database trong bài này. Các bài UI `RoleWorkspaceTest`, `DemoFlowCheckTest` dùng cùng cơ chế opt-in.
