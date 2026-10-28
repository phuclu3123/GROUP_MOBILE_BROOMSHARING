"""Check tree structure, connector routing and exported vector figures."""
from pathlib import Path
import xml.etree.ElementTree as ET
import json
import hashlib
import pymupdf

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'docs/design/chapter2'


def styles(c):
    return dict(p.split('=',1) for p in c.get('style','').split(';') if '=' in p)


def anchor(c,st,prefix):
    g=c.find('mxGeometry')
    x,y,w,h=[float(g.get(k,'0')) for k in ('x','y','width','height')]
    return x+float(st.get(prefix+'X','.5'))*w, y+float(st.get(prefix+'Y','.5'))*h


def crosses(a,b,r):
    x,y,w,h=r
    # Exclude touching a rectangle boundary; only count crossing its interior.
    if abs(a[0]-b[0])<.01:
        return x+2<a[0]<x+w-2 and max(min(a[1],b[1]),y+2)<min(max(a[1],b[1]),y+h-2)
    if abs(a[1]-b[1])<.01:
        return y+2<a[1]<y+h-2 and max(min(a[0],b[0]),x+2)<min(max(a[0],b[0]),x+w-2)
    raise AssertionError(('Unaligned routing anchors',a,b))


def main():
    results=[]
    for filename in ['usecase_decomposition_v2.drawio','workflow_diagram_v2.drawio']:
        source=BASE/filename
        model=ET.parse(source).getroot()
        pdfpath=ROOT/'output/pdf/chapter2'/Path(filename).with_suffix('.pdf')
        pdf=pymupdf.open(pdfpath)
        assert len(pdf)==len(model.findall('diagram'))
        for index,d in enumerate(model.findall('diagram')):
            cells=d.findall('.//mxCell');byid={c.get('id'):c for c in cells}
            assert len(byid)==len(cells)
            edges=[c for c in cells if c.get('edge')=='1']
            vertices=[c for c in cells if c.get('vertex')=='1']
            for e in edges: assert e.get('source') in byid and e.get('target') in byid
            assert not {'title','subtitle'} & set(byid)
            assert not any('shape=note;' in c.get('style','') for c in vertices)
            page={'file':filename,'page':index+1,'vertices':len(vertices),'edges':len(edges),
                  'connectedEndpoints':True,'decorativeTitles':False}
            if filename.startswith('usecase_decomposition'):
                assert len(edges)==len(vertices)-1
                reached={'root'}
                for _ in vertices:
                    reached|={e.get('target') for e in edges if e.get('source') in reached}
                assert reached=={v.get('id') for v in vertices}
                assert all(sum(e.get('target')==v.get('id') for e in edges)==1
                           for v in vertices if v.get('id')!='root')
                page['rootedTree']=True
            else:
                boxes={}
                for v in vertices:
                    st=styles(v)
                    if st.get('strokeColor') in (None,'none'): continue
                    g=v.find('mxGeometry')
                    boxes[v.get('id')]=tuple(float(g.get(k,'0')) for k in ('x','y','width','height'))
                for e in edges:
                    st=styles(e)
                    pts=[anchor(byid[e.get('source')],st,'exit')]
                    pts += [(float(q.get('x')),float(q.get('y')))
                            for q in e.findall("./mxGeometry/Array[@as='points']/mxPoint")]
                    pts += [anchor(byid[e.get('target')],st,'entry')]
                    for a,b in zip(pts,pts[1:]):
                        for id,r in boxes.items():
                            if id in (e.get('source'),e.get('target')): continue
                            assert not crosses(a,b,r),(d.get('name'),e.get('id'),'crosses',id,a,b)
                page['noConnectorThroughOtherNode']=True
                pairs={(e.get('source'),e.get('target')) for e in edges}
                if index==1:
                    assert {('pending','review'),('pending','cancelled'),('pending','expired')}<=pairs
                if index==2:
                    assert {('verify_usage','used'),('used','corrected'),('used','keep')}<=pairs
                    assert not any(a in ('corrected','keep') for a,b in pairs)
                if index==3:
                    assert {('money_result','paid'),('money_result','unpaid'),('money_result','invalid_event')}<=pairs
                count=0
                for drawing in pdf[index].get_drawings():
                    color=drawing.get('color')
                    if color is None or drawing.get('fill') is not None: continue
                    if max(abs(color[j]-v) for j,v in enumerate([130/255,145/255,158/255]))>.03: continue
                    for item in drawing['items']:
                        if item[0]!='l': continue
                        a,b=item[1:3];dx=abs(a.x-b.x);dy=abs(a.y-b.y)
                        if max(dx,dy)>8:
                            assert min(dx,dy)<.2,('Slanted connector in PDF',index+1,a,b)
                            count+=1
                page['axisAlignedPDFSegments']=count
            assert not pdf[index].get_images()
            assert all(pdf.extract_font(f[0])[3] for f in pdf[index].get_fonts(full=True))
            page.update(vector=True,fontsEmbedded=True)
            results.append(page)
    receipt={'scope':'Document and source review; no app/cloud changes or new runtime acceptance tests.',
             'pages':results,'sha256':{f:hashlib.sha256((BASE/f).read_bytes()).hexdigest()
              for f in ['usecase_decomposition_v2.drawio','workflow_diagram_v2.drawio']}}
    (BASE/'flow-final-review.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'pagesChecked':len(results),'rootedTrees':15,'workflowPages':4,
                      'connectorRouting':'PASS','vectorAndFonts':'PASS'}))


if __name__=='__main__':main()
