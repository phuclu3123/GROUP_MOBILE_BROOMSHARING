# Tài liệu BROOM Sharing

Chỉ giữ tài liệu phản ánh code hiện tại. Ứng dụng dùng **Java + XML, MVVM + Repository + Model**, database chính **Firestore `broomsharing`**.

| Cần xem | Tài liệu |
| --- | --- |
| Luồng code, ranh giới các lớp và các điểm cần lưu ý | [Thiết kế](design/architecture.md) |
| Cây package, vị trí Activity/ViewModel và cách đọc từng chức năng | [Cấu trúc mã nguồn](design/source-structure.md) |
| Collection, giữ chỗ và phân quyền | [Firestore](design/firestore.md) |
| GPS/địa chỉ/chọn điểm, tìm 3 km và Google Maps dự phòng | [Bản đồ](features/maps.md) |
| Địa chỉ công khai đã đối chiếu và nạp Firestore | [Địa điểm tham khảo](../data/reference/README.md) |
| Dataset giả định đã nạp cloud và tài khoản demo | [Dataset TP.HCM](../data/demo/README.md) |
| Build, kiểm thử, kết quả và giới hạn | [Kiểm thử](testing/verification.md) |
| Booking hết hạn, review, báo cáo, khóa Host và kết quả nghiệm thu 24/10 | [Nghiệm thu nghiệp vụ](testing/business-completion-2026-10-24.md) |
| Nghiệm thu booking/nghiệp vụ bằng tài khoản thật trên cloud và dữ liệu quay video | [Nghiệm thu cloud](testing/cloud-business-acceptance-2026-10-24.md) |
| Bản hợp nhất Trúc/Hoàng và công việc mới giai đoạn 3–4 | [Nghiệm thu tích hợp](testing/member-integration-2026-10-25.md), [bàn giao](testing/phase3-phase4-handoff-2026-10-25.md), [đối chiếu nhánh](testing/upstream-members-review-2026-10-25.md) |
| Bộ đếm phòng cũ, phiên bản booking và chuyển đổi dữ liệu cloud | [Tương thích phòng cũ](testing/room-counter-compatibility-2026-10-25.md) |
| Khép lượt đang sử dụng của Host bị khóa, khách không đến và quyền đánh giá | [Chính sách và nghiệm thu 26/10](testing/booking-resolution-2026-10-26.md) |

File phân công `.xlsx` và slide gốc vẫn ở đường dẫn người dùng cung cấp tại thư mục dự án; không phải mã nguồn ứng dụng. Các báo cáo cũ, log Functions, ảnh/log thử lặp và tài liệu mô tả kiến trúc đã bỏ không được giữ lẫn với hướng dẫn hiện tại.
