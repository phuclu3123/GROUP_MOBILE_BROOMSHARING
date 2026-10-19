# Java/XML + MVVM + Repository + Model

Luồng duy nhất: **Activity/XML → ViewModel → Repository → Firebase Android SDK → Firestore**. Model mang dữ liệu qua các lớp. Không có backend riêng, Cloud Functions, Node, JavaScript, DTO, use-case layer, DI framework hoặc event bus.

| Phần | Trách nhiệm |
| --- | --- |
| Activity/XML/Adapter | Nhận thao tác, hiển thị trạng thái, điều hướng và quản lý vòng đời MapView |
| ViewModel/LiveData | Trạng thái màn hình, tránh gửi lặp, giữ dữ liệu khi xoay màn hình, gọi Repository |
| Repository | Firebase Auth/Firestore/Storage hoặc HTTP bản đồ; đọc dữ liệu mới, kiểm tra quyền và ghi transaction |
| Model | UserProfile, PublicHost, Room, Building, Booking, AvailabilityMonth, Favorite và các model màn hình |
| BookingPolicy/HostSchedule/HostValidation | Hàm Java kiểm tra thời gian, giá và dữ liệu; không I/O, không giữ Activity |
| FirebaseHelper | Một Firestore client cho database `broomsharing`; bản Debug có tùy chọn emulator, mặc định dùng database thật |

## Các chỗ lệch đã sửa

- Bỏ toàn bộ service và kiểm thử JS/CJS, package manifest Node, cấu hình/dependency Firebase Functions.
- BookingRepository tự đọc Room trên Firestore, tính giá và ghi giữ chỗ/booking/audit bằng transaction. Không tin giá truyền từ form.
- Lưu phòng và đổi trạng thái rao nằm trong HostRepository; không đẩy sang service đặt phòng như trước.
- AdminRepository duyệt, khóa/mở Host/phòng và ghi audit bằng transaction Firestore. Mã đọc bằng chứng dùng Firebase Storage Android SDK; quyền Admin đọc tệp chưa cấu hình do giới hạn database đặt tên (xem Firestore).
- RoomRepository đọc PublicHost thay vì gọi service hoặc mở quyền đọc hồ sơ Users riêng tư.
- SessionActivity quan sát AuthViewModel; không gọi Repository trực tiếp.
- Request đặt chỗ dùng Booking Model, bỏ Map truyền tải kiểu API. Xóa bốn enum trạng thái không được tham chiếu và constructor Context không dùng.

## Điều hướng ba vai trò (20/10/2026)

Đối chiếu toàn bộ workbook phân công, ưu tiên U02/P04/P10 (khung vai trò), H02–H17 (không gian, lịch và booking), P16–P20/P29 (xét duyệt, quản lý, báo cáo). Các lựa chọn kỹ thuật đã chốt với người dùng như Java Model thay DTO, không dùng Functions/JS và bản đồ MapLibre được giữ nguyên.

| Vai trò | Khu vực UI |
| --- | --- |
| Người thuê | Trang chủ → Bản đồ → Đặt chỗ → Hồ sơ; phòng đã lưu nằm trong Hồ sơ |
| Host | Tổng quan → Không gian (Phòng/Tòa nhà) → Đặt chỗ → Tài khoản |
| Admin | Tổng quan → Xét duyệt (Host/Không gian/Xác minh) → Quản lý (Host/Phòng/Nhật ký) → Báo cáo → Tài khoản |

- Đăng nhập hoặc phục hồi phiên Host vào HostDashboardActivity; Admin vào AdminDashboardActivity. Các màn vẫn qua SessionActivity/AuthViewModel, không dùng lựa chọn giao diện để cấp quyền.
- ManagementNavigation chỉ quản lý menu/Intent/insets; không đọc database. RoleAccountActivity quan sát hồ sơ từ SessionActivity, có đăng xuất xác nhận; Host có thể mở giao diện người thuê và quay lại qua mục quản lý có sẵn trong Hồ sơ.
- Host giữ số liệu thực từ HostViewModel. Admin tổng quan là đường dẫn tác vụ, chưa tuyên bố hoàn thành KPI P31. Chat/thông báo, xác minh Host và các nghiệp vụ chưa có không được thêm nút giả hoàn tất.
- Màn không gian chia phòng và tòa nhà thành hai tab; danh sách đặt chỗ có đủ bộ lọc các trạng thái hiện có. Admin dùng thẻ tóm tắt, mở thông tin đầy đủ trong hộp thoại cuộn; các thao tác duyệt/khóa vẫn dùng ViewModel và Repository cũ.
- Người thuê mở bộ lọc trong hộp thoại cuộn, có Xem kết quả/Hủy/Xóa bộ lọc. Bản nháp bộ lọc được phục hồi khi tái tạo Activity, chỉ áp dụng khi xác nhận. Form đặt chỗ giữ toolbar và nút kiểm tra/gửi ở ngoài vùng cuộn; thẻ booking tách trạng thái, thời gian, giá và cọc.
- Lưu tab/nhóm lọc khi tái tạo Activity, xử lý Intent khi quay lại bằng thanh điều hướng. Chuyển mục ngang không giữ một chuỗi màn hình anh em trong back stack; màn chi tiết vẫn quay về màn cha.
- Bỏ trang Tin nhắn chờ triển khai và các lựa chọn đăng nhập Google/số điện thoại khỏi UI. Chat là đầu việc Đợt 3 chưa hoàn tất. Quên mật khẩu gọi Firebase Auth qua AuthViewModel/AuthRepository; không có thông báo gửi giả.
- Tổ chức UI không đổi schema, Rules hoặc transaction. Nghiệm thu cloud bổ sung fixture DEMO QA riêng, hủy toàn bộ giữ chỗ và ẩn lại hai phòng sau khi chạy; không đổi vai trò tài khoản dataset.

## Đối chiếu bài giảng

Đã đọc nội dung các bài liên quan từ thư mục slide gốc và FireBase_Guide.pdf; bản trích đọc tạm nằm trong build, không đưa vào mã ứng dụng.

| Bài | Áp dụng trong code |
| --- | --- |
| 01.2 Layouts and resources; 01.3 Text and scrolling views | XML, TextView, LinearLayout/ConstraintLayout/ScrollView; dp/sp, strings/styles/colors; không dựng UI web |
| 02.1 Activities and Intents; 02.2 Activity lifecycle and state; 02.3 Implicit Intents | Intent theo ID/nhóm; saved state, dọn listener/dialog; Google Maps chỉ fallback qua Intent |
| 04.1–04.5 Buttons, inputs, menus/pickers, navigation, RecyclerView | Nút có hành động thật, Date/TimePicker, menu theo vai trò, TabLayout và RecyclerView; không tạo lịch sử qua từng lần đổi tab |
| 05.1–05.3 Drawables, Material Design, adaptive resources | Resource dùng chung, vùng bấm tối thiểu, nội dung cuộn và thanh thao tác cố định |
| 07.1–07.2 Async/Internet | Callback Firebase và HTTP chạy nền; Activity không truy cập database trực tiếp |
| 10.1 Room, LiveData and ViewModel | Activity → ViewModel/LiveData → Repository → Model; dùng Firestore thay Room/SQLite theo yêu cầu đã chốt |
| FireBase_Guide.pdf | Firebase Android SDK, Model, callback/query/snapshot; giữ Rules theo role thay vì mở read/write true như ví dụ thực hành |

MapLibre/MapTiler/openrouteservice thay Google SDK, Java transaction + Rules thay Functions và Model thay DTO là các thay đổi người dùng đã yêu cầu. Không thêm JavaScript, Node, backend, Compose, DI framework, lớp use-case hoặc database khác. File Gradle .kts và Firebase .rules là cấu hình/DSL, không phải mã nghiệp vụ bằng JS. Công cụ Java trong tools/dataset chỉ quản trị dataset, không phải dịch vụ chạy cùng ứng dụng.

## Những giới hạn phải hiểu đúng

- Repository kiểm tra nghiệp vụ Java không thay thế Rules. Rules bảo vệ role, trạng thái booking, giá theo Room và khóa lịch; các điều kiện blackout tùy biến trong danh sách vẫn được kiểm tra bởi Java, chưa có bằng chứng chống mọi client sửa đổi đối với các chính sách đó. Không coi đây là hệ thống đặt chỗ production chống gian lận hoàn chỉnh.
- Cấp ADMIN ban đầu thực hiện bằng tài khoản quản trị trên Console; ứng dụng không có nút tự cấp ADMIN. Đã bổ sung PublicHost cho Host đang có trên cloud theo yêu cầu người dùng; các lần đăng nhập Host tiếp tục đồng bộ ba trường công khai này. Không tự đổi role của tài khoản có sẵn.
- Storage bằng chứng: chủ tệp được đọc; Admin chưa có quyền đọc tệp vì Storage Rules không tra được role từ database `broomsharing`. Không thêm dịch vụ hay database phụ. Đây là phần chưa hoàn tất cần trao đổi trước khi mở luồng kiểm chứng tệp.
- `ReviewItem.details` còn được ghép chuỗi hiển thị trong AdminRepository; đây là phần trình bày chưa tách triệt để, không phải lớp nghiệp vụ/backend mới. RoomListing và ReviewItem dùng làm model màn hình, không có tầng DTO song song.
- `Booking.priceSnapshot` là map lồng trong document Firestore hiện có. Không thêm một bản DTO trùng dữ liệu. Nếu thay schema, phải đổi model, Rules và test cùng lúc.
- Gradle `.kts`, JSON cấu hình và `.rules` phân quyền là cấu hình; mã ứng dụng và kiểm thử nghiệp vụ viết bằng Java. Không thêm ngôn ngữ/backend nghiệp vụ khác.

Chỉ bổ sung lớp/model khi có trách nhiệm thực tế; không tạo wrapper cho từng thao tác, không nhân bản validation hoặc thêm framework vì “sau này có thể cần”. Thay đổi kiến trúc/ngôn ngữ phải trao đổi với người dùng trước.
