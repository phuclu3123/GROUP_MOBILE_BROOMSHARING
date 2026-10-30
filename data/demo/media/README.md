# Bộ 30 ảnh demo BroomSharing

Ảnh chụp không gian thật, tải từ các trang ảnh miễn phí Unsplash ngày 26/10/2026. Bộ này dùng minh họa cho đồ án; các ảnh trong cùng một phòng demo có thể đến từ nhiều địa điểm khác nhau, không xác nhận đó là cùng tài sản hoặc nằm tại địa chỉ ghi trong app.

| Thư mục | Số ảnh | Mục sử dụng |
| --- | ---: | --- |
| `buildingMedia/b01` đến `b03` | 3 | Bìa ba tòa nhà demo |
| `roomMedia/r01` đến `r06` | 24 | Sáu phòng, mỗi phòng bốn ảnh |
| `verification/v01` đến `v03` | 3 | Minh chứng LOCATION/PHOTOS demo; không có giấy tờ IDENTITY |

`contact-sheet.jpg` xem nhanh toàn bộ ảnh. `sources.json` ghi trang ảnh, tác giả, giấy phép và URL tải; `manifest.json` ghi thêm kích thước, dung lượng và SHA-256. Có 30 ảnh JPEG khác nhau, cạnh dài 1.600 px, mỗi ảnh dưới 5 MiB. Bảng xem trước là ảnh bổ sung, không tính vào 30 ảnh nguồn.

Ảnh phòng bao gồm phòng họp, bàn làm việc, văn phòng riêng, mặt bằng và không gian sáng tạo. Trong bộ dữ liệu app, r01/r02/r03/r06 chỉ thuê giờ; r04/r05 chỉ thuê tháng hoặc quý. Không tạo một phòng đồng thời có cả hai hình thức thuê.

Bộ dữ liệu trên Firestore dùng tiền tố `media_demo_20261026_`, tên hiển thị `[DEMO ẢNH]`. Thông tin địa chỉ, giá và sức chứa là dữ liệu mô phỏng. Thông tin khách hàng tiếp tục ở Firestore; Cloudinary chỉ chứa tệp. Các hồ sơ minh chứng được giữ ở trạng thái chờ, không coi ảnh stock là chứng cứ xác minh tài sản có thật.

Nguồn và quyền sử dụng: [Unsplash License](https://unsplash.com/license), [Unsplash Help: personal/commercial projects](https://help.unsplash.com/en/articles/2612315-can-i-use-unsplash-images-for-personal-or-commercial-projects). Đây là tập ảnh minh họa đi kèm ứng dụng demo, không phải dịch vụ phân phối lại ảnh stock.
