# Tình trạng kiểm thử Auth thật

Đã chuẩn bị `AuthFormValidationTest`, cập nhật `AuthUiTest` để validation chạy được khi có Firebase config, và `LiveAuthTest` để tạo/dọn tài khoản test ngẫu nhiên khi bật `runLiveAuth=true`.

Lượt chạy Firebase thật trước bị lỗi socket EPERM trên máy ảo; app hiện lỗi kết nối và chưa tạo tài khoản thành công. Sau đó Phúc yêu cầu tự kiểm tra thiết bị/cloud, nên không chạy tiếp. Không coi bộ test đã biên dịch được là bằng chứng test thật đã đạt.

Xem **manual-check-dot-1.md**, phần Auth để tự kiểm tra đăng ký/đăng nhập, unique username/email, định dạng, thông báo và phiên. Firebase project hiện tại dùng database **broomsharing**.

Căn cứ: Buổi 4 — Input controls; Buổi 5 — UI testing; Buổi 9 — ViewModel/Repository/LiveData và Firestore. Không thêm framework vào ứng dụng.
