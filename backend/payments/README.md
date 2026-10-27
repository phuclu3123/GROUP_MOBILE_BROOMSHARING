# Backend thanh toán BroomSharing

Java 17, HTTP server của JDK, Firebase Admin SDK và SDK payOS chính thức. Không dùng Spring Boot. Android giữ Java + XML, MVVM + Repository + Model.

## Phạm vi

- Booking mới dùng `paymentFlow=INSTANT_5M`: khách bấm thanh toán thì giữ lịch tối đa 5 phút và mở QR ngay. Backend nhận đủ 40% đúng hạn mới chuyển `PENDING` thành `CONFIRMED`, không cần Host duyệt. Hạn tính từ `createdAt` của server và không vượt giờ bắt đầu thuê; retry không kéo dài hạn.
- Chỉ Host trong `PAYMENT_DEMO_HOST_UID` nhận booking mới qua kênh này. Backend công bố UID/phiên bản luồng tại `appConfig/payments`; không công bố khóa payOS. Booking cũ giữ nguyên lịch sử và cơ chế tương thích. Không nhận giá hoặc UID người thuê từ request body.
- Một `PAYMENT_DEMO_HOST_UID` gắn với một kênh merchant payOS của nhóm. Tiền vào tài khoản ngân hàng của kênh đó, không tự chuyển đến email hay tài khoản riêng của từng Host.
- Không triển khai API hoàn tiền. Hủy đơn vẫn nhả lịch theo chính sách hiện có; số tiền cần đối soát được ghi với `refundStatus=MANUAL_REVIEW`, không đánh dấu đã hoàn.
- Khoản thanh toán không tự chuyển booking sang CHECKED_IN/COMPLETED, không gia hạn lịch. Chưa bắt buộc thu nốt 60% còn lại bằng API.

## Cấu hình và chạy

1. Tạo `backend/payments/.env` theo `.env.example`. Bộ ba khóa payOS đã được sao chép vào file local riêng, không nằm trong APK/Git. Khi đổi kênh phải thay cả ba khóa của cùng kênh.
2. Khai báo `GOOGLE_APPLICATION_CREDENTIALS` bằng đường dẫn file service-account JSON. File nằm ngoài Git, hoặc trong `credentials/` đã ignore. Trên hosting có thể dùng Application Default Credentials thay file khóa. `app/google-services.json` không phải khóa server. Server cần quyền đọc tài khoản Firebase Authentication và đọc/ghi Firestore của project.
3. Khai báo UID một tài khoản role HOST, status ACTIVE trong `PAYMENT_DEMO_HOST_UID`. Không tự đổi tài khoản Admin sang Host.
4. Khai báo `PUBLIC_BASE_URL=https://<địa-chỉ-server>`; `FIRESTORE_DATABASE_ID=broomsharing`. HTTPS có thể được xử lý bởi hosting hoặc tunnel demo tới cổng 8090 trên máy.
5. Từ thư mục `backend/payments`, chạy `../../gradlew.bat -p . run`. Java 17 trở lên; có thể đặt `JAVA_HOME` trỏ JBR của Android Studio.
6. Giữ webhook payOS hiện có của WEB_TCC. Bản WEB_TCC bổ sung relay tại commit `d00716407350d1f61e253e1ad68e43b34bca962d`; sau khi Render chạy bản này, dùng `tools/payments/connect-relay.ps1` để đăng ký URL Java bằng token setup server hiện có. Không gọi API đổi webhook của payOS. Luồng khóa học của web giữ nguyên; relay chỉ nhận vùng orderCode của BroomSharing và chuyển nguyên payload có chữ ký về Java.
7. Root `.env` Android chỉ thêm `PAYMENT_API_BASE_URL=https://<địa-chỉ-server>`, rồi build lại APK. Không thêm checksum/API secret vào root `.env` để dùng phía client.

Chưa cấu hình service account, Host UID hoặc HTTPS thì không khởi động server tiền thật. Bản APK chưa có URL backend sẽ báo chưa cấu hình kênh.

## Chạy demo trên máy hiện tại

Tài khoản server riêng đã được cấp `roles/datastore.user` với IAM Condition chỉ database `broomsharing`, cùng `roles/firebaseauth.viewer` để kiểm tra phiên. Khóa nằm tại `backend/payments/credentials/service-account.json`, bị Git ignore. Không dùng khóa Admin rộng hoặc refresh token cá nhân để chạy server.

Từ root, build backend bằng `gradlew.bat -p backend/payments installDist`, rồi chạy `powershell -NoProfile -File tools/payments/run-demo.ps1`. Script mở Java/tunnel bằng tiến trình ẩn, kiểm tra Firebase thật trước khi server sẵn sàng, xác minh `/health` qua HTTPS và cập nhật URL vào hai file `.env`. Cloudflared portable được kiểm SHA-256 theo digest bản phát hành chính thức. Có thể chạy script dừng với tham số `-Stop`; script chỉ dừng PID và thời gian khởi động do chính nó ghi nhận.

Mỗi lần mở tunnel mới có thể đổi URL, nên cần build APK lại. Nếu Render chưa deploy relay, server Java vẫn hoạt động cho tạo QR/đối soát trực tiếp; webhook chuyển tiếp chỉ hoạt động sau khi chạy thành công `powershell -NoProfile -File tools/payments/connect-relay.ps1`. Không cần khởi động lại tunnel để đăng ký relay. Script dùng `PAYOS_RELAY_TOKEN` ở backend, không đưa token vào app, không thay cấu hình webhook payOS.

QR mẫu thật đã tạo ở booking `78cb225b84f342b8d0eb2b6dc88547bd6e48071c24e2366f907a257074419fa7`, phòng `payos_demo_20261026_room`, khoản trả trước 2.000đ. Tài khoản Host dùng nhóm `applicant.*`, Guest dùng `guest.*` trong file local `data/demo/profile-identity-credentials.local.properties`. Không commit file này hoặc bản sao mật khẩu trên thiết bị. Đây là phòng/giá demo, không phải tài sản cho thuê thật.

## HTTP và dữ liệu

| Endpoint | Quyền và chức năng |
| --- | --- |
| `GET /health` | Kiểm tra tiến trình HTTP; không khẳng định provider hoặc Firestore đang khỏe |
| `POST /api/bookings/{id}/payment` | Bearer Firebase ID token, Guest của booking; tạo hoặc lấy lại cùng mã QR |
| `GET /api/bookings/{id}/payment` | Thành viên booking/Admin ACTIVE; đối chiếu trạng thái trực tiếp payOS |
| `POST /api/payos/webhook` | SDK kiểm chữ ký, tìm order đã đăng ký, đối chiếu thêm qua API payOS rồi ghi transaction |
| `GET /payment/return`, `/payment/cancel` | Chỉ thông báo quay về app; không dùng redirect để xác nhận tiền hoặc hủy booking |

`payments/{bookingId}` lưu giao dịch hiện hành; `paymentOrders/{orderCode}` là tra cứu nội bộ backend; `auditLogs/payment_{orderCode}_paid` ghi nhận tiền một lần. Android chỉ đọc payment và booking qua Rules; không được tự ghi PAID, paidAmount hoặc xác nhận hoàn tiền.

Một booking dùng một orderCode bền vững. Booking mới hết hạn sau tối đa 5 phút; giao dịch cũ giữ hạn đã có. Khi create timeout, server kiểm tra lại cùng mã thay vì tạo đơn thu tiền khác. Khi payOS đã hủy/hết hạn QR, phiên bản này không tạo orderCode thứ hai cho cùng booking. Có thể đặt booking mới nếu còn lịch và đủ điều kiện; không tự hủy/đặt lại trong backend.

Backend đối soát phiên giữ chỗ ngay cả khi app bị đóng trước khi gọi tạo QR. Sau khi xác nhận link chưa trả đã kết thúc, transaction nhả đúng lịch giờ/tháng/quý, giảm bộ đếm một lần, giữ lịch sử `EXPIRED` và ghi audit. Không nhả mù khi API nhà cung cấp mất kết nối. Thời điểm ngân hàng nhận tiền được đối chiếu khi webhook chậm; tiền thiếu, dư, đến muộn hoặc booking đã hủy được ghi `REVIEW_REQUIRED`/`MANUAL_REVIEW`. Không tự lấy lại lịch đã nhả. Tiến trình local dừng thì công việc này chờ tới khi Java chạy lại.

Để đổi bản Java mà giữ nguyên URL tunnel: `powershell -NoProfile -File tools/payments/restart-server.ps1`. Script chỉ thay đúng tiến trình Java demo theo PID/thời điểm khởi tạo, build lại backend và kiểm tra health. Nếu chạy `run-demo.ps1` để tạo tunnel mới thì vẫn cần build lại APK theo URL mới.

Trạng thái tiền: `CREATING`, `PENDING`, `PAID`, `CANCELLED`, `EXPIRED`, `REVIEW_REQUIRED`. Booking giữ `UNAVAILABLE` trước khi dùng API, `PENDING` khi đã tạo yêu cầu, `PAID` khi payOS xác nhận nhận đủ, `REVIEW_REQUIRED` khi tiền đến cho booking đã hủy hoặc số nhận khác số yêu cầu. Webhook lặp và kết quả đối chiếu đến muộn không hạ trạng thái đã nhận tiền.

Khi Java đang chạy, tác vụ đối soát mỗi 15 giây kiểm tra tối đa 50 payment đang tạo/chờ theo cursor, hủy link chưa trả nếu booking đã khép, Host bị khóa hoặc QR quá hạn. Một lỗi provider không làm dừng các đơn khác; lần sau sẽ thử lại. Màn Android đang mở cũng cập nhật thời hạn và đối soát định kỳ, không tự xác nhận đã nhận tiền. Nếu thanh toán thắng race với hủy, server vẫn ghi nhận tiền để đối soát, không phục hồi booking/lịch đã nhả.

Webhook cập nhật khi server đang hoạt động; nút kiểm tra giao dịch và quay về màn thanh toán đối chiếu trực tiếp để bù webhook bị lỡ. Sau khi server dừng, tác vụ đối soát cũng dừng và payOS không thể gọi vào máy; cần bật server/tunnel khi demo. Hủy link chưa thanh toán không phải thao tác hoàn tiền.

## Kiểm thử

Từ root: `gradlew.bat -p backend/payments test`. Muốn chạy cả transaction integration test phải đặt `FIRESTORE_EMULATOR_HOST=127.0.0.1:8085`; dùng database emulator riêng `payment_backend_qa`. Nếu không có biến này, integration suite được đánh dấu skipped, không coi là đã nghiệm thu đủ.

`tools/testing/payment_rules_acceptance.py` chạy kiểm thử Rules độc lập và hồi quy booking trên emulator. `gradlew.bat -p backend/payments liveProviderCheck` là lệnh bật chủ động: tạo link QR thật 2.000đ rồi hủy, không chuyển tiền, không thay webhook, không ghi Firestore cloud.

Tài liệu đối chiếu: [payOS Java SDK](https://payos.vn/docs/sdks/back-end/java/), [Firebase token verification](https://firebase.google.com/docs/auth/admin/verify-id-tokens), [Firestore named database client](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/cloud/FirestoreClient).
