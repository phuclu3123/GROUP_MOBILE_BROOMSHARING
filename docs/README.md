# Tài liệu BROOM Sharing

Chỉ giữ tài liệu phản ánh code hiện tại. Ứng dụng dùng **Java + XML, MVVM + Repository + Model**, database chính **Firestore `broomsharing`**.

| Cần xem | Tài liệu |
| --- | --- |
| Luồng code, ranh giới các lớp và các điểm cần lưu ý | [Thiết kế](design/architecture.md) |
| Cây package, vị trí Activity/ViewModel và cách đọc từng chức năng | [Cấu trúc mã nguồn](design/source-structure.md) |
| Collection, giữ chỗ và phân quyền | [Firestore](design/firestore.md) |
| GPS/địa chỉ/chọn điểm, tìm 3 km và Google Maps dự phòng | [Bản đồ](features/maps.md) |
| Cloudinary unsigned upload, biến .env và phân nhóm ảnh/tệp demo | [Thiết lập media API](integrations/cloudinary-setup.md), [kết quả kiểm thử](testing/cloudinary-media-2026-10-26.md) |
| 30 ảnh thật, gallery sáu phòng và dữ liệu demo Cloudinary/Firestore | [Bộ ảnh và nguồn](../data/demo/media/README.md), [nghiệm thu ảnh demo](testing/cloudinary-photo-demo-2026-10-26.md) |
| Avatar thư viện/URL, hai mặt giấy tờ demo và duyệt quyền Host | [Luồng hồ sơ](integrations/profile-host-verification.md), [nghiệm thu](testing/profile-identity-2026-10-26.md) |
| Backend Java payOS, QR trả trước 40% và webhook dùng chung WEB_TCC | [Thiết lập thanh toán](../backend/payments/README.md), [nghiệm thu tích hợp 27/10](testing/payos-2026-10-27.md), [mốc kiểm thử local ban đầu](testing/payos-2026-10-26.md) |
| Địa chỉ công khai đã đối chiếu và nạp Firestore | [Địa điểm tham khảo](../data/reference/README.md) |
| Dataset giả định đã nạp cloud và tài khoản demo | [Dataset TP.HCM](../data/demo/README.md) |
| Build, kiểm thử, kết quả và giới hạn | [Kiểm thử](testing/verification.md) |
| Booking hết hạn, review, báo cáo, khóa Host và kết quả nghiệm thu 24/10 | [Nghiệm thu nghiệp vụ](testing/business-completion-2026-10-24.md) |
| Nghiệm thu booking/nghiệp vụ bằng tài khoản thật trên cloud và dữ liệu quay video | [Nghiệm thu cloud](testing/cloud-business-acceptance-2026-10-24.md) |
| Bản hợp nhất Trúc/Hoàng và công việc mới giai đoạn 3–4 | [Nghiệm thu tích hợp](testing/member-integration-2026-10-25.md), [bàn giao](testing/phase3-phase4-handoff-2026-10-25.md), [đối chiếu nhánh](testing/upstream-members-review-2026-10-25.md) |
| Bộ đếm phòng cũ, phiên bản booking và chuyển đổi dữ liệu cloud | [Tương thích phòng cũ](testing/room-counter-compatibility-2026-10-25.md) |
| Khép lượt đang sử dụng của Host bị khóa, khách không đến và quyền đánh giá | [Chính sách và nghiệm thu 26/10](testing/booking-resolution-2026-10-26.md) |

## Kiểm tra trước khi nộp

- [Đối chiếu yêu cầu Firestore, responsive, offline và API ngày 28/10](testing/course-requirements-check-2026-10-28.md).
- [Biên bản QA cuối và các giới hạn còn lại](testing/final-qa-2026-10-28.md).
- [Nguyên nhân reads nền, bản sửa và bằng chứng đo ngày 28/10](testing/firestore-read-optimization-2026-10-28.md).
- [Responsive điện thoại/tablet và giới hạn đã kiểm tra](testing/responsive-2026-10-28.md).
- [Bàn giao phần Phúc Chương 5–6 và ảnh hỗ trợ thành viên](report/phuc-chapter5-6-handoff.md).

File phân công `.xlsx` và slide gốc vẫn ở đường dẫn người dùng cung cấp tại thư mục dự án; không phải mã nguồn ứng dụng. Các báo cáo cũ, log Functions, ảnh/log thử lặp và tài liệu mô tả kiến trúc đã bỏ không được giữ lẫn với hướng dẫn hiện tại.
