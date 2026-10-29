# Nguồn sơ đồ Firestore đang dùng trong báo cáo

`BroomSharing-current-20261028.drawio` giữ năm trang native của bố cục được chấp nhận, cập nhật theo app Java và backend payOS tại commit `5a9c790`. Bản sao dùng để xuất nằm trong `Latex/FINAL_ANDROID (1)/Resources/chapter4/source-pages.drawio`.

Cây lưu trữ vẫn là database → collection → document; favorites, noticeReads và messages là subcollection của document cha. Payment và Review là collection gốc, liên kết với booking bằng ID; pendingPaymentIds là mảng nhúng trong lịch tháng. PaymentOrder và appConfig/payments thuộc backend; không có khóa bí mật trong sơ đồ.

Thẻ màu xám notifications chỉ là contract Rules cũ. Payments đã hiện thực nên không còn nhãn Rules-only. Nhãn SERVER trên PaymentOrder và cấu hình biểu thị quyền ghi của backend, không phải một loại document Firestore mới.

Mô hình dùng 17 collection gốc và ba mẫu subcollection đang được app/backend sử dụng; không tính notifications vào nhóm đã hiện thực. Năm trang lần lượt là tổng quan, tài khoản, tài sản/booking/thanh toán/đánh giá, chat và quản trị/cấu hình/tham khảo. Các liên kết logic được giải thích thêm trong bảng quan hệ của Chương 4.

Không dùng các bản kiểm kê ngày 23/10 làm schema hiện tại. File dữ liệu cấu trúc cập nhật là `docs/design/firestore-visualization-schema.json`; `source-manifest.json` và `code-manifest.json` ghi nguồn và mã băm của bản trong LaTeX.
