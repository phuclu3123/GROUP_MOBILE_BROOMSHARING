# Sơ đồ và báo cáo đang sử dụng

- Nguồn Chương 2: `docs/design/chapter2/*.drawio`. Xuất vector bằng `export_chapter2_diagrams.py`, không dựng lại bố cục từ các generator cũ.
- Nguồn Firestore Chương 4: `Latex/FINAL_ANDROID (1)/Resources/chapter4/source-pages.drawio`.
- Biên dịch toàn bài: `python tools/visualization/compile_main_report.py`.
- Xuất bản PDF đã biên dịch: `python tools/visualization/export_report.py`. Nguồn build là `tmp/chapters123/build/main.pdf`; đầu ra chính là `Latex/FINAL_ANDROID (1)/main.pdf` và `output/pdf/BroomSharing-Bao-cao.pdf`.
- Kiểm đối chiếu schema, trích đoạn và các chương được giữ nguyên: `verify_report_payment_20261028.py`.
- Khi comment/mã nguồn đổi dòng, gọi riêng hàm `snippets()` trong `sync_report_payment_20261028.py` để cập nhật trích đoạn và mốc dòng. Không chạy lại toàn bộ script để ghi đè nội dung báo cáo đã duyệt.

Các generator thử nghiệm và script sửa bố cục một lần đã chuyển sang `tmp/archive-2026-10-28/retired-visualization-tools`, có manifest và SHA-256. Không dùng thư mục này làm nguồn xuất bản. Các PDF, draw.io và ảnh nguồn được giữ nguyên.
