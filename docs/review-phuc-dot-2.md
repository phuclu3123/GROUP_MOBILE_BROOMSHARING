# Phúc — hoàn thiện Đợt 2 (P11–P21)

Ngày rà soát: 19/10/2026. Phạm vi lấy từ sheet `03 PHÚC ADMIN API`, dòng 32–42 của bảng phân công. Đây là phần của Phúc, tách với H09–H17 của Hoàng trong `review-hoang-dot-2.md`.

## Phân biệt phần thiếu

Ở nhánh `phuc` trước khi tích hợp, booking engine chống trùng của Phúc chưa có. Màn Admin duyệt hồ sơ Host/phòng đã có từ Đợt 1, nhưng chưa đủ khóa/mở chủ-phòng, xác minh bằng chứng, audit đầy đủ và vòng đời booking của Đợt 2. Nhánh Hoàng có form Host/màn yêu cầu thuê nhưng không thay thế booking engine của Phúc.

## Đối chiếu P11–P21

| Mục | Phần hoàn thiện |
| --- | --- |
| P11 — hai mode thuê | Mỗi phòng HOURLY hoặc FIXED; cấm đổi mode/tòa nhà/lịch khi còn booking chiếm chỗ; khoảng `[start,end)` |
| P12 — availability và khóa | Slot chuẩn 30 phút trong `availabilityDays`; tháng/quý trong `fixedMonthLocks`; giờ hoạt động, ngày trong tuần, thời lượng tối thiểu và blackout được kiểm tra ở server |
| P13 — transaction | `createBooking` kiểm tra role, trạng thái chủ/phòng/tòa nhà, thời gian, giá và xung đột; booking + khóa + event + audit ghi atomically; key gửi lại không tạo booking thứ hai |
| P14 — thuê dài hạn | Bắt đầu 00:00 ngày 1 giờ Việt Nam; tháng là một tháng lịch, quý ba tháng; kiểm tra toàn kỳ, khóa từng tháng, không cho đổi mode khi có booking |
| P15 — giá server và snapshot | Giá/cọc lấy từ phòng trên server, VND nguyên; snapshot tiền thuê/phí/cọc/chính sách; không tin số tiền hoặc nhãn PAID từ client |
| P16 — duyệt Host | Admin xem hồ sơ, duyệt/từ chối, kiểm tra tài khoản; đổi application và role trong cùng transaction; chỉ server cấp HOST, có audit |
| P17 — duyệt phòng | Hàng chờ kèm ảnh, địa chỉ/tòa nhà/tọa độ, tiện ích/nội quy, giá/cọc/chính sách và tình trạng xác minh; server kiểm tra owner/building/room trước duyệt; pending không được đặt |
| P18 — khóa chủ/phòng | Admin khóa/mở Host có lý do; khóa chủ đồng thời ẩn phòng, chặn đặt mới và thao tác Host; giữ booking cũ; mở chủ không tự gỡ khóa riêng của phòng; gỡ khóa phòng về INACTIVE để Host chủ động mở lại |
| P19 — xác minh thủ công | Hàng chờ `propertyVerifications`; phân biệt IDENTITY/LOCATION/PHOTOS; xem ảnh/PDF riêng tư trước duyệt và nhập ghi chú; lưu ADMIN_REVIEWED, người/thời điểm duyệt; không có nhãn PARTNER_VERIFIED |
| P20 — audit (P1) | Admin xem 100 thao tác gần nhất; ai, đối tượng, trước/sau, lý do, timestamp server; log cùng transaction cho review, moderation, tạo/chuyển booking, sửa phòng/trạng thái nhận đặt; client không sửa/tự tạo audit |
| P21 — state machine và hủy | PENDING (yêu cầu đã giữ chỗ), CONFIRMED, REJECTED, CANCELLED, CHECKED_IN, COMPLETED; kiểm tra actor và thời điểm; khóa giữ khi xác nhận/check-in, nhả đúng booking khi từ chối/hủy/hoàn tất; payment tách riêng |

## Tổ chức mã

Giữ **Activity/XML → ViewModel → Repository → Model**. Không thêm DTO, use-case layer hay framework kiến trúc mới. Màn Admin được mở rộng bằng Spinner chọn nội dung và RecyclerView hiện có.

- `AdminDashboardActivity` / `activity_admin_dashboard.xml`: chọn hàng chờ, khóa/mở, xem lịch sử, dialog nhập lý do.
- `AdminViewModel`: trạng thái tải/xử lý, nhóm đang chọn, các bằng chứng đã mở xem.
- `AdminRepository`: đọc hàng chờ và chuyển phản hồi Firebase thành Model; gửi thao tác quản trị lên server.
- `ReviewItem`: Model hiển thị đã có từ Đợt 1, bổ sung trạng thái và tham chiếu bằng chứng.
- `EvidencePreviewActivity` / `AdminEvidenceViewModel`: xem ảnh hoặc các trang PDF; chỉ giữ file PDF tạm trong cache và xóa khi đóng; không tạo URL download công khai.
- `functions/admin-service.js`: transaction review/moderation, kiểm tra quyền admin và audit.
- `functions/service.js`: booking/giá/lịch/trạng thái dùng chung.

Cách làm dùng lại Input controls/Spinner, RecyclerView, Dialog, Activity/Intent, lifecycle/saved state, LiveData/ViewModel và Firebase từ slide. Phần server chỉ thực hiện các thao tác tin cậy cần cho booking/Admin.

## Quy ước cần biết

- Không tạo lại project hay kết nối Firebase. Project vẫn `broom-sharing-firebase-mobile`, database `broomsharing`.
- Có 10 callable trong codebase `booking`, vùng `asia-southeast1`: `quoteBooking`, `createBooking`, `transitionBooking`, `saveHostRoom`, `setHostListing`, `getPublicHost`, `reviewAdminItem`, `moderateHost`, `moderateRoom`, `getVerificationEvidence`.
- `firebase.json` chọn `firestore.secure.rules`; các ghi role/moderation/review/booking/lock/audit đều được chuyển sang server. Cần triển khai server và rules tương ứng cùng đợt; không giữ rule mở sau khi bật service.
- Mỗi thao tác khóa/mở Host xử lý tối đa 400 phòng trong một transaction; nếu vượt, báo cần quy trình quản trị theo lô, không âm thầm làm một phần.
- Hàng chờ/lịch sử đọc theo yêu cầu và có tải lại; chưa thêm push notification/chat/Maps Đợt 3. Hàng chờ tối đa 100 mục, quản lý chủ/phòng tối đa 200 mục, audit 100 thao tác mới nhất; chưa có phân trang Admin cho dữ liệu lớn.
- H22 (Host nộp bằng chứng) thuộc Đợt 3: không mở thêm flow tải giấy tờ trong lượt này. Admin xử lý các hồ sơ có `ownerUid`, `verificationType`, `roomId`/`buildingId` khi phù hợp, `status=PENDING`, và `evidenceRefs` (hoặc `evidencePath` cũ). Bằng chứng nằm trong `verification/{ownerUid}/{file}`. Không tạo dữ liệu/bằng chứng giả lên cloud để lấp hàng chờ.
- Xác minh danh tính không tự xác minh địa điểm/ảnh. Duyệt LOCATION/PHOTOS của một room chỉ cập nhật loại tương ứng; sửa lại nội dung phòng sẽ yêu cầu rà lại xác minh. Pin Maps/ảnh/mô tả không được coi là chứng nhận thực địa.
- Đặt cọc vẫn DEMO_PENDING; check-in chỉ là ghi nhận demo, không đổi trạng thái thanh toán. Hủy trước giờ bắt đầu theo policy demo; không tự hoàn tiền thật.
- Booking cũ thiếu lockPlan không được tự nhận là có giữ chỗ an toàn; cần kiểm tra/chuyển đổi riêng trước khi áp dụng vòng đời mới.

## Kiểm tra và phần còn lại

Đã kiểm tra cục bộ:

- Android: assembleDebug, testDebugUnitTest (32 test qua), lintDebug (0 errors), assembleDebugAndroidTest.
- Quy tắc thời gian/giá: 6 test qua.
- Firestore Emulator: 19 integration test qua, gồm các luồng booking và Admin P16–P21; test dùng project demo, không ghi project Firebase thật. Các kiểm thử bằng chứng xác nhận quyền và tham chiếu tệp; chưa kiểm tra đọc file từ Storage thật hoặc hình ảnh/PDF trên thiết bị.
- Kiểm thử Rules chặn cả Admin ghi role/trạng thái phòng/audit trực tiếp, chặn tài khoản không liên quan đọc booking, chặn client tự tạo/sửa booking và lock.
- Đã chạy nghiệm thu tích hợp trên Pixel 4 với Auth/Firestore thật và Functions cục bộ; chi tiết tại `acceptance-firebase-dot-2.md`.
- Cập nhật triển khai 19/10/2026: Firebase CLI đã đăng nhập thành công và có quyền trên project. Lệnh triển khai service bị Firebase chặn vì Cloud Build cần gói Blaze. Theo yêu cầu của chủ project, **tạm dừng triển khai service, không nâng Blaze**.
- Ba composite index trong `firestore.indexes.json` đã được tạo trên database `broomsharing`. Chưa phát hành 10 callable hoặc rules mới. Trong bước chuẩn bị, CLI đã bật Cloud Functions API; không có function nào được tạo bởi lần triển khai bị chặn này.
- Booking/Admin đã qua 17 ca service dùng Auth/Firestore thật và kiểm tra Android. Functions vẫn chạy cục bộ; chưa có đầy đủ service trên cloud. Storage trả 404 nên chưa nghiệm thu xem ảnh/PDF bằng chứng thật. Xem `acceptance-firebase-dot-2.md` để phân biệt phạm vi đã kiểm tra và giới hạn còn lại.

Không tự thay project, bật billing, migration dữ liệu thật, push Git hoặc xóa dữ liệu cloud. Khi triển khai, dùng project hiện có và giữ đồng bộ code/rules/index; Firebase yêu cầu quyền đăng nhập triển khai riêng với kết nối SDK của Android.
