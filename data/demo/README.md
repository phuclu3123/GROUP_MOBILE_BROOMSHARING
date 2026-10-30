# Dữ liệu demo

`firestore-dataset.json` là snapshot gốc được tạo cho dữ liệu mô phỏng ngày 19/10/2026; không phải bản sao trạng thái cloud hiện tại. Không nạp lại để ghi đè các booking, thanh toán hoặc lịch đã dùng sau đó.

- `media/`: ảnh minh họa, nguồn tác giả/giấy phép và manifest.
- `host-identity/`: hai ảnh giấy tờ DEMO/NOT VALID và avatar chữ viết tắt; không có giấy tờ cá nhân thật.
- `credentials.local.properties` và các tệp `*.local.*`: thông tin đăng nhập demo tại máy, được Git bỏ qua. Bàn giao riêng cho thành viên được phép, không đưa vào APK, báo cáo hoặc video.

Hai công cụ Java gốc ở `tools/dataset/`; không tham gia build Android. Dữ liệu demo không chứng minh tài sản, địa chỉ, xác minh danh tính hoặc giao dịch thương mại có thật. Bản cuối giữ lịch sử booking để quay demo và không xóa dữ liệu cloud khi dọn repository.
