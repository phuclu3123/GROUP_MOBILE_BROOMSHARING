"""Prepare traceable Chapter 4 tables and exact code excerpts, without app edits."""
import hashlib
import json
import re
from pathlib import Path

BASE=Path(__file__).resolve().parents[2]
TEX=BASE/'Latex/FINAL_ANDROID (1)'
RES=TEX/'Resources/chapter4'
CODE=RES/'code'
CODE.mkdir(exist_ok=True)
SCHEMA=json.loads((BASE/'docs/design/firestore-visualization-schema.json').read_text(encoding='utf-8'))
DOCS={d['path']:d for d in SCHEMA['documents']}
COMMENTARY=json.loads((Path(__file__).with_name('chapter4_commentary.json')).read_text(encoding='utf-8'))


def escape(s):
    chars={'\\':r'\textbackslash{}','&':r'\&','%':r'\%','$':r'\$','#':r'\#','_':r'\_','{':r'\{','}':r'\}',
           '~':r'\textasciitilde{}','^':r'\textasciicircum{}','<':r'\textless{}','>':r'\textgreater{}'}
    return ''.join(chars.get(c,c) for c in str(s))


def code(s):
    tokens=re.split(r'(?<=[a-z])(?=[A-Z])|(?<=[./_,<>])',s)
    return r'\texttt{'+r'\allowbreak{}'.join(escape(x) for x in tokens if x)+'}'

def prose(s,label):
    """Render source commentary while escaping literal percent signs and IDs."""
    pieces=re.split(r'(\[\[.*?\]\]|<TABLE>)',s)
    return ''.join(
        code(p[2:-2]) if p.startswith('[[') and p.endswith(']]')
        else r'Bảng~\ref{'+label+'}' if p=='<TABLE>'
        else escape(p) for p in pieces)


GROUPS=[
    ('Tài khoản, bản chiếu và dữ liệu thuộc tài khoản',[
        ('users/{uid}','Hồ sơ tài khoản','Document sử dụng UID của Firebase Authentication. Các trường quyền và trạng thái thuộc cùng một hồ sơ; Guest, Host và Admin không được tách thành ba collection.'),
        ('publicHosts/{uid}','Bản chiếu Host','Document cùng UID với hồ sơ Host và chỉ chứa ba trường. Quyền đọc yêu cầu tài khoản đang hoạt động; tên publicHosts không đồng nghĩa với truy cập ẩn danh.'),
        ('usernames/{normalizedUsername}','Chỉ mục tên đăng nhập','Tên đăng nhập chuẩn hóa nằm trong document ID. Document lưu UID và email để luồng đăng nhập tra cứu thông tin trước khi xác thực.'),
        ('hostApplications/{uid}','Đơn đăng ký Host','Document ID là UID người nộp. Khi đơn bị từ chối được nộp lại, luồng hiện tại cập nhật cùng document thay vì tạo lịch sử nhiều đơn độc lập.'),
        ('users/{uid}/favorites/{roomId}','Phòng yêu thích','Document nằm dưới hồ sơ tài khoản, với ID trùng roomId. UID người lưu được xác định từ đường dẫn cha, không có trường userId riêng trong document.'),
        ('users/{uid}/noticeReads/{eventId}','Trạng thái đã đọc','Document chỉ ghi thời điểm đã đọc một sự kiện dẫn xuất. Event ID được xây từ booking, trạng thái và mốc chính sách, không phải mã document của một collection thông báo khác.')]),
    ('Không gian, đặt chỗ và lịch giữ chỗ',[
        ('buildings/{buildingId}','Thông tin tòa nhà','Building mô tả địa điểm và chủ sở hữu. Getter getLocation() tạo biểu diễn GeoPoint từ tọa độ khi đủ điều kiện; không mặc định mọi document cũ đều có đồng thời các biểu diễn vị trí.'),
        ('rooms/{roomId}','Thông tin không gian cho thuê','Room là đơn vị được thuê độc quyền trong một khoảng thời gian. Capacity mô tả sức chứa, còn lịch giữ chỗ không quản lý tồn kho từng ghế hoặc cho phép nhiều booking chồng lên cùng không gian.'),
        ('bookings/{bookingId}','Thông tin đặt chỗ','Luồng tạo hiện tại ghi schemaVersion bằng 2, paymentStatus bằng UNAVAILABLE và snapshot khoản trả trước dự kiến 40% tiền thuê. Các giá trị mặc định hoặc dữ liệu cũ được phân biệt trong phần mô tả từng trường.'),
        ('availabilityMonths/{roomId_yyyyMM}','Lịch giữ chỗ theo tháng','Document dùng cho một phòng và một tháng, với monthIndex là số YYYYMM. Lịch chỉ lưu khóa kỹ thuật và booking ID; không lưu tên, số điện thoại hoặc các thông tin liên hệ của người thuê.')]),
    ('Hội thoại và tin nhắn',[
        ('conversations/{guestUid:hostUid}','Thông tin hội thoại','Document ID ghép guestUid và hostUid theo đúng thứ tự vai trò. Một hội thoại được dùng cho cặp tài khoản đó; roomId và bookingId ghi ngữ cảnh khởi tạo, không tạo hội thoại riêng cho từng booking.'),
        ('conversations/{guestUid:hostUid}/messages/{messageId}','Thông tin tin nhắn','Tin nhắn nằm trong subcollection của một document hội thoại. Sequence xác định thứ tự; roomId và roomName có thể bổ sung ngữ cảnh phòng thuộc Host của hội thoại.')]),
    ('Xác minh, phản ánh, nhật ký và địa điểm tham khảo',[
        ('propertyVerifications/{verificationId}','Hồ sơ xác minh','AdminRepository đọc và xét duyệt hồ sơ theo loại xác minh. Danh sách evidenceRefs được ưu tiên, evidencePath là dạng đơn dự phòng; các biến thể chỉ thấy trong dataset không được xem là contract đọc hiện hành.'),
        ('reports/{reportId}','Báo cáo về phòng','Luồng tạo báo cáo lấy ownerUid từ document phòng và ghi trạng thái OPEN. Các trường kết luận, hành động xử lý và người duyệt chỉ phát sinh khi Admin giải quyết báo cáo.'),
        ('auditLogs/{auditId}','Nhật ký thao tác','Audit lưu chủ thể thực hiện, đích và nội dung thay đổi. Before và after là map động theo hành động, có thể chỉ ghi phần thay đổi; không mặc định là bản sao đầy đủ của document.'),
        ('rentalPlaces/{placeId}','Địa điểm tham khảo','RentalPlace là dữ liệu địa điểm từ nguồn công khai được dùng trên bản đồ. Nó không phải tin do Host trong ứng dụng đăng và không được dùng làm Room để đặt chỗ.')]),
]

source_fields=[];excluded=[]
lines=[]
for group,items in GROUPS:
    lines += [r'\Needspace{10\baselineskip}',r'\subsubsection{'+group+'}', '']
    for path,title,description in items:
        doc=DOCS[path]
        label='tab:ch4-'+re.sub(r'[^A-Za-z0-9]+','-',path).strip('-')
        commentary=COMMENTARY['entities'][path]
        current_fields=[f for f in doc['fields'] if f['notes'].split(';')[0].strip() not in {'D','D-only'}]
        short_table=len(current_fields)<=9
        columns=r'@{}>{\raggedright\arraybackslash}p{4.35cm}>{\raggedright\arraybackslash}p{2.75cm}>{\raggedright\arraybackslash}p{8.15cm}@{}'
        lines += [r'\Needspace{7\baselineskip}',prose(commentary['before'],label), '']
        if short_table:
            lines += [r'\par\medskip\noindent\begin{minipage}{\linewidth}\small\setstretch{1.12}',
                      r'\captionof{table}{'+title+' tại '+code(path)+r'}\label{'+label+r'}',
                      r'\begin{tabular}{'+columns+r'}',
                      r'\toprule Trường & Kiểu dữ liệu & Ý nghĩa và ràng buộc \\ \midrule']
        else:
            lines += [
          r'\begingroup\small\setstretch{1.12}',
          r'\begin{longtable}{@{}>{\raggedright\arraybackslash}p{4.35cm}>{\raggedright\arraybackslash}p{2.75cm}>{\raggedright\arraybackslash}p{8.15cm}@{}}',
          r'\caption{'+title+' tại '+code(path)+r'}\label{tab:ch4-'+re.sub(r'[^A-Za-z0-9]+','-',path).strip('-')+r'}\\',
          r'\toprule Trường & Kiểu dữ liệu & Ý nghĩa và ràng buộc \\ \midrule\endfirsthead',
          r'\multicolumn{3}{l}{\small '+title+r' (tiếp theo)}\\',
          r'\toprule Trường & Kiểu dữ liệu & Ý nghĩa và ràng buộc \\ \midrule\endhead',
          r'\midrule\multicolumn{3}{r}{\small Còn tiếp ở trang sau}\\\endfoot',
          r'\bottomrule\endlastfoot']
        for field in doc['fields']:
            prefix=field['notes'].split(';')[0].strip()
            if prefix in {'D','D-only'}:
                excluded.append({'path':path,'field':field['name'],'reason':'Dataset-only; not a current code read/write contract'})
                continue
            note=field['notes'].split(';',1)[-1].strip()
            if note:note=note[0].upper()+note[1:]
            if (path,field['name'])==('users/{uid}','photoUrl'):
                note='Rules cho phép cập nhật tên trường này nhưng chưa kiểm tra kiểu; Repository chưa có luồng ghi tương ứng được xác nhận'
            elif (path,field['name'])==('reports/{reportId}','hostResponse'):
                note='Rules cho phép Host cập nhật phản hồi nhưng chưa kiểm tra kiểu; Repository chưa có luồng ghi phản hồi được xác nhận'
            elif (path,field['name'])==('rooms/{roomId}','locationVerification'):
                note='Mặc định UNVERIFIED; thao tác Admin có thể ghi ADMIN_REVIEWED'
            elif (path,field['name'])==('rooms/{roomId}','photosVerification'):
                note='Trạng thái xác minh ảnh; mặc định UNVERIFIED, thao tác Admin có thể ghi ADMIN_REVIEWED'
            note=note.replace('>=', 'ít nhất ').replace('→',' sang ')
            row_end=r'. \\*' if len(current_fields)>1 and field in [current_fields[0],current_fields[-2]] else r'. \\'
            lines.append(code(field['name'])+' & '+code(field['type'])+' & '+escape(note.rstrip('.'))+row_end)
            source_fields.append({'path':path,'name':field['name'],'type':field['type']})
        if short_table:
            lines += [r'\bottomrule\end{tabular}\end{minipage}\par\medskip']
        else:
            lines += [r'\end{longtable}\endgroup']
        lines += ['',r'\Needspace{3\baselineskip}',prose(commentary['after'],label),'']
(TEX/'chapters/chapter4-entities.tex').write_text('\n'.join(lines)+'\n',encoding='utf-8')

relations=[]
for key,title,indices in [
    ('accounts','Tài khoản và dữ liệu thuộc tài khoản',[0,1,2,3,4,10,21,22,23]),
    ('booking','Không gian, booking và lịch tháng',[5,6,7,8,9,11,12,13,14,15,16,17,18,19,20]),
    ('chat','Hội thoại, thành viên và ngữ cảnh',[24,25,26,27,28,29,30,31,32,33]),
    ('administration','Hồ sơ quản trị và đích nhật ký',[34,35,36,37,38,39,40,41,42,43])]:
    label='tab:ch4-relations-'+key
    commentary=COMMENTARY['relationships'][key]
    relations += [r'\Needspace{7\baselineskip}',prose(commentary['before'],label),'',
                  r'\begingroup\small\setstretch{1.12}',r'\begin{longtable}{@{}>{\raggedright\arraybackslash}p{5.1cm}>{\raggedright\arraybackslash}p{4.0cm}>{\raggedright\arraybackslash}p{6.15cm}@{}}',
                  r'\caption{Liên kết của nhóm '+title.lower()+r'}\label{'+label+r'}\\',
                  r'\toprule Nguồn liên kết & Đích & Bội số và điều kiện \\ \midrule\endfirsthead',
                  r'\toprule Nguồn liên kết & Đích & Bội số và điều kiện \\ \midrule\endhead',
                  r'\midrule\multicolumn{3}{r}{\small Còn tiếp ở trang sau}\\\endfoot',
                  r'\bottomrule\endlastfoot']
    for position,i in enumerate(indices):
        r=SCHEMA['relationships'][i]
        note=r['cardinalityAndNotes'].replace('→','đến')
        note=note.replace('1; User–Room là N–N thông qua Favorite','1 Room cho mỗi Favorite; quan hệ nhiều với nhiều giữa User và Room thông qua Favorite')
        note=note.replace('–',' đến ')
        note=note.replace('; có thể gộp nhãn để tránh đường trùng','')
        note=note.replace('liên kết đa hình/dynamic, không vẽ thành cột FK cố định hoặc khẳng định mọi targetPath đều trỏ document tồn tại',
                          'Đường dẫn đích thay đổi theo hành động; không có collection đích cố định và cần kiểm tra sự tồn tại của document')
        if note:note=note[0].upper()+note[1:]
        row_end=r'. \\*' if position in [0,len(indices)-2] else r'. \\'
        relations.append(code(r['source'])+' & '+code(r['target'])+' & '+escape(note.rstrip('.'))+row_end)
    relations += [r'\end{longtable}\endgroup','',r'\Needspace{3\baselineskip}',prose(commentary['after'],label),'']
(TEX/'chapters/chapter4-relationships.tex').write_text('\n'.join(relations)+'\n',encoding='utf-8')

snippets=[]
root='app/src/main/java/vn/edu/ueh/phuclu/broomsharing/'
for filename,source,first,last in [
    ('room-query.java',root+'repository/RoomRepository.java',43,50),
    ('booking-query-listener.java',root+'repository/BookingRepository.java',410,422),
    ('chat-listener.java',root+'repository/ChatRepository.java',86,95),
    ('booking-prepare.java',root+'repository/BookingRepository.java',188,200),
    ('booking-commit.java',root+'repository/BookingRepository.java',203,215),
    ('rules-roles.txt','firestore.rules',5,11),
    ('rules-held-month.txt','firestore.rules',32,38),
    ('rules-favorites.txt','firestore.rules',112,117),
    ('storage-evidence.txt','storage.rules',21,27),
]:
    p=BASE/source;original=p.read_text(encoding='utf-8').splitlines()
    chunk='\n'.join(original[first-1:last])+'\n'
    (CODE/filename).write_text(chunk,encoding='utf-8')
    snippets.append({'file':filename,'source':source,'firstLine':first,'lastLine':last,
                     'sourceSha256':hashlib.sha256(p.read_bytes()).hexdigest(),
                     'excerptSha256':hashlib.sha256(chunk.encode()).hexdigest()})
(RES/'code-manifest.json').write_text(json.dumps({'snippets':snippets,'tableDocumentPatterns':16,
    'tableFieldRows':len(source_fields),'includedFields':source_fields,'datasetOnlyExcluded':excluded,
    'deferredContracts':['payments','notifications'],'logicalRelationshipRows':44},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Prepared:',len(source_fields),'field rows, 16 document tables, 44 relationship rows,',len(snippets),'exact excerpts.')
