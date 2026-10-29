# Hiệu chỉnh Chương 1–3

## Nguồn và phạm vi

- `Broom_Viet/Final report (1).pdf`: bản thành viên cho Chương 1.
- `Broom_Viet/Final report.pdf`: bản thành viên cho Chương 2 (tệp không có hậu tố số).
- `Broom_Viet/Final report (2).pdf`: bản thành viên cho Chương 3.
- Sáu ảnh nguyên bản trong `Broom_Viet/drive-download-20261027T031129Z-1-001`.
- Bộ PDF vector và draw.io đã kiểm định tại `docs/design/chapter2` và `output/pdf/chapter2`.

Giữ tuyến nội dung của các bản thành viên: giới thiệu, tính cấp thiết, công cụ và kiểm thử ở Chương 1; tác nhân, yêu cầu, workflow và phân rã ở Chương 2; thiết kế tổng quan và ba phân hệ ở Chương 3. Chỉnh câu khẳng định thiếu căn cứ, nội dung lỗi thời và bổ sung luồng còn thiếu. Không thêm số liệu thị trường hoặc kết quả nghiệm thu mới.

## Quyết định của người dùng

Chương 1 không có ảnh. Chương 3 sử dụng bộ prototype được cung cấp, không coi là ảnh chụp ứng dụng đã hiện thực. Các khác biệt prototype và bản chạy được xử lý ở Chương 5–7; không lặp lại lời cảnh báo dưới từng ảnh.

## Nội dung được đối chiếu

- `FirebaseAuthRepository`: email/username và mật khẩu, đăng ký và reset mật khẩu.
- `BookingPolicy`, `BookingResolutionPolicy`, `CancellationPolicy`, `ReviewPolicy`: chế độ thuê, giữ lịch, hết hạn, không đến, sự cố và quyền đánh giá.
- `BookingRepository`, `HostRepository`, `AdminRepository`: cập nhật lịch, quyền xử lý và nhật ký.
- `GuestNavigation`, `ManagementNavigation` và các menu XML: điều hướng theo vai trò.
- `colors.xml`, các Activity/ViewModel nghiệp vụ và Repository ảnh, báo cáo, hội thoại.
- `backend/payments/README.md`: backend Java, khoản trả trước 40%, kênh demo, QR hết hạn và webhook relay; chưa có hoàn tiền tự động.

Các mô tả cũ về Firebase Storage, thuê theo ngày, Admin chỉ là dự kiến và QR check-in được sửa trong Chương 1–2. Chương 3 mô tả bố cục prototype, thay vì gán các lựa chọn trong hình thành tính năng đã triển khai.

## Phân bổ hình

- Chương 2: workflow tạo/xử lý booking, phân rã tổng quát, use case tổng quát.
- Chương 3: đăng nhập/đăng ký, Trang chủ, chi tiết không gian, xác nhận đặt phòng và đăng tin của Host. Hai hình Trang chủ/chi tiết đặt cạnh nhau.
- Phụ lục A: prototype Admin dài, có mô tả và dẫn chiếu trong chương.
- Phụ lục G: use case từng vai trò và ba workflow bổ sung. Mỗi sơ đồ có mô tả; workflow rộng dùng trang ngang, số trang đặt giữa dưới.
- Không đưa toàn bộ 14 trang phân rã chi tiết vào báo cáo; bộ đầy đủ vẫn nằm trong tệp PDF/draw.io nguồn.

Ảnh PNG được sao chép nguyên dữ liệu, không phóng tạo chi tiết mới. Các sơ đồ được nhúng trực tiếp từ PDF vector. Nguồn LaTeX trước chỉnh sửa nằm tại `tmp/pdfs/chapters123-before`.

## Giới hạn của lượt chỉnh sửa

Chương 4–7 và các phụ lục kỹ thuật cũ chưa được rà soát lại toàn bộ. Chương 4 còn mô tả Firebase Storage và kiểm kê schema cũ; Chương 7 còn phần kết luận ở giai đoạn thiết kế. Những nội dung này cần đồng bộ trước khi coi toàn báo cáo là bản nộp cuối cùng. Lượt này không thay thế việc nghiệm thu app hoặc kiểm thử trên cloud.

Biên dịch đầy đủ: `python tools/visualization/compile_main_report.py`. Đầu ra dựng tại `tmp/chapters123/build/main.pdf`; chỉ công bố sang `main.pdf` sau kiểm tra trang và dẫn chiếu.

## Bổ sung chiều sâu theo phản hồi

Chương 1 bổ sung cơ sở dữ liệu NoSQL hướng tài liệu, phân biệt Firebase với Cloud Firestore, quan hệ chứa với liên kết ID, map/array với subcollection, transaction và dữ liệu chụp theo thời điểm. Phần này dẫn đến cách biểu diễn cấu trúc lưu trữ ở Chương 4, không coi sơ đồ là class diagram hay gán khóa ngoại tự động cho mọi đường nối. Thêm tài liệu chính thức `Choose a data structure` vào thư mục tham khảo chung.

Chương 2 bổ sung đặc tả đặt chỗ, điều kiện đầu vào, cạnh tranh lịch, gửi lại cùng định danh yêu cầu, khép lượt thuê, xử lý vi phạm và yêu cầu phi chức năng. Những điều kiện theo giờ được phân biệt với kỳ thuê cố định, không áp dụng giờ mở cửa cho toàn bộ tháng thuê.

Chương 3 phân tích thêm phân cấp thông tin, hành động chính, trạng thái tải/lỗi và cách tổ chức từng luồng prototype. Ảnh xác nhận đặt phòng được đưa vào phần chính, có phân tích thông tin thời gian, liên hệ, chi phí và quyết định xác nhận. Bản trước mở rộng được giữ tại `tmp/pdfs/chapters123-before-depth`.

Bản mở rộng trước lần cân đối có 15 trang: Chương 1 bốn trang, Chương 2 sáu trang, Chương 3 năm trang. Không giảm cỡ chữ hoặc giãn dòng để đạt dung lượng. Hình xác nhận đặt phòng chỉ xuất hiện một lần trong phần chính; không lặp lại ở phụ lục.

## Cân đối theo phản hồi về phần NoSQL

Đã bỏ mục NoSQL riêng cùng bốn tiểu mục và bảng so sánh. Nội dung cần thiết được gộp thành hai đoạn ở mục Firebase và Cloud Firestore: định nghĩa NoSQL hướng tài liệu, collection/document, dữ liệu nhúng, subcollection và liên hệ ngắn với sơ đồ Chương 4. Phần giới thiệu không đi sâu vào transaction, dữ liệu chụp hoặc cách kiểm tra liên kết ID.

Rà lại Chương 1–3, cô đọng các đoạn mục tiêu và tính cấp thiết trùng ý. Chương 2 phân biệt mô tả luồng với điều kiện xử lý, bỏ câu mang giọng hướng dẫn cách viết báo cáo ở yêu cầu phi chức năng. Chương 3 đưa prototype đăng tin Host từ phụ lục lên phần chính, không lặp hình; bổ sung phân tích bố cục xử lý Admin trong phần văn bản. Không thêm sơ đồ mới vì ba hình tổng quát và các trang chi tiết ở phụ lục đã bao quát các nhóm nghiệp vụ.

Bản trước cân đối nằm tại `tmp/pdfs/chapters123-before-balance`. Số trang và kết quả kiểm tra của bản hiện hành được ghi trong `chapters123-validation.json`.

Kết quả bản cân đối: Chương 1 hai trang, Chương 2 sáu trang và Chương 3 sáu trang, tổng cộng 14 trang phần thân. Đã biên dịch ba lượt pdfLaTeX cùng biber, kiểm tra hình render, dẫn chiếu và log tràn lề; công bố cùng một PDF sang `main.pdf` và `output/pdf/BroomSharing-Bao-cao.pdf`. Chương 1 không có hình; mục Firebase chỉ giới thiệu ngắn NoSQL, không còn bảng hoặc mục phân tích dữ liệu riêng.
