# Kiến trúc ứng dụng tổng quan

[Bản draw.io chỉnh sửa được](05-application-architecture.drawio) · [SVG vector](05-application-architecture.svg) · [PNG xem trước](05-application-architecture.png)

Trang `05 · KIẾN TRÚC ỨNG DỤNG` thể hiện trách nhiệm của View, ViewModel, Repository và Model. Các mũi tên giữa ba khối đầu phân biệt sự kiện, yêu cầu, kết quả và cập nhật LiveData. Model là các đối tượng nghiệp vụ được các tầng sử dụng, không phải một bước xử lý nằm sau Repository. Đường nối xuống Model biểu thị việc sử dụng các đối tượng này.

Các khối ngoài ứng dụng biểu diễn nhóm dịch vụ ở mức tổng quan. Nhánh thanh toán dùng nét đứt để thể hiện phần dự kiến. Nhà cung cấp thanh toán và phương án lưu ảnh tương lai chưa được quyết định. Code hiện dùng Firebase Storage cho ảnh; hình giữ khối lưu ảnh độc lập để thể hiện trách nhiệm mà không chốt nhà cung cấp tương lai.

Hình được đối chiếu với mã nguồn Java và cấu hình XML hiện tại ngày 24/10/2026: Activity quan sát LiveData; ViewModel gọi Repository và cập nhật trạng thái; Repository thực hiện truy cập dữ liệu và dịch vụ. Sơ đồ không mô tả toàn bộ lời gọi SDK: hiển thị bản đồ còn có giao tiếp trực tiếp của MapLibre với dịch vụ bản đồ, và một số ViewModel đọc UID từ Firebase Authentication.

Nguồn đối chiếu chính:

- [RoomViewModel](../../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/ui/room/RoomViewModel.java), [BookingViewModel](../../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/ui/booking/BookingViewModel.java).
- [Repository](../../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository), [Model](../../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/model), [XML giao diện](../../../app/src/main/res/layout).

Icon vector thuộc [Microsoft Fluent UI System Icons](https://github.com/microsoft/fluentui-system-icons), giấy phép MIT. Các SVG gốc, giấy phép và nguồn tải nằm trong [assets/architecture](assets/architecture/sources.json); icon được nhúng trực tiếp vào tệp draw.io và SVG xuất ra.

Đã đồng bộ bản chỉnh sửa trực tiếp của người dùng, giữ 53 đối tượng native và 13 đường nối. Giữ kích thước thẻ và khoảng cách thoáng của bản người dùng đã chỉnh; cả cụm dịch vụ được dịch lên để tâm dọc trùng tâm khối ứng dụng. Chữ hiển thị dùng Segoe UI nét thường với cỡ chữ vừa phải. Bước xuất PDF đọc lại trang đang mở qua MCP trước khi tạo SVG, PNG và PDF vector. Không dựng lại trang từ đặc tả cũ sau khi người dùng chỉnh sửa.
