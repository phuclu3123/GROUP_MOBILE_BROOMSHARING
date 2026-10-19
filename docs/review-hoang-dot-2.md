# Rà soát và tích hợp Host Đợt 2

Cập nhật cùng ngày: Phúc yêu cầu hoàn thiện thêm P11–P21; xem `review-phuc-dot-2.md` cho phạm vi hiện tại. Tổng service đã thành 10 callable, integration test đã tăng từ 12 lên 19; báo cáo dưới đây ghi lại bước rà Hoàng ban đầu.

Trạng thái triển khai mới nhất: CLI đã đăng nhập, ba index đã tạo trên database thật. Functions/rules mới chưa phát hành do yêu cầu Blaze; chủ project chọn tạm dừng triển khai service và không nâng gói.

Ngày kiểm tra: 19/10/2026. Nguồn yêu cầu: `BroomSharing_PhanCong_4Dot_Android_Java_Firebase.xlsx`, sheet `02 HOÀNG HOST`, dòng 31–39 (H09–H17). Bảng phân công xác định phạm vi; cách triển khai dựa trên slide và cấu trúc hiện có.

## Kết luận đối với bài nộp của Hoàng

Nhánh `origin/lam-hoang`, commit `2508d74` (`complete phase 2`), **chưa đáp ứng đầy đủ Đợt 2**. Commit đã được fast-forward vào nhánh `phuc` trước khi sửa; các thay đổi bổ sung hiện nằm trong working tree của `phuc`, chưa push.

| Mục | Bản Hoàng nộp | Bổ sung trong lần tích hợp này |
| --- | --- | --- |
| H09 — giờ hoạt động | Có giờ mở/đóng chung; thiếu ngày trong tuần, bước giờ, thời lượng tối thiểu | Chọn T2–CN; bước 30/60 phút; kiểm tra thời lượng và giờ hợp lệ ở Android/server |
| H10 — khóa lịch | Chỉ có danh sách ngày dạng `dd/MM/yyyy`; thiếu lý do/khung giờ và kiểm tra booking | Chọn ngày, cả ngày hoặc khoảng giờ, lý do; lưu Timestamp UTC; giữ cách đọc ngày cũ; server chặn khóa đè booking |
| H11 — thuê tháng/quý | Có hai mức giá; thiếu tháng bắt đầu và điều khoản gia hạn | Chọn tháng bắt đầu, quy ước ngày 1; chỉ cho đặt kỳ có giá; tháng lịch 1/3 tháng, không nhân 30 ngày |
| H12 — quản lý/sửa phòng (P1) | Có sửa nhưng chưa bảo vệ booking; lỗi upload có thể bị thay bằng ảnh mẫu | Sửa đi qua server; giữ snapshot booking; thay đổi nhạy cảm gửi duyệt lại; chặn đổi mode/lịch khi còn booking; upload lỗi được báo đúng |
| H13 — yêu cầu thuê | Có danh sách và thông tin cơ bản; thiếu snapshot cọc/payment và session guard | Session HOST; lọc riêng chờ/xác nhận/từ chối/hủy; hiển thị giá/cọc/chính sách lúc đặt, trạng thái thanh toán, giờ Việt Nam |
| H14 — chấp nhận/từ chối | Transaction chỉ đổi status; chưa có lock chống trùng, kiểm tra transition/lý do đầy đủ | Gọi service chung; yêu cầu phải có lock; kiểm tra owner/status/conflict; giữ lock khi accept, nhả khi reject/cancel; chống bấm lặp |
| H15 — lịch đã thuê (P1) | Chưa có lịch phân biệt trạng thái | Xem theo phòng/ngày, phân biệt còn trống, khóa, chờ giữ chỗ, xác nhận; làm mới sau thao tác/quay lại; không hiện trống khi chưa tải xong |
| H16 — tiện ích/chính sách | Có tiện ích/nội quy chung; thiếu chính sách thuê/gia hạn rõ ràng | Form chính sách check-in/đổi-hủy/cọc/phụ phí/bãi xe/thiết bị; hiển thị ở chi tiết/đặt thuê; snapshot giữ nguyên theo booking; không tự hứa hoàn tiền |
| H17 — ngừng đặt mới (P1) | Dùng SUSPENDED cho cả Host và Admin; thay đổi gộp vào lưu phòng | Host dùng INACTIVE, có nút lưu trạng thái riêng và cảnh báo giữ lịch sử; không tự mở SUSPENDED của Admin |

Các mục trên đã có mã triển khai và kiểm thử cục bộ. **Chưa nghiệm thu hoàn tất trên Firebase thật** vì service/rules/index mới chưa được triển khai; cũng chưa kiểm thử UI trực tiếp trên thiết bị.

## Kiến trúc và nội dung bài giảng

Android giữ **Activity/XML → ViewModel → Repository → Model**. Các lớp mới chính: `BookingViewModel`, `BookingRepository`, `RoomBlackout`; dùng tiếp `Room`, `Booking`, `HostViewModel`, `HostRepository`. Không thêm DTO, use-case layer, dependency injection framework hoặc kiến trúc khác. Repository chuyển phản hồi Firebase thành Model; ViewModel quản lý trạng thái, Activity hiển thị và nhận tương tác.

Các lớp `RoomListing`/`RoomRepository.Page` đã tồn tại từ Đợt 1; không tạo thêm lớp truyền dữ liệu tương tự cho phần mới. `HostScheduleEditor` chỉ gắn các control của form; `HostSchedule` chỉ kiểm tra ngày/giờ và tạo nội dung lịch.

- Buổi 2 — `02.1 Activities and Intents`: mở màn bằng Intent và ID phòng.
- Buổi 2 — `02.2 Activity lifecycle and state`: ViewModel, saved state của lựa chọn/ngày/ảnh/khóa lịch; không thêm snapshot listener kéo dài.
- Buổi 4 — `04.2 Input controls`: checkbox/chip nhiều lựa chọn, RadioGroup, nhập số/chính sách.
- Buổi 4 — `04.3 Menus and pickers`: DatePickerDialog, TimePickerDialog, dialog xác nhận và lý do từ chối.
- Buổi 4 — `04.5 RecyclerView`: danh sách booking thật và các trạng thái.
- Buổi 9 — `10.1_ Room, LiveData, and ViewModel`: tách UI, ViewModel, Repository; Firestore tiếp tục là dữ liệu chính.

Theo yêu cầu bổ sung trong hội thoại, service chung nằm trong `functions/`. Đây là phần server tối thiểu để bảo vệ ghi booking, không thay kiến trúc Android. Tham khảo API chính thức: [Firebase callable functions](https://firebase.google.com/docs/functions/callable), [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions).

## Hợp đồng booking chung

Project ứng dụng hiện có: `broom-sharing-firebase-mobile`. Database giữ nguyên **`broomsharing`**; không tạo lại Firebase project, không đổi `google-services.json` hoặc kết nối Authentication/Firestore/Storage của ứng dụng.

Callable ở `asia-southeast1`:

| Function | Công việc |
| --- | --- |
| `quoteBooking` | Kiểm tra lịch/giá trên server, trả tiền dự kiến; chưa giữ chỗ |
| `createBooking` | Kiểm tra phiên/role/trạng thái host-room-building, lịch và giá; tạo booking + lock + event trong một transaction |
| `transitionBooking` | Host xác nhận/từ chối; Guest hủy trước giờ bắt đầu theo chính sách demo |
| `saveHostRoom` | Kiểm tra chủ sở hữu, thông tin phòng và xung đột với booking khi sửa lịch |
| `setHostListing` | Host mở/tạm ngừng nhận đặt; không thay booking hoặc mở khóa Admin |
| `getPublicHost` | Trả tên và tình trạng Host công khai cần cho danh sách; không cho guest đọc nguyên hồ sơ users |

- Khoảng thuê `[startAt, endAt)`, giờ Việt Nam ở UI, UTC Timestamp trong Firestore.
- Một phòng chỉ có HOURLY hoặc FIXED. Còn booking chiếm chỗ thì không đổi mode/tòa nhà/lịch hoạt động. Có thể sửa giá/nội dung/ảnh, snapshot cũ không đổi; phòng quay về nháp/chờ duyệt.
- `PENDING` **đã giữ chỗ**, chưa xác nhận sử dụng. Canonical slot 30 phút nằm trong `availabilityDays`; kỳ cố định nằm trong `fixedMonthLocks`. Từ chối/hủy chỉ nhả khóa còn thuộc đúng booking.
- Mọi thao tác booking và sửa cấu hình đều đọc/ghi revision của cùng phòng để tránh đua giữa sửa lịch và tạo booking.
- Mã booking xác định theo UID + idempotency key. Gửi lại cùng yêu cầu trả booking cũ; tái sử dụng key cho yêu cầu khác bị từ chối.
- Server lấy giá VND nguyên từ phòng; `priceSnapshot` tách tiền thuê, phí (hiện 0), tiền cọc, tổng thuê/phí, số tiền gồm cọc và chính sách. Client không tự gán PAID.
- Thanh toán mới là `DEMO_PENDING`; không thu/hoàn tiền thật. Hủy trước giờ bắt đầu là chính sách demo được ghi ở màn xác nhận; trạng thái tiền không được tự chuyển sang REFUNDED.
- Booking cũ không có lockPlan sẽ không được client tự xác nhận/hủy bằng đường tắt; cần kiểm tra/chuyển đổi riêng trước khi dùng. Không tự migration dữ liệu thật.
- Lịch là dữ liệu đọc từ server khi mở/làm mới/sau thao tác, không phải cập nhật đẩy liên tục giữa các thiết bị. Server luôn kiểm tra lại khi ghi.
- Phí/cọc demo, chat, Maps, thông báo và thanh toán thật không được mở rộng thêm trong lượt này.

## Kiểm tra đã chạy

- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebugAndroidTest`: thành công. Có 32 unit test Android qua; lint 0 errors, còn cảnh báo (chủ yếu text/layout hiện có và nội dung UI).
- `npm test` trong `functions`: 6/6 test quy tắc thời gian/giá qua.
- Firestore Emulator: 12/12 integration test qua, gồm tranh chấp hai guest, idempotency, giá giả, quyền xử lý, nhả khóa không xóa booking thay thế, tạm ngừng/khóa host, tháng-quý, bảo toàn snapshot, đua sửa lịch với đặt phòng và chặn client ghi trực tiếp.
- Test sử dụng project demo cục bộ; không tạo booking/room/users trên project Firebase thật. Backend kiểm thử với named database `broomsharing`; kiểm thử Security Rules chạy với database mặc định của emulator do công cụ rules-unit-testing sử dụng database đó.
- AndroidTest APK đã biên dịch; **chưa chạy instrumentation hoặc kiểm tra trực quan trên thiết bị**. `adb devices` chưa có thiết bị kết nối ở thời điểm kiểm tra.
- `git diff --check`: sạch. Các thay đổi `.idea`, file kiểm thử bảo mật bị xóa sẵn và file download của người dùng được giữ nguyên, không đưa vào phần sửa này.

## Bước còn lại để dùng trên Firebase thật

Kiểm tra chỉ đọc bằng Firebase CLI trả `Failed to authenticate, have you run firebase login?`. Việc Android đã kết nối Firebase và quyền đăng nhập công cụ triển khai server là hai việc riêng. Không thay kết nối app, không tự bật billing hoặc triển khai cloud trong lần kiểm tra này.

Sau khi chủ project cho phép triển khai và Firebase CLI đăng nhập tài khoản có quyền, triển khai **cùng một đợt** functions, rules và indexes; không deploy riêng Functions rồi giữ rules mở. `firebase.json` đã trỏ tới `firestore.secure.rules`, chặn client ghi booking/lock/cấu hình phòng; file rules mở cũ được giữ nguyên để không âm thầm sửa nội dung lịch sử nhưng không còn là file được chọn triển khai.

Từ thư mục gốc:

```powershell
.\functions\node_modules\.bin\firebase.cmd login
.\functions\node_modules\.bin\firebase.cmd deploy --project broom-sharing-firebase-mobile --only functions:booking,firestore:rules,firestore:indexes
```

Không tự xóa function cũ hoặc ép triển khai nếu có xung đột. Nếu project chưa hỗ trợ runtime/billing cần thiết cho Functions thì dừng để chủ project quyết định. Không sửa billing tự động.

Sau triển khai, nghiệm thu bằng hai Guest + một Host: tạo cùng slot, xác nhận/từ chối, hủy rồi đặt lại, tháng/quý, khóa lịch trùng booking, Host INACTIVE/Admin SUSPENDED và mất mạng. Kiểm tra UI trên điện thoại, nhất là màn nhỏ và xoay màn hình. Khi function chưa sẵn sàng, app phải hiện lỗi/chưa xác nhận; không có fallback ghi trực tiếp hoặc báo thành công giả.

Ngày 19/10/2026 đã nghiệm thu các luồng booking chính bằng Auth/Firestore thật và Functions chạy cục bộ, không nâng Blaze. Kết quả Android và các giới hạn về Storage/rules/cloud được ghi riêng tại `acceptance-firebase-dot-2.md`; chưa kết luận production đã triển khai.
