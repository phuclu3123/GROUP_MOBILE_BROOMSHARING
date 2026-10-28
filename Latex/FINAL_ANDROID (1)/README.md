# BroomSharing: nguồn báo cáo LaTeX

Bản chính là `main.pdf`, được biên dịch từ `main.tex`, các tệp trong `chapters`, `appendix.tex`, `ref.bib` và tài nguyên tại `Resources`. Không dùng `chapter4-preview` làm bản nộp.

## Phạm vi hiện tại

- Chương 1–2 đã đối chiếu nghiệp vụ hiện tại; Chương 3 là prototype/wireframe ban đầu, không phải ảnh nghiệm thu app thật.
- Chương 4 đã đối chiếu Model, Repository, Rules và schema local theo luồng thanh toán tức thời. Booking tạm giữ tối đa 5 phút, mở QR ngay và chỉ xác nhận sau khi backend nhận đủ 40% đúng hạn; Host không duyệt booking mới.
- Body Chương 2 có 9 sơ đồ; 14 trang phân rã chi tiết nằm ở Phụ lục G. Body Chương 4 có 5 sơ đồ Firestore và dài 13 trang.
- Nguồn Chương 5–7 vẫn là bản dự thảo cũ, chưa được cập nhật theo toàn bộ implementation và kết quả QA. Không coi các chương này đã hoàn thiện chỉ vì chúng xuất hiện trong PDF ghép toàn bài.
- Prototype Chương 3 có thể chứa phương án chưa triển khai; mô tả implementation và kiểm thử thực tế sẽ được viết riêng ở các chương sau.

## Biên dịch và kiểm tra

Từ thư mục gốc repository:

```powershell
python tools/visualization/compile_main_report.py
python tools/visualization/export_report.py
```

Quy trình dùng pdfLaTeX, Biber và Pygments/minted; không chuyển sang XeLaTeX. Cần cài đủ LaTeX packages, bật shell escape cho minted và có các tài nguyên đã lưu trong repository. Script build ghi vào `tmp/chapters123/build`, script export cập nhật `main.pdf` tại thư mục này và `output/pdf/BroomSharing-Bao-cao.pdf`.

`tools/visualization/verify_report_payment_20261028.py` kiểm schema, trích đoạn source, nguồn sơ đồ và các chương được giữ nguyên. Một số manifest bảo vệ/ảnh QA của lượt sửa nằm trong `tmp` local; trên máy mới cần chuẩn bị manifest kiểm định riêng trước khi chạy verifier đó. Không lấy một script kiểm định chưa có đầu vào local làm lệnh build bắt buộc.

## Nguồn hình và tài liệu tham khảo

- Chương 2: `docs/design/chapter2/*.drawio`; các PDF vector tương ứng trong `Resources/chapter2`.
- Chương 4: `Resources/chapter4/source-pages.drawio`; các PDF vector `firestore-*.pdf` và trích đoạn source tại `Resources/chapter4/code`.
- Kiến trúc Chương 5: hình đã duyệt tại `Resources/chapter5`; sẽ đối chiếu lại phần mô tả khi viết Chương 5.
- `ref.bib` quản lý chung tài liệu tham khảo theo APA/Biber, không tách danh mục riêng từng chương.

Bản PDF hiện tại có 73 trang gồm phần đầu, bảy chương, tài liệu tham khảo và phụ lục. Số trang có thể thay đổi khi hoàn thiện Chương 5–7. Cần kiểm tra lại bìa, MSSV, liên kết học phần/phân công và các mục phụ lục trước khi nộp.

Kết quả kiểm thử app và giới hạn không được suy từ hình thiết kế: xem `docs/testing/final-qa-2026-10-28.md` và `docs/testing/course-requirements-check-2026-10-28.md` tại thư mục gốc.
