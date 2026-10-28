# Kiểm định trước khi nộp BroomSharing, ngày 27/10/2026

## Kết luận nghiệm thu

**Nghiệp vụ cốt lõi đã có bằng chứng kiểm thử tốt, nhưng chưa nên chốt bản hiện tại là sẵn sàng sử dụng thực tế.** Hai nhóm cần sửa trước là kiểm tra dữ liệu hồ sơ tại Security Rules và đồng bộ vòng đời QR thanh toán. Buổi demo còn phụ thuộc backend Java/tunnel trên máy; tại thời điểm kiểm tra, cả hai địa chỉ health đều không truy cập được.

Trong phạm vi đồ án, chưa thấy cần bổ sung một phân hệ lớn nữa. Các chức năng tìm phòng, đặt chỗ độc quyền, quản lý lịch, xét duyệt, chat, đánh giá, báo cáo và xử lý booking bất thường đã hiện diện. Việc còn lại chủ yếu là khép các nhánh lỗi, ổn định bản cài và nghiệm thu trên điện thoại thật. Kết luận này giới hạn trong các phép kiểm đã thực hiện bên dưới, không phải chứng nhận mọi trạng thái đều không còn lỗi.

## Phạm vi và tính nguyên vẹn

- Mã nguồn kiểm tra: commit `2a20fe89078f8e703fd08a75a309245119127023`.
- Android: Java/XML, MVVM, Repository, Model; emulator Pixel 4, Android 10/API 29; cỡ chữ hệ thống 100% và 130%.
- Backend: kiểm thử lại Java payment service, bao gồm transaction Firestore emulator và provider giả lập. Không thực hiện chuyển khoản ngân hàng trong đợt này.
- Cloud: đọc dữ liệu tại project `broom-sharing-firebase-mobile`, database `broomsharing`; đối chiếu Rules đang triển khai và trạng thái index. Không triển khai lại Rules, không sửa dữ liệu nghiệp vụ, không thay webhook hay cấu hình payment.
- Lượt kiểm thử Repository Android dùng Firestore emulator, có tạo bốn tài khoản Firebase Auth tạm và đã xóa cả bốn. Các lượt kiểm tra giao diện cloud dùng tài khoản demo sẵn có. Không tạo booking, đơn Host hoặc ảnh mới trên cloud.
- Không sửa mã ứng dụng trong đợt review. Chỉ bổ sung công cụ đọc/kiểm thử và biên bản. Hai file `.idea/gradle.xml`, `.idea/misc.xml` đã có thay đổi từ trước, được giữ nguyên.

Bằng chứng tập trung tại [thư mục nghiệm thu](../../output/verification/pre-submission-2026-10-27). Các JSON và log trong thư mục này là kết quả của đợt kiểm tra hiện tại; không dùng tên thư mục nghiệm thu cũ để suy ra ngày chạy.

## Các phát hiện cần xử lý

### QA-01. Rules chấp nhận tên và điện thoại sai kiểu dữ liệu

**Ưu tiên P1. Đã tái hiện trên emulator với đúng nội dung Rules đang triển khai.**

Tại `firestore.rules:139`, cập nhật hồ sơ cá nhân có kiểm tra danh sách trường được thay đổi, ảnh đại diện và `phoneVerified`, nhưng thiếu kiểm tra kiểu/giá trị cho `name`, `phone`. Quy tắc `publicHosts` đối chiếu tên với `users`, song không bắt buộc tên là chuỗi.

Probe qua REST trực tiếp đã chấp nhận cả bốn trường hợp sau với HTTP 200:

| Thao tác bằng tài khoản có quyền sở hữu | Kết quả mong muốn | Thực tế |
| --- | --- | --- |
| Ghi `users.name` thành map | Từ chối | Chấp nhận |
| Ghi `users.phone` thành số | Từ chối | Chấp nhận |
| Ghi điện thoại là `not-a-phone` | Từ chối | Chấp nhận |
| Host ghi đồng thời tên dạng map vào `users` và `publicHosts` | Từ chối | Chấp nhận |

Đây không phải khả năng sửa hồ sơ của người khác. Rủi ro là chủ tài khoản có thể làm hỏng cấu trúc hồ sơ của mình và bản chiếu Host công khai bằng request ngoài giao diện. `UserProfile` và `PublicHost` khai báo tên/điện thoại dạng `String`. `RoomRepository.java:178` giải mã toàn bộ trang trong một khối `try`; lỗi ở một Host có thể đưa cả lượt tải trang vào `onError`. `SessionGuardRepository.java:30` cũng giải mã trực tiếp hồ sơ tài khoản.

Việc Rules chấp nhận dữ liệu đã được tái hiện. Hậu quả giải mã trên là phân tích từ mã, chưa chủ động ghi dữ liệu hỏng lên cloud để gây lỗi cho người dùng.

Cần kiểm tra kiểu, độ dài và định dạng hồ sơ tại Rules, áp dụng tương ứng khi tạo và cập nhật; bảo vệ bản chiếu `publicHosts`; bổ sung test âm và xử lý document lỗi theo từng bản ghi khi tải phòng. Không nên chỉ sửa validation ở form.

Bằng chứng: [malformed-profile-reproduction.json](../../output/verification/pre-submission-2026-10-27/malformed-profile-reproduction.json), [Rules đang triển khai](../../output/verification/pre-submission-2026-10-27/deployed-rules-indexes.json).

### QA-02. Màn hình QR chưa cập nhật đầy đủ trạng thái kết thúc

**Ưu tiên P1. Phát hiện từ mã; chưa chạy một giao dịch ngân hàng thật để tái hiện trên màn hình trong đợt này.**

`PaymentViewModel.java:31` chỉ đưa sự kiện listener lên UI khi `received()` là true, tức `PAID` hoặc `REVIEW_REQUIRED`. Sự kiện `CANCELLED` và `EXPIRED` không được đưa lên UI qua nhánh này. `BookingPaymentActivity.java:65` hiển thị QR dựa trên trạng thái `PENDING`, không kiểm tra `expiresAt` và không có bộ đếm hết hạn. Màn hình chỉ gọi đọc lại khi người dùng bấm tải lại hoặc quay trở lại ứng dụng.

Vì vậy, khi giữ nguyên màn hình chờ tới quá hạn, UI có thể vẫn hiện QR và dòng “Chờ chuyển khoản” tới lần refresh. Đó là sự không nhất quán giao diện, không đồng nghĩa payOS vẫn chấp nhận link hết hạn: code tạo link đã truyền `expiredAt` cho provider.

Cần phản ánh tất cả trạng thái payment, xử lý hạn bằng đồng hồ kết hợp đọc lại server, ngừng hiển thị QR khi không còn hiệu lực và kiểm tra booking liên quan. Nghiệm thu thêm các ca: đang mở QR thì hết hạn, thiết bị khác hủy booking, mất mạng rồi kết nối lại, xoay màn hình, webhook đến cùng lúc refresh. Không suy ra đã thanh toán chỉ từ việc mở ứng dụng ngân hàng.

### QA-03. Hủy booking chưa hủy link thanh toán đang chờ tại provider

**Ưu tiên P1 nếu dùng tiền thật; cần khép trước khi demo nhánh hủy có QR. Phát hiện từ mã.**

`PaymentGateway` hiện có tạo, đọc và xác minh webhook; chưa có thao tác hủy link. Hủy booking trong `BookingRepository` không gọi provider. Một link đã tạo có thể tiếp tục còn hiệu lực tới hạn ban đầu dù booking đã hủy. Nếu tiền đến sau khi booking kết thúc, backend có nhánh ghi nhận để đối soát, không phục hồi booking/lịch; đây là xử lý dự phòng tốt nhưng chưa thay thế việc hủy link chưa trả.

Cần phân biệt hủy link chưa thanh toán với hoàn tiền đã nhận. Có thể bổ sung hủy link mà vẫn giữ giới hạn “chưa tự động hoàn tiền”. payOS có endpoint hủy link `POST /v2/payment-requests/{id}/cancel` trong [tài liệu API chính thức](https://payos.vn/docs/api/). Cần để backend thực hiện và kiểm tra lại trạng thái provider khi có race với webhook; không để Android tự xác nhận tiền.

### QA-04. Payment chưa sẵn sàng cho buổi demo tại thời điểm kiểm tra

**Điều kiện vận hành cần hoàn tất trước demo, không kết luận là lỗi nghiệp vụ.**

Lúc 12:06 ngày 27/10/2026, `GET /health` tới Java local cổng 8090 và origin đang cấu hình trong APK đều không thành công (`URLError`). Review không tự khởi động lại dịch vụ, thay URL hoặc tạo giao dịch để giữ nguyên cấu hình đang dùng.

Trước demo cần chạy `tools/payments/run-demo.ps1`, xác nhận health và relay. Nếu tunnel đổi URL, phải đăng ký lại bằng `tools/payments/connect-relay.ps1` và build APK theo cấu hình mới. Máy chạy Java và tunnel phải duy trì kết nối. Không cần đổi sang mô hình hosting phức tạp chỉ để đáp ứng phạm vi đồ án, nhưng cần thử đúng quy trình khởi động lại máy trước buổi nộp.

Bằng chứng: [payment-service-readiness.json](../../output/verification/pre-submission-2026-10-27/payment-service-readiness.json). Việc unit/integration test backend đạt không chứng minh endpoint demo hiện đang hoạt động.

### QA-05. Dữ liệu demo còn các trạng thái cần rà soát

**Ưu tiên P2 trước quay video; đã quan sát trên cloud bằng lượt đọc, chưa tự sửa.**

| Quan sát | Diễn giải và xử lý phù hợp |
| --- | --- |
| 39 phòng được đối soát; không có phòng cần sửa counter/lịch | Chưa phát hiện lệch số booking giữ chỗ, slot hay khóa tháng trong phép đối soát hiện tại. |
| 6 phòng còn `APPROVED + ACTIVE` nhưng Host bị khóa/bản chiếu không hoạt động | Đây là cùng sáu phòng, không phải mười hai lỗi riêng. Repository lọc Host không hoạt động và Rules chặn tạo booking mới; chưa có bằng chứng khách đặt được các phòng này. Cần làm rõ trạng thái quản trị/hiển thị khi khóa và mở khóa Host. |
| 1 booking `PENDING` đã qua giờ bắt đầu còn lưu | Hết hạn đang được xử lý khi đồng bộ app, không có scheduler server chạy liên tục. Cần mở luồng đồng bộ hoặc Admin kiểm tra rồi xác nhận được nhả lịch. |
| 3 booking `CONFIRMED`/`CHECKED_IN` đã qua giờ kết thúc | Không tự coi là dữ liệu sai: chính sách không tự xác nhận khách đã sử dụng. Host/Admin cần khép từng trường hợp hoặc ghi nhận không đến. |
| 1 đánh giá thiếu `usageConfirmedAt` ở booking liên quan | Đánh giá tạo ngày 24/10, trước chính sách xác nhận sử dụng bổ sung ngày 26/10. Cần rà lại dữ liệu cũ. Rules/test mới đã kiểm tra việc thiếu bằng chứng sử dụng; không kết luận luồng mới đang cho khách không đến viết đánh giá. |

Đã đọc 19 users, 7 publicHosts, 17 buildings, 39 rooms, 72 bookings, 30 availabilityMonths, 1 review, 8 reports, 10 propertyVerifications, 7 hostApplications và 1 payment. Đây là phạm vi snapshot đọc được, không phải kiểm kê mọi document/subcollection trên cloud.

Chi tiết ID cần đối chiếu nằm trong [cloud-data-read-audit.json](../../output/verification/pre-submission-2026-10-27/cloud-data-read-audit.json). Không nên đổi trạng thái hàng loạt chỉ để số liệu đẹp trước demo.

### QA-06. Bản phát hành và UI cần một lượt chốt riêng

**Ưu tiên P2 trước nộp.**

Build debug, test APK và release đều thành công. File release hiện là `app-release-unsigned.apk`, chưa phải bản APK đã ký để giao người khác cài đặt. Cần ký bản cuối và thử cài mới/cập nhật trên điện thoại thật, dùng đúng endpoint payment, kiểm tra quyền vị trí/ảnh/thông báo trên phiên bản Android của máy demo.

Ở cỡ chữ 130%, các màn hình đã chụp vẫn sử dụng được, nhưng giá trên thẻ phòng bị xuống dòng nhiều; nhóm nút avatar sát nhau và nhãn bị chia dòng; màn tài khoản dài hơn đáng kể. Đây là vấn đề bố cục/khả năng đọc cần chỉnh, chưa phải bằng chứng mất dữ liệu. Một số chữ phụ và nút viền ở cỡ mặc định cũng nên được kiểm tra tương phản bằng công cụ đo trước khi tuyên bố đạt accessibility.

Lint có 0 lỗi và 606 cảnh báo, chủ yếu chuỗi hardcode, nối chuỗi khi hiển thị, tài nguyên thừa, overdraw, autofill và cập nhật danh sách. Không phải 606 lỗi nghiệp vụ và cũng không cần sửa toàn bộ trước nộp. Ưu tiên nội dung bị cắt/chồng, trạng thái lỗi/tải lại, target chạm và các màn hình demo thường dùng.

Ảnh: [Guest, chữ 130%](../../output/verification/pre-submission-2026-10-27/screens-large-font/role-guest-home.png), [Host, chữ 130%](../../output/verification/pre-submission-2026-10-27/screens-large-font/role-host-spaces.png), [Admin, chữ 130%](../../output/verification/pre-submission-2026-10-27/screens-large-font/role-admin-account.png).

## Kết quả thực thi kiểm thử

| Lớp kiểm định | Kết quả mới chạy | Giới hạn |
| --- | --- | --- |
| Android unit test, chạy lại task | 125/125 đạt | Không thay thế test thao tác trên máy. |
| Backend Java, chạy lại task | 38/38 đạt | Có Firestore emulator; provider giả lập, không chuyển tiền thật. |
| Rules: booking resolution | 44/44 đạt | Kiểm định quyền và transaction trên emulator. |
| Rules: payment | 26/26 đạt | Android không được tự ghi nhận tiền; không xác nhận webhook ngân hàng live. |
| Rules: avatar/đơn Host | 37/37 đạt | Kiểm tra contract URL và xét duyệt. |
| Rules: media minh chứng | 34/34 đạt | Không chứng minh CDN/preset hiện hoạt động với tệp mới. |
| Rules: booking boundaries | 15/15 đạt | Giá, lịch khóa, slot, transaction thiếu thành phần. |
| Tương thích counter phòng | 14/14 đạt | Có ca integration emulator. |
| Android form/contract/map priority | 27 đạt, 1 bỏ qua do opt-in | Test MediaRepository được chạy riêng sau đó và đạt. |
| MediaRepository trên Android | 1/1 đạt sau chỉnh fixture tài khoản | Auth thật, file/cache Android thật; upload/delete provider fixture, không upload cloud mới. |
| Workspace Guest/Host/Admin | 2/2 đạt | Có đăng nhập cloud và kiểm tra điều hướng, tải dữ liệu, đăng xuất. |
| Map experience | 1/1 đạt sau cấp tọa độ GPS emulator | Có geocoding/route API thực; GPS được mô phỏng, không phải tín hiệu điện thoại ngoài trời. |
| Repository nghiệp vụ Android | 1 kịch bản tích hợp đạt | Nhiều bước liên hoàn; không coi mỗi bước là một test độc lập. Firestore local, Auth tạm được xóa. |
| Build debug/test/release và lint | Thành công; 0 lint error | Release chưa ký; còn 606 warning. |
| Probe dữ liệu hồ sơ ngoài UI | 4/4 request sai được chấp nhận | Đây là bốn phát hiện QA-01, không tính là bốn test nghiệm thu đạt. |

Tổng 156 ca Rules dương/âm có sẵn đều đạt. Bốn probe bổ sung vẫn phát hiện khoảng trống; do đó không dùng con số test đạt để kết luận đã phủ hết quyền truy cập.

Kịch bản Repository Android đã đi qua tạo tòa nhà/phòng, xét duyệt, hai khách tranh lịch, chống sửa giá, retry, thuê giờ/tháng/quý, hủy/từ chối/hết hạn và nhả lịch, xác nhận sử dụng/hoàn tất, đánh giá, báo cáo/phản hồi/xử lý, chat sequence/read, hộp cập nhật, khóa Host và Admin can thiệp. Lưu lịch được kiểm tra qua intent Android, chưa xác nhận sự kiện thực sự được lưu vào tài khoản lịch bên ngoài.

Rules đang triển khai trùng SHA-256 với bản local kiểm thử: `75fffe0fe2cd2d213f8cc4e1fc9b46d3cff0c5706ebcdc1cf9d8158db56ce15f`. Cả 7 composite index đọc được đều `READY`. APK không chứa file `.env`/service-account và không tìm thấy các giá trị secret payment/Cloudinary đã cấu hình trong lượt quét giá trị biết trước. Phép quét này không phải đánh giá bảo mật APK toàn diện.

## Các lần chạy không đạt và cách phân loại

- Firestore emulator ban đầu chạy bằng Java không đủ phiên bản; đổi sang Android Studio JBR rồi chạy lại đạt. Đây là lỗi môi trường khởi chạy.
- Map experience ban đầu thiếu GPS emulator; sau cấp tọa độ phù hợp, cùng kịch bản đạt. Chưa dùng lượt này để khẳng định GPS phần cứng đã được kiểm thử.
- MediaRepository ban đầu được opt-out, sau đó lượt chạy riêng dùng nhầm alias `host` thay cho `host1`, khiến thông tin đăng nhập rỗng. Sửa mapping fixture, chạy lại đạt; không sửa app.
- Workspace ở chữ 130% có một lần lỗi assertion kiểm tra phiên ngay sau bấm đăng xuất. Cùng ca chạy riêng ở 100% và 130% đều đạt. Giữ là tín hiệu kiểm thử chưa ổn định, chưa đủ chứng cứ gán thành lỗi đăng xuất hoặc lỗi do font. Cần quan sát lại trên máy thật; không xóa log lần thất bại.

Các log gốc đã được lưu cùng kết quả chạy lại tại [logs](../../output/verification/pre-submission-2026-10-27/logs). Thống kê tại [automated-summary.json](../../output/verification/pre-submission-2026-10-27/automated-summary.json) và [rules-summary.json](../../output/verification/pre-submission-2026-10-27/rules-summary.json).

Ba công cụ bổ sung để tái kiểm tra là `tools/testing/pre_submission_read_audit.py` (đọc dữ liệu cloud bằng tài khoản Admin local), `tools/testing/pre_submission_cloud_rules.py` (đọc metadata Rules/index) và `tools/testing/pre_submission_adversarial_probe.py` (chỉ ghi fixture vào database emulator riêng trên cổng 8085). Hai công cụ cloud không có chế độ apply; probe cuối là công cụ tái hiện phát hiện, không phải runner tự đánh dấu mọi request HTTP 200 là nghiệm thu đạt. Sau kiểm thử đã gỡ test APK, dọn file mật khẩu fixture trên emulator và trả cỡ chữ về 100%; app chính vẫn là bản cấu hình cloud.

## Chức năng và chính sách còn giới hạn

1. **Quan hệ giữa trả trước và nhận phòng:** code chuyển `CONFIRMED` sang `CHECKED_IN` hiện chưa bắt buộc `paymentStatus == PAID`. Điều này tương thích cách trình diễn API thanh toán độc lập với trạng thái sử dụng đã chọn. Nếu muốn 40% là điều kiện bắt buộc để nhận phòng, cần chốt thêm chính sách với Host và xử lý các booking demo không dùng kênh payOS; không tự siết trong đợt review.
2. **Thu tiền:** chỉ một Host/kênh payOS demo; chưa có onboarding nhiều người nhận, thu 60% còn lại, tự động hoàn tiền hay trung gian giữ tiền. Đây là phạm vi đã giới hạn, không phải các nút chức năng đã triển khai đầy đủ.
3. **Ảnh và xác minh:** upload unsigned và ảnh giấy tờ DEMO theo URL công khai phù hợp trình diễn đã thống nhất; chưa đáp ứng tiếp nhận giấy tờ cá nhân thật. Xét duyệt Host là Admin duyệt hồ sơ, không phải eKYC tự động. Không dùng CCCD của người thật trên mạng để bổ sung dữ liệu nghiệm thu.
4. **Tác vụ nền:** hộp cập nhật dựa trên dữ liệu/lắng nghe trong app; chưa xác nhận push khi app đóng. Hết hạn booking không có scheduler server chạy liên tục. Không mô tả hai tính năng này như dịch vụ nền đã đảm bảo hoạt động 24/7.
5. **Độ bền và thiết bị:** chưa nghiệm thu tải lớn, nhiều thiết bị thật đồng thời, mất tiến trình ở mọi màn hình, chạy dài nhiều giờ, đầy bộ nhớ, nâng cấp dữ liệu qua nhiều phiên bản APK. Chưa kiểm thử đủ Android mới và quyền thông báo runtime.
6. **Dịch vụ thực:** chưa chạy mới chuỗi chọn ảnh/URL → Cloudinary → Firestore → thiết bị khác trong đợt này; chưa chạy mới chuyển khoản thật → WEB_TCC relay → Java → Firestore → Android. Các biên bản ngày 26/10 vẫn là bằng chứng lịch sử riêng, không cộng vào kết quả live của ngày 27/10.

## Thứ tự hoàn tất đề xuất

1. Sửa QA-01, kiểm thử âm ngoài Repository và chạy lại các suite Rules liên quan; chỉ triển khai cloud theo quy trình thay đổi có kiểm soát.
2. Khép QA-02/QA-03, kiểm tra hủy/hết hạn/race payment. Giữ cơ chế đối soát tiền đến muộn; chưa cần mở rộng sang hoàn tiền tự động.
3. Rà dữ liệu demo nêu tại QA-05 theo từng trường hợp; không tự hoàn tất lượt thuê hoặc cấp bằng chứng sử dụng giả.
4. Chỉnh các điểm UI ảnh hưởng đọc/chạm, ký APK cuối, nghiệm thu một Guest và một Host trên hai điện thoại; thử offline, đăng xuất/đăng nhập lại và cỡ chữ lớn.
5. Chạy backend/tunnel/relay và nghiệm thu một booking thanh toán demo đã được chủ tài khoản đồng ý; kiểm tra số tiền, người nhận, trạng thái realtime và audit. Chỉ khi có bằng chứng đó mới chốt phần payment live cho buổi nộp.

Sau các bước này, phạm vi đồ án có thể chốt mà không cần thêm ví điện tử, hợp đồng điện tử, chia tiền nhiều Host hoặc eKYC tự động. Những mở rộng đó không giải quyết các lỗi cụ thể đã phát hiện ở bản hiện tại.
