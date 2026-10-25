# Bàn giao giai đoạn 3–4 để hoàn thiện app

Ngày đối chiếu: 25/10/2026. Mốc hoàn thành app dự kiến của nhóm: 27/10/2026. Đã đọc chín sheet và đối chiếu 47 đầu việc đợt 3–4: 25 việc đợt 3, 22 việc đợt 4. Các cột “Chưa làm” trong workbook chưa phản ánh code đã tích hợp; không dùng chúng để tính phần trăm hoàn tất. Workbook là dàn ý, các quyết định nghiệp vụ mới được ưu tiên.

Nguồn: workbook `BroomSharing_PhanCong_4Dot_Android_Java_Firebase.xlsx`, sheet Trúc `A44:J62`, Hoàng `A44:J61`, Phúc `A47:J68`; [nghiệm thu cloud 24/10](cloud-business-acceptance-2026-10-24.md); [đối chiếu nhánh vừa fetch](upstream-members-review-2026-10-25.md). Workbook không bị sửa trong lượt này.

## Phân công phần còn lại

### Phúc: API thanh toán, ảnh/tệp và tích hợp chung

Chủ dự án đã nhận cả payment và lưu ảnh qua dịch vụ bên thứ ba vì nhóm không chọn phương án Firebase Storage trả phí ở bước này. Chưa chốt nhà cung cấp hoặc phạm vi thu tiền/hoàn tiền. Không tự ghi một provider, endpoint hoặc luồng tiền là đã triển khai.

| Việc cần hoàn thiện | Mã liên quan | Kết quả cần bàn giao |
| --- | --- | --- |
| Tích hợp ảnh phòng/tòa nhà | P07, phối hợp H06/H29/U30 | API dùng chung cho chọn/tải/thay/xóa ảnh; loại/kích thước, lỗi giữa chừng, phiên thay đổi và dọn tệp. Hoàng/Trúc nhận kết quả để hiển thị, không viết thêm API upload khác. |
| Tệp bằng chứng riêng tư | P19/P29, phối hợp H22/U31 | Chốt tên field/ID tệp, Host gửi và Admin đọc đúng quyền; không công khai giấy tờ. Chốt lỗi ghi document sau upload và xử lý tệp mồ côi. |
| Payment | P28/P39, phối hợp H23/U13/U26 | Chốt scope với nhóm trước; có nguồn xác nhận đáng tin cậy, chống callback lặp, xử lý hủy/hết hạn/lỗi và trạng thái đối soát theo scope. Không để client tự xác nhận PAID hoặc hoàn tiền. |
| Ghép và nghiệm thu chung | P30/P33–P37 | Giữ schema và lịch sử Git; rà Rules/quyền tệp sau API; build/cài mới/nâng cấp, release có ký và cập nhật setup cuối. |

Phần đã có không giao viết lại: booking engine, EXPIRED tại giờ bắt đầu, can thiệp từng booking của Host bị khóa, audit, giá và lịch transaction, review, phản hồi báo cáo, chat và receipt thông báo. Các luồng này đã có bằng chứng cloud riêng. Tích hợp payment mới vẫn phải kiểm lại ảnh hưởng đến vòng đời booking.

### Trúc: hoàn thiện Guest/UI trên bản tích hợp

| Việc cần làm tiếp | Mã liên quan | Phần đã có và cách tiếp tục |
| --- | --- | --- |
| Đồng bộ Sprint 3 với contract chung | U18–U24 | Dùng package `ui/map`, `ui/chat`, `ui/booking` hiện tại. Không giữ query `participantUids/lastMessage`, nguồn notifications riêng hoặc ghi chat ngoài transaction của nhánh cũ. |
| Polish và lỗi trên thiết bị | U28/U29/U32 | Rà màn nhỏ/font lớn/bàn phím/Back, loading/empty/error/retry và xoay màn hình. Hoàn thiện trên luồng thật, không tạo lại màn mẫu dùng dữ liệu Intent hoặc mock cứng. |
| Ảnh và report sau API | U30/U31 | Nối URL ảnh mới, placeholder/cache/ảnh lỗi. Nếu nhóm giữ ảnh minh chứng thì nối API tệp riêng tư Phúc cấp; báo cáo mô tả đã chạy được. |
| Hủy, review, Calendar và thông báo | U22–U24/U26/U27 | Chức năng đã có; tự kiểm UI và điều hướng trên điện thoại. Kiểm chỉ booking hoàn tất mới review, một lần/booking; Calendar đúng ngày/giờ và không coi intent là đã lưu sự kiện. |

U25 Settings/sort/theme và dark mode là tùy chọn theo thời gian; không chặn nghiệm thu luồng chính khi nhóm không chọn. Không giao Trúc viết lại API bản đồ, booking engine hoặc Rules.

### Hoàng: ghép phần Host đã làm và khép UI theo API mới

| Việc cần làm tiếp | Mã liên quan | Phần đã có và cách tiếp tục |
| --- | --- | --- |
| Lịch tháng | H20 | Đã có ở `c74d963`. Lấy dialog/lưới có chọn lọc, sửa ngày kết thúc loại trừ, EXPIRED, lịch khóa và chọn phòng. Giữ lịch ngày hiện có, không thay service giữ chỗ. |
| Hồ sơ xác minh | H22, phối hợp P19 | Đã có form/list/model ở nhánh Hoàng; chưa ghép vào base. Thống nhất `evidenceRefs`/ID tệp với Phúc, đổi upload Firebase Storage sang API mới và kiểm Admin xem đủ bằng chứng. |
| Phản hồi báo cáo | H25 | Bản tích hợp đã có `ReportCenterActivity` và `ReportRepository.reply`; có thể lấy thẻ RecyclerView từ nhánh Hoàng. Không tạo một Repository phản hồi song song thiếu timestamp/kiểm trạng thái. |
| Ảnh và biên nhận | H23/H29 | Ghép form tải/thay/xóa qua API chung của Phúc, kiểm lỗi và rollback. UI payment/biên nhận chỉ theo trạng thái đã được dịch vụ xác nhận, không suy từ tiền dự kiến hoặc phần trăm hủy. |
| Chốt Host end-to-end | H18/H19/H21/H26/H27/H30/H31 | Tự kiểm chat, inbox, check-in/hoàn tất, bị khóa khi đang mở màn hình, danh sách đồng bộ và toàn chuỗi xin Host → duyệt → tạo phòng → duyệt → nhận đơn. Các phần lõi đã có, không viết lại. |

H24 hủy đã có; đề nghị đổi lịch/phần chênh lệch tiền chưa có scope riêng, không tự thêm cơ chế sửa thẳng khoảng thuê. H28 dashboard cơ bản đã có số thật; KPI tài chính nâng cao không được coi tiền dự kiến là doanh thu.

## Các đầu việc đã có nhưng cần tự nghiệm thu lại

| Người | Kịch bản tự chạy | Điều kiện đạt |
| --- | --- | --- |
| Trúc | Guest Home → Detail → chọn ngày/giờ → báo giá → gửi → danh sách/chi tiết → chat | Đúng ID và tiền; không báo thành công trước server; loading/lỗi rõ; không mất lựa chọn sau xoay; retry không tạo đơn/tin nhắn hai lần. |
| Trúc | Hủy/review/thông báo/lịch | Nút đúng trạng thái; không review sớm hoặc lặp; bấm thông báo đến đúng nội dung; Calendar giữ đúng múi giờ và giờ kết thúc. |
| Hoàng | Hai Guest tranh giờ, Host xác nhận/từ chối/hủy và xem lịch | Một lượt giữ lịch; nhả đúng slot/tháng; không sửa lịch phòng khi còn booking; không tự mở lại phòng bị Admin đình chỉ. |
| Hoàng | Bị khóa đang dùng app, rồi Admin xử lý từng đơn | Mất quyền thao tác, giữ lịch sử; CHECKED_IN không tự bị hủy/kết thúc; danh sách cập nhật sau can thiệp. |
| Hoàng và Phúc | Upload bằng chứng → gửi hồ sơ → Admin xem → duyệt/từ chối → Host xem kết quả | File và document cùng đúng chủ; người ngoài không xem; upload/lưu lỗi có xử lý; không gắn nhãn đã xác minh chỉ vì đã tải tệp. |
| Phúc | Payment và media sau tích hợp | Thành công/lỗi/retry/phiên đổi/quyền bị từ chối đều rõ; callback lặp không nhân tiền/trạng thái; khóa lịch vẫn khớp trạng thái booking. |
| Cả nhóm | Ít nhất hai thiết bị, ba vai trò; mất mạng/reconnect, quyền bị từ chối, cài mới/nâng cấp | Có kết quả quan sát được trên APK chốt, không chỉ kết luận từ việc build thành công. |

Mỗi người ghi commit/APK, thiết bị và phiên bản Android, ID dữ liệu demo, bước chạy, kết quả mong đợi/thực tế, ảnh hoặc log và lỗi còn lại. Giữ mật khẩu/API secret trong file local được bỏ qua bởi Git. Không giao một người làm tester thay cho việc tự kiểm module của mỗi thành viên.

## Rà hết phạm vi sheet 3–4

| Chủ phần | Đã có nền tảng/đã kiểm một phần | Còn tích hợp hoặc tự nghiệm thu | Tùy chọn/chưa chốt |
| --- | --- | --- | --- |
| Trúc | U18–U24, U26/U27 | U28–U32; nối UI vào base đã nghiệm thu | U25; phần đính kèm của U31 phụ thuộc API tệp |
| Hoàng | H18/H19/H21/H25–H28 | H20/H22 cần ghép có chỉnh; H23/H29 phụ thuộc API; H30/H31 tự nghiệm thu | Phần đổi lịch trong H24; KPI nâng cao H28 |
| Phúc | P22–P24/P26/P27/P29, phần booking của P33/P34 | P25/P30/P35–P37 cần kiểm bản cuối; P28/P39 payment; quyền tệp P07/P19 | P31 KPI nâng cao, phần tiền/tranh chấp mở rộng P32; P38 đối tác xác minh |

P32 đã có xử lý báo cáo, đình chỉ phòng/khóa Host, audit và Admin xử lý từng booking; chưa coi đó là hệ phân xử tiền hoàn chỉnh. Không tự thêm QR/PIN giả, push lúc app đóng, collection trùng hoặc partner verification để bám chữ trong sheet. Quyết định hiện tại là thông báo trong app/foreground, chưa có FCM nền.

Ngày 25/10 ưu tiên chốt contract API và các phần nhánh cần ghép. Ngày 26/10 tích hợp và sửa lỗi xuyên vai trò. Ngày 27/10 dành cho nghiệm thu và bản chốt. Đây là thứ tự công việc, không phải cam kết hai API chưa chọn nhà cung cấp chắc chắn kịp hoàn tất.
