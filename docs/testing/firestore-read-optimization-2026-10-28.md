# Tối ưu reads Firestore

Ảnh Usage ngày 28/10/2026 ghi nhận khoảng 100 read units/phút dù ít thao tác. Không coi một HTTP request là một document read; số request, document và read units là các đại lượng khác nhau.

## Các điểm thiết kế đã sửa

| Điểm phát sinh tải | Cách xử lý |
| --- | --- |
| Đồng hồ QR gọi đối soát mỗi 15 giây | Đồng hồ tính tại máy; listener nhận trạng thái, đối soát khi tạo/mở lại giao dịch hoặc thao tác kiểm tra |
| Notification service đối soát trùng với màn QR | Service chỉ đếm ngược/hiển thị và nghe trạng thái, dừng khi đã có kết quả |
| Worker server vẫn quét khi không có giao dịch | Hai listener `limit(1)` nhận biết hold/payment chờ; `PaymentWorkMonitor` bỏ nhịp quét lúc rảnh |
| Lấy booking rồi mới lọc flow cũ ở Java | Đưa `status=PENDING` và `paymentFlow=INSTANT_5M` vào query |

Khi listener lỗi hoặc chưa nhận snapshot, worker vẫn có thể đối soát để tránh bỏ sót nhả lịch. Không tắt xử lý giao dịch chỉ để giảm quota. Log chỉ ghi `scans`, `idleSkips` và `workPending`, không chứa khóa hoặc nội dung thanh toán.

## Kết quả đã quan sát

Trong cửa sổ đo 20:10–20:15 ngày 28/10/2026, Java và tunnel chạy, app đóng và không tạo giao dịch mới. `scans` giữ nguyên 1, `idleSkips` tăng từ 3 lên 23. Sau khi chờ Monitoring cập nhật, năm khoảng phút đều ghi Query read units, Listen read units, realtime read units và RunQuery request count bằng 0. Chi phí khởi động được tách riêng: Query 26 units và Listen 13 units ở khoảng trước đó. Người dùng cũng đối chiếu Usage và xác nhận giảm tải.

Đây là kết quả lúc rảnh, không khẳng định thao tác app hoặc truy vấn rỗng luôn miễn phí. Log/Monitoring gốc được giữ trong `_local_archive/final-20261030/output/verification/read-cost-2026-10-28/` tại máy. Bản cuối không chạy lại truy vấn cloud hàng loạt để tránh phát sinh quota khi demo.
