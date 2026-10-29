"""Check Chapter 4 provenance and render every page for manual visual QA."""
import hashlib
import json
import re
import argparse
from pathlib import Path

import pymupdf
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('--pdf',type=Path)
args=parser.parse_args()
REPORT = ROOT / 'Latex/FINAL_ANDROID (1)'
RES = REPORT / 'Resources/chapter4'
QA = ROOT / 'tmp/chapter4/qa'
QA.mkdir(parents=True, exist_ok=True)
manifest = json.loads((RES / 'code-manifest.json').read_text(encoding='utf-8'))
source = json.loads((RES / 'source-manifest.json').read_text(encoding='utf-8'))
assert hashlib.sha256(Path(source['source']).read_bytes()).hexdigest() == source['sha256']
assert [p['sourcePage'] for p in source['includedPages']] == [2, 3, 4, 5, 6]
assert manifest['tableDocumentPatterns'] == 16
assert manifest['tableFieldRows'] == 219
assert manifest['logicalRelationshipRows'] == 44
assert len({f['path'] for f in manifest['includedFields']}) == 16
assert not any(f['path'].split('/')[0] in ['payments', 'notifications'] for f in manifest['includedFields'])
for s in manifest['snippets']:
    p = ROOT / s['source']
    assert hashlib.sha256(p.read_bytes()).hexdigest() == s['sourceSha256'], s['source']
    lines = p.read_text(encoding='utf-8').splitlines()
    exact = '\n'.join(lines[s['firstLine'] - 1:s['lastLine']]) + '\n'
    assert (RES / 'code' / s['file']).read_text(encoding='utf-8') == exact, s['file']
active=['chapter4.tex','chapter4-entities-summary.tex','chapter4-figures.tex']
tex = '\n'.join((REPORT/'chapters'/p).read_text(encoding='utf-8') for p in active)
assert len(re.findall(r'\\subsection\{', tex)) == 4
assert not re.search(r'\\textbf|--|[\u2013\u2014]', tex)
for line in tex.splitlines():
    if line.lstrip().startswith('%'):
        continue
    assert not re.search(r'(?<!\\)%', line), line
summary=json.loads((RES/'summary-manifest.json').read_text(encoding='utf-8'))
assert summary['patterns']==16 and summary['appendixAdded'] is False
assert len(summary['selectedFields'])==67
inventory=json.loads((ROOT/'docs/design/firestore-visualization-schema.json').read_text(encoding='utf-8'))
known={(d['path'],f['name']):f['type'] for d in inventory['documents'] for f in d['fields']}
for field in summary['selectedFields']:
    assert known[(field['path'],field['name'])]==field['type']
main_text=(REPORT/'chapters/chapter4.tex').read_text(encoding='utf-8')
assert r'\input{chapters/chapter4-entities}' not in main_text
assert r'\input{chapters/chapter4-relationships}' not in main_text
assert len(re.findall(r'\\caption\{',tex))==9  # Four tables and five figures.
assert 'Page-2' not in main_text and 'PDF vector' not in main_text
for phrase in ['Thiết kế dưới đây', 'chưa bao gồm kiểm kê', 'ngày 23/10/2026', 'hướng dẫn người đọc']:
    assert phrase not in main_text, phrase
for label in ['tab:ch4-entities-summary','tab:ch4-relations-summary','tab:ch4-operations','tab:ch4-permissions']:
    assert r'\ref{'+label+'}' in tex
used_snippets=re.findall(r'\\inputminted(?:\[[^\]]*\])?\{[^}]*\}\{Resources/chapter4/code/([^}]+)\}',tex)
assert len(used_snippets)==5
assert r'\subsubsection{Các truy vấn tiêu biểu}' in main_text
assert 'booking-query-listener.java' in used_snippets
assert set(used_snippets).issubset({s['file'] for s in manifest['snippets']})
operation_rows=main_text.split(r'\label{tab:ch4-operations}',1)[1].split(r'\end{longtable}',1)[0]
assert len([line for line in operation_rows.splitlines() if ' & ' in line and not line.startswith(r'\toprule')])==8
log = (ROOT / 'tmp/chapter4/build-preview/chapter4-preview.log').read_text(encoding='utf-8', errors='replace')
assert not re.search(r'Overfull|Underfull|undefined|LaTeX Error|Package minted Error', log)
figures = []
for p in sorted(RES.glob('firestore-*.pdf')):
    doc = pymupdf.open(p)
    assert len(doc) == 1
    assert len(doc[0].get_images(full=True)) == 0
    assert len(doc[0].get_text()) > 500
    figures.append({'file': p.name, 'pages': len(doc), 'rasterImages': 0})
pdf = args.pdf if args.pdf else ROOT / 'tmp/chapter4/build-preview/chapter4-preview.pdf'
doc = pymupdf.open(pdf)
assert len(doc)<=13, 'Chapter exceeds the agreed length.'
texts = [p.get_text() for p in doc]
assert all(len(t.strip()) > 100 for t in texts)
all_text = ''.join(texts)
assert 'hasPendingWrites' in all_text
assert '40%' in re.sub(r'\s+', '', all_text)
assert 'broomsharing_source' not in all_text
pages = []
landscape_footers=[]
for i, page in enumerate(doc):
    if page.rotation:
        footers=[]
        for word in page.get_text('words'):
            rect=pymupdf.Rect(word[:4])*page.rotation_matrix
            if word[4].isdigit() and rect.y0>page.rect.height-50:
                footers.append((word[4],(rect.x0+rect.x1)/2,(rect.y0+rect.y1)/2))
        assert len(footers)==1, (i+1,footers)
        number,x,y=footers[0]
        assert abs(x-page.rect.width/2)<3 and page.rect.height-45<y<page.rect.height-10
        landscape_footers.append({'page':i+1,'number':number,'centerX':round(x,2),'centerY':round(y,2)})
    page.get_pixmap(matrix=pymupdf.Matrix(1.45, 1.45), alpha=False).save(QA / f'page-{i+1:02}.png')
    pages.append({'page': i+1, 'rotation': page.rotation, 'characters': len(texts[i]),
                  'firstText': texts[i][:90].replace('\n', ' '),
                  'lastText': texts[i][-100:].replace('\n', ' ')})
for first in range(0, len(doc), 4):
    sheet = Image.new('RGB', (1240, 1810), '#e0e0e0')
    draw = ImageDraw.Draw(sheet)
    for j in range(4):
        i = first + j
        if i >= len(doc):
            break
        im = Image.open(QA / f'page-{i+1:02}.png')
        im.thumbnail((600, 865))
        x = (j % 2) * 620 + (620 - im.width) // 2
        y = (j // 2) * 905 + 28
        sheet.paste(im, (x, y))
        draw.text(((j % 2) * 620 + 12, (j // 2) * 905 + 7), f'Page {i+1}', fill='black')
    sheet.save(QA / f'sheet-{first//4+1:02}.png')
assert len(landscape_footers)==4
result = {'sourceDrawioUnchanged': True, 'mainTables':4,'documentPatterns':16,
          'representativeFields':67,'mainRelationshipRows':8,'appendixAdded':False,
          'landscapeFooters':landscape_footers,'exactExcerptsRetained':len(manifest['snippets']),
          'operationRows':8, 'printedCodeExcerpts':used_snippets,
          'figureChecks': figures, 'previewPages': len(doc),
          'layoutWarnings': [], 'pageChecks': pages,
          'scope': 'Static source and report checks; no live cloud inventory or runtime acceptance test.'}
(QA / 'verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({k: v for k, v in result.items() if k not in ['pageChecks', 'figureChecks']}))
