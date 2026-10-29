# Tối ưu lượt đọc Firestore trước khi nghiệm thu

Trạng thái: đã sửa và kiểm chứng giảm tải nền bằng cửa sổ lặp lại 5 phút; Phúc cũng quan sát Usage và đồng ý tiếp tục responsive/báo cáo. Không coi số đo lúc rảnh là bằng chứng mọi query đã tối ưu hoặc thay cho nghiệm thu các luồng tiền khi có giao dịch.

## Phạm vi và đơn vị đo

Kiểm tra ngày 28/10/2026, project `broom-sharing-firebase-mobile`, database `broomsharing`. Metadata thực tế xác nhận `FIRESTORE_NATIVE`, **Enterprise Edition**, vùng `asia-east1`. Không đổi database hoặc gói dịch vụ trong lượt xử lý này.

Ảnh Usage do Phúc cung cấp ghi nhận khoảng 100 read units/phút, có phút đạt 118, trong khi writes và realtime reads gần như bằng không. Nếu giữ mức 100/phút liên tục thì tương đương 6.000/giờ, 144.000/ngày; hạn mức 50.000 sẽ cạn sau khoảng 8 giờ 20 phút kể từ đầu chu kỳ. Đây là ngoại suy theo tốc độ quan sát, không phải dự báo chắc chắn cho cả ngày.

Phải phân biệt số lần gọi API, số document trả về và read units. Enterprise tính dữ liệu document/index đã xử lý theo từng 4 KiB; truy vấn trả về rỗng không chứng minh không tốn quota. Do đó ước lượng ban đầu `2 query × 4 lần/phút × 1 unit = 8 units/phút` chỉ là mức tối thiểu giả định, không giải thích được chi phí thực tế của database này.

Nguồn: [Enterprise pricing](https://firebase.google.com/docs/firestore/enterprise/pricing), [Monitoring metrics](https://docs.cloud.google.com/monitoring/api/metrics_gcp_d_h#firestore). Chỉ số sử dụng để đối chiếu là `api/billable_read_units`, tách khỏi `api/request_count`, `document/read_ops_count` và `api/billable_realtime_read_units`.

## Các lỗi thiết kế đã xác nhận trong code

| Điểm | Trước khi sửa | Tác động | Xử lý |
| --- | --- | --- | --- |
| Đồng hồ màn QR kiêm polling mạng | `PaymentViewModel.tick` chạy mỗi giây và gọi đối soát mỗi 15 giây. Chỉ chặn PAID/REVIEW_REQUIRED nên EXPIRED/CANCELLED vẫn gọi khi màn hình còn mở | Một request backend lại đọc actor, payment, order mapping, booking, Host và dữ liệu transaction. Không phải một request chỉ bằng một read | Đồng hồ chỉ tính tại máy. Tạo/lấy QR ban đầu, listener và nút kiểm tra thủ công vẫn hoạt động; không gọi API theo timer |
| Notification service đối soát trùng | `PaymentReminderService` vừa nghe Firestore vừa gọi backend mỗi 15 giây, song song với màn QR và worker server | Cùng một giao dịch có thể bị ba nơi chủ động đối soát | Service chỉ cập nhật thông báo/đếm ngược tại máy và nhận trạng thái qua listener. Dừng khi đã nhận kết quả hoặc quá thời gian an toàn |
| Worker server không có điều kiện rảnh | Hai query nền chạy mỗi 15 giây ngay cả khi không còn payment/hold cần xử lý | Tạo tải đọc đều khi không ai sử dụng app; đủ gây cạn quota dù dữ liệu rất nhỏ | `PaymentWorkMonitor` dùng hai listener `limit(1)` để nhận biết việc chờ. Chỉ chạy lượt quét khi có hold INSTANT_5M hoặc payment CREATING/PENDING |
| Từng lọc lịch sử ở Java | Bản cũ lấy toàn bộ booking PENDING rồi mới loại flow cũ tại Java | Dữ liệu đã tải vẫn phải tính chi phí, dù bị bỏ ở client | Đã sửa trước lượt này: đưa cả `status=PENDING` và `paymentFlow=INSTANT_5M` vào query. Bytecode JAR thực sự chạy ngày 28/10 đã được đối chiếu, không chỉ kiểm file nguồn |
| Dashboard Host tải trùng khi mở/xoay | `onProfileReady` gọi tải một lần, còn `onResume` gắn bộ listener; nút tải lại cũng dùng một nhánh GET khác | Có thể đọc lặp rooms/buildings/booking và để kết quả GET xen vào snapshot thống kê | Dashboard chỉ dùng bộ listener hiện có; `resumeDashboard` idempotent cho thời điểm hồ sơ sẵn sàng trước/sau onResume. Nút tải lại chủ động gắn lại bộ listener, onStop vẫn tháo. Không áp số liệu tiết kiệm của worker cho thay đổi này |

Timer một giây tự nó không phải lỗi. Lỗi là gắn request mạng vào timer mà thiếu điều kiện trạng thái cuối, hoặc để nhiều tầng cùng polling một nguồn dữ liệu. `onStop` của màn QR đã dừng timer khi rời Activity; vấn đề cụ thể là nó vẫn polling trên màn EXPIRED/CANCELLED đang mở, không được mô tả thành mọi Activity đều rò listener.

## Bằng chứng tách nguồn

Lượt đọc chỉ một lần xác nhận 9 payment: 6 EXPIRED, 2 CANCELLED, 1 PAID; không có CREATING/PENDING. Mười booking PENDING còn lại thuộc flow lịch sử. Không thay trạng thái chúng để làm đẹp kết quả đo.

Backend Java local được tạm dừng lúc **17:33:28 ngày 28/10/2026**, giữ nguyên tunnel, merchant và dữ liệu. Cloud Monitoring cho thấy trước mốc này API RunQuery vẫn phát sinh khoảng 6–9 request/phút; read units của Query trong các phút gần đó khoảng 32–71. Sau thời gian chuyển tiếp, các khoảng phút đã được thu thập về 0 khi không thao tác app. Kết quả này xác nhận worker local là nguồn tải nền trong cửa sổ cô lập; không quy toàn bộ usage của cả hai ngày cho một hàm duy nhất.

Backend mới được chạy lại lúc **17:53:54**, cùng URL và webhook relay. Log có bộ đếm `scans`, `idleSkips`, `workPending`, chỉ ghi số lượt, không ghi nội dung chuyển tiền hoặc thông tin tài khoản. Một lượt quét lúc khởi động có thể xảy ra trước snapshot đầu tiên; khi snapshot xác nhận rỗng, các nhịp sau phải tăng `idleSkips` mà không tăng `scans`.

Monitoring thu lúc 19:56 xác nhận Query phát sinh 26 read units ở khoảng 17:54–17:55 khi khởi động; các điểm Query đã thu sau đó bằng 0, thay cho chuỗi quét đều trước bản sửa. Log cuối của phiên Java ghi `scans=1, idleSkips=403, workPending=false`: 403 nhịp rảnh được bỏ qua mà không chạy lượt quét thứ hai. Đây là kết quả của cửa sổ rảnh, không phải cam kết mọi thao tác app đều không tốn reads. Hai series nhãn Query và RunQuery trong dữ liệu Monitoring có thể cùng phản ánh một nguồn, nên không cộng máy móc chúng thành tổng chi phí.

Tệp bằng chứng local nằm trong `output/verification/read-cost-2026-10-28/`: metadata/Monitoring theo phút, thời điểm dừng/chạy lại server và thống kê ứng viên. Không chứa token đăng nhập. Monitoring có độ trễ thu thập; không lấy một điểm chưa cập nhật làm bằng chứng tối ưu đã thành công.

### Đo lặp lại đủ 5 phút, 20:10–20:15

Backend bản sửa và tunnel mới được bật lại sau gián đoạn máy. Trong cửa sổ 20:10:02–20:15:02, app đóng, không tạo payment, Java vẫn chạy. Cả 21 probe health thành công; `scans` giữ nguyên 1, `idleSkips` tăng từ 3 lên 23: không có lượt quét mới trong 20 nhịp định kỳ. Health không thực hiện đọc Firestore.

Đã chờ Monitoring cập nhật rồi lấy điểm gốc không aggregation. Năm khoảng 20:10–20:11, 20:11–20:12, 20:12–20:13, 20:13–20:14 và 20:14–20:15 đều ghi Query read units = 0, Listen read units = 0, realtime read units = 0 và RunQuery request count = 0. Chi phí khởi động được tách riêng ở 20:09–20:10: Query 26 units và Listen 13 units; không cộng lại alias RunQuery vào Query. Kết quả xác nhận đã chặn quét nền khi rảnh, không chỉ dừng Java để làm đường biểu diễn bằng 0.

Bằng chứng: `idle-five-minute.json` chứa probe và counter; `monitoring-five-minute-raw.json` chứa các interval gốc. Trước sửa, năm bucket Query gần 17:25–17:30 có 62, 54, 38, 68, 32 units, tổng 254. Hai cửa sổ ở thời điểm khác nhau nên chỉ đối chiếu tải rảnh đã quan sát, không gọi đây là benchmark toàn bộ app hoặc tỷ lệ tiết kiệm cho mọi tình huống.

## Không đánh đổi tính đúng nghiệp vụ

Backend vẫn đối soát định kỳ khi có việc chờ và vẫn nhận webhook. Android không tự suy ra PAID từ thao tác quay lại hoặc tự kết luận booking đã nhả lịch khi bộ đếm tại máy về 0. Listener tiếp tục nhận PAID, REVIEW_REQUIRED, EXPIRED và CANCELLED để đổi màn hình/thông báo.

Nếu listener nhận biết việc chờ chưa có snapshot hoặc báo lỗi, worker giữ hướng xử lý thận trọng: vẫn được phép đối soát, tránh bỏ quên booking giữ lịch. Trạng thái này phải được quan sát qua log; không dùng cờ rỗng giả để tiết kiệm reads.

57 kiểm thử backend đã qua, không có skipped, gồm 34 ca transaction trên Firestore emulator riêng. Hai ca mới kiểm tra: rảnh không chạy 20 lượt quét giả lập, booking mới đánh thức worker và được nhả đúng slot khi hết hạn; payment của booking đã xác nhận vẫn đánh thức worker dù không có hold PENDING. Các ca thanh toán đủ tiền, trả muộn, webhook và nhả lịch cũ tiếp tục chạy. Đây là kiểm thử local, tách khỏi ảnh cloud dùng trong báo cáo.

146 kiểm thử đơn vị Android cũng qua, không lỗi hoặc skipped; APK debug đã build thành công. Chưa dùng các kết quả local này để thay cho nghiệm thu webhook/chuyển tiền thật sau bản sửa. Phiên Java và tunnel local có thể dừng sau gián đoạn máy; cần kiểm tra health trước buổi demo, không suy ra đang chạy chỉ từ log của phiên cũ.

## Bài học có thể trình bày khi bảo vệ

Ứng dụng nhỏ vẫn có thể hết quota do cách tổ chức vòng lặp, không phải vì số người dùng lớn. Chi phí cần ước lượng bằng tần suất nhân với lượng dữ liệu xử lý mỗi lượt và số thành phần cùng làm việc. Cần đo đúng edition, dùng số liệu theo phút và tách nguồn phát sinh, thay vì chỉ nhìn tổng tích lũy trong ngày.

Nguồn cập nhật thời gian thực nên có một trách nhiệm rõ: server xác thực và đối soát tiền; Android quan sát kết quả và hiển thị đồng hồ tại máy. Tác vụ nền phải có điều kiện bắt đầu, dừng và phục hồi khi lỗi. Trạng thái đã kết thúc không được tiếp tục tạo cùng tải mạng như trạng thái đang chờ.

Giảm polling không thay cho tối ưu kế hoạch query. `RunQuery` Explain của Standard không được Enterprise này hỗ trợ; API trả yêu cầu dùng ExecutePipeline. Vì chưa có execution plan tương ứng, lượt rà soát này không khẳng định query đã dùng index tối ưu hay bịa số document/index bị quét. Có thể tiếp tục khảo sát Pipeline Explain hoặc Query Insights trong một lượt tối ưu riêng, thay vì tạo index theo suy đoán.
