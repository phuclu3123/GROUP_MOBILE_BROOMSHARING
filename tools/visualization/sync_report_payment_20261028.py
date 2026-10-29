"""Synchronize report chapters 1/2/4 with the tested instant-payment source.

Only report/design artifacts are edited. Chapter 3 and chapters 5-7 are protected.
"""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / 'Latex/FINAL_ANDROID (1)'
RES = REPORT / 'Resources/chapter4'
SCHEMA_PATH = ROOT / 'docs/design/firestore-visualization-schema.json'
SCHEMA = json.loads(SCHEMA_PATH.read_text(encoding='utf-8'))


def replace(text, old, new):
    if new in text:
        return text
    if old in text:
        return text.replace(old, new)
    assert new in text, 'Missing expected text: ' + old[:100]
    return text


def update_chapters():
    p = REPORT / 'chapters/chapter1.tex'
    t = p.read_text(encoding='utf-8')
    t = replace(t, 'yêu cầu đã gửi còn cần Host xác nhận.',
                'booking chỉ được xác nhận sau khi hệ thống ghi nhận đủ khoản trả trước trong thời hạn thanh toán.')
    t = replace(t, 'Yêu cầu được giữ lịch từ khi tạo và lưu lịch sử sau khi khép.',
                'Lịch được tạm giữ tối đa 5 phút khi khởi tạo thanh toán; booking được lưu lịch sử sau khi khép.')
    t = replace(t, r'khoản trả trước 40\% sau xác nhận',
                r'khoản trả trước 40\%; QR được mở ngay khi đặt chỗ, không qua bước Host duyệt booking')
    p.write_text(t, encoding='utf-8')

    p = REPORT / 'chapters/chapter2.tex'
    t = p.read_text(encoding='utf-8')
    t = replace(t, 'Do yêu cầu đang chờ đã giữ lịch, một yêu cầu khác không được chiếm cùng khoảng thời gian.',
                'Lịch này là phần giữ chỗ tạm trong tối đa 5 phút, không vượt giờ bắt đầu thuê; yêu cầu khác không được chiếm cùng khoảng thời gian trong lúc giữ chỗ còn hiệu lực.')
    t = replace(t, 'Host xác nhận hoặc từ chối trước giờ bắt đầu; người thuê hoặc Host có thể hủy theo điều kiện của booking. Yêu cầu vẫn chờ duyệt khi tới giờ bắt đầu được chuyển thành \\texttt{EXPIRED} lúc ứng dụng đồng bộ, nhả lịch và giữ lịch sử. Cơ chế này chưa có tác vụ máy chủ chạy nền độc lập.',
                r'Ứng dụng mở QR ngay sau bước tạo booking. Backend ghi nhận đủ 40\% đúng hạn mới chuyển booking sang \texttt{CONFIRMED}; Host không duyệt booking mới. Khi hết hạn hoặc hủy, backend đối chiếu trạng thái của payOS trước khi kết thúc link chưa trả, nhả lịch và giữ lịch sử. Nếu chưa xác định được kết quả từ nhà cung cấp, lịch chưa được nhả cho đến khi đối soát được kết quả.')
    t = replace(t, r'Sau khi Host xác nhận, người thuê thanh toán khoản trả trước 40\% trong phạm vi kênh payOS demo của nhóm.',
                r'Người thuê thanh toán khoản trả trước 40\% qua QR trong phạm vi một Host và kênh payOS demo đã cấu hình. Booking đang chờ thanh toán chưa được trình bày như một lượt đặt chỗ thành công.')
    t = replace(t, 'Mã thanh toán hết hiệu lực tại giờ bắt đầu; ứng dụng không tự tạo giao dịch thay thế cho cùng booking đã hết hạn thanh toán.',
                'Thời hạn thanh toán tính từ lúc tạo booking trên máy chủ, tối đa 5 phút và không vượt giờ bắt đầu thuê. Quay lại hoặc tiếp tục thanh toán dùng cùng mã đơn và cùng thời hạn; thao tác này không gia hạn giữ chỗ.')
    t = replace(t, 'Ứng dụng nhận thay đổi để hiển thị trạng thái mới; nhận tiền thành công không tự xác nhận khách đã nhận phòng.',
                r'Khi nhận đủ tiền đúng hạn và khóa lịch còn hợp lệ, backend ghi \texttt{PAID} và xác nhận booking trong transaction. Ứng dụng nhận thay đổi để cập nhật QR, bộ đếm và thông báo; thanh toán thành công không tự ghi nhận khách đã đến nhận phòng.')
    t = replace(t, 'không phải booking đã được Host chấp thuận.',
                'chưa phải booking đã thanh toán thành công. Màn hình tiếp theo mở QR; backend mới có quyền xác nhận kết quả nhận tiền.')
    t = replace(t, 'kể cả khi yêu cầu còn chờ duyệt.', 'kể cả trong thời hạn tạm giữ chờ thanh toán.')
    t = replace(t, 'tạo booking luôn bao gồm kiểm tra khả dụng,', 'đặt chỗ luôn bao gồm kiểm tra khả dụng và mở bước thanh toán,')
    p.write_text(t, encoding='utf-8')
    p = REPORT / 'chapters/chapter2-appendix.tex'
    t = p.read_text(encoding='utf-8')
    t = replace(t, 'QR được tạo cho booking đã xác nhận và đủ điều kiện thanh toán.',
                r'QR được tạo cho booking \texttt{PENDING} đang giữ lịch tạm, với thời hạn tối đa 5 phút. Nhận đủ 40\% đúng hạn mới xác nhận booking; hết hạn chưa trả thì đối soát, kết thúc link và nhả lịch.')
    p.write_text(t, encoding='utf-8')

    p = REPORT / 'chapters/chapter4.tex'
    t = p.read_text(encoding='utf-8')
    t = replace(t, r'Ảnh và bằng chứng được lưu tách khỏi document; Firestore giữ URL hoặc đường dẫn tham chiếu, còn Firebase Storage kiểm soát truy cập các tệp thuộc hệ thống',
                r'Cloudinary lưu ảnh và minh chứng DEMO, còn Firestore giữ URL tham chiếu. Backend Java quản lý giao dịch payOS và trạng thái tiền')
    t = replace(t, '13 collection gốc và ba loại subcollection, tương ứng 16 mẫu document',
                '17 collection gốc và ba loại subcollection, tương ứng 20 mẫu document đang được ứng dụng hoặc backend sử dụng')
    t = replace(t, 'Document còn chứa dữ liệu nhúng', 'Collection thông báo cũ chỉ còn contract trong Rules, không phải nguồn hộp cập nhật đang hoạt động. Document còn chứa dữ liệu nhúng')
    t = replace(t, 'Một Room có nhiều booking qua thời gian.', 'Một Room có nhiều booking qua thời gian.') if 'Một Room có nhiều booking qua thời gian.' in t else t
    t = replace(t, 'Nhật ký & Actor UID', r'Thanh toán & Booking ID của Payment; booking ID trong PaymentOrder. & Mỗi booking dùng một Payment cùng ID; order code ánh xạ về booking để đối soát webhook. Payment không phải subcollection của Booking. \\\nNhật ký & Actor UID'.replace('\\\n', '\\\\'+'\n'))
    t = replace(t, 'PENDING đã giữ lịch, HOURLY', 'PENDING giữ lịch tạm chờ thanh toán trong tối đa 5 phút, HOURLY')
    t = replace(t, 'Transaction duy trì sự nhất quán giữa booking, lịch tháng và revision của phòng.',
                r'Mảng \texttt{pending\allowbreak{}Payment\allowbreak{}Ids} phân biệt các khóa tạm chờ thanh toán với lịch đã xác nhận. Transaction duy trì sự nhất quán giữa booking, lịch tháng và revision của phòng.')
    t = replace(t, 'Thao tác & Dữ liệu & Cách xử lý trong Repository', 'Thao tác & Dữ liệu & Cách xử lý')
    t = replace(t, 'hủy, từ chối hoặc hoàn tất giải phóng khóa lịch tương ứng.', 'hủy, hết hạn hoặc khép lượt giải phóng khóa lịch tương ứng; booking mới được backend xác nhận sau thanh toán.')
    t = replace(t, 'Theo dõi chat &', r'Đối soát thanh toán & Payment, Booking, lịch tháng, Room và AuditLog. & Backend kiểm tra kết quả payOS; xác nhận khi nhận đủ tiền đúng hạn hoặc khép link chưa trả và nhả lịch, không tự hoàn tiền. \\\nTheo dõi chat &'.replace('\\\n', '\\\\'+'\n'))
    t = replace(t, 'Danh sách được lọc trên dữ liệu đã tải rồi phát qua',
                'Khi có từ khóa hoặc bộ lọc, ViewModel tự tải các trang còn lại trước khi kết luận không có kết quả; danh sách được phát qua')
    t = replace(t, 'sắp theo thời điểm bắt đầu giảm dần', 'sắp theo thời điểm tạo booking giảm dần')
    t = replace(t, 'Repository đọc hồ sơ người thuê, phòng, bản chiếu Host, tòa nhà và các tháng lịch liên quan.',
                r'Repository đọc hồ sơ người thuê, phòng, bản chiếu Host, tòa nhà, cấu hình \texttt{appConfig/payments} và các tháng lịch liên quan. Booking mới phải thuộc Host của kênh demo với luồng \texttt{INSTANT\_5M}.')
    t = replace(t, 'Kết quả thành công là booking PENDING cùng phần lịch đã giữ, nên phòng được giữ ngay từ lúc gửi yêu cầu hợp lệ.',
                'Kết quả ghi thành công là booking PENDING và phần lịch tạm giữ; đây chưa phải xác nhận đã thanh toán. Màn hình QR được mở ngay để tiếp tục cùng booking.')
    t = replace(t, r'Trạng thái thanh toán UNAVAILABLE và khoản trả trước dự kiến 40\% chưa thể hiện giao dịch thu tiền. Khi hủy, từ chối hoặc hoàn tất, hệ thống giải phóng khóa lịch tương ứng và giữ lại booking để lưu lịch sử.',
                r'Trường \texttt{paymentFlow=INSTANT\_5M} xác định luồng giữ lịch tạm. Trước khi tạo yêu cầu payOS, booking chưa ghi nhận tiền; sau đó Payment theo dõi mã đơn, số tiền, QR và thời hạn. Backend đối chiếu kết quả nhà cung cấp, số tiền và thời điểm nhận tiền trước khi xác nhận \texttt{PAID/CONFIRMED}. Khi link đã kết thúc mà chưa trả tiền, backend chuyển booking sang \texttt{EXPIRED}, nhả đúng khóa lịch và giữ lịch sử. Tiền đến muộn, sai số tiền hoặc lịch không còn hợp lệ được đưa vào \texttt{REVIEW\_REQUIRED}; chưa có API hoàn tiền tự động.')
    t = replace(t, 'Chat & Chỉ thành viên', r'Thanh toán và cấu hình & Người thuê, Host liên quan và Admin được đọc Payment; client không được tự ghi trạng thái tiền. PaymentOrder chỉ do backend truy cập; cấu hình kênh cho tài khoản hoạt động đọc, không chứa khóa payOS. \\\nChat & Chỉ thành viên'.replace('\\\n', '\\\\'+'\n'))
    old = 'Storage cho đọc công khai ảnh phòng và tòa nhà; ghi hoặc xóa yêu cầu UID sở hữu. Ảnh nhận JPEG, PNG hoặc WebP tối đa 5 MiB; bằng chứng nhận thêm PDF và chỉ chủ UID được đọc. Quyền xử lý hồ sơ xác minh trong Firestore và quyền đọc tệp được áp dụng riêng: Admin được duyệt hồ sơ nhưng chưa được Storage Rules cấp quyền mở tệp bằng chứng.'
    new = r'Backend dùng Firebase Admin SDK nên phải tự xác thực Firebase ID token, kiểm tra người tham gia và đối chiếu kết quả payOS; không dựa vào Rules để xác thực lệnh ghi của server. Khóa dịch vụ được giữ ở môi trường backend. Ảnh và minh chứng DEMO hiện dùng URL Cloudinary; Rules kiểm tra đường dẫn theo UID và đối tượng liên quan. Ảnh giấy tờ DEMO công khai theo URL không được xem là cơ chế bảo vệ giấy tờ cá nhân thật.'
    t = replace(t, old, new)
    t = t.replace(r'\Needspace{35\baselineskip}', r'\Needspace{8\baselineskip}')
    p.write_text(t, encoding='utf-8')


def field(name, typ, note):
    return {'name': name, 'type': typ, 'notes': note}


def update_schema():
    docs = {d['path']: d for d in SCHEMA['documents']}
    def add(path, entries):
        old = {f['name']: f for f in docs[path]['fields']}
        for entry in entries: old[entry['name']] = entry
        docs[path]['fields'] = list(old.values())
    add('users/{uid}', [field('photoUrl','string','M/W/S; URL avatar Cloudinary theo UID, có thể rỗng')])
    add('publicHosts/{uid}', [field('photoUrl','string','M/W/S; ảnh đại diện đồng bộ với hồ sơ Host')])
    add('hostApplications/{uid}', [field('identityImageUrls','array<string>','M/W/S; hai URL ảnh giấy tờ DEMO'),field('evidenceMode','string','M/W/S; DEMO'),field('identityReviewedAt','timestamp/null','M/W; mốc Admin xem ảnh')])
    add('rooms/{roomId}', [field('activeBookingCount','integer','M/W/S; số booking đang chiếm lịch, gồm phần giữ tạm')])
    add('bookings/{bookingId}', [field('paymentFlow','string','M/W/S; INSTANT_5M cho booking mới; rỗng ở dữ liệu tương thích'),field('paidAmount','integer','M/W; số tiền thực nhận do backend đối soát'),field('usageConfirmedAt','timestamp/null','M/W/S; xác nhận đã sử dụng, có thể chưa có'),field('usageConfirmedBy','string','M/W/S; UID xác nhận sử dụng'),field('closedAt','timestamp/null','M/W/S; mốc khép lượt')])
    for f in docs['bookings/{bookingId}']['fields']:
        if f['name']=='status':f['notes']='M/W/S; PENDING, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, REJECTED, EXPIRED, NO_SHOW, TERMINATED; REJECTED thuộc nhánh cũ'
        if f['name']=='paymentStatus':f['notes']='M/W; chưa khởi tạo, PENDING, PAID, REVIEW_REQUIRED hoặc UNPAID khi hết hạn; UNAVAILABLE/DEMO_PENDING thuộc dữ liệu cũ'
        if f['name']=='refundStatus':f['notes']='M/W; NOT_REQUIRED hoặc MANUAL_REVIEW; không xác nhận đã hoàn tiền'
    add('availabilityMonths/{roomId_yyyyMM}', [field('pendingPaymentIds','array<string>','M/W/S; booking đang giữ lịch tạm chờ thanh toán')])
    add('reports/{reportId}', [field('bookingId','string','M/W/S; booking liên quan nếu có, có thể rỗng'),field('hostResponse','string','M/W/S; phản hồi của Host khi báo cáo còn mở')])
    docs.pop('payments/{paymentId}', None)
    payment_types = [('bookingId','string'),('guestUid','string'),('ownerUid','string'),('orderCode','integer'),('amount','integer'),('expiresAt','integer'),('status','string'),('provider','string'),('advancePercent','integer'),('createdAt','timestamp'),('updatedAt','timestamp'),('paymentLinkId','string'),('checkoutUrl','string'),('qrCode','string'),('accountName','string'),('accountNumber','string'),('bin','string'),('paidAmount','integer'),('paidAt','timestamp'),('providerReference','string')]
    for path, fields, status in [
        ('payments/{bookingId}',[field(n,t,'W/R; backend quản lý; expiresAt dùng epoch giây, các trường kết quả phát sinh theo giao dịch') for n,t in payment_types],'repository_referenced'),
        ('reviews/{bookingId}',[field(n,t,'M/W/S; một đánh giá cho lượt thuê đã khép và có xác nhận sử dụng') for n,t in [('bookingId','string'),('roomId','string'),('guestUid','string'),('guestName','string'),('comment','string'),('rating','integer'),('createdAt','timestamp')]],'repository_referenced'),
        ('paymentOrders/{orderCode}',[field('bookingId','string','W/R; ánh xạ mã đơn payOS về booking; chỉ backend truy cập')],'backend_referenced'),
        ('appConfig/payments',[field('flow','string','W/R; INSTANT_5M'),field('demoHostUid','string','W/R; UID Host của kênh demo')],'backend_referenced')]:
        docs[path]={'path':path,'status':status,'fields':fields}
    SCHEMA['documents']=list(docs.values())
    SCHEMA['reviewedAt']='2026-10-28'
    SCHEMA['scope']='Current Android Java/XML, Java payment backend and local Rules; optional Rules-only notifications excluded from implemented document count.'
    SCHEMA['businessConstraints']='Booking mới INSTANT_5M: transaction giữ slot/tháng tối đa 5 phút từ createdAt, không vượt startAt; mở QR ngay. Backend xác nhận đủ 40% đúng hạn và lịch hợp lệ mới CONFIRMED. Link đóng chưa trả thì EXPIRED và nhả lịch. Chưa rõ provider thì đối soát lại; tiền cần xem xét là REVIEW_REQUIRED/MANUAL_REVIEW. Không Host duyệt booking mới; dữ liệu cũ giữ nhánh tương thích. Check-in, sử dụng, no-show và quyền review vẫn kiểm tra riêng.'
    SCHEMA['coverageChecks']={'scope':'Local schema verified from current app/backend; not an exhaustive cloud inventory.','implementedDocumentPatterns':20,'rootCollections':17,'subcollectionPatterns':3,'rulesOnlyPatterns':['notifications/{notificationId}'],'fieldEntries':sum(len(d['fields']) for d in docs.values())}
    SCHEMA['relationships']=[r for r in SCHEMA['relationships'] if not r['source'].startswith('payments.')]
    for source,target,note in [('payments.bookingId / khóa document','bookings/{bookingId}','1 Payment cho một booking; cùng document ID'),('payments.guestUid / ownerUid','users/{uid}','Hai bên tham gia, không phải thông tin tài khoản ngân hàng động của từng Host'),('paymentOrders.bookingId','bookings/{bookingId}','Một mã đơn ánh xạ tới một booking'),('appConfig.demoHostUid','users/{uid}','Một Host demo của kênh hiện tại'),('reviews.bookingId / khóa document','bookings/{bookingId}','0..1 đánh giá cho booking đã sử dụng và khép'),('reviews.roomId','rooms/{roomId}','Một phòng có nhiều đánh giá theo thời gian'),('reviews.guestUid','users/{uid}','Tác giả phải là Guest của booking'),('availabilityMonths.pendingPaymentIds[]','bookings/{bookingId}','Các khóa tạm chờ thanh toán, không thay thế fixedBookingId/hourlyBookings')]:
        if not any(r['source']==source for r in SCHEMA['relationships']):SCHEMA['relationships'].append({'source':source,'target':target,'cardinalityAndNotes':note})
    SCHEMA_PATH.write_text(json.dumps(SCHEMA,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def esc(s):
    return ''.join({'\\':r'\textbackslash{}','&':r'\&','%':r'\%','_':r'\_','{':r'\{','}':r'\}','<':r'\textless{}','>':r'\textgreater{}'}.get(c,c) for c in s)


def code(s):
    return r'\texttt{'+r'\allowbreak{}'.join(esc(x) for x in re.split(r'(?<=[a-z])(?=[A-Z])|(?<=[/.,_<>{}])',s) if x)+'}'


def summary():
    rows=[('users/{uid}','Hồ sơ và quyền tài khoản.',['uid','role','status','photoUrl']),('publicHosts/{uid}','Bản chiếu Host.',['uid','name','photoUrl']),('usernames/{normalizedUsername}','Ánh xạ đăng nhập.',['uid','email']),('hostApplications/{uid}','Đơn cấp quyền Host.',['applicantUid','status','identityImageUrls']),('users/{uid}/favorites/{roomId}','Phòng yêu thích.',['roomId','roomName','createdAt']),('users/{uid}/noticeReads/{eventId}','Mốc đọc sự kiện dẫn xuất.',['readAt']),('buildings/{buildingId}','Địa điểm và chủ sở hữu.',['ownerUid','name','address','location']),('rooms/{roomId}','Không gian và trạng thái tin.',['buildingId','ownerUid','activeMode','activeBookingCount']),('bookings/{bookingId}','Kỳ thuê, trạng thái và giá đã chốt.',['roomId','guestUid','status','paymentFlow','priceSnapshot']),('availabilityMonths/{roomId_yyyyMM}','Khóa lịch chính thức và tạm giữ.',['fixedBookingId','slotsBusy','hourlyBookings','pendingPaymentIds']),('payments/{bookingId}','Giao dịch trả trước.',['orderCode','amount','status','expiresAt']),('paymentOrders/{orderCode}','Ánh xạ mã đơn của backend.',['bookingId']),('appConfig/payments','Cấu hình kênh demo công khai.',['flow','demoHostUid']),('reviews/{bookingId}','Đánh giá sau sử dụng.',['roomId','guestUid','rating','comment']),('conversations/{guestUid:hostUid}','Hội thoại và mốc đọc.',['memberUids','sequence','guestRead','hostRead']),('conversations/{guestUid:hostUid}/messages/{messageId}','Tin nhắn của hội thoại.',['senderUid','text','sequence','createdAt']),('propertyVerifications/{verificationId}','Minh chứng xác minh.',['ownerUid','verificationType','evidenceRefs','status']),('reports/{reportId}','Phản ánh và kết quả xử lý.',['reporterUid','roomId','bookingId','status']),('auditLogs/{auditId}','Dấu vết thay đổi.',['actorUid','targetPath','action','before','after']),('rentalPlaces/{placeId}','Địa điểm tham khảo, không đặt thuê.',['name','location','sourceUrl'])]
    docs={d['path']:d for d in SCHEMA['documents']}
    out=[r'\begingroup\small\setstretch{1.06}',r'\begin{longtable}{@{}>{\raggedright\arraybackslash}p{3.6cm}>{\raggedright\arraybackslash}p{3.75cm}>{\raggedright\arraybackslash}p{7.9cm}@{}}',r'\caption{Các mẫu document và thuộc tính tiêu biểu}\label{tab:ch4-entities-summary}\\',r'\toprule Đường dẫn & Vai trò & Thuộc tính tiêu biểu và kiểu \\ \midrule\endfirsthead',r'\toprule Đường dẫn & Vai trò & Thuộc tính tiêu biểu và kiểu \\ \midrule\endhead',r'\midrule\multicolumn{3}{r}{\small Còn tiếp ở trang sau}\\\endfoot',r'\bottomrule\endlastfoot']
    selected=[]
    for path,purpose,names in rows:
        actual={f['name']:f['type'] for f in docs[path]['fields']};grouped={}
        for name in names:grouped.setdefault(actual[name],[]).append(name);selected.append({'path':path,'name':name,'type':actual[name]})
        fields='; '.join(', '.join(code(n) for n in ns)+' ('+code(typ)+')' for typ,ns in grouped.items())
        out.append(code(path)+' & '+purpose+' & '+fields+(r'; \ldots' if len(names)<len(actual) else '.')+r' \\')
    out.extend([r'\end{longtable}\endgroup',''])
    (REPORT/'chapters/chapter4-entities-summary.tex').write_text('\n'.join(out),encoding='utf-8')
    (RES/'summary-manifest.json').write_text(json.dumps({'patterns':len(rows),'selectedFields':selected,'selection':'Representative fields; current implemented app/backend patterns.','appendixAdded':False},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def snippets():
    base='app/src/main/java/vn/edu/ueh/phuclu/broomsharing/'
    specs=[('room-query.java',base+'repository/RoomRepository.java','Query query =',8),('booking-query-listener.java',base+'repository/BookingRepository.java','.whereEqualTo(field, uid)',13),('chat-listener.java',base+'repository/ChatRepository.java','.orderBy("sequence", Query.Direction.DESCENDING)',8),('booking-prepare.java',base+'repository/BookingRepository.java','BookingPolicy.plan(room, request, System.currentTimeMillis());',12),('booking-commit.java',base+'repository/BookingRepository.java','for (int i = 0; i < months.size(); i++)',12),('rules-roles.txt','firestore.rules','function own(uid)',7),('rules-held-month.txt','firestore.rules','function heldMonth(',7),('rules-favorites.txt','firestore.rules','match /users/{uid}/favorites/{roomId}',7),('storage-evidence.txt','storage.rules','match /verification/',7)]
    result=[]
    # Each excerpt is contiguous and retains exact source lines, including comments.
    for filename,source,anchor,count in specs:
        path=ROOT/source;lines=path.read_text(encoding='utf-8').splitlines()
        matches=[i for i,line in enumerate(lines) if anchor in line]
        if not matches:raise RuntimeError('Excerpt anchor missing: '+source+' '+anchor)
        first=matches[0];last=min(len(lines),first+count)
        text='\n'.join(lines[first:last])+'\n'
        (RES/'code'/filename).write_text(text,encoding='utf-8')
        result.append({'file':filename,'source':source,'firstLine':first+1,'lastLine':last,'sourceSha256':hashlib.sha256(path.read_bytes()).hexdigest(),'excerptSha256':hashlib.sha256(text.encode()).hexdigest()})
    manifest={'snippets':result,'tableDocumentPatterns':20,'includedFields':[{'path':d['path'],**f} for d in SCHEMA['documents'] if d['path']!='notifications/{notificationId}' for f in d['fields']],'deferredContracts':['notifications'],'logicalRelationshipRows':len(SCHEMA['relationships'])}
    (RES/'code-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    p=REPORT/'chapters/chapter4.tex';t=p.read_text(encoding='utf-8')
    for entry in result:
        pattern=r'(\\inputminted[^\n]*'+re.escape(entry['file'])+r'\}\s*\{\\footnotesize[^\n]*dòng )\d+ đến \d+'
        t=re.sub(pattern,lambda m:m[1]+str(entry['firstLine'])+' đến '+str(entry['lastLine']),t)
    p.write_text(t,encoding='utf-8')
    print('Exact snippets:',[(s['file'],s['firstLine'],s['lastLine']) for s in result])


if __name__=='__main__':
    update_chapters();update_schema();summary();snippets()
