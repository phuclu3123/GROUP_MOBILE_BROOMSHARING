# Hợp đồng dữ liệu v1

Database ID: **`broomsharing`**, dùng `FirebaseConfig`, không fallback sang `(default)`.

Đây là contract của code hiện tại và điểm nối đợt sau; một số collection chưa có chức năng ghi ở đợt 1. Firestore tự tạo collection khi có document đầu tiên. `schema-examples.json` là ví dụ đọc hiểu, không phải file import trực tiếp.

## Quy ước

- Document ID là nguồn chính; field `id` trong POJO phải trùng ID document.
- Model chính có `schemaVersion: 1`. `usernames` là chỉ mục nhỏ chỉ có `uid/email`.
- `ownerUid`, `guestUid`, `applicantUid` là Firebase Auth UID.
- Tiền VND, từ 0 đến 1.000.000.000.000. Không tự bỏ ký tự sai để biến `-100` thành `100`.
- Firestore Timestamp/Java Date lưu thời điểm UTC; hiển thị VN qua `TimeUtils`. Không lưu chuỗi ngày hiển thị thay Timestamp.
- Giờ mở cửa `HH:mm` là giờ VN, mở trước đóng trong cùng ngày; chưa hỗ trợ qua đêm.
- Role `GUEST/HOST/ADMIN`; HOST kế thừa chức năng thuê. Tài khoản mới GUEST/ACTIVE. ADMIN đầu tiên do người quản lý cấp trong Console.
- Status lưu tên enum/string viết hoa, không lưu nhãn tiếng Việt.

## Collections

| Đường dẫn | Trường chính / trạng thái | Phạm vi |
| --- | --- | --- |
| `users/{uid}` | uid, name, username, email, phone, phoneVerified, role, status ACTIVE/BLOCKED, schemaVersion, createdAt/updatedAt | Auth |
| `usernames/{normalizedUsername}` | uid, email; tên 3–24 ký tự `[a-z0-9_]`, lowercase + trim | Transaction cùng user profile |
| `hostApplications/{uid}` | applicantUid, fullName, phone, email, organizationType INDIVIDUAL/BUSINESS, identityDoc (mô tả demo), status PENDING/APPROVED/REJECTED, rejectionReason, timestamps, reviewedBy khi duyệt | Có; ID theo UID tránh gửi trùng. Đọc được đơn cũ ID ngẫu nhiên |
| `buildings/{id}` | ownerUid, name, address, district, contactName/contactPhone, directionGuide, coverUrl, latitude/longitude, location GeoPoint hoặc null, status ACTIVE, timestamps | Có |
| `rooms/{id}` | buildingId/buildingName, ownerUid, roomName/roomType, floor, address, capacity, area, activeMode, các giá/cọc, openTime/closeTime, amenities, rules, imageUrls, approvalStatus/listingStatus, rejectionReason, timestamps | Có |
| `bookings/{id}` | roomId, ownerUid, guestUid, activeMode, startAt/endAt, status PENDING/CONFIRMED/CANCELLED/COMPLETED/REJECTED, paymentStatus UNPAID/PENDING/PAID/FAILED/REFUNDED, totalAmount | Model + thống kê PENDING; chưa có booking engine |
| `availabilityDays/{roomId_YYYYMMDD}` | roomId, day (VN), slotsBusy, closedRanges, version | Dự kiến đợt booking |
| `fixedMonthLocks/{roomId_YYYYMM}` | roomId, month (VN), bookingId, state | Dự kiến đợt booking |
| `chats/{id}` | participantUids, lastMessage, updatedAt | Contract đợt sau |
| `messages/{id}` | chatId, senderUid, text, createdAt | Contract đợt sau |
| `reviews/{id}` | bookingId, roomId, guestUid, rating, comment, createdAt | Contract đợt sau |
| `reports/{id}` | reporterUid, targetType/targetId, reason/description, status OPEN/RESOLVED/DISMISSED, resolution, reviewedBy, timestamps | Admin đọc/xử lý; form gửi thuộc module sau |
| `payments/{id}` | bookingId, guestUid, amount, status, providerRef, timestamps | Contract; client không tự đặt PAID |
| `notifications/{id}` | recipientUid, type, title, body, readAt, createdAt | Contract đợt sau |
| `propertyVerifications/{id}` | ownerUid, buildingId, evidencePath, status PENDING/VERIFIED/REJECTED, reviewedBy, timestamps | Contract; đợt 1 chỉ mô tả giấy tờ |
| `auditLogs/{id}` | actorUid, targetPath, action APPROVE/REJECT, reason, createdAt | Ghi cùng transaction duyệt |

Dùng `propertyVerifications/auditLogs` theo DATA CONTRACT, không tạo `verifications/audit` song song. `availabilityDays/fixedMonthLocks` cụ thể hóa availability, chưa triển khai giữ chỗ.

## Giá, ảnh và công khai

- Một phòng chọn `HOURLY` hoặc `FIXED`. Giờ cần `pricePerHour > 0`; FIXED cần ít nhất một `pricePerMonth/pricePerQuarter > 0` khi gửi duyệt. Không suy mode từ giá nữa.
- Giá của mode không dùng lưu 0. `pricePerDay` giữ để đọc model cũ, không dùng tính tháng/quý; form mới đặt 0. Không tự chuyển giá ngày thành tháng.
- Mức giá 0–1.000 tỷ; diện tích 0–1.000.000 m²; sức chứa 0–10.000. Gửi duyệt cần giá áp dụng, diện tích, sức chứa >0.
- Tính tháng lịch, min/max thời lượng, chống trùng và chặn đổi mode khi có booking/lock thuộc đợt 2; chưa coi đã hoàn tất.
- `DRAFT → PENDING_REVIEW → APPROVED/REJECTED`. Chủ sửa rồi lưu về DRAFT hoặc PENDING_REVIEW; không tự đặt APPROVED. Có lý do từ chối để nộp lại.
- Chỉ query công khai khi **APPROVED và ACTIVE**. Nháp/chờ duyệt không công khai.
- `imageUrls` giữ thứ tự gallery, phần tử đầu làm cover. Tối đa 6 JPEG/PNG/WebP, mỗi ảnh 5 MiB. Không đưa giấy tờ vào gallery.
- Phòng bắt buộc thuộc tòa nhà của chủ. Vị trí tòa nhà tùy chọn; để trống nghĩa là chưa chọn vị trí, không gán điểm mẫu. Map picker/geocoding/API bên thứ ba để sau.

## Index và ghép nhóm

`firestore.indexes.json` có index phòng approvalStatus/listingStatus và booking ownerUid/status. Chưa deploy. Không tự seed dữ liệu/giá mẫu lên cloud.

Khi ghép Trúc cần rà package/applicationId, booking model và tên field/collection trên. Dùng `Room`/`Booking` làm DTO, không tạo lớp chỉ đổi tên. Module tương lai phải hoàn thiện schema/Rules của chính nó; file kiểm soát hiện đóng các module chưa triển khai.

## Số điện thoại khi đăng ký

Form mới bắt buộc số điện thoại. Nhận số VN 10 chữ số bắt đầu bằng 0 hoặc số có mã quốc gia + (8–15 chữ số). Chuẩn hóa số VN sang +84 trước khi lưu `users/{uid}.phone`; `phoneVerified` luôn false lúc đăng ký. Đây là số liên hệ chưa chứng minh quyền sở hữu; chưa áp uniqueness cho số chưa xác minh, chưa tạo chỉ mục đăng nhập bằng phone và chưa gửi SMS. Tài khoản cũ thiếu phone vẫn đăng nhập được. Khi làm OTP cần xác minh và liên kết Phone credential vào UID hiện có; không tự tạo tài khoản thứ hai từ số điện thoại nhập trong form.
