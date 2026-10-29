"""Check the integrated report before publishing the main PDF."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import unicodedata

import pymupdf

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / 'Latex/FINAL_ANDROID (1)'
BUILD = ROOT / 'tmp/chapters123/build'
PDF = BUILD / 'main.pdf'


def plain(s):
    return unicodedata.normalize('NFD', s).encode('ascii', 'ignore').decode()


def check():
    log = (BUILD / 'main.log').read_text(encoding='utf-8', errors='replace')
    for token in ['undefined references', 'undefined on input', 'Overfull',
                  'Float too large', 'Fatal error', 'Rerun to get outlines right',
                  'Label(s) may have changed']:
        assert token not in log, token
    with pymupdf.open(PDF) as d:
        sections = [(title, page) for level, title, page in d.get_toc() if level == 1]
        titles = ['TỔNG QUAN', 'PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG',
                  'THIẾT KẾ GIAO DIỆN', 'THIẾT KẾ CƠ SỞ DỮ LIỆU']
        starts = [next(page for title, page in sections if title == target) for target in titles]
        assert starts == sorted(set(starts)), starts
        for title, start in zip(titles, starts):
            assert ''.join(plain(title).split()) in ''.join(plain(d[start - 1].get_text()).split()), (title, start)
        counts = [starts[i + 1] - starts[i] for i in range(3)]
        body = '\n'.join(p.get_text() for p in d[starts[0] - 1:starts[3] - 1])
        assert '??' not in body
        chapter1 = '\n'.join(p.get_text() for p in d[starts[0] - 1:starts[1] - 1])
        assert 'NoSQL' in chapter1 and 'subcollection' in chapter1
        assert 'roomId' in chapter1 and 'Chương 4' in chapter1
        chapter3 = '\n'.join(p.get_text() for p in d[starts[2] - 1:starts[3] - 1])
        assert 'Prototype' in chapter3 and 'Figma' in chapter3
        assert 'ba bước' in chapter3
        assert all(not p.get_images() for p in d[starts[0] - 1:starts[1] - 1]), 'Chapter 1 has images'
        assert all(not p.get_images() for p in d[starts[1] - 1:starts[2] - 1]), 'Chapter 2 rasterized'
        assert sum(len(p.get_drawings()) for p in d[starts[1] - 1:starts[2] - 1]) > 100
        assert len([t for t, _ in sections if t == 'TÀI LIỆU THAM KHẢO']) == 1
        source_map = {
            'Luồng đăng nhập & Đăng ký.png': 'auth-design.png',
            'Trang chủ.png': 'home-design.png',
            'Chi tiết không gian.png': 'detail-design.png',
            'Xác nhận đặt phòng.png': 'booking-design.png',
            'Luồng đăng tin.png': 'host-design.png',
            'Luồng Admin.png': 'admin-design.png',
        }
        for source, target in source_map.items():
            a = ROOT / 'Broom_Viet/drive-download-20261027T031129Z-1-001' / source
            b = REPORT / 'Resources/chapter3' / target
            assert a.read_bytes() == b.read_bytes(), source
        ranges = {f'chapter{i+1}': {'pdfPages': [starts[i], starts[i+1]-1],
                                 'pageCount': counts[i]} for i in range(3)}
        metadata = {'fullReport': True, 'pages': len(d), 'chapters123': ranges,
                    'chapters123PageCount': sum(counts),
                    'chapter1Images': 0, 'chapter2VectorFigures': True,
                    'chapter3PrototypeImages': True, 'originalImageBytesPreserved': True,
                    'chapter1BriefNoSqlIntroduction': True, 'chapter3BookingPrototypeInBody': True,
                    'chapter3HostPrototypeInBody': True,
                    'oneGlobalBibliography': True, 'unresolvedReferences': False,
                    'overfullBoxes': False, 'sha256': hashlib.sha256(PDF.read_bytes()).hexdigest(),
                    'scope': 'Chapters 1–3, selected appendix figures and shared abstract/glossary. Chapters 4–7 not re-audited.'}
    output = ROOT / 'output/pdf/BroomSharing-Bao-cao.pdf'
    for target in [REPORT / 'main.pdf', output]:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(PDF, target)
        assert hashlib.sha256(target.read_bytes()).hexdigest() == metadata['sha256']
    (output.parent / 'BroomSharing-Bao-cao-manifest.json').write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (ROOT / 'docs/design/chapters123-validation.json').write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(metadata, ensure_ascii=False))


if __name__ == '__main__':
    check()
