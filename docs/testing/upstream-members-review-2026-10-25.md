# Đối chiếu nhánh thành viên trước khi push, 25/10/2026

Đã fetch và đọc nguồn từ GitHub, chưa merge/pull vào working tree và chưa push trong lượt kiểm tra này theo yêu cầu tạm dừng để đối chiếu. Bản local dùng HEAD `9a2c214` cộng các thay đổi đã nghiệm thu ngày 24/10. `origin/main` là `3c39dcf`, `origin/truc` là `693688e`, `origin/lam-hoang` là `c74d963`. Bản ghi có hash từng file tại [receipt](evidence/upstream-truc-sprint3-2026-10-25.json).

## Nhánh Trúc: 12 file Sprint 3

| Phần | Nguồn mới trên GitHub | Đối chiếu bản tích hợp | Hướng xử lý |
| --- | --- | --- | --- |
| Inbox | `MessagesActivity` query `participantUids`, preview `lastMessage` | `ChatRepository` dùng `memberUids`, `lastText`, counters và sequence | Không chép query/schema cũ; Trúc dùng `ui/chat/ConversationsActivity` khi hoàn thiện UI. |
| Gửi chat | Ghi message có sender/text/createdAt rồi update preview ở lần ghi khác | Rules hiện buộc message ID/sequence, preview/counters và timestamp cùng transaction | Không thay Repository đã nghiệm thu. Nhánh mới chưa tương thích Rules đang triển khai. |
| Thông báo | Đọc collection `notifications`, ghi cả `read=true` và `readAt` | App đang tổng hợp booking/chat/report, receipt ở `users/{uid}/noticeReads`; contract `notifications` chỉ cho đổi `readAt` | Không nối màn này trực tiếp; dùng `BookingNoticeViewModel` và màn cập nhật đã có. |
| Xác nhận/lưu lịch | Đọc toàn bộ nội dung từ Intent, mô tả lịch ghi cố định “Chờ thanh toán” | Màn chi tiết đọc booking server; chỉ cho lưu lịch khi CONFIRMED/CHECKED_IN và chưa kết thúc | Giữ bản hiện tại. Fallback web Calendar có thể tham khảo sau, URL ở commit mới chưa điền dữ liệu sự kiện. |
| Bản đồ | Danh sách phòng có tọa độ, mở chỉ đường/tìm kiếm Google Maps ngoài app | MapLibre trong app đã có marker, GPS/chọn tay, tuyến và POI; vẫn có Maps Intent dự phòng | Không đổi lại màn danh sách hoặc thêm SDK chưa dùng. |
| Gradle/Manifest | Thêm Maps/Location, Lifecycle chỉ định riêng và key placeholder | Dependency/BuildConfig hiện đã theo dịch vụ bản đồ đang dùng | Không lấy dependency hoặc key placeholder. |
| XML | Parser phát hiện `activity_chat.xml` có XML declaration ở dòng 2 | Các layout tương ứng hiện có schema/binding khác | Cần sửa XML nếu tái sử dụng. Đây là kết quả parse nguồn, không phải tuyên bố đã chạy Gradle của nhánh Trúc. |

Các chức năng Trúc đang triển khai ở commit này đã có bản tương ứng trong app tích hợp. Chưa thấy đoạn nghiệp vụ nào nên thay trực tiếp cho bản đã nghiệm thu. Có thể tham khảo cách trình bày thẻ/tin nhắn, nhưng phải dùng resource, ViewModel/Repository và schema hiện tại. Kết luận này chỉ áp dụng cho các nguồn đã fetch, không suy ra nhánh thành viên không còn công việc hoặc bản local không còn lỗi.

## Nhánh Hoàng: 20 file ở c74d963

| Phần | Giá trị có thể lấy | Điểm cần chỉnh trước khi ghép |
| --- | --- | --- |
| Lịch tháng | Dialog lưới tháng, chọn phòng, đổi tháng và xem đơn theo ngày | Điều kiện đang nhận cả ngày `endAt`; phải theo `[startAt,endAt)`. Ví dụ kỳ 01/10 đến 01/11 không chiếm ngày 01/11. Phân biệt EXPIRED/đơn đã nhả lịch với chỗ còn giữ. |
| Hồ sơ xác minh | Model, form chọn phòng/tòa nhà, chọn bằng chứng, danh sách hồ sơ và trạng thái | Ghi `evidencePaths` nhưng Admin đọc `evidenceRefs` hoặc `evidencePath`. Upload đang dùng Firebase Storage; cần nối API tệp mới của Phúc, quyền đọc Admin, lỗi upload/lưu document và dọn tệp. |
| Báo cáo Host | RecyclerView, thẻ báo cáo và dialog giải trình có thể dùng để làm giao diện dễ đọc hơn | Dùng `ReportRepository.reply` hiện có: OPEN, nội dung 10–2.000 ký tự, `hostRespondedAt` và `updatedAt` server. Không thay bằng update trực tiếp chỉ có hai trường cũ. |
| Check-in, biên nhận, hủy | Có thêm phần trình bày nghiệp vụ phía Host | Giữ kiểm thời gian, EXPIRED, khóa quyền và transaction của bản hiện tại. Tỷ lệ chính sách không phải bằng chứng đã hoàn tiền; chưa nối payment thì không coi biên nhận là đã thu tiền. |
| Rules/Repository | Có thêm quyền tạo hồ sơ và phản hồi báo cáo | Không thay toàn bộ Rules bằng bản nhánh; bản tích hợp có thêm validation, audit, vòng đời booking và counter đã kiểm thử. |

Phần lịch tháng và form hồ sơ xác minh là bổ sung thực sự so với UI local. Nên lấy có chọn lọc sau khi cập nhật các hợp đồng ở trên. Không đánh dấu H20/H22 hoàn tất chỉ vì đã có file nguồn; cũng không ghi là Hoàng chưa làm các màn này. Chưa chạy thiết bị/cloud cho riêng nhánh Hoàng trong lượt đọc nguồn này.

## Chốt trước khi cập nhật GitHub

Giữ bản tích hợp làm nền cho booking, chat, thông báo, Rules và bản đồ. Bảo toàn commit của các thành viên khi hợp nhất lịch sử, chỉ ghép phần bổ sung đã chỉnh và nghiệm thu. Không force-push đè `main`, không đưa các Activity gốc cũ quay lại cạnh package `ui/*`, không tạo hai luồng cùng ghi một nghiệp vụ.

Phần API lưu ảnh/tệp và payment do Phúc tiếp tục bằng dịch vụ bên thứ ba theo xác nhận mới của chủ dự án. Phần phân công và tự nghiệm thu ở [bàn giao giai đoạn 3–4](phase3-phase4-handoff-2026-10-25.md). Các file LaTeX, cấu hình máy, mật khẩu local, APK và log sinh tự động không nằm trong phạm vi đẩy code app của lượt này.
