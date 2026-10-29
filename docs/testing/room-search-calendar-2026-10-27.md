# Nghiệm thu tìm phòng và nhãn lịch tháng/quý

Ngày kiểm tra: 27/10/2026. Database: `broomsharing`.

## Kết luận và nguyên nhân

- Document `rooms/payos_demo_20261026_room` tồn tại trên Firestore. Phòng `APPROVED/ACTIVE`, Host công khai và tòa nhà đều `ACTIVE`. Lượt kiểm tra này chỉ đọc, không xóa hoặc tạo lại phòng.
- Trang đầu tải 20 document thô, sau kiểm tra quyền công khai còn 14 phòng. Tìm kiếm trước đây chỉ lọc 14 phòng này; phòng PAYOS nằm ở trang sau nên bị báo không có kết quả.
- Khi có tìm kiếm hoặc bộ lọc, app hiện tự duyệt các trang còn lại. Phân trang thông thường vẫn giữ nút “Xem thêm”. Không kết luận không có kết quả trước khi tải xong các trang.
- Nhãn tháng/quý đọc `availabilityMonths` qua listener Firestore. Không suy diễn trạng thái nhận tiền từ lịch booking cũ. Chỉ dùng snapshot được máy chủ xác nhận; cache chưa xác nhận hoặc lỗi đọc không được coi là còn trống.

## Nhãn lịch

Phòng tháng/quý hiển thị nhãn tại góc phải dưới ảnh và dòng kỳ thuê/ngày nhận thuê tiếp theo. Các trường hợp gồm kỳ trống, tạm giữ chờ thanh toán, đã đặt trước, đang có lịch thuê và sắp hết kỳ. Mốc sắp hết kỳ là tối đa 3 ngày. Ngày nhận thuê tiếp theo kiểm tra cả các kỳ liền kề và ngày Host khóa, trong phạm vi lịch đã tải. Phòng theo giờ không hiển thị nhãn tháng/quý.

## Bằng chứng

- [Đọc trực tiếp trạng thái phòng](evidence/instant-payment-2026-10-27/payos-persistence.json).
- [Tìm PAYOS từ trang đầu](evidence/instant-payment-2026-10-27/payos-search-fixed.png): 1 kết quả trong 25 phòng, không bấm tải thêm.
- [Tìm lại sau đóng và mở app](evidence/instant-payment-2026-10-27/payos-search-after-reopen.png): vẫn tìm thấy phòng.
- [Nhãn tháng/quý trên giả lập](evidence/instant-payment-2026-10-27/monthly-badge-final.png): “Đã đặt trước”, kỳ 01/11/2026 đến 01/02/2027, nhận thuê tiếp từ 01/02/2027.

## Kiểm tra kỹ thuật

- Android: `assembleDebug`, `testDebugUnitTest`, `lintDebug` thành công; 146 kiểm thử, không thất bại. Có 7 kiểm thử nhãn lịch, bao gồm biên 3 ngày, giữ chỗ chưa trả tiền, kỳ liền kề, blackout và lịch chưa xác định.
- Backend: 54 kiểm thử thành công, không bỏ qua; transaction chạy với Firestore emulator riêng, không ghi dữ liệu kiểm thử lên cloud.
- APK cập nhật: `output/apk/instant-payment-2026-10-27/broomsharing-debug.apk`, đã cài lên giả lập.

## Ghi chú còn lại

- Nhãn phản ánh khóa lịch công khai, không khẳng định khách thực tế đã đến nhận phòng hoặc xác nhận đã thu tiền cho dữ liệu cũ.
- `rentalPolicy` lưu trong phòng PAYOS demo vẫn có câu “sau xác nhận” từ luồng cũ. Đây là nội dung mô tả cũ, không phải cơ chế thanh toán hiện tại; lần sửa tìm kiếm này không đổi nội dung document phòng.
- Không thay đổi hoặc xóa lịch sử thanh toán thành công, giao dịch hết hạn và dữ liệu demo để quay video.
