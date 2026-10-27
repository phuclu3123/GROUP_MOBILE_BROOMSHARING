# Bàn giao bản booking và thanh toán tức thời ngày 27/10/2026

## Luồng hiện tại

Booking mới dùng `paymentFlow=INSTANT_5M`. Khách chọn kỳ thuê, kiểm tra lịch và giá rồi bấm thanh toán. Transaction tạo booking `PENDING`, giữ đúng slot/tháng và mở màn hình QR ngay. Chỉ backend nhận đủ khoản trả trước 40% đúng hạn mới xác nhận booking. Không còn bước Host duyệt cho luồng mới; booking cũ giữ nhánh tương thích riêng.

Hạn giữ tối đa 5 phút từ thời gian tạo trên server và không vượt giờ bắt đầu thuê. Đóng/mở màn hình hoặc tiếp tục thanh toán không tạo đơn mới, không kéo dài hạn. Backend đối soát trạng thái provider, kết thúc QR chưa trả, nhả đúng khóa lịch và giữ lịch sử `EXPIRED`. Chưa rõ kết quả provider thì không nhả lịch mù. Tiền đến muộn, thiếu, dư hoặc trạng thái không nhất quán được đưa vào đối soát thủ công, không tự xác nhận thuê hay hoàn tiền.

Nút tiếp tục thanh toán và nhắc trong app chỉ hiện khi có payment thật còn hiệu lực và đã có QR/link. Thông báo điện thoại mặc định bật, tôn trọng lựa chọn tắt và quyền thông báo của Android. Thông báo có đếm ngược, mở lại đúng QR và phản ánh kết quả đã trả/hết hạn. Đây là service theo dõi giao dịch đang hoạt động, không phải FCM bảo đảm chạy sau force-stop hoặc khởi động lại máy.

## Các sửa đổi kèm theo

- Tìm kiếm và bộ lọc duyệt toàn bộ các trang phòng công khai, thay vì chỉ trang đã tải.
- Thẻ tháng/quý đọc lịch Firestore, hiển thị trạng thái và kỳ nhận thuê tiếp theo; mốc sắp hết kỳ là tối đa 3 ngày. Dữ liệu chưa được server xác nhận không được coi là còn trống.
- Danh sách booking sắp theo thời gian tạo mới nhất, giữ nhãn rõ cho đặt chỗ chưa hoàn tất/thành công/thất bại.
- Lịch trong form theo dõi thay đổi bằng listener; ngày/giờ đã đặt hoặc tạm giữ được phân biệt. Transaction vẫn là bước bảo vệ cuối cùng khi hai người cùng chọn.
- Gia cố kiểu dữ liệu hồ sơ trong Rules, xử lý bản ghi hỏng theo từng phòng và giữ phiên màn hình khi xoay. Bổ sung layout ngang và lưới tablet.

## Nghiệm thu

- [Kết quả kiểm thử](evidence/instant-payment-2026-10-27/unit-tests.json): 146 kiểm thử Android và 54 kiểm thử backend, không lỗi hoặc bỏ qua. Backend chạy transaction trên emulator riêng. Android build và lint thành công; APK debug đã cài trên giả lập.
- [Rules luồng tức thời](evidence/instant-payment-2026-10-27/rules-acceptance.json): 9 ca, có chặn giả trạng thái đã trả, Host duyệt vượt luồng và hai lượt đặt đồng thời.
- [Rules cloud đã triển khai](evidence/instant-payment-2026-10-27/rules-deployment.json): đọc lại trùng nội dung local; không đổi index.
- Người dùng đã tự chuyển 2.000 VND và xác nhận thành công; booking `011ea4e87c86a4608bf573102ef52e02e836ffbd92fdd0ceb305bc5567a06e3b` được quan sát `CONFIRMED/PAID`. Agent không thực hiện chuyển tiền.
- [Lượt chờ hết hạn thật](evidence/instant-payment-2026-10-27/expiry-result.json): sau khoảng 300 giây, booking `EXPIRED`, payment `EXPIRED`, chưa nhận tiền, lịch được nhả.
- [Tìm kiếm sau mở lại và nhãn lịch](room-search-calendar-2026-10-27.md) đã thao tác trực tiếp trên giả lập. Không dùng các unit test làm bằng chứng đã kiểm hết giao diện.

## Vận hành và giới hạn

Một Host demo gắn với một merchant payOS. Máy chạy Java và tunnel phải đang hoạt động để tạo QR, nhận relay và đối soát. Không phải chỉ push GitHub là backend được deploy. Xem [hướng dẫn backend](../../backend/payments/README.md); khi giữ tunnel cũ có thể cập nhật Java bằng `tools/payments/restart-server.ps1`. Tunnel mới cần cập nhật `PAYMENT_API_BASE_URL` và build lại APK.

Khóa payOS, khóa server, `.env`, thông tin đăng nhập demo và runtime không đưa lên Git. Dữ liệu phòng PAYOS, lịch sử đã trả và hết hạn vẫn giữ trên Firestore để quay video. Chưa có API hoàn tiền hoặc phân bổ tiền cho nhiều Host. Lượt kiểm tra này không khẳng định app không còn mọi lỗi trên mọi thiết bị; các ca tablet, xoay màn hình, mất mạng và dữ liệu demo cũ vẫn cần nghiệm thu theo kịch bản của nhóm.
