"""Validate only updated report material; never run or modify app/cloud data."""
from pathlib import Path
import hashlib
import html
import json
import re
import unicodedata
import xml.etree.ElementTree as ET
import pymupdf

ROOT=Path(__file__).resolve().parents[2]
REPORT=ROOT/'Latex/FINAL_ANDROID (1)'
QA=ROOT/'tmp/report-sync-2026-10-28'
RES=REPORT/'Resources/chapter4'


def normal(s):
    return ''.join(c for c in unicodedata.normalize('NFKC',html.unescape(re.sub('<[^>]+>',' ',s))) if not c.isspace())


def main():
    protected=json.loads((QA/'protected-hashes.json').read_text())
    for path,digest in protected.items():assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==digest,path
    schema=json.loads((ROOT/'docs/design/firestore-visualization-schema.json').read_text(encoding='utf-8'))
    docs={d['path']:d for d in schema['documents']}
    summary=json.loads((RES/'summary-manifest.json').read_text(encoding='utf-8'))
    assert summary['patterns']==20
    for f in summary['selectedFields']:
        actual={v['name']:v['type'] for v in docs[f['path']]['fields']}
        assert actual[f['name']]==f['type']
    for item in json.loads((RES/'code-manifest.json').read_text(encoding='utf-8'))['snippets']:
        p=ROOT/item['source'];assert hashlib.sha256(p.read_bytes()).hexdigest()==item['sourceSha256']
        text='\n'.join(p.read_text(encoding='utf-8').splitlines()[item['firstLine']-1:item['lastLine']])+'\n'
        assert (RES/'code'/item['file']).read_text(encoding='utf-8')==text,item['file']
    actual_paths=set();checks=[]
    tree=ET.parse(RES/'source-pages.drawio')
    for diagram in tree.findall('diagram'):
        root=diagram.find('mxGraphModel/root');assert root is not None
        cells=list(root);ids=[c.get('id') for c in cells];assert len(ids)==len(set(ids)),diagram.get('name')
        for c in cells:
            if c.get('edge')=='1':assert c.get('source') in ids and c.get('target') in ids,c.attrib
        cards=[c for c in cells if c.get('parent')=='1' and c.get('schemaPath')]
        for c in cards:
            path=c.get('schemaPath');actual_paths.add(path)
            rendered={n.get('fieldPath') for n in cells if n.get('parent')==c.get('id') and n.get('fieldPath')}
            assert rendered=={f['name'] for f in docs[path]['fields']},path
        # Field cards may not cover one another; roots/headers intentionally share a trunk.
        for i,a in enumerate(cards):
            ag=a.find('mxGeometry');ax,ay,aw,ah=[float(ag.get(k,'0')) for k in ['x','y','width','height']]
            for b in cards[i+1:]:
                bg=b.find('mxGeometry');bx,by,bw,bh=[float(bg.get(k,'0')) for k in ['x','y','width','height']]
                assert min(ax+aw,bx+bw)<=max(ax,bx) or min(ay+ah,by+bh)<=max(ay,by),(a.get('schemaPath'),b.get('schemaPath'))
    assert actual_paths==set(docs),sorted(set(docs)-actual_paths)
    for path in (ROOT/'docs/design/chapter2').glob('*.drawio'):
        for diagram in ET.parse(path).findall('diagram'):
            cells=diagram.findall('.//mxCell');ids=[c.get('id') for c in cells]
            assert len(ids)==len(set(ids)),(path.name,diagram.get('name'))
            for c in cells:
                if c.get('edge')=='1':assert c.get('source') in ids and c.get('target') in ids
            if path.name=='workflow_diagram_v2.drawio' and diagram.get('name','').startswith(('02.','04.')):
                labels=' '.join(c.get('value','') for c in cells)
                assert '5 phút' in labels and '40%' in labels
                assert 'Duyệt?' not in labels
    for path in sorted(RES.glob('firestore-*.pdf')):
        doc=pymupdf.open(path);assert len(doc)==1
        page=doc[0];assert not page.get_images();assert page.get_drawings()
        assert all(doc.extract_font(f[0])[3] for f in page.get_fonts(full=True))
        checks.append({'file':path.name,'vectorPaths':len(page.get_drawings()),'rasterImages':0,'fontsEmbedded':True})
    # The uniform-orientation requirement applies to workflow only.
    path=ROOT/'output/pdf/chapter2/workflow_diagram_v2.pdf'
    document=pymupdf.open(path)
    for i,page in enumerate(document):assert page.rect.width>page.rect.height,(path.name,i+1,'Workflow must be landscape')
    active='\n'.join((REPORT/'chapters'/f).read_text(encoding='utf-8') for f in ['chapter1.tex','chapter2.tex','chapter2-appendix.tex','chapter4.tex','chapter4-entities-summary.tex','chapter4-figures.tex'])
    for wrong in ['Sau khi Host xác nhận, người thuê thanh toán','còn chờ duyệt khi tới giờ bắt đầu','Trạng thái thanh toán UNAVAILABLE và khoản trả trước','13 collection gốc']:
        assert wrong not in active,wrong
    assert not re.search(r'\\{3,}n',active)
    for filename in ['chapter1.tex','chapter4.tex','chapter4-entities-summary.tex','chapter4-figures.tex']:
        text=(REPORT/'chapters'/filename).read_text(encoding='utf-8')
        assert not re.search(r'\\textbf|--|[\u2013\u2014]',text),filename
    out={'protectedChaptersUnchanged':True,'implementedPatterns':20,'exactCodeExcerpts':True,'nativeDiagramIdsAndEdgesValid':True,'fieldCardsDoNotOverlap':True,'oldPaymentNarrativeRemoved':True,'vectorChecks':checks}
    (QA/'validation.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(out,ensure_ascii=False))


if __name__=='__main__':main()
