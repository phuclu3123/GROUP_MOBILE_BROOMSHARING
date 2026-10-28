# Kiểm định 5 sơ đồ Firestore — 23/10/2026

> Biên bản này ghi trạng thái **trước khi sửa**, được giữ để đối chiếu. Bộ hình hiện tại đã được tạo lại; xem [biên bản xử lý và kết quả kiểm tra mới](diagram-audit-resolution.md). Các kích thước, tên generator và kết luận chưa sửa bên dưới chỉ áp dụng cho bản cũ.

**Kết luận: chưa nên chốt bộ hình hiện tại để đưa nguyên trạng vào báo cáo.** Cây lưu trữ đã đúng ở những đường nối được kiểm tra; độ phủ tên trường tốt trong phạm vi local. Tuy nhiên, bộ hình còn thiếu phần giải thích quan hệ logic, chưa phân biệt nguồn/trạng thái của nhiều trường khi in và chưa đạt khả năng đọc ở kích thước trang báo cáo.

Chưa tìm thấy bản LaTeX hoặc nội dung chính xác của mục **3.2** trong các tài liệu local đã tìm. Đánh giá độ đầy đủ dưới đây dùng phạm vi “thiết kế dữ liệu Firestore, cấu trúc lưu trữ và quan hệ”. Nếu 3.2 chỉ yêu cầu cây collection/document, phần quan hệ có thể đặt ở mục khác, nhưng các vấn đề chú giải và kích thước in vẫn cần sửa. Không lấy số lượng 5 hình làm tiêu chí nghiệm thu.

## 1. Phạm vi và cách kiểm tra

- Đọc lại Model, các chỗ gọi `collection`, ghi/update trong Repository, BookingPolicy, các match Rules, cấu hình database, Storage Rules, dataset demo và dữ liệu địa điểm tham khảo.
- Phân tích **file native** [broomsharing-data-design.drawio](broomsharing-data-design.drawio), gồm 5 trang; kiểm tra source/target của connector, nhãn document, tooltip và kích thước trang.
- Xem lại cả 5 PNG xuất hiện có. Nhận xét thị giác áp dụng cho các bản xuất này; lượt kiểm định này không đọc lại canvas trình duyệt đang mở. Nếu canvas đã được sửa tay sau lần xuất, cần đối chiếu thêm bản đó.
- Dùng [audit_diagram_sources.py](../../../tools/visualization/audit_diagram_sources.py) để trích tên property Model, collection trong Java, hợp tên trường dataset, các cạnh native và ước lượng cỡ chữ khi in. Script chỉ đọc nguồn và ghi [diagram-audit-evidence.json](diagram-audit-evidence.json), không xuất giá trị document.
- Kiểm tra regex chỉ hỗ trợ rà soát tên: không phải Java compiler, không chứng minh kiểu dữ liệu, đầy đủ mọi nhánh ghi động hoặc Rules hoạt động đúng lúc chạy. Các trường phát sinh qua update/map được đối chiếu thủ công với nguồn liên quan.
- **Không đọc cloud, không chạy luồng app, không kiểm định triển khai Rules/index, không sửa schema/app/cloud trong lượt này.** Hash các nguồn đã đối chiếu được ghi trong evidence để nhận biết khi checkout thay đổi.

## 2. Phần đã xác nhận đúng

| Hạng mục | Kết quả local | Giới hạn kết luận |
| --- | --- | --- |
| Tên collection trong Java app | 16 tên: 13 root + favorites, noticeReads, messages | Không đồng nghĩa mọi luồng ghi đã hoàn thiện |
| Contract trong Rules ngoài nhóm trên | payments, notifications | Chưa có luồng Repository tương ứng; không xác nhận document tồn tại |
| Mẫu đường dẫn trong kiểm kê | 18: 15 root contract + 3 subcollection | 16 mẫu thuộc nhóm app tham chiếu, 2 Rules-only |
| Model lưu độc lập được rà soát | 13 class; chưa phát hiện property trích được bị thiếu khỏi kiểm kê | RoomBlackout là dữ liệu nhúng; không phải collection thứ 14 |
| Dataset demo | 157 document; chưa phát hiện trường cấp cao nhất ngoài kiểm kê | Đây là dataset local, không phải số document cloud |
| Dữ liệu reference | 8 bản ghi; tên trường đều nằm trong contract RentalPlace | Không suy ra đây là listing của Host hoặc đặt được |
| Cây overview | 36 cạnh chứa; source/target đúng các tầng đã liệt kê | Không phải 36 quan hệ nghiệp vụ |
| Cây chi tiết | Accounts 12 cạnh, Booking 8, Chat 4, Admin 12 | Chỉ biểu diễn quan hệ chứa |

Các đường dẫn subcollection đúng là:

```text
users/{uid}/favorites/{roomId}
users/{uid}/noticeReads/{eventId}
conversations/{guestUid:hostUid}/messages/{messageId}
```

`buildings`, `rooms`, `bookings`, `availabilityMonths` là **các collection gốc ngang cấp**; không đặt rooms dưới Building hoặc bookings dưới Room chỉ vì có trường ID. `messages` được nối từ document hội thoại, không trực tiếp từ collection conversations. Các map/array vẫn nằm trong document.

225 mục trong kiểm kê gồm 36 ở Accounts, 109 ở Booking, 26 ở Chat, 54 ở Admin. Đây là số **dòng định nghĩa có cả đường dẫn lồng/key động**, không phải 225 trường cấp cao nhất, cũng không phải 225 trường luôn bắt buộc có trong document.

## 3. Các phát hiện cần xử lý

### A01 — Quan trọng: cây chứa chưa thay thế được mô tả quan hệ dữ liệu

Cả 5 trang native hiện chỉ có cạnh chứa. README cũng nói liên kết ID nằm trong ghi chú/tài liệu riêng. Vì vậy, nếu chỉ đưa 5 ảnh vào mục thiết kế dữ liệu, người đọc chưa thấy đầy đủ ý nghĩa `rooms.buildingId`, các UID trong booking, lịch tháng, favorites hoặc chat, và các bội số đi kèm.

Căn cứ: [README](README.md), [BookingRepository](../../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository/BookingRepository.java), [ChatRepository](../../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository/ChatRepository.java), [bảng quan hệ trong handoff](../firestore-visualization-handoff.md#4-quan-hệ-cần-vẽ).

Cách xử lý: giữ cây Firestore; bổ sung **bảng liên kết logic** với trường nguồn, đường dẫn đích, bội số và điều kiện. Nếu cần vẽ, chỉ thêm vài liên kết chính bằng nét đứt với chú giải riêng. Không biến liên kết string thành DocumentReference hoặc quan hệ chứa. Không cần kéo toàn bộ 46 mục quan hệ trong handoff lên cùng một hình.

### A02 — Quan trọng: overview chưa phân biệt trực quan contract Rules-only

Trên trang 00, `c-payments`/`c-notifications` vẫn có nhãn `COLLECTION`, document bên cạnh có nhãn `DOCUMENT PATTERN`, cùng cách trình bày với nhóm đang được app tham chiếu. Chỉ có footer giải thích Rules-only. Trang Admin đã có nhãn riêng, nên hai trang chưa nhất quán.

Căn cứ: các cell native trên; [firestore.rules](../../../firestore.rules) dòng 324–325 chỉ quy định quyền; danh sách collection trong Java ở evidence không có hai tên này.

Cách xử lý: đánh dấu **Rules-only / chưa xác nhận dữ liệu**, màu xám hoặc vùng contract dự phòng ngay tại node ở overview; caption ghi rõ 13 root app tham chiếu + 2 root contract. Không gọi cả 15 là collection nghiệp vụ đã triển khai.

### A03 — Quan trọng: trộn trường hiện hành, cũ, dataset-only và Rules-only mà hình in không giải thích

Các ví dụ cần phân biệt ngay trên hình hoặc bảng kèm hình:

| Trường | Điều đã đối chiếu | Nguy cơ hiểu nhầm |
| --- | --- | --- |
| rooms.pricePerDay, rooms.deposit | Model còn giữ; booking mới không lấy đây làm luồng thuê ngày/khoản trả trước | Người đọc tưởng tính năng hiện hành |
| propertyVerifications.evidencePaths | Dataset có biến thể này; AdminRepository đọc evidenceRefs, dự phòng evidencePath | Tưởng ba tên được app hỗ trợ tương đương |
| propertyVerifications.reviewed, schemaVersion | Có nguồn dataset; không suy ra đã xem bằng chứng/luồng ghi đầy đủ | Hiểu cờ reviewed là bằng chứng nghiệp vụ |
| reports.hostResponse | Rules cho phép Host thay đổi; chưa có Repository phản hồi tương ứng trong checkout | Tưởng tính năng phản hồi đã được triển khai |
| users.photoUrl | Rules cho phép cập nhật, chưa có luồng ghi tương ứng đã xác nhận | Nhầm với trường hồ sơ đang hoạt động |

Nhiều giải thích đang được lưu trong **tooltip** của cell. Tooltip không xuất hiện trong bản in. Accounts có chú thích photoUrl, nhưng các trang còn lại chưa có ký hiệu nguồn tương đương.

Căn cứ: [BookingPolicy](../../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/utils/BookingPolicy.java) dòng 66–140; [AdminRepository](../../../app/src/main/java/vn/edu/ueh/phuclu/broomsharing/repository/AdminRepository.java) dòng 293–296, 683–689; [Rules](../../../firestore.rules) dòng 91, 230; dataset và notes của từng trường trong kiểm kê.

Cách xử lý: thêm ký hiệu nguồn/trạng thái ngắn gọn, giải thích các trường ngoại lệ trong bảng bên cạnh. Không xóa trường cũ khỏi kiểm kê chỉ để hình đẹp hơn; không mô tả hợp tất cả nguồn như schema bắt buộc của document mới.

### A04 — Quan trọng: tên và kiểu chưa nói được dữ liệu snapshot

Các trường `bookings.roomName/roomAddress/guestName/guestPhone/priceSnapshot`, `favorites.roomName`, tên trong chat và `rooms.buildingName/address` vẫn xuất hiện như dòng thuộc tính thông thường. Hình không cho biết chúng là bản sao tại thời điểm ghi/ngữ cảnh, không mặc định được đồng bộ khi nguồn đổi.

Căn cứ: BookingPolicy gán từ Room; BookingRepository gán tên/phone Guest trước khi ghi booking; FavoritesRepository ghi roomName; ChatRepository tạo/sao chép ngữ cảnh.

Cách xử lý: ký hiệu `snapshot` hoặc một ghi chú đủ rõ theo nhóm; không vẽ những trường tên này thành đối tượng/collection mới hay khóa liên kết. Đây là thiếu ngữ nghĩa trình bày, chưa phải bằng chứng dữ liệu app sai.

### A05 — Quan trọng cho LaTeX: kích thước trang chưa đạt khả năng đọc

Cỡ chữ dòng tên trường là 15 đơn vị native. Khi fit nguyên bề ngang trang hình vào 160 mm, ước lượng cỡ chữ như sau:

| Trang | Kích thước native | Cỡ chữ dòng trường ước lượng |
| --- | --- | --- |
| Accounts | 2100 × 1500 | 3,24 pt |
| Booking | 3540 × 2140 | 1,92 pt |
| Chat | 2820 × 1200 | 2,41 pt |
| Admin/reference | 3320 × 2140 | 2,05 pt |

Công thức: `15 × (160 / 25,4 × 72) / pageWidth`. Đây là ước lượng fit **nguyên trang**, crop lề có thể cải thiện một phần; chưa render bản LaTeX cuối. Overview có nhãn khác cỡ và không có dòng trường, nên không dùng số 15 để kết luận chính xác từng nhãn của nó.

SVG/PDF giữ nét khi phóng lớn, nhưng không làm chữ nhỏ trở nên đọc được trên giấy. Cần bản hình cho báo cáo với ít thuộc tính chính, bảng thuộc tính ở phần chi tiết/phụ lục hoặc chia panel theo khổ in. Chỉ chọn landscape vẫn chưa đủ để giữ toàn bộ 109 dòng Booking trong một hình dễ đọc.

### A06 — Trung bình: nhãn thẻ document chưa nhất quán

Accounts đặt tiêu đề `{uid}`, `{roomId}`… và nhãn DOCUMENT. Các trang Booking/Chat/Admin đặt tiêu đề lớn bằng tên collection, ví dụ `conversations`, rồi thêm badge DOCUMENT SCHEMA và đường dẫn bên dưới. Source/target vẫn đúng, nên **không kết luận đây là lỗi cấu trúc chứa**, nhưng cách ghi gây nhầm tầng và không đồng nhất với hình mẫu.

Căn cứ: [build_accounts_drawio.py](../../../tools/visualization/build_accounts_drawio.py) hàm document; [build_remaining_drawio.py](../../../tools/visualization/build_remaining_drawio.py) hàm card.

Cách xử lý: tiêu đề document dùng mẫu ID, đường dẫn đầy đủ ở dòng phụ; tên collection giữ ở node COLLECTION. Nếu giữ tiêu đề theo collection, cần nhất quán và phân biệt tầng đủ rõ.

### A07 — Trung bình: unknown/any là ký hiệu kiểm kê, chưa có chú giải trên hình

`unknown`, `array<unknown>`, `map<string,any>` không phải các tên kiểu Firestore cần được diễn giải như kiểu cụ thể. Chúng biểu thị nguồn chưa xác nhận kiểu hoặc map có cấu trúc động. Accounts dùng `unspecified`, các trang khác dùng `unknown`, nên người đọc khó hiểu thống nhất.

Cách xử lý: một chú giải chung: kiểu chưa xác nhận; trường có thể vắng mặt; map động; array phần tử map. Giữ nguyên tính không chắc chắn, không tự gán photoUrl thành string hoặc notifications.readAt thành timestamp như dữ kiện đã được xác nhận. Thụt dòng `↳` cho map/array hiện không sai, nhưng nên thể hiện rõ `blackoutRanges[]` là phần tử map, và `{bookingId}` là key động của hourlyBookings.

### A08 — Phụ thuộc phạm vi 3.2: thiếu ranh giới Authentication/Storage và giải thích truy cập/index

Không có vùng dịch vụ ngoài database trong 5 trang native. Điều này chấp nhận được nếu 3.2 chỉ nói cấu trúc Firestore và có mục khác giải thích hệ thống. Nếu 3.2 là toàn bộ thiết kế lưu trữ, cần thêm đoạn/bảng: users liên hệ Authentication bằng UID; imageUrls/coverUrl và evidenceRefs liên hệ Storage; file không nằm nhị phân trong document.

Cũng cần giải thích truy cập/query ở phần chữ hoặc bảng: publicHosts vẫn yêu cầu active(); inbox dùng memberUids; thông báo app dùng noticeReads + dữ liệu dẫn xuất, không lấy notifications làm nguồn; index local không chứng minh đã triển khai. Không cần nhét tất cả lên sơ đồ.

Căn cứ: [firebase.json](../../../firebase.json), [firestore.rules](../../../firestore.rules), [storage.rules](../../../storage.rules), [firestore.indexes.json](../../../firestore.indexes.json), NoticeRepository và RoomRepository.

Giới hạn triển khai cần giữ nguyên: Rules Storage chỉ chủ tệp đọc verification; AdminRepository có đường đọc bằng chứng nhưng quyền Storage cho Admin chưa hoàn tất. Không mô tả luồng này như đã vận hành đầy đủ.

### A09 — Trung bình: tài liệu và công cụ còn có phiên bản dễ dùng nhầm

- `docs/design/firestore.md` dòng 23 còn nói booking mới DEMO_PENDING; BookingPolicy dòng 130 ghi UNAVAILABLE, và chính tài liệu này dòng 70 cũng ghi UNAVAILABLE. Đây là mâu thuẫn tài liệu đã xác nhận.
- `00-relationships-overview` hiện là cây chứa nhưng filename vẫn mang tên relationships; nhóm JSON FireDraw cũ và nhóm native draw.io không cùng một kiểu thể hiện.
- `build_remaining_drawio.py` còn có hàm tạo view quan hệ cũ và entrypoint chạy hàm đó. Chạy nhầm script này có thể ghi đè một số artifact bằng kiểu hình trước; README hiện chỉ đúng script hierarchy.
- Các SVG hiện có vẫn chứa chuỗi fallback `Text is not SVG - cannot display`; ít nhất ở PNG Admin nhìn thấy dòng nhỏ ở cuối hình. Cần làm sạch và kiểm tra bản xuất cuối trước khi chèn báo cáo; không chỉ kiểm tra XML native.

Cách xử lý: ghi một bộ native làm bản chuẩn; đánh dấu/đổi tên generator cũ, sửa câu DEMO_PENDING sau khi chốt văn bản; xuất lại có kiểm tra ở khổ in thật.

### A10 — Lỗi phương pháp kiểm định trước: độ phủ theo inventory chưa chứng minh inventory đúng

Các generator lấy cùng `firestore-visualization-schema.json` rồi assert output có đủ trường của JSON đó. Kiểm tra này hữu ích để phát hiện thiếu khi chuyển đổi, nhưng **không độc lập xác nhận schema theo code**. Kiểm tra card không chồng nhau cũng không chứng minh connector mang ý nghĩa đúng hoặc chữ đọc được khi in.

Lượt này đã bổ sung đối chiếu Model/dataset/collection độc lập ở mức tên và đọc lại những luồng ghi/đọc đặc thù. Chưa có bằng chứng cho phép gọi đây là kiểm kê toàn cloud hoặc kiểm định toàn bộ tính đúng của app. Không dùng một nhãn PASS để gộp tất cả các mức này.

## 4. Quyết định theo từng trang

| Trang | Đánh giá | Việc cần trước khi chốt |
| --- | --- | --- |
| 00 overview | Đủ 18 mẫu đường dẫn trong kiểm kê; các cạnh chứa đúng | Mark Rules-only tại node; chia/bố trí lại cho khổ in; đặt tên phù hợp cây chứa |
| 01 Accounts | Cây chứa đúng, 36 mục định nghĩa | Bổ sung liên kết UID/bội số ở bảng kèm; phân biệt snapshot/field phát sinh; tăng cỡ chữ in |
| 02 Booking | 4 root ngang cấp đúng, đủ 109 mục theo inventory | Tách bản báo cáo khỏi bảng thuộc tính đầy đủ; chú thích snapshot, legacy, lịch kỹ thuật; bổ sung bảng liên kết |
| 03 Chat | Chuỗi collection/document/subcollection đúng, 26 trường | Thống nhất header document; giải thích cặp có thứ tự và các ID ngữ cảnh; chỉnh khổ in |
| 04 Admin/reference | Root/subcollection không bị đặt sai; 54 mục theo inventory | Gắn nguồn/optional cho field ngoại lệ; giữ Rules-only; nêu giới hạn bằng chứng; sửa artifact xuất và khổ in |

## 5. Bộ nội dung đủ để nghiệm thu mục thiết kế dữ liệu

1. Hình cây tổng quan chính xác, đánh dấu rõ nhóm app tham chiếu và Rules-only.
2. Bốn nhóm chi tiết vẫn giữ tầng Firestore; bản chèn báo cáo chỉ giữ trường cần để hiểu cấu trúc/nghiệp vụ, đầy đủ thuộc tính chuyển sang bảng hoặc phụ lục.
3. Bảng liên kết logic, ghi rõ string ID, bội số, optional và context; không cần thêm một hình lớn rối dây.
4. Chú giải snapshot, map/array, key động, trường cũ, nguồn S-only/D-only và trường chỉ phát sinh sau xử lý.
5. Đoạn mô tả giới hạn triển khai: paymentStatus UNAVAILABLE, lịch PENDING giữ chỗ, noticeReads là receipt, RentalPlace là reference; Auth/Storage và index/quyền trình bày ở nơi phù hợp phạm vi báo cáo.
6. Kiểm tra PDF LaTeX ở kích thước in cuối: tên trường, kiểu, chú giải đọc được; không cắt chữ; caption đủ nghĩa khi tách khỏi IDE/tooltip.

**Trạng thái sau lượt này: đã kiểm định và ghi nhận lỗi; chưa tự sửa/redraw các hình.** Các phát hiện là đầu vào cho lần chỉnh hình, không phải xác nhận rằng các hình đã được sửa. Không có cơ sở để thêm collection Guest/Host/Admin, reviews, refund hoặc payout vào schema hiện tại.
