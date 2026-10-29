"""Native, editable draw.io figures for the Chapter 2 source audit."""
from pathlib import Path
import xml.etree.ElementTree as ET
import html
import json
import re

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/design/chapter2'
FONT = 'Segoe UI'
INK = '#243748'
LINE = '#82919E'
BLUE = ('#EDF4FB', '#7494B7')
GREEN = ('#EEF5EE', '#7C9C82')
PURPLE = ('#F3EFF8', '#9B8AAF')
GOLD = ('#FFF6E6', '#C4A36C')
RED = ('#FBEFEC', '#BD8982')
GRAY = ('#F3F5F7', '#A0ABB5')


def style(**kw):
    return ';'.join(str(k) + '=' + str(v) for k, v in kw.items()) + ';'


class Page:
    def __init__(self, name, width, height, title, subtitle=''):
        self.name, self.width, self.height = name, width, height
        self.model = ET.Element('mxGraphModel', dx='0', dy='0', grid='1', gridSize='10',
                                guides='1', tooltips='1', connect='1', arrows='1', fold='1',
                                page='1', pageScale='1', pageWidth=str(width),
                                pageHeight=str(height), background='#ffffff', math='0', shadow='0')
        self.root = ET.SubElement(self.model, 'root')
        ET.SubElement(self.root, 'mxCell', id='0')
        ET.SubElement(self.root, 'mxCell', id='1', parent='0')
        self.boxes, self.texts = {}, {}
        self.n = 0
        # Page names carry figure titles; the report supplies its own captions.

    def cell(self, id, text, x, y, w, h, st, parent='1'):
        c = ET.SubElement(self.root, 'mxCell', id=id, value=html.escape(text).replace('\n', '<br>'),
                          style=st, vertex='1', parent=parent)
        ET.SubElement(c, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h),
                      attrib={'as': 'geometry'})
        self.boxes[id] = (x,y,w,h)
        self.texts[id] = text
        return id

    def text(self, id, text, x, y, w, h, size=17, bold=False, color=INK, align='left'):
        return self.cell(id,text,x,y,w,h,style(text='',html=1,whiteSpace='wrap',
            fillColor='none',strokeColor='none',fontFamily=FONT,fontSize=size,
            fontColor=color,fontStyle=1 if bold else 0,align=align,verticalAlign='middle'))

    def box(self, id, text, x, y, w=210, h=62, palette=BLUE, size=18, shape='rect', bold=False):
        extra = {'shape':'rhombus'} if shape=='decision' else {'shape':'ellipse'} if shape=='ellipse' else {'rounded':1,'arcSize':12}
        return self.cell(id,text,x,y,w,h,style(**extra,whiteSpace='wrap',html=1,
            fillColor=palette[0],strokeColor=palette[1],strokeWidth=1.3,
            fontFamily=FONT,fontSize=size,fontColor=INK,fontStyle=1 if bold else 0,spacing=8))

    def note(self,id,text,x,y,w,h=70):
        # Explanatory prose belongs in the report, outside the exported figure.
        return None

    def actor(self,id,text,x,y):
        return self.cell(id,text,x,y,48,82,style(shape='umlActor',verticalLabelPosition='bottom',
            verticalAlign='top',html=1,outlineConnect=0,fillColor='#FFFFFF',strokeColor=INK,
            strokeWidth=1.5,fontFamily=FONT,fontColor=INK,fontSize=18,fontStyle=0))

    def edge(self,src,dst,text='',points=None,kind='flow',exit=(1,.5),entry=(0,.5),color=LINE,label_position=None):
        if src not in self.boxes or dst not in self.boxes:
            if kind=='note': return None
            raise ValueError('Missing endpoint: '+src+' -> '+dst)
        self.n+=1
        s=dict(edgeStyle='orthogonalEdgeStyle',rounded=0,html=1,strokeColor=color,
               strokeWidth=1.3,fontFamily=FONT,fontSize=15,fontColor=INK,
               labelBackgroundColor='#FFFFFF',endArrow='block',endFill=1,
               exitX=exit[0],exitY=exit[1],entryX=entry[0],entryY=entry[1],jettySize=16)
        if kind in ('tree','association'): s.update(endArrow='none')
        if kind=='association': s.update(edgeStyle='none')
        if kind in ('include','extend'): s.update(dashed=1,dashPattern='5 4',endArrow='open',endFill=0)
        if kind=='generalization': s.update(endArrow='block',endFill=0,endSize=16,strokeWidth=1.5)
        if kind=='note': s.update(dashed=1,endArrow='none',strokeColor='#A5AFB9')
        c=ET.SubElement(self.root,'mxCell',id=f'e{self.n}',value=html.escape(text),
                        style=style(**s),edge='1',parent='1',source=src,target=dst)
        g=ET.SubElement(c,'mxGeometry',relative='1',attrib={'as':'geometry'})
        if label_position is not None: g.set('x',str(label_position))
        if points:
            a=ET.SubElement(g,'Array',attrib={'as':'points'})
            for x,y in points: ET.SubElement(a,'mxPoint',x=str(x),y=str(y))
        return c

    def boundary(self,id,text,x,y,w,h):
        return self.cell(id,text,x,y,w,h,style(rounded=0,html=1,whiteSpace='wrap',
            fillColor='#FFFFFF',strokeColor='#BAC5CF',strokeWidth=1.2,verticalAlign='top',
            spacingTop=16,fontFamily=FONT,fontSize=19,fontColor=INK,fontStyle=1))

    def band(self,id,text,x,y,w,h,palette=GRAY):
        self.cell(id,'',x,y,w,h,style(rounded=1,arcSize=7,html=1,fillColor=palette[0],
                  strokeColor='none',fontFamily=FONT,fontColor=INK))
        self.text(id+'_label',text,x+16,y+8,w-32,26,16,bold=True,color=palette[1])


def save(name,pages):
    root=ET.Element('mxfile',host='Electron',agent='BroomSharing',version='29.0.0',
                    type='device',pages=str(len(pages)))
    for i,p in enumerate(pages):
        d=ET.SubElement(root,'diagram',id=f'{name[:8]}-{i:02d}',name=p.name)
        d.append(p.model)
    ET.indent(root,space='  ')
    OUT.mkdir(parents=True,exist_ok=True)
    ET.ElementTree(root).write(OUT/(name+'.drawio'),encoding='utf-8',xml_declaration=True)


# Each branch contains two concrete subfunctions, each with two short leaves.
# Wording stays at business level; Java is the evidence, not a class inventory.
FUNCTIONS = [
('01. Tài khoản và phiên', 'TÀI KHOẢN VÀ PHIÊN ĐĂNG NHẬP', [
 ('Đăng ký tài khoản', [('Khai báo thông tin',['Họ tên, username, email, điện thoại','Nhập và kiểm tra mật khẩu']),('Khởi tạo tài khoản',['Kiểm tra email và username trùng','Tạo danh tính và hồ sơ GUEST'])]),
 ('Đăng nhập và đăng xuất', [('Xác thực đăng nhập',['Dùng email hoặc username','Kiểm tra mật khẩu với Firebase']),('Quản lý phiên',['Kiểm tra hồ sơ và trạng thái ACTIVE','Đăng xuất, xóa trạng thái phiên'])]),
 ('Khôi phục mật khẩu', [('Yêu cầu khôi phục',['Nhập email tài khoản','Gửi liên kết qua Firebase']),('Đặt mật khẩu mới',['Mở liên kết đặt lại mật khẩu','Cập nhật qua Firebase Authentication'])]),
]),
('02. Khám phá không gian', 'TÌM KIẾM VÀ KHÁM PHÁ KHÔNG GIAN', [
 ('Tìm kiếm và lọc', [('Từ khóa và khu vực',['Tìm tên phòng, tòa nhà, địa chỉ','Lọc quận/khu vực trên dữ liệu đã tải']),('Thuộc tính và sắp xếp',['Lọc loại phòng, hình thức, sức chứa','Lọc mức giá; sắp theo giá/sức chứa'])]),
 ('Bản đồ và chỉ đường', [('Hiển thị vị trí',['Hiển thị marker phòng và tòa nhà','Lấy vị trí khi được cấp quyền']),('Tìm đường và địa điểm',['Chọn điểm đến, xem tuyến đường','Xem các địa điểm tham khảo'])]),
 ('Xem chi tiết', [('Thông tin không gian',['Ảnh, diện tích, tiện ích, nội quy','Địa chỉ và thông tin Host công khai']),('Giá và tương tác',['Xem đơn giá, chế độ thuê, đánh giá','Yêu thích, liên hệ hoặc mở đặt chỗ'])]),
]),
('03. Tạo yêu cầu đặt chỗ', 'TẠO YÊU CẦU ĐẶT CHỖ', [
 ('Chọn kỳ thuê', [('Theo hình thức của phòng',['HOURLY: chọn khung giờ trong ngày','FIXED: chọn một tháng hoặc một quý']),('Kiểm tra kỳ thuê',['Thời gian bắt đầu ở tương lai','Đối chiếu giờ mở cửa và ngày khóa'])]),
 ('Báo giá và giữ lịch', [('Tính chi phí',['Tính tổng theo giá của phòng','Chụp giá và khoản trả trước 40%']),('Ghi yêu cầu nguyên tử',['Kiểm tra lịch, giữ slot hoặc tháng','Tạo PENDING cùng revision và audit'])]),
 ('Theo dõi kết quả', [('Kết quả tạo yêu cầu',['Trùng lịch: không tạo booking','Chống tạo trùng cùng yêu cầu']),('Chờ Host xử lý',['PENDING đã giữ lịch, chưa thu tiền','Host duyệt trước giờ bắt đầu'])]),
]),
('04. Lịch đặt và hủy', 'THEO DÕI BOOKING VÀ HỦY ĐẶT CHỖ', [
 ('Theo dõi lịch đặt', [('Danh sách và chi tiết',['Xem booking theo tài khoản/vai trò','Xem thời gian, giá và trạng thái']),('Lưu lịch sử',['Theo dõi cập nhật trong ứng dụng','Giữ booking sau khi đã kết thúc'])]),
 ('Hủy trước giờ bắt đầu', [('Kiểm tra quyền và hạn',['Guest/Host hủy PENDING hoặc CONFIRMED','Host hủy phải có lý do']),('Cập nhật kết quả',['Chuyển CANCELLED và nhả lịch','Lưu lý do, người hủy và nhật ký'])]),
 ('Hết hạn và tiền', [('Yêu cầu chờ quá hạn',['PENDING đến giờ bắt đầu: EXPIRED','Đồng bộ khi app tải/đối soát dữ liệu']),('Đối soát sau hủy',['Tính chính sách trên tiền thực nhận','Ghi khoản cần xem xét, chưa hoàn tự động'])]),
]),
('05. Hồ sơ Host và xác minh', 'ĐĂNG KÝ HOST VÀ XÁC MINH KHÔNG GIAN', [
 ('Nộp đơn làm Host', [('Thông tin người nộp',['Họ tên, liên hệ, loại cá nhân/tổ chức','Thông tin và hai ảnh giấy tờ DEMO']),('Gửi và theo dõi hồ sơ',['Gửi đơn PENDING, theo dõi kết quả','Sửa và gửi lại hồ sơ bị từ chối'])]),
 ('Xác minh không gian', [('Chọn đối tượng sở hữu',['Chọn phòng hoặc tòa nhà của Host','Chọn xác minh vị trí hoặc hình ảnh']),('Nộp minh chứng',['Mô tả, tải ảnh/PDF minh chứng demo','Gửi hồ sơ PENDING và theo dõi kết quả'])]),
 ('Admin xét duyệt', [('Xem hồ sơ và minh chứng',['Kiểm tra nội dung và tệp đã gửi','Đánh dấu đã xem ảnh trước khi duyệt']),('Ghi quyết định',['Duyệt Host và cấp quyền cùng giao dịch','Duyệt/từ chối hồ sơ, lưu lý do và audit'])]),
]),
('06. Quản lý không gian', 'QUẢN LÝ TÒA NHÀ VÀ BÀI ĐĂNG PHÒNG', [
 ('Khai báo không gian', [('Tòa nhà và phòng',['Tòa nhà, địa chỉ, tọa độ, liên hệ','Phòng, loại, diện tích và sức chứa']),('Thông tin sử dụng',['Ảnh, tiện ích và nội quy','Chính sách thuê và gia hạn'])]),
 ('Giá và lịch cho thuê', [('Chọn hình thức cho thuê',['Theo giờ hoặc theo tháng/quý','Đổi hình thức khi hết booking giữ chỗ']),('Thiết lập lịch',['Giờ mở cửa, bước giờ, thời lượng tối thiểu','Ngày hoạt động, ngày/khoảng khóa lịch'])]),
 ('Quản lý bài đăng', [('Lưu và gửi duyệt',['Lưu nháp hoặc gửi PENDING_REVIEW','Theo dõi APPROVED hoặc REJECTED']),('Hiển thị và chỉnh sửa',['Tạm dừng INACTIVE, mở lại ACTIVE','Không sửa lịch xung đột booking đang giữ'])]),
]),
('07. Duyệt đơn và sử dụng', 'XỬ LÝ BOOKING VÀ GHI NHẬN SỬ DỤNG', [
 ('Xử lý yêu cầu', [('Xem và duyệt',['Xem khách thuê và kỳ thuê','PENDING → CONFIRMED trước giờ bắt đầu']),('Từ chối hoặc hủy',['Từ chối PENDING kèm lý do','Nhả lịch khi REJECTED/CANCELLED'])]),
 ('Ghi nhận lượt sử dụng', [('Khách đến nhận phòng',['Host xác nhận trong thời gian thuê','CONFIRMED → CHECKED_IN']),('Kết thúc lượt thuê',['CHECKED_IN → COMPLETED sau giờ kết thúc','Xác nhận sử dụng muộn cần lý do'])]),
 ('Xử lý ngoại lệ', [('Khách không đến',['Sau giờ kết thúc, chưa check-in','Ghi NO_SHOW kèm lý do và nhả lịch']),('Host bị khóa',['Admin xử lý từng booking, có lý do','Lượt đã dùng có thể khép TERMINATED'])]),
]),
('08. Chat và liên hệ', 'CHAT VÀ LIÊN HỆ GIỮA HAI BÊN', [
 ('Quản lý hội thoại', [('Khởi tạo hội thoại',['Mở từ phòng hoặc từ booking','Một hội thoại theo cặp Guest–Host']),('Danh sách hội thoại',['Xem tin gần nhất và người gửi','Hiển thị số tin đến chưa đọc'])]),
 ('Trao đổi tin nhắn', [('Gửi và nhận',['Gửi văn bản, nhận cập nhật thời gian thực','Lưu tin và preview trong cùng giao dịch']),('Theo dõi đã đọc',['Hiển thị thời điểm và thứ tự tin','Chỉ cập nhật mốc đã đọc của chính mình'])]),
 ('Liên hệ qua điện thoại', [('Thông tin liên hệ',['Xem liên hệ trong ngữ cảnh được phép','Dùng số điện thoại đã công bố']),('Mở ứng dụng gọi',['Mở trình quay số trên thiết bị','Người dùng tự xác nhận cuộc gọi'])]),
]),
('09. Đánh giá sau sử dụng', 'ĐÁNH GIÁ KHÔNG GIAN SAU SỬ DỤNG', [
 ('Kiểm tra điều kiện', [('Quyền của người thuê',['Đúng Guest trong booking','Đã có xác nhận việc sử dụng']),('Trạng thái hợp lệ',['COMPLETED đã hết thời gian thuê','Hoặc TERMINATED đã khép sự cố'])]),
 ('Gửi đánh giá', [('Chấm điểm và nhận xét',['Một điểm tổng quan từ 1 đến 5 sao','Nhận xét từ 10 đến 1.000 ký tự']),('Lưu một lần mỗi booking',['Dùng booking làm định danh nhận xét','Gửi lại cùng nội dung không tạo trùng'])]),
 ('Xem đánh giá phòng', [('Danh sách nhận xét',['Đọc đánh giá theo phòng','Sắp xếp từ mới đến cũ']),('Thông tin nhận xét',['Xem người đánh giá và điểm sao','Đọc nội dung và thời điểm nhận xét'])]),
]),
('10. Báo cáo và phản hồi', 'BÁO CÁO PHÒNG VÀ XỬ LÝ VI PHẠM', [
 ('Người thuê gửi báo cáo', [('Nội dung phản ánh',['Chọn phòng và lý do báo cáo','Mô tả; gắn booking khi có ngữ cảnh']),('Theo dõi báo cáo',['Tạo OPEN, xem báo cáo đã gửi','Theo dõi phản hồi và kết quả xử lý'])]),
 ('Host phản hồi', [('Tiếp nhận phản ánh',['Xem báo cáo về phòng mình sở hữu','Kiểm tra mô tả và booking liên quan']),('Gửi phản hồi',['Trả lời bằng văn bản khi còn OPEN','Không tự sửa kết luận hay đóng báo cáo'])]),
 ('Admin kết luận', [('Đối chiếu và xử lý',['Xem báo cáo, phòng và phản hồi Host','Không mặc định đọc hội thoại riêng']),('Ghi quyết định',['RESOLVED hoặc DISMISSED cùng audit','Có thể đình chỉ phòng hoặc khóa Host'])]),
]),
('11. Quản trị và sự cố', 'KIỂM DUYỆT VÀ XỬ LÝ SỰ CỐ', [
 ('Kiểm duyệt nội dung', [('Hàng chờ xét duyệt',['Đơn Host, bài đăng, hồ sơ minh chứng','Xem thông tin và ghi lý do quyết định']),('Quản lý phòng',['Duyệt APPROVED hoặc từ chối REJECTED','Đình chỉ SUSPENDED hoặc gỡ đình chỉ'])]),
 ('Khóa quyền cho thuê', [('Trạng thái Host',['Khóa BLOCKED hoặc mở lại ACTIVE','Đồng bộ trạng thái Host công khai']),('Giữ lịch sử nghiệp vụ',['Không xóa booking khi khóa Host','Hạn chế thao tác theo quyền thực tế'])]),
 ('Khép từng vụ việc', [('Booking của Host bị khóa',['Admin hủy lượt chưa check-in có lý do','Lượt đã check-in: hoàn tất hoặc chấm dứt']),('Xác minh việc sử dụng',['Sửa NO_SHOW khi có xác nhận sử dụng','Ghi lý do, mốc sử dụng và audit'])]),
]),
('12. Theo dõi và nhật ký', 'THEO DÕI HOẠT ĐỘNG VÀ NHẬT KÝ', [
 ('Tổng hợp của Host', [('Theo dõi bài đăng',['Tổng số phòng đang sở hữu','Số phòng hoạt động và chờ duyệt']),('Theo dõi yêu cầu',['Số booking còn PENDING','Xem danh sách và lịch booking'])]),
 ('Hàng đợi quản trị', [('Theo nhóm đối tượng',['Hồ sơ Host, phòng và minh chứng','Báo cáo OPEN; trạng thái Host/phòng']),('Đối soát và theo dõi',['Đối soát số booking giữ lịch của phòng','Xem/tải lại hàng đợi sau xử lý'])]),
 ('Nhật ký nghiệp vụ', [('Nội dung bản ghi',['Người thao tác, hành động, đối tượng','Dữ liệu trước/sau, lý do và thời gian']),('Tra cứu nhật ký',['Admin xem nhật ký mới nhất','Lưu audit cùng thay đổi được kiểm soát'])]),
]),
('13. Hồ sơ và thông báo', 'HỒ SƠ, ẢNH VÀ HỘP CẬP NHẬT', [
 ('Hồ sơ và ảnh đại diện', [('Thông tin tài khoản',['Xem tên, username, email và điện thoại','Xem hồ sơ Host công khai']),('Ảnh đại diện',['Chọn từ thư viện hoặc nhập URL ảnh','Tải lên, lưu URL và đồng bộ ảnh Host'])]),
 ('Quản lý ảnh minh chứng', [('Ảnh không gian',['Ảnh phòng và ảnh bìa tòa nhà','Cloudinary lưu tệp, Firestore lưu URL']),('Minh chứng demo',['Hai ảnh giấy tờ DEMO của đơn Host','Ảnh/PDF vị trí và hình ảnh không gian'])]),
 ('Yêu thích và cập nhật', [('Phòng đã lưu',['Thêm hoặc bỏ phòng yêu thích','Xem danh sách riêng theo tài khoản']),('Hộp cập nhật trong app',['Xem cập nhật booking, chat và báo cáo','Đánh dấu đã đọc theo người dùng'])]),
]),
('14. Thanh toán demo', 'THANH TOÁN KHOẢN TRẢ TRƯỚC QUA PAYOS', [
 ('Kiểm tra điều kiện', [('Booking được hỗ trợ',['Đã CONFIRMED, chưa đến giờ bắt đầu','Đúng Guest và Host của kênh demo']),('Xác định khoản thu',['Lấy 40% từ snapshot giá đã chốt','Không nhận số tiền tùy ý từ ứng dụng'])]),
 ('Tạo và hiển thị QR', [('Backend Java tạo giao dịch',['Xác thực phiên, tạo link payOS','Một mã đơn cho mỗi booking']),('Ứng dụng thanh toán',['Hiện QR hoặc mở trang payOS','Chủ động kiểm tra trạng thái giao dịch'])]),
 ('Đồng bộ kết quả', [('Xác nhận phía server',['Webhook qua relay; kiểm tra chữ ký','Đối chiếu trạng thái và số tiền với payOS']),('Cập nhật kết quả',['Ghi PAID hoặc yêu cầu đối soát riêng','App nhận cập nhật tiền, không tự check-in'])]),
]),
]


def decompositions():
    pages=[]
    p=Page('00. Tổng quan chức năng',1320,930,'PHÂN RÃ CHỨC NĂNG BROOMSHARING',
           'Các nhóm chức năng nghiệp vụ; sơ đồ chi tiết được tách theo từng trang.')
    p.box('root','BROOMSHARING',470,110,380,62,BLUE,22,bold=True)
    groups=[('Tài khoản và hồ sơ',['Đăng ký, đăng nhập, khôi phục','Hồ sơ, avatar, quyền Host']),
            ('Khám phá và tương tác',['Tìm/lọc, bản đồ, xem chi tiết','Yêu thích, chat và liên hệ']),
            ('Đặt chỗ và sử dụng',['Giữ lịch, duyệt, hủy, hết hạn','Check-in, không đến, đánh giá']),
            ('Quản lý không gian',['Tòa nhà, bài đăng, hình thức thuê','Giá, ảnh, lịch và khóa lịch']),
            ('Kiểm duyệt và hỗ trợ',['Duyệt Host/phòng, xác minh demo','Báo cáo, sự cố và nhật ký']),
            ('Thanh toán và cập nhật',['payOS: trả trước 40% sau xác nhận','Trạng thái tiền, hộp cập nhật'])]
    for i,(name,leaves) in enumerate(groups):
        x=45+(i%3)*430;y=270+(i//3)*280
        p.box('g'+str(i),name,x,y,370,62,[BLUE,GREEN,PURPLE][i%3],20,bold=True)
        for j,t in enumerate(leaves):p.box(f'g{i}_{j}',t,x,y+88+j*64,370,50,GRAY,18)
        for j in range(2):
            p.edge('g'+str(i),f'g{i}_{j}',kind='tree',exit=(0,.5),entry=(0,.5),
                   points=[(x-15,y+31),(x-15,y+113+j*64)])
        # A shared trunk routes outside the group rectangles.
        top=245 if i<3 else 525
        mid=x+185
        p.edge('root','g'+str(i),kind='tree',exit=(.5,1),entry=(.5,0),
               points=[(660,205),(25,205),(25,top),(mid,top)] if i>=3 else [(660,235),(mid,235)])
    pages.append(p)
    for name,title,branches in FUNCTIONS:
        # Three branches, four leaves each. Equal vertical rhythm across all 14 pages.
        p=Page(name,1420,950,title,'Phân rã theo mục tiêu nghiệp vụ, từ nhóm chức năng đến thao tác cụ thể.')
        p.box('root',title,35,450,230,110,BLUE,18,bold=True)
        for i,(b,subs) in enumerate(branches):
            cy=235+i*270
            p.box(f'b{i}',b,330,cy-35,235,70,GREEN,19,bold=True)
            p.edge('root',f'b{i}',kind='tree',points=[(295,505),(295,cy)])
            for j,(sub,leaves) in enumerate(subs):
                sy=cy-68+j*136
                p.box(f's{i}{j}',sub,635,sy-28,245,56,GOLD,18)
                p.edge(f'b{i}',f's{i}{j}',kind='tree',points=[(600,cy),(600,sy)])
                for k,leaf in enumerate(leaves):
                    ly=sy-32+k*64
                    p.box(f'l{i}{j}{k}',leaf,950,ly-26,425,52,GRAY,18)
                    p.edge(f's{i}{j}',f'l{i}{j}{k}',kind='tree',points=[(915,sy),(915,ly)])
        pages.append(p)
    save('usecase_decomposition_v2',pages)


def role_page(name,title,role,goals,palette=BLUE,auxiliary=False):
    height=120+len(goals)*88
    width=1210 if auxiliary else 790
    p=Page(name,width,height,title)
    p.boundary('sys','BroomSharing',210,30,width-240,height-60)
    p.actor('actor',role,75,height/2-40)
    for i,goal in enumerate(goals):
        y=100+i*88
        p.box('u'+str(i),goal,280,y,310,62,palette,18,shape='ellipse')
        p.edge('actor','u'+str(i),kind='association')
    return p


def usecases():
    pages=[]
    p=Page('00. Tổng quát',1420,1200,'SƠ ĐỒ USE CASE TỔNG QUÁT')
    p.boundary('sys','BroomSharing',200,30,1010,1140)
    p.actor('guest','Người thuê',75,330)
    p.actor('host','Host',75,770)
    p.actor('admin','Admin',1290,690)
    p.actor('payos','payOS',1290,190)
    left=[('Quản lý tài khoản và hồ sơ',100),('Thanh toán trả trước 40%',200),
          ('Tìm kiếm và xem không gian',300),('Tạo yêu cầu đặt chỗ',400),
          ('Theo dõi booking',500),('Chat với bên cho thuê',600),
          ('Đánh giá không gian',700),('Gửi báo cáo phòng',800),
          ('Quản lý không gian cho thuê',900),('Xử lý booking của khách thuê',990),
          ('Chat với khách thuê',1080)]
    for i,(t,y) in enumerate(left):
        p.box('g'+str(i),t,265,y,310,60,GOLD if i==1 else BLUE if i<8 else GREEN,18,shape='ellipse')
        p.edge('guest' if i<8 else 'host','g'+str(i),kind='association')
    right=[('Xét duyệt hồ sơ Host',330),('Xét duyệt minh chứng vị trí/ảnh',420),
           ('Kiểm duyệt không gian',510),('Khóa hoặc mở khóa Host',600),
           ('Xử lý báo cáo vi phạm',690),('Xử lý booking của Host bị khóa',780),
           ('Xác minh việc sử dụng',870),('Đối soát lịch giữ chỗ của phòng',960),('Tra cứu nhật ký',1050)]
    for i,(t,y) in enumerate(right):
        p.box('a'+str(i),t,810,y,320,60,PURPLE,18,shape='ellipse')
        p.edge('admin','a'+str(i),kind='association',exit=(0,.5),entry=(1,.5))
    p.edge('host','guest',kind='generalization',points=[(30,811),(30,371)],exit=(0,.5),entry=(0,.5))
    p.edge('payos','g1',kind='association',exit=(0,.5),entry=(1,.5))
    pages.append(p)

    guest=role_page('01. Người thuê','USE CASE CỦA NGƯỜI THUÊ','Người thuê',[
        'Đăng ký, đăng nhập\nvà quản lý hồ sơ','Khôi phục mật khẩu',
        'Khám phá và lưu\nkhông gian yêu thích','Xem bản đồ và chỉ đường','Tạo yêu cầu đặt chỗ',
        'Theo dõi booking\nvà hộp cập nhật','Thanh toán trả trước 40%','Chat và liên hệ Host',
        'Đánh giá sau sử dụng','Đăng ký quyền Host','Gửi và theo dõi báo cáo'],BLUE,auxiliary=True)
    guest.box('slot','Kiểm tra kỳ thuê\nvà lịch khả dụng',750,452,350,62,GRAY,18,shape='ellipse')
    guest.edge('u4','slot','«include»',kind='include')
    guest.box('cancel','Hủy trước giờ bắt đầu',750,540,350,62,GRAY,18,shape='ellipse')
    guest.edge('cancel','u5','«extend»',kind='extend',exit=(0,.5),entry=(1,.5))
    guest.box('id_demo','Cung cấp hai ảnh\ngiấy tờ DEMO',750,892,350,62,GRAY,18,shape='ellipse')
    guest.edge('u9','id_demo','«include»',kind='include')
    pages.append(guest)

    host=role_page('02. Host','USE CASE CỦA HOST','Host',[
        'Quản lý tòa nhà','Tạo và sửa bài đăng phòng','Thiết lập giá\nvà lịch cho thuê','Gửi bài đăng xét duyệt',
        'Tạm dừng hoặc mở lại phòng','Xác nhận hoặc từ chối booking','Hủy booking cho thuê',
        'Chat với khách thuê','Ghi nhận khách đến nhận phòng',
        'Hoàn tất lượt thuê','Ghi nhận khách không đến','Gửi minh chứng vị trí/ảnh',
        'Phản hồi báo cáo của khách','Xem lịch và thống kê hoạt động'],GREEN)
    pages.append(host)

    admin=role_page('03. Admin','USE CASE CỦA ADMIN','Admin',[
        'Xét duyệt đơn làm Host','Xét duyệt minh chứng\nvị trí và hình ảnh','Duyệt hoặc từ chối bài đăng',
        'Đình chỉ hoặc gỡ đình chỉ phòng','Khóa hoặc mở khóa Host','Xử lý báo cáo vi phạm',
        'Xử lý booking của Host bị khóa','Xác minh việc sử dụng',
        'Đối soát lịch giữ chỗ của phòng','Tra cứu nhật ký nghiệp vụ'],PURPLE)
    pages.append(admin)
    save('usecase_diagram_v2',pages)


def workflows():
    # Matching centers and explicit grid waypoints avoid automatic staircase bends.
    pages=[]
    p=Page('01. Host và đăng phòng',1540,830,'')
    p.band('hostband','NGƯỜI DÙNG / HOST',30,100,1480,320,GREEN)
    p.band('adminband','ADMIN',30,460,1480,340,PURPLE)
    items=[
        ('apply','Nộp đơn Host\nvà hai ảnh DEMO',70,220,220,80,GREEN,'rect'),
        ('review','Xem hồ sơ\nvà minh chứng',70,570,220,80,PURPLE,'rect'),
        ('accept','Duyệt?',340,560,100,100,GOLD,'decision'),
        ('grant','Cấp quyền HOST\nvà ghi nhật ký',500,570,220,80,PURPLE,'rect'),
        ('asset','Tạo tòa nhà/phòng\nGiá, lịch và ảnh',500,220,220,80,GREEN,'rect'),
        ('submit','Gửi bài đăng\nPENDING_REVIEW',810,220,220,80,GREEN,'rect'),
        ('roomreview','Kiểm tra\nthông tin bài đăng',810,570,220,80,PURPLE,'rect'),
        ('roomok','Duyệt?',1080,560,100,100,GOLD,'decision'),
        ('publish','Host mở cho thuê\nAPPROVED + ACTIVE',1210,220,240,80,GREEN,'rect'),
        ('hostreject','REJECTED\nKèm lý do',290,710,200,60,RED,'rect'),
        ('roomreject','REJECTED\nSửa và gửi lại',1220,710,220,60,RED,'rect'),
    ]
    for id,t,x,y,w,h,col,sh in items:p.box(id,t,x,y,w,h,col,18,sh)
    p.edge('apply','review',exit=(.5,1),entry=(.5,0))
    p.edge('review','accept');p.edge('accept','grant','Có')
    p.edge('accept','hostreject','Không',exit=(.5,1),entry=(.5,0))
    p.edge('hostreject','apply','Bổ sung, gửi lại',points=[(40,740),(40,350),(180,350)],exit=(0,.5),entry=(.5,1),label_position=.65)
    p.edge('grant','asset',exit=(.5,0),entry=(.5,1))
    p.edge('asset','submit');p.edge('submit','roomreview',exit=(.5,1),entry=(.5,0))
    p.edge('roomreview','roomok')
    p.edge('roomok','publish','Có',points=[(1130,400),(1330,400)],exit=(.5,0),entry=(.5,1))
    p.edge('roomok','roomreject','Không',points=[(1130,740)],exit=(.5,1))
    p.edge('roomreject','submit','Gửi lại',points=[(1480,740),(1480,170),(920,170)],exit=(1,.5),entry=(.5,0),label_position=.7)
    pages.append(p)

    p=Page('02. Tạo và duyệt booking',1640,1120,'')
    p.band('guest','NGƯỜI THUÊ',30,100,1580,220,BLUE)
    p.band('system','HỆ THỐNG',30,360,1580,300,GRAY)
    p.band('host','HOST',30,700,1580,240,GREEN)
    items=[
        ('select','Tìm/lọc\nXem chi tiết',70,190,220,80,BLUE,'rect'),
        ('period','Chọn kỳ thuê\nXem báo giá',340,190,220,80,BLUE,'rect'),
        ('request','Gửi yêu cầu',620,190,220,80,BLUE,'rect'),
        ('check','Kiểm tra điều kiện\nvà lịch trong transaction',590,450,280,80,GRAY,'rect'),
        ('available','Hợp lệ?',930,440,100,100,GOLD,'decision'),
        ('pending','PENDING, lịch và audit\nGhi cùng transaction',1120,450,280,80,GRAY,'rect'),
        ('conflict','Không tạo booking\nBáo lỗi / chọn lại',340,450,220,80,RED,'rect'),
        ('review','Xử lý yêu cầu\ntrước giờ bắt đầu',1120,780,280,80,GREEN,'rect'),
        ('choice','Duyệt?',930,770,100,100,GOLD,'decision'),
        ('confirmed','CONFIRMED',620,780,240,80,GREEN,'rect'),
        ('rejected','REJECTED\nLý do + nhả lịch',300,780,240,80,RED,'rect'),
        ('cancelled','CANCELLED\nNhả lịch, giữ lịch sử',40,1000,360,80,RED,'rect'),
        ('expired','EXPIRED\nNhả lịch, giữ lịch sử',1170,1000,360,80,RED,'rect'),
    ]
    for id,t,x,y,w,h,col,sh in items:p.box(id,t,x,y,w,h,col,18,sh)
    p.edge('select','period');p.edge('period','request')
    p.edge('request','check',exit=(.5,1),entry=(.5,0))
    p.edge('check','available');p.edge('available','pending','Có')
    p.edge('available','conflict','Không',points=[(980,600),(450,600)],exit=(.5,1),entry=(.5,1))
    p.edge('conflict','period','Chọn lại',exit=(.5,0),entry=(.5,1))
    p.edge('pending','review',exit=(.5,1),entry=(.5,0))
    p.edge('review','choice',exit=(0,.5),entry=(1,.5))
    p.edge('choice','confirmed','Có',exit=(0,.5),entry=(1,.5))
    p.edge('choice','rejected','Không',points=[(980,915),(420,915)],exit=(.5,1),entry=(.5,1))
    p.edge('pending','cancelled','Yêu cầu hủy hợp lệ',
           points=[(1190,560),(1080,560),(1080,965),(220,965)],exit=(.25,1),entry=(.5,0))
    p.edge('pending','expired','Đồng bộ: tới giờ bắt đầu,\nvẫn PENDING',
           points=[(1560,490),(1560,965),(1350,965)],exit=(1,.5),entry=(.5,0),label_position=.65)
    pages.append(p)

    p=Page('03. Sử dụng và ngoại lệ',1660,1190,'')
    p.band('main','SỬ DỤNG PHÒNG',30,100,1600,250,GREEN)
    p.band('special','CÁC NHÁNH KẾT THÚC',30,400,1600,200,GRAY)
    p.band('admin','ADMIN: XÁC MINH VIỆC SỬ DỤNG',30,800,1600,360,PURPLE)
    p.box('confirmed','CONFIRMED',80,210,280,80,GREEN)
    p.box('checkin','Host ghi nhận khách đến\nCHECKED_IN',520,210,280,80,GREEN)
    p.box('complete','Ghi nhận hoàn tất\nCOMPLETED',1060,210,280,80,GREEN)
    p.edge('confirmed','checkin','Trong kỳ thuê')
    p.edge('checkin','complete','Đã hết kỳ thuê')
    p.edge('confirmed','complete','Đã hết kỳ thuê; xác nhận đã sử dụng, có lý do',
           points=[(220,160),(1200,160)],exit=(.5,0),entry=(.5,0))
    p.box('cancel','CANCELLED\nHủy booking hợp lệ',80,500,280,80,RED)
    p.box('noshow','Khách không đến, ghi lý do\nNO_SHOW',520,500,280,80,RED)
    p.box('terminate','Admin kết thúc sự cố\nTERMINATED',1060,500,280,80,RED)
    p.edge('confirmed','cancel','Yêu cầu hủy hợp lệ',exit=(.5,1),entry=(.5,0))
    p.edge('confirmed','noshow','Đã hết kỳ thuê; chưa check-in',
           points=[(290,440),(660,440)],exit=(.75,1),entry=(.5,0))
    p.edge('checkin','terminate','Host bị khóa; có lý do',
           points=[(660,390),(1200,390)],exit=(.5,1),entry=(.5,0))
    p.box('release','Nhả lịch và ghi nhật ký\nGiữ booking lịch sử',480,650,800,80,BLUE,19)
    p.edge('cancel','release',points=[(220,690)],exit=(.5,1),entry=(0,.5))
    p.edge('noshow','release',exit=(.5,1),entry=(.225,0))
    p.edge('terminate','release',exit=(.5,1),entry=(.9,0))
    p.edge('complete','release',points=[(1540,250),(1540,690)],exit=(1,.5),entry=(1,.5))
    p.box('verify_usage','Admin kiểm tra\nviệc sử dụng',480,880,400,80,PURPLE)
    p.box('used','Đã dùng?',920,870,100,100,GOLD,18,'decision')
    p.box('corrected','COMPLETED\nBổ sung xác nhận, ghi audit',1060,880,400,80,PURPLE)
    p.box('keep','Giữ nguyên trạng thái\nvà lịch sử',820,1060,300,60,GRAY)
    p.edge('release','verify_usage','NO_SHOW/COMPLETED cũ\ncần xác minh',exit=(.25,1),entry=(.5,0),label_position=-.5)
    p.edge('verify_usage','used')
    p.edge('used','corrected','Có')
    p.edge('used','keep','Không',exit=(.5,1),entry=(.5,0))
    pages.append(p)

    p=Page('04. Thanh toán payOS demo',1940,1080,'')
    p.band('app','NGƯỜI THUÊ / ỨNG DỤNG',30,100,1880,220,BLUE)
    p.band('java','BACKEND JAVA',30,360,1880,470,GREEN)
    p.band('provider','PAYOS / RELAY WEBHOOK',30,870,1880,180,GOLD)
    items=[
        ('book','Booking CONFIRMED\nMở thanh toán',60,190,280,80,BLUE,'rect'),
        ('auth','Xác thực Guest, Host demo\nKỳ thuê và snapshot giá',50,460,300,80,GREEN,'rect'),
        ('valid','Hợp lệ?',410,450,100,100,GOLD,'decision'),
        ('create','Tạo/lấy lại mã đơn\nvà link payOS',590,460,280,80,GREEN,'rect'),
        ('qr','Hiển thị QR\nhoặc mở trang payOS',590,190,280,80,BLUE,'rect'),
        ('pay','Guest thanh toán',960,190,280,80,BLUE,'rect'),
        ('provider_pay','payOS ghi nhận\ngiao dịch',960,920,280,80,GOLD,'rect'),
        ('relay','Webhook qua\nWEB_TCC relay',1410,920,280,80,GOLD,'rect'),
        ('verify','Kiểm chữ ký, mã đơn\nvà đối chiếu payOS',1410,460,280,80,GREEN,'rect'),
        ('paid','Ghi PAID hoặc\nREVIEW_REQUIRED',960,460,280,80,GREEN,'rect'),
        ('money_result','Kết quả\nđối chiếu?',1480,580,140,140,GOLD,'decision'),
        ('live','App nhận cập nhật tiền\nthời gian thực',1410,190,280,80,BLUE,'rect'),
        ('invalid','Từ chối tạo QR',90,570,260,60,RED,'rect'),
        ('invalid_event','Không hợp lệ:\ntừ chối cập nhật',1690,620,180,60,RED,'rect'),
        ('unpaid','Chưa xác nhận đủ tiền\nGiữ/cập nhật trạng thái',1410,750,280,60,GRAY,'rect'),
    ]
    for id,t,x,y,w,h,col,sh in items:p.box(id,t,x,y,w,h,col,18,sh)
    p.edge('book','auth',exit=(.5,1),entry=(.5,0))
    p.edge('auth','valid');p.edge('valid','create','Có')
    p.edge('valid','invalid','Không',points=[(460,600)],exit=(.5,1),entry=(1,.5))
    p.edge('create','qr',exit=(.5,0),entry=(.5,1));p.edge('qr','pay')
    p.edge('pay','provider_pay',points=[(1100,330),(920,330),(920,960)],exit=(.5,1),entry=(0,.5))
    p.edge('provider_pay','relay')
    p.edge('relay','verify',points=[(1900,960),(1900,500)],exit=(1,.5),entry=(1,.5))
    p.edge('verify','money_result',exit=(.5,1),entry=(.5,0))
    p.edge('money_result','paid','Đã nhận đủ',points=[(1320,650),(1320,500)],exit=(0,.5),entry=(1,.5))
    p.edge('money_result','invalid_event')
    p.edge('money_result','unpaid',exit=(.5,1),entry=(.5,0))
    p.edge('paid','live',points=[(1100,350),(1550,350)],exit=(.5,0),entry=(.5,1))
    pages.append(p)
    save('workflow_diagram_v2',pages)


def validate():
    findings=[]
    for f in OUT.glob('*.drawio'):
        root=ET.parse(f).getroot()
        for d in root.findall('diagram'):
            cells=d.findall('.//mxCell');byid={c.get('id'):c for c in cells}
            assert len(cells)==len(byid),(f,d.get('name'),'duplicate cell')
            for c in cells:
                if c.get('edge')=='1':
                    assert c.get('source') in byid and c.get('target') in byid,(f,c.attrib)
            findings.append({'file':f.name,'page':d.get('name'),
                             'vertices':sum(c.get('vertex')=='1' for c in cells),
                             'edges':sum(c.get('edge')=='1' for c in cells)})
    (OUT/'diagram-validation.json').write_text(json.dumps(findings,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'files':3,'pages':len(findings),'nativeCells':True}))


if __name__=='__main__':
    decompositions();usecases();workflows();validate()
