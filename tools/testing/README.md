# Kiểm thử nghiệp vụ

Ứng dụng vẫn dùng Java/XML. Các script Python ở đây chỉ tạo fixture và gửi yêu cầu kiểm thử đến Firestore emulator trên `127.0.0.1:8085`, database `broomsharing`. Không dùng chúng để kết luận đã thử trên cloud hay xác thực chữ ký token của Firebase Authentication.

## Rules trực tiếp

Sau khi khởi động Firestore emulator cho project `broom-sharing-firebase-mobile`:

```powershell
python tools/testing/run_rules_acceptance.py
```

Runner nạp đúng `firestore.rules` vào named database, tạo fixture `qa_rules_*`/`probe_*` tại emulator và chạy 13 ca dương/âm. Các ca gồm giá giả, slot không liên tiếp, bỏ write phòng hoặc audit, 16 ngày/8 khoảng khóa, ngày ISO/ngày cũ, tháng cuối của quý và đầu tháng kết thúc loại trừ. Ca 48 slot kiểm giới hạn kỹ thuật của Rules; không bổ sung một chế độ đặt phòng mới vào UI. Kết quả nằm trong `output/verification/business-completion-2026-10-24/rules-boundaries.json`.

`firestore_rules_probe.py` chạy riêng một ca khi cần chẩn đoán. Fixture dùng token emulator tự tạo; không phải bằng chứng Firebase Auth đã chấp nhận token đó trên cloud. Nghiệm thu Auth và Repository thực tế được thực hiện bằng Android SDK bên dưới.

## Android Repository và giao diện

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PfirestoreEmulatorHost=10.0.2.2'
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class vn.edu.ueh.phuclu.broomsharing.FirestoreRepositoryTest,vn.edu.ueh.phuclu.broomsharing.ui.auth.AuthFormValidationTest vn.edu.ueh.phuclu.broomsharing.test/androidx.test.runner.AndroidJUnitRunner
```

Luôn build/cài cả hai APK từ cùng cấu hình. `FirestoreRepositoryTest` đọc thêm cấu hình runtime của APK đang cài để từ chối chạy nhầm vào bản cloud. Test tạo bốn tài khoản Firebase Auth tạm thời, dùng Firestore emulator, rồi xóa bốn tài khoản trong `finally`; cần Internet cho Auth. Không chạy song song các cohort thay đổi phiên Auth trên cùng thiết bị.

Chuỗi Repository kiểm Host tạo tòa nhà/phòng, Admin duyệt, Guest xem, hai thiết bị tranh lịch, gửi lại, thuê quý, nhả lịch, hết hạn, review, báo cáo/phản hồi/kết luận, chat, receipt, khóa Host và can thiệp từng booking. Màn hình đánh giá, báo cáo, thông báo và thu hồi quyền được mở thật trên Android; Calendar Intent được chặn bằng ActivityMonitor để kiểm dữ liệu chuyển giao, không tự ghi lịch của người dùng.

Để tạo APK dùng database cloud, build lại với `'-PfirestoreEmulatorHost='`. Rules/index tương ứng đã triển khai ngày 24/10/2026 vào database `broomsharing`; khi đổi cấu hình ở lần sau vẫn cần triển khai và xác minh lại. Release hiện chưa có cấu hình ký phát hành; không dùng APK unsigned để cài trực tiếp.

## Nghiệm thu nghiệp vụ trên cloud thật

Chỉ chạy khi đã được chấp thuận ghi dữ liệu thử lên cloud. `prepare_cloud_business_demo.py --serve` tạo/giữ bốn tài khoản Auth thật, hồ sơ demo và bridge loopback port 8086. Bootstrap và ba lịch sử thời gian dùng quyền quản trị từ phiên Firebase CLI local; bridge giới hạn collection, prefix phòng, UID và ID booking của lượt đó. Các transaction nghiệp vụ vẫn do Android Repository gửi với ID token của từng vai trò, không đi qua bridge.

```powershell
python tools/testing/prepare_cloud_business_demo.py --serve
```

Sau khi bridge sẵn sàng, ở terminal khác build/cài cả APK cloud và APK test, rồi chạy đúng method:

```powershell
adb push data/demo/video-credentials.local.properties /sdcard/Android/data/vn.edu.ueh.phuclu.broomsharing/files/video-credentials.local.properties
adb shell am instrument -w -r -e class vn.edu.ueh.phuclu.broomsharing.FirestoreRepositoryTest#javaBookingTransactionsAdminAndRules -e runCloudBusinessChecks true vn.edu.ueh.phuclu.broomsharing.test/androidx.test.runner.AndroidJUnitRunner
```

Lấy `cloud-business-acceptance.json` và `video-business-state.json` từ `getExternalFilesDir(null)` của app vào `output/verification/cloud-business-2026-10-24/`. Khi đã có state, `python tools/testing/verify_cloud_business_demo.py` đọc đối chiếu qua ID token Admin demo, kiểm bộ đếm/phần giữ lịch/audit/review/report. Bài `cloudRetainedBookingDetailAfterVisualFix` chạy bằng opt-in `runCloudBusinessReadChecks=true` để kiểm APK cuối trên booking đã có, không tạo lại booking.

Lượt chuẩn bị mới có prefix mới; không chạy lại method ghi bằng một prefix cũ đang có dữ liệu demo. Không xóa tài khoản sau cohort cloud. Dừng bridge, gỡ APK test và bản sao file credential tạm trên emulator sau nghiệm thu; giữ bản local được Git bỏ qua để quay video. File Properties được đọc bằng UTF-8 do có tên tiếng Việt. Biên bản và giới hạn: [nghiệm thu cloud](../../docs/testing/cloud-business-acceptance-2026-10-24.md).
