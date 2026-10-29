# Sơ đồ Chương 2 hiện tại

Cập nhật ngày 28/10/2026 theo mã app/backend tại commit `5a9c790`. Ba file nguồn trùng bản trong thư mục `drive-download-20261027T010946Z-1-001` trước khi sửa. Bản cập nhật giữ phong cách và các trang không bị ảnh hưởng; chỉ thay phần nghiệp vụ thanh toán và các chức năng liên quan.

| File chỉnh sửa được | Nội dung cập nhật |
| --- | --- |
| `usecase_diagram_v2.drawio` | Host kế thừa quyền thuê; không còn use case duyệt booking mới. Xét duyệt Host và bài đăng vẫn là nghiệp vụ Admin. |
| `usecase_decomposition_v2.drawio` | Giữ tạm 5 phút, tiếp tục cùng QR, đếm ngược, thông báo và kết quả nhận tiền/đối soát/hết hạn. |
| `workflow_diagram_v2.drawio` | Trang 2: đặt chỗ và thanh toán; trang 4: payOS/backend. Trang 1 cấp quyền Host/đăng phòng và trang 3 sử dụng/ngoại lệ được giữ nguyên. |

Các hình dùng đối tượng native draw.io, có thể kéo thả và sửa chữ; không phải ảnh nền. Không thêm tiêu đề hoặc ghi chú dài trong hình. Caption nằm trong báo cáo. PDF vector tương ứng ở `output/pdf/chapter2/` và được đưa vào `Resources/chapter2/` của LaTeX.

Yêu cầu thống nhất chiều ngang chỉ áp dụng cho bốn trang trong file workflow. Use case, phân rã chức năng, Firestore và các phần trình bày đã ổn trong LaTeX giữ bố cục đã chấp nhận, chỉ điền hoặc sửa nội dung liên quan. Use case giữ nguyên tọa độ và kiểu dáng đối tượng gốc, bao gồm các trang chi tiết theo chiều dọc. Workflow giữ các dải ngang và ID đối tượng từ file gốc.

## Nghiệp vụ đã đối chiếu

- Phòng dùng một chế độ theo giờ hoặc tháng/quý; không tự thêm thuê ngày. Khi còn booking giữ chỗ, không tùy ý đổi hình thức hoặc cấu hình lịch.
- Booking mới có `paymentFlow=INSTANT_5M`. Transaction ghi `PENDING`, khóa lịch, revision và audit; app mở QR ngay, không hiển thị đặt chỗ thành công trước khi nhận tiền.
- Hạn tối đa 5 phút từ `createdAt` trên server và không vượt `startAt`. Quay lại/tiếp tục dùng cùng mã đơn, không gia hạn.
- Backend đối soát payOS, số tiền, thời điểm ngân hàng nhận và lịch. Đủ 40% đúng hạn và lịch hợp lệ mới `PAID/CONFIRMED`. Link đã kết thúc mà chưa trả thì `EXPIRED`, nhả đúng lịch và giữ lịch sử. Chưa xác định được kết quả provider thì chưa nhả lịch mù.
- Tiền sai, đến muộn hoặc lịch không còn phù hợp được đưa vào `REVIEW_REQUIRED`/`MANUAL_REVIEW`; không tự hoàn tiền hay chi tiền cho nhiều Host.
- QR chuyển khoản khác việc ghi nhận khách đã đến. Check-in, hoàn tất, không đến, sự cố và quyền đánh giá tiếp tục kiểm tra riêng bằng điều kiện sử dụng.
- Booking cũ giữ lịch sử và nhánh tương thích trong code. Sơ đồ trình bày luồng tạo booking mới.
- Cấp quyền Host và duyệt bài đăng vẫn độc lập. Minh chứng/ảnh giấy tờ DEMO không phải xác minh pháp lý hoặc eKYC vận hành chính thức.
- Thông báo điện thoại theo dõi giao dịch đang hoạt động; không mô tả thành FCM hoặc bảo đảm hoạt động sau force-stop/reboot. Java và tunnel cần chạy khi demo.

## Kiểm tra và xuất

`export_chapter2_diagrams.py` xuất bằng draw.io, kiểm đủ chữ, font nhúng, nét vector và không có raster toàn trang. `verify_report_payment_20261028.py` kiểm ID, hai đầu đường nối, mô hình dữ liệu, trích đoạn code và bảo vệ Chương 3, 5, 6, 7.

Không chạy lại `revise_chapter2_diagrams.py` để ghi đè bản đã sửa tay: đây là trình dựng cũ trước khi đổi nghiệp vụ. `sync_payment_diagrams_20261028.py` cập nhật các trang liên quan trên bản native hiện có. Khi có thay đổi kéo thả mới, cần đối chiếu trước khi chạy công cụ cập nhật.

Lượt chỉnh tài liệu không chạy app, không tạo giao dịch và không sửa dữ liệu cloud. Nghiệm thu app thực hiện ở bước sau.
