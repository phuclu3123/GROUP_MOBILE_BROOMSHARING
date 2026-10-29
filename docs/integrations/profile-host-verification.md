# Ảnh đại diện và hồ sơ Host

Ứng dụng giữ Java + XML, MVVM + Repository + Model. Avatar và hai ảnh giấy tờ demo được upload thật lên Cloudinary; Firestore lưu URL, đơn và trạng thái xét duyệt. Không thêm backend hoặc nhúng API Secret vào APK.

## Hai cách nhập ảnh

- **Chọn ảnh:** Android document picker nhận JPEG/PNG/WebP từ thư viện, dùng URI được người dùng cấp. Không yêu cầu quyền đọc toàn bộ bộ nhớ.
- **Nhập URL:** dùng liên kết HTTPS trực tiếp tới file ảnh, không dùng URL trang web chứa ảnh. `RemoteImageClient` thực hiện GET bằng OkHttp trên worker, giới hạn 5 MiB, chặn redirect và URL mạng nội bộ. Nội dung tải được kiểm nhận dạng rồi upload vào thư mục Cloudinary thuộc UID hiện tại. Không chỉ lưu URL ngoài vào hồ sơ rồi giả nhận đã upload.

Không nén hoặc đổi định dạng file upload trong app. Picasso resize ảnh phục vụ hiển thị để giảm bộ nhớ, không thay đổi file gửi lên provider. File quá 5 MiB hoặc HTML/PDF trong vị trí chỉ nhận ảnh bị từ chối.

`Picasso 2.8` áp dụng cùng cách `load(...).into(...)` của bài tập `FireBase_test_Article2_Picasso_URL_Backup`, nhưng được tổ chức trong `OnlineImages` dùng chung. `OkHttp 4.12.0` xử lý GET nguồn ảnh và multipart upload. `CloudinaryClient.getJSON()` đọc JSON phản hồi bằng `JSONObject`, kiểm URL, public ID, định dạng và kích thước; không phải một dịch vụ xác minh danh tính.

## Ảnh đại diện

Màn Hồ sơ Guest và Tài khoản Host/Admin đều có Chọn ảnh, Nhập URL và Gỡ ảnh. `ProfilePhotoViewModel` giữ trạng thái bận và lỗi; `ProfileRepository` cập nhật `users/{uid}.photoUrl`. Khi tài khoản là Host, transaction cập nhật cả `publicHosts/{uid}.photoUrl`. Màn chi tiết phòng đọc avatar từ bản chiếu Host, không mở quyền đọc hồ sơ khách hàng riêng tư.

Cloudinary: `broomsharing/avatarMedia/{uid}/avatar/{file}`. Avatar phải thuộc đúng UID và cloud đã cấu hình. Gỡ ảnh bỏ URL trong Firestore; không khẳng định file provider đã bị xóa vật lý nếu preset không cấp delete token.

## Đơn Host và vòng xét duyệt

Người dùng GUEST đang hoạt động nộp thông tin liên hệ, mô tả hồ sơ và hai ảnh khác nhau cho mặt trước/mặt sau. Mỗi mặt có thể lấy từ thư viện hoặc URL. Bản hiện tại chỉ dùng tài liệu giả lập ghi rõ DEMO/NOT VALID và lưu công khai theo URL. Không dùng CCCD thật hoặc CCCD lấy từ người khác trên mạng.

Cloudinary: `broomsharing/hostIdentity/{uid}/{uid}/{file}`. Firestore: `hostApplications/{uid}`. Đơn mới dùng `schemaVersion=2`, `identityImageUrls` đúng hai URL theo thứ tự, `evidenceMode=DEMO_PUBLIC`, `status=PENDING`, `identityReviewedAt=null`. Số 2 là phiên bản cấu trúc của đơn Host, độc lập với phiên bản booking.

Nộp đơn không cấp quyền cho thuê. UI Admin buộc mở đủ hai ảnh trong màn xem trước rồi mới cho duyệt. Repository kiểm lại người nộp, hai URL và thông tin đã xem của luồng UI. Việc thực sự đã xem ảnh là trạng thái ứng dụng; Rules không thể tự chứng minh Admin đã nhìn nội dung ảnh.

- **Từ chối:** cần lý do không rỗng. Người nộp xem kết quả, thay/bổ sung ảnh rồi gửi lại. Đơn dùng cùng UID, trở lại PENDING. Nếu URL đã upload còn dùng được thì ViewModel retry lưu không upload lại.
- **Duyệt:** một transaction cập nhật đơn APPROVED, reviewedBy/identityReviewedAt, `users.role=HOST`, PublicHost và audit. Rules buộc đơn hợp lệ và cấp quyền Host đi cùng thao tác duyệt; client không được tự đổi role.
- **Đơn đang chờ/đã duyệt:** người nộp không được tự sửa ảnh của đơn. Host đã được cấp quyền trước bản này vẫn giữ nguyên quyền. Đơn cũ thiếu hai ảnh không được duyệt mới; Admin cần từ chối kèm yêu cầu bổ sung để người nộp gửi lại.

Hồ sơ Firestore chỉ chủ hồ sơ đang hoạt động và Admin được đọc. URL Cloudinary vẫn là công khai theo cấu hình đồ án. Việc duyệt hồ sơ demo không được gọi là xác minh CCCD thật, OCR, nhận diện khuôn mặt hoặc eKYC. Nếu triển khai giấy tờ thật, cần chuyển tệp sang storage riêng tư và thiết kế lại kiểm soát quyền xem/xóa trước khi thu thập.

## Dữ liệu và nghiệm thu

`data/demo/host-identity/` có hai mặt giấy tờ hình học/chữ do script tạo, không sao chép mẫu CCCD; hai avatar dùng chữ viết tắt. Script: `tools/dataset/prepare_host_identity_demo.py`.

Thông tin đăng nhập tài khoản nghiệm thu riêng được giữ tại `data/demo/profile-identity-credentials.local.properties`, được Git bỏ qua. Không hiển thị mật khẩu trong log hoặc video. [Biên bản nghiệm thu](../testing/profile-identity-2026-10-26.md) ghi rõ kết quả emulator và tài khoản cloud thật.
