# Chương 4: phạm vi và căn cứ biên soạn

Ngày cập nhật nội dung và đối chiếu thao tác: 24/10/2026.

## Phạm vi đã thống nhất

- Giữ đúng bốn mục: 4.1 Các thực thể và thuộc tính; 4.2 Quan hệ dữ liệu và cấu trúc lưu trữ; 4.3 Một số thao tác dữ liệu tiêu biểu; 4.4 Phân quyền và bảo vệ dữ liệu.
- Bảng thuộc tính tóm tắt 16 mẫu document thuộc 13 collection gốc và ba subcollection được app tham chiếu, chọn 67 thuộc tính tiêu biểu. Dấu ba chấm chỉ phần còn lại của cấu trúc; các sơ đồ vẫn giữ danh sách chi tiết.
- Rút gọn trực tiếp chương chính trước, chưa đưa dữ liệu sang phụ lục theo chỉ dẫn mới nhất của người dùng.
- Payments và notifications để lại cho lần hoàn thiện chức năng sau. Các thẻ Rules-only vẫn xuất hiện trong hình gốc, có chú thích giới hạn phạm vi.
- Chỉ dùng Page-2 đến Page-6 của tệp BroomSharing.drawio do người dùng chỉ định. Không đưa Page-1 về PAYOS vào Chương 4.
- Giữ nguyên từng sơ đồ, không thêm vùng cắt chi tiết hoặc sửa bố cục nguồn. Kích thước hình trên trang sẽ được tinh chỉnh sau theo lựa chọn của người dùng.

## Nội dung và cách trình bày

- Chương chính có bốn bảng: 16 hàng mẫu document với 67 thuộc tính tiêu biểu, tám hàng nhóm quan hệ logic, tám hàng thao tác dữ liệu và sáu hàng nhóm phân quyền. Mỗi bảng có dẫn nhập và diễn giải nghiệp vụ, không đặt liên tiếp mà thiếu nội dung.
- Các tệp bảng đầy đủ có 219 hàng thuộc tính và 44 hàng quan hệ vẫn được giữ trong mã nguồn tham chiếu nhưng không được input vào báo cáo. Không thêm phụ lục in ra để chuyển dung lượng từ chương sang phần khác.
- Mục 4.3 gồm bảng tổng hợp thao tác, các truy vấn tiêu biểu và ví dụ tạo booking bằng transaction. Bảng phản ánh thao tác hiện có, không phải thống kê tần suất hoặc kết quả đo hiệu năng.
- Mục 4.3.2 có hai truy vấn: danh sách phòng có phân trang và listener booking theo guestUid. Phần diễn giải nối Repository với RoomViewModel/BookingViewModel và Activity, không thêm đoạn mã giao diện dài. Các mô tả được đối chiếu với mã nguồn hiện tại.
- In năm đoạn mã: truy vấn phòng, listener booking, chuẩn bị booking và kiểm tra lịch, ghi booking, kiểm tra lịch trong Rules. Chín đoạn trích chính xác được giữ trong manifest; các đoạn không sử dụng không in vào chương.
- Số dòng hiển thị bắt đầu lại từ 1 cho từng đoạn mã. Dải dòng trong chú thích nguồn vẫn là vị trí thật trong tệp Java hoặc Rules để truy vết.
- Ví dụ phân trang làm rõ đầu vào after, điều kiện công bố phòng, giới hạn 20 document, cursor và ý nghĩa có điều kiện của hasMore. Ví dụ booking phân tích chống gửi lặp, kiểm tra quyền và tài sản, kiểm tra xung đột trước ghi, kết quả nguyên tử và callback.
- Trang ngang giữ chiều hình; số trang được xoay và đặt ở giữa mép dưới khi hiển thị theo chiều ngang, kiểm tra bằng cả tọa độ PDF và ảnh render.
- Bỏ ghi chú biên soạn khỏi văn bản học thuật: không mô tả công cụ xuất PDF, Page-2 đến Page-6, ngày đối chiếu, phạm vi kiểm kê cloud hay đưa lời hướng dẫn người đọc cách đánh giá hình vào chương. Thông tin truy vết và phạm vi kiểm tra được lưu trong các manifest và tệp kiểm định này.
- Các giải thích SDK dựa trên tài liệu Firebase chính thức. Cấu trúc và hành vi của riêng BroomSharing dựa trên mã nguồn cục bộ, được ghi thành nguồn riêng trong ref.bib.

## Các phân biệt cần giữ khi chỉnh tiếp

- Booking mới dùng schemaVersion 2, PENDING đã giữ lịch, paymentStatus UNAVAILABLE. Khoản trả trước và tỷ lệ hủy chưa xác nhận thu hoặc hoàn tiền thật.
- Favorite, snapshot tên phòng và bản chiếu PublicHost có vai trò khác nhau; không khẳng định mọi dữ liệu sao chép đều tự đồng bộ.
- Admin không mặc định được đọc chat, sửa Building hoặc chuyển mọi trạng thái Booking. Bảng quyền bám đúng nhánh Rules.
- usernames cho phép get trước đăng nhập và document chứa email. Không mô tả toàn bộ email như dữ liệu chỉ chủ tài khoản có thể đọc.
- photoUrl và hostResponse còn thiếu xác nhận kiểu/luồng ghi; thông tin này thuộc tài liệu kiểm định chi tiết, không lặp lại như ghi chú rời ở cuối mục phân quyền.
- Admin đọc được hồ sơ xác minh trong Firestore không đồng nghĩa được đọc tệp bằng chứng ở Storage. Rules tệp hiện chỉ cho chủ UID đọc.
- Các trường propertyVerifications.schemaVersion, evidencePaths và reviewed chỉ thấy trong dataset được loại khỏi bảng cấu trúc code hiện hành; nhãn [D] trong hình gốc được giải thích tại chú thích hình.
- Chưa kiểm kê trực tiếp metadata/document cloud hoặc xác nhận trạng thái triển khai index trong công việc biên soạn này.

## Tệp chỉnh sửa và kiểm tra

- LaTeX chính: Latex/FINAL_ANDROID (1)/chapters/chapter4.tex.
- Bảng tóm tắt nằm trong chapter4-entities-summary.tex; hình nằm trong chapter4-figures.tex. Hai bảng quan hệ và phân quyền được viết trực tiếp trong chapter4.tex.
- Bản xem riêng dùng chapter4-preview.tex, lấy cùng preamble của main.tex và đặt bộ đếm chương bằng 4. Không có danh mục tham khảo riêng cho chương; main.tex in duy nhất danh mục chung sau các chương nội dung.
- PDF hình, mã trích và manifest nguồn nằm tại Latex/FINAL_ANDROID (1)/Resources/chapter4/.
- Bàn giao báo cáo đầy đủ thay cho PDF riêng Chương 4. export_report.py xuất output/pdf/BroomSharing-Bao-cao.pdf và cập nhật main.pdf trong thư mục LaTeX; bản tách chương trong tmp chỉ phục vụ kiểm tra nội bộ.
- prepare_chapter4_summary.py tái tạo bảng tóm tắt và summary-manifest.json. prepare_chapter4_sources.py chỉ tái tạo bộ bảng đầy đủ được giữ ngoài chương chính.
- verify_chapter4.py đối chiếu SHA-256 tệp draw.io, mã trích, 67 thuộc tính được chọn, số bảng, dẫn chiếu, lỗi ngắt dòng và tọa độ số trang ngang; đồng thời render mọi trang để xem bằng mắt.
- Kết quả kiểm tra máy đọc được lưu trong tmp/chapter4/qa/verification.json. Đây là kiểm tra tài liệu và nguồn tĩnh, không thay thế kiểm thử nghiệp vụ hoặc kiểm kê cloud.

Không thay mã ứng dụng, Rules, dữ liệu cloud hoặc tệp draw.io gốc trong công việc này. Nội dung các chương khác chưa nằm trong lượt biên soạn Chương 4.
