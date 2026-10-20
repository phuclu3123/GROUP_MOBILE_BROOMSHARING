# Địa chỉ cho thuê công khai tại TP.HCM

Snapshot đối chiếu ngày **20/10/2026**, nạp vào Firestore project `broom-sharing-firebase-mobile`, database `broomsharing`, collection `rentalPlaces`. [Dữ liệu](rental-places-hcm.json) chứa 8 địa chỉ, tọa độ bản đồ nhúng, nhãn tọa độ, URL nguồn và thời điểm kiểm tra. [Kết quả đọc lại cloud](verification.json).

| Địa điểm | Nguồn công khai |
| --- | --- |
| 2–4 Cần Giuộc | [Connect Land](https://connectland.vn/cho-thue-van-phong-2-4-can-giuoc-phuong-1-quan-8) |
| 108–112 Dương Quang Đông | [Connect Land](https://connectland.vn/cho-thue-van-phong-108-110-112-duong-quang-dong-phuong-5-quan-8) |
| 3086 Phạm Thế Hiển | [Connect Land](https://connectland.vn/cho-thue-van-phong-3086-pham-the-hien-phuong-7-quan-8) |
| 783 Hưng Phú | [Connect Land](https://connectland.vn/cho-thue-van-phong-783-hung-phu-phuong-9-quan-8) |
| 195 Dạ Nam | [Connect Land](https://connectland.vn/cho-thue-van-phong-195-da-nam-phuong-3-quan-8) |
| Green River, 2225 Phạm Thế Hiển | [Connect Land](https://connectland.vn/cho-thue-van-phong-2225-pham-the-hien-phuong-6-quan-8) |
| 547–549 Tạ Quang Bửu | [Connect Land](https://connectland.vn/cho-thue-van-phong-toa-nha-so-547-549-duong-ta-quang-buu-phuong-4-quan-8) |
| 849 Tạ Quang Bửu | [Kim Quang Group](https://kimquanggroup.vn/cho-thue-nguyen-toa-nha-849-ta-quang-buu-quan-8-bds4242.htm) |

Chỉ lấy dữ kiện địa chỉ và vị trí công khai. Không sao chép ảnh, thông tin người đăng, điện thoại cá nhân hay giá để tạo giao dịch trong app. Nhãn trong bản đồ nhúng đã được đối chiếu với địa chỉ của tin; tọa độ văn phòng môi giới trong footer trang khác bị loại. Đây là điểm bản đồ do tin nguồn cung cấp, chưa xác minh cửa vào/ranh giới tòa nhà hoặc tình trạng còn trống. Tên phường trên nhãn nguồn có thể khác tên cũ trong URL.

Các địa điểm hiển thị là **tham khảo**, có màu cam và nút xem nguồn, không phải tài sản của tài khoản Host demo. Muốn nhận đặt chỗ phải có Host đăng phòng và qua luồng duyệt hiện có. Không sửa dữ liệu 157 document demo cũ. Rules cho tài khoản đang hoạt động đọc collection này; ứng dụng không có quyền ghi.

Công cụ `tools/dataset/RentalPlacesDataset.java` biên dịch cùng FirestoreDataset và Gson sẵn có. `apply` yêu cầu token quản trị qua biến môi trường `BROOM_DATASET_TOKEN_FILE` trỏ tới tệp ngoài repo; tạo mới bằng precondition `exists=false`, dừng khi document có sẵn khác snapshot, đọc lại từng trường sau khi ghi. `verify` có thể dùng tài khoản Admin demo từ file local đã có, không cần token quản trị. Không đưa token/mật khẩu vào JSON hoặc APK.
