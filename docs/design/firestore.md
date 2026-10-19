# Firestore

Project giữ nguyên `broom-sharing-firebase-mobile`; database chính là **`broomsharing`**. Không đổi sang `(default)` và không tạo database mới.

| Collection | Nội dung/quyền |
| --- | --- |
| users | Hồ sơ riêng, role và status; chủ hồ sơ/Admin đọc, không cho tự nâng quyền |
| publicHosts | Chỉ uid, name, status; đồng bộ từ hồ sơ Host, Guest dùng để ẩn chủ bị khóa |
| usernames | Ánh xạ username phục vụ đăng nhập hiện có |
| buildings / rooms | Tòa nhà và phòng; Host quản lý phòng mình, Admin duyệt/khóa |
| bookings | Booking schemaVersion 2, thời gian Timestamp UTC, tiền VND và priceSnapshot |
| availabilityMonths | Lịch chung thuê giờ/tháng/quý theo `{roomId}_{yyyyMM}` |
| hostApplications / propertyVerifications / reports | Hồ sơ và hàng chờ quản trị |
| auditLogs | Người xử lý, đối tượng, trước/sau, lý do, server timestamp |
| users/{uid}/favorites | Phòng quan tâm của từng người dùng |

## Giữ chỗ bằng transaction Java

Thuê giờ có `slotStart`, `slotCount`, `slotKeys`; mỗi slot 30 phút, tối đa một ngày. AvailabilityMonth lưu danh sách slot số nguyên `slotsBusy` và `hourlyBookings` ánh xạ bookingId → danh sách slot. Hai trường được cập nhật đồng thời và Rules kiểm tra tính nhất quán để chặn đè/nhả lịch của người khác. Thuê cố định một tháng/quý giữ `fixedBookingId` của một/ba tài liệu tháng. Tháng có slot giờ thì không được giữ toàn tháng; tháng đã giữ cố định thì không được đặt giờ. Không lưu tên/điện thoại Guest trong lịch công khai.

Tạo booking đọc lại user, Room, PublicHost, Building và các tài liệu lịch rồi mới ghi. Lịch + booking + bookingRevision của Room + audit được commit cùng transaction. Hai yêu cầu tranh chỗ được Firestore retry trên dữ liệu mới. Nếu Rules từ chối commit do lịch vừa thay đổi, Repository đọc/giao dịch lại đúng một lần để trả lỗi trùng lịch; không hạ quyền Rules. Quote chỉ đọc, không giữ chỗ.

ID booking được tạo ổn định từ UID và requestKey. Gửi lại cùng lựa chọn trả ID cũ; đổi lựa chọn sinh requestKey mới. Giá/cọc/chính sách đọc từ Room mới nhất, chụp vào booking; trạng thái thanh toán là `DEMO_PENDING`, không tự nhận đã thanh toán.

`PENDING → CONFIRMED → CHECKED_IN → COMPLETED`; Guest hủy PENDING/CONFIRMED trước giờ bắt đầu; Host từ chối PENDING với lý do. Cancel/reject/complete nhả đúng khóa của booking, không xóa lịch của người khác. Thuê cố định bắt đầu 00:00 ngày 1 theo Asia/Ho_Chi_Minh.

Host sửa phòng đọc lịch đang giữ chỗ trước khi đổi mode, ngày/giờ hoặc blackout. Khóa Host đổi users/publicHosts trong cùng transaction; Guest lọc PublicHost bị khóa, việc đặt mới kiểm tra lại chủ. Gỡ khóa riêng một phòng trả về INACTIVE để Host chủ động mở lại.

Booking theo mẫu cũ không có `monthKeys`/schemaVersion 2 vẫn đọc được nhưng không tự nhả khóa bằng cơ chế mới. Cần rà soát/chuyển dữ liệu có thật trước khi hỗ trợ thay đổi các booking đó; không tự chuyển booking cũ khi chưa đối soát. Tại lần kiểm tra trước khi nạp dataset 19/10/2026 chưa có booking nào; dataset bổ sung sau đó dùng toàn bộ schemaVersion 2. Collection lịch ngày/tháng cũ không được dùng cho booking Java mới. Nếu cloud có booking còn hiệu lực theo mẫu cũ, chưa được mở đặt mới trên cùng phòng trước khi đối soát/chuyển khóa lịch; nếu bỏ qua bước này có thể trùng với dữ liệu cũ.

## Rules và triển khai

Chỉ giữ một bộ Firestore Rules: `firestore.rules`, được `firebase.json` tham chiếu. `.rules` là ngôn ngữ phân quyền của Firebase, không phải mã JavaScript.

Ngày 19/10/2026, Rules mới đã được biên dịch và phát hành cho đúng database **broomsharing** qua Firebase Rules API bằng phiên đăng nhập quản trị hiện có. Đã đọc lại release và so khớp nguyên văn với `firestore.rules`. Những lần sửa file local sau này vẫn cần kiểm tra/phát hành lại. Không nâng Blaze hoặc dùng Cloud Functions. Storage Rules chỉ cho chủ tệp đọc bằng chứng riêng tư. [Firebase giới hạn Storage Rules chỉ đọc database mặc định](https://firebase.google.com/docs/storage/security/rules-conditions#enhance_with_firestore), nên không thể kiểm tra role từ `broomsharing` tại đây. AdminRepository đã có mã Java đọc tệp, nhưng quyền Admin xem tệp chưa cấu hình và chưa nghiệm thu; không mở công khai, không tạo database phụ hoặc thêm backend để lách giới hạn. Admin vẫn duyệt dữ liệu Firestore; chưa coi luồng kiểm chứng tệp là hoàn tất.

## Dữ liệu cloud đã bổ sung (19/10/2026)

- Đồng bộ PublicHost cho 1 Host đang có, không thay role/status của 3 tài khoản hiện hữu.
- Bổ sung 11 trường chưa tồn tại của phòng cũ: workingDays, slotMinutes, minBookingMinutes, fixedStartMonth, renewalPolicy, rentalPolicy, locationVerification, photosVerification, blackoutRanges, bookingRevision, lastBookingId. Các giá trị nghiệp vụ đã có được giữ lại.
- Tạo `buildings/demo_hcm_java_building` tại 10.7769, 106.6908 và hai phòng `rooms/demo_hcm_java_hourly`, `rooms/demo_hcm_java_fixed`, tên đều có `[DEMO]`. Giá thử: 100.000đ/giờ, 3.000.000đ/tháng, 8.000.000đ/quý; cọc 200.000đ. Đây là dữ liệu mô phỏng, không phải chào thuê thật; ảnh minh họa dùng lại từ phòng công khai có sẵn.
- Lần thiết lập ban đầu: users 3, publicHosts 1, buildings 3, rooms 3. Sau đó, theo yêu cầu bổ sung dữ liệu giả định, đã nạp dataset 157 document, bao gồm booking và lịch giữ chỗ nhất quán (xem bên dưới).
- Hai tòa nhà cũ có tọa độ 0 được giữ nguyên; không gán vị trí TP.HCM giả cho dữ liệu cũ. Có audit `JAVA_FIRESTORE_SETUP` cho thao tác bổ sung.
- Dataset mở rộng tạo tài khoản ADMIN demo riêng; không thay role của tài khoản có sẵn.
## Dataset mở rộng theo yêu cầu người dùng

Đã nạp thêm **157 document**, gồm 10 user demo (Auth thật), 8 buildings, 24 rooms, 28 bookings, 16 availabilityMonths và dữ liệu liên quan. Tổng cloud sau nạp: users **13**, publicHosts **5**, buildings **11**, rooms **27**, bookings **28**. Có Admin demo để đăng nhập quản trị. Chi tiết, bản JSON nạp lại được và vị trí mật khẩu local: [dataset TP.HCM](../../data/demo/README.md).

Đã đọc lại toàn bộ 157 document và so khớp từng trường với snapshot. Tài khoản Admin demo truy vấn được 13 users; Guest demo đọc được phòng và 7 booking của mình, bị chặn đọc hồ sơ riêng của Admin. Dữ liệu demo được giữ để làm bài; không bị xóa như tài khoản kiểm thử tạm của lần nghiệm thu trước.
