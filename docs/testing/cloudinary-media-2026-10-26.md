# Tích hợp Cloudinary trực tiếp cho đồ án

Ngày 26/10/2026. Phạm vi ứng dụng: Java + XML, MVVM + Repository + Model; không thêm backend Spring Boot. [Cấu hình và giới hạn](../integrations/cloudinary-setup.md).

## Phần mã đã nối

- `.env` và Gradle có `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_UPLOAD_PRESET`. Không đọc/nhúng Cloudinary API Secret. Thiếu cấu hình được báo khi upload, không làm hỏng build hay luồng giữ ảnh cũ.
- `MediaRepository` tải ảnh/PDF trên worker, kiểm nội dung và giới hạn 5 MiB, giữ thứ tự tối đa 6 tệp. Có kiểm tra lại phiên đăng nhập; dọn upload mới khi lỗi bằng delete token nếu provider có trả token.
- Ảnh phòng và bìa tòa nhà giữ các trường Firestore cũ, sử dụng URL Cloudinary cho file mới. Gỡ ảnh khỏi tin không đồng nghĩa đã xóa vật lý file trên provider; file không còn token phải dọn trong Console.
- Form hồ sơ có chọn ảnh/PDF, xem trước, draft qua recreate, trạng thái bận và retry URL đã upload để không tải lại nếu chỉ lỗi ghi document. Admin có nhánh đọc URL đúng cloud/Host/hồ sơ và giới hạn dung lượng, không theo redirect sang host khác. Các path Firebase Storage cũ vẫn được giữ.
- Thông tin khách hàng tiếp tục ở `users` và các document nghiệp vụ, không tạo database khách hàng trên Cloudinary.

## Kiểm tra đã thực hiện

| Nhóm | Kết quả và giới hạn chứng cứ |
| --- | --- |
| Unit test Java | 119 test đạt, gồm 18 test MediaPolicy mới; không bỏ qua test |
| CloudinaryContractTest, Android API 29 | 15 test đạt: cấu trúc unsigned request, parser, PDF raw, giới hạn URL/file, HTTP multipart thực qua socket loopback, HTTP 400/429 và không theo redirect |
| MediaRepositoryAcceptanceTest, Android API 29 | 1 test nhiều tình huống đạt: tài khoản demo Auth thật, ảnh cache thật, giữ thứ tự, lỗi giữa chừng/dọn upload, file hỏng/quá số lượng, thiếu cấu hình, đổi tài khoản. Provider được tiêm fixture, không upload Cloudinary thật |
| Lint | 0 lỗi, 586 cảnh báo của dự án tại lượt kiểm tra |
| Rules minh chứng Cloudinary | 34 ca đạt: URL đúng/sai UID và hồ sơ, loại IDENTITY, giới hạn tệp, ký tự đặc biệt, quyền đọc và tự xét duyệt |
| Hồi quy Rules | 14 ca hồ sơ cũ và 44 ca booking đạt |
| Cloudinary thật, Android API 29 | 1 test đạt sau khi bật PDF delivery: upload PNG/PDF thật, tải lại cả hai URL HTTP 200, nội dung khớp từng byte |

Build debug, instrumentation và release unsigned đều thành công. [Biên bản máy đọc được](evidence/cloudinary-media-2026-10-26.json) lưu kết quả và SHA-256 của APK. APK tại `output/releases/cloudinary-2026-10-26/` hiện chưa có cloud name/preset; cần điền `.env` và build lại trước khi demo upload thật.

Phản hồi Cloudinary trong test contract và test Repository là fixture, được ghi rõ trong mã. Không dùng những kết quả này để khẳng định upload vào tài khoản Cloudinary của nhóm đã đạt.

## Rules đã triển khai

Sau phê duyệt tường minh ngày 26/10/2026, Rules cho `propertyVerifications` đã nhận URL Cloudinary demo của `LOCATION`/`PHOTOS`, giữ nguyên quyền đọc/update và loại trừ URL Cloudinary cho `IDENTITY`. Nguồn kiểm đúng UID/mã hồ sơ và hình dạng URL; ứng dụng kiểm thêm cloud name đã cấu hình. Đây không phải cơ chế xác minh quyền sở hữu file trên Cloudinary hay làm URL công khai trở thành riêng tư.

Đã compile bằng API Firebase, triển khai lên project `broom-sharing-firebase-mobile`, database `broomsharing`, rồi đọc lại nguồn đang hoạt động và đối chiếu SHA-256 `b430e2378032d48f953dea713c72f8743f1291901d30fd2640785bb772764ad8`. Ruleset: `3c6d21de-326e-4caf-a5e5-52239e504ef4`. Bảy index giữ nguyên và `READY`; script triển khai không ghi document nghiệp vụ. [Biên bản triển khai](evidence/cloudinary-rules-deployment-2026-10-26.json).

## Kiểm thử tài khoản Cloudinary thật

Sau khi người phụ trách điền hai biến `.env`, đã build/cài lại debug và chạy `CloudinaryLiveUploadTest` với `runCloudinaryLive=true` trên Android API 29. Tệp được tạo trên thiết bị, UID ký hiệu `demo_acceptance`, không sử dụng tệp khách hàng. Cả PNG và PDF được provider nhận, public ID/URL đáp ứng contract của ứng dụng. Lượt đầu PDF delivery trả HTTP 401 với `deny or ACL failure`; sau khi người phụ trách bật và lưu `Allow delivery of PDF and ZIP files`, đã chạy lại bằng tệp mới. Cả hai URL trả HTTP 200 và nội dung tải lại khớp từng byte. Kết quả cuối: `OK (1 test)`, 4,131 giây.

[Biên bản provider thật](evidence/cloudinary-live-2026-10-26.json) lưu URL, kích thước, mã HTTP và kết quả đối chiếu. Preset chưa trả delete token; các tệp demo ở nhánh `demo_acceptance` cần dọn bằng Cloudinary Console nếu không giữ làm bằng chứng. Bốn lượt chạy gồm chẩn đoán và xác nhận đã tạo tối đa tám tệp demo; hai lượt đầu không giữ được danh sách URL đầy đủ. Không ghi document Firestore trong bài test này. Nghiệm thu này xác nhận transport/upload/delivery của provider thật; chưa thay thế bài kiểm tra toàn bộ giao diện tạo phòng, lưu URL Firestore và Admin xem minh chứng.

APK debug có cấu hình đã lưu tại `output/releases/cloudinary-2026-10-26/BroomSharing-cloudinary-configured-debug.apk`, SHA-256 `45f433c47738c2175569c0101670deab504cf14e67d309d755e9fa8b6ca9ab49`; các APK mang tên `unconfigured` ở phần trên là hiện vật kiểm tra trước khi có cấu hình, không phải bản dùng để demo upload.

## Điều kiện còn chờ

1. Dọn tệp demo trong Cloudinary Console khi không còn cần giữ. Preset hiện không trả delete token, nên chưa xác nhận xóa vật lý tự động sau upload. [Tài liệu phân phối PDF trên gói Free](https://cloudinary.com/documentation/image_delivery_options).
2. Không áp dụng cách lưu công khai này cho giấy tờ IDENTITY thật. Trước khi dùng ngoài bản đồ án cần thiết kế quyền upload/xem/xóa phía dịch vụ tin cậy, cùng dọn file hết token.

Không thực hiện upload minh chứng thật hoặc chuyển dữ liệu khách hàng lên dịch vụ ngoài trong lượt kiểm thử này. Chưa push phần media lên GitHub. Kết quả 119 unit test, 15 contract test, 1 Repository test và release unsigned ở biên bản trước là các lượt kiểm tra riêng; không dùng chúng để thay cho kết quả provider thật hiện tại.
