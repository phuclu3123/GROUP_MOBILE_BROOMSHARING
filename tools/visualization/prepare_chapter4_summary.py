"""Select representative schema fields for the concise Chapter 4 table."""
import json
import re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
REPORT=ROOT/'Latex/FINAL_ANDROID (1)'
schema=json.loads((ROOT/'docs/design/firestore-visualization-schema.json').read_text(encoding='utf-8'))
docs={d['path']:d for d in schema['documents']}

def esc(s):
    return ''.join({'\\':r'\textbackslash{}','&':r'\&','%':r'\%','_':r'\_',
                    '{':r'\{','}':r'\}','<':r'\textless{}','>':r'\textgreater{}'}.get(c,c) for c in s)

def code(s):
    return r'\texttt{'+r'\allowbreak{}'.join(esc(t) for t in re.split(r'(?<=[a-z])(?=[A-Z])|(?<=[./_,<>])',s) if t)+'}'

rows=[
('users/{uid}','Hồ sơ, vai trò và trạng thái tài khoản.',['uid','name','role','status','phoneVerified']),
('publicHosts/{uid}','Bản chiếu tên và trạng thái Host.',['uid','name','status']),
('usernames/{normalizedUsername}','Ánh xạ tên đăng nhập sang tài khoản.',['uid','email']),
('hostApplications/{uid}','Đơn đăng ký và kết quả duyệt Host.',['applicantUid','status','rejectionReason','reviewedBy']),
('users/{uid}/favorites/{roomId}','Phòng được tài khoản lưu yêu thích.',['roomId','roomName','createdAt']),
('users/{uid}/noticeReads/{eventId}','Mốc đã đọc một sự kiện dẫn xuất.',['readAt']),
('buildings/{buildingId}','Địa điểm và chủ sở hữu tòa nhà.',['ownerUid','name','address','location']),
('rooms/{roomId}','Không gian, chế độ thuê và trạng thái tin.',['buildingId','ownerUid','activeMode','pricePerHour','approvalStatus','listingStatus']),
('bookings/{bookingId}','Yêu cầu thuê, các bên tham gia và giá đã chốt.',['roomId','guestUid','ownerUid','status','startAt','endAt','priceSnapshot']),
('availabilityMonths/{roomId_yyyyMM}','Khóa giữ chỗ của một phòng trong một tháng.',['roomId','monthIndex','fixedBookingId','slotsBusy','hourlyBookings']),
('conversations/{guestUid:hostUid}','Thành viên, preview và bộ đếm hội thoại.',['memberUids','lastText','sequence','guestRead','hostRead']),
('conversations/{guestUid:hostUid}/messages/{messageId}','Tin nhắn thuộc một hội thoại.',['senderUid','text','sequence','createdAt']),
('propertyVerifications/{verificationId}','Hồ sơ và bằng chứng chờ xác minh.',['ownerUid','verificationType','evidenceRefs','status']),
('reports/{reportId}','Phản ánh về phòng và kết quả xử lý.',['reporterUid','roomId','ownerUid','status','action']),
('auditLogs/{auditId}','Dấu vết người thực hiện và phần thay đổi.',['actorUid','targetPath','action','before','after']),
('rentalPlaces/{placeId}','Địa điểm tham khảo và căn cứ nguồn.',['name','location','sourceUrl','checkedAt'])
]
out=[r'\begingroup\small\setstretch{1.08}',
     r'\begin{longtable}{@{}>{\raggedright\arraybackslash}p{3.6cm}>{\raggedright\arraybackslash}p{3.75cm}>{\raggedright\arraybackslash}p{7.9cm}@{}}',
     r'\caption{Các mẫu document và thuộc tính tiêu biểu}\label{tab:ch4-entities-summary}\\',
     r'\toprule Đường dẫn & Vai trò & Thuộc tính tiêu biểu và kiểu \\ \midrule\endfirsthead',
     r'\toprule Đường dẫn & Vai trò & Thuộc tính tiêu biểu và kiểu \\ \midrule\endhead',
     r'\midrule\multicolumn{3}{r}{\small Còn tiếp ở trang sau}\\\endfoot',
     r'\bottomrule\endlastfoot']
selected=[]
for path,purpose,fields in rows:
    actual={f['name']:f['type'] for f in docs[path]['fields']}
    grouped={}
    for name in fields:
        grouped.setdefault(actual[name],[]).append(name)
        selected.append({'path':path,'name':name,'type':actual[name]})
    cells=['; '.join(', '.join(code(n) for n in names)+' ('+code(typ)+')' for typ,names in grouped.items())]
    suffix=r'; \ldots' if len(fields)<len(actual) else '.'
    out.append(code(path)+' & '+esc(purpose)+' & '+cells[0]+suffix+r' \\')
out += [r'\end{longtable}\endgroup','']
(REPORT/'chapters/chapter4-entities-summary.tex').write_text('\n'.join(out),encoding='utf-8')
(REPORT/'Resources/chapter4/summary-manifest.json').write_text(json.dumps({
    'patterns':16,'selectedFields':selected,
    'selection':'Representative fields for the main text; full source tables retained but not included in the report.',
    'appendixAdded':False},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Prepared 16 summary rows with',len(selected),'source-verified representative fields.')
