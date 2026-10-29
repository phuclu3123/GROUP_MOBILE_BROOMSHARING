# Đối chiếu yêu cầu học phần trước khi push, 28/10/2026

Phạm vi: bản Java/XML, MVVM + Repository + Model đang dùng luồng `INSTANT_5M`. Đối chiếu ảnh yêu cầu của giảng viên, code hiện tại và bằng chứng nghiệm thu; không mở rộng chức năng trong lượt này.

| Yêu cầu | Hiện trạng và căn cứ | Kết luận |
| --- | --- | --- |
| Firebase/Firestore | `FirebaseHelper` chọn named database `broomsharing`; Repository dùng SDK Java, query/listener/transaction. Đã có ca cloud tranh lịch, QR và đối soát Rules/index. | Đã triển khai, có bằng chứng chạy cloud. Hiện quota đọc ngày bị chặn; đây là giới hạn vận hành cần nêu khi demo. |
| Responsive khi xoay màn hình | Không khóa hướng trong Manifest; các màn chính có layout ngang, lưu trạng thái và ViewModel. Có resource `room_grid_columns` riêng cho `sw600dp`; đã thao tác QR ngang/dọc, thử danh sách và bản đồ ở 800×1280dp mô phỏng. | Có triển khai và kiểm thử các màn đã ghi nhận; không tuyên bố mọi màn trên mọi tablet đều hoàn hảo. Giữ lưu ý MapView/viewport trong biên bản QA. |
| Đồng bộ local/offline bằng SQLite | Không có `SQLiteOpenHelper`, Room hoặc database SQLite do app quản lý. Bản cloud giữ cache mặc định của Firestore; chỉ APK emulator tắt persistence. `RoomRepository` dùng `Source.SERVER`, nên cache SDK không đồng nghĩa có thể mở lại mọi trang khi mất mạng. | Chưa triển khai SQLite/offline-first. Theo ảnh đây là phần khuyến khích, không ghi là đã hoàn thành và không dùng nó làm minh chứng bắt buộc. |
| Khai thác API hỗ trợ | payOS qua backend Java và webhook relay; Cloudinary upload và tải PNG/PDF; MapTiler/MapLibre và openrouteservice cho geocode, bản đồ, tuyến đường. Cohort APK cloud cuối chạy 4/4 qua; có thao tác QR và hết hạn 5 phút. | Đã có tích hợp API thật. Google Sign-In là ví dụ trong yêu cầu, app hiện dùng Firebase Auth email/password và không công bố đã làm Google Sign-In. |

Firestore Android bật offline persistence mặc định theo [tài liệu Firebase](https://firebase.google.com/docs/firestore/manage-data/enable-offline). Đây là khả năng SDK, không phải một tầng SQLite hoặc fallback offline đã được app thiết kế và nghiệm thu.

Kết quả nền: 146 unit test Android, 55 test backend; Rules 15/15, 9/9 và 44/44; cohort SDK, Auth, khép lượt Host/Admin và API cloud đã được ghi trong [biên bản QA](final-qa-2026-10-28.md). Không chạy lại kiểm thử cloud khi quota ngày đang cạn. Build/test/lint trước push được kiểm tra local, không tạo giao dịch hoặc gửi tiền. Vòng đối soát backend đã được sửa để lọc flow tức thời ngay tại query, tránh đọc lặp booking PENDING lịch sử mỗi 15 giây; ca hồi quy mới được chạy trên emulator. Backend demo cũ đã dừng, bản sửa đã build sẵn và chờ quota phục hồi để khởi động lại.

## Điều kiện và phần chưa đóng

- Thanh toán demo cần backend Java, tunnel và webhook relay hoạt động; một Host merchant, chưa có API hoàn tiền hoặc đa merchant.
- Chủ nhiệm đã xác nhận ba booking DEMO cũ chưa sử dụng; đang chờ quota để đọc lại và khép `NO_SHOW`, giữ lịch sử, nhả lịch và audit. Chưa ghi cloud; không coi là đã khép xong.
- Review DEMO lịch sử vẫn giữ nguyên, không thêm `usageConfirmedAt` giả.
- MapView từng có ANR và lệch camera khi ép đổi kích thước AVD; đã đổi artifact sang OpenGL cùng phiên bản và thử lại. Chưa có nghiệm thu tablet vật lý riêng.
- APK là debug phục vụ học phần; chưa có bản ký phát hành cửa hàng.

## Phạm vi thảo luận Chương 5–7 tiếp theo

1. Chương 5: trình bày implementation Java/XML, MVVM/Repository/Model, các màn chính và tích hợp API đúng code; dùng sơ đồ kiến trúc đã duyệt.
2. Chương 6: trình bày luồng đã vận hành và bảng kết quả kiểm thử có bằng chứng. Tách kiểm thử tự động, thao tác giả lập, API thật và lần chuyển tiền thật trước đó; không biến số test thành số người dùng hoặc giao dịch thực tế.
3. Chương 7: kết quả đạt được, giới hạn và hướng phát triển. Ghi rõ quota miễn phí, backend local/tunnel, một merchant, hoàn tiền thủ công, SQLite chưa làm và phạm vi thiết bị đã thử.

Các file Chương 5–7 hiện còn nội dung dự thảo cũ, chưa được xem là bản nộp. Chưa sửa chúng trong lượt push này; cần thống nhất nội dung trước khi viết tiếp.
