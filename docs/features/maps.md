# Bản đồ thử TP.HCM

Tâm thử cố định **10.7769, 106.6908**, nhãn “Điểm thử TP.HCM”. Cửa sổ pilot: kinh độ 106.45–106.95, vĩ độ 10.60–10.95; không phải ranh giới hành chính toàn thành phố.

| Chức năng | Công nghệ |
| --- | --- |
| MapView XML, marker và vẽ tuyến | MapLibre Native Android |
| Bản đồ nền / tìm địa chỉ | MapTiler |
| Vị trí thiết bị một lần ở foreground | Android LocationManager |
| Lọc phòng ≤3 km, sắp gần trước | Java và tọa độ Building trên Firestore |
| Tuyến đường ô tô thật | openrouteservice Directions |

Giữ logo/ghi công MapTiler và OpenStreetMap. Geocoding giới hạn bbox pilot rồi kiểm tra lại tọa độ. GPS ngoài phạm vi không thay điểm thử. Thiếu quyền vị trí có thể nhấn giữ bản đồ để chọn xuất phát; chạm thường chọn đích thử. Đích chọn tay được ghi rõ chưa phải văn phòng đăng trên hệ thống.

Phòng lấy qua RoomRepository/PublicHost trực tiếp từ Firestore. Không cần Functions. Chỉ phòng đã duyệt/đang rao, chủ và tòa nhà đang hoạt động, có tọa độ thật mới được hiển thị. Lọc trên các trang đã tải, có số lượng rõ ràng; không tạo marker giả khi thiếu tọa độ.

Lỗi bản đồ/geocoding/Directions tự mở Google Maps bằng ACTION_VIEW, ưu tiên ứng dụng đã cài rồi trình duyệt; không có hộp chọn nhà cung cấp trong app và không dùng Google API key. Chỉ mở một lần cho một lượt lỗi, có thể thử lại khi quay về.

`driving-car` là tuyến ô tô, thời gian ước tính; không có giao thông trực tiếp hoặc dẫn đường giọng nói. Root `.env` có `MapTiler_API_KEY`, `HeiGIT_API_KEY`, Gradle đưa vào BuildConfig. Key không commit nhưng vẫn có thể trích xuất từ APK; dùng hạn mức/quyền key phù hợp.

Kiểm thử API Java: `MapApiTest`, bật bằng instrumentation argument `runMapApis=true`. Không giữ script Python/Node kiểm tra trùng với test Java.

## Ưu tiên chỉ đường trong ứng dụng (20/10/2026)

Nút chỉ đường ở chi tiết phòng mở MapActivity và chọn đúng phòng. Hai tọa độ cách nhau dưới 5 m được báo trùng điểm, không gửi Directions và không mở Google Maps. Với hai điểm khác nhau, luôn gọi Directions và vẽ tuyến bằng MapLibre trước; Google Maps chỉ nhận khi dịch vụ thực sự thất bại. Lỗi tải một tile/glyph/sprite không tự chuyển ứng dụng. Timeout nạp style được hủy khi callback style đã sẵn sàng, tránh chuyển Google Maps dù nền bản đồ đã hiện. Khi đổi xuất phát/đích, sự kiện fallback cũ được xóa.
