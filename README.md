# BROOM Sharing

**Ứng dụng di động kết nối người có không gian làm việc còn trống với người cần thuê linh hoạt.**

BROOM Sharing giúp tận dụng hiệu quả diện tích văn phòng chưa được sử dụng hết. Chủ không gian có thể đăng bàn làm việc, phòng họp hoặc khu vực văn phòng còn trống; người thuê tìm kiếm, xem lịch khả dụng và gửi yêu cầu đặt chỗ theo nhu cầu — từ vài giờ đến ngày, tuần hoặc tháng.

> Đây là đồ án ứng dụng di động. README mô tả ý tưởng, phạm vi và định hướng chức năng của dự án; các chức năng bên dưới là yêu cầu/roadmap, không mặc định rằng chúng đã được triển khai.

## Thành viên

| STT | Họ và tên | Mã số sinh viên |
| --- | --- | --- |
| 1 | Lữ Võ Hoàng Phúc | 31241025389 |
| 2 | Từ Trương Thanh Trúc | 3124102353 |
| 3 | Lâm Hoàng | 31221025740 |

## Tổng quan

Nhiều doanh nghiệp, cá nhân và đơn vị vận hành coworking có diện tích không được sử dụng liên tục: bàn trống vào một số ngày, phòng họp chưa có lịch, hoặc một phần văn phòng chưa được khai thác. Trong khi đó, nhóm nhỏ, người làm việc tự do và doanh nghiệp lại cần địa điểm làm việc phù hợp trong một khoảng thời gian ngắn hoặc linh hoạt.

BROOM Sharing tạo một nền tảng di động để hai nhu cầu này gặp nhau:

```text
Chủ không gian có diện tích còn trống
                 ↓
             BROOM Sharing
                 ↓
Người cần nơi làm việc theo thời hạn linh hoạt
```

Mục tiêu là giúp chủ không gian khai thác phần diện tích nhàn rỗi, đồng thời giúp người thuê dễ tìm và đặt nơi làm việc phù hợp với vị trí, sức chứa, tiện ích, ngân sách và lịch trình.

## Đối tượng sử dụng

- **Người thuê:** tìm không gian, xem thông tin và lịch trống, gửi yêu cầu đặt chỗ, trao đổi với chủ và quản lý các lượt đặt.
- **Người cho thuê (chủ không gian):** đăng không gian, thiết lập giá và lịch khả dụng, tiếp nhận yêu cầu và quản lý các bài đăng.
- **Quản trị viên:** kiểm duyệt bài đăng, quản lý tài khoản, theo dõi booking và tiếp nhận báo cáo.

Một tài khoản người dùng có thể đảm nhiệm cả vai trò người thuê lẫn người cho thuê; không cần tạo hai tài khoản riêng.

## Loại không gian

BROOM Sharing tập trung vào không gian làm việc và có thể hỗ trợ:

- Bàn làm việc linh hoạt (hot desk) hoặc bàn cố định (dedicated desk).
- Phòng làm việc riêng và văn phòng dùng chung.
- Phòng họp.
- Văn phòng nguyên căn, tầng hoặc khu vực văn phòng chưa sử dụng.
- Không gian tổ chức workshop hoặc sự kiện phù hợp với định hướng dự án.

Chủ không gian có thể cung cấp mô tả, hình ảnh, địa chỉ, vị trí, diện tích, sức chứa, tiện ích, nội quy, giá và thời gian có thể cho thuê.

## Chức năng dự kiến

### Phạm vi MVP

Các chức năng cốt lõi được ưu tiên cho phiên bản đầu:

- Đăng ký, đăng nhập, đăng xuất và quản lý hồ sơ người dùng.
- Đăng, sửa và quản lý thông tin không gian.
- Duyệt danh sách, tìm kiếm và lọc theo khu vực, loại không gian, thời gian, sức chứa, giá và tiện ích.
- Xem trang chi tiết và lịch khả dụng của không gian.
- Gửi, xác nhận hoặc từ chối yêu cầu đặt chỗ.
- Quản lý booking của người thuê và chủ không gian.
- Kiểm duyệt bài đăng và xử lý báo cáo ở phía quản trị.

### Hướng phát triển tiếp theo

Khi phạm vi và nguồn lực cho phép, dự án có thể bổ sung:

- Bản đồ, vị trí lân cận và chỉ đường.
- Đặt không gian theo giờ, ngày, tuần hoặc tháng; hỗ trợ đặt nhóm và gia hạn booking.
- Thanh toán trực tuyến, lịch sử giao dịch và thông báo trạng thái đặt chỗ.
- Trò chuyện giữa người thuê và chủ không gian.
- Danh sách yêu thích, đánh giá và nhận xét sau khi hoàn tất booking.
- Mã QR cho quy trình check-in/check-out.
- Tài khoản đối tác đã xác minh và bảng thống kê cho chủ không gian.
- Gợi ý không gian phù hợp dựa trên tiêu chí tìm kiếm.

Các ý tưởng như chatbot AI, khóa thông minh, cảm biến IoT, định giá linh hoạt và hợp đồng điện tử là hướng mở rộng trong tương lai, không thuộc cam kết của MVP.

## Luồng sử dụng chính

### Người thuê

1. Đăng nhập và nhập nhu cầu về khu vực, thời gian, sức chứa, loại không gian hoặc ngân sách.
2. Tìm kiếm, lọc kết quả và xem thông tin chi tiết.
3. Kiểm tra lịch trống và gửi yêu cầu đặt chỗ.
4. Theo dõi trạng thái yêu cầu và trao đổi với chủ không gian khi cần.
5. Sử dụng không gian theo booking; đánh giá sau khi hoàn tất nếu chức năng này được triển khai.

### Người cho thuê

1. Tạo bài đăng với mô tả, vị trí, hình ảnh, sức chứa, tiện ích và nội quy.
2. Thiết lập giá thuê và lịch khả dụng theo thời hạn phù hợp.
3. Gửi bài đăng để kiểm duyệt và công khai sau khi được duyệt.
4. Xem yêu cầu đặt chỗ, xác nhận hoặc từ chối và quản lý lịch sử dụng.

## Quy tắc nghiệp vụ quan trọng

- Không chấp nhận các booking chồng lấn thời gian trên cùng một không gian.
- Không hiển thị công khai không gian đang ẩn, bị đóng hoặc chưa được duyệt.
- Chỉ người đã hoàn tất booking mới có thể đánh giá không gian.
- Chỉ chủ sở hữu được chỉnh sửa bài đăng của mình; quản trị viên có quyền kiểm duyệt và xử lý báo cáo.
- Lưu lại mức giá tại thời điểm đặt để booking không bị thay đổi theo lần cập nhật giá sau đó.
- Nếu tích hợp thanh toán, trạng thái thanh toán cần được xác nhận từ máy chủ hoặc đơn vị cung cấp dịch vụ thanh toán.
- Nếu dùng QR check-in, mã phải gắn với booking hợp lệ.
- Không đưa mật khẩu dạng rõ hoặc khóa API bí mật vào mã nguồn.

## Nền tảng và công nghệ

Dự án dùng **Android Java + XML**, AndroidX và Material Components. Module Auth đã có UI đăng nhập/đăng ký/quên mật khẩu, ViewModel/LiveData và repository Firebase Auth + Cloud Firestore. Đăng nhập hỗ trợ username hoặc email kèm mật khẩu; Google Sign-In, số điện thoại và đặt lại mật khẩu triển khai sau. Firebase project chưa kết nối vào mã nguồn; nhà cung cấp Maps đang chờ chốt.

## Hướng dẫn cài đặt và chạy

Mở thư mục `BroomSharing` trong Android Studio và Gradle Sync. App khởi động ở `LoginActivity`; thiếu cấu hình Firebase vẫn xem được ba màn hình Auth. Đặt `google-services.json` tại `app/`, bật Email/Password và tạo Cloud Firestore để thử đăng nhập/đăng ký thật. Xem [hướng dẫn Auth đợt 1](docs/auth-dot-1.md) cho schema, Rules, checklist và tiến độ P01–P10.

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

## Trạng thái dự án

**Giai đoạn:** đợt 1, triển khai trước module Auth theo phạm vi Phúc đã chốt.

Các màn hình nghiệp vụ còn lại là prototype. Mã Auth cần kiểm thử với Firebase project thật sau khi kết nối. Theo yêu cầu demo của Phúc, file Firestore Rules local đang mở đọc/ghi; phân quyền database theo P06 chưa hoàn tất. Chưa deploy Firebase hoặc tích hợp Maps trong phần việc hiện tại.

## Đóng góp

Đây là đồ án nhóm. Các thành viên có thể cùng cập nhật tài liệu, thiết kế, mã nguồn và tiến độ triển khai thông qua các nhánh và pull request của repository.
