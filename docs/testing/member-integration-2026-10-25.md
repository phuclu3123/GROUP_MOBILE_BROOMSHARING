# Nghiệm thu bản hợp nhất Trúc và Hoàng, 25/10/2026

## Phạm vi đã ghép

Giữ lịch sử `main`/Trúc và `lam-hoang`, lấy phần bổ sung phù hợp rồi chuyển về contract đã nghiệm thu. Checkpoint nghiệp vụ trước hợp nhất là `0039aac`; merge lịch sử Trúc là `a528879`. Không force-push hoặc thay luồng booking/chat bằng các bản ghi rời của nhánh cũ.

| Nguồn | Phần được tích hợp | Điều chỉnh |
| --- | --- | --- |
| Trúc, `693688e` qua `main` | Lối tắt từ inbox đến hộp cập nhật | Dùng `BookingNoticesActivity`, nguồn booking/chat/report và `noticeReads`; giữ `memberUids`, `lastText`, sequence/counter và transaction. |
| Hoàng, `c74d963` | Lịch tháng, đổi phòng/tháng, chi tiết ngày | Dùng `[startAt,endAt)`, loại trạng thái đã nhả lịch, lọc phòng ở cả lưới và chi tiết, cập nhật theo booking server, chú giải khớp màu. Khóa/ngày nghỉ chỉ đánh dấu có hạn chế, không khẳng định cả ngày bị khóa. |
| Hoàng, `c74d963` | Model, form và danh sách hồ sơ xác minh | Dùng ViewModel/Repository riêng, ID ổn định khi retry, giữ draft qua recreate, `evidenceRefs`, owner/asset hợp lệ và thời gian server. Chưa nối upload. |
| Hoàng, `c74d963` | Thẻ báo cáo và màn giải trình Host | Dùng `RoomReport`, `ReportCenterViewModel`, `ReportRepository.reply`; chỉ OPEN, nội dung 10–2.000 ký tự, `hostRespondedAt` server. |

Hồ sơ mới chỉ là thông tin đề nghị. Danh sách hiển thị tối đa 100 document theo giới hạn query, sắp xếp trong tập đó; chưa có phân trang hoặc cam kết lấy 100 hồ sơ mới nhất toàn bộ. Hồ sơ không có bằng chứng chưa thể được Admin chấp thuận qua Repository. Tệp riêng tư, quyền Admin đọc tệp và API lưu ảnh vẫn thuộc phần Phúc tiếp tục.

Sửa thêm sai lệch nhãn báo giá: tổng tiền thuê/phí đọc `totalAmount`, khoản trả trước dự kiến đọc `paymentDueAmount`. Không gắn tiền dự kiến thành thanh toán thành công.

## Kết quả kiểm thử

Receipt gồm thống kê, hash APK và release Rules tại [evidence/member-integration-2026-10-25.json](evidence/member-integration-2026-10-25.json).

| Kiểm tra | Kết quả |
| --- | --- |
| Debug APK và APK instrumentation | Build thành công, cấu hình trỏ cloud `broomsharing`. |
| Unit test | 77 test, 10 suite, không lỗi hoặc bỏ qua. Gồm kết thúc tháng loại trừ, mốc nửa đêm, EXPIRED, CHECKED_IN, lọc phòng và khóa/ngày nghỉ. |
| Rules emulator | 14 ca hồ sơ xác minh và 13 ca hồi quy booking đạt. Có ghi trực tiếp trái quyền, tài sản người khác/không tồn tại, field cũ, trạng thái tự xác minh và kiểu bằng chứng sai. |
| Android SDK/UI trên cloud thật | 1 bài tích hợp với 5 nhóm kiểm tra, đạt sau 57,283 giây trên Pixel 4 emulator API 29. Dùng bốn tài khoản Auth đã giữ lại, không chỉ demo đăng nhập. |
| Đối soát chỉ đọc | 3 phòng, 12 booking, 6 document lịch, 4 booking còn giữ chỗ; counter, slot/tháng, audit và trạng thái kết thúc khớp. 1 review và 2 report vẫn được giữ. |
| Lint | 0 lỗi, 581 cảnh báo. Chưa coi app đã sạch cảnh báo. |
| Release | Build bản unsigned để kiểm tra R8; chưa phải bản phát hành có ký. |

Bài cloud mở lịch và đổi tháng/phòng; gửi hồ sơ qua UI sau recreate; retry không tạo thêm document; chặn Host tự xác minh và tài sản không thuộc chủ; chặn Guest đọc/gửi hồ sơ Host; chặn Admin duyệt khi thiếu bằng chứng; Admin từ chối có audit; Host giải trình có timestamp server; inbox mở đúng màn cập nhật. Đã đọc ảnh thực tế của lịch, danh sách hồ sơ, giải trình và màn cập nhật sau điều hướng.

Hai lượt thử giữ hai hồ sơ demo ở trạng thái REJECTED, lý do thiếu bằng chứng; chỉ cập nhật phản hồi trên report OPEN của bộ demo. Không tạo thêm booking hoặc thay khóa lịch. File mật khẩu tạm trên emulator và APK instrumentation đã gỡ; bốn tài khoản Auth và dữ liệu quay video vẫn giữ. Mật khẩu local không được commit.

## Rules trên cloud

Project `broom-sharing-firebase-mobile`, database `broomsharing`. Rules mới bổ sung validation tạo hồ sơ: quyền Host, tài sản cùng chủ, trạng thái PENDING, whitelist trường, độ dài/kiểu dữ liệu và timestamp server. Giữ các kiểm tra booking hiện có. Ruleset sau triển khai là `0536aa04-8f30-4984-a013-239c84d5aeff`; SHA-256 nguồn chuẩn hóa `f7b8891e7052afece493e100cfe569dbbfe5b49e96379b548381b07a158c919e`. Bảy index giữ nguyên và READY. Không ghi dữ liệu nghiệp vụ trong bước triển khai Rules.

Đường dẫn bằng chứng trong Rules đang theo contract `verification/{uid}/{file}` cũ. Khi chọn dịch vụ tệp bên thứ ba, Phúc phải chốt ID tệp/quyền truy cập rồi cập nhật đồng bộ contract, Rules, Host và Admin; không tự thay bằng URL công khai cho giấy tờ.

## Phần còn lại

Phân công và kịch bản tự nghiệm thu nằm trong [bàn giao giai đoạn 3–4](phase3-phase4-handoff-2026-10-25.md). Payment, API ảnh/tệp và luồng bằng chứng đầy đủ chưa hoàn tất. Hết hạn vẫn đồng bộ khi app hoạt động, chưa có tác vụ máy chủ nền; thông báo chưa có FCM khi app đóng. Nghiệm thu đa thiết bị thật, mạng yếu và bản release có ký còn cần làm sau tích hợp API. Các kết quả trên không được dùng để kết luận app đã hoàn thiện 100%.

APK kiểm thử nằm local tại `output/releases/member-integration-2026-10-25/`; APK, log và ảnh thử không đưa vào Git. Tài liệu LaTeX, sơ đồ và cấu hình IDE đang chỉnh riêng được giữ nguyên ngoài commit app.
