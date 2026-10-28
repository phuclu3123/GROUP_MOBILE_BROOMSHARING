"""Add reviewed Vietnamese comments; prove Java tokens are unchanged by this pass."""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/verification/final-qa-2026-10-28'

# Các mô tả bám đúng trách nhiệm lớp, không tạo chú thích lặp cho getter/setter.
HEADERS = {
    'BookingAdapter': 'Bind danh sách booking của người thuê và chuyển các thao tác hợp lệ về màn hình.\nKhông ghi trạng thái hoặc suy ra đã thanh toán từ nội dung hiển thị.',
    'HostBookingAdapter': 'Bind booking của Host theo trạng thái và thời gian thuê.\nCác callback chỉ yêu cầu thao tác; Repository và Rules kiểm lại điều kiện khi ghi.',
    'RoomAdapter': 'Hiển thị phòng thuộc Host, gồm trạng thái xét duyệt, công bố và hình thức thuê.\nMở form sửa hoặc lịch bằng ID phòng, không tự cập nhật lịch từ Adapter.',
    'Room': 'Model phòng lưu dưới rooms/{roomId}, liên kết tòa nhà và chủ sở hữu bằng ID.\nactiveMode chọn HOURLY hoặc FIXED; giá, lịch và trạng thái được kiểm lại khi đặt chỗ.',
    'AuthMessages': 'Ánh xạ lỗi xác thực đã phân loại sang chuỗi giao diện.\nKhông hiển thị trực tiếp nội dung exception có thể chứa thông tin kỹ thuật.',
    'BaseAuthActivity': 'Khung chung cho các biểu mẫu xác thực: trạng thái bận, thông báo và điều hướng.\nXóa nội dung mật khẩu theo vòng đời màn hình, không lưu mật khẩu vào Bundle.',
    'ResultCallback': 'Hợp đồng trả dữ liệu hoặc lỗi từ Repository về bên gọi.\nBên gọi quản lý trạng thái tải và vòng đời; callback không đại diện một lần ghi thành công nếu chưa nhận onSuccess.',
    'AuthError': 'Phân loại lỗi Firebase Auth thành mã dùng chung trong luồng đăng nhập và khôi phục mật khẩu.',
    'GoogleMapsLink': 'Tạo URL bản đồ dự phòng từ tọa độ hoặc địa chỉ đã mã hóa.\nMở ứng dụng bên ngoài không tạo booking và không thay đổi dữ liệu vị trí trong Firestore.',
    'HostBookingsActivity': 'Màn hình Host theo dõi booking, lịch và các thao tác sử dụng phòng.\nBooking INSTANT_5M do backend xác nhận khi nhận tiền; quyền xử lý lịch sử không cấp quyền duyệt lượt mới.',
}

METHODS = {
    'AdminRepository': {
        'load': 'Đọc và chuẩn hóa đúng nhóm hàng đợi quản trị; các loại hồ sơ vẫn có collection và điều kiện xử lý riêng.',
        'review': 'Đọc lại người xử lý và đối tượng trong transaction trước khi duyệt.\nCấp quyền Host hoặc hạn chế tài khoản/phòng đi cùng bản ghi xét duyệt và audit tương ứng.',
        'evidence': 'Đọc bằng chứng từ nguồn được chấp nhận; quyền xem hồ sơ không tự cấp quyền tải tệp ở dịch vụ lưu trữ.',
        'validEvidence': 'Kiểm định dạng và nguồn bằng chứng trước khi mở, tránh dùng một URL tùy ý làm chứng cứ đã xét duyệt.',
    },
    'HostRepository': {
        'watchOwned': 'Nghe dữ liệu thuộc đúng Host hiện tại; bên gọi phải tháo listener khi không còn dùng màn hình.',
        'submitHostApplication': 'Gửi hoặc bổ sung hồ sơ theo trạng thái đơn hiện có; nộp đơn không tự cấp role HOST.',
        'checkHostApplicationStatus': 'Đọc hồ sơ của người nộp để phục hồi biểu mẫu và kết quả xét duyệt.',
        'addBuilding': 'Kiểm phiên Host và dữ liệu tòa nhà, cố định ownerUid theo tài khoản đang đăng nhập trước khi lưu.',
        'saveRoom': 'Đọc phòng cũ và lịch trước khi lưu thay đổi; chặn sửa cấu hình lịch khi còn booking giữ chỗ.\nKhông dùng giá trị bộ đếm do biểu mẫu gửi lên để bỏ qua khóa lịch.',
        'getStats': 'Tổng hợp phòng và booking thuộc Host; số tiền thuê không đồng nghĩa tiền ngân hàng đã nhận.',
        'getBookings': 'Đọc lượt thuê thuộc Host để hiển thị; chuyển trạng thái vẫn đi qua BookingRepository.',
        'updateBookingStatus': 'Chuyển yêu cầu sang cơ chế transaction chung để booking, lịch và audit không bị cập nhật rời nhau.',
        'toggleListingStatus': 'Kiểm chủ sở hữu và trạng thái xét duyệt trước khi mở hoặc tạm dừng nhận yêu cầu mới.',
    },
    'PaymentRepository': {
        'request': 'Gửi ID token Firebase và bookingId tới backend; không gửi số tiền từ form làm căn cứ thanh toán.\ncreate chọn tạo/lấy lại QR, còn đối soát chỉ đọc kết quả; client không được tự ghi PAID.',
        'watch': 'Nghe document giao dịch của booking, chỉ nhận dữ liệu phù hợp phiên đang đăng nhập.',
        'watchMine': 'Nghe giao dịch của chính Guest để dựng nhắc thanh toán; không tạo giao dịch chỉ vì mở danh sách.',
    },
    'RoomRepository': {
        'page': 'Đọc một trang phòng đã duyệt và đang hoạt động theo document ID, giữ cursor của nguồn trước khi ghép dữ liệu.',
        'detail': 'Đọc lại một phòng từ server và ghép thông tin chủ/tòa nhà; không dùng thẻ danh sách cũ làm căn cứ đặt chỗ.',
        'enrich': 'Ghép Building và PublicHost, loại mục không còn đáp ứng điều kiện công bố trước khi trả cho giao diện.',
    },
    'MediaRepository': {
        'upload': 'Upload tuần tự theo thứ tự ảnh đã chọn, kiểm phiên và thư mục của đúng tài khoản/đối tượng.',
        'discardRecent': 'Chỉ dọn các tệp vừa upload mà còn delete token hợp lệ; không coi đây là quyền xóa mọi tệp Cloudinary.',
        'close': 'Giải phóng tài nguyên upload khi ViewModel không còn sử dụng Repository.',
    },
    'PropertyVerificationRepository': {
        'submit': 'Lưu hồ sơ và URL minh chứng đã kiểm tra, giữ ownerUid theo phiên và trạng thái chờ xét duyệt.',
        'watch': 'Theo dõi hồ sơ xác minh của Host, không trộn kết quả duyệt bài đăng với kết quả duyệt bằng chứng.',
    },
    'ReportRepository': {
        'submit': 'Đọc lại phòng và người báo trước khi tạo hồ sơ; không nhận ownerUid tùy ý từ màn hình.',
        'reply': 'Host phản hồi báo cáo còn mở thuộc tài sản của mình, không được sửa kết luận quản trị.',
        'resolve': 'Ghi kết luận cùng tác động quản trị và audit trong transaction; không xóa lịch sử booking liên quan.',
    },
    'ReviewRepository': {
        'watch': 'Nghe nhận xét theo phòng, sắp xếp mới nhất và bỏ snapshot cache/ghi chưa xác nhận để tránh hiển thị thành công sớm.',
        'submit': 'Kiểm lại Guest, trạng thái khép lượt và xác nhận sử dụng; document ID là bookingId nên mỗi lượt chỉ có một đánh giá.',
    },
    'FavoritesRepository': {
        'set': 'Thêm hoặc bỏ yêu thích dưới UID hiện tại; đây là quyền lưu phòng, không phải quyền giữ lịch.',
        'load': 'Đọc danh sách đã lưu và ghép lại thông tin phòng để phản ánh trạng thái hiện tại.',
    },
    'LocationRepository': {
        'current': 'Xin vị trí một lần khi màn hình cần dùng, trả lỗi hoặc kết quả qua callback thay vì giữ Activity.',
        'cancel': 'Hủy yêu cầu vị trí đang chờ khi màn hình dừng để tránh callback và tiêu thụ pin ngoài phiên sử dụng.',
    },
    'MapRepository': {
        'geocode': 'Gọi dịch vụ tìm tọa độ theo từ khóa, không coi kết quả tìm địa chỉ là phòng có thể đặt.',
        'route': 'Gọi dịch vụ tuyến đường từ điểm xuất phát tới đích; lỗi API được trả về để giao diện cho phép thử lại.',
    },
    'NoticeRepository': {
        'watch': 'Nghe mốc đọc dưới users/{uid}/noticeReads của đúng tài khoản đang dùng.',
        'read': 'Lưu thời điểm đọc bằng server timestamp cho định danh sự kiện dẫn xuất, không sao chép nội dung thông báo.',
    },
    'SessionGuardRepository': {'watch': 'Nghe role/status của UID hiện tại; thu hồi phiên hoặc chuyển quyền phải phản ánh lên màn hình đang mở.'},
    'ProfileRepository': {'setPhoto': 'Cập nhật ảnh đúng UID và bản chiếu PublicHost trong cùng transaction, không cho sửa role/status qua biểu mẫu ảnh.'},
    'PublicCalendarRepository': {'watch': 'Nghe lịch tháng công khai của phòng để dựng nhãn khả dụng mà không đọc hồ sơ Guest khác.'},
    'PaymentViewModel': {
        'bind': 'Gắn đúng booking và bắt đầu quan sát giao dịch; mở lại màn hình phải dùng cùng mã đơn, không gia hạn thời hạn.',
        'load': 'Tạo/lấy lại QR hoặc đối soát qua Repository, đồng bộ cờ bận và lỗi cho màn hình.',
        'startUpdates': 'Bắt đầu đối soát định kỳ khi màn thanh toán hoạt động; listener và backend vẫn là nguồn trạng thái tiền.',
        'stopUpdates': 'Ngừng lịch đối soát của màn hình để không gửi request khi View đã dừng.',
    },
    'PendingPaymentViewModel': {
        'start': 'Nghe cả booking và payment; chỉ giao dịch thật còn hạn mới được đưa vào danh sách tiếp tục thanh toán.',
        'stop': 'Tháo hai listener và ngừng bộ đếm của View; thao tác này không hủy booking trên server.',
    },
    'PaymentReminderService': {
        'enabled': 'Mặc định bật nhắc thanh toán theo UID, nhưng tôn trọng lựa chọn tắt đã được người dùng lưu.',
        'render': 'Dựng thông báo từ giao dịch đang theo dõi; hết hạn trên đồng hồ không tự xác nhận tiền hoặc tự sửa khóa lịch.',
    },
    'CheckoutReminder': {'attach': 'Gắn lối tắt tiếp tục thanh toán vào màn đang xem; dữ liệu nhắc phải được ViewModel xác nhận còn hiệu lực.'},
    'BookingNotifier': {'deliver': 'Chỉ phát các sự kiện chưa đọc/chưa gửi của tài khoản khi quyền thông báo và lựa chọn người dùng cho phép.\nĐây là thông báo cục bộ khi có dữ liệu, không phải dịch vụ FCM đánh thức app đã bị dừng cưỡng bức.'},
    'BookingNoticeViewModel': {
        'start': 'Đăng ký các nguồn sự kiện theo UID; thông báo được dựng từ booking, hội thoại và báo cáo.',
        'render': 'Hợp nhất nguồn thành sự kiện có định danh ổn định, áp dụng mốc đọc của tài khoản để tính số chưa đọc.',
        'markRead': 'Gửi mốc đọc tới noticeReads; không sửa booking chỉ vì người dùng mở thông báo.',
        'stop': 'Tháo các nguồn đang nghe và vô hiệu callback thuộc lần tải cũ.',
    },
    'ChatViewModel': {
        'watchInbox': 'Nghe các hội thoại có UID hiện tại là thành viên, không truy vấn toàn bộ chat của hệ thống.',
        'acknowledgeIfVisible': 'Chỉ nâng mốc đọc khi người nhận đang xem vùng tin mới; mở danh sách không tự coi mọi tin đã đọc.',
        'older': 'Đọc trang tin cũ theo cursor, ghép với tin mới mà không đánh lại sequence.',
        'send': 'Gửi nội dung qua Repository; sequence, preview và bộ đếm người nhận được ghi atomically ở tầng dữ liệu.',
        'stop': 'Tháo listener khi đổi hội thoại hoặc rời màn hình, tránh cập nhật nhầm luồng tin.',
    },
    'HostViewModel': {
        'watchDashboard': 'Theo dõi phòng và booking thuộc Host; thống kê và danh sách lấy từ các snapshot cùng phiên.',
        'dashboardStats': 'Tổng hợp trạng thái từ dữ liệu dashboard đã nhận; không cộng doanh thu dự kiến thành tiền đã thu.',
        'stopDashboard': 'Tháo listener dashboard, giữ việc giải phóng vòng đời tách khỏi thay đổi tài sản.',
        'persistRoom': 'Lưu form qua HostRepository sau upload; lỗi ghi dữ liệu phải giữ trạng thái để người dùng thử lại.',
        'loadBookings': 'Nghe booking cho màn Host và cập nhật dữ liệu lọc, thay vì để Adapter tự truy cập Firestore.',
        'stopBookings': 'Dừng nguồn booking khi màn hình không còn theo dõi.',
    },
    'RoomViewModel': {
        'filter': 'Lưu bộ lọc và tải tiếp các trang cần thiết để tìm kiếm không chỉ giới hạn ở trang đầu.',
        'fetch': 'Đọc trang kế tiếp qua Repository, quản lý cursor và cờ đang tải để tránh request trùng.',
        'watchCalendars': 'Đăng ký lịch công khai cho các phòng cần hiển thị nhãn tháng/quý, không đọc chi tiết khách khác.',
        'pauseCalendars': 'Ngừng nguồn lịch khi rời màn hình; quay lại sẽ nghe dữ liệu hiện tại thay vì dùng nhãn cũ mãi.',
    },
    'MapViewModel': {
        'openRoom': 'Đọc lại phòng từ ID đã chọn trước khi mở trang chi tiết; địa điểm tham khảo chỉ có liên kết nguồn.',
        'selectReference': 'Chọn một nguồn tham khảo trên bản đồ; không tạo ra phòng có thể đặt từ dữ liệu ngoài app.',
        'directions': 'Yêu cầu tuyến đường từ điểm xuất phát hiện tại, giữ lỗi và kết quả trong trạng thái giao diện.',
        'stopLocation': 'Hủy yêu cầu GPS khi màn hình dừng, không tắt dịch vụ vị trí của thiết bị.',
    },
    'ProfilePhotoViewModel': {'persist': 'Lưu URL ảnh mới trước; chỉ dọn ảnh upload gần đây khi kết quả lưu hồ sơ cho phép.'},
    'HostApplicationViewModel': {'persist': 'Gửi đơn sau khi có URL hai ảnh DEMO; giữ kết quả upload để retry lưu không tạo tệp lặp.'},
    'PropertyVerificationViewModel': {'persist': 'Lưu hồ sơ sau upload bằng Repository chung, giữ UID và loại xác minh theo contract.'},
    'AdminBookingsViewModel': {
        'resolve': 'Chuyển yêu cầu khép từng lượt sang Repository, không hủy hàng loạt khi Host bị khóa.',
        'reconcile': 'Yêu cầu đối soát phòng để bộ đếm và khóa lịch khớp với booking thực tế.',
    },
    'AdminViewModel': {'review': 'Chuyển ý định xét duyệt cùng căn cứ đã xem bằng chứng xuống Repository; role không được nâng ngay trên View.'},
    'AdminEvidenceViewModel': {'load': 'Giữ trạng thái tải byte của bằng chứng cho màn preview, trả lỗi khi nguồn không cho phép đọc.'},
    'EvidencePreviewActivity': {'closePdf': 'Đóng tài nguyên PDF khi thay tệp hoặc hủy màn hình để không giữ descriptor của bản xem cũ.'},
    'AuthMessages': {'resource': 'Trả ID chuỗi thông báo từ mã lỗi chung để các màn xác thực hiển thị nhất quán.'},
    'PendingCheckout': {'select': 'Ghép payment với booking cùng UID/phòng, lọc đúng PENDING còn hạn và có QR/link thực tế.'},
    'FixedRentalBadge': {'from': 'Dựng nhãn từ lịch thực tế, phân biệt giữ tạm, đặt trước và kỳ thuê còn tối đa 3 ngày.'},
    'BookingAvailability': {'conflict': 'So sánh khoảng nửa mở [startAt,endAt) để hai lượt tiếp giáp không bị coi là trùng giờ.'},
    'HostCalendarPolicy': {'heldOnDay': 'Chỉ đưa các booking còn giữ lịch và giao với ngày đang xem vào danh sách của lưới tháng.'},
    'HostSchedule': {'validate': 'Kiểm ngày hoạt động, giờ mở và các khoảng khóa theo múi giờ nghiệp vụ dùng chung.'},
    'RoomFilter': {'matches': 'Kết hợp từ khóa và các điều kiện lọc trên dữ liệu đã ghép Building/PublicHost, không suy ra mode từ mức giá.'},
    'Payment': {'canReplaceWith': 'Ngăn snapshot cũ ghi đè kết quả đã nhận tiền hoặc dữ liệu khác mã đơn đang được quan sát.'},
}


def tokens(text):
    # Giữ nguyên string/char literals; loại comment/whitespace để so sánh logic trước và sau.
    parts = re.findall(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*.*?\*/|//[^\n]*|\s+|.', text, re.S)
    return ''.join(p for p in parts if not p.isspace() and not p.startswith(('//', '/*')))


def main():
    roots = ['app/src/main/java', 'app/src/androidTest/java', 'app/src/test/java', 'backend/payments/src', 'tools/dataset']
    files = [p for base in roots for p in (ROOT / base).rglob('*.java')]
    evidence = []
    for path in files:
        original = path.read_text(encoding='utf-8-sig')
        text = original
        name = path.stem
        header = HEADERS.get(name)
        if header and header.split('\n')[0] not in text:
            match = re.search(r'(?m)^public (?:abstract |final )?(?:class|interface|enum) ', text)
            if match:
                block = '/*\n' + '\n'.join('   ' + line for line in header.splitlines()) + '\n*/\n'
                text = text[:match.start()] + block + text[match.start():]
        for method, comment in METHODS.get(name, {}).items():
            if comment.split('\n')[0] in text:
                continue
            pattern = (r'(?m)^(?P<indent>    )(?:public|protected|private)\s+'
                       r'(?:(?:static|final|synchronized)\s+)*(?:<[^\n]+>\s+)?'
                       r'[\w<>?,.\[\] ]+\s+' + re.escape(method) + r'\s*\(')
            match = re.search(pattern, text)
            if not match:
                raise AssertionError(f'Method not found: {name}.{method}')
            block = ''.join('    // ' + line + '\n' for line in comment.splitlines())
            text = text[:match.start()] + block + text[match.start():]
        if not re.search(r'/\*|//', text):
            # Chỉ các test thuần Java còn thiếu phần nói rõ phạm vi, không thêm mô tả giả vào method.
            methods = re.findall(r'@Test[^\n]*?void\s+(\w+)', text)
            scope = 'Kiểm thử ' + ', '.join(methods[:3]) + '.' if methods else 'Các ca kiểm thử hồi quy của ' + name + '.'
            if '/src/test/' in path.as_posix():
                scope += '\nDữ liệu được tạo trong test; kết quả này không thay cho nghiệm thu thao tác trên thiết bị hoặc cloud.'
            match = re.search(r'(?m)^public (?:final )?class ', text)
            if match:
                text = text[:match.start()] + '/*\n' + '\n'.join('   ' + line for line in scope.splitlines()) + '\n*/\n' + text[match.start():]
        assert tokens(original) == tokens(text), path
        changed = text != original
        if changed:
            path.write_text(text, encoding='utf-8')
        evidence.append({'path': path.relative_to(ROOT).as_posix(), 'commentOnlyChange': changed,
                         'tokensUnchanged': True, 'hasComments': bool(re.search(r'/\*|//', text)),
                         'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'java-comments-proof.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'reviewedFiles': len(files), 'commentedFiles': sum(r['commentOnlyChange'] for r in evidence),
                      'filesWithoutComments': [r['path'] for r in evidence if not r['hasComments']], 'tokensUnchanged': True}))


if __name__ == '__main__':
    main()
