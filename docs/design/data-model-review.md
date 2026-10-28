# Thiết kế dữ liệu BroomSharing theo code hiện tại

Đối chiếu ngày 23/10/2026. Nguồn ưu tiên là Model, Repository, BookingPolicy, Firestore Rules và cấu hình local. Dataset chỉ bổ sung biến thể đã thấy. Không coi toàn bộ hợp trường là schema bắt buộc và không khẳng định trạng thái cloud hiện tại.

## 1. Bộ tài liệu thống nhất

- [Handoff schema và ràng buộc](firestore-visualization-handoff.md).
- [5 trang native và hướng dẫn xuất báo cáo](drawio/README.md).
- [Toàn bộ trường và quan hệ](drawio/field-and-relationship-dictionary.md).
- [Kết quả xử lý kiểm định](drawio/diagram-audit-resolution.md).

Bản này mô tả cách app đang lưu dữ liệu, không đề xuất collection mới hoặc migration. Các trao đổi/đề xuất thiết kế trước không được xem là tính năng đã triển khai.

## 2. Cấu trúc lưu Firestore

Database local cấu hình là `broomsharing`. Java app tham chiếu 13 collection gốc: users, publicHosts, usernames, hostApplications, buildings, rooms, bookings, availabilityMonths, conversations, propertyVerifications, reports, auditLogs, rentalPlaces.

Ba subcollection:

```text
users/{uid}/favorites/{roomId}
users/{uid}/noticeReads/{eventId}
conversations/{guestUid:hostUid}/messages/{messageId}
```

`payments` và `notifications` chỉ có contract trong Rules, chưa có Repository tương ứng trong checkout. Tổng kiểm kê là 18 mẫu document, gồm cả hai contract này. Không suy ra chúng có dữ liệu hoặc luồng tiền/thông báo đã hoạt động.

Building, Room, Booking và AvailabilityMonth là các root collection ngang cấp. Quan hệ buildingId/roomId/monthKeys không làm collection này thành subcollection của collection kia. Chỉ document mới chứa subcollection. `blackoutRanges`, `priceSnapshot`, `hourlyBookings`, `before/after` là dữ liệu nhúng/động.

## 3. Thực thể, dữ liệu nhúng và class giao diện

Có 13 Model lưu độc lập được rà soát: UserProfile, PublicHost, HostApplication, Building, Room, Booking, AvailabilityMonth, Conversation, ChatMessage, Favorite, RoomReport, AuditLog, RentalPlace. `usernames`, `noticeReads`, `propertyVerifications` có code/contract document nhưng không có Model đầy đủ tương ứng riêng.

RoomBlackout là phần tử map trong array `rooms.blackoutRanges`. BookingNotice, HostStats, MapPoint, MapRoute, NearbyPlace, ReviewItem, RoomListing, UiState, UserRole không phải collection độc lập hiện tại. Không thêm reviews vì có ReviewItem; không tạo collection cho ViewModel/Activity/Adapter.

Guest, Host và Admin là giá trị `users.role`. Host có thể thực hiện nghiệp vụ người thuê. PublicHost là bản chiếu cùng UID, không phải tài khoản thứ hai và không phải quyền đọc ẩn danh.

## 4. Quan hệ logic và snapshot

ID được lưu bằng string, không phải DocumentReference. Mỗi Room liên hệ một Building bằng buildingId và phải cùng ownerUid trong luồng ghi. Mỗi Booking liên hệ một Room, một người thuê và một Host khác người thuê. Một Room có thể có nhiều Booking qua thời gian; đây là quan hệ logic giữa root collection.

Favorite liên hệ Room; UID chủ Favorite nằm trong đường dẫn cha. User có tối đa một document đơn Host và một bản chiếu PublicHost theo cách đặt ID hiện tại. Hội thoại dùng ID có thứ tự guestUid:hostUid; không tạo hội thoại cho từng booking. Tin nhắn nằm trong subcollection của chính document hội thoại.

Toàn bộ bội số, điều kiện và liên kết kỹ thuật/quản trị được ghi ở R01–R46 trong từ điển. Audit.targetPath là đích động; không ép thành FK cố định và không bảo đảm mọi đích tồn tại. eventId của noticeReads là định danh dẫn xuất, không phải FK notifications.

Các snapshot cần giữ riêng ý nghĩa: rooms.buildingName/address; bookings.roomName/roomAddress/guestName/guestPhone/priceSnapshot; favorites.roomName; conversations.guestName/hostName/roomName; messages.roomName. Không suy ra chúng tự đồng bộ khi nguồn thay đổi, cũng không tạo đối tượng mới từ trường tên.

## 5. Booking và lịch theo code

Room là đơn vị thuê độc quyền theo khoảng `[startAt,endAt)`. capacity mô tả sức chứa, không phải tồn kho ghế. PENDING đã giữ lịch. Booking, lịch tháng, revision phòng và audit được ghi cùng transaction khi tạo; hủy/từ chối/hoàn tất nhả khóa tương ứng, không xóa booking lịch sử.

HOURLY dùng slot kỹ thuật 30 phút và một tài liệu tháng trong luồng hiện có. FIXED giữ trọn một hoặc ba tháng. monthIndex là số YYYYMM. workingDays/slotMinutes/minBookingMinutes là cấu hình chọn lịch; slotMinutes không đồng nghĩa bước slot kỹ thuật. pricePerDay không chứng minh có luồng thuê ngày.

Luồng trạng thái chính: PENDING → CONFIRMED → CHECKED_IN → COMPLETED; REJECTED từ PENDING; CANCELLED từ PENDING/CONFIRMED trước giờ bắt đầu theo điều kiện code/Rules. Không mô tả tự chuyển trạng thái chỉ vì thời gian trôi qua.

BookingPolicy tạo schemaVersion 2, paymentStatus UNAVAILABLE, khoản trả trước dự kiến 40%. Model mặc định schemaVersion 1/UNPAID và code nhận diện giá trị cũ DEMO_PENDING; phải phân biệt với giá trị luồng tạo mới. Room.deposit là trường cũ. Chưa tích hợp thu/hoàn tiền; hủy hiện ghi refundAmount=0, refundStatus=NOT_REQUIRED. Tỷ lệ chính sách không chứng minh tiền đã được hoàn.

## 6. Chat, cập nhật và xác minh

Gửi chat ghi message, preview/sequence và received counter trong transaction. Chỉ bên nhận tăng read counter của mình. Admin không mặc định đọc chat riêng. roomId/bookingId của hội thoại là ngữ cảnh khởi tạo; tin nhắn có thể mang ngữ cảnh phòng khác thuộc Host đó.

BookingNotice là dữ liệu dẫn xuất. noticeReads chỉ lưu readAt; không chứa nội dung hoặc người gửi. Collection notifications không phải nguồn hộp cập nhật hiện tại.

PropertyVerification có code Admin đọc/duyệt và Rules tạo PENDING; chưa suy ra phần nộp hồ sơ Host đã hoàn thiện. Admin đọc evidenceRefs, dự phòng evidencePath. evidencePaths/reviewed là biến thể dataset, không coi tương đương với contract đọc. photoUrl/hostResponse chỉ có quyền update trong Rules chưa đủ để khẳng định đã có luồng app ghi.

## 7. Dịch vụ ngoài và quyền

Authentication quản lý danh tính, mật khẩu và phiên; users liên hệ bằng UID. Storage giữ ảnh phòng/tòa nhà và bằng chứng riêng tư. Không lưu file nhị phân, password hoặc token vào sơ đồ document.

Storage Rules hiện chỉ cho chủ tệp đọc verification; AdminRepository có đường đọc nhưng quyền Admin ở Storage chưa hoàn tất. Không vẽ luồng kiểm chứng tệp như đã nghiệm thu. RentalPlace là địa điểm tham khảo, không phải listing đặt được.

Index hiện có ba khai báo local: rooms(approvalStatus,listingStatus), bookings(ownerUid,status), bookings(roomId,endAt), đều ASC/COLLECTION; fieldOverrides rỗng. Không dùng cấu hình local làm chứng cứ index cloud đã triển khai. Bản đồ lọc khoảng cách phía app, chưa có geohash.

## 8. Phạm vi xác nhận

225 dòng định nghĩa gồm trường lồng/key động, không phải 225 trường bắt buộc. S-only/D-only và unknown/any được ghi rõ trong từ điển. Các hình chính dùng bố cục cũ với đầy đủ trường; từ điển Markdown giữ giải thích nguồn và toàn bộ quan hệ. Bộ hình chọn lọc theo bố cục bảng mới đã bị loại theo yêu cầu người dùng.

Các kiểm tra tên Model/dataset và chuyển đổi native là bằng chứng tĩnh có giới hạn. Không có lượt đọc cloud hoặc thay đổi schema, Rules, dữ liệu và logic app khi chỉnh bộ sơ đồ này.
