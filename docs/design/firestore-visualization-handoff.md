# BroomSharing — Firestore Schema and Relationships Visualization

Ngày đối chiếu: 23/10/2026. Database: `broomsharing`. Project: `broom-sharing-firebase-mobile`.

## 1. Phạm vi và hướng dẫn cho công cụ visualize

Hãy dựng sơ đồ cấu trúc Firestore BroomSharing theo toàn bộ đặc tả dưới đây. Giữ nguyên tên collection và trường, kể cả chữ hoa/thường. Tách rõ Collection → Document → Subcollection → Document; map/array là dữ liệu nhúng, không phải collection. Giữ bố cục 5 trang với các thẻ đầy đủ trường của bản cũ làm bản chính; chỉ chỉnh nhãn/chú thích hoặc bổ sung có căn cứ, không thay bằng bảng trường chọn lọc. Quan hệ chứa dùng nét liền; liên kết ID nếu vẽ phải dùng nét đứt. Bội số là số đích logic của mỗi bản ghi nguồn, không phải khóa ngoại tự động. Toàn bộ 46 đặc tả quan hệ có trong từ điển Markdown; các hình chính hiện chưa vẽ tất cả những liên kết đó thành đường nối. SVG/PDF giữ nét khi zoom, nhưng không khẳng định fit toàn bộ một trang rộng xuống 16 cm sẽ đọc được.

- Nguồn xác nhận: mã Java hiện tại (Model, Repository, BookingPolicy), `firestore.rules`, `firestore.indexes.json`, `firebase.json`, dataset local và công cụ tạo dataset. Đây là kiểm kê cấu trúc được các nguồn này thể hiện, **không phải bản quét trực tiếp toàn bộ cloud**. Document do người khác tạo ngoài checkout có thể có trường khác; không khẳng định không còn trường nào trên cloud.
- Có **13 collection gốc được Repository tham chiếu**, **3 loại subcollection** và **2 collection chỉ có contract trong Rules**. Tức 18 mẫu đường dẫn document cần biểu diễn, trong đó 16 mẫu thuộc nhóm Repository tham chiếu. “Được tham chiếu” không có nghĩa mọi luồng đã hoàn thiện.
- Không đưa mật khẩu, token, API key hay dữ liệu liên hệ thật vào sơ đồ. Chỉ thể hiện schema và ID ký hiệu.
- Không tự thêm `displayName`, `bio`, `stats`, `followers`, `posts`, `isVerified`, `avatarUrl` chỉ vì có trong ảnh minh họa của công cụ khác. Hồ sơ app hiện dùng `name`; `photoUrl` chỉ có trong quyền cập nhật Rules.
- Không tạo ba collection Guest/Host/Admin: đây là ba giá trị `users.role`; Host cũng có thể thực hiện quyền thuê.
- Dữ liệu tiền/phương thức thanh toán chưa tích hợp: không tự vẽ Payment/Refund/Payout thành luồng đang hoạt động.
- ID được lưu bằng `string`, không đổi thành Firestore `DocumentReference`. Những mũi tên dưới đây là quan hệ logic, không phải khóa ngoại tự động.
- Ký hiệu nguồn: **M** = Model Java; **W** = code ghi/update; **R** = code đọc; **S** = Security Rules; **D** = dataset/công cụ tạo dữ liệu. Nguồn S đơn lẻ không chứng minh trường đã có document thật. Các trường phát sinh sau duyệt/hủy có thể chưa tồn tại ở document mới; không mặc định mọi trường đều bắt buộc.
- Kiểu `integer`/`double` đều thuộc nhóm số Firestore nhưng được ghi riêng cho chính xác. `number` là số có thể khác kiểu giữa code/dataset. `unknown` nghĩa nguồn hiện tại không đủ xác định kiểu được bảo vệ, không tự ép kiểu. `timestamp` được Java ánh xạ bằng Date/Timestamp; có thể null trước khi server giải quyết giá trị server timestamp.

## 2. Cây lưu trữ đầy đủ

```text
Firebase Authentication                 [dịch vụ ngoài Firestore]
Firestore: broomsharing
├── users/{uid}
│   ├── favorites/{roomId}
│   └── noticeReads/{eventId}
├── publicHosts/{uid}
├── usernames/{normalizedUsername}
├── hostApplications/{uid}
├── buildings/{buildingId}
├── rooms/{roomId}
├── bookings/{bookingId}
├── availabilityMonths/{roomId_yyyyMM}
├── conversations/{guestUid:hostUid}
│   └── messages/{messageId}
├── propertyVerifications/{verificationId}
├── reports/{reportId}
├── auditLogs/{auditId}
├── rentalPlaces/{placeId}
├── payments/{paymentId}                 [Rules-only; chưa có schema đầy đủ]
└── notifications/{notificationId}       [Rules-only; chưa có schema đầy đủ]
Firebase Storage                        [dịch vụ ngoài Firestore]
├── roomMedia/{uid}/{roomId}/{file}
├── buildingMedia/{uid}/{buildingId}/{file}
└── verification/{uid}/{file}
```

Các phần dưới liệt kê từng trường riêng để công cụ có thể tạo thẻ dữ liệu trực tiếp.

## 3. Danh mục document và trường

### 3.1. `users/{uid}`

Hồ sơ riêng. Document ID là Firebase Authentication UID. Nguồn: UserProfile, FirebaseAuthRepository, AdminRepository, ReportRepository, Rules, dataset.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| uid | string | M/W/D; cùng UID với document ID và Authentication |
| schemaVersion | integer | M/W/D; hiện 1 |
| name | string | M/W/D; tên hiển thị |
| username | string | M/W/D; username đã chuẩn hóa |
| email | string | M/W/D; email tài khoản |
| phone | string | M/W/D; số điện thoại, đăng ký chuẩn hóa dạng quốc tế |
| phoneVerified | boolean | M/W/D; mặc định false; không phải xác minh email |
| role | string | M/W/D; GUEST, HOST, ADMIN |
| status | string | M/W/D; ACTIVE, BLOCKED |
| createdAt | timestamp | W/D; thời gian server khi đăng ký |
| updatedAt | timestamp | W/D; lần cập nhật |
| moderationReason | string | W/D; lý do quản trị, phát sinh khi xử lý |
| moderatedBy | string | W; UID Admin xử lý |
| photoUrl | unknown | S-only; tên trường được phép cập nhật, dự kiến URL string nhưng Rules không kiểm tra kiểu và chưa có luồng ghi tương ứng trong Repository |

Subcollections: `favorites`, `noticeReads`. Không có trường password. Không biến helper `getUserRole()` thành trường dữ liệu: có `@Exclude`.

### 3.2. `users/{uid}/favorites/{roomId}`

Phòng yêu thích của đúng tài khoản cha. ID roomId giúp không lưu hai document yêu thích trùng phòng trong cùng tài khoản. Nguồn: Favorite, FavoritesRepository, Rules, dataset.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| roomId | string | M/W/D; liên kết rooms |
| roomName | string | M/W/D; snapshot tên lúc lưu |
| createdAt | timestamp | M/W/D; thời gian lưu |

UID chủ sở hữu lấy từ đường dẫn cha; không tự thêm `userId` vào document.

### 3.3. `users/{uid}/noticeReads/{eventId}`

Receipt đã đọc, không chứa nội dung thông báo. Nguồn: NoticeRepository, Rules. ID sự kiện được dựng từ booking/trạng thái/mốc chính sách; không phải document ID của collection notifications.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| readAt | timestamp | W/S; thời gian server xác nhận đã đọc |

### 3.4. `publicHosts/{uid}`

Bản chiếu tối thiểu của Host cho người dùng đang hoạt động; không phải tài khoản thứ hai. Nguồn: PublicHost, FirebaseAuthRepository, AdminRepository, ReportRepository, Rules, dataset.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| uid | string | M/W/D; liên kết users cùng UID |
| name | string | M/W/D; tên Host |
| status | string | M/W/D; đồng bộ trạng thái users, ACTIVE/BLOCKED |

Không thêm email, phone, ảnh giấy tờ, createdAt hoặc schemaVersion vào contract này: Rules chỉ cho đúng ba trường trên. “Public” ở đây không có nghĩa truy cập ẩn danh; Rules yêu cầu active().

### 3.5. `usernames/{normalizedUsername}`

Chỉ mục username → tài khoản/email để đăng nhập. ID lowercase theo normalizeUsername, dạng `[a-z0-9_]{3,24}`. Nguồn: FirebaseAuthRepository, Rules, dataset.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| uid | string | W/D; liên kết users/Authentication |
| email | string | W/D; email đưa vào Firebase Authentication sau lookup |

Username nằm trong ID document, không tự thêm field username. Rules cho get trước đăng nhập; đây là điểm cần mô tả khi nói về quyền và lộ email qua lookup.

### 3.6. `hostApplications/{uid}`

Đơn xin quyền Host. Một tài khoản tối đa một document theo cách đặt ID hiện tại; nộp lại sau từ chối thay document cùng ID, không phải lịch sử nhiều đơn. Nguồn: HostApplication, HostRepository, AdminRepository, Rules, dataset.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| id | string | M/W/D; cùng UID/document ID |
| schemaVersion | integer | M/W/D; hiện 1 |
| applicantUid | string | M/W/D; người nộp, liên kết users |
| fullName | string | M/W/D; họ tên khai trong đơn |
| phone | string | M/W/D; số liên hệ |
| email | string | M/W/D; email liên hệ |
| organizationType | string | M/W/D; loại cá nhân/tổ chức |
| identityDoc | string | M/W/D; thông tin giấy tờ dạng chuỗi, không phải file nhị phân |
| status | string | M/W/D; PENDING, APPROVED, REJECTED |
| rejectionReason | string | M/W/D; lý do/kết quả duyệt, có thể rỗng |
| createdAt | timestamp | M/W/D; thời gian tạo |
| updatedAt | timestamp | M/W/D; thời gian cập nhật |
| reviewedBy | string | W; UID Admin duyệt |

### 3.7. `buildings/{buildingId}`

Tòa nhà/địa điểm thuộc một Host. Nguồn: Building, HostRepository, Rules, dataset.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| id | string | M/W/D; document ID |
| schemaVersion | integer | M/W/D; hiện 1 |
| ownerUid | string | M/W/D; Host sở hữu, liên kết users |
| name | string | M/W/D; tên tòa nhà |
| address | string | M/W/D; địa chỉ |
| district | string | M/W/D; khu vực/quận |
| contactPhone | string | M/W/D; số liên hệ địa điểm |
| contactName | string | M/W/D; người liên hệ |
| latitude | double | M/W/D; vĩ độ |
| longitude | double | M/W/D; kinh độ |
| location | geopoint/null | M/W; getter getLocation() có thể được serialize khi ghi POJO; dataset cũ chỉ có latitude/longitude. Không coi cả hai biểu diễn là luôn hiện diện hoặc tự đồng bộ ngoài getter/setter Java |
| directionGuide | string | M/W/D; hướng dẫn đến/lối vào |
| coverUrl | string | M/W/D; URL ảnh bìa |
| status | string | M/W/D; mặc định ACTIVE; luồng hiện tại không xác định thêm enum hoàn chỉnh |
| createdAt | timestamp | M/W/D; thời gian tạo |
| updatedAt | timestamp | M/W/D; thời gian cập nhật |

`locationSet` là biến private hỗ trợ getter, không có getter lưu riêng; không thêm thành field Firestore.

### 3.8. `rooms/{roomId}`

Đơn vị không gian cho thuê độc quyền theo khoảng thời gian. capacity là sức chứa, không phải số chỗ còn lại. Nguồn: Room, RoomBlackout, HostRepository, BookingRepository, AdminRepository, ReportRepository, Rules, dataset.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| id | string | M/W/D; document ID |
| schemaVersion | integer | M/W/D; hiện 1 |
| buildingId | string | M/W/D; liên kết buildings |
| buildingName | string | M/W/D; tên tòa nhà sao chép |
| ownerUid | string | M/W/D; Host, phải khớp chủ Building |
| roomName | string | M/W/D; tên không gian |
| roomType | string | M/W/D; loại không gian, ví dụ bàn/phòng riêng/phòng họp/mặt bằng |
| capacity | integer | M/W/D; sức chứa |
| area | double | M/W/D; diện tích m² |
| floor | string | M/W/D; tầng |
| address | string | M/W/D; địa chỉ sao chép |
| activeMode | string | M/W/D; HOURLY hoặc FIXED |
| pricePerHour | double | M/W/D; giá thuê giờ VND |
| pricePerDay | double | M/W/D; trường cũ, không suy ra có luồng đặt theo ngày |
| pricePerMonth | double | M/W/D; giá tháng VND |
| pricePerQuarter | double | M/W/D; giá quý VND |
| deposit | double | M/W/D; trường cọc cũ; không dùng để tính khoản trả trước 40% của booking mới |
| openTime | string | M/W/D; HH:mm |
| closeTime | string | M/W/D; HH:mm |
| amenities | array<string> | M/W/D; tiện ích |
| rules | string | M/W/D; nội quy |
| approvalStatus | string | M/W/D; DRAFT, PENDING_REVIEW, APPROVED, REJECTED |
| listingStatus | string | M/W/D; ACTIVE, INACTIVE, SUSPENDED |
| imageUrls | array<string> | M/W/D; URL ảnh, Rules giới hạn tối đa 6 |
| blackoutDates | array<string> | M/W/D; ngày khóa YYYY-MM-DD |
| blackoutRanges | array<map> | M/W/D; khoảng khóa lịch nhúng, không phải collection |
| blackoutRanges[].startAt | timestamp | M; bắt đầu khoảng khóa |
| blackoutRanges[].endAt | timestamp | M; kết thúc loại trừ |
| blackoutRanges[].reason | string | M; lý do khóa |
| workingDays | array<integer> | M/W/D; thứ ISO 1..7 |
| slotMinutes | integer | M/W/D; bước chọn giờ; không đồng nhất với slot kỹ thuật 30 phút |
| minBookingMinutes | integer | M/W/D; thời lượng thuê tối thiểu |
| fixedStartMonth | string | M/W/D; tháng nhận thuê cố định YYYY-MM |
| renewalPolicy | string | M/W/D; chính sách gia hạn |
| rentalPolicy | string | M/W/D; chính sách thuê |
| locationVerification | string | M/W/D; mặc định UNVERIFIED, luồng Admin có thể ghi ADMIN_REVIEWED; không tự thêm trạng thái đối tác |
| photosVerification | string | M/W/D; như locationVerification cho ảnh |
| rejectionReason | string | M/W/D; lý do/kết quả xét duyệt |
| bookingRevision | integer | M/W/D; tăng khi thay đổi booking/lịch liên quan |
| lastBookingId | string | M/W/D; booking gần nhất tác động lịch/phòng, rỗng khi chưa có |
| createdAt | timestamp | M/W/D; thời gian tạo |
| updatedAt | timestamp | M/W/D; lần cập nhật |
| reviewedBy | string | W; UID Admin xét duyệt tin |
| moderationReason | string | W; lý do đình chỉ/gỡ đình chỉ |
| moderatedBy | string | W; UID Admin quản trị |

Trạng thái tin đăng không đồng nghĩa phòng bận mọi ngày. Phải đối chiếu lịch cho khoảng thời gian cần thuê.

### 3.9. `bookings/{bookingId}`

Booking mới ID = SHA-256(uid + ':' + requestKey), 64 ký tự hex. requestKey chỉ tồn tại phía app và có @Exclude. Nguồn: Booking, BookingPolicy, BookingRepository, Rules, dataset.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| id | string | M/W/D; document ID |
| schemaVersion | integer | M/W/D; class mặc định 1, luồng tạo mới ghi 2 |
| roomId | string | M/W/D; liên kết rooms |
| roomName | string | M/W/D; snapshot tên phòng |
| roomAddress | string | M/W/D; snapshot địa chỉ |
| ownerUid | string | M/W/D; Host của booking |
| guestUid | string | M/W/D; người thuê; tài khoản role HOST cũng có thể thuê |
| guestName | string | M/W/D; snapshot tên Guest |
| guestPhone | string | M/W/D; snapshot liên hệ Guest |
| status | string | M/W/D; PENDING, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, REJECTED |
| paymentStatus | string | M/W/D; booking mới UNAVAILABLE; UNPAID/DEMO_PENDING là giá trị mặc định/cũ được code nhận diện, không phải thanh toán thật |
| activeMode | string | M/W/D; HOURLY/FIXED |
| rejectionReason | string | M/W/D; lý do từ chối hoặc hủy, có thể rỗng |
| startAt | timestamp | M/W/D; thời điểm bắt đầu |
| endAt | timestamp | M/W/D; thời điểm kết thúc loại trừ |
| totalAmount | double | M/W/D; tổng tiền thuê VND |
| priceSnapshot | map | M/W/D; chụp giá/chính sách tại lúc đặt |
| priceSnapshot.currency | string | W/D; VND |
| priceSnapshot.rentalAmount | number | W/D; tiền thuê |
| priceSnapshot.feeAmount | integer | W/D; hiện 0 |
| priceSnapshot.depositAmount | number | W/D; khoản trả trước dự kiến, booking mới 40%; code mới ghi số nguyên, dataset cũ có double |
| priceSnapshot.advancePercent | integer | W; booking mới 40; dataset cũ có thể thiếu |
| priceSnapshot.cancellationPolicyVersion | integer | W; booking mới 1; dataset cũ có thể thiếu |
| priceSnapshot.totalAmount | number | W/D; tổng tiền |
| priceSnapshot.paymentDueAmount | number | W/D; khoản phải trả trước dự kiến |
| priceSnapshot.rentalPolicy | string | W/D; snapshot chính sách thuê |
| priceSnapshot.renewalPolicy | string | W/D; snapshot chính sách gia hạn |
| rentalMonths | integer | M/W/D; HOURLY=0, FIXED=1 hoặc 3 |
| slotStart | integer | M/W/D; slot 30 phút đầu trong tháng, FIXED=0 |
| slotCount | integer | M/W/D; số slot 30 phút liên tiếp, FIXED=0 |
| monthKeys | array<string> | M/W/D; ID availabilityMonths, 1 hoặc 3 phần tử |
| slotKeys | array<integer> | M/W/D; danh sách slot giữ, FIXED rỗng |
| requestFingerprint | string | M/W/D; SHA-256 lựa chọn phòng/thời gian |
| cancelledAt | timestamp/null | M/W; mặc định null, khi hủy là server timestamp; dataset cũ có thể thiếu |
| cancelledByUid | string | M/W; UID bên hủy, mặc định rỗng |
| cancellationRefundPercent | integer | M/W; tỷ lệ chính sách 0/50/100, không xác nhận đã chuyển tiền |
| refundAmount | number | M/W; mặc định double 0, thao tác hủy hiện ghi integer 0 vì chưa thu tiền |
| refundStatus | string | M/W; hiện NOT_REQUIRED |
| createdAt | timestamp | M/W/D; thời gian tạo |
| updatedAt | timestamp | M/W/D; lần cập nhật |

Không vẽ requestKey thành field lưu. Snapshot giá cũ không bị sửa theo giá phòng mới. Chưa có expiresAt, paymentId, paidAt, transactionId, gateway hay hold thanh toán tự hết hạn.

### 3.10. `availabilityMonths/{roomId_yyyyMM}`

Lịch kỹ thuật chống đặt trùng dùng chung HOURLY/FIXED. Nguồn: AvailabilityMonth, BookingPolicy, BookingRepository, Rules, dataset.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| roomId | string | M/W/D; liên kết rooms |
| monthIndex | integer | M/W/D; số YYYYMM, không phải chỉ số tháng 0..11 |
| fixedBookingId | string | M/W/D; booking giữ toàn tháng; rỗng nếu không có |
| slotsBusy | array<integer> | M/W/D; tập slot kỹ thuật 30 phút đang giữ |
| hourlyBookings | map<string,array<integer>> | M/W/D; key động là bookingId, value là các slot booking đó giữ |
| hourlyBookings.{bookingId} | array<integer> | M/W/D; liên kết theo key, không phải subcollection |
| lastBookingId | string | M/W/D; booking vừa tác động lịch |
| updatedAt | timestamp | M/W/D; thời gian server |

Slot = (ngày trong tháng - 1) * 48 + phút trong ngày / 30. Các ngày/giờ nghiệp vụ tính theo Asia/Ho_Chi_Minh, thời điểm lưu timestamp. Không có field id/schemaVersion/createdAt trong contract lịch hiện tại.

### 3.11. `conversations/{guestUid:hostUid}`

Một hội thoại/cặp vai trò có thứ tự; không phải một hội thoại/booking. Nguồn: Conversation, ChatRepository, Rules.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| id | string | M/W; guestUid + ':' + hostUid |
| guestUid | string | M/W; liên kết users bên thuê |
| hostUid | string | M/W; liên kết users bên cho thuê |
| guestName | string | M/W; snapshot tên bên thuê |
| hostName | string | M/W; snapshot tên Host |
| memberUids | array<string> | M/W; đúng hai UID [guestUid, hostUid], dùng query inbox |
| roomId | string | M/W; phòng ngữ cảnh khởi tạo |
| roomName | string | M/W; tên phòng ngữ cảnh |
| bookingId | string | M/W; booking ngữ cảnh khởi tạo hoặc chuỗi rỗng nếu mở từ phòng |
| lastText | string | M/W; preview tin cuối, mới tạo rỗng |
| lastSenderUid | string | M/W; người gửi cuối hoặc rỗng |
| lastMessageId | string | M/W; ID tin cuối trong subcollection hoặc rỗng |
| sequence | integer | M/W; số thứ tự tin gần nhất, khởi tạo 0 |
| guestReceived | integer | M/W; tổng số tin đến Guest |
| hostReceived | integer | M/W; tổng số tin đến Host |
| guestRead | integer | M/W; mốc số tin đến Guest đã đọc |
| hostRead | integer | M/W; mốc số tin đến Host đã đọc |
| createdAt | timestamp | M/W; thời gian tạo |
| updatedAt | timestamp | M/W; cập nhật khi gửi tin |

Subcollection `messages`. Chưa đọc được tính max(0, received - read), không có field unreadCount riêng. Hàm unread/received/peerName không phải field. Khi mở lại cặp đã có, không mặc định roomId/bookingId ở document cha tự thay theo mỗi lần mở; ngữ cảnh từng tin nằm ở messages.

### 3.12. `conversations/{guestUid:hostUid}/messages/{messageId}`

ID UUID của tin được giữ lại khi retry. Nguồn: ChatMessage, ChatRepository, Rules.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| id | string | M/W; cùng messageId |
| senderUid | string | M/W; thành viên gửi, liên kết users |
| text | string | M/W; nội dung 1..2000 ký tự |
| roomId | string | M/W; phòng ngữ cảnh hoặc rỗng |
| roomName | string | M/W; snapshot tên phòng ngữ cảnh hoặc rỗng |
| sequence | integer | M/W; thứ tự tin trong conversation |
| createdAt | timestamp | M/W; thời gian server |

Không có receiverUid/conversationId riêng: hội thoại lấy từ đường dẫn cha. Chưa có file đính kèm hoặc sửa/xóa tin trong contract.

### 3.13. `propertyVerifications/{verificationId}`

Hồ sơ xác minh theo loại; ID không có quy tắc thống nhất được chứng minh bởi code nộp hiện tại. Nguồn: AdminRepository (đọc/duyệt), Rules (cho tạo), dataset. Luồng Host nộp hồ sơ và Admin đọc tệp chưa được chứng minh hoàn thiện trong checkout này.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| schemaVersion | integer | D; dữ liệu mẫu hiện 1 |
| ownerUid | string | R/S/D; Host nộp hồ sơ |
| verificationType | string | R/S/D; LOCATION, PHOTOS, IDENTITY |
| roomId | string | R/D; phòng cần xác minh; có thể rỗng/thiếu |
| buildingId | string | R/D; tòa nhà cần xác minh; có thể rỗng/thiếu |
| description | string | R/D; mô tả |
| evidenceRefs | array<string> | R/D; đường dẫn tệp, code ưu tiên đọc danh sách này |
| evidencePath | string | R; dạng đơn được code đọc dự phòng khi evidenceRefs không phải List |
| evidencePaths | array<unknown> | D-only; tên trường biến thể trong dataset, các mảng đang rỗng nên chưa xác định kiểu phần tử bằng dữ liệu; AdminRepository không đọc tên này |
| reviewed | boolean | D-only; cờ dữ liệu mẫu; không đồng nhất với reviewedBy/reviewedAt và không chứng minh Admin đã xem tệp |
| status | string | R/W/S/D; PENDING, VERIFIED, REJECTED |
| rejectionReason | string | W; lý do/kết quả duyệt |
| reviewedBy | string | W; UID Admin xử lý |
| method | string | W; ADMIN_REVIEWED |
| reviewNote | string | W; ghi chú xét duyệt |
| reviewedAt | timestamp | W; thời gian xét duyệt |
| partnerName | string | W; code ghi rỗng, không phải tích hợp đối tác |
| createdAt | timestamp | R/S/D; thời gian tạo |
| updatedAt | timestamp | W/D; lần cập nhật |

LOCATION/PHOTOS cần roomId hoặc buildingId; IDENTITY có thể chỉ gắn ownerUid. Code duyệt kiểm tra roomId trước, nếu không có mới xét buildingId: không vẽ cả hai như hai quan hệ luôn bắt buộc. Không thêm trường id nếu chưa có nguồn lưu field đó.

### 3.14. `reports/{reportId}`

Báo cáo phòng; ID được truyền từ app, giữ ổn định để gửi lại không tạo trùng. Nguồn: RoomReport, ReportRepository, Rules, dataset.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| id | string | M/W; cùng reportId; dataset cũ có thể thiếu |
| schemaVersion | integer | M/W/D; hiện 1 |
| reporterUid | string | M/W/D; người báo, liên kết users |
| roomId | string | M/W/D; phòng bị báo cáo |
| ownerUid | string | M/W/D; Host lấy từ dữ liệu phòng |
| reason | string | M/W/D; Sai thông tin / Ảnh không khớp / Nghi ngờ gian lận / Khác |
| description | string | M/W/D; mô tả 20..2000 ký tự |
| status | string | M/W/D; OPEN, RESOLVED, DISMISSED |
| createdAt | timestamp | M/W/D; thời gian tạo |
| updatedAt | timestamp | M/W/D; lần cập nhật |
| action | string | W; NO_ACTION, SUSPEND_ROOM, BLOCK_HOST |
| resolution | string | W; kết luận 10..1000 ký tự |
| reviewedBy | string | W; UID Admin xử lý |
| reviewedAt | timestamp | W; thời gian xử lý |
| hostResponse | unknown | S-only; contract cho Host cập nhật; dự kiến nội dung phản hồi nhưng Rules hiện không kiểm tra kiểu, chưa có UI/Repository phản hồi trong checkout |

DISMISSED chỉ đi với NO_ACTION. Không có evidenceUrls/attachments trong contract báo cáo hiện tại.

### 3.15. `auditLogs/{auditId}`

Nhật ký dùng ID tự sinh. Nguồn: AuditLog, BookingRepository, HostRepository, AdminRepository, ReportRepository, Rules, dataset.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| schemaVersion | integer | M/W/D; hiện 1 |
| actorUid | string | M/W/D; người thực hiện, liên kết users |
| targetPath | string | M/W/D; đường dẫn đối tượng, có thể là document hoặc đích tổng hợp của tác vụ dataset |
| action | string | M/W/D; mã hành động |
| before | map<string,any> | M/W/D; dữ liệu trước thay đổi, có thể rỗng |
| after | map<string,any> | M/W/D; dữ liệu sau thay đổi, có thể rỗng |
| reason | string | M/W/D; lý do |
| createdAt | timestamp | M/W/D; thời gian server |

Map before/after là cấu trúc động theo action, không phải hai collection. Các key đã thấy ở code/dataset gồm status, listingStatus, approvalStatus, action, users, buildings, rooms, bookings; không gán tất cả key này là bắt buộc trong mọi audit. Audit có thể chỉ lưu phần trạng thái đổi, không mặc định sao chụp đầy đủ document. Không có field id trong Model.

### 3.16. `rentalPlaces/{placeId}`

Địa điểm tham khảo công khai trên bản đồ, không phải phòng do Host trong app đăng và không đặt được. ID dataset dạng ref_hcm_*. Nguồn: RentalPlace, RoomRepository, RentalPlacesDataset, dữ liệu reference, Rules.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| id | string | M/D; document ID |
| name | string | M/D; tên địa điểm |
| address | string | M/D; địa chỉ nguồn |
| location | geopoint | M/D; vị trí từ nguồn tham khảo; không phải map collection |
| sourceUrl | string | M/D; URL tin nguồn |
| mapSourceUrl | string | M/D; URL bản đồ nguồn |
| sourceCoordinateLabel | string | M/D; nhãn tọa độ từ nguồn |
| locationNote | string | M/D; ghi chú giới hạn vị trí |
| checkedAt | timestamp | M/D; thời điểm đối chiếu nguồn |

GeoPoint có latitude/longitude trong biểu diễn tọa độ, không tự thêm thành hai field cấp document. Không thêm ownerUid/roomId/giá/trạng thái trống. Không nối rentalPlaces với rooms/buildings chỉ vì cùng khu vực.

### 3.17. `payments/{paymentId}` — Rules-only

Chưa có Repository/Model hoặc dataset được kiểm kê xác định schema đầy đủ. Chỉ vẽ thẻ phụ màu xám, không trộn với luồng thanh toán đã triển khai.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| guestUid | unknown | S-only; được dùng kiểm tra quyền chủ thanh toán, ngữ nghĩa là UID string nhưng không có validation/luồng ghi xác nhận kiểu |

Rules chỉ cho Admin hoặc người có UID bằng guestUid đọc; client không có quyền tạo/cập nhật trong match này. Không tự thêm bookingId, amount, provider, orderId hoặc refund vào schema đang có.

### 3.18. `notifications/{notificationId}` — Rules-only

Collection dự phòng trong Rules; không phải nguồn của hộp cập nhật hiện tại. Không đồng nhất với noticeReads.

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| recipientUid | unknown | S-only; dùng kiểm tra người nhận, ngữ nghĩa UID string nhưng chưa có schema ghi/validation kiểu |
| readAt | unknown | S-only; trường duy nhất được phép thay đổi; dự kiến timestamp nhưng Rules match này chưa kiểm tra kiểu |

Không có căn cứ thêm title, body, type, createdAt, bookingId vào collection này. Nội dung thông báo hiện dựng từ booking, chính sách và chat; đã đọc booking/policy lưu ở users/{uid}/noticeReads, đã đọc chat lưu bằng counter conversation.

## 4. Quan hệ cần vẽ

Quy ước: A → B là trường ở A trỏ tới B; cột bội số ghi số đối tượng B trên mỗi A. Với quan hệ ngược, một User/Room có thể có 0..N document liên quan. Quan hệ tùy chọn là có thể thiếu/rỗng; không yêu cầu tạo document giả để nối.

| Từ / trường | Đến | Bội số phía đích / chú thích |
| --- | --- | --- |
| users/{uid}, khóa document | Authentication UID | 1 theo thiết kế đăng ký; hai dịch vụ không có transaction chung, có thể phát sinh tài khoản/hồ sơ thiếu cần xử lý |
| publicHosts.uid / khóa document | users/{uid} | 1; User có 0..1 bản chiếu |
| usernames.uid | users/{uid} | 1; theo đăng ký hiện tại User có một alias username |
| hostApplications.applicantUid | users/{uid} | 1; User có 0..1 document đơn hiện hành |
| hostApplications.reviewedBy | users/{uid} | 0..1 Admin |
| buildings.ownerUid | users/{uid} | 1 Host |
| rooms.ownerUid | users/{uid} | 1 Host |
| rooms.buildingId | buildings/{buildingId} | 1; cùng ownerUid |
| rooms.reviewedBy | users/{uid} | 0..1 Admin |
| rooms.moderatedBy | users/{uid} | 0..1 Admin |
| users.moderatedBy | users/{uid} | 0..1 Admin; quan hệ tự tham chiếu |
| rooms.lastBookingId | bookings/{bookingId} | 0..1; liên kết kỹ thuật gần nhất |
| bookings.roomId | rooms/{roomId} | 1; Room có 0..N booking qua thời gian |
| bookings.guestUid | users/{uid} | 1 người thuê |
| bookings.ownerUid | users/{uid} | 1 Host; khác guestUid |
| bookings.cancelledByUid | users/{uid} | 0..1; bên thuê hoặc Host |
| bookings.monthKeys[] | availabilityMonths/{roomId_yyyyMM} | 1 hoặc 3 ở schema 2; một tháng có thể phục vụ nhiều booking giờ |
| availabilityMonths.roomId | rooms/{roomId} | 1; Room có 0..N tháng lịch |
| availabilityMonths.fixedBookingId | bookings/{bookingId} | 0..1 booking giữ cả tháng |
| availabilityMonths.hourlyBookings.{bookingId} | bookings/{bookingId} | 0..N, liên kết bằng key của map |
| availabilityMonths.lastBookingId | bookings/{bookingId} | 0..1 lần tác động gần nhất |
| users/{uid} chứa favorites | users/{uid}/favorites/{roomId} | quan hệ chứa 1 → 0..N |
| favorites.roomId | rooms/{roomId} | 1; User–Room là N–N thông qua Favorite |
| users/{uid} chứa noticeReads | users/{uid}/noticeReads/{eventId} | quan hệ chứa 1 → 0..N; eventId là định danh dẫn xuất, không phải FK notifications |
| conversations.guestUid | users/{uid} | 1 bên thuê |
| conversations.hostUid | users/{uid} | 1 Host |
| conversations.memberUids[] | users/{uid} | đúng 2, cùng cặp guestUid/hostUid; có thể gộp nhãn để tránh đường trùng |
| conversations.roomId | rooms/{roomId} | 1 ngữ cảnh khởi tạo của luồng hiện có; không có nghĩa chỉ chat về một phòng |
| conversations.bookingId | bookings/{bookingId} | 0..1 ngữ cảnh khởi tạo |
| conversations.lastSenderUid | users/{uid} | 0..1; rỗng khi chưa có tin |
| conversations.lastMessageId | conversations/{guestUid:hostUid}/messages/{messageId} | 0..1 tin cuối trong chính hội thoại |
| conversations/{guestUid:hostUid} chứa messages | conversations/{guestUid:hostUid}/messages/{messageId} | quan hệ chứa 1 → 0..N |
| messages.senderUid | users/{uid} | 1, phải là thành viên |
| messages.roomId | rooms/{roomId} | 0..1, phòng thuộc Host của hội thoại |
| propertyVerifications.ownerUid | users/{uid} | 1 Host |
| propertyVerifications.roomId | rooms/{roomId} | 0..1 theo loại/ngữ cảnh |
| propertyVerifications.buildingId | buildings/{buildingId} | 0..1 theo loại/ngữ cảnh; code ưu tiên roomId |
| propertyVerifications.reviewedBy | users/{uid} | 0..1 Admin |
| reports.reporterUid | users/{uid} | 1 người báo |
| reports.ownerUid | users/{uid} | 1 Host liên quan |
| reports.roomId | rooms/{roomId} | 1 |
| reports.reviewedBy | users/{uid} | 0..1 Admin |
| auditLogs.actorUid | users/{uid} | 1 người thực hiện theo contract app; dữ liệu tác vụ ngoài app có thể dùng định danh khác cần đối chiếu |
| auditLogs.targetPath | document được chỉ định | liên kết đa hình/dynamic, không vẽ thành cột FK cố định hoặc khẳng định mọi targetPath đều trỏ document tồn tại |
| payments.guestUid | users/{uid} | S-only, chưa xác nhận schema dữ liệu |
| notifications.recipientUid | users/{uid} | S-only, chưa xác nhận schema dữ liệu |

Snapshot cần thể hiện bằng ghi chú/nét riêng, không thêm FK: rooms.buildingName/address; bookings.roomName/roomAddress/guestName/guestPhone/priceSnapshot; favorites.roomName; conversations.guestName/hostName/roomName; messages.roomName. Snapshot không phải đối tượng mới và không mặc định đồng bộ theo dữ liệu nguồn.

## 5. Các ràng buộc giải thích cùng sơ đồ

- Một Room thuê độc quyền trong khoảng [startAt, endAt); không đặt chồng trên cùng không gian. PENDING đã giữ lịch; không phải chờ thanh toán mới giữ.
- Luồng chính PENDING → CONFIRMED → CHECKED_IN → COMPLETED; có nhánh REJECTED từ PENDING và CANCELLED từ PENDING/CONFIRMED trước giờ bắt đầu. Không khẳng định tự động chuyển trạng thái chỉ vì thời gian trôi qua.
- Tạo booking ghi booking + lịch tháng + revision phòng + audit cùng transaction. Hủy/từ chối/hoàn tất nhả đúng khóa lịch tương ứng; không xóa booking lịch sử.
- HOURLY dùng các slot 30 phút; FIXED giữ trọn 1 hoặc 3 tháng. Không có luồng đặt theo ngày từ pricePerDay.
- Tiền mới: paymentStatus UNAVAILABLE, khoản trả trước dự kiến 40%. Chưa thu/hoàn tiền thật; hủy hiện refundAmount=0, refundStatus=NOT_REQUIRED. Không thêm payment/refund/payout đang hoạt động vào sơ đồ.
- Policy v1 cho Guest: HOURLY trước >=24h:100%, >=6h:50%, còn lại 0%; FIXED trước >=7 ngày:100%, >=48h:50%, còn lại 0%. Host hủy trước giờ bắt đầu cần lý do, tỷ lệ chính sách 100%; tỷ lệ không phải bằng chứng tiền đã hoàn.
- Chat ghi message + preview/sequence + received counter trong transaction; chỉ bên nhận tăng read counter của chính mình. Admin không mặc định đọc chat riêng.
- noticeReads không có nội dung/thông tin người gửi. BookingNotice là dữ liệu dẫn xuất, không có collection tương ứng đang được app ghi.
- Khi phân loại quyền, phải theo Rules thực tế; “publicHosts” vẫn cần đăng nhập hoạt động. Phòng được người thuê đọc khi APPROVED + ACTIVE; người sở hữu/Admin có quyền riêng.

## 6. Dịch vụ ngoài Firestore để vẽ ở vùng riêng

| Dịch vụ / đường dẫn | Vai trò và liên kết |
| --- | --- |
| Firebase Authentication | Danh tính, mật khẩu, phiên đăng nhập, email reset; liên kết users bằng UID. Không đưa password/token vào Firestore |
| Storage roomMedia/{uid}/{roomId}/{file} | Ảnh phòng; liên hệ rooms.imageUrls theo URL/path, không có collection Media trong Firestore |
| Storage buildingMedia/{uid}/{buildingId}/{file} | Ảnh tòa nhà; liên hệ buildings.coverUrl |
| Storage verification/{uid}/{file} | Bằng chứng riêng tư; liên hệ evidenceRefs/evidencePath. Storage Rules hiện chỉ chủ tệp đọc; quyền Admin xem tệp chưa hoàn tất |

Tệp ảnh/PDF không lưu nhị phân trong Firestore. URL ảnh mẫu có thể trỏ dịch vụ khác, không khẳng định mọi URL đều thuộc các path Storage này. Rules tệp cho ảnh JPEG/PNG/WebP tối đa 5 MiB; verification cho thêm PDF, tối đa 5 MiB.

## 7. Truy vấn, index và realtime để chú thích

Composite index được khai báo local:

| Collection | Trường / thứ tự | Query scope |
| --- | --- | --- |
| rooms | approvalStatus ASC, listingStatus ASC | COLLECTION |
| bookings | ownerUid ASC, status ASC | COLLECTION |
| bookings | roomId ASC, endAt ASC | COLLECTION |

fieldOverrides rỗng. Đây là cấu hình local, không xác nhận trạng thái triển khai index cloud.

- Trang phòng: lọc APPROVED + ACTIVE, phân trang theo document ID; đọc bổ sung Building/PublicHost.
- Booking: lọc guestUid hoặc ownerUid; listener theo dữ liệu của bên tham gia.
- Inbox: conversations whereArrayContains(memberUids, uid); messages orderBy(sequence DESC) và limit.
- noticeReads dùng listener theo tài khoản; không đọc collection notifications làm nguồn hộp cập nhật.
- Bản đồ: đọc phòng và Building, lọc khoảng cách phía app; rentalPlaces limit 100. Không có geohash trong schema hiện tại, không vẽ truy vấn bán kính phía Firestore như tính năng đã có.

## 8. Class không được biến thành collection độc lập

BookingNotice, HostStats, MapPoint, MapRoute, NearbyPlace, ReviewItem, RoomListing, UiState và UserRole không phải collection lưu độc lập hiện tại. RoomBlackout là map nhúng trong rooms.blackoutRanges. PublicHost và AvailabilityMonth có lưu thật nhưng là bản chiếu/lịch kỹ thuật, không phải loại người dùng hay đơn vị tài sản mới.

Không thêm collection reviews chỉ vì có ReviewItem: class này đang phục vụ hàng chờ xét duyệt. Không thêm collection cho State/ViewModel/Activity/Adapter.

## 9. Cách bố trí sơ đồ để không thiếu và vẫn đọc được

1. Tổng quan: đủ 13 collection gốc đang tham chiếu, 3 subcollection và hai thẻ Rules-only màu xám.
2. Tài khoản/quyền: users, usernames, publicHosts, hostApplications; favorites và noticeReads dưới users.
3. Không gian/booking: buildings, rooms, bookings, availabilityMonths, map giá và blackout mở đầy đủ.
4. Chat: conversations và messages, counters và ngữ cảnh phòng/booking.
5. Quản trị/nguồn ngoài: propertyVerifications, reports, auditLogs, rentalPlaces; Authentication/Storage ở vùng ngoài database.

Các trang chi tiết dùng lại cùng mẫu đường dẫn với trang tổng quan. Bộ hình chính giữ đủ 225 dòng định nghĩa, gồm cả đường dẫn lồng/key động. Từ điển Markdown giải thích nguồn, legacy, snapshot và unknown/any, đồng thời giữ đủ 46 đặc tả quan hệ (bao gồm 3 quan hệ chứa, 1 liên hệ Auth và 1 đích động của audit). [S]/[D] ở tên hiển thị là chú giải nguồn, không phải ký tự trong tên trường lưu thật. D-only chỉ là biến thể đã thấy trong dataset, không nâng thành contract được code đọc/ghi. Không sử dụng các giá trị ví dụ làm field mới.

Sản phẩm chính: [5 trang native draw.io](drawio/broomsharing-data-design.drawio), [từ điển và quan hệ Markdown](drawio/field-and-relationship-dictionary.md), [PDF vector toàn bộ 5 trang](../../output/pdf/firestore/broomsharing-firestore-diagrams.pdf). Bố cục bảng chọn lọc và phụ lục native/PDF sinh theo bố cục đó đã bị loại, không lưu làm backup. Auth/Storage/index được diễn giải ở các mục 6–7 của tài liệu này.

## 10. Nguồn code để kiểm chứng

- [Model Java](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/model)
- [Repository Java](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository)
- [BookingPolicy](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/utils/BookingPolicy.java)
- [CancellationPolicy](../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/utils/CancellationPolicy.java)
- [Firebase config](../../firebase.json), [Rules](../../firestore.rules), [Storage Rules](../../storage.rules), [Index](../../firestore.indexes.json)
- [Dataset Firestore local](../../data/demo/firestore-dataset.json), [Reference places](../../data/reference/rental-places-hcm.json)
- [Công cụ tạo dataset](../../tools/dataset/FirestoreDataset.java), [Công cụ reference places](../../tools/dataset/RentalPlacesDataset.java)

Không thay schema, dữ liệu cloud hay Rules khi tạo tài liệu này. Muốn gọi đây là kiểm kê toàn bộ cloud cần thêm lượt đọc metadata/document thực tế trong đúng database; giữ nguyên ghi chú phạm vi cho tới khi có bằng chứng đó.
