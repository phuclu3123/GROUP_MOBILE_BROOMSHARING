# Tích hợp nhánh Trúc — đợt 1

Đã fetch `origin/truc` tại commit `e6ebd15` ngày 15/10/2026. Code Trúc được ghép có chọn lọc vào bản Auth/Host/Admin trong commit tích hợp đợt 1; không merge nguyên package/ứng dụng riêng của Trúc. Không dùng JSON Firebase, package, Gradle hoặc Auth mô phỏng của nhánh Trúc.

## Những khác biệt đã xử lý

- Package Trúc `vn.edu.ueh.tructu.mobile_project_group` → giữ package chung `vn.edu.ueh.phuclu.broomsharing`.
- Trúc dùng Firestore mặc định → dùng `FirebaseHelper`, database `broomsharing`.
- Trúc `name/price/images/PUBLISHED/ownerStatus` → giữ `roomName`, giá giờ/tháng/quý, `imageUrls`, `APPROVED + ACTIVE`. Không tạo Room thứ hai hoặc đổi dữ liệu cloud.
- Auth Trúc chỉ validate rồi chuyển màn hình/thông báo thành công → giữ FirebaseAuthRepository/AuthViewModel thật, username/email/password và phone liên hệ đã có.
- Nhãn cố định “Đã xác minh” → “Nội dung đã được Admin duyệt”. Duyệt tin không xác minh độc lập địa điểm.
- RoomDetail gọi Firestore trực tiếp → Activity → RoomViewModel → RoomRepository → model RoomListing/Room/Building.
- Tái sử dụng và điều chỉnh card, placeholder, menu bottom navigation và bộ kích thước từ Trúc. Giữ palette/theme chung, không chép app/theme thứ hai.
- Không chép Fragment mới chỉ hiển thị chữ nếu Activity hiện tại đã có chức năng tương ứng. Activity là lựa chọn đơn giản trong cùng mô hình MVVM.

## Đối chiếu U01–U08

| Mục | Code hiện tại / giới hạn |
| --- | --- |
| U01 | Dùng màu/theme chung, dimens.xml, style và string guest; Material toolbar/button, card, loading/empty/error, ảnh lỗi/thiếu là placeholder trung tính. Chưa thiết kế lại toàn bộ màn prototype của những đợt sau. |
| U02 | Bottom navigation chung Trang chủ/Bản đồ/Đặt chỗ/Tin nhắn/Hồ sơ; giữ back stack qua Activity, Up ở chi tiết. HOST có thể thuê và mở quản lý; GUEST mở đơn Host; ADMIN đi dashboard riêng qua SessionActivity. Bản đồ/Đặt chỗ/Tin nhắn mở màn trạng thái cùng shell; nghiệp vụ đợt sau chưa triển khai. |
| U03 | Dùng chung các màn Auth và repository hiện tại, giữ validation/phone; không tạo luồng Auth giả của Trúc. Chưa thêm màn chào riêng vì chưa cần cho luồng hiện tại. |
| U04 | Tìm tên phòng/địa chỉ/tòa nhà, khu vực từ district/address tòa nhà; loại phòng và mode giờ/tháng-quý; bỏ lọc; tìm không phân biệt hoa thường/dấu tiếng Việt. Không tìm gần tôi hay tính khoảng cách. |
| U05 | Card dùng ảnh thật/placeholder; đơn vị giá đúng mode, sức chứa, diện tích, loại phòng, khu vực; nhãn duyệt nội dung có nguồn. |
| U06 | Query server APPROVED/ACTIVE, thứ tự document ID, 20 document/trang. Kiểm tra users owner HOST/ACTIVE và tòa nhà ACTIVE/đúng owner trước hiển thị. Kiểm tra lại khi mở chi tiết. Đọc lỗi phải báo lỗi, không biến lỗi thành danh sách trống. |
| U07 | Chi tiết theo ROOM_ID thật: gallery, giá/cọc, giờ, tiện ích/nội quy, chủ, tên/liên hệ/địa chỉ/quận/hướng dẫn vào của tòa nhà, tọa độ nếu có. Không đặt chỗ giả, không map API. |
| U08 | ViewModel giữ các trang khi xoay; Bundle giữ bộ lọc/vị trí cuộn; ROOM_ID từ Intent; giữ back stack khi mở tài khoản/chi tiết; không tạo snapshot listener nên không có listener cần tháo. Process bị hệ điều hành hủy: khôi phục bộ lọc, tải lại từ trang đầu; chưa khôi phục toàn bộ cache nhiều trang. |

Bộ lọc chạy trên các trang đã tải, không phải full-text search toàn database. UI ghi rõ phạm vi và có nút Tải thêm, kể cả khi trang hiện tại không có kết quả hợp lệ. Chưa thêm search engine hoặc index theo mọi tổ hợp bộ lọc cho đồ án đợt 1.

## Bám bài giảng

- Buổi 2: Layouts/resources, Activities/Intents, lifecycle/state.
- Buổi 4: Input controls, User navigation, RecyclerView (Adapter/ViewHolder/LayoutManager).
- Buổi 5: Drawables/styles/themes, Material Design.
- Buổi 9: LiveData/ViewModel/Repository và Firebase callbacks.

Bổ sung pagination và ghép thông tin phòng/tòa nhà/chủ ở repository để dùng được dữ liệu Host hiện tại. Không DI framework, backend mới, use-case layer, Rx hoặc Navigation graph.

## Phạm vi kiểm tra và Rules

Giữ `firestore.rules = true` theo lựa chọn của Phúc; không deploy. Guest repository hiện đọc users và buildings để đối chiếu trạng thái/chủ và lấy tên/liên hệ tòa nhà. File `firestore.secure.rules` vẫn chỉ cho chủ/Admin đọc các tài liệu này: **chưa chuyển sang file secure để thử luồng guest**. Trước khi chuyển, cần projection hồ sơ/tòa nhà công khai tối thiểu hoặc một cách kiểm tra phía server phù hợp; không mở toàn bộ users chỉ để cho guest xem tên. Đây là phần server access còn lại, không coi P06 đã hoàn thành nhờ client guard.

Không chạy thiết bị, Auth/Firestore/Storage thật hoặc sửa dữ liệu cloud trong lượt tích hợp này. Xem checklist dưới đây; kết quả build cục bộ được ghi sau khi chạy xong.

## Tự thử nhanh

1. Firebase Authentication bật Email/Password; app dùng đúng JSON/project và database broomsharing. Lỗi CONFIGURATION_NOT_FOUND cần sửa cấu hình Console, không cần Google OAuth.
2. Đăng nhập GUEST. Home không hiển thị room DRAFT, PENDING_REVIEW, REJECTED, INACTIVE/SUSPENDED; không có dữ liệu thì có trạng thái trống.
3. Dùng HOST đã được Admin duyệt tạo tòa nhà ACTIVE + phòng đủ thông tin/ảnh, gửi duyệt; ADMIN duyệt phòng; GUEST tải lại thấy card đúng giá/mode/ảnh.
4. Tìm tên không dấu/khác hoa thường; lọc khu vực, từng loại và HOURLY/FIXED; thử kết hợp không khớp rồi xóa lọc. Kiểm tra giá quý khi phòng FIXED không có giá tháng.
5. Với hơn 20 document đã duyệt/ACTIVE: bấm Tải thêm, không trùng room ID. Nếu một trang bị lọc hết do chủ/tòa nhà không hợp lệ vẫn có thể tải trang sau. Bộ lọc chỉ xét các trang đã tải như dòng mô tả trên màn hình.
6. Mở phòng, kiểm tra gallery, nội quy, giờ, cọc, host, liên hệ/địa chỉ/quận/tọa độ tòa nhà. Thiếu dữ liệu không hiện chữ null; ảnh lỗi có placeholder. Không có huy hiệu xác minh địa điểm tự động.
7. Sau khi tải nhiều trang/lọc/cuộn: mở chi tiết, quay lại; xoay màn hình; kiểm tra bộ lọc và cuộn. Xoay ở chi tiết kiểm tra ROOM_ID và vị trí cuộn. Process bị kill chỉ hứa giữ bộ lọc, không hứa cache toàn danh sách.
8. Trong Console đổi chủ status không ACTIVE hoặc role không HOST, hoặc tòa nhà status không ACTIVE. Home tải lại phải ẩn; mở ROOM_ID trực tiếp/chi tiết tải lại phải báo không công khai. Đổi phòng về PENDING_REVIEW cũng phải ẩn.
9. Tắt mạng rồi Tải lại: có thông báo lỗi và thử lại được; không hiển thị thông báo “thành công” giả.
10. GUEST không vào quản lý Host/Admin; HOST vẫn thuê được; ADMIN vào dashboard; đăng xuất xóa luồng tài khoản.

Đợt sau: booking engine, OTP/Google/reset mật khẩu, bản đồ/Places/Routes, xác minh địa điểm thực tế và việc chuyển Rules phân quyền.

## Kết quả kiểm tra cục bộ (15/10/2026)

`assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest`: BUILD SUCCESSFUL. 27 unit test, 0 thất bại/0 lỗi. Lint 0 lỗi/455 cảnh báo (gồm cảnh báo hiện hữu). APK instrumentation đã biên dịch, chưa chạy trên thiết bị. Chưa kiểm chứng luồng Firebase thật.

## Đồng bộ UI sau khi Phúc thử trên máy

- Home/Profile và ba màn trạng thái dùng chung `view_guest_navigation.xml`, menu và `GuestNavigation`: cùng 5 mục, icon, màu và tab đang chọn.
- Home: search toàn chiều rộng, bộ lọc có nhãn nằm trong vùng mở/thu gọn; nút tải lại riêng, bỏ CTA Host chen ngang. Giữ bộ lọc/vị trí cuộn cũ; số lượng kết quả gọn thay phần chú thích kỹ thuật chiếm cuối màn hình.
- Profile: cùng toolbar/palette; bỏ số liệu mẫu 12/2/4,9, tên mẫu và menu tài khoản chưa có thao tác. Hiển thị thông tin tài khoản thật và giữ nguyên menu/luồng đăng ký Host, quản lý Host, đăng xuất.
- Bản đồ/Đặt chỗ/Tin nhắn: cùng shell, có mô tả trạng thái và nút về Trang chủ. Không dùng popup khi chuyển tab, không dùng danh sách/QR/chats mẫu như dữ liệu thật. Các layout prototype cũ còn trong source để tham khảo, không dùng làm màn hiện tại.
- Không sửa AuthRepository, schema, host onboarding, phân quyền hoặc dữ liệu Firebase trong lượt sửa UI này.

Tự thử: mở lần lượt cả 5 tab và kiểm tra mục đang chọn; từ Hồ sơ về Trang chủ rồi bấm Back; lọc/cuộn → Hồ sơ → Trang chủ giữ trạng thái; mở/thu bộ lọc và xoay màn hình; GUEST mở lại đơn Host; HOST mở quản lý; đăng xuất rồi Back không về tài khoản cũ. Danh sách trống khi chưa có phòng được duyệt/ACTIVE là trạng thái dữ liệu, không phải vì đã đăng ký Host thì tự có phòng.

Kiểm tra sau đồng bộ UI: assembleDebug/testDebugUnitTest/lintDebug/assembleDebugAndroidTest thành công; 27 unit test qua, Lint 0 lỗi/421 cảnh báo. Chưa chạy hoặc cài lại app trên thiết bị; Phúc kiểm tra giao diện bằng bản Run mới.

Màn kiểm tra phiên chỉ hiển thị tiến trình/thông báo, lỗi mới hiện Thử lại; đã bỏ nút Đăng xuất ở màn chờ. Đăng xuất tài khoản vẫn có trong Hồ sơ.
