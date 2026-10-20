# Build và kiểm thử

Mặc định ứng dụng kết nối **Firestore cloud `broomsharing`** của project hiện có. Emulator chỉ là lựa chọn kiểm thử để kiểm tra tranh chỗ và thao tác bị từ chối trước khi phát hành Rules.

## Chạy bản dùng cloud

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Giữ `app/google-services.json`, `.env` và `local.properties` của máy. Không thêm Functions, Node hay dịch vụ backend. Cấu hình Debug mặc định có `FIRESTORE_EMULATOR_HOST` rỗng.

## Kết quả đã xác nhận ngày 19/10/2026

| Kiểm tra | Kết quả/phạm vi |
| --- | --- |
| Build APK mặc định dùng cloud | BUILD SUCCESSFUL |
| Unit test Java | 51 test, 0 lỗi, 0 bỏ qua |
| Android FirestoreRepositoryTest | Đạt, 90,457 giây; Java Repository + Auth thật + Firestore Emulator và Rules thực tế |
| Android MapApiTest + AuthUiTest + AuthFormValidationTest | 10 test được chạy: 9 đạt, 1 bỏ qua do dự án đã cấu hình Firebase; 0 lỗi, 74,329 giây |
| Map API thật | Geocoding và Directions tại TP.HCM đạt; key không ghi vào báo cáo |
| Lint | 0 lỗi, 512 cảnh báo; không coi là đã xử lý toàn bộ cảnh báo UI cũ |
| Firestore cloud | Rules đã phát hành cho `broomsharing`, nội dung đọc lại khớp file local; 3 index cần dùng ở trạng thái READY |
| Cloud Guest smoke | Đăng ký Guest, đọc hai phòng DEMO, truy vấn được 3 phòng công khai; PublicHost chỉ có uid/name/status |
| Cloud kiểm tra quyền | Chặn đọc hồ sơ riêng của Host, tự đổi role thành ADMIN, sửa giá phòng và đọc phòng khi chưa đăng nhập |
| Dọn dữ liệu thử cloud | Tài khoản Auth, users và usernames của Guest thử đều đã xóa; giữ nguyên 3 tài khoản trước đó |

FirestoreRepositoryTest kiểm tra: hai Guest tranh chỗ chỉ một người tạo được; gửi lại trả đúng ID; giá lấy lại từ Room; Guest không tự xác nhận; hai khung sát nhau không đè lịch; Host xác nhận, Guest hủy, Host từ chối có lý do và nhả đúng khóa; chặn sửa mode khi đang giữ chỗ; khóa/gỡ khóa Host/phòng; thuê quý chặn thuê tháng trùng rồi cho đặt sau khi hủy; lịch công khai không chứa tên/điện thoại Guest. Màn hình Guest mở và xoay máy qua AuthViewModel thành công. Bốn tài khoản Auth của lượt này đã xóa trong finally; các lần chạy kiểm tra sửa lỗi trước đó cũng xác nhận xóa đủ tài khoản.

Kiểm tra cloud dùng quyền Guest thật để đánh giá các thao tác cho phép/bị chặn, không dùng quyền quản trị để giả lập kết quả phân quyền. Dữ liệu thiết lập được ghi bằng quyền quản trị hiện có: 1 PublicHost, bổ sung 11 trường thiếu ở phòng cũ, 1 tòa nhà DEMO và 2 phòng DEMO TP.HCM, kèm audit. Không tạo booking cloud giả. Sau lượt smoke này, người dùng yêu cầu dataset rộng hơn; đã tạo Admin demo riêng trong bộ dataset TP.HCM.

## Chạy lại kiểm thử Java cục bộ khi cần

Terminal thứ nhất, dùng JAR emulator đã có trong cache và Java đi kèm Android Studio:

```powershell
$firestoreJar = "$env:USERPROFILE\.cache\firebase\emulators\cloud-firestore-emulator-v1.22.0.jar"
& "$env:ProgramFiles\Android\Android Studio\jbr\bin\java.exe" -Xmx512m -jar $firestoreJar --host 127.0.0.1 --port 8085 --project_id broom-sharing-firebase-mobile --rules firestore.rules
```

Terminal thứ hai, sau khi bật máy ảo Android:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PfirestoreEmulatorHost=10.0.2.2'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $adb shell am instrument -w -r -e class vn.edu.ueh.phuclu.broomsharing.FirestoreRepositoryTest vn.edu.ueh.phuclu.broomsharing.test/androidx.test.runner.AndroidJUnitRunner
```

Muốn chạy API bản đồ thật: thay class bằng `vn.edu.ueh.phuclu.broomsharing.MapApiTest` và thêm `-e runMapApis true`. FirestoreRepositoryTest tạo tài khoản Auth thật rồi tự xóa, Firestore của test này chỉ ghi vào emulator; cần mạng cho Auth. Không chạy test ghi bằng cách bỏ điều kiện bảo vệ emulator.

Phải khởi động JAR với `--rules firestore.rules`; khởi động lại khi sửa Rules. HTTP 200 của endpoint nạp Rules không đủ để kết luận named database đang dùng bản mới. Test phải xác nhận cả thao tác được phép và bị từ chối trên `broomsharing`.

Build lại **không có** tham số `-PfirestoreEmulatorHost` để trở về APK cloud. Sau nghiệm thu, cache/build/log và script quản trị tạm được dọn; Android Studio/Gradle sẽ sinh lại cache khi cần. Chỉ giữ 5 tài liệu hiện tại trong docs.

## Giới hạn

Transaction booking Java đã kiểm tra đầy đủ luồng nêu trên với emulator; lượt smoke cloud xác nhận dữ liệu, truy vấn và các quyền nêu trên, chưa phải nghiệm thu mọi luồng booking trên thiết bị với cloud. Chưa đo hiệu năng production, chưa chứng minh chống mọi client sửa đổi đối với blackout tùy biến. Không coi bộ test này là chứng nhận hệ thống thanh toán/đặt chỗ production hoàn chỉnh.

Storage/bằng chứng chưa nghiệm thu bucket thật. Storage Rules không tra được role ở named database `broomsharing`, nên quyền Admin đọc tệp chưa hoàn tất; giữ tệp riêng tư. Không tự tạo bucket, đổi billing hoặc thêm backend. Chi tiết và phần trình bày còn nằm trong AdminRepository được ghi ở [thiết kế](../design/architecture.md).
Firestore Ruleset đang dùng: `05e4efd1-aa3e-4730-ad4d-4b000dd4fd6e`. Ruleset trước lần chuyển Java: `917c034f-aaaa-4e67-ae0d-750093ef0dbf` (Firebase giữ phiên bản để đối chiếu/khôi phục).

## Kiểm tra dataset mở rộng

Dataset 157 document đã nạp bằng công cụ Java, đọc lại từng trường đều khớp JSON. Admin demo truy vấn được 13 users; Guest demo đọc được phòng và 7 booking của mình, không đọc được hồ sơ riêng Admin. Kết quả máy đọc được: [verification.json](../../data/demo/verification.json). Tài khoản và dữ liệu này được giữ để làm bài theo yêu cầu, không phải tài khoản smoke tạm cần xóa.

## Sửa UI, chỉ đường và đăng nhập demo (20/10/2026)

- Cả 10 email/mật khẩu demo xác thực được trên Firebase Auth thật; UID, username và role/status khớp Firestore. `host4` và `guest5` vẫn là trường hợp BLOCKED theo dataset. Kết quả: [login-verification.json](../../data/demo/login-verification.json).
- Nguyên nhân copy mật khẩu sai là `Properties.store()` ghi thêm dấu escape trước `!`. File local đã ghi lại dạng copy trực tiếp; đọc lại bằng Properties vẫn cho đúng toàn bộ giá trị cũ. Không đổi mật khẩu trên Firebase. Công cụ sinh dataset Java đã sửa cách ghi cho các lần sau.
- Form LoginActivity đăng nhập Host bằng email thật từ file; Admin/Guest đăng nhập bằng username qua FirebaseAuthRepository và nhận đúng role/UID.
- Chi tiết phòng chia mục rõ, toolbar và nút lưu/đặt chỗ cố định. Đã kiểm tra ảnh chụp đầu/cuối trang trên máy ảo; chạm thumbnail đổi ảnh bìa.
- Chỉ đường từ chi tiết mở bản đồ trong app. Hai điểm trùng nhau không gọi API và không mở Google. Với hai điểm khác nhau, Directions thật trả tuyến 1,63 km/khoảng 4 phút, MapLibre đã vẽ đường màu xanh trên nền MapTiler. Đã chờ và kiểm tra `queryRenderedFeatures` của lớp `pilot-route-line`, không chỉ kiểm tra API trả số km.
- Google Maps chỉ nhận khi có lỗi sau lần thử dịch vụ trong app. Test Repository giả lập lỗi chỉ nằm trong androidTest, kiểm tra thứ tự gọi; không đưa dữ liệu tuyến giả vào app.
- Build APK cloud thành công; 51 unit test Java đạt. Ba bài Android `MapApiTest`, `DemoFlowCheckTest`, `MapPriorityTest` đạt (59,729 giây); DemoFlowCheckTest chạy bổ sung kiểm tra geometry render đạt (41,674 giây). Lint: 0 lỗi, 525 cảnh báo (chưa xử lý toàn bộ cảnh báo lint).
- Bản APK đã cài trên máy ảo. File mật khẩu đưa tạm cho instrumentation đã được xóa khỏi thiết bị; không đóng gói tài khoản/mật khẩu vào APK. Không seed lại hoặc đổi dữ liệu booking cloud trong lượt sửa này.

## Nghiệm thu UI ba vai trò và Đợt 2 (20/10/2026)

Bản cloud đã build và cài trên emulator. `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `lintDebug` đạt; 51 unit test, 0 lỗi. Lint 0 lỗi, 473 cảnh báo còn tồn tại, không coi là đã xử lý hết cảnh báo.

Lượt Android cuối chạy 15 bài trong 199,087 giây: **14 đạt, 1 bỏ qua, 0 lỗi**. Bài bỏ qua là `missingFirebaseDoesNotFakeSuccessfulLogin`, chỉ dành cho build chưa có Firebase; build hiện tại đã kết nối. Kết quả theo từng bài: [device-verification.json](../../data/demo/device-verification.json).

- Host/Admin: đăng nhập vào đúng workspace; điều hướng, lịch theo phòng, tài khoản, chuyển Host sang giao diện thuê, khôi phục tab sau tái tạo Activity, hủy/xác nhận đăng xuất. Đã xem ảnh chụp trên emulator.
- Guest: đọc phòng công khai từ cloud, mở/áp dụng/xóa bộ lọc, trạng thái không có kết quả, khôi phục màn hình, danh sách đặt chỗ và hồ sơ đúng vai trò, mở phòng đã lưu. Màn chi tiết và tuyến đường thật tiếp tục đạt; đã chờ geometry xuất hiện trên bản đồ.
- Kiểm tra hình ảnh bổ sung đạt trong 37,347 giây: ngoài query geometry, bài DemoFlowCheckTest chờ đường màu xanh hiện trên khung hình Android rồi mới lưu ảnh. Đã xem lại ảnh có tuyến ô tô 1,63 km/khoảng 4 phút. Mã ứng dụng không đổi sau lượt 15 bài; thay đổi bổ sung chỉ ở harness chụp ảnh và bằng chứng.
- Form đăng nhập/đăng ký kiểm tra dữ liệu sai và không lưu lại mật khẩu khi tái tạo màn hình. Quên mật khẩu đã nối Firebase Auth qua ViewModel/Repository; bài test chỉ kiểm tra email sai để không tự gửi thư. **Chưa nghiệm thu thư reset thực sự tới hộp thư người dùng.**
- Lỗi focus hộp thoại trong lượt kiểm tra trước đã sửa ở harness: chờ Activity có focus và chọn đúng dialog root; lượt cuối đạt toàn bộ bài áp dụng.

### Đối chiếu các đầu việc Đợt 2

“Cloud” bên dưới nghĩa là Firebase Auth/Firestore thật qua Android Java Repository, không giả kết quả từ quyền quản trị hay emulator. Các thao tác sai quyền được chạy bằng tài khoản Guest thật. Bằng chứng: [cloud-booking-verification.json](../../data/demo/cloud-booking-verification.json).

| ID Excel | Nội dung | Bằng chứng / trạng thái |
| --- | --- | --- |
| U09 | Lọc/sắp xếp | Code/RoomFilter unit test và UI cloud; lọc trên các trang đã tải, có số lượng rõ |
| U10 | Chi tiết thông tin | UI cloud, ảnh chụp; giá/cọc/chính sách/nguồn xác minh tách mục |
| U11–U14 | Giờ, tháng/quý, quote và tạo booking | Cloud: giá từ Room, tranh chỗ chỉ một người thắng, retry đúng ID; tháng/quý giữ lịch đúng |
| U15 | Danh sách trạng thái | UI cloud từ booking của Guest; đủ các trạng thái hiện có, không trộn paymentState |
| U16 (P1) | Phiếu xác nhận | Đọc booking trên server; không có QR/PIN ra vào thật, không tự nhận đã thanh toán |
| U17 (P1) | Favorites | Repository/Rules hiện có, dataset cloud và mở danh sách qua UI; lượt này không chạy lại thao tác ghi favorites |
| H09–H11 | Giờ hoạt động, blackout, thuê cố định | Unit test lịch + cloud: lưu ngày khóa, từ chối ngày đóng/giờ ngoài lịch; tháng và quý không chồng |
| H12 | Sửa phòng | Cloud chặn đổi mode khi có giữ chỗ; UI mở chỉnh sửa đúng phòng |
| H13–H14 | Yêu cầu thuê, chấp nhận/từ chối | UI cloud và transaction cloud; lý do từ chối, giá/cọc và status được giữ đúng |
| H15 (P1) | Xem lịch | Nút Lịch đặt theo roomId; lịch ngày dạng văn bản hiện có, chưa phải lịch tuần dạng lưới |
| H16 | Chính sách | Model/form/chi tiết đọc cùng trường; snapshot lưu trong booking cloud |
| H17 (P1) | Ngừng nhận đặt mới | Cloud Host tạm dừng/mở lại; giữ nguyên lịch sử; cuối test phòng về INACTIVE |
| P11–P15 | Mode, lịch, transaction, giá và thuê dài hạn | Cloud và 51 unit test; idempotency, race, giá, ba tháng lịch, hủy/từ chối nhả lịch |
| P16 | Duyệt Host | Code transaction/Rules và kiểm thử emulator ngày 19/10 đã đạt; UI đọc hàng chờ cloud. Không đổi role tài khoản dataset trong lượt 20/10 |
| P17 | Duyệt không gian | Cloud: Host tạo hai phòng pending, Admin duyệt, Guest mới đặt được |
| P18 | Khóa/mở | Cloud: khóa/gỡ phòng, chặn đặt mới; Host demo BLOCKED bị từ chối. Khóa/mở Host qua transaction đã đạt trên emulator ngày 19/10 |
| **P19** | **Xác minh bằng chứng riêng tư** | **Chưa hoàn tất/nghiệm thu end-to-end: quyền Admin đọc bằng chứng Storage chưa có. Không bỏ điều kiện mở đủ bằng chứng để duyệt** |
| P20 (P1) | Audit | Các transaction cloud sinh audit thật; Admin có menu nhật ký và query 100 thao tác gần nhất |
| P21 | Chuyển trạng thái | Cloud: pending/confirmed/rejected/cancelled; check-in/completed và biên thời gian có unit/emulator trước đó, chưa chạy đủ hai thao tác này theo thời gian thật trong lượt cloud |

**Kết luận Đợt 2:** các luồng đặt thuê và quản lý nêu trên đã có bằng chứng chạy thật; chưa thể đánh dấu toàn bộ Đợt 2 hoàn tất vì P19. Không chứng nhận “mọi luồng đều không còn lỗi”. Giữ nguyên lựa chọn không Blaze/không backend mới và chờ thống nhất cách xử lý phần bằng chứng.

Cloud test dùng một tòa nhà và hai phòng QA DEMO riêng, ID cố định để không tạo thêm phòng sau mỗi lần chạy. Mọi booking thử đã hủy/từ chối, lịch giữ chỗ đọc lại rỗng; hai phòng INACTIVE. Giữ booking/audit làm dấu vết, không xóa lịch sử hoặc sửa role/booking của snapshot demo gốc. File tài khoản và ảnh thử trên emulator đã được xóa sau khi thu bằng chứng; app để ở màn đăng nhập.

Sau nghiệm thu đã chạy lại Java dataset verifier trên cloud: đủ 157 document gốc khớp từng trường với snapshot, kiểm tra liên kết/giá/lịch đạt; Admin đọc 13 users và Guest1 đọc 7 booking, không đọc được hồ sơ riêng Admin. Favorites được đọc bằng tài khoản chủ sở hữu, không dùng quyền app Admin để vượt riêng tư. Kết quả cập nhật tại [verification.json](../../data/demo/verification.json).

### Phạm vi bài giảng và giới hạn còn lại

Đối chiếu các bài XML/resources, scrolling, Activity/Intent/lifecycle, navigation, RecyclerView/Material, async/network, ViewModel/Repository và PDF Firebase gốc; xem [thiết kế](../design/architecture.md). Không có mã ứng dụng JS/CJS/MJS/Kotlin, DTO, Functions, Node, DI framework hoặc backend riêng. Java dataset tool nằm ngoài ứng dụng, chỉ dùng để nạp/đối chiếu dữ liệu.

Giới hạn cũ còn giữ rõ: Rules chưa chứng minh bao phủ mọi blackout tùy biến đối với client sửa đổi; key API bản đồ vẫn có thể trích từ APK; `ReviewItem.details` còn được ghép chuỗi tại AdminRepository. Đây là điểm cần biết khi phát triển tiếp, không mở rộng thêm dịch vụ để che giới hạn. Tại thời điểm nghiệm thu Đợt 2 ở trên, chat/thông báo chưa triển khai; phần bổ sung theo thảo luận sau đó được ghi riêng bên dưới.

### Chat, hủy và thông báo theo phạm vi đã chốt (20/10/2026)

- Build APK, AndroidTest APK, 55 unit test và lint đạt; lint 0 lỗi, 488 cảnh báo. Các bài chính sách kiểm tra đúng ranh giới 24 giờ/6 giờ, 7 ngày/48 giờ, sau khi bắt đầu và không tự áp chính sách mới vào booking cũ.
- Suite cloud/UI gồm 5 bài đạt, 0 lỗi, 182,144 giây: CloudBookingAcceptanceTest, CloudChatAcceptanceTest, ChatUiTest và hai bài RoleWorkspaceTest. Sau khi thêm nhánh Host hủy có lý do, chạy lại CloudBookingAcceptanceTest đạt trong 50,066 giây; bao gồm Host không được hủy thiếu lý do, tỷ lệ chính sách 100%, lưu người hủy và nhả lịch. [Bằng chứng chat/UI](../../data/demo/chat-verification.json), [booking cloud](../../data/demo/cloud-booking-verification.json).
- Chat thử với các phiên Firebase Auth độc lập Guest1/Host1 trên cloud: snapshot realtime, cùng một hội thoại khi mở lại, retry không nhân tin, hai phía gửi đồng thời không mất số thứ tự/bộ đếm. Người ngoài và Admin không đọc được chat; không sửa được tin đã gửi, mốc đọc của đối phương hoặc mốc lớn hơn tin đã nhận. Tin QA có tiền tố rõ trên tài khoản demo, không phải hội thoại người dùng thật.
- UI emulator Android 12/API 31: gửi Guest, đổi Host thấy badge, đọc tin làm số chưa đọc về 0, Host trả lời, draft giữ khi recreate. Đã xem ảnh chat/inbox/badge và trang thông báo. Bài thử bắt được lỗi listener cũ lúc đăng xuất; đã chặn theo phiên và sửa, suite cuối đạt.
- Notification cục bộ được post thật vào NotificationManager khi bật trong app; không dùng FCM/push, không bảo đảm thông báo khi app đóng. Nhắc chính sách tính từ booking hiện tại khi app mở/đang hoạt động. Nhánh cấp quyền Android 13 có trong code nhưng chưa thử trên thiết bị API 33 trở lên. Lỗi harness cấp quyền API 33 trên emulator API 31 đã sửa điều kiện phiên bản trước suite đạt.
- Theo quyết định người dùng, thanh toán và hoàn tiền để sau: UI ghi “Chức năng đang phát triển”; không có nút giả nhận đã chuyển tiền hoặc xác nhận đã hoàn. Booking mới snapshot trả trước dự kiến 40% và chính sách v1. Hủy chưa thanh toán ghi 0 tiền hoàn/NOT_REQUIRED, nhả lịch ngay không chờ Admin; Rules kiểm tra tỷ lệ bằng thời gian server. P19 lưu/xem tệp bằng chứng tiếp tục để sau theo thống nhất, không chặn phần chat/hủy.
- Thuê đã bắt đầu, đổi lịch có chênh lệch tiền, xử lý tranh chấp và đối soát thanh toán chưa có luồng tự động. Không tuyên bố đã tích hợp cổng tiền thật hoặc mọi nghiệp vụ production đã hoàn tất.
