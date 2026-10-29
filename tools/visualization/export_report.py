"""Publish the full report; keep a Chapter 4 slice only for internal PDF QA."""
import hashlib
import json
import shutil
import unicodedata
from pathlib import Path

import pymupdf

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / 'Latex/FINAL_ANDROID (1)'
SOURCE = ROOT / 'tmp/chapters123/build/main.pdf'
TARGET = ROOT / 'output/pdf/BroomSharing-Bao-cao.pdf'


def plain(value):
    return unicodedata.normalize('NFD', value).encode('ascii', 'ignore').decode()


with pymupdf.open(SOURCE) as document:
    toc = document.get_toc()
    start = next(page - 1 for level, title, page in toc
                 if level == 1 and plain(title) == 'THIET KE CO SO DU LIEU')
    end = next(page - 2 for level, title, page in toc
               if level == 1 and plain(title) == 'XAY DUNG UNG DUNG VA THIET KE KIEN TRUC')
    references = [page for level, title, page in toc
                  if level == 1 and plain(title) == 'TAI LIEU THAM KHAO']
    assert len(references) == 1 and references[0] > end + 1
    assert ''.join(plain(document[references[0] - 1].get_text()).split()).startswith('TAILIEUTHAMKHAO')
    with pymupdf.open() as chapter:
        chapter.insert_pdf(document, from_page=start, to_page=end)
        qa_target = ROOT / 'tmp/report-sync-2026-10-28/chapter4-in-report.pdf'
        qa_target.parent.mkdir(parents=True, exist_ok=True)
        chapter.save(qa_target, garbage=4, deflate=True)
    pages = len(document)

TARGET.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(SOURCE, TARGET)
shutil.copy2(SOURCE, REPORT / 'main.pdf')
digest = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
assert hashlib.sha256(TARGET.read_bytes()).hexdigest() == digest
assert hashlib.sha256((REPORT / 'main.pdf').read_bytes()).hexdigest() == digest
metadata = {
    'fullReport': True,
    'pages': pages,
    'sha256': digest,
    'chapter4PagesInReport': [start + 1, end + 1],
    'chapter4PageCount': end - start + 1,
    'globalBibliographyPage': references[0],
    'canonicalPdf': str((REPORT / 'main.pdf').relative_to(ROOT)),
    'deliveredPdf': str(TARGET.relative_to(ROOT)),
    'chapterOnlyExport': False,
}
(TARGET.parent / 'BroomSharing-Bao-cao-manifest.json').write_text(
    json.dumps(metadata, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(metadata))
