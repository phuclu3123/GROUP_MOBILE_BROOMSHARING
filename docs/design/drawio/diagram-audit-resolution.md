# Trạng thái sau khi khôi phục bố cục cũ

Ngày 23/10/2026. Người dùng yêu cầu giữ bố cục cũ làm chính, chỉ chỉnh điểm chưa hợp lý hoặc bổ sung có căn cứ. Bộ hình bảng chọn lọc vừa thử đã bị loại và các artifact riêng của bộ đó đã được xóa, không giữ làm backup.

## Đã thực hiện

- Khôi phục đúng 5 trang với các thẻ đầy đủ trường, bố cục/kích thước/vị trí theo bản trước khi thử thiết kế mới.
- Dùng mẫu document ID trong tiêu đề thẻ để phân biệt với collection.
- Đánh dấu Rules-only tại node overview và trang Admin; thêm [S]/[D] và ghi chú giới hạn nguồn/trường cũ/snapshot.
- Giữ nội dung theo code: booking mới UNAVAILABLE; Room.deposit/pricePerDay là trường cũ; evidencePaths không tương đương evidenceRefs; Admin Storage evidence chưa hoàn tất.
- Giữ bảng Markdown đủ 225 dòng định nghĩa và 46 đặc tả quan hệ cùng nguồn/bội số/điều kiện.
- Đồng bộ lại 5 trang chính trên draw.io và đọc lại endpoint các cạnh chứa: overview 36, Accounts 12, Booking 8, Chat 4, Admin 12.
- Xóa nội dung 9 trang mới trên canvas. MCP không có lệnh xóa tab nên chúng còn dưới dạng tab Empty; file native chính chỉ gồm 5 trang.

## Còn cần bổ sung tăng dần trên bản cũ

- Chưa vẽ toàn bộ liên kết ID bằng nét đứt trên các hình chính. Bảng 46 quan hệ là tài liệu tham chiếu đầy đủ, không thay thế tuyên bố rằng tất cả đã được thể hiện bằng connector.
- Chưa giải quyết khả năng đọc nếu thu cả canvas dài/rộng xuống một trang LaTeX nhỏ. SVG/PDF mới giữ nét nhưng không tự làm chữ lớn hơn.
- Các giải thích nguồn, snapshot và trường phát sinh chưa đều nằm trực tiếp cạnh từng dòng trên hình; cần bổ sung vừa đủ, không thay toàn bộ cách trình bày.

Không thay logic ứng dụng, Rules hoặc dữ liệu cloud. Kiểm định tĩnh có giới hạn; không cam kết không còn lỗi ngoài phạm vi đã kiểm.
