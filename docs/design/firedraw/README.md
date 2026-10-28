# Các bản FireDraw dễ đọc

Import từng file vào canvas riêng. Tránh gộp các file vào cùng một canvas vì các view dùng lại cùng ID.

| File | Mục đích |
| --- | --- |
| [00-relationships-overview.json](00-relationships-overview.json) | Cây lưu trữ đủ 18 mẫu document; không liệt kê trường. Collection → Document → Subcollection → Document |
| [01-accounts.json](01-accounts.json) | Đầy đủ trường tài khoản, alias username, bản chiếu Host, đơn Host; favorites và noticeReads nối từ users |
| [02-booking.json](02-booking.json) | Đầy đủ trường Building, Room, Booking và lịch tháng, gồm map/array lồng |
| [03-chat.json](03-chat.json) | Đầy đủ trường hội thoại và messages; chỉ nối theo quan hệ chứa |
| [04-administration-reference.json](04-administration-reference.json) | Đầy đủ trường xác minh, báo cáo, audit, địa điểm tham khảo; payments/notifications ghi rõ Rules-only trong metadata |
| [../broomsharing-firedraw.json](../broomsharing-firedraw.json) | Bản toàn bộ: 37 node, 36 đường cây lưu trữ, đủ 18 mẫu document. Dùng tra cứu; khi thu nhỏ toàn bộ thì chữ sẽ nhỏ do nhiều trường |

Bản toàn bộ chỉ vẽ đường chứa để không trộn với quan hệ ID. Toàn bộ 46 đặc tả quan hệ nằm trong metadata.schemaInventory.relationships và [từ điển Markdown](../drawio/field-and-relationship-dictionary.md). Chưa vẽ hết các đặc tả này thành connector trong bộ hình chính. Hai contract Rules-only được đánh dấu [S-only] ngay ở nhãn collection/document.

Các file nhóm không bỏ trường trong phạm vi nhóm; hợp của bốn nhóm phủ đủ 18 mẫu document của bản toàn bộ. Source schema, tên và kiểu trường không thay đổi. Kiểm tra vị trí dựa trên kích thước ước tính bảo thủ của thẻ; chưa nghiệm thu hình render trong tài khoản FireDraw của người dùng.

Nguồn là code/Rules/dataset local, chưa phải kiểm kê trực tiếp cloud. Chỉ thay cách trình bày sơ đồ; không thay database hoặc ứng dụng.

Tạo lại từ project root: `python tools/visualization/export_firedraw.py`.
