# Tương thích bộ đếm phòng cũ, 25/10/2026

## Nguyên nhân

Trước đợt hoàn thiện nghiệp vụ tại `0039aac`, code ở `9a2c214` và nhánh Hoàng `c74d963` đều có Model `Booking` mặc định version 1, luồng tạo chủ động ghi version 2. Merge tại `5ae8312` không đổi hai giá trị này.

`activeBookingCount` được thêm vào Model phòng và Rules ở `0039aac`, trước merge Trúc/Hoàng. Phòng thiếu trường được đọc thành -1, nghĩa là chưa đối soát, nên không cho đổi cấu hình lịch. Lượt hoàn thiện trước đã ghi nhận giới hạn này nhưng mới nghiệm thu ba phòng riêng, chưa cập nhật đủ dữ liệu cloud cũ.

Trước chuyển đổi, cloud có 32 phòng: 29 thiếu trường, ba phòng video có bộ đếm. Trong 29 phòng, 28 document được tạo 19/10 và một document 17/10. Tất cả 71 booking đều là schema 2 và có khóa tháng; không có việc chia booking thành bản của Phúc và bản của Hoàng.

## Chuyển đổi đã thực hiện

Chủ dự án đã chấp thuận bổ sung dữ liệu tương thích. Dùng Firebase Auth Admin đang ACTIVE qua Security Rules hiện hành, không đổi Rules/index hoặc dùng credential dịch vụ để bỏ qua quyền. Công cụ và cách chạy ở [maintenance](../../tools/maintenance/README.md).

Đã lưu backup có kiểu Firestore trước lần ghi đầu tiên. Mỗi phòng được đếm theo PENDING/CONFIRMED/CHECKED_IN, kiểm liên kết hai chiều với lịch tháng, slot và lịch cố định. Đọc snapshot nhất quán rồi ghi bộ đếm cùng audit bằng atomic commit, có điều kiện updateTime; thay đổi đồng thời buộc đọc và đối soát lại.

Kết quả:

- Bổ sung 29 phòng: 20 phòng có bộ đếm 0, ba phòng có bộ đếm 1, sáu phòng có bộ đếm 2.
- Ba phòng đã có bộ đếm giữ nguyên. Cả 32 phòng có bộ đếm xác định và khớp dữ liệu giữ lịch.
- 71 booking và 29 document lịch không thay đổi, so sánh cả nội dung và metadata updateTime trước/sau.
- Không đổi giá, chế độ thuê, trạng thái duyệt/đăng tin, revision hoặc trường khác của phòng; chỉ thêm activeBookingCount và cập nhật updatedAt cho 29 phòng.
- Mỗi phòng có audit RECONCILE_ROOM, đã đọc lại actor và kết quả. Không gán toàn bộ phòng thành 0.

Receipt công khai ở [evidence](evidence/room-counter-compatibility-2026-10-25.json). Backup thô, kế hoạch, progress và readback nằm local trong `output/verification/room-counter-compatibility-2026-10-25/`, được Git bỏ qua. Backup có thể chứa dữ liệu liên hệ trong snapshot booking, nên không commit hoặc đưa vào video.

Lần ghi đầu bằng REST read-write transaction bị Firebase từ chối trước khi ghi document. Đã chuyển sang read-only snapshot và atomic commit với precondition như trên, kiểm lại ca xung đột trên emulator rồi mới chạy lại cloud. Không thay quyền tài khoản hoặc mở Rules để vượt lỗi.

## Code và kiểm thử

Model công bố rõ CURRENT_SCHEMA_VERSION=2 và LEGACY_SCHEMA_VERSION=1. Fallback đọc dữ liệu cũ vẫn là 1 để document thiếu version không bị tự coi là hợp lệ; luồng tạo mới và các guard dùng cùng hằng phiên bản hiện tại. Không đổi nội dung các booking đang lưu, không nâng số phiên bản nghiệp vụ.

Đã chạy bộ kiểm dữ liệu và emulator ở database QA riêng: 13 ca dữ liệu và một ca tích hợp. Ca tích hợp kiểm Host/Guest không tự sửa bộ đếm, phòng thiếu counter bị chặn đổi mode, phòng trống sau đối soát được đổi, phòng còn giữ chỗ vẫn bị chặn, atomic retry khi có sửa đồng thời và không nhân audit khi chạy lại. Unit test Android hiện có cũng được chạy lại; receipt ghi kết quả build/lint và kiểm tra Android SDK sau chuyển đổi.

Kết quả cuối: 77 unit test Java đạt; 14 ca dữ liệu/emulator đạt, không bỏ qua; debug/instrumentation/release unsigned build thành công; lint 0 lỗi, 581 cảnh báo. Android SDK đọc cloud trong 10,456 giây, kiểm 32 phòng: 20 phòng trống qua điều kiện đổi mode, 12 phòng còn giữ chỗ bị chặn. Đây là kiểm tra Model đọc từ SDK và BookingPolicy, không ghi thay mode thật; quyền ghi trực tiếp đã được kiểm riêng trên emulator. Kế hoạch chỉ đọc chạy lại báo 0 phòng cần sửa. File credential tạm và APK instrumentation trên emulator đã gỡ, tài khoản demo giữ nguyên.

## Nghiệp vụ giữ nguyên và việc còn mở

Không thay chính sách HOURLY/FIXED, thuê 1/3 tháng, đầu cuối loại trừ, hết hạn PENDING tại giờ bắt đầu hoặc quyền thuê của Host. Bộ đếm dựa trên trạng thái, không tự kết thúc đơn chỉ vì thời gian đã qua. Phòng trống vẫn phải đi qua luồng lưu/gửi duyệt hiện có khi đổi chế độ.

Nhánh CHECKED_IN khi Host bị khóa, phân biệt khách không đến với lượt đã sử dụng và cách thu khoản 60% còn lại vẫn cần thảo luận trước khi đổi nghiệp vụ. PENDING quá giờ được đồng bộ theo foreground; CONFIRMED qua giờ kết thúc cần thao tác hoàn tất hợp lệ. Không xử lý các trạng thái này trong lượt bổ sung counter.

Phúc nhận payment, API ảnh/tệp và mapping vào Host. Hoàng và Trúc tiếp tục UI, trạng thái lỗi và tự nghiệm thu trên các luồng thật theo [bàn giao cập nhật](phase3-phase4-handoff-2026-10-25.md). Chưa kết luận app hoàn thiện giai đoạn 3–4 chỉ từ việc chuyển đổi này.
