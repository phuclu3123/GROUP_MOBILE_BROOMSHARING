# Rà soát và ghép nhánh Trúc — Đợt 2

Nguồn: `origin/truc` tại `82e3b3a` (commit trước: `fa694e3`), lấy từ GitHub ngày 19/10/2026. Ghép vào nhánh `phuc` sau checkpoint phần Host/Phúc `d02a763`. Không push Git hoặc triển khai Firebase trong lượt ghép này.

## Kết quả rà nhánh gốc

Trúc đã có XML cho thuê giờ/tháng/quý, chọn ngày/giờ, danh sách booking, màn kết quả và thao tác hủy. Tuy nhiên, repository khoảng 948 dòng đang tự tính giá/ghi booking và lock trực tiếp từ Android; không dùng service chung của Phúc. Model `priceSnapshot` là số, khác map snapshot đã dùng ở Host/server. Màn kết quả lấy giá/địa chỉ/thời gian từ Intent extras; không kiểm tra lại booking đã lưu. Bộ lọc đầy đủ, lịch trống thực tế và favorites chưa được nối đủ.

Giữ form/XML đặt chỗ và màn kết quả của Trúc, nối về `BookingRepository`/`BookingViewModel` chung. Không ghép hai bản zip, thay đổi IDE, form Auth không liên quan hoặc lớp SharedPreferences chưa được dùng. Giữ thay đổi IDE riêng của chủ workspace. Chỉ có một Activity đặt chỗ là `BookingActivity`; bỏ form trùng trước đó.

## Đối chiếu bảng phân công

| ID | Kết quả sau ghép |
|---|---|
| U09 — P1 | Lọc vị trí, loại, hình thức, sức chứa tối thiểu, giá tối đa; sắp xếp giá/sức chứa; hiển thị tiêu chí đang dùng và giữ trạng thái khi xoay/process recreation. Lọc trên các trang đã tải, không tạo query tổ hợp/index mới. Giá được nhóm riêng giờ/tháng/quý; nhãn nêu rõ đơn vị trên thẻ. |
| U10 — P0 | Chi tiết đọc Room/Building thật; tiện ích, địa chỉ, quy định, ngày/giờ hoạt động, chính sách thuê/gia hạn, cọc demo và nhãn xác minh đúng nguồn. |
| U11 — P0 | DatePicker/TimePicker; chặn quá khứ và start >= end. `getRoomAvailability` chỉ trả khoảng bận, không tên/UID/mã booking khách khác. Kết hợp giờ/ngày hoạt động và blackout của Host; không hiển thị trống khi tải lịch lỗi. Service kiểm tra lại khi gửi. |
| U12 — P0 | Chỉ có kỳ tháng/quý có giá hỗ trợ; bắt đầu đúng ngày 1, không âm thầm đổi ngày người dùng đã chọn. Kiểm tra xung đột trọn kỳ tại server. |
| U13 — P0 | Báo giá qua `quoteBooking`; snapshot tách tiền thuê/phí/cọc/tổng phải trả; ghi dự kiến và chưa thanh toán. Không dùng tiền client tính làm kết quả xác nhận. |
| U14 — P0 | `createBooking` chung, chặn gửi lặp, giữ request key/signature khi phục hồi form và retry cùng yêu cầu; báo lỗi service thay vì ghi trực tiếp dự phòng. |
| U15 — P0 | Danh sách theo Guest thật, bộ lọc trạng thái kể cả check-in demo; listener Firestore tháo ở onStop/onCleared; hủy qua service, tách paymentStatus với booking status. |
| U16 — P1 | Mở theo bookingId; tải booking server và kiểm tra đúng guest; hiển thị mã, địa chỉ snapshot, thời gian, giá và trạng thái thực. Không nhận giá/xác nhận thành công từ Intent, không tạo QR/PIN giả. Booking cũ thiếu địa chỉ được ghi rõ. |
| U17 — P1 | `users/{uid}/favorites/{roomId}`; lưu/bỏ ở chi tiết, danh sách mở từ Hồ sơ; Model/Repository/ViewModel riêng đơn giản. Rules kiểm tra UID, field và timestamp, chỉ lưu phòng đang công bố; vẫn bỏ lưu được khi phòng ngừng công bố. |

## Phạm vi slide và kiến trúc

- Buổi 4, `04.3 Menus and pickers`, slide 46, 52, 55–56: DatePicker/TimePicker, dialog và chọn ngày/giờ.
- Buổi 2, `02.2 Activity lifecycle and state`, slide 32–33: Bundle và `onSaveInstanceState`.
- Buổi 9, `10.1_ Room, LiveData, and ViewModel`, slide 8–9, 46–56, 65–66: Activity → ViewModel → Repository, LiveData và Model.
- XML, Spinner, RecyclerView và resource dùng hệ thống hiện có. Không thêm DTO, DI framework, use-case layer, Compose, Rx hoặc database mới. `Favorite` là model document thật, không phải lớp chuyển đổi dữ liệu.
- Giữ Firebase project và database `broomsharing`. Chỉ thêm một callable đọc lịch; không mở Maps/chat/notification/payment thật của Đợt 3.

## Giới hạn và vận hành

- Functions/rules mới vẫn chưa triển khai vì project chưa nâng Blaze theo quyết định của chủ project. Có 11 callable trong source sau khi thêm lịch trống. Không coi APK mặc định đã có backend cloud hoạt động đầy đủ.
- Quyền favorites cần bộ rules mới khi phát hành; kiểm tra quyền từ emulator không chứng minh rules hiện đang chạy trên Firebase thật.
- Favorites đọc tối đa 100 mục; tên là snapshot lúc lưu, màn danh sách nói rõ phải mở chi tiết để kiểm tra tình trạng hiện tại. Không giả định phòng cũ còn được rao.
- Tìm kiếm/sắp xếp giới hạn trên trang đã tải. Khi xoay màn hình form phải kiểm tra lại giá trước khi gửi. Nếu ứng dụng bị đóng lúc gửi không rõ kết quả, kiểm tra Đặt chỗ của tôi trước khi tạo yêu cầu khác.
- Lịch chỉ phản ánh thời điểm đọc; transaction khi gửi mới quyết định giữ chỗ. Check-in/cọc vẫn demo.
- Storage thật trả 404 ở lượt nghiệm thu trước; phần preview bằng chứng không được xác nhận lại trong lượt Guest này.

## Kiểm tra và nghiệm thu

- Android unit: 34 test, không failure/error. Firestore Emulator: 21 integration test qua, gồm quyền favorites và lịch chỉ trả khoảng bận.
- Kiểm tra cuối cấu hình mặc định: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` đều qua; lint không có lỗi. `FUNCTIONS_EMULATOR_HOST` mặc định đã trả về rỗng; máy ảo và Functions Emulator nghiệm thu đã tắt.
- Pixel 4, Auth/Firestore thật, Functions cục bộ: 2 bài Android qua trong 69,95 giây. Bài thứ nhất kiểm tra Guest–Host–Admin và audit; bài thứ hai bấm DatePicker, xem giá, gửi form, đối chiếu booking server, rồi lưu và bỏ favorites thực tế.
- Đã kiểm tra ảnh confirmation: giờ thuê 09:00–10:00, tiền thuê/phí 100.000 đ, cọc riêng 200.000 đ, trạng thái PENDING/DEMO_PENDING đúng dữ liệu fixture. Ảnh tại `build/acceptance/android-guest-confirmation.png`. Không dùng ảnh chụp sớm chưa render xong để khẳng định toàn bộ UI đã được rà trực quan.
- Các ca service live qua, gồm API lịch trống. Một lần khởi động worker `saveHostRoom` lỗi môi trường; chạy lại riêng phần còn lại qua. Máy ảo cũng từng hiện ANR của System UI (không phải crash của app); đã đóng hộp thoại và dừng Gradle daemon trước lần Android thành công. Nhật ký lỗi và retry vẫn giữ trong `build/truc-live-service.log`, `truc-live-retry.log` và `android-instrumentation.log` của lượt cuối.
- Cleanup hoàn tất: xóa 68 document thử và 4 tài khoản Auth; 6 document có sẵn giữ nguyên đường dẫn/updateTime. Favorites thử cũng được kiểm tra/dọn theo UID riêng. Nhật ký `build/truc-live-cleanup.log`, kết quả không chứa mật khẩu/token tại `build/acceptance/results.json`.

Chưa có nghiệm thu đầy đủ mọi thiết bị, xoay màn hình ở mọi bước, hoặc runtime/rules đã host trên cloud. Cấu hình mặc định không bật Functions Emulator; chỉ dùng cờ Debug khi chạy lại nghiệm thu.
