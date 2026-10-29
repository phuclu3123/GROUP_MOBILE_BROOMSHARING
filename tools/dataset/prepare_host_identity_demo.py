"""Create clearly fictional test fixtures, never a copy of a government ID."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'data/demo/host-identity';OUT.mkdir(parents=True,exist_ok=True)
def font(size):return ImageFont.truetype('C:/Windows/Fonts/arial.ttf',size)
for side,number in [('front',1),('back',2)]:
 image=Image.new('RGB',(1200,760),'#e5f2f2');draw=ImageDraw.Draw(image)
 draw.rounded_rectangle((24,24,1176,736),radius=24,outline='#2a7375',width=4)
 draw.text((65,60),'BROOMSHARING | HO SO HOST GIA LAP',font=font(40),fill='#1b414c')
 draw.text((65,130),'KHONG PHAI GIAY TO TUY THAN',font=font(48),fill='#a02631')
 if side=='front':
  draw.rounded_rectangle((70,230,345,560),radius=12,fill='#c5dadd')
  draw.ellipse((140,270,275,405),fill='#5e8992');draw.ellipse((95,410,320,620),fill='#5e8992')
  draw.text((405,260),'Ten: NGUOI DUNG DEMO',font=font(34),fill='#193e4b')
  draw.text((405,340),'Ma: DEMO-HOST-001',font=font(34),fill='#193e4b')
  draw.text((405,420),'Muc dich: KIEM THU DO AN',font=font(30),fill='#193e4b')
 else:
  for i,line in enumerate(['MAT SAU - DU LIEU GIA LAP','Khong thuoc bat ky ca nhan nao.','Chi phuc vu kiem thu upload, xem va duyet.','Khong co gia tri nhan dang hay phap ly.']):
   draw.text((75,250+i*68),line,font=font(32),fill='#193e4b')
 draw.rectangle((50,625,1150,710),fill='#a02631')
 draw.text((95,640),'DEMO  /  NOT VALID  /  MAT '+str(number),font=font(43),fill='white')
 image.save(OUT/(side+'.jpg'),quality=92)
for name,color,text in [('avatar-guest','#216b76','DG'),('avatar-host','#735bb7','DH')]:
 image=Image.new('RGB',(600,600),color);draw=ImageDraw.Draw(image)
 draw.ellipse((70,70,530,530),outline='white',width=12)
 draw.text((145,185),text,font=font(190),fill='white')
 draw.text((195,470),'DEMO',font=font(42),fill='white')
 image.save(OUT/(name+'.jpg'),quality=92)
(OUT/'README.md').write_text('''# Tệp nghiệm thu hồ sơ Host\n\nHai ảnh front.jpg/back.jpg được vẽ bằng hình học và chữ, ghi rõ DEMO/NOT VALID. Không mô phỏng chính xác mẫu CCCD, không chứa quốc huy, mã số căn cước hay dữ liệu cá nhân thật. Hai avatar chỉ là chữ viết tắt, không lấy chân dung người khác.\n\nDùng kiểm thử hai cách chọn ảnh từ điện thoại và nhập URL. Tệp minh chứng công khai trong scope đồ án; không dùng làm xác minh danh tính thật.\n''',encoding='utf-8')
print('Created 2 fictional document sides and 2 initial-only avatars.')
