# Cấu trúc Java và cách đọc code

Mã Java nằm trong `app/src/main/java/vn/edu/ueh/phuclu/broomsharing`.
Package gốc không chứa Activity. Màn hình và ViewModel được đặt cạnh nhau theo chức năng trong `ui`.
Kiến trúc vẫn là **Java/XML + MVVM + Repository + Model**.

```text
broomsharing/
├── adapter/       # Mapping dữ liệu lên từng dòng/thẻ RecyclerView
├── model/         # Class biểu diễn dữ liệu
├── repository/    # Đọc/ghi Firebase và gọi API bên ngoài
├── utils/         # Quy tắc, tính toán và các hàm dùng chung
└── ui/
    ├── admin/     # Quản trị, xét duyệt và xem bằng chứng
    ├── auth/      # Đăng nhập, đăng ký, quên mật khẩu và kiểm tra phiên
    ├── booking/   # Đặt chỗ, chi tiết đơn, danh sách đơn và thông báo
    ├── chat/      # Danh sách cuộc trò chuyện và tin nhắn
    ├── common/    # Điều hướng, tài khoản quản lý và notification dùng chung
    ├── favorites/ # Danh sách phòng yêu thích
    ├── host/      # Tổng quan Host, tòa nhà, chỉnh sửa phòng và đơn của Host
    ├── map/       # Bản đồ, đường đi và tiện ích lân cận
    ├── profile/   # Hồ sơ Guest
    ├── report/    # Gửi báo cáo phòng
    └── room/      # Trang chủ, tìm/lọc phòng và chi tiết phòng
```

## Các file UI cần tìm

| Package | Activity và thành phần giao diện | ViewModel |
| --- | --- | --- |
| `ui.auth` | LoginActivity, RegisterActivity, ForgotPasswordActivity, BaseAuthActivity, SessionActivity, AuthMessages | AuthViewModel |
| `ui.room` | MainActivity, SpaceDetailActivity | RoomViewModel |
| `ui.booking` | BookingActivity, MyBookingsActivity, BookingSuccessActivity, BookingNoticesActivity | BookingViewModel, BookingNoticeViewModel |
| `ui.chat` | ConversationsActivity, ChatActivity | ChatViewModel |
| `ui.favorites` | FavoritesActivity | FavoritesViewModel |
| `ui.map` | MapActivity, NearbyPlacesActivity | MapViewModel, NearbyPlacesViewModel |
| `ui.host` | HostDashboardActivity, HostApplyActivity, AddBuildingActivity, CreateSpaceActivity, HostBookingsActivity, HostScheduleEditor | HostViewModel |
| `ui.admin` | AdminDashboardActivity, EvidencePreviewActivity | AdminViewModel, AdminEvidenceViewModel |
| `ui.profile` | ProfileActivity | Dùng hồ sơ từ SessionActivity |
| `ui.report` | RoomReportActivity | ReportViewModel |
| `ui.common` | GuestNavigation, ManagementNavigation, RoleAccountActivity, BookingNotifier | Dùng ViewModel của chức năng tương ứng khi cần |

Package theo chức năng không thay thế phân quyền. Ví dụ: `ui.booking` được nhiều vai trò sử dụng; quyền truy cập vẫn do kiểm tra phiên, nghiệp vụ và Firestore Rules quyết định.

## Đọc một chức năng như thế nào?

1. Mở Activity để tìm layout XML, phần Mapping và sự kiện nhấn nút.
2. Theo lời gọi sang ViewModel để xem trạng thái loading, dữ liệu và lỗi.
3. Mở Repository tương ứng để xem truy vấn, listener hoặc transaction Firebase.
4. Mở Model được dùng để hiểu các trường dữ liệu.
5. Nếu có danh sách, đọc Adapter và layout `item_*.xml` để hiểu cách hiển thị mỗi phần tử.

Ví dụ Auth: `ui.auth.LoginActivity → ui.auth.AuthViewModel → repository.AuthRepository → repository.FirebaseAuthRepository`. Model hồ sơ là `model.UserProfile`.

Ví dụ đặt chỗ: `ui.booking.BookingActivity → ui.booking.BookingViewModel → repository.BookingRepository`. Model chính là `model.Booking`; quy tắc thời gian/giá/hủy nằm trong các helper tương ứng của `utils`.

Mỗi Activity lấy ViewModel trong phạm vi vòng đời của nó. Hai Activity cùng dùng một class ViewModel không mặc nhiên dùng chung một instance.

## Quy ước khi viết thêm code

- Tạo Activity và ViewModel trong `ui.<chức năng>`. Thành phần dùng chung giữa nhiều màn hình đặt vào `ui.common` khi phù hợp.
- Khai báo class cần dùng ở phần `import`; trong thân class chỉ gọi tên ngắn như `UserProfile`, `HostBookingsActivity`, `CancellationPolicy`.
- `android.R` là ngoại lệ có chủ đích: đây là tài nguyên hệ thống Android, cần phân biệt với `R` của BroomSharing. Không thể import đồng thời hai class `R` bằng cùng tên ngắn.
- Comment tiếng Việt giải thích vai trò, phần Mapping, sự kiện và lý do xử lý; không chép lại ý nghĩa từng câu lệnh đơn giản.
- Giữ layout XML tại `res/layout`, menu tại `res/menu`; không tạo thư mục con tùy ý trong `res/layout`. Tên layout và ID hiện có không đổi khi chuyển package Java.
- Khi chuyển Activity: cập nhật package, import, Intent, Manifest, `tools:context` và mã kiểm thử. `applicationId`, namespace tài nguyên và tên database không thay đổi theo package màn hình.
