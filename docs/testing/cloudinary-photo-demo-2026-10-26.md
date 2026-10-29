# Nghiệm thu bộ 30 ảnh demo trên Cloudinary và Firestore

Ngày 26/10/2026. Project `broom-sharing-firebase-mobile`, database `broomsharing`. Ảnh được tải từ các trang ảnh miễn phí Unsplash và đưa qua các lớp hiện có của ứng dụng Android. Không dùng phản hồi upload giả lập trong lượt này.

## Dữ liệu đã chuẩn bị

| Hạng mục | Số ảnh | Dữ liệu Firestore |
| --- | ---: | --- |
| Bìa tòa nhà | 3 | 3 document `buildings`, trường `coverUrl` |
| Gallery phòng | 24 | 6 document `rooms`, mỗi document có 4 URL theo thứ tự trong `imageUrls` |
| Minh chứng demo | 3 | 3 document `propertyVerifications`, URL trong `evidenceRefs` |
| Tổng | 30 | 12 document dữ liệu chính; các audit phát sinh theo luồng Repository |

Ảnh JPEG không trùng, cạnh dài 1.600 px, tổng 9.160.096 byte. Thư mục local: `data/demo/media/`. [Danh mục và nguồn từng ảnh](../../data/demo/media/sources.json), [manifest kích thước và SHA-256](../../data/demo/media/manifest.json), [bảng xem trước](../../data/demo/media/contact-sheet.jpg). Có ZIP tại `output/demo-media/BroomSharing-30-photos.zip`.

Tất cả document trong bộ này có tiền tố `media_demo_20261026_` và tên hiển thị `[DEMO ẢNH]`. Bốn phòng thuê giờ gồm Phòng họp Sen, Phòng họp Vista, Bàn làm việc Light và Không gian Creative. Hai phòng thuê tháng/quý gồm Văn phòng Worknest và Mặt bằng Studio. Giá, vị trí, sức chứa là dữ liệu mô phỏng; các ảnh stock không chứng minh cùng thuộc một địa điểm. Ba hồ sơ minh chứng giữ trạng thái `PENDING`; không dùng ảnh stock để xác minh tài sản thật, không tạo tệp IDENTITY.

## Luồng kiểm tra

1. Dùng tài khoản Host demo đã có, kiểm tra profile `HOST` và `ACTIVE`. `HostViewModel.saveBuilding()` upload bìa bằng `MediaRepository` rồi lưu document; `HostViewModel.saveRoom()` upload gallery, giữ thứ tự ảnh, lưu phòng chờ duyệt qua `HostRepository`.
2. `PropertyVerificationViewModel` upload ba ảnh minh chứng theo đúng UID/mã hồ sơ rồi lưu bằng `PropertyVerificationRepository`. URL Cloudinary được ghi thật trong Firestore và được Rules đang triển khai chấp nhận.
3. Tài khoản Admin demo duyệt riêng từng phòng bằng `AdminRepository.review()`. Không ghi quyền quản trị trực tiếp để bỏ qua Rules. Nhánh `AdminRepository.evidence()` đọc được cả ba ảnh từ URL thuộc hồ sơ đã lưu; SHA-256 khớp ảnh nguồn.
4. Guest đọc sáu phòng qua `RoomRepository.detail()`, gồm gallery bốn ảnh, Building và PublicHost. Màn `SpaceDetailActivity` thực hiển thị ảnh bitmap và tiêu đề phòng; có screenshot bằng chứng. Kiểm tra gallery riêng chờ các thumbnail tải đủ, chọn ảnh thứ ba rồi quay về ảnh đầu.
5. Cả 30 URL trả HTTP 200. Mọi ảnh tải lại khớp SHA-256, chiều rộng và chiều cao với file local; không chỉ dựa vào kết quả JSON upload.

[Biên bản máy đọc được](evidence/cloudinary-photo-demo-2026-10-26.json) chứa từng URL, nguồn, document và digest. Lượt xác nhận toàn luồng đạt `OK (1 test)`, 48,033 giây, trên Android API 29. Nhật ký local: `tmp/cloudinary-photo-demo-device.log`; screenshot: `output/verification/cloudinary-2026-10-26/guest-photo-detail.png`.

Bài chỉ đọc gallery `guestCanViewPreparedPhotoGallery` đạt `OK (1 test)`, 11,515 giây. Các thumbnail hiển thị đủ bitmap; chọn vị trí thứ ba rồi vị trí đầu đều đổi ảnh bìa. Screenshot cuối được chụp sau khi các ảnh đã tải xong. Nhật ký: `tmp/cloudinary-photo-gallery-device.log`.

## Hiện tượng ghi nhận và giới hạn

Lượt đầu sau khi Admin vừa duyệt các phòng, bước Guest đọc `rooms/media_demo_20261026_r01` qua SDK trả `PERMISSION_DENIED`. Đối chiếu chỉ đọc bằng token Guest qua REST cho phòng, Building, PublicHost và profile đều HTTP 200; sáu phòng trên server đều `APPROVED` + `ACTIVE`. Chạy lại toàn bộ bài kiểm tra trong phiên mới đạt, không sửa Rules hay trạng thái phòng. Chưa xác định độc lập nguyên nhân của lần từ chối đầu, nên không kết luận đây là lỗi dữ liệu hay đã sửa một lỗi phiên đăng nhập. Log đầu được giữ tại `tmp/cloudinary-photo-demo-first-guest-denial.log`.

Các lỗi thiết lập bài test trước khi ghi cloud (đường dẫn fixture bị scoped storage chặn, kiểu Context khi tạo ViewModel) đã được chỉnh trong mã kiểm thử. Không bổ sung quyền bộ nhớ vào ứng dụng để vượt scoped storage. File fixture dùng thư mục riêng; ảnh trong thư mục Pictures là bản để người dùng chọn thủ công khi demo.

Scope đã đạt gồm upload thật qua ViewModel/Repository, lưu Firestore thật, duyệt phòng, Guest đọc và màn hình chi tiết. Việc tự động hóa chưa thao tác toàn bộ form bằng tay từ Android document picker. Credentials kiểm thử được gỡ khỏi thiết bị sau khi lấy biên bản; tài khoản và dữ liệu demo giữ lại cho video. Không chỉnh phòng ngoài tiền tố demo, không thay Rules/index, không di chuyển dữ liệu khách hàng.

Nguồn ảnh: [Unsplash License](https://unsplash.com/license), [phạm vi dùng ảnh trong dự án](https://help.unsplash.com/en/articles/2612315-can-i-use-unsplash-images-for-personal-or-commercial-projects). Danh mục nguồn không khẳng định các ảnh được chụp tại Việt Nam.
