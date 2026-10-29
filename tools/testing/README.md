# Kiểm thử nghiệp vụ

## Nghiệm thu hiện tại: INSTANT_5M

- Booking mới giữ lịch tạm tối đa 5 phút và mở QR ngay. Host không duyệt hoặc từ chối booking mới. Chỉ backend xác nhận nhận đủ 40% đúng hạn.
- `CloudBookingAcceptanceTest#instantConcurrentGuestsCannotDoubleBookOrConfirmBeforePayment`: hai Guest thật tranh lịch của phòng PAYOS đã cấu hình, kiểm retry và từ chối xác nhận bằng client, sau đó hủy đúng các lượt thử và giữ lịch sử. Bật `runCloudBookingChecks=true`.
- `PayOSAcceptanceTest#instantBookingCreatesQrWithoutHostApprovalAndReusesDeadline`: QR thật 2.000đ, không chuyển tiền; kiểm không cần Host duyệt, cùng order/deadline khi mở lại, tái tạo Activity, hủy link và nhả đúng khóa. Bật `runPayosLive=true`.
- Hai ca cloud đọc credential từ vùng private của APK debug: `files/payos-acceptance/accounts.properties` và `files/video-credentials.local.properties`. Chép bằng `run-as`; không đóng gói credential vào APK, không commit, gỡ bản chép sau khi kiểm tra.
- `FirestoreRepositoryTest#javaBookingTransactionsAdminAndRules` hiện chỉ chạy với APK Firestore emulator. Phần đầu kiểm tạo booking tức thời và cấm Host xác nhận; các ca vận hành lịch sử tiếp theo dùng fixture được chuẩn bị tường minh tại emulator. Không dùng kết quả của phần fixture như bằng chứng backend đã nhận tiền.
- Bộ Rules dùng chung database emulator phải chạy lần lượt, không chạy song song với cohort SDK vì có cùng `appConfig/payments`.
- Sau khi build đồng bộ cả hai APK cho đúng biến thể, dùng `run_device_check.py --variant cloud|emulator --name <receipt-name> --class-name <class-or-method> [--flag <opt-in>]`. Runner ghim bản sao APK và SHA, kiểm cấu hình, dùng timeout và chỉ ghi pass khi cohort đã chạy thành công, không chỉ dựa vào exit code của ADB.
- Bằng chứng lượt kiểm tra hiện tại nằm tại `output/verification/final-qa-2026-10-28`. Các kết quả ngày cũ bên dưới chỉ phục vụ truy vết, không thay cho nghiệm thu luồng mới.

## Quy trình và bằng chứng trước INSTANT_5M

Ứng dụng vẫn dùng Java/XML. Các probe Rules dùng fixture và Firestore emulator trên `127.0.0.1:8085`, database `broomsharing`; không dùng kết quả emulator để kết luận đã thử cloud hoặc đã xác thực chữ ký token Firebase Auth. Các lượt cloud có opt-in và biên bản riêng bên dưới.

[Công cụ đối soát phòng cũ](../maintenance/README.md) chạy cloud bằng tài khoản Admin thật sau khi được chấp thuận. `test_room_counter_migration.py` kiểm dữ liệu và database emulator riêng `counter_compatibility_qa`; `RoomCounterCompatibilityCloudTest` chỉ đọc cloud khi có `runRoomCounterReadChecks=true`. Không chạy công cụ apply như một phần của bộ test mặc định.

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

## Nghiệm thu nghiệp vụ trên cloud thật trước INSTANT_5M (lưu để truy vết)

Không chạy lại hướng dẫn cohort cũ này cho booking mới. Các lệnh dưới đây mô tả cách chuẩn bị bằng chứng lịch sử: `prepare_cloud_business_demo.py --serve` tạo/giữ bốn tài khoản Auth thật, hồ sơ demo và bridge loopback port 8086. Bootstrap và ba lịch sử thời gian dùng quyền quản trị từ phiên Firebase CLI local; bridge giới hạn collection, prefix phòng, UID và ID booking của lượt đó. Các transaction nghiệp vụ khi đó do Android Repository gửi với ID token của từng vai trò, không đi qua bridge.

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
