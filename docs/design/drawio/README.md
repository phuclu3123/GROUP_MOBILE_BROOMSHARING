# Firestore BroomSharing — bố cục chính

Theo yêu cầu người dùng, **bố cục cũ với các thẻ đầy đủ trường là bản chính**. Bản bảng trường chọn lọc vừa thử đã bị loại; không giữ làm bản dự phòng.

Mở [broomsharing-data-design.drawio](broomsharing-data-design.drawio): đúng 5 trang native, kéo thả/chỉnh sửa được.

| Trang | Nội dung |
| --- | --- |
| 00 | Cây Database → Collection → Document → Subcollection → Document; đủ 18 mẫu đường dẫn |
| Accounts | 6 mẫu document, 36 dòng định nghĩa trường |
| Booking | 4 root collection ngang cấp, 109 dòng định nghĩa gồm trường nhúng |
| Chat | conversations → document → messages → document; 26 trường |
| Admin/reference | 6 root contract, 54 trường; payments/notifications ghi Rules-only |

Giữ nguyên vị trí, kích thước và kiểu thẻ của bản cũ. Các sửa nhỏ hiện có: tiêu đề thẻ dùng mẫu document ID; Rules-only được đánh dấu ngay ở overview; [S]/[D] phân biệt nguồn đơn lẻ; chú thích trường cũ/snapshot và giới hạn triển khai. Dấu [S]/[D] là nhãn hiển thị, không thuộc tên trường lưu thật.

Nét liền hiện chỉ quan hệ chứa. [Từ điển và quan hệ Markdown](field-and-relationship-dictionary.md) giữ đủ 225 dòng định nghĩa và 46 đặc tả quan hệ, gồm nguồn/đích/bội số/điều kiện. Các liên kết ID chưa được vẽ hết thành nét đứt trong 5 hình chính; không tuyên bố phần này đã hoàn thành bằng việc chỉ ghi chú trong footer.

SVG là ảnh vector; PNG là preview. [PDF vector 5 trang](../../../output/pdf/firestore/broomsharing-firestore-diagrams.pdf) giữ bố cục canvas đầy đủ. Không thu toàn bộ trang rộng xuống khổ nhỏ rồi coi là đọc được: vấn đề cỡ chữ khi đưa vào LaTeX còn phải xử lý bằng chia vùng hình hoặc khổ trang, vẫn giữ phong cách cũ.

Nguồn: code/Rules/dataset local; không phải bản quét cloud. D-only chỉ là quan sát dataset, không nâng thành contract app. unknown/any là ký hiệu chưa xác nhận/động, không phải kiểu Firestore mới.

Tạo native: `python tools/visualization/build_firestore_diagrams.py`.

Đồng bộ 5 trang đang mở: `python tools/visualization/sync_drawio_diagrams.py`.

Xuất lại SVG/PNG/PDF từ MCP: `python tools/visualization/normalize_drawio_svg.py`.

Kiểm tra nguồn và trường native: `python tools/visualization/audit_diagram_sources.py`.

Các [phát hiện kiểm định](diagram-audit.md) được giữ để sửa tăng dần trên chính bố cục này; xem [trạng thái xử lý hiện tại](diagram-audit-resolution.md). Không đánh đồng việc khôi phục bố cục với đã giải quyết hết mọi phát hiện.

5 trang chính trên cửa sổ draw.io đã được khôi phục và đọc lại đường nối qua MCP. Chín trang của bản bị loại đã được làm trống; MCP hiện không có lệnh xóa tab. File `.drawio` chính ở trên chỉ có đúng 5 trang, không chứa các tab trống đó.
