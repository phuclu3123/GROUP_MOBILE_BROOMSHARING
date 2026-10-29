# Current field and relationship dictionary

Updated 2026-10-28 from Android Java, Java payment backend and Security Rules. Schema only; no contact values or secrets.

## `users/{uid}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `uid` | `string` | M/W/D; cùng UID với document ID và Authentication |
| `schemaVersion` | `integer` | M/W/D; hiện 1 |
| `name` | `string` | M/W/D; tên hiển thị |
| `username` | `string` | M/W/D; username đã chuẩn hóa |
| `email` | `string` | M/W/D; email tài khoản |
| `phone` | `string` | M/W/D; số điện thoại, đăng ký chuẩn hóa dạng quốc tế |
| `phoneVerified` | `boolean` | M/W/D; mặc định false; không phải xác minh email |
| `role` | `string` | M/W/D; GUEST, HOST, ADMIN |
| `status` | `string` | M/W/D; ACTIVE, BLOCKED |
| `createdAt` | `timestamp` | W/D; thời gian server khi đăng ký |
| `updatedAt` | `timestamp` | W/D; lần cập nhật |
| `moderationReason` | `string` | W/D; lý do quản trị, phát sinh khi xử lý |
| `moderatedBy` | `string` | W; UID Admin xử lý |
| `photoUrl` | `string` | M/W/S; URL avatar Cloudinary theo UID, có thể rỗng |

## `users/{uid}/favorites/{roomId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `roomId` | `string` | M/W/D; liên kết rooms |
| `roomName` | `string` | M/W/D; snapshot tên lúc lưu |
| `createdAt` | `timestamp` | M/W/D; thời gian lưu |

## `users/{uid}/noticeReads/{eventId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `readAt` | `timestamp` | W/S; thời gian server xác nhận đã đọc |

## `publicHosts/{uid}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `uid` | `string` | M/W/D; liên kết users cùng UID |
| `name` | `string` | M/W/D; tên Host |
| `status` | `string` | M/W/D; đồng bộ trạng thái users, ACTIVE/BLOCKED |
| `photoUrl` | `string` | M/W/S; ảnh đại diện đồng bộ với hồ sơ Host |

## `usernames/{normalizedUsername}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `uid` | `string` | W/D; liên kết users/Authentication |
| `email` | `string` | W/D; email đưa vào Firebase Authentication sau lookup |

## `hostApplications/{uid}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `id` | `string` | M/W/D; cùng UID/document ID |
| `schemaVersion` | `integer` | M/W/D; hiện 1 |
| `applicantUid` | `string` | M/W/D; người nộp, liên kết users |
| `fullName` | `string` | M/W/D; họ tên khai trong đơn |
| `phone` | `string` | M/W/D; số liên hệ |
| `email` | `string` | M/W/D; email liên hệ |
| `organizationType` | `string` | M/W/D; loại cá nhân/tổ chức |
| `identityDoc` | `string` | M/W/D; thông tin giấy tờ dạng chuỗi, không phải file nhị phân |
| `status` | `string` | M/W/D; PENDING, APPROVED, REJECTED |
| `rejectionReason` | `string` | M/W/D; lý do/kết quả duyệt, có thể rỗng |
| `createdAt` | `timestamp` | M/W/D; thời gian tạo |
| `updatedAt` | `timestamp` | M/W/D; thời gian cập nhật |
| `reviewedBy` | `string` | W; UID Admin duyệt |
| `identityImageUrls` | `array<string>` | M/W/S; hai URL ảnh giấy tờ DEMO |
| `evidenceMode` | `string` | M/W/S; DEMO |
| `identityReviewedAt` | `timestamp/null` | M/W; mốc Admin xem ảnh |

## `buildings/{buildingId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `id` | `string` | M/W/D; document ID |
| `schemaVersion` | `integer` | M/W/D; hiện 1 |
| `ownerUid` | `string` | M/W/D; Host sở hữu, liên kết users |
| `name` | `string` | M/W/D; tên tòa nhà |
| `address` | `string` | M/W/D; địa chỉ |
| `district` | `string` | M/W/D; khu vực/quận |
| `contactPhone` | `string` | M/W/D; số liên hệ địa điểm |
| `contactName` | `string` | M/W/D; người liên hệ |
| `latitude` | `double` | M/W/D; vĩ độ |
| `longitude` | `double` | M/W/D; kinh độ |
| `location` | `geopoint/null` | M/W; getter getLocation() có thể được serialize khi ghi POJO; dataset cũ chỉ có latitude/longitude. Không coi cả hai biểu diễn là luôn hiện diện hoặc tự đồng bộ ngoài getter/setter Java |
| `directionGuide` | `string` | M/W/D; hướng dẫn đến/lối vào |
| `coverUrl` | `string` | M/W/D; URL ảnh bìa |
| `status` | `string` | M/W/D; mặc định ACTIVE; luồng hiện tại không xác định thêm enum hoàn chỉnh |
| `createdAt` | `timestamp` | M/W/D; thời gian tạo |
| `updatedAt` | `timestamp` | M/W/D; thời gian cập nhật |

## `rooms/{roomId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `id` | `string` | M/W/D; document ID |
| `schemaVersion` | `integer` | M/W/D; hiện 1 |
| `buildingId` | `string` | M/W/D; liên kết buildings |
| `buildingName` | `string` | M/W/D; tên tòa nhà sao chép |
| `ownerUid` | `string` | M/W/D; Host, phải khớp chủ Building |
| `roomName` | `string` | M/W/D; tên không gian |
| `roomType` | `string` | M/W/D; loại không gian, ví dụ bàn/phòng riêng/phòng họp/mặt bằng |
| `capacity` | `integer` | M/W/D; sức chứa |
| `area` | `double` | M/W/D; diện tích m² |
| `floor` | `string` | M/W/D; tầng |
| `address` | `string` | M/W/D; địa chỉ sao chép |
| `activeMode` | `string` | M/W/D; HOURLY hoặc FIXED |
| `pricePerHour` | `double` | M/W/D; giá thuê giờ VND |
| `pricePerDay` | `double` | M/W/D; trường cũ, không suy ra có luồng đặt theo ngày |
| `pricePerMonth` | `double` | M/W/D; giá tháng VND |
| `pricePerQuarter` | `double` | M/W/D; giá quý VND |
| `deposit` | `double` | M/W/D; trường cọc cũ; không dùng để tính khoản trả trước 40% của booking mới |
| `openTime` | `string` | M/W/D; HH:mm |
| `closeTime` | `string` | M/W/D; HH:mm |
| `amenities` | `array<string>` | M/W/D; tiện ích |
| `rules` | `string` | M/W/D; nội quy |
| `approvalStatus` | `string` | M/W/D; DRAFT, PENDING_REVIEW, APPROVED, REJECTED |
| `listingStatus` | `string` | M/W/D; ACTIVE, INACTIVE, SUSPENDED |
| `imageUrls` | `array<string>` | M/W/D; URL ảnh, Rules giới hạn tối đa 6 |
| `blackoutDates` | `array<string>` | M/W/D; ngày khóa YYYY-MM-DD |
| `blackoutRanges` | `array<map>` | M/W/D; khoảng khóa lịch nhúng, không phải collection |
| `blackoutRanges[].startAt` | `timestamp` | M; bắt đầu khoảng khóa |
| `blackoutRanges[].endAt` | `timestamp` | M; kết thúc loại trừ |
| `blackoutRanges[].reason` | `string` | M; lý do khóa |
| `workingDays` | `array<integer>` | M/W/D; thứ ISO 1..7 |
| `slotMinutes` | `integer` | M/W/D; bước chọn giờ; không đồng nhất với slot kỹ thuật 30 phút |
| `minBookingMinutes` | `integer` | M/W/D; thời lượng thuê tối thiểu |
| `fixedStartMonth` | `string` | M/W/D; tháng nhận thuê cố định YYYY-MM |
| `renewalPolicy` | `string` | M/W/D; chính sách gia hạn |
| `rentalPolicy` | `string` | M/W/D; chính sách thuê |
| `locationVerification` | `string` | M/W/D; mặc định UNVERIFIED, luồng Admin có thể ghi ADMIN_REVIEWED; không tự thêm trạng thái đối tác |
| `photosVerification` | `string` | M/W/D; như locationVerification cho ảnh |
| `rejectionReason` | `string` | M/W/D; lý do/kết quả xét duyệt |
| `bookingRevision` | `integer` | M/W/D; tăng khi thay đổi booking/lịch liên quan |
| `lastBookingId` | `string` | M/W/D; booking gần nhất tác động lịch/phòng, rỗng khi chưa có |
| `createdAt` | `timestamp` | M/W/D; thời gian tạo |
| `updatedAt` | `timestamp` | M/W/D; lần cập nhật |
| `reviewedBy` | `string` | W; UID Admin xét duyệt tin |
| `moderationReason` | `string` | W; lý do đình chỉ/gỡ đình chỉ |
| `moderatedBy` | `string` | W; UID Admin quản trị |
| `activeBookingCount` | `integer` | M/W/S; số booking đang chiếm lịch, gồm phần giữ tạm |

## `bookings/{bookingId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `id` | `string` | M/W/D; document ID |
| `schemaVersion` | `integer` | M/W/D; class mặc định 1, luồng tạo mới ghi 2 |
| `roomId` | `string` | M/W/D; liên kết rooms |
| `roomName` | `string` | M/W/D; snapshot tên phòng |
| `roomAddress` | `string` | M/W/D; snapshot địa chỉ |
| `ownerUid` | `string` | M/W/D; Host của booking |
| `guestUid` | `string` | M/W/D; người thuê; tài khoản role HOST cũng có thể thuê |
| `guestName` | `string` | M/W/D; snapshot tên Guest |
| `guestPhone` | `string` | M/W/D; snapshot liên hệ Guest |
| `status` | `string` | M/W/S; PENDING, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, REJECTED, EXPIRED, NO_SHOW, TERMINATED; REJECTED thuộc nhánh cũ |
| `paymentStatus` | `string` | M/W; UNAVAILABLE before QR creation, PENDING, PAID, REVIEW_REQUIRED; expiry is UNPAID. Legacy values retained on old data. |
| `activeMode` | `string` | M/W/D; HOURLY/FIXED |
| `rejectionReason` | `string` | M/W/D; lý do từ chối hoặc hủy, có thể rỗng |
| `startAt` | `timestamp` | M/W/D; thời điểm bắt đầu |
| `endAt` | `timestamp` | M/W/D; thời điểm kết thúc loại trừ |
| `totalAmount` | `double` | M/W/D; tổng tiền thuê VND |
| `priceSnapshot` | `map` | M/W/D; chụp giá/chính sách tại lúc đặt |
| `priceSnapshot.currency` | `string` | W/D; VND |
| `priceSnapshot.rentalAmount` | `number` | W/D; tiền thuê |
| `priceSnapshot.feeAmount` | `integer` | W/D; hiện 0 |
| `priceSnapshot.depositAmount` | `number` | W/D; khoản trả trước dự kiến, booking mới 40%; code mới ghi số nguyên, dataset cũ có double |
| `priceSnapshot.advancePercent` | `integer` | W; booking mới 40; dataset cũ có thể thiếu |
| `priceSnapshot.cancellationPolicyVersion` | `integer` | W; booking mới 1; dataset cũ có thể thiếu |
| `priceSnapshot.totalAmount` | `number` | W/D; tổng tiền |
| `priceSnapshot.paymentDueAmount` | `number` | W/D; khoản phải trả trước dự kiến |
| `priceSnapshot.rentalPolicy` | `string` | W/D; snapshot chính sách thuê |
| `priceSnapshot.renewalPolicy` | `string` | W/D; snapshot chính sách gia hạn |
| `rentalMonths` | `integer` | M/W/D; HOURLY=0, FIXED=1 hoặc 3 |
| `slotStart` | `integer` | M/W/D; slot 30 phút đầu trong tháng, FIXED=0 |
| `slotCount` | `integer` | M/W/D; số slot 30 phút liên tiếp, FIXED=0 |
| `monthKeys` | `array<string>` | M/W/D; ID availabilityMonths, 1 hoặc 3 phần tử |
| `slotKeys` | `array<integer>` | M/W/D; danh sách slot giữ, FIXED rỗng |
| `requestFingerprint` | `string` | M/W/D; SHA-256 lựa chọn phòng/thời gian |
| `cancelledAt` | `timestamp/null` | M/W; mặc định null, khi hủy là server timestamp; dataset cũ có thể thiếu |
| `cancelledByUid` | `string` | M/W; UID bên hủy, mặc định rỗng |
| `cancellationRefundPercent` | `integer` | M/W; tỷ lệ chính sách 0/50/100, không xác nhận đã chuyển tiền |
| `refundAmount` | `number` | M/W; mặc định double 0, thao tác hủy hiện ghi integer 0 vì chưa thu tiền |
| `refundStatus` | `string` | M/W; NOT_REQUIRED hoặc MANUAL_REVIEW; không xác nhận đã hoàn tiền |
| `createdAt` | `timestamp` | M/W/D; thời gian tạo |
| `updatedAt` | `timestamp` | M/W/D; lần cập nhật |
| `paymentFlow` | `string` | M/W/S; INSTANT_5M cho booking mới; rỗng ở dữ liệu tương thích |
| `paidAmount` | `integer` | M/W; số tiền thực nhận do backend đối soát |
| `usageConfirmedAt` | `timestamp/null` | M/W/S; xác nhận đã sử dụng, có thể chưa có |
| `usageConfirmedBy` | `string` | M/W/S; UID xác nhận sử dụng |
| `closedAt` | `timestamp/null` | M/W/S; mốc khép lượt |

## `availabilityMonths/{roomId_yyyyMM}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `roomId` | `string` | M/W/D; liên kết rooms |
| `monthIndex` | `integer` | M/W/D; số YYYYMM, không phải chỉ số tháng 0..11 |
| `fixedBookingId` | `string` | M/W/D; booking giữ toàn tháng; rỗng nếu không có |
| `slotsBusy` | `array<integer>` | M/W/D; tập slot kỹ thuật 30 phút đang giữ |
| `hourlyBookings` | `map<string,array<integer>>` | M/W/D; key động là bookingId, value là các slot booking đó giữ |
| `hourlyBookings.{bookingId}` | `array<integer>` | M/W/D; liên kết theo key, không phải subcollection |
| `lastBookingId` | `string` | M/W/D; booking vừa tác động lịch |
| `updatedAt` | `timestamp` | M/W/D; thời gian server |
| `pendingPaymentIds` | `array<string>` | M/W/S; booking đang giữ lịch tạm chờ thanh toán |

## `conversations/{guestUid:hostUid}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `id` | `string` | M/W; guestUid + ':' + hostUid |
| `guestUid` | `string` | M/W; liên kết users bên thuê |
| `hostUid` | `string` | M/W; liên kết users bên cho thuê |
| `guestName` | `string` | M/W; snapshot tên bên thuê |
| `hostName` | `string` | M/W; snapshot tên Host |
| `memberUids` | `array<string>` | M/W; đúng hai UID [guestUid, hostUid], dùng query inbox |
| `roomId` | `string` | M/W; phòng ngữ cảnh khởi tạo |
| `roomName` | `string` | M/W; tên phòng ngữ cảnh |
| `bookingId` | `string` | M/W; booking ngữ cảnh khởi tạo hoặc chuỗi rỗng nếu mở từ phòng |
| `lastText` | `string` | M/W; preview tin cuối, mới tạo rỗng |
| `lastSenderUid` | `string` | M/W; người gửi cuối hoặc rỗng |
| `lastMessageId` | `string` | M/W; ID tin cuối trong subcollection hoặc rỗng |
| `sequence` | `integer` | M/W; số thứ tự tin gần nhất, khởi tạo 0 |
| `guestReceived` | `integer` | M/W; tổng số tin đến Guest |
| `hostReceived` | `integer` | M/W; tổng số tin đến Host |
| `guestRead` | `integer` | M/W; mốc số tin đến Guest đã đọc |
| `hostRead` | `integer` | M/W; mốc số tin đến Host đã đọc |
| `createdAt` | `timestamp` | M/W; thời gian tạo |
| `updatedAt` | `timestamp` | M/W; cập nhật khi gửi tin |

## `conversations/{guestUid:hostUid}/messages/{messageId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `id` | `string` | M/W; cùng messageId |
| `senderUid` | `string` | M/W; thành viên gửi, liên kết users |
| `text` | `string` | M/W; nội dung 1..2000 ký tự |
| `roomId` | `string` | M/W; phòng ngữ cảnh hoặc rỗng |
| `roomName` | `string` | M/W; snapshot tên phòng ngữ cảnh hoặc rỗng |
| `sequence` | `integer` | M/W; thứ tự tin trong conversation |
| `createdAt` | `timestamp` | M/W; thời gian server |

## `propertyVerifications/{verificationId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `schemaVersion` | `integer` | D; dữ liệu mẫu hiện 1 |
| `ownerUid` | `string` | R/S/D; Host nộp hồ sơ |
| `verificationType` | `string` | R/W/S; LOCATION or PHOTOS in the current submission flow; Host identity demo is in hostApplications. |
| `roomId` | `string` | R/D; phòng cần xác minh; có thể rỗng/thiếu |
| `buildingId` | `string` | R/D; tòa nhà cần xác minh; có thể rỗng/thiếu |
| `description` | `string` | R/D; mô tả |
| `evidenceRefs` | `array<string>` | R/D; đường dẫn tệp, code ưu tiên đọc danh sách này |
| `evidencePath` | `string` | R; dạng đơn được code đọc dự phòng khi evidenceRefs không phải List |
| `evidencePaths` | `array<unknown>` | D-only; tên trường biến thể trong dataset, các mảng đang rỗng nên chưa xác định kiểu phần tử bằng dữ liệu; AdminRepository không đọc tên này |
| `reviewed` | `boolean` | D-only; cờ dữ liệu mẫu; không đồng nhất với reviewedBy/reviewedAt và không chứng minh Admin đã xem tệp |
| `status` | `string` | R/W/S/D; PENDING, VERIFIED, REJECTED |
| `rejectionReason` | `string` | W; lý do/kết quả duyệt |
| `reviewedBy` | `string` | W; UID Admin xử lý |
| `method` | `string` | W; ADMIN_REVIEWED |
| `reviewNote` | `string` | W; ghi chú xét duyệt |
| `reviewedAt` | `timestamp` | W; thời gian xét duyệt |
| `partnerName` | `string` | W; code ghi rỗng, không phải tích hợp đối tác |
| `createdAt` | `timestamp` | R/S/D; thời gian tạo |
| `updatedAt` | `timestamp` | W/D; lần cập nhật |

## `reports/{reportId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `id` | `string` | M/W; cùng reportId; dataset cũ có thể thiếu |
| `schemaVersion` | `integer` | M/W/D; hiện 1 |
| `reporterUid` | `string` | M/W/D; người báo, liên kết users |
| `roomId` | `string` | M/W/D; phòng bị báo cáo |
| `ownerUid` | `string` | M/W/D; Host lấy từ dữ liệu phòng |
| `reason` | `string` | M/W/D; Sai thông tin / Ảnh không khớp / Nghi ngờ gian lận / Khác |
| `description` | `string` | M/W/D; mô tả 20..2000 ký tự |
| `status` | `string` | M/W/D; OPEN, RESOLVED, DISMISSED |
| `createdAt` | `timestamp` | M/W/D; thời gian tạo |
| `updatedAt` | `timestamp` | M/W/D; lần cập nhật |
| `action` | `string` | W; NO_ACTION, SUSPEND_ROOM, BLOCK_HOST |
| `resolution` | `string` | W; kết luận 10..1000 ký tự |
| `reviewedBy` | `string` | W; UID Admin xử lý |
| `reviewedAt` | `timestamp` | W; thời gian xử lý |
| `hostResponse` | `string` | M/W/S; phản hồi của Host khi báo cáo còn mở |
| `bookingId` | `string` | M/W/S; booking liên quan nếu có, có thể rỗng |

## `auditLogs/{auditId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `schemaVersion` | `integer` | M/W/D; hiện 1 |
| `actorUid` | `string` | M/W/D; người thực hiện, liên kết users |
| `targetPath` | `string` | M/W/D; đường dẫn đối tượng, có thể là document hoặc đích tổng hợp của tác vụ dataset |
| `action` | `string` | M/W/D; mã hành động |
| `before` | `map<string,any>` | M/W/D; dữ liệu trước thay đổi, có thể rỗng |
| `after` | `map<string,any>` | M/W/D; dữ liệu sau thay đổi, có thể rỗng |
| `reason` | `string` | M/W/D; lý do |
| `createdAt` | `timestamp` | M/W/D; thời gian server |

## `rentalPlaces/{placeId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `id` | `string` | M/D; document ID |
| `name` | `string` | M/D; tên địa điểm |
| `address` | `string` | M/D; địa chỉ nguồn |
| `location` | `geopoint` | M/D; vị trí từ nguồn tham khảo; không phải map collection |
| `sourceUrl` | `string` | M/D; URL tin nguồn |
| `mapSourceUrl` | `string` | M/D; URL bản đồ nguồn |
| `sourceCoordinateLabel` | `string` | M/D; nhãn tọa độ từ nguồn |
| `locationNote` | `string` | M/D; ghi chú giới hạn vị trí |
| `checkedAt` | `timestamp` | M/D; thời điểm đối chiếu nguồn |

## `notifications/{notificationId}`

rules_only

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `recipientUid` | `unknown` | S-only; dùng kiểm tra người nhận, ngữ nghĩa UID string nhưng chưa có schema ghi/validation kiểu |
| `readAt` | `unknown` | S-only; trường duy nhất được phép thay đổi; dự kiến timestamp nhưng Rules match này chưa kiểm tra kiểu |

## `payments/{bookingId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `bookingId` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `guestUid` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `ownerUid` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `orderCode` | `integer` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `amount` | `integer` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `expiresAt` | `integer` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `status` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `provider` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `advancePercent` | `integer` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `createdAt` | `timestamp` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `updatedAt` | `timestamp` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `paymentLinkId` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `checkoutUrl` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `qrCode` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `accountName` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `accountNumber` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `bin` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `paidAmount` | `integer` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `paidAt` | `timestamp` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |
| `providerReference` | `string` | W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch |

## `reviews/{bookingId}`

repository_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `bookingId` | `string` | M/W/S; một đánh giá cho lượt thuê đã khép và có xác nhận sử dụng |
| `roomId` | `string` | M/W/S; một đánh giá cho lượt thuê đã khép và có xác nhận sử dụng |
| `guestUid` | `string` | M/W/S; một đánh giá cho lượt thuê đã khép và có xác nhận sử dụng |
| `guestName` | `string` | M/W/S; một đánh giá cho lượt thuê đã khép và có xác nhận sử dụng |
| `comment` | `string` | M/W/S; một đánh giá cho lượt thuê đã khép và có xác nhận sử dụng |
| `rating` | `integer` | M/W/S; một đánh giá cho lượt thuê đã khép và có xác nhận sử dụng |
| `createdAt` | `timestamp` | M/W/S; một đánh giá cho lượt thuê đã khép và có xác nhận sử dụng |

## `paymentOrders/{orderCode}`

backend_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `bookingId` | `string` | W/R; ánh xạ mã đơn payOS về booking; chỉ backend truy cập |

## `appConfig/payments`

backend_referenced

| Field | Firestore type | Source and meaning |
| --- | --- | --- |
| `flow` | `string` | W/R; INSTANT_5M |
| `demoHostUid` | `string` | W/R; UID Host của kênh demo |

## Logical relationships

ID links are application relationships, not automatic foreign keys.

| Source | Target | Meaning |
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
| auditLogs.actorUid | users/{uid} | App actions link to a user; payment backend actions may use payment_backend, not a Firebase user UID. |
| auditLogs.targetPath | document được chỉ định | liên kết đa hình/dynamic, không vẽ thành cột FK cố định hoặc khẳng định mọi targetPath đều trỏ document tồn tại |
| notifications.recipientUid | users/{uid} | S-only, chưa xác nhận schema dữ liệu |
| paymentOrders.bookingId | bookings/{bookingId} | Một mã đơn ánh xạ tới một booking |
| appConfig.demoHostUid | users/{uid} | Một Host demo của kênh hiện tại |
| reviews.bookingId / khóa document | bookings/{bookingId} | 0..1 đánh giá cho booking đã sử dụng và khép |
| reviews.roomId | rooms/{roomId} | Một phòng có nhiều đánh giá theo thời gian |
| reviews.guestUid | users/{uid} | Tác giả phải là Guest của booking |
| availabilityMonths.pendingPaymentIds[] | bookings/{bookingId} | Các khóa tạm chờ thanh toán, không thay thế fixedBookingId/hourlyBookings |
| payments.bookingId / khóa document | bookings/{bookingId} | 1 Payment cho một booking; cùng document ID |
| payments.guestUid / ownerUid | users/{uid} | Hai bên tham gia, không phải thông tin tài khoản ngân hàng động của từng Host |
