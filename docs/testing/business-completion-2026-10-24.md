# Nghiệm thu nghiệp vụ BroomSharing, 24/10/2026

> Bổ sung sau triển khai: [nghiệm thu booking và nghiệp vụ trên cloud](cloud-business-acceptance-2026-10-24.md) đã đạt bằng tài khoản thật được giữ lại để quay video. Các kết quả emulator và triển khai bên dưới là mốc trước của cùng đợt.

Phạm vi đợt này là luồng nghiệp vụ trong app Java/XML và bảo vệ dữ liệu Firestore. Thanh toán và tích hợp lưu ảnh tiếp tục để riêng theo quyết định của nhóm. Sheet phân công là tài liệu đối chiếu; các kết luận dưới đây dựa vào code và lượt kiểm thử ghi nhận, không coi mọi dòng trong sheet là yêu cầu bắt buộc.

## Chính sách đã chốt và hành vi triển khai

| Luồng | Hành vi |
| --- | --- |
| Chờ duyệt quá giờ | `PENDING` hết hạn tại giờ bắt đầu, chuyển `EXPIRED`, nhả đúng lịch và giữ lịch sử. Không thêm hạn chờ 24 giờ. Việc đối soát chạy khi app nghe dữ liệu, khi Admin mở danh sách và theo nhịp foreground; chưa có job máy chủ lúc tất cả app đóng. |
| Tạo/chuyển trạng thái | Booking, lịch tháng, revision/bộ đếm phòng và audit cùng transaction. Retry cùng ý định không tạo thêm booking, tin nhắn hoặc nhả lịch hai lần. |
| Host bị khóa | Thu hồi giao diện đang mở, giữ lịch sử. Admin hủy từng đơn `PENDING`/`CONFIRMED` với lý do; không hủy hàng loạt. `CHECKED_IN` giữ để xem xét riêng, không tự kết thúc. |
| Sửa lịch phòng | Khi còn booking giữ chỗ, chặn thay tòa nhà, mode, giờ/ngày làm việc, slot, thời lượng tối thiểu và blackout. Giá/mô tả vẫn theo luồng lưu/gửi duyệt. Phòng cũ thiếu bộ đếm dùng `-1`, cần Admin đối soát; không giả định không có booking. |
| Hoàn tất và đánh giá | Host hoàn tất khi đã qua giờ kết thúc. Guest đánh giá 1–5 sao, nội dung 10–1.000 ký tự sau `COMPLETED`. Document `reviews/{bookingId}` chỉ tạo một lần; gửi lại cùng nội dung trả ID cũ, không sửa/xóa. |
| Báo cáo | Guest gửi báo cáo từ phòng hoặc booking đã thuê. Ngữ cảnh booking vẫn gửi được khi phòng bị đình chỉ. Host phản hồi báo cáo `OPEN`; Admin kết luận, lưu audit và cập nhật phòng/Host theo hành động đã chọn. Không tự phân xử tiền. |
| Thông báo | Ghép cập nhật booking, chat và báo cáo từ nguồn gốc; receipt nằm dưới UID. Không thêm collection nội dung trùng lặp, không có push khi app đóng. |
| Lịch thiết bị | Mở Calendar Intent cho booking đã xác nhận/còn thời gian sử dụng. Người dùng xác nhận trong ứng dụng Lịch; sự kiện đã lưu không tự đổi theo lần hủy sau này. |
| Tiền | Booking mới vẫn `UNAVAILABLE`, trả trước dự kiến 40%. Không tích hợp thu/hoàn tiền; nhánh hủy hiện ghi `refundAmount=0`, `refundStatus=NOT_REQUIRED`. |

Host dashboard nghe thay đổi phòng, tòa nhà và booking theo vòng đời màn hình. Trạng thái bị khóa hoặc đổi role được theo dõi bằng hồ sơ của chính tài khoản; Rules vẫn là hàng rào đọc/ghi. Bổ sung comment tiếng Việt ở 55 file cùng các đoạn transaction/callback quan trọng; bước thêm comment riêng đã so sánh token thực thi để tránh đổi logic do chú thích.

## Kết quả kiểm thử

| Kiểm tra | Kết quả |
| --- | --- |
| Unit test | 71 test trong 9 bộ, 0 lỗi, 0 thất bại, 0 bỏ qua. Gồm biên hủy, hạn tại giờ bắt đầu, điều kiện review, lịch thiết bị và sửa lịch phòng có giữ chỗ/dữ liệu cũ. |
| Android cohort | `FirestoreRepositoryTest` và `AuthFormValidationTest`: 4 bài đạt trên Pixel 4 API 29. SDK nghiệp vụ dùng Firebase Auth thật và Firestore emulator của named database. |
| Chuỗi vòng đời bổ sung | SDK xác nhận check-in của booking FIXED đang trong tháng, hoàn tất booking đã check-in, hết hạn qua listener, giữ document lịch sử, nhả lịch đúng một lần và Admin hủy cả đơn chờ lẫn đơn đã xác nhận. Dùng fixture lịch sử ở emulator cho các mốc thời gian; không chờ cả tháng hoặc giả thời gian server của cloud. |
| Rules ngoài Repository | 13 ca dương/âm đạt: 16 ngày/8 khoảng khóa, ngày ISO/ngày cũ, giới hạn slot kỹ thuật, slot giả, giá giả, quý qua năm mới, tháng cuối bị khóa, đầu tháng kết thúc loại trừ, thiếu write phòng, thiếu audit. |
| Quyền trong SDK | Guest không tự xác nhận, không sửa giá/role; Host không tự mở phòng bị đình chỉ, không sửa lịch khi còn giữ chỗ, không sửa bộ đếm; không đánh giá thay người khác, không sửa/xóa review; không tự đóng báo cáo; Admin không mặc định đọc chat riêng. |
| Giao diện | Mở thật màn đánh giá, báo cáo, thông báo, dashboard Host; xoay màn hình đánh giá/báo cáo; kiểm dữ liệu server; Admin khóa Host khi màn Host còn mở. Đã xem ảnh chụp sau test. |
| Chat/receipt | Gửi và retry cùng message ID, preview/sequence/counter, mốc đọc của đúng thành viên; từ chối tài khoản ngoài hội thoại, Admin đọc chat và gửi đến Host bị khóa. Receipt khác UID bị từ chối. |
| Calendar | Kiểm thời gian, tiêu đề và việc chuyển `ACTION_INSERT` bằng ActivityMonitor. Không ghi một sự kiện thật vào lịch thiết bị trong test. |
| Dataset | Biên dịch công cụ Java và chạy validator quan hệ trên snapshot local. 24 bộ đếm phòng khớp 13 booking đang giữ chỗ; không sửa snapshot tiền hay nạp lại cloud. |
| Build | Debug và release unsigned build được; lint debug 0 lỗi, 503 cảnh báo. Release chưa có khóa ký phát hành. |
| Compiler cloud | API kiểm tra cú pháp Rules trả về 0 issue; không thay release hoặc index khi kiểm tra này. |
| APK cloud | Đã cài bản debug host rỗng, mở Login và chạy lại 3 ca Auth UI đạt. Có một lượt chờ mở Activity timeout; cold-start kiểm lại đạt sau 9,122 giây trên emulator. Đây chưa phải nghiệm thu hiệu năng trên thiết bị thực. |

Các lượt đầu đã phát hiện transaction vượt giới hạn đánh giá biểu thức của Rules. Bản sửa phân bổ kiểm thời gian/giá tại write revision phòng, kiểm held/release tại booking/calendar và buộc audit trong cùng commit. Các ca hợp lệ lẫn ghi trực tiếp sai đã được chạy lại. Lỗi bộ kiểm thử Calendar Intent đã được sửa bằng ActivityMonitor và ca chuyển giao đã đạt. Không tính các lượt thất bại trước đó là nghiệm thu thành công.

Mỗi lượt SDK emulator tại mốc này tạo bốn tài khoản Auth tạm, xóa trong `finally`; lượt chốt ghi nhận 4/4 đã xóa, 0 lần ghi Firestore cloud. Các fixture của lượt emulator chỉ ở emulator. Lượt cloud bổ sung sau đó dùng opt-in riêng và giữ bốn tài khoản demo, xem biên bản mới ở đầu trang. Test có guard đọc BuildConfig của APK đang cài, tránh chạy nhầm cohort emulator vào APK cloud.

## Artifact và đối chiếu

- APK cài thử: `output/releases/business-completion-2026-10-24/BroomSharing-business-2026-10-24-debug.apk`. Host emulator trong bản này là chuỗi rỗng, database là `broomsharing`.
- Bản kiểm tra build phát hành: cùng thư mục, hậu tố `release-unsigned.apk`; chưa dùng để cài trực tiếp hoặc phát hành.
- Hash và kích thước APK: `output/releases/business-completion-2026-10-24/artifacts.json`.
- Log, ảnh và receipt tổng hợp: `output/verification/business-completion-2026-10-24/`. Đây là artifact local được Git bỏ qua, không chứa mật khẩu/token.
- Tái chạy: [hướng dẫn bộ kiểm thử](../../tools/testing/README.md). Receipt gọn: [business-completion-2026-10-24.json](evidence/business-completion-2026-10-24.json).

## Triển khai cloud đã xác minh

Sau chấp thuận rõ phạm vi của người dùng, đã phát hành Rules cho project `broom-sharing-firebase-mobile`, database `broomsharing` lúc 19:08 ngày 24/10/2026 (UTC+7). Release trỏ đến ruleset `f1a984f1-da45-4fd0-ab04-522e667aff7c`. Bản nguồn đọc lại từ cloud khớp file local sau chuẩn hóa xuống dòng: SHA-256 `cafebcdaafe62bedf41965bb54d5c5e691ed5464464f4594cdee5e6eacf33264`. Hash byte của file local dùng CRLF là `8e9d0f15d18f144a0197fcb68a84b483763517a5bc22d6a0390afa934e6aee62`; khác hash do cách xuống dòng, không khác nội dung Rules.

Bốn index dưới đây và ba index cũ đều đạt `READY`; tên ba index cũ vẫn giữ nguyên. Bản Rules cũ và tên ruleset đã được lưu để khôi phục nếu cần. Không ghi, xóa hoặc nạp lại document cloud. Cần cập nhật APK của nhóm cùng Rules mới do audit ID và bộ đếm giữ lịch thay đổi.

| Collection | Index mới |
| --- | --- |
| reviews | roomId ASC, createdAt DESC |
| reports | reporterUid ASC, createdAt DESC |
| reports | ownerUid ASC, createdAt DESC |
| bookings | status ASC, startAt ASC |

Đã chạy thành công bốn truy vấn REST chỉ đọc tương ứng các index mới; không trả về dữ liệu nghiệp vụ trong các truy vấn kiểm tra. Kiểm tra triển khai này dùng quyền quản trị để xác minh index. Sau đó, lượt nghiệm thu cloud trong biên bản bổ sung đã kiểm giao dịch bằng ID token từng vai trò và đối chiếu trạng thái cuối. Receipt triển khai nằm tại `output/verification/business-completion-2026-10-24/cloud-verification.json`; receipt nghiệp vụ ở thư mục `output/verification/cloud-business-2026-10-24/`.

## Phần còn lại trước mốc 27/10

1. Cài APK tương ứng cho nhóm. Lượt dữ liệu riêng trên cloud đã đạt; phòng cũ ngoài cohort vẫn cần Admin đối soát trước khi đổi cấu hình lịch, không reset dữ liệu chung để bỏ qua bước này.
2. Chốt provider và scope của thanh toán/lưu ảnh, hoàn thiện callback, quyền truy cập tệp, lỗi upload và dọn tệp. Đính kèm bằng chứng và quyền Admin xem tệp đi cùng công việc này.
3. Nghiệm thu tích hợp ba vai trò trên thiết bị thực, mạng chập chờn/offline, cài mới/nâng cấp và bản release có ký. Các ca đã đạt ở emulator không thay cho ma trận này.
4. Đối soát lại phần còn lại của sheet theo phạm vi đã chốt: KPI nâng cao là tùy chọn; không coi xác minh đối tác thực địa là chức năng đã có. Đồng bộ sơ đồ và báo cáo sau các tích hợp cuối, nhất là collection reviews và các trường mới.

Đây là mốc hoàn thiện nghiệp vụ chính, chưa phải xác nhận toàn bộ app và giai đoạn 4 đã hoàn tất. Không cam kết xử lý nền khi app đóng, thu/hoàn tiền thật hoặc tự kết thúc lượt đã check-in của Host bị khóa.
