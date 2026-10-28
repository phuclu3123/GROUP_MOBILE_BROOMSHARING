# Sơ đồ Chương 2: phân tích chức năng và luồng nghiệp vụ

> Bản hiện tại ngày 28/10/2026 đã đổi sang thanh toán ngay và giữ lịch tối đa 5 phút. Xem [quy định hiện tại](current-payment-flow-20261028.md). Ba file `.drawio` ở thư mục này là bản đã cập nhật. Các mục kiểm định ngày 27/10 bên dưới là lịch sử trước thay đổi nghiệp vụ, không dùng làm mô tả luồng thanh toán mới.

Ngày đối chiếu: 27/10/2026. Nguồn đối chiếu là mã Android Java/XML, backend Java thanh toán và `firestore.rules` trong checkout BroomSharing, commit nền `2a20fe8`. Không thay đổi code nghiệp vụ, Rules hoặc dữ liệu cloud trong lượt chỉnh tài liệu này.

## Ba file chỉnh sửa được

| File | Nội dung |
| --- | --- |
| `usecase_diagram_v2.drawio` | 4 trang: tổng quát, Người thuê, Host, Admin. Use case dùng ellipse; association không có mũi tên; include và extend dùng nét đứt theo đúng chiều. |
| `usecase_decomposition_v2.drawio` | 15 trang: tổng quan và 14 nhóm chức năng chi tiết. Bổ sung hồ sơ/ảnh, yêu thích, hộp cập nhật và thanh toán. Các nhánh đều gắn với nút cha. |
| `workflow_diagram_v2.drawio` | 4 trang: đăng ký Host/đăng phòng; tạo và duyệt booking; sử dụng/ngoại lệ; thanh toán payOS demo. Tách luồng để không thu nhỏ một canvas quá rộng. |

Mỗi hộp, ellipse, actor và đường nối là đối tượng draw.io độc lập, không phải ảnh nền. Toàn bộ dùng Segoe UI, màu nhạt và nền trắng. PDF tương ứng nằm tại `output/pdf/chapter2/`; font nhúng, chữ và đường vẽ giữ dạng vector. Không cần chụp màn hình để đưa vào LaTeX.

## Điều chỉnh hình thức sau khi rà soát

- Bỏ tiêu đề hình, phụ đề và toàn bộ ô ghi chú diễn giải khỏi cả ba file; tên trang vẫn giữ để chọn khi chỉnh sửa. Caption được đặt trong LaTeX.
- Giữ tên BroomSharing trong ranh giới hệ thống của sơ đồ use case. Đây là nhãn của subject UML, không phải tiêu đề hình bị lặp.
- Sơ đồ tổng quát có generalization từ Host đến Người thuê, nét liền và đầu tam giác rỗng hướng về Người thuê. Các use case chung của Người thuê được Host thừa hưởng. Đơn xin quyền Host là tình huống riêng ở trang chi tiết, không gán thành chức năng chung mà Host phải thực hiện lại.
- Bỏ đường liên kết thanh toán đi vòng tạo cảm giác có khung thứ hai. payOS liên kết trực tiếp với use case thanh toán, các actor vẫn ở ngoài ranh giới.
- Trang Host phân biệt ghi nhận khách đến, hoàn tất và không đến; bỏ extend tùy tiện từ nhánh từ chối đến một use case xử lý chung.

Đối chiếu ký pháp: [IBM: ranh giới hệ thống](https://www.ibm.com/docs/en/dma?topic=diagrams-defining-boundaries-system), [IBM: generalization](https://www.ibm.com/docs/en/dma?topic=diagrams-generalization-relationships), [IBM: use case dùng hình oval](https://www.ibm.com/docs/en/dma?topic=diagrams-use-cases). Theo các hướng dẫn này, ranh giới hệ thống là hình chữ nhật; use case là ellipse; generalization áp dụng giữa các actor hoặc giữa các use case, không phải từ actor đến use case.

## Bổ sung sau kiểm tra Host, Admin và Người thuê

- Host: thể hiện riêng hủy booking cho thuê và chat với khách thuê; làm rõ xem lịch/thống kê hoạt động. Quyền thuê được kế thừa không thay thế vai trò xử lý một booking mà Host là bên cho thuê.
- Admin: thể hiện riêng xử lý booking của Host bị khóa, xác minh việc sử dụng, đối soát lịch giữ chỗ của phòng; dùng tên gỡ đình chỉ phòng để phân biệt với việc Host mở lại bài đăng. Xác minh việc sử dụng/sửa kết luận NO_SHOW không luôn yêu cầu Host bị khóa.
- Người thuê: bao quát tài khoản/hồ sơ, khôi phục mật khẩu, khám phá/yêu thích, bản đồ/chỉ đường, tạo/theo dõi/hủy booking, hộp cập nhật, thanh toán trả trước demo, chat/liên hệ, đánh giá, xin quyền Host và báo cáo. Khôi phục mật khẩu và bản đồ được hiển thị riêng để không bị ẩn trong các nhóm lớn.
- Các nguồn kiểm tra bổ sung: `ui/host/HostBookingsActivity.java`, `ui/admin/AdminBookingsActivity.java`, `repository/BookingRepository.java: reconcileRoomCount`, `utils/BookingResolutionPolicy.java`, `ui/profile/ProfileActivity.java`, `ui/room/SpaceDetailActivity.java`, `ui/auth/ForgotPasswordActivity.java` và các nhánh quyền tương ứng trong `firestore.rules`.

Các mục trên là mục tiêu nghiệp vụ đã có trong code; không tách từng nút tải lại, bộ lọc trạng thái hoặc nhập lý do thành một use case riêng.

## Kiểm định cuối sơ đồ phân rã và workflow

Đã đối chiếu 15 trang phân rã chức năng và 4 trang workflow với nguồn hiện tại. Giữ file use case đã chốt. Những điểm được sửa trong lượt rà soát cuối:

- Căn chung tâm các hộp/hình thoi cùng tuyến và các điểm neo trên cùng cột; đường nối chính ngang hoặc dọc, không chạy xuyên hộp khác. Bỏ đoạn nối bậc thang do lệch tâm.
- Phòng chỉ hiển thị khi vừa APPROVED vừa ACTIVE; bước cuối được ghi là Host mở cho thuê, không mặc định Admin duyệt là tự mở lại mọi phòng.
- Thể hiện PENDING đã giữ lịch cùng transaction với booking và audit. Có nhánh hủy hợp lệ ngay khi còn PENDING; đến giờ bắt đầu vẫn PENDING thì được đối soát EXPIRED khi đồng bộ.
- Nhãn hủy hợp lệ bao gồm Guest/Host hủy trước giờ bắt đầu; Admin có thể hủy PENDING/CONFIRMED của Host bị khóa với lý do, kể cả đã qua giờ bắt đầu nhưng chưa check-in. Không suy diễn quyền hủy CHECKED_IN thành CANCELLED.
- Check-in nằm trong kỳ thuê. Hoàn tất theo luồng thông thường sau giờ kết thúc. Xác nhận đã sử dụng muộn cần lý do; NO_SHOW chỉ sau giờ kết thúc và có lý do.
- Chỉ Admin khép CHECKED_IN thành TERMINATED khi Host bị khóa. Xác minh lại NO_SHOW/COMPLETED cũ là nhánh riêng sau khi lịch đã nhả; có căn cứ thì bổ sung xác nhận sử dụng và audit, không nhả lịch lần hai. Không đủ căn cứ thì giữ nguyên trạng thái/lịch sử.
- Thanh toán tách ba kết quả: không hợp lệ thì không cập nhật tiền; chưa xác nhận đủ thì giữ/cập nhật trạng thái giao dịch; đã nhận đủ thì ghi PAID hoặc REVIEW_REQUIRED theo kiểm tra server. Không nối thanh toán thành điều kiện tự động check-in hay hoàn tất lượt thuê.
- Bổ sung đối soát số booking giữ lịch của phòng vào cây chức năng và thể hiện kết quả cần đối soát trong nhánh thanh toán.

Nguồn đối chiếu bổ sung: `utils/BookingPolicy.java`, `utils/BookingResolutionPolicy.java`, `utils/ReviewPolicy.java`, `repository/BookingRepository.java`, `repository/AdminRepository.java`, `backend/payments/src/main/java/vn/edu/ueh/broomsharing/payment/PaymentPolicy.java`, `backend/payments/src/main/java/vn/edu/ueh/broomsharing/payment/PaymentService.java` và `firestore.rules`.

`flow-final-review.json` lưu kết quả kiểm tra tự động của từng trang. Công cụ `tools/visualization/audit_chapter2_flows.py` kiểm tra cây có một gốc, đầu nối tồn tại, tuyến đi qua hộp khác, tính ngang/dọc của các đoạn nối trong PDF và font nhúng. Toàn bộ bản render cuối được xem lại trước khi giao. Đây là kiểm định tài liệu và đối chiếu code; không thay đổi chính sách app, triển khai cloud hoặc thay thế một lượt nghiệm thu runtime.

## Các lỗi nội dung đã sửa

Các đường dẫn `model/`, `ui/`, `repository/`, `utils/` dưới đây tính từ `app/src/main/java/vn/edu/ueh/phuclu/broomsharing/`; các đường dẫn khác tính từ thư mục gốc dự án.

| Nội dung nguồn chưa đúng | Điều chỉnh | Căn cứ trong code |
| --- | --- | --- |
| Ghép thanh toán với vé QR check-in | Tách QR chuyển khoản payOS khỏi việc Host xác nhận khách đến; không mô tả quét vé QR | `ui/booking/BookingPaymentActivity.java`, `repository/BookingRepository.java`, `utils/BookingResolutionPolicy.java` |
| Chọn gói giờ/ngày/tháng, nhập số khách thực tế | Đặt theo hình thức của phòng: giờ hoặc 1/3 tháng. Không thêm luồng thuê ngày hay trường số khách trong booking | `utils/BookingPolicy.java: plan`, `ui/booking/BookingActivity.java` |
| Giữ lịch tạm chờ cọc | PENDING đã giữ lịch bằng transaction; CONFIRMED mới được mở thanh toán demo | `repository/BookingRepository.java: create`, `backend/payments/src/main/java/vn/edu/ueh/broomsharing/payment/PaymentPolicy.java: canCreate` |
| Tự hoàn 100% khi hủy trước 24 giờ | Hủy và nhả lịch được tách khỏi xử lý tiền. Tỷ lệ phụ thuộc chế độ thuê và thời điểm; chưa có API hoàn tiền | `utils/CancellationPolicy.java`, `repository/BookingRepository.java: transition` |
| Thiếu các nhánh hết hạn/không đến/sự cố | Bổ sung EXPIRED, NO_SHOW, TERMINATED và nhánh xác minh đã sử dụng | `utils/BookingPolicy.java: transition`, `utils/BookingResolutionPolicy.java`, nhánh cập nhật booking trong `firestore.rules` |
| IN_USE; phòng CLOSED; tài khoản SUSPENDED | Sử dụng CHECKED_IN; phân biệt approvalStatus với listingStatus; Host dùng ACTIVE/BLOCKED | `model/Booking.java`, `model/Room.java`, `model/UserProfile.java`, `firestore.rules` |
| Tài khoản nhận tiền riêng cho Host, giấy sở hữu/ủy quyền bắt buộc | Bỏ các trường chưa có. Tách đơn Host có hai ảnh giấy tờ DEMO khỏi minh chứng LOCATION/PHOTOS | `model/HostApplication.java`, `ui/host/HostApplicationViewModel.java`, `repository/PropertyVerificationRepository.java` |
| Chiết khấu tuần, giá thuê ngày trong luồng chính | Bỏ chức năng chưa có; mỗi phòng giữ một chế độ thuê, không đổi cấu hình khi còn booking giữ chỗ | `utils/BookingPolicy.java: validateRoomChange`, `repository/HostRepository.java: saveRoom` |
| Lịch sử tìm kiếm, lọc tiện ích | Bỏ chức năng chưa có. Lọc từ khóa/khu vực/loại/chế độ/sức chứa/mức giá trên dữ liệu đã tải | `ui/room/RoomViewModel.java`, `utils/RoomFilter.java` |
| Chấm điểm sạch sẽ/tiện nghi và cộng rating trung bình vào Room | Chỉ chấm điểm tổng quan 1..5 và nhận xét; một đánh giá mỗi booking, không ghi rating tổng hợp vào Room | `repository/ReviewRepository.java`, `utils/ReviewPolicy.java`, `ui/review/RoomReviewsActivity.java` |
| Chỉ COMPLETED là đủ để đánh giá | Phải có usageConfirmedAt; COMPLETED hết kỳ thuê hoặc TERMINATED đã khép. NO_SHOW không được đánh giá | `utils/ReviewPolicy.java`, `firestore.rules: reviews` |
| Báo cáo tài khoản, tải chứng cứ, IN_REVIEW và Admin đọc chat đối chất | Báo cáo phòng, có thể gắn booking; Host phản hồi văn bản; OPEN → RESOLVED/DISMISSED. Không mặc định Admin đọc chat riêng | `repository/ReportRepository.java`, `repository/AdminRepository.java`, `firestore.rules: conversations/reports` |
| Báo cáo doanh thu, lọc audit theo ngày/hành động | Chỉ thể hiện số phòng/yêu cầu của Host, hàng đợi quản trị và nhật ký mới nhất đang có | `repository/HostRepository.java: getStats`, `repository/AdminRepository.java: load` |

## Phạm vi cần giữ đúng khi viết Chương 2

- Host vẫn có thể thuê phòng; không tách Guest/Host thành các loại tài khoản không có quyền dùng chung.
- Admin xét duyệt minh chứng demo không đồng nghĩa xác thực CCCD hay pháp lý BĐS ngoài thực tế. Không gọi đây là KYC vận hành chính thức.
- payOS hiện dùng một kênh demo đã cấu hình cho một Host; không mô tả hệ thống chi tiền cho nhiều chủ nhà, giữ tiền hộ hoặc hoàn tiền tự động.
- QR/link được tạo với hạn đến giờ bắt đầu. Các điểm mở về giao dịch đến muộn, QR cũ trên UI, điều kiện thanh toán trước check-in và phần tiền còn lại đã được ghi riêng trong `docs/testing/payos-2026-10-27.md`; không tự chốt thêm chính sách ở sơ đồ.
- EXPIRED được ghi khi app đồng bộ/đối soát, không tuyên bố có tác vụ máy chủ chạy nền tự động đúng từng giây.
- Hộp cập nhật là dữ liệu dẫn xuất trong ứng dụng, không tự thêm dịch vụ FCM/push notification hoặc collection `notifications` đang vận hành.
- Chưa sửa nội dung LaTeX Chương 2 trong lượt này. Bản chương hiện có mô tả cũ về Admin và QR; cần đồng bộ phần văn bản khi chèn các sơ đồ đã sửa.

## Kiểm tra và tái xuất

`diagram-validation.json` kiểm tra ID và hai đầu của từng đường nối. `pdf-validation.json` kiểm tra số trang, chữ không thiếu, font nhúng, đường vẽ vector và không có ảnh raster toàn trang. Bản render từng trang được xem tại `tmp/chapter2-diagrams/qa/` trước khi giao.

```powershell
python tools/visualization/revise_chapter2_diagrams.py
python tools/visualization/export_chapter2_diagrams.py
```

Các script là công cụ dựng/xuất tài liệu. Chỉnh sửa bằng kéo thả trong draw.io vẫn được; không chạy lại script dựng sau khi đã có chỉnh sửa tay nếu chưa muốn thay thế các chỉnh sửa đó.
