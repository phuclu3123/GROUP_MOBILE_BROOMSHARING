# Khép lượt thuê, khách không đến và quyền đánh giá

Chính sách được chốt ngày 26/10/2026. Phạm vi là nghiệp vụ booking trong đồ án; tích hợp API thanh toán và lưu ảnh thuộc lượt tiếp theo của Phúc.

## Quyết định và hành vi

| Trường hợp | Người xử lý và kết quả |
| --- | --- |
| Host bị khóa, khách vẫn sử dụng | Giữ `CHECKED_IN`. Sau `endAt`, Admin xác nhận `COMPLETED` với lý do. Không tự khép đơn khi khóa Host. |
| Host bị khóa, khách phải ngừng sử dụng do sự cố | Admin xử lý từng đơn `CHECKED_IN` thành `TERMINATED`, bắt buộc lý do. Lịch được nhả khi xác nhận khách đã ngừng sử dụng. |
| Đơn `CONFIRMED` đã hết thời gian nhưng chưa check-in | Giữ nguyên, chờ xác nhận thực tế. Host chọn khách đã sử dụng hoặc không đến. Không tự chuyển hoàn tất, không kết luận vắng tại giờ bắt đầu. |
| Host xác nhận khách không đến | Chuyển `NO_SHOW` sau `endAt`, bắt buộc lý do, nhả lịch và giữ lịch sử. Admin được làm thay nếu Host bị khóa. |
| Khách đã sử dụng nhưng Host quên ghi nhận | Sau `endAt`, Host hoàn tất kèm lý do xác minh bổ sung. Guest có thể gửi phản ánh theo booking để Admin xác minh việc sử dụng. |
| Kết luận `NO_SHOW` sai | Admin xác minh và chuyển `COMPLETED` kèm lý do. Không nhả lịch lần hai, không giảm lại bộ đếm. Nhật ký `NO_SHOW` cũ được giữ. |
| Đơn cũ `COMPLETED` thiếu xác nhận sử dụng | Không tự mở quyền tạo đánh giá mới. Admin xác minh bổ sung bằng một nhật ký `ATTENDANCE_CONFIRMED`; không sửa/xóa đánh giá đã tồn tại. |

Không tự mở khóa Host, hủy hàng loạt, sửa thời gian thuê gốc hoặc suy ra đã thu/hoàn tiền. `PENDING` vẫn chỉ hết hạn tại giờ bắt đầu như chính sách trước.

## Dữ liệu và tính nhất quán

- `usageConfirmedAt` và `usageConfirmedBy`: thời điểm server và người xác nhận việc sử dụng. Đây là mốc xác nhận trên hệ thống, không phải dữ liệu GPS hay bằng chứng đến phòng tự động. Khi bổ sung muộn không giả lập giờ check-in trong quá khứ.
- `closedAt`: thời điểm khép/xác minh hồ sơ trên hệ thống. `startAt`/`endAt` và snapshot giá được giữ nguyên; kết thúc do sự cố được ghi nhận tại thời điểm Admin thực hiện sau khi xác minh.
- Lần chuyển từ trạng thái giữ chỗ sang kết thúc ghi booking, lịch tháng, revision/bộ đếm phòng và audit cùng transaction. Nhả đúng khóa booking, giữ khóa của booking khác; FIXED quý nhả đủ ba tháng.
- Xác minh lại đơn đã kết thúc không ghi lại lịch tháng, không giảm lại `activeBookingCount`; vẫn tăng revision và lưu audit. Retry cùng trạng thái đã xử lý không tạo thêm lần ghi.
- Booking schema hiện hành vẫn là 2. Các trường xác nhận được bổ sung khi phát sinh nghiệp vụ; không nâng mọi document cũ lên một phiên bản mới và không tự suy diễn việc sử dụng từ nhãn `COMPLETED` cũ.

## Quyền đánh giá và phản ánh

Chỉ Guest của booking, tài khoản còn hoạt động, được tạo một đánh giá cho lượt có xác nhận sử dụng và đã kết thúc. `COMPLETED` phải qua `endAt`; `TERMINATED` được đánh giá sau `closedAt` dù chưa đến giờ kết thúc dự kiến. `NO_SHOW`, đơn đang sử dụng và đơn cũ thiếu xác nhận không được tạo đánh giá mới.

Guest giữ khả năng gửi phản ánh gắn `bookingId` khi phòng bị đình chỉ hoặc Host bị khóa. Admin xác minh thủ công từ thông tin các bên; đồ án không thêm QR/PIN, định vị check-in hay hệ thống tranh chấp tiền. Mọi xác nhận bổ sung có người chịu trách nhiệm và lý do; không coi việc nhấn xác nhận là bằng chứng khách quan tuyệt đối.

## Nghiệm thu

Bộ kiểm thử gồm unit test chính sách/mốc thời gian, lệnh REST trực tiếp vào Rules để thử vượt quyền, hồi quy giá/lịch giờ và tháng/quý, cùng Android Repository và giao diện Host/Admin/Guest. Dữ liệu nghiệp vụ phục vụ nghiệm thu nằm trên Firestore emulator. Tài khoản demo đăng nhập Firebase Auth thật được giữ lại; không dùng kiểm thử này làm bằng chứng đã chạy những nhánh mới trên dữ liệu cloud thật.

Kết quả đã chạy: 101 unit test, 44 ca Rules mới, 13 ca hồi quy giá/lịch và 14 ca công cụ đối soát đều đạt, không bỏ qua. Bốn ca Rules bổ sung kiểm tra lý do chỉ chứa khoảng trắng, tab hoặc xuống dòng ở các nhánh xác nhận sử dụng, khách không đến và Admin khép đơn. Một bài nghiệm thu Android trên Pixel 4/API 29 đã chạy toàn chuỗi Repository cùng giao diện Host/Admin/Guest và đạt trong 58,943 giây. Build debug, instrumentation và release unsigned thành công; Lint có 0 lỗi và 586 cảnh báo. [Biên bản máy đọc được](evidence/booking-resolution-2026-10-26.json) chứa hash APK và nguồn Rules.

APK debug dùng database cloud `broomsharing` đã được đóng gói tại `output/releases/booking-resolution-2026-10-26/BroomSharing-booking-resolution-2026-10-26-debug.apk`. Bản release là unsigned, chưa dùng thay bản debug để cài demo.

### Triển khai Rules đã xác minh

Sau phê duyệt tường minh của người phụ trách ngày 26/10/2026, ba điều kiện lý do đã được siết từ `size()>0` thành `trim().size()>0`, giữ nguyên các điều kiện Host/Admin, trạng thái, thời gian, lịch và audit. Nguồn đã qua 44 ca kiểm thử và được API compiler của Firebase chấp nhận trước khi triển khai lên project `broom-sharing-firebase-mobile`, database `broomsharing`.

Ruleset đang hoạt động: `3c6fc665-01e7-4f1d-89ef-4faf9a934074`. Đã đọc lại nguồn Rules đang triển khai và xác nhận khớp nguồn local, SHA-256 `f3cd7420ac52eea81be7c214d56e7860673a8938604c247b53c12c0f1595024b`. Bảy index vẫn `READY`, metadata không thay đổi; script triển khai không ghi document nghiệp vụ. [Biên bản triển khai](evidence/booking-resolution-rules-deployment-2026-10-26.json) lưu phiên bản trước/sau và kết quả compiler.

APK mới và Rules mới đã sẵn sàng dùng cùng nhau. Nghiệm thu thao tác của các nhánh mới được thực hiện ở emulator; xác minh cloud trong lượt này là compiler, release metadata và nguồn Rules sau triển khai, không phải chạy lại toàn chuỗi nghiệp vụ trên cloud.
