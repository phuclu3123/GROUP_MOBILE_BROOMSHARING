# Đối soát bộ đếm phòng cũ

`reconcile_room_counters.py` là công cụ bảo trì một lần, không phải backend của app. Mã nghiệp vụ Android vẫn là Java/XML. Công cụ dùng tài khoản Firebase Auth Admin đang ACTIVE trong file credential local được Git bỏ qua; Security Rules đang triển khai vẫn được áp dụng.

Chạy mặc định chỉ đọc, kiểm booking và lịch ở cùng snapshot, lưu kế hoạch và bản sao có kiểu Firestore vào thư mục `output/verification/room-counter-compatibility-2026-10-25/` đã được bỏ qua bởi Git:

```powershell
python tools/maintenance/reconcile_room_counters.py
```

Sau khi kiểm tra kế hoạch và được chủ dự án chấp thuận cập nhật dữ liệu:

```powershell
python tools/maintenance/reconcile_room_counters.py --apply --plan output/verification/room-counter-compatibility-2026-10-25/plan.json
```

Chỉ bổ sung bộ đếm thiếu hoặc `-1`; bộ đếm đã biết nhưng khác booking sẽ làm công cụ dừng để xem xét. Đếm PENDING, CONFIRMED, CHECKED_IN theo trạng thái lưu, không tự hoàn tất hoặc hủy vì thời gian đã qua. Một booking quý vẫn tính là một booking, dù giữ ba document tháng.

Trước khi ghi phải khớp cả liên kết booking → lịch và lịch → booking, tập slot, loại lịch cố định/giờ và không có slot chồng. Mỗi phòng được đọc lại trong snapshot nhất quán; room counter và audit được ghi nguyên tử với điều kiện `updateTime` của phòng. Mọi transaction booking trong app đều tác động document phòng; nếu có thay đổi đồng thời thì điều kiện ghi thất bại và công cụ đọc/đối soát lại. Không dùng quyền dịch vụ quản trị để bỏ qua Rules.

Sau chuyển đổi, công cụ kiểm toàn bộ bộ đếm và đọc lại audit, so sánh booking/lịch với bản trước, kiểm các trường khác của phòng giữ nguyên. Nếu một người khác thay đổi dữ liệu đồng thời khiến so sánh cuối khác, công cụ dừng kết luận nghiệm thu; dùng bản backup và progress để đối chiếu trước khi chạy lại. Không phục hồi mù quáng bộ đếm cũ khi booking đã thay đổi.

Kiểm thử trước trên emulator:

```powershell
$env:RUN_COUNTER_EMULATOR = 'true'
python tools/testing/test_room_counter_migration.py
```

Database QA là `counter_compatibility_qa`, khác database cloud `broomsharing`. Có 13 ca kiểm dữ liệu và một ca tích hợp, gồm retry khi thay đổi đồng thời, retry không nhân audit và quyền Host/Guest. Emulator không chứng minh xác thực cloud; bản chuyển đổi cloud và Android SDK có receipt riêng.

Tham chiếu: [read-only transaction](https://firebase.google.com/docs/firestore/reference/rest/v1/TransactionOptions), [atomic commit](https://firebase.google.com/docs/firestore/reference/rest/v1/projects.databases.documents/commit), [updateTime precondition](https://firebase.google.com/docs/firestore/reference/rest/v1/Precondition). REST read-write transaction với Firebase ID token không dùng trong công cụ này.
