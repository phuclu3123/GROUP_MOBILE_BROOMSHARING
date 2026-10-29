# Đối chiếu giai đoạn 3 và 4 với bản tích hợp hiện tại

> Bảng dưới là snapshot trước đợt hoàn thiện nghiệp vụ ngày 24/10. Các khoảng trống booking, review, báo cáo và thu hồi phiên đã có cập nhật trong [mốc nghiệm thu mới](business-completion-2026-10-24.md). Không dùng số test hoặc trạng thái thiếu trong snapshot này để kết luận về bản mới; thanh toán, ảnh và nghiệm thu phát hành vẫn còn bước riêng.

Ngày kiểm tra: 24/10/2026. Workbook gốc: `BroomSharing_PhanCong_4Dot_Android_Java_Firebase.xlsx`. Nhánh local `phuc`, HEAD `9a2c214`, có thay đổi chưa commit; đánh giá dưới đây dùng nội dung working tree hiện tại. Không sửa workbook, mã ứng dụng, Rules hoặc dữ liệu cloud trong lượt rà soát.

Sheet là dàn ý, không phải đặc tả bắt buộc về công nghệ hoặc căn cứ tính phần trăm hoàn tất. Đã đọc chín sheet; đối chiếu chi tiết 25 đầu việc đợt 3 và 22 đầu việc đợt 4 của ba thành viên. Thay đổi đã thống nhất trước đây và yêu cầu mới của chủ dự án được ưu tiên hơn sheet: thanh toán và lưu ảnh nay là hai phần chắc chắn tiếp tục hoàn thiện.

Chủ dự án xác nhận trong lượt này: hạn hoàn thành app chuyển sang **27/10/2026**, thay cho mốc 25/10 trong workbook. Phạm vi nghiệp vụ thanh toán cần bàn lại ở lượt tiếp theo; chưa chọn sandbox hay thu/hoàn tiền thật và chưa quyết định provider. Không tự diễn giải hạn này thành phê duyệt một nhà cung cấp hoặc backend cụ thể.

## Bằng chứng và giới hạn

- Chạy lại `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest`: Gradle ghi `BUILD SUCCESSFUL`. XML hiện tại có 8 bộ, 56 unit test, 0 lỗi, 0 thất bại, 0 bỏ qua. Lint có 0 lỗi và 493 cảnh báo. APK debug khoảng 92,2 MB; đây không phải kích thước của một bản release đã tối ưu.
- Log lượt này: `tmp/phase-review/current-build.log`; XML test/lint trong `app/build`. Không chạy instrumented test trên thiết bị hoặc tác vụ tạo/đổi dữ liệu cloud.
- Bằng chứng chạy thiết bị/cloud trước đó nằm trong [nghiệm thu](verification.md), [giai đoạn 3 của Phúc](../../data/demo/phuc-phase3-verification.json). Các bằng chứng này có ngày 20–21/10, không phải kiểm thử lại toàn bộ vào ngày 24/10.
- SHA-256 của `firestore.rules` hiện tại khớp hash đã ghi trong nghiệm thu giai đoạn 3. Không đọc lại Rules đang triển khai trên cloud trong lượt này nên không xác nhận trạng thái cloud hiện nay.
- Đã có test nguồn cho tranh slot, retry/idempotency, thuê quý, hủy, chat và quyền. Có mã test không đồng nghĩa tất cả tình huống này vừa được chạy lại.

## Các điểm phải quyết định hoặc xử lý trước khi khép app

1. **Thanh toán phải nối với toàn bộ vòng đời booking.** Hiện booking mới là `UNAVAILABLE`; hủy ghi `refundAmount=0`, `NOT_REQUIRED`; nút thanh toán chỉ giải thích chưa tích hợp. Cần chốt provider và phạm vi sandbox/thu tiền, bên tạo lệnh và xác nhận kết quả đáng tin cậy, khóa chống xử lý lặp, luồng hủy/hết hạn và đối soát/hoàn tiền. Không dùng callback từ màn hình hoặc việc quay lại app làm bằng chứng đã trả tiền. Đây là đầu việc mới được yêu cầu tiếp tục, không còn coi là phần hoãn để chốt app.
2. **Tách ảnh công khai và bằng chứng riêng tư.** MediaRepository và form Host đã có chọn/upload ảnh phòng/tòa nhà, kiểm MIME/kích thước và gọi xóa ảnh cũ. Chưa có bằng chứng nghiệm thu bucket thật xuyên suốt. Rules tệp bằng chứng hiện chỉ cho chủ tệp đọc, không cho Admin; chưa thấy luồng Host upload và tạo `propertyVerifications` trong bản tích hợp. Cần khép Host gửi hồ sơ → lưu tệp riêng tư → Admin mở đủ bằng chứng → quyết định → Host nhận kết quả. Không giải quyết bằng cách công khai tệp giấy tờ.
3. **Booking PENDING quá giờ bắt đầu chưa có đường kết thúc.** `BookingPolicy.transition` chỉ cho confirm/reject/cancel trước giờ bắt đầu; check-in cần CONFIRMED; completed chỉ nhận CONFIRMED/CHECKED_IN. Rules có cùng giới hạn. Vì vậy PENDING đã qua giờ không có chuyển trạng thái hợp lệ hiện có. Đây là kết luận từ điều kiện code, chưa tái hiện bằng thao tác cloud trong lượt này. Cần quyết định thời hạn chờ duyệt/thanh toán và cách hết hạn, nhả đúng khóa, lưu lịch sử; trường hợp thuê quý có thể liên quan những tháng vẫn ở tương lai.
4. **Host bị khóa nhưng có booking đang hiệu lực.** Báo cáo có thể khóa Host/PublicHost và giữ lịch sử. Chuyển trạng thái booking hiện yêu cầu Guest/Host đang ACTIVE; không có đường Admin xử lý booking thay Host trong Repository/Rules. Không kết luận mọi booking đều kẹt: Guest vẫn có thể hủy trước giờ theo quyền hiện có. Phần thiếu là cách xử lý những đơn còn lại, đặc biệt sau giờ bắt đầu, có thanh toán hoặc tranh chấp.
5. **Không coi kiểm tra Java là hàng rào an toàn duy nhất.** Java kiểm `blackoutDates`, `blackoutRanges` và chặn sửa phòng ảnh hưởng giữ chỗ. `validPeriod` trong Rules chưa kiểm hai trường blackout; nhánh Host sửa phòng chưa thực hiện cùng kiểm tra khóa tương lai như `validateRoomChange`. Cần test ghi trực tiếp vượt Repository trên emulator, rồi chọn cách bảo vệ tương ứng. Chưa thực hiện khai thác bypass cloud trong lượt này. Storage Rules cũng chỉ kiểm UID đối với upload/xóa, chưa ràng buộc vai trò/trạng thái khóa tài khoản.
6. **Báo cáo còn thiếu phía Host.** Guest gửi báo cáo và Admin xử lý/ẩn phòng/khóa Host/audit đã có. Host được Rules cho đọc và sửa `hostResponse`, nhưng bản tích hợp chưa có màn hình/Repository cho phản hồi và gửi chứng cứ. Cần kiểm tra nhánh thành viên trước khi code mới; có thể đây là việc ghép thay vì viết lại. Cơ chế tranh chấp và can thiệp booking chưa khép kín.
7. **Thông báo khi app đóng là một quyết định sản phẩm còn mở.** Hiện có hộp cập nhật, receipt đã đọc và badge; Android notification cục bộ theo dữ liệu app đang theo dõi. Không có FCM, token thiết bị hoặc bộ gửi push tin cậy. Nội dung hộp cập nhật là trạng thái booking và tin nhắn cuối, không phải lịch sử mọi event; booking kết thúc quá bảy ngày bị loại khỏi phần tổng hợp này. Nếu kỳ vọng Host nhận đơn hoặc tin nhắn khi không mở app thì cần bổ sung push và kiểm tra Android 13+, đăng xuất/chuyển tài khoản, nhiều thiết bị và quyền bị từ chối.

## Đợt 3: đối chiếu đủ các mã công việc

“Có” dưới đây nghĩa có luồng tương ứng trong code; bằng chứng chạy trước đây và phạm vi kiểm thử mới được phân biệt ở đầu tài liệu.

| ID | Hiện trạng trong bản tích hợp | Việc còn lại / quyết định |
| --- | --- | --- |
| U18 | Có bản đồ/marker/chi tiết; dùng MapLibre | Không đổi lại Google Maps chỉ để khớp sheet |
| U19 | Có LocationManager, quyền và chọn vị trí thủ công | Thử GPS ngoài thực địa, vị trí cũ/sai và từ chối quyền |
| U20 | Có Directions, tuyến, khoảng cách/thời gian, fallback | Thử mất mạng/quota trên bản cuối |
| U21 | Có POI MapTiler, lọc nhóm/bán kính/cache | Tập trả về giới hạn, không cam kết danh bạ đầy đủ |
| U22 | Có inbox/chat text/read counter, tải thêm lịch sử | Nghiệm thu Guest–Host hai máy sau ghép |
| U23 | Có hộp cập nhật và đã đọc | Chốt có cần event history/push khi app đóng |
| U24 | Có lịch đặt trong app; không thấy Calendar Intent | Có thể giữ lịch trong app theo lựa chọn sheet |
| U25 | SharedPreferences dùng cho thông báo; chưa có Settings tổng quát | Sort/theme/map-list preferences là tùy chọn |
| H18 | Host có thể mở và trả lời chat theo booking | Không viết thêm hệ chat thứ hai; nghiệm thu hai vai trò |
| H19 | Hộp cập nhật có nhánh booking của Host | Không có push nền và luồng cảnh báo tranh chấp đầy đủ |
| H20 | Có xem lịch theo ngày, lọc phòng/ngày | Lưới tuần/tháng nâng cao có thể để sau |
| H21 | Có xác nhận CHECKED_IN thủ công và kiểm thời gian | Chưa có PIN/QR; không cần thêm mã giả chỉ để khớp sheet |
| H22 | Có tiếp nhận đơn Host bằng thông tin văn bản; có phía Admin đọc hồ sơ xác minh | Chưa có luồng tải bằng chứng tài sản riêng tư end-to-end |
| H23 | Có hiển thị trả trước dự kiến, chưa thu tiền | Hoàn thiện cùng payment, không làm biên nhận giả |
| H24 | Host có hủy với lý do và nhả lịch | Chưa có yêu cầu đổi lịch/đối soát; cần chốt phạm vi |
| H25 | Rules có contract Host đọc/phản hồi báo cáo | Chưa có UI/Repository phản hồi trong bản tích hợp |
| P22 | Định vị foreground có kiểm tra/fallback | Không bắt buộc đổi sang Fused |
| P23 | Routes qua openrouteservice, có timeout/cache | Nghiệm thu provider trên bản cuối |
| P24 | POI MapTiler hoạt động theo phạm vi giới hạn | Không coi thay provider so với sheet là thiếu tính năng |
| P25 | Key lấy qua cấu hình build; không đưa admin credential vào app | Key client vẫn trích xuất được; kiểm quyền/quota provider và secret payment phía tin cậy |
| P26 | Chat membership, timestamp, counter và Rules đã có | Thử lại đọc/ghi chéo tài khoản và gửi lặp |
| P27 | Hộp cập nhật tổng hợp và receipt đã có | Không đồng nhất với collection notifications/event log/push |
| P28 | Chưa có tích hợp payment | Phải làm tiếp theo yêu cầu hiện tại |
| P29 | Guest report → Admin resolve/moderate/audit có code và bằng chứng cũ | Bằng chứng tệp, phản hồi Host và thông báo xử lý còn thiếu |
| P30 | Có chờ server ack, xử lý cache/lifecycle và lỗi | Chạy lại mất mạng, reconnect và process death trên bản cuối |

## Đợt 4: cái gì đã có, cái gì thực sự phải làm tiếp

| ID | Hiện trạng | Hướng xử lý |
| --- | --- | --- |
| U26 | Hủy trước giờ theo policy v1, transaction nhả lịch đã có | Nối payment thật/sandbox và các trạng thái đối soát mới |
| U27 | Report có; chưa có đánh giá sau thuê | Review/rating là phần có thể hoãn, không bịa collection từ ReviewItem |
| U28 | UI dùng tài nguyên và điều hướng chung | Rà màn nhỏ, bàn phím, font scale; không ưu tiên dark mode trước nghiệp vụ |
| U29 | Có loading/error/retry, không giả thành công booking | Nghiệm thu ngắt mạng và server từ chối xuyên ba vai trò |
| U30 | Glide/placeholder đã có; upload kiểm kích thước nhưng chưa resize ảnh | Thử ảnh lớn, ảnh hỏng, cuộn và bộ nhớ; resize theo nhu cầu |
| U31 | Báo ảnh không khớp bằng mô tả đã có | Đính kèm ảnh minh chứng làm cùng phần tệp riêng tư |
| U32 | Guest flow dùng ID/Repository thật | Chạy trọn Home → Detail → Book → MyBooking → Chat với bản tích hợp cuối |
| H26 | Rules chặn tài khoản bị khóa, Repository kiểm ACTIVE | Hoàn thiện hiển thị lý do/hỗ trợ và xử lý booking cũ khi Host không còn thao tác được |
| H27 | Room/booking dùng dữ liệu chung và kiểm chủ sở hữu | Danh sách Host còn lấy toàn bộ; rà phân trang và refresh ở quy mô mục tiêu |
| H28 | Đếm tổng phòng/phòng hoạt động/phòng chờ/đơn chờ đã có | KPI nâng cao theo kỳ/giờ thuê là tùy chọn; chưa gọi tiền dự kiến là doanh thu |
| H29 | Có gọi xóa ảnh cũ/rollback khi upload hoặc lưu lỗi | Chưa chờ/xử lý đủ kết quả xóa; cần nghiệm thu tệp mồ côi/thay ảnh/quyền |
| H30 | Form validate/loading/error đã có | Rà UI thực tế và thiết bị nhỏ, không coi có code là UX đã đạt |
| H31 | Có các mảnh Host apply → Admin → building/room → booking | Nghiệm thu nguyên chuỗi bằng dữ liệu mới, gồm cả từ chối/gửi lại |
| P31 | Admin có trang tổng quan/lối tắt và số lượng hàng chờ từng nhóm | Chưa có bộ KPI tổng hợp đúng mô tả sheet; có thể để dashboard đơn giản |
| P32 | Có moderation phòng/Host và audit | Chưa có luồng tranh chấp gắn booking/tiền và quyền xử lý thay; phải chốt tối thiểu |
| P33 | Transaction/idempotency/race có code và test nguồn | Bổ sung pending hết hạn, khóa Host, đổi phòng/lịch cùng lúc, payment callback lặp |
| P34 | Có Rules và một số test âm | Chạy ma trận bypass trực tiếp, blackout, đổi mode và Storage; chưa đủ kết luận production |
| P35 | Có thông báo lỗi và mở Maps dự phòng | Kiểm không key/quota/GPS/app Maps, không bịa ETA/POI |
| P36 | Working tree build được debug và APK kiểm thử | Chốt nhánh tích hợp, build release ký, cài mới/nâng cấp, test ba vai trò |
| P37 | README/docs có, nhưng có lịch sử giới hạn và kiến trúc cũ | Cập nhật sau khi chốt payment/storage, bộ demo, setup máy mới và hạn chế thật |
| P38 | Chưa có đối tác xác minh thực địa | Ngoài phạm vi nếu chưa có đối tác; giữ nhãn kiểm tra nội bộ trung thực |
| P39 | Chưa có thanh toán đối tác | Yêu cầu mới nâng payment thành phần phải tiếp tục; chưa tự chốt provider/sandbox/thu tiền |

## Thứ tự hoàn thiện đề xuất

1. Chốt hành vi cho pending hết hạn, thanh toán thất bại, hủy sau thu tiền, Host bị khóa và tranh chấp. Chọn dịch vụ payment/lưu tệp và phạm vi vận hành trước khi viết tích hợp.
2. Hoàn thiện phần bảo vệ dữ liệu và máy trạng thái tối thiểu; viết test cho các nhánh chốt ở bước 1, kiểm trực tiếp ngoài Repository.
3. Khép ảnh phòng/tòa nhà và hồ sơ bằng chứng, gồm quyền Admin, thay/xóa, lỗi giữa chừng và kết quả gửi lại.
4. Tích hợp payment qua luồng đáng tin cậy, đồng bộ trạng thái booking, lỗi mạng, callback trùng và đối soát/hoàn tiền đúng phạm vi. Backend/proxy nếu cần phải được thống nhất, không tự thêm dịch vụ trong lượt rà soát này.
5. Khép Guest report → Host phản hồi → Admin kết luận và quyết định có push booking/chat. Bổ sung chỉnh tên/số liên hệ tài khoản là cải thiện thực tế nên cân nhắc: trang hồ sơ hiện chỉ hiển thị thông tin và các lối tắt.
6. Nghiệm thu bản tích hợp cuối trên ít nhất hai tài khoản/thiết bị; chạy race, mất mạng, quyền bị từ chối, recreate/process death, Android 13+ notification, GPS ngoài thực địa. Rà đọc dữ liệu lớn ở Host/Admin/map; map hiện tự tải lần lượt hết các trang phòng rồi lọc khoảng cách phía app.
7. Chốt bản release ký và cấu hình máy mới; loại dữ liệu thử, kiểm backup/cache/thông tin nhạy cảm và cập nhật tài liệu. `release` hiện chưa có cấu hình ký trong Gradle; không coi debug APK là bản phát hành cuối. Nếu phát hành lên cửa hàng cần một lượt kiểm tra yêu cầu phát hành hiện hành riêng.

Có thể ghi nhận ngoài phạm vi: rating/review, lịch lưới tuần, Calendar Intent khi đã có lịch trong app, dark mode/theme, dashboard nâng cao, QR/PIN ra vào, đổi lịch có chênh lệch tiền và xác minh qua đối tác. Việc hoãn push chỉ hợp lý khi nhóm chấp nhận thông báo trong app; việc hoãn luồng tranh chấp phải đi cùng kênh xử lý thủ công cụ thể. Không dùng danh sách hoãn này để bỏ qua kiểm tra quyền, booking hết hạn hoặc trạng thái tiền.

## Tổ chức đến mốc 27/10

- **24–25/10:** chốt phạm vi payment/lưu tệp và những nhánh nghiệp vụ còn mở; ưu tiên pending quá hạn, booking của Host bị khóa, Rules và quyền tệp. Khép luồng upload/xem bằng chứng và xác định phần Host cần ghép. Các thành viên có thể nhận các phần độc lập theo sở hữu module.
- **25–26/10:** tích hợp dịch vụ đã thống nhất, khép trạng thái qua Guest–Host–Admin; xử lý báo cáo/phản hồi; thử các nhánh thành công, từ chối, lỗi mạng và yêu cầu lặp. Không dành hết ngày cuối cho chức năng mới.
- **27/10:** ưu tiên kiểm thử hai máy/tài khoản, kiểm tra dữ liệu và cấu hình cloud tương ứng bản chốt, build release ký và cài lại từ đầu; chỉ nhận sửa lỗi chặn luồng chính, cập nhật tài liệu đúng phạm vi đã chạy.

Đây là thứ tự ưu tiên để lập kế hoạch, không phải ước lượng đã chứng minh đủ giờ triển khai. Phụ thuộc lớn nhất là provider/tài khoản/quyền của payment và lưu tệp chưa chốt; chưa đủ căn cứ cam kết thu/hoàn tiền thật an toàn vào ngày 27/10. Ở lượt chốt scope cần xác định thời điểm nào giữ chỗ, hạn thanh toán/chờ Host, bên xác nhận tiền, hủy/hoàn, và cách xử lý khi Host bị khóa hoặc phát sinh tranh chấp.

## Nguồn code chính để kiểm lại

- [BookingPolicy](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/utils/BookingPolicy.java:200), [BookingRepository](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository/BookingRepository.java:237), [Firestore Rules](../../firestore.rules:46).
- [HostRepository](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository/HostRepository.java:253), [HostViewModel](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/ui/host/HostViewModel.java:129), [MediaRepository](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository/MediaRepository.java:27), [Storage Rules](../../storage.rules:28).
- [ReportRepository](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository/ReportRepository.java:15), [AdminRepository](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository/AdminRepository.java:606), [AdminViewModel](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/ui/admin/AdminViewModel.java:20).
- [ChatRepository](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository/ChatRepository.java:18), [BookingNoticeViewModel](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/ui/booking/BookingNoticeViewModel.java:170), [BookingNotifier](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/ui/common/BookingNotifier.java:18).
- [MapRepository](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository/MapRepository.java:29), [NearbyPlacesRepository](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository/NearbyPlacesRepository.java:16), [MapViewModel](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/ui/map/MapViewModel.java:212).
- [ProfileActivity](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/ui/profile/ProfileActivity.java:31), [Manifest](../../app/src/main/AndroidManifest.xml:8), [cấu hình build](../../app/build.gradle.kts:26), [CloudBookingAcceptanceTest](../../app/src/androidTest/java/vn/edu/ueh/phuclu/broomsharing/CloudBookingAcceptanceTest.java:151).
