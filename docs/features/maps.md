# Bản đồ và tìm quanh vị trí tại TP.HCM

Tâm mặc định **10.7769, 106.6908** chỉ dùng khi chưa chọn vị trí, không giả là GPS của người dùng. Đổi tâm bằng LocationManager, nhập địa chỉ hoặc chọn điểm trên bản đồ. Cửa sổ hỗ trợ mở rộng: kinh độ 106.35–107.05, vĩ độ 10.35–11.20; đây là giới hạn kỹ thuật khu vực TP.HCM, không phải ranh giới hành chính.

| Chức năng | Công nghệ |
| --- | --- |
| MapView XML, marker và vẽ tuyến | MapLibre Native Android |
| Bản đồ nền / tìm địa chỉ | MapTiler |
| Vị trí thiết bị một lần ở foreground | Android LocationManager |
| Lọc phòng ≤3 km, sắp gần trước | Java và tọa độ Building trên Firestore |
| Tuyến đường ô tô thật | openrouteservice Directions |

Giữ logo/ghi công MapTiler và OpenStreetMap. Geocoding giới hạn bbox, ưu tiên kết quả gần tâm hiện tại rồi kiểm tra lại tọa độ; nhiều kết quả yêu cầu chọn địa chỉ, không tự nhận một kết quả mơ hồ. Nhập số nhà có thể chỉ trả tọa độ đường, cần chọn lại điểm nếu cần chính xác hơn. GPS ngoài phạm vi giữ tâm cũ và báo lý do. Nút “Chọn trên bản đồ” bật chế độ chạm đổi tâm; nhấn giữ cũng đổi tâm. Chạm marker chọn địa điểm và tính tuyến, không biến thao tác chạm nền thông thường thành điểm đến giả.

Phòng lấy qua RoomRepository/PublicHost trực tiếp từ Firestore. Không cần Functions. Chỉ phòng đã duyệt/đang rao, chủ và tòa nhà đang hoạt động, có tọa độ trong vùng mới được hiển thị. Tự tải hết các trang công khai trong phạm vi dữ liệu hiện tại rồi lọc khoảng cách đường thẳng ≤3.000 m từ tâm đã chọn; không đợi người dùng bấm “Tải tiếp”. Phòng thiếu tọa độ bị loại, không tự gán tọa độ mặc định. Không gian cùng tòa nhà dùng một marker, chạm để chọn phòng.

Đã bổ sung 8 địa chỉ công khai vào `rentalPlaces`, tách khỏi `rooms/buildings`. Xem [dataset và nguồn](../../data/reference/README.md). Đây là địa chỉ tham khảo từ tin cho thuê thật, không gán thành phòng của Host demo, không khẳng định còn trống, không nhận booking hoặc tự bịa giá/ảnh. Bản đồ nguồn được đối chiếu nhãn địa chỉ; bỏ tọa độ footer văn phòng môi giới không khớp bất động sản. Điểm từ bản đồ nhúng vẫn cần xác nhận lối vào thực tế.

Marker xanh lá: không gian trong app; cam: địa chỉ tham khảo; xanh dương: tâm; đỏ: đích. Vòng tròn hiển thị phạm vi 3 km. Chọn marker hoặc dòng danh sách sẽ vẽ tuyến ô tô thật; tất cả marker lân cận được giữ lại. “Xem phòng” mở phòng trong app; “Xem nguồn” trình bày địa chỉ/giới hạn và cho mở tin gốc. Danh sách dùng BottomSheetDialog, không chiếm cố định phần lớn bản đồ.

Các điểm được vẽ bằng GeoJsonSource/CircleLayer để đặt đúng tâm tọa độ, không xóa/tạo lại hàng loạt annotation bitmap mỗi lần LiveData thay đổi. Chỉ cập nhật nguồn marker khi tập dữ liệu/điểm chọn thay đổi, nguồn tuyến khi tuyến đổi. Không fit camera lại khi render cùng tuyến hoặc khi người dùng đang kéo/zoom. Cache bộ nhớ tối đa 12 địa chỉ/tuyến trong phiên để giảm gọi API trùng; không lưu bản đồ hay tuyến giả.

Lỗi bản đồ/geocoding/Directions tự mở Google Maps bằng ACTION_VIEW, ưu tiên ứng dụng đã cài rồi trình duyệt; không có hộp chọn nhà cung cấp trong app và không dùng Google API key. Chỉ mở một lần cho một lượt lỗi, có thể thử lại khi quay về.

`driving-car` là tuyến ô tô, thời gian ước tính; không có giao thông trực tiếp hoặc dẫn đường giọng nói. Root `.env` có `MapTiler_API_KEY`, `HeiGIT_API_KEY`, Gradle đưa vào BuildConfig. Key không commit nhưng vẫn có thể trích xuất từ APK; dùng hạn mức/quyền key phù hợp.

Kiểm thử Java: `MapApiTest` (`runMapApis=true`), `MapExperienceTest` (`runMapExperience=true`) và MapPriorityTest. Công cụ nạp/đối chiếu địa chỉ là Java `RentalPlacesDataset`, nằm ngoài APK. Không có JS/Node/backend mới trong ứng dụng.

## Tiện ích quanh địa điểm — giai đoạn 3

Menu bản đồ có “Tiện ích trong 1 km”, tìm quanh điểm đến đang chọn hoặc tâm tìm kiếm nếu chưa có đích. Repository dùng **MapTiler reverse geocoding `types=poi&limit=10`**, với key MapTiler hiện có, rồi lọc nhà hàng/cà phê, giao thông, bãi đỗ và cửa hàng tiện lợi. API trả tối đa 10 POI gần nhất trong 1 km; sau lọc có thể còn ít hơn. Không phải tìm toàn bộ POI hoặc bảo đảm mỗi nhóm đều có kết quả. Thông tin này không phải danh sách phòng hoặc bằng chứng dịch vụ còn hoạt động.

Kiểm tra lại 1.000 m đường thẳng bằng Java, giới hạn phản hồi 512 KiB, timeout và một yêu cầu foreground; cache bộ nhớ 5 phút/tối đa 8 tâm và cách nhau ít nhất 30 giây giữa các yêu cầu chưa cache. Bán kính tìm **phòng** vẫn là 3 km. Chỉ giữ nhóm được hỗ trợ có `feature_tags` và tham chiếu OSM hợp lệ; tọa độ có thể là tâm khuôn viên, không khẳng định đó là lối vào. Chạm một kết quả có thể xem đối tượng OSM gốc hoặc chọn tuyến trong app từ điểm xuất phát đã chọn. Màn tiện ích trả tọa độ bằng Activity Result về MapActivity đang mở, giữ nguyên MapView; không chồng thêm một màn bản đồ và renderer thứ hai. Callback phòng cũ không được ghi đè điểm đến mới.

Ghi công MapTiler/OSM và liên kết copyright ở cuối màn hình. Không coi HTTP lỗi/JSON sai/timeout là dữ liệu rỗng thành công. Lỗi dịch vụ mở Google Maps dự phòng theo lựa chọn trước của người dùng; quay lại có thể thử lại. Xem [API và giới hạn nearest-neighbour](https://docs.maptiler.com/cloud/api/geocoding/). Overpass đã bỏ khỏi mã ứng dụng sau khi timeout; openrouteservice POI trả rỗng tại Q8 nên không được thêm. Không cần dịch vụ hoặc API key mới, không tắt xác thực TLS để vượt lỗi mạng.

## Ưu tiên chỉ đường trong ứng dụng (20/10/2026)

Nút chỉ đường ở chi tiết phòng mở MapActivity và chọn đúng phòng. Hai tọa độ cách nhau dưới 5 m được báo trùng điểm, không gửi Directions và không mở Google Maps. Với hai điểm khác nhau, luôn gọi Directions và vẽ tuyến bằng MapLibre trước; Google Maps chỉ nhận khi dịch vụ thực sự thất bại. Lỗi tải một tile/glyph/sprite không tự chuyển ứng dụng. Timeout nạp style được hủy khi callback style đã sẵn sàng, tránh chuyển Google Maps dù nền bản đồ đã hiện. Khi đổi xuất phát/đích, sự kiện fallback cũ được xóa.

### Ổn định khung vẽ

Khung MapView giữ nguyên kích thước khi mở thẻ địa điểm (thẻ nằm chồng bên trong khung), không resize theo bàn phím. Giới hạn bộ vẽ 30 FPS và dùng TextureView. Lựa chọn này theo lỗi SurfaceView/resize được ghi nhận ở [MapLibre Native #3836](https://github.com/maplibre/maplibre-native/issues/3836), sau khi kiểm thử chuyển màn hình gặp ANR. TextureView có chi phí compositing; đây không phải cam kết hiệu năng trên mọi máy. Không bật cơ chế async renderer cleanup thử nghiệm. Cần tiếp tục đo trên điện thoại thật.

LocationManager chỉ dùng vị trí cache có tuổi từ 0 đến 30 giây, độ chính xác ≤150 m và trong vùng hỗ trợ. Vị trí cache có thời gian tương lai trên emulator bị loại để tiếp tục yêu cầu GPS mới, thay vì dùng điểm Mỹ cũ rồi báo ngoài khu vực. Đây là lỗi đã quan sát được; không quy lỗi geocoding cho cách mã hóa dấu cách khi hai dạng đều trả kết quả trong phép thử API.
