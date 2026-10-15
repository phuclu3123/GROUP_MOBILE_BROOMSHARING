# Đợt 1: đăng nhập và đăng ký

> Cập nhật 15/10/2026: Phúc đã thêm cấu hình Firebase local và xác nhận database ID là `broomsharing` trong project BRoom-Sharing-Firebase-Mobile. Auth và Host đã dùng chung database này; xem [ghi chú tích hợp](integration-dot-1.md). Phần mô tả kiểm thử ngày 14/10 bên dưới ghi lại trạng thái trước khi kết nối.

## Phạm vi Phúc đã chốt

- Làm trước UI đăng nhập, đăng ký, quên mật khẩu; viết luồng đăng nhập và đăng ký với database.
- Đăng nhập bằng username **hoặc** email, kèm mật khẩu.
- Google Sign-In, đăng nhập SMS/OTP và gửi email đặt lại mật khẩu triển khai sau. Số điện thoại liên hệ đã được thêm vào đăng ký. Nút hiện tại thông báo chưa hỗ trợ, không gọi API và không báo thành công giả.
- Firebase project đã có nhưng Phúc sẽ kết nối sau. Không tạo project hay deploy Rules từ phiên làm việc này.
- HOST dùng được chức năng của GUEST. Đăng ký mới luôn tạo GUEST, không có lựa chọn tự cấp HOST/ADMIN.
- Maps chưa chốt nhà cung cấp, tạm hoãn cấu hình SDK/API.
- Theo yêu cầu trực tiếp của Phúc, `firestore.rules` dùng `allow read, write: if true` như ví dụ bài giảng. Đây là lựa chọn demo; P06 và kiểm thử từ chối truy cập chưa đạt. Kiểm tra vai trò trong app chỉ điều hướng giao diện, không bảo vệ database với Rules này.

## Cấu trúc đã thêm

```text
ui/auth/
  LoginActivity, RegisterActivity, ForgotPasswordActivity
  BaseAuthActivity, AuthViewModel, AuthMessages, SessionActivity
repository/
  AuthRepository, FirebaseAuthRepository, AuthError
model/
  UserProfile, UserRole
utils/
  AuthValidation
res/layout/
  activity_login, activity_register, activity_forgot_password
  activity_session_check
res/values/
  auth_strings, auth_styles
```

Activity dùng XML, Explicit Intent và ViewModel/LiveData. Repository thực hiện Firebase calls bất đồng bộ. Mật khẩu không được ghi vào Firestore, SharedPreferences hoặc Bundle của app; mật khẩu được truyền cho Firebase Auth. Các ô mật khẩu không lưu view state. ViewModel giữ trạng thái tác vụ khi xoay màn hình, không giữ Activity/View.

## Luồng tài khoản

**Đăng ký:** validate → kiểm tra username tồn tại → Firebase Auth tạo email/password → transaction Firestore đọc username rồi ghi đồng thời hồ sơ và ánh xạ username → điều hướng trang chủ.

Tên đăng nhập gồm 3–24 ký tự chữ ASCII, số hoặc `_`, không phân biệt hoa/thường. Ví dụ `Phuc_Lu` và `phuc_lu` cùng một tên đăng nhập. Quy ước này là cách triển khai tính năng username Phúc đã chọn, không phải một ràng buộc ghi trong slide.

Transaction kiểm tra lại username để ngăn hai tác vụ đăng ký đồng thời nhận cùng tên. Nếu lưu hồ sơ thất bại, repository thử xóa tài khoản Auth vừa tạo và đăng xuất. Nếu không thể xóa, UI báo tài khoản chưa hoàn tất, cần kiểm tra Auth và Firestore; không báo thành công. Auth và Firestore không hỗ trợ cùng một transaction nên vẫn cần kiểm tra tài khoản dở dang nếu app bị tắt giữa hai bước.

**Đăng nhập email:** Firebase Auth kiểm tra mật khẩu → tải `users/{uid}` từ server → kiểm tra `status` và `role` → GUEST/HOST vào `MainActivity`, ADMIN vào `AdminDashboardActivity`.

**Đăng nhập username:** đọc `usernames/{usernameLowercase}` từ server → lấy email → thực hiện cùng luồng đăng nhập email. Không query mật khẩu trong database.

**Phiên:** Firebase Auth quản lý phiên. Mở app kiểm tra phiên đã có và tải lại hồ sơ từ server. Không có hồ sơ hợp lệ hoặc mất mạng thì hiện lỗi, không mở nội dung dựa vào role cache. Trang chủ, hồ sơ và dashboard Admin kiểm tra phiên trước khi mở layout. Trang chủ có nút Hồ sơ để tới Đăng xuất; đăng xuất xóa task stack để Back không trở về trang đã đăng nhập.

## Database tối thiểu cho Auth

`users/{uid}`:

| Field | Kiểu | Ý nghĩa |
| --- | --- | --- |
| schemaVersion | number | `1` |
| uid | string | UID của Firebase Auth và document ID |
| name | string | Họ tên, tối đa 100 ký tự |
| username | string | Tên đăng nhập chuẩn hóa chữ thường |
| email | string | Email đăng ký Firebase Auth |
| role | string | `GUEST`, `HOST`, `ADMIN`; đăng ký mới chỉ `GUEST` |
| status | string | Đăng ký mới `ACTIVE`; trạng thái khác sẽ chặn vào trang chính |
| createdAt, updatedAt | timestamp | Server timestamp |

`usernames/{usernameLowercase}`:

| Field | Kiểu | Ý nghĩa |
| --- | --- | --- |
| uid | string | UID của người sở hữu |
| email | string | Email cho Firebase Auth xác thực username/password |

Collection `usernames` bổ sung để hỗ trợ yêu cầu username; không có trong P05 ban đầu. Hai document được ghi trong một transaction; không cần composite index cho các lần đọc document ID này. Với phương án hiện tại, lookup username cần đọc email trước đăng nhập. Khi làm P06, phải thiết kế lại quyền đọc hoặc chuyển lookup sang backend, đồng thời ngăn sửa registry và role tùy ý. Username chưa có chức năng đổi tên.

## Kết nối Firebase sau

1. Trong Firebase project của Phúc, thêm Android app với package **`vn.edu.ueh.phuclu.broomsharing`**.
2. Tải `google-services.json`, đặt đúng tại **`BroomSharing/app/google-services.json`**.
3. Trong Authentication, bật provider **Email/Password**. Google Sign-In và Phone không cần bật lúc này.
4. Sử dụng Cloud Firestore database ID **`broomsharing`** theo lựa chọn Phúc đã xác nhận. File Rules local chưa tự áp dụng lên project. Nếu làm demo theo lựa chọn đã chốt, copy nội dung `firestore.rules` vào Rules của đúng database này và Publish bằng tài khoản của Phúc.
5. Gradle Sync rồi build lại. Google services plugin chỉ được áp dụng khi file JSON tồn tại. Thiếu JSON vẫn build/mở được UI, nhưng không đăng nhập hoặc đăng ký thật.
6. Chạy checklist bên dưới với project thật trước khi ghi P03 hoàn tất.

Firebase SDK được quản lý bằng BoM; project dùng Auth và Firestore. Hướng dẫn chính thức: [cấu hình Android](https://firebase.google.com/docs/android/setup), [email/password Authentication](https://firebase.google.com/docs/auth/android/password-auth).

## Checklist kiểm tra sau khi kết nối

- Đăng ký email mới và username mới: có Auth user cùng UID với `users/{uid}` và `usernames/{username}`. Không có field mật khẩu.
- Đăng nhập lại bằng email và bằng username viết hoa/thường: cùng tài khoản.
- Email đã dùng hoặc username đã dùng: báo lỗi; không ghi đè hồ sơ.
- Hai đăng ký đồng thời cùng username: chỉ một registry; tài khoản thất bại được thử cleanup.
- Sai mật khẩu, email/username chưa tồn tại: không mở trang chủ.
- Ngắt mạng khi lookup/lưu hồ sơ: có lỗi; không báo thành công.
- Tắt app, mở lại: phiên Firebase Auth được khôi phục; hồ sơ lấy từ server.
- Hồ sơ thiếu/role không hợp lệ/status bị khóa: không mở trang chính.
- Đăng xuất rồi Back hoặc mở app lại: vẫn ở đăng nhập.
- GUEST và HOST cùng có luồng thuê; ADMIN vào dashboard. Chỉ chỉnh role bằng công cụ quản lý dự án trong bài demo, không có nút tự nâng quyền.
- Google, điện thoại, quên mật khẩu: thông báo sẽ bổ sung sau; không gửi yêu cầu API.

## Kết quả kiểm tra ngày 14/10/2026

- Build APK debug thành công, chưa có `google-services.json`.
- 6 unit test: ranh giới độ dài username, ký tự không hợp lệ, chuẩn hóa hoa/thường độc lập locale, bảo toàn mật khẩu chứa khoảng trắng và HOST kế thừa GUEST. Không có failure/error.
- 6 test UI chạy trên emulator Pixel 4: không báo đăng nhập thành công khi thiếu Firebase, input rỗng, username không hợp lệ, mật khẩu xác nhận sai, các nút để sau/điều hướng và tái tạo Activity. Đều qua.
- Đã xem ảnh chụp thực tế của Login/Register/Forgot Password trên emulator. Form đăng ký cuộn để tới các nút phía dưới.
- `lintDebug` thành công: 0 lỗi, 419 cảnh báo; phần lớn từ các layout prototype cũ (chuỗi hardcode, kích thước chữ và resource chưa dùng). Ba layout Auth có cảnh báo Overdraw do cùng màu nền với window, không chặn build.
- Sửa 5 lỗi `android:tint` thành `app:tint` ở các layout prototype theo kiểm tra AppCompat, giữ màu và bố cục.
- Môi trường kiểm tra bị timeout HTTPS trong Java. Thư viện được lấy từ cache có sẵn và kho Maven chính thức bằng helper tạm, rồi dùng repository file local trong lệnh kiểm tra. Repository Google/Maven Central trong project không bị thay đổi. Gradle Sync thông thường vẫn cần truy cập được các kho thư viện.
- Chưa chạy đăng ký/đăng nhập trên Firebase project thật, chưa deploy Rules, chưa kiểm thử phân quyền database. Chỉ ghi P03 hoàn tất sau khi checklist database phía trên đã qua.

## Đối chiếu bài giảng

- Buổi 2, `02.1 Activities and Intents`: Activity/Intent, khai báo Manifest và back stack.
- Buổi 2, `02.2 Activity lifecycle and state`, slide 31–37: view có ID, lưu trạng thái và khôi phục Activity.
- Buổi 4, `04.2 Input controls`, slide 19–24: `EditText`, inputType email/password và lấy nội dung form.
- Buổi 5, `05.1 Drawables, styles, and themes`, slide 27–35: styles/resources/theme dùng lại.
- Buổi 5, `05.2 Material Design`, slide 8, 13, 21, 26–30: khoảng trắng, typography, contrast, dp/sp và Material fields/buttons.
- Buổi 9, `10.1_ Room, LiveData, and ViewModel`, slide 42–55 và 60–71: ViewModel, Repository, LiveData và lifecycle. Slide 89–92: Firestore config, model, get và callbacks.
- `FireBase_Guide.pdf`, trang 1–3: kết nối Firebase và đọc/ghi Firestore. Rules true theo trang 2 là lựa chọn Phúc đã xác nhận.

Phần username registry và Firebase Authentication là ứng dụng yêu cầu của đồ án vào nền tảng bài giảng; không tuyên bố đó là code có sẵn trong slide.

## Tiến độ P01–P10 sau khi thu hẹp phạm vi

| Mục | Tình trạng |
| --- | --- |
| P01 Firebase chung | Đã chuẩn bị SDK và hướng dẫn; chờ Phúc kết nối project |
| P02 package Java/XML | Tạo cấu trúc Auth/model/repository/utils; chưa refactor toàn app |
| P03 Auth | UI và mã đăng nhập/đăng ký/đăng xuất đã viết; chờ kiểm thử Firebase thật |
| P04 role | Đăng ký GUEST, kiểm tra route; HOST kế thừa chức năng thuê; duyệt HOST/backend còn sau |
| P05 schema | Có schema tối thiểu Auth; schema toàn bộ nghiệp vụ chưa chốt |
| P06 Rules | Demo mở true theo Phúc; phân quyền database và denied-write tests chưa đạt |
| P07 Storage | Chưa triển khai trong phạm vi Auth |
| P08 Maps | Hoãn theo yêu cầu mới, chưa chọn nhà cung cấp |
| P09 shared contract | AuthRepository/UserProfile/UserRole/AuthError; RoomDTO/BookingDTO còn sau |
| P10 Admin | Giữ dashboard prototype, thêm kiểm tra phiên/role; moderation và dữ liệu dashboard còn sau |

Sheet phân công còn có khác biệt tên collection `verifications/audit` với `propertyVerifications/auditLogs`; chưa tạo các collection này trong phần Auth. Cần Phúc chốt khi quay lại P05.

### Bổ sung số điện thoại vào form đăng ký

Số điện thoại bắt buộc, được chuẩn hóa và lưu cùng profile với phoneVerified=false. Chưa gửi OTP/chưa đăng nhập bằng số điện thoại; bước đó sẽ liên kết vào cùng UID sau khi xác minh. Tài khoản cũ chưa có số vẫn đăng nhập được. Xem schema.md và manual-check-dot-1.md cho định dạng và ca kiểm tra.
