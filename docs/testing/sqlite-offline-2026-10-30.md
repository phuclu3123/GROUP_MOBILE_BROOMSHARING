# SQLite: đọc dữ liệu đã lưu khi mất mạng

## Phạm vi

- Đọc slide `Buổi 9/10.0 SQLite Primer.pptx` (26 trang) và `10.1_ Room, LiveData, and ViewModel.pptx` trong ZIP người dùng cung cấp. Phần thực hiện dùng SQLiteOpenHelper, ContentValues, query có tham số và Cursor; không thêm Room hoặc backend.
- OfflineDatabase lưu bản chụp model vào BLOB, cùng UID, loại dữ liệu, mã đối tượng và thời điểm nhận từ server. Ảnh không lưu nhị phân vào SQLite; thư viện ảnh quản lý cache riêng.
- Lưu tối đa 200 phòng/booking trong từng danh sách; tối đa 400 bản lưu nội dung mỗi UID, giữ hồ sơ phiên riêng. Giới hạn 750.000 byte mỗi bản lưu để tránh vượt CursorWindow.
- Danh sách/chi tiết phòng và danh sách/chi tiết booking của chính Guest được xem từ SQLite. Không ghi/xếp hàng giao dịch offline. Chỉ lỗi kết nối được phép đọc bản lưu, không fallback khi server từ chối quyền.
- Phiên Firebase phải còn tồn tại, và phải có hồ sơ ACTIVE đã kiểm tra trên server trước đó. Bản hồ sơ local chỉ mở bốn màn đọc; không cấp quyền Host/Admin. Khi có mạng, kiểm tra lại phiên và tải dữ liệu mới.
- Bỏ nhãn lịch trống hiện thời khỏi dữ liệu lưu. UI offline hiện thời điểm lưu; các nút đặt/hủy/thanh toán/đánh giá/báo cáo bị khóa. Đăng xuất xóa cache UID; callback cũ không chuyển dữ liệu sang UID mới.

## Kiểm tra đã chạy

- `:app:assembleDebug :app:testDebugUnitTest`: thành công, 146 kiểm thử, 0 lỗi.
- `OfflineDatabaseTest` trên thiết bị Android: 7/7 qua. Database riêng `offline_acceptance.db`, không ghi cloud.
- Nội dung kiểm thử: model phòng với khoảng khóa và booking có map giá qua đóng/mở database; cách ly hai UID; thay thế và danh sách rỗng; BLOB hỏng/sai kiểu; dọn dữ liệu cũ vẫn giữ hồ sơ; từ chối payload quá lớn vẫn giữ bản cũ; lỗi quyền không được fallback.
- Đã vá thêm trường hợp ViewModel còn giữ dữ liệu online sau khi Activity được tái tạo vì đổi mạng. Khi kết nối lại, không dùng hồ sơ giữ trong ViewModel để bỏ qua kiểm tra phiên.

## Nghiệm thu giao diện

Đã thao tác trực tiếp bằng giao diện Android Studio trên Pixel 6 Pro API 31 x86_64. Dùng tài khoản đã được người dùng đăng nhập, không tạo dữ liệu cloud.

1. Online: mở danh sách/chi tiết phòng và danh sách/chi tiết booking có sẵn. SQLite thực tế có hồ sơ, 14 phòng, 20 booking và hai bản lưu danh sách.
2. Bật chế độ máy bay, đối chiếu `Active default network: none`: danh sách và chi tiết phòng/booking vẫn đọc được, có nhãn và thời điểm bản lưu. Các nút đặt chỗ, thanh toán, báo cáo, đánh giá và chat bị khóa ở màn đọc ngoại tuyến.
3. Đóng màn chính bằng Back rồi mở lại qua biểu tượng app khi vẫn offline: dữ liệu đã lưu vẫn hiển thị, không cần đăng nhập giả hoặc lấy dữ liệu từ fixture.
4. Bật mạng lại: app kiểm tra phiên, tải dữ liệu online và gỡ nhãn ngoại tuyến. Đã lặp lại vòng chuyển mạng sau bản vá.
5. Chưa thao tác riêng trường hợp trang chưa lưu và đăng xuất/đổi tài khoản bằng UI. Trường hợp thiếu/hỏng bản lưu, cách ly UID và xóa riêng từng UID đã được kiểm bằng SQLite instrumentation; không tính chúng là bằng chứng UI.

Trong nghiệm thu đã tái hiện và sửa lỗi listener kiểm tra phiên trả lỗi mạng trước callback kết nối, làm che màn đọc bằng lỗi truy cập. UiState phân biệt lỗi mạng với lỗi quyền; màn đọc chuyển sang SQLite khi mất mạng. Callback mạng được gộp và kiểm lại sau 350 ms, kèm kiểm tra khi Activity resume. Khi kết nối mới phục hồi nhưng Firestore chưa sẵn sàng, tải phiên được thử lại tối đa hai lần, không polling vô hạn. Lỗi quyền vẫn chặn như trước.

Biên bản chi tiết và SHA-256 APK: `_local_archive/final-20261030/docs/testing/sqlite-acceptance-2026-10-30.json`. Ảnh gốc nằm trong `_local_archive/final-20261030/tmp/sqlite-2026-10-30/ui/`. Build cuối và 146 unit test qua; 7 SQLite instrumentation qua trên Pixel. Không thay đổi nghiệp vụ đặt chỗ/thanh toán, không sửa Rules hoặc dữ liệu cloud.

## Báo cáo

Cập nhật Tóm tắt, mục 5.2, 5.4 và Chương 7; bỏ câu sự cố AVD khỏi văn bản nộp. Hạn chế còn lại ghi đúng phạm vi: dữ liệu offline có thể cũ; đặt chỗ/thanh toán cần mạng; kênh demo một Host và chưa hoàn tiền tự động.
