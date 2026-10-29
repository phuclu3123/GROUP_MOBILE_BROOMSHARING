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
| reviews/{bookingId} | Nhận xét của chính Guest sau booking hoàn tất; mỗi booking một document, không sửa/xóa |
| users/{uid}/noticeReads | Mốc đã đọc của thông báo dẫn xuất từ booking, chat và báo cáo |

## Bổ sung nghiệp vụ ngày 24/10/2026

`PENDING` đến giờ bắt đầu được chuyển `EXPIRED` khi app đồng bộ. Transaction nhả lịch, cập nhật revision/bộ đếm phòng và ghi audit; booking vẫn được giữ trong lịch sử. Chưa có máy chủ chạy nền để bảo đảm chuyển trạng thái khi tất cả thiết bị đều đóng app. Không thêm hạn chờ 24 giờ.

Admin được hủy từng booking `PENDING` hoặc `CONFIRMED` của Host `BLOCKED`, bắt buộc lý do và audit. Không hủy hàng loạt, không tự kết thúc `CHECKED_IN`, không xác nhận thu/hoàn tiền. Guest vẫn gửi được báo cáo gắn `bookingId` từ lịch sử thuê khi phòng bị đình chỉ. Host chỉ phản hồi báo cáo `OPEN`; Admin kết luận theo luồng quản trị.

Phòng mới có `activeBookingCount=0`. Tạo booking tăng một; hủy/từ chối/hoàn tất/hết hạn giảm một trong cùng transaction. Thiếu trường ở dữ liệu cũ được hiểu là `-1`, không coi là lịch trống. Admin có thao tác đối soát theo booking và revision. Chỉ sửa cấu hình lịch khi bộ đếm bằng 0; giá và mô tả vẫn theo luồng lưu/gửi duyệt hiện có. Dataset local đã bổ sung bộ đếm cho 24 phòng, không sửa snapshot tiền hoặc nạp lại cloud.

Rules kiểm thời gian/giá trên write revision phòng, kiểm khóa lịch trên write booking/calendar và yêu cầu audit trong cùng lần commit. Phân bổ này tránh vượt giới hạn đánh giá của Rules. Lịch khóa hỗ trợ tối đa 16 ngày riêng lẻ và 8 khoảng; ngày liên tiếp nên biểu diễn bằng khoảng. Ngày cũ `dd/MM/yyyy` và ISO vẫn được nhận diện. Các ID vẫn là chuỗi, không đổi thành DocumentReference.

Index mới: `reviews(roomId ASC, createdAt DESC)`, `reports(reporterUid ASC, createdAt DESC)`, `reports(ownerUid ASC, createdAt DESC)`, `bookings(status ASC, startAt ASC)`. Rules mới và bốn index đã triển khai vào database `broomsharing` ngày 24/10/2026; cả bảy index đạt `READY`, ba index cũ được giữ nguyên. Xem [nghiệm thu nghiệp vụ](../testing/business-completion-2026-10-24.md). Bộ sơ đồ và chương báo cáo cũ cần đồng bộ sau khi chốt các tích hợp còn lại.

## Giữ chỗ bằng transaction Java

Thuê giờ có `slotStart`, `slotCount`, `slotKeys`; mỗi slot 30 phút, tối đa một ngày. AvailabilityMonth lưu danh sách slot số nguyên `slotsBusy` và `hourlyBookings` ánh xạ bookingId → danh sách slot. Hai trường được cập nhật đồng thời và Rules kiểm tra tính nhất quán để chặn đè/nhả lịch của người khác. Thuê cố định một tháng/quý giữ `fixedBookingId` của một/ba tài liệu tháng. Tháng có slot giờ thì không được giữ toàn tháng; tháng đã giữ cố định thì không được đặt giờ. Không lưu tên/điện thoại Guest trong lịch công khai.

Tạo booking đọc lại user, Room, PublicHost, Building và các tài liệu lịch rồi mới ghi. Lịch + booking + bookingRevision của Room + audit được commit cùng transaction. Hai yêu cầu tranh chỗ được Firestore retry trên dữ liệu mới. Nếu Rules từ chối commit do lịch vừa thay đổi, Repository đọc/giao dịch lại đúng một lần để trả lỗi trùng lịch; không hạ quyền Rules. Quote chỉ đọc, không giữ chỗ.

ID booking được tạo ổn định từ UID và requestKey. Gửi lại cùng lựa chọn trả ID cũ; đổi lựa chọn sinh requestKey mới. Giá và chính sách đọc từ Room mới nhất, chụp vào booking; booking mới ghi `paymentStatus=UNAVAILABLE`, khoản trả trước dự kiến là 40% tiền thuê. `Room.deposit` là trường cũ, không dùng để tính khoản này. `UNPAID/DEMO_PENDING` chỉ là giá trị mặc định/cũ được code nhận diện, không chứng minh đã thanh toán.

`PENDING → CONFIRMED → CHECKED_IN → COMPLETED`; Guest hủy PENDING/CONFIRMED trước giờ bắt đầu; Host từ chối PENDING với lý do. Cancel/reject/complete nhả đúng khóa của booking, không xóa lịch của người khác. Thuê cố định bắt đầu 00:00 ngày 1 theo Asia/Ho_Chi_Minh.

Host sửa phòng đọc lịch đang giữ chỗ trước khi đổi mode, ngày/giờ hoặc blackout. Khóa Host đổi users/publicHosts trong cùng transaction; Guest lọc PublicHost bị khóa, việc đặt mới kiểm tra lại chủ. Gỡ khóa riêng một phòng trả về INACTIVE để Host chủ động mở lại.

Booking theo mẫu cũ không có `monthKeys`/schemaVersion 2 vẫn đọc được nhưng không tự nhả khóa bằng cơ chế mới. Cần rà soát/chuyển dữ liệu có thật trước khi hỗ trợ thay đổi các booking đó; không tự chuyển booking cũ khi chưa đối soát. Tại lần kiểm tra trước khi nạp dataset 19/10/2026 chưa có booking nào; dataset bổ sung sau đó dùng toàn bộ schemaVersion 2. Collection lịch ngày/tháng cũ không được dùng cho booking Java mới. Nếu cloud có booking còn hiệu lực theo mẫu cũ, chưa được mở đặt mới trên cùng phòng trước khi đối soát/chuyển khóa lịch; nếu bỏ qua bước này có thể trùng với dữ liệu cũ.

## Báo cáo và trạng thái đã đọc — giai đoạn 3

`reports/{id}` nhận `RoomReport` gồm người báo, phòng, chủ phòng lấy từ dữ liệu phòng, loại vấn đề, mô tả, trạng thái OPEN và thời gian server. Người báo chỉ đọc báo cáo của mình; không sửa kết luận. Admin chuyển OPEN sang RESOLVED/DISMISSED, bắt buộc lý do, người duyệt và thời gian server. NO_ACTION ghi kết luận; SUSPEND_ROOM ẩn phòng; BLOCK_HOST khóa Host và đồng bộ PublicHost. Transaction ghi kết luận, chế tài và audit cùng lúc. Không thay booking hoặc trạng thái tiền. Bằng chứng tệp vẫn hoãn; không tuyên bố đã kiểm chứng ảnh/PDF riêng tư.

Trong lúc nghiệm thu, một phiên khác phát hành Rules bổ sung Host đọc/phản hồi báo cáo thuộc mình (`hostResponse/updatedAt`) và tạo hồ sơ xác minh PENDING. Bản Phúc giữ nguyên hai contract đang có trên cloud, ghép thêm kiểm tra báo cáo và receipt; không ghi đè mất phần Host. UI phản hồi Host của nhánh thành viên khác chưa được tích hợp trong lượt Phúc. Cần rà lại validation nội dung/thời gian phản hồi cùng nhóm khi ghép phần Host; lượt này không tự sửa contract đó. Phát hành Rules đã được chủ dự án xác nhận riêng; có kiểm tra phiên bản trước khi ghi để tránh đè thay đổi đồng thời.

`users/{uid}/noticeReads/{eventId}` chỉ chứa `readAt` là thời gian server. Chỉ chủ tài khoản đang hoạt động được đọc/ghi; không cho ghi trạng thái của người khác hoặc thêm nội dung thông báo giả. Event ID dựa trên booking/trạng thái/mốc chính sách; có trạng thái mới thì có mục chưa đọc mới. Nội dung thông báo không được sao chép thành một collection cho client tự gửi thay người khác. Chat tiếp tục dùng read counter bảo vệ trong conversation.

Repository chỉ phát thành công khi có xác nhận server, bỏ snapshot cache/pending writes ở luồng yêu cầu dữ liệu mới. Màn cập nhật có trạng thái đồng bộ, lỗi và thử lại; listener được tháo khi dừng màn hình. `disableNetwork()` của SDK không được dùng làm bằng chứng mạng thiết bị đã ngắt đối với transaction; kiểm thử offline phải ngắt kết nối emulator thật.

## Rules và triển khai

Chỉ giữ một bộ Firestore Rules: `firestore.rules`, được `firebase.json` tham chiếu. `.rules` là ngôn ngữ phân quyền của Firebase, không phải mã JavaScript.

Ngày 19/10/2026, Rules mới đã được biên dịch và phát hành cho đúng database **broomsharing** qua Firebase Rules API bằng phiên đăng nhập quản trị hiện có. Đã đọc lại release và so khớp nguyên văn với `firestore.rules`. Những lần sửa file local sau này vẫn cần kiểm tra/phát hành lại. Không nâng Blaze hoặc dùng Cloud Functions. Storage Rules chỉ cho chủ tệp đọc bằng chứng riêng tư. [Firebase giới hạn Storage Rules chỉ đọc database mặc định](https://firebase.google.com/docs/storage/security/rules-conditions#enhance_with_firestore), nên không thể kiểm tra role từ `broomsharing` tại đây. AdminRepository đã có mã Java đọc tệp, nhưng quyền Admin xem tệp chưa cấu hình và chưa nghiệm thu; không mở công khai, không tạo database phụ hoặc thêm backend để lách giới hạn. Admin vẫn duyệt dữ liệu Firestore; chưa coi luồng kiểm chứng tệp là hoàn tất.

## Lịch sử dữ liệu cloud đã bổ sung (19/10/2026)

Các số lượng và lần đọc lại trong hai mục lịch sử dưới đây là ghi nhận của lần thao tác trước, không phải kiểm kê cloud hiện tại ngày 23/10/2026. Bộ sơ đồ mới chỉ khẳng định phạm vi code/Rules/dataset local; không lấy số lượng lịch sử làm trạng thái hiện hành.

- Đồng bộ PublicHost cho 1 Host đang có, không thay role/status của 3 tài khoản hiện hữu.
- Bổ sung 11 trường chưa tồn tại của phòng cũ: workingDays, slotMinutes, minBookingMinutes, fixedStartMonth, renewalPolicy, rentalPolicy, locationVerification, photosVerification, blackoutRanges, bookingRevision, lastBookingId. Các giá trị nghiệp vụ đã có được giữ lại.
- Tạo `buildings/demo_hcm_java_building` tại 10.7769, 106.6908 và hai phòng `rooms/demo_hcm_java_hourly`, `rooms/demo_hcm_java_fixed`, tên đều có `[DEMO]`. Giá thử: 100.000đ/giờ, 3.000.000đ/tháng, 8.000.000đ/quý; cọc 200.000đ. Đây là dữ liệu mô phỏng, không phải chào thuê thật; ảnh minh họa dùng lại từ phòng công khai có sẵn.
- Lần thiết lập ban đầu: users 3, publicHosts 1, buildings 3, rooms 3. Sau đó, theo yêu cầu bổ sung dữ liệu giả định, đã nạp dataset 157 document, bao gồm booking và lịch giữ chỗ nhất quán (xem bên dưới).
- Hai tòa nhà cũ có tọa độ 0 được giữ nguyên; không gán vị trí TP.HCM giả cho dữ liệu cũ. Có audit `JAVA_FIRESTORE_SETUP` cho thao tác bổ sung.
- Dataset mở rộng tạo tài khoản ADMIN demo riêng; không thay role của tài khoản có sẵn.
## Lịch sử nạp dataset mở rộng theo yêu cầu người dùng

Đã nạp thêm **157 document**, gồm 10 user demo (Auth thật), 8 buildings, 24 rooms, 28 bookings, 16 availabilityMonths và dữ liệu liên quan. Tổng cloud sau nạp: users **13**, publicHosts **5**, buildings **11**, rooms **27**, bookings **28**. Có Admin demo để đăng nhập quản trị. Chi tiết, bản JSON nạp lại được và vị trí mật khẩu local: [dataset TP.HCM](../../data/demo/README.md).

Đã đọc lại toàn bộ 157 document và so khớp từng trường với snapshot. Tài khoản Admin demo truy vấn được 13 users; Guest demo đọc được phòng và 7 booking của mình, bị chặn đọc hồ sơ riêng của Admin. Dữ liệu demo được giữ để làm bài; không bị xóa như tài khoản kiểm thử tạm của lần nghiệm thu trước.

## Chat và chính sách hủy (20/10/2026)

- `conversations/{guestUid}:{hostUid}`: một hội thoại cho mỗi cặp người thuê/Host. `memberUids` phục vụ truy vấn inbox; tên là snapshot lúc tạo. Host mở từ booking, người thuê mở từ phòng hoặc booking. Hội thoại giữ nguyên khi trao đổi về phòng khác; từng tin có `roomId/roomName` để xác định ngữ cảnh.
- Subcollection `messages`: mỗi tin có UUID, người gửi, nội dung tối đa 2.000 ký tự, số thứ tự và thời gian server. Transaction ghi tin cùng preview và tăng bộ đếm tin nhận của bên kia. Retry giữ nguyên UUID; hai bên gửi đồng thời có thể đọc lại và retry một lần khi Rules thấy trạng thái vừa thay đổi. Không cho sửa/xóa tin hoặc đổi người tham gia.
- `guestReceived/hostReceived` đếm tin đến, `guestRead/hostRead` là mốc bên nhận đã đọc. Chỉ bên nhận thay đổi mốc của mình, không vượt số đã nhận. Admin và người ngoài không có quyền đọc chat riêng. Listener không trình bày snapshot cache trước khi server xác thực quyền, tránh hiện dữ liệu cache khi đổi tài khoản trên cùng thiết bị.
- Booking mới có `priceSnapshot.cancellationPolicyVersion=1`, `advancePercent=40`; `depositAmount/paymentDueAmount` là tiền thuê trả trước dự kiến, được trừ vào tổng tiền thuê. `Room.deposit` giữ để đọc dữ liệu cũ, không dùng để tính tiền trả trước của booking mới. Không sửa snapshot booking/dataset cũ.
- Thuê giờ: trước ít nhất 24 giờ hoàn 100%; từ 6 đến dưới 24 giờ hoàn 50%; dưới 6 giờ là 0%. Thuê tháng/quý: mốc tương ứng là 7 ngày và 48 giờ. Tỷ lệ tính trên tiền trả trước thực nhận. Sau khi bắt đầu không áp dụng thao tác hủy thông thường.
- Hủy ghi `cancelledAt` bằng thời gian server, `cancellationRefundPercent`, `refundAmount` và `refundStatus`, đồng thời nhả lịch trong cùng transaction. Rules tính lại tỷ lệ bằng `request.time`; UI yêu cầu xem lại nếu vừa đổi mốc. Admin không duyệt việc hủy thông thường.
- `cancelledByUid` lưu bên hủy. Host được hủy trước khi bắt đầu, bắt buộc có lý do và mức hoàn chính sách 100% đối với booking v1; chưa trả tiền vẫn là 0 thực hoàn. Host/Guest đọc cùng booking và lịch, không thay trạng thái tin đăng thành “hết chỗ” cho mọi ngày khác.
- Theo quyết định người dùng, thanh toán/hoàn tiền chưa tích hợp. Booking mới là `UNAVAILABLE`, không có thao tác tự nhận đã trả tiền. Hủy booking chưa trả tiền ghi `refundAmount=0`, `refundStatus=NOT_REQUIRED`. Booking có trạng thái thanh toán khác cần đối soát riêng, không tự hoàn tiền. Không có giữ chỗ thanh toán có hạn hoặc tự động xác nhận sau cọc trong bản này.
