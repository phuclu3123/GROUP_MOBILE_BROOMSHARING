# Báo cáo BroomSharing

Tệp chính: `main.tex`; bản đọc: `main.pdf`.

- `chapters/`: nội dung Chương 1–7.
- `Resources/`: ảnh thực tế, prototype, sơ đồ PDF vector và đoạn mã minh họa.
- `appendix.tex`: phụ lục, tài nguyên và phân công viết.
- `ref.bib`: tài liệu tham khảo chung.

Biên dịch trong thư mục này bằng pdfLaTeX và Biber. Đoạn mã dùng minted, cần Pygments/latexminted và cho phép shell escape.

```text
pdflatex -shell-escape main.tex
biber main
pdflatex -shell-escape main.tex
pdflatex -shell-escape main.tex
```

Không cần cấu hình Android/Firebase hoặc backend để biên dịch báo cáo. Giữ nguyên các đường dẫn tương đối khi tải nguồn lên Prism. Trích dẫn dùng APA 6th tùy chỉnh theo bản đã chốt; Chương 3 là prototype ban đầu, Chương 5–6 dùng giao diện app thực tế.

Các tệp biên dịch trung gian, bản xem riêng từng chương và công cụ Python xử lý tài liệu không theo bản nguồn cuối. Đợt dọn thư mục ngày 30/10/2026 không chỉnh nội dung hoặc byte của `main.pdf`.
