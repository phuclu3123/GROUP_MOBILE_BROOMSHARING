# Phần Phúc trong Chương 5 và 6

Phân công theo ảnh đã chốt ngày 28/10/2026: Phúc viết 5.1, 5.2, 5.3.8 và 6.4; đóng góp màn Admin cho 5.4. Chương 7 chưa viết trong lượt này. Không có nội dung viết thay User/Host của hai thành viên.

## Các file đã tách để ghép vào bản chính

| File trong `Latex/FINAL_ANDROID (1)/chapters` | Phạm vi |
| --- | --- |
| `chapter5-phuc-architecture.tex` | 5.1.1 kiến trúc và 5.1.2 luồng xử lý |
| `chapter5-phuc-services.tex` | 5.2.1 Firebase và 5.2.2 dịch vụ/thư viện hỗ trợ |
| `chapter5-phuc-admin.tex` | 5.3.8 các màn hình Admin |
| `chapter5-phuc-responsive.tex` | Phần Admin trong 5.4 |
| `chapter6-phuc-booking.tex` | 6.4.1 chọn lịch/giá và 6.4.2 QR/kết quả thanh toán |
| `chapter5-phuc-appendix.tex` | Ảnh quản trị bổ sung ở Phụ lục H |

`chapter5.tex` và `chapter6.tex` là điểm ghép. Khi chưa có bài thành viên, các mục tương ứng được bỏ qua, không in thông báo chờ hoặc nội dung mẫu vào body. Số 5.3.8 và 6.4 được giữ theo phân công, nên có khoảng số có chủ ý ở bản đang ghép dở.

## Tệp thành viên sẽ nhận

- Trúc: `chapter5-truc.tex` chứa 5.3.1 đến 5.3.6; `chapter5-truc-responsive.tex` bổ sung phần mình cho 5.4; `chapter6-truc-login.tex` chứa 6.2.
- Hoàng: `chapter5-hoang.tex` chứa 5.3.7; `chapter5-hoang-responsive.tex` bổ sung 5.4; `chapter6-hoang-registration.tex` chứa 6.1; `chapter6-hoang-publishing.tex` chứa 6.3, 6.3.1 và 6.3.2; `chapter6-hoang-usage.tex` chứa 6.5.
- Các tệp thành viên không tự thêm `\section` mở chương hoặc `\documentclass`. Khi ghép phải kiểm tra counter, nhãn hình và trích dẫn; không chỉ nối văn bản rồi coi PDF đã hoàn chỉnh.

## Ảnh và nguồn

Ảnh chính của Admin nằm ở `Resources/chapter5`; chuỗi đặt chỗ/thanh toán nằm ở `Resources/chapter6`. PNG lấy trực tiếp từ ADB, giữ nguyên số pixel, không ghép chữ hoặc trạng thái vào ảnh app. Chỉ sử dụng dữ liệu Firestore cloud theo lựa chọn của chủ nhiệm; không thay bằng fixture emulator để minh họa một giao dịch cloud.

Manifest `phuc-chapter5-6-captures.json` được ghi khi chụp ảnh, gồm thời điểm, vai trò, nguồn, độ phân giải và SHA-256. Bản chụp lại trạng thái đã trả tiền phải liên kết với booking thanh toán thật đã có; không thực hiện chuyển tiền mới và không sửa document thành PAID để có ảnh.

Ảnh User/Host chụp hỗ trợ, nếu có, được giao dưới dạng ảnh và thông tin nguồn; không kèm bài viết thay thành viên.

## Bản ghép đã kiểm tra

`main.pdf` đã được xuất lại từ toàn bộ dự án LaTeX, không xuất riêng Chương 4. Bản ghép hiện có 80 trang; phần Phúc của Chương 5–6 chiếm 11 trang ở vị trí vật lý 41–51. Chương 4 vẫn 13 trang. Bài của Trúc/Hoàng chưa ghép nên tổng trang sẽ thay đổi khi nhận nội dung thành viên.

Mỗi nhóm ảnh Admin, QR/kết quả và Phụ lục H có đoạn mô tả nội dung và ý nghĩa xử lý đi cùng ảnh. 5.4 dùng ảnh điện thoại ngang và cặp tablet dọc/ngang, không dùng ảnh điện thoại giả làm tablet. Đã render kiểm tra các trang mới, không có Overfull, thiếu ký tự hoặc tham chiếu chưa định nghĩa trong log. Các nguồn Chương 1–4 và 7 giữ nguyên hash.

Ảnh hỗ trợ thành viên nằm trong `Resources/chapter5/members/`, manifest ghi riêng vai trò/AVD/độ phân giải. Chỉ ảnh có mặt trong manifest và đã xem trực tiếp mới được giao làm minh họa. Không chèn ảnh trạng thái tải hoặc lỗi vào báo cáo để thay cho màn chức năng hoàn chỉnh.

Đã có ảnh Host tổng quan dọc/ngang, không gian, tòa nhà, booking và lịch tháng trên tablet API31 x86_64. Nhóm ảnh `user-tablet-map-*` chụp giao diện thuê bằng tài khoản Host chuyển chế độ: bản đồ dọc/ngang, bảng danh sách đã mở rộng và điểm đến/tuyến đường. Role trong manifest vẫn là HOST. Ảnh trang chủ User dọc từng bắt trúng trạng thái tải đã được chuyển vào tmp, loại khỏi bộ giao; không dùng ảnh đó trong bài thành viên.

Kết quả responsive và lỗi tương thích AVD API29 x86 được tách tại [biên bản responsive](../testing/responsive-2026-10-28.md). Không viết vào báo cáo rằng đã thử iPad hoặc mọi tablet đều không có lỗi.

Ngày 29/10 bổ sung ảnh điện thoại ngang `host-phone-spaces-landscape`, `host-phone-schedule-landscape`, `user-phone-map-results-landscape`. Các ảnh cho thấy vùng cuộn/nút lịch Host, layout lịch ngày đã tách cột và danh sách Map mở rộng. Chúng được giao hỗ trợ thành viên, không thêm bài viết User/Host vào phần Phúc.

Phần giảm reads đã tách ghi chú và bằng chứng ở [biên bản tối ưu](../testing/firestore-read-optimization-2026-10-28.md): thử 5 phút Java chạy/app đóng, không có quét mới; Monitoring gốc ghi 0 read units của Query ở đủ năm khoảng phút. Đây không phải kết luận mọi query đều tối ưu hoặc phép thử thanh toán thật mới.

## Căn cứ nội dung

- Kiến trúc và trạng thái: Activity/XML, ViewModel/LiveData, Repository, Model, `SessionActivity`.
- Quản trị: `AdminDashboardActivity`, `AdminBookingsActivity`, `AdminViewModel`, `AdminRepository`, `ReportRepository`, `BookingRepository`.
- Thanh toán: `BookingActivity`, `BookingPaymentActivity`, `PendingPaymentViewModel`, `PaymentReminderService`; `PaymentService`, `PaymentPolicy`, `BookingHold` ở backend Java.
- Kết nối ngoài: `FirebaseAuthRepository`, `FirebaseHelper`, `MapRepository`, `CloudinaryClient`, `RemoteImageClient`, `OnlineImages`.
- Trích dẫn ngoài dùng tài liệu chính thức của Android, Firebase, MapLibre, MapTiler, openrouteservice, Cloudinary, payOS và nguồn icon Microsoft; được gộp trong `ref.bib` chung.

Nguồn Chương 1–4 và Chương 7 được kiểm hash trước/sau. Sơ đồ kiến trúc đã duyệt được giữ nguyên bố cục và asset; không dựng lại thành một mẫu mới.
