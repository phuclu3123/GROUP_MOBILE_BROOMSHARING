"""Export classic pages through draw.io MCP; normalize SVG bounds and portable text.

Native editable shapes and layout stay unchanged. Rejected compact pages are not
used, regenerated or archived by this command.
"""
import json
import math
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import pymupdf as fitz
from PIL import ImageFont
from sync_drawio_diagrams import rpc, DOCUMENT, MAIN_IDS

BASE=Path(__file__).resolve().parents[2]
ROOT=BASE/'docs/design/drawio'
PDF=BASE/'output/pdf/firestore'
NS='http://www.w3.org/2000/svg'
ET.register_namespace('',NS)


def normalize(slug):
    native=ET.parse(ROOT/(slug+'-model.xml')).getroot()
    cells={c.get('id'):c for c in native.find('root')}
    svg=ET.parse(ROOT/(slug+'.svg')).getroot()
    groups={g.get('data-cell-id'):g for g in svg.iter('{'+NS+'}g') if g.get('data-cell-id')}
    texts=[c for c in cells.values() if c.get('style','').startswith('text;')]
    first=texts[0];geometry=first.find('mxGeometry')
    rect=next(groups[first.get('id')].iter('{'+NS+'}rect'))
    scale=float(rect.get('width'))/float(geometry.get('width'))
    tx=float(rect.get('x'))-float(geometry.get('x'))*scale
    ty=float(rect.get('y'))-float(geometry.get('y'))*scale

    def absolute(c):
        g=c.find('mxGeometry');x,y=float(g.get('x','0')),float(g.get('y','0'))
        parent=cells.get(c.get('parent'))
        while parent is not None and parent.get('vertex')=='1':
            pg=parent.find('mxGeometry');x+=float(pg.get('x','0'));y+=float(pg.get('y','0'))
            parent=cells.get(parent.get('parent'))
        return x,y,float(g.get('width','0')),float(g.get('height','0'))

    for c in texts:
        group=groups[c.get('id')];style=dict(s.split('=',1) for s in c.get('style').split(';') if '=' in s)
        x,y,w,h=absolute(c);size=int(style['fontSize'])
        bold=style.get('fontStyle')=='1'
        font=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf' if bold else 'C:/Windows/Fonts/arial.ttf',size*4)
        lines=[]
        for paragraph in c.get('value','').split('\n'):
            current=''
            for word in paragraph.split(' '):
                trial=(current+' '+word).strip()
                if current and font.getlength(trial)>(w-24)*4:lines.append(current);current=word
                else:current=trial
            lines.append(current)
        lineh=size*1.2
        assert len(lines)*lineh<=h+2,(slug,c.get('value'))
        for child in list(group):group.remove(child)
        align=style.get('align','left');xx=x+w-10 if align=='right' else x+14
        yy=y+(h-len(lines)*lineh)/2+size*.95
        text=ET.SubElement(group,'{'+NS+'}text',x=str(xx*scale+tx),y=str(yy*scale+ty),
            **{'font-family':'Arial, Helvetica, sans-serif','font-size':str(size*scale),
               'fill':style['fontColor'],'font-weight':'bold' if bold else 'normal',
               'text-anchor':'end' if align=='right' else 'start'})
        for i,line in enumerate(lines):
            span=ET.SubElement(text,'{'+NS+'}tspan',x=str(xx*scale+tx),dy=str(0 if i==0 else lineh*scale));span.text=line
    bounds=[absolute(c) for c in cells.values() if c.get('vertex')=='1']
    minx=min(x for x,y,w,h in bounds)*scale+tx-24
    miny=min(y for x,y,w,h in bounds)*scale+ty-24
    maxx=max(x+w for x,y,w,h in bounds)*scale+tx+24
    maxy=max(y+h for x,y,w,h in bounds)*scale+ty+24
    svg.set('viewBox',f'{minx} {miny} {maxx-minx} {maxy-miny}')
    svg.set('width',str(math.ceil(maxx-minx)));svg.set('height',str(math.ceil(maxy-miny)))
    for element in svg.iter():
        if 'light-dark(' in element.get('style',''):element.attrib.pop('style',None)
    for parent in list(svg.iter()):
        for child in list(parent):
            if 'Text is not SVG' in ''.join(child.itertext()):
                # Only remove fallback nodes, never a group containing real diagram text.
                if child.tag in {'{'+NS+'}switch','{'+NS+'}a','{'+NS+'}text'}:parent.remove(child)
    for child in svg:
        if child.tag=='{'+NS+'}rect' and child.get('width')=='100%':
            child.set('x',str(minx));child.set('y',str(miny));child.set('width',str(maxx-minx));child.set('height',str(maxy-miny))
    ET.ElementTree(svg).write(ROOT/(slug+'.svg'),encoding='utf-8',xml_declaration=True)
    vector=fitz.open(ROOT/(slug+'.svg'));one=fitz.open('pdf',vector.convert_to_pdf());vector.close()
    assert not one[0].get_images()
    zoom=2200/one[0].rect.width
    one[0].get_pixmap(matrix=fitz.Matrix(zoom,zoom),alpha=False).save(ROOT/(slug+'.png'))
    one.save(PDF/(slug+'.pdf'),garbage=4,deflate=True)
    print('Classic SVG/PNG/PDF restored:',slug,flush=True)
    return one


def main():
    PDF.mkdir(parents=True,exist_ok=True);book=fitz.open()
    for p in json.loads((ROOT/'remaining-pages-manifest.json').read_text(encoding='utf-8'))['pages']:
        slug=p['slug']
        exported=rpc('export-diagram',{'target_document':{'id':DOCUMENT},'target_page':{'id':MAIN_IDS[slug]},
            'format':'svg','scale':1,'border':24,'background':'#ffffff','crop':True,'output_path':str(ROOT/(slug+'.svg'))})
        if isinstance(exported,str):(ROOT/(slug+'.svg')).write_text(exported,encoding='utf-8')
        one=normalize(slug);book.insert_pdf(one);one.close()
    book.save(PDF/'broomsharing-firestore-diagrams.pdf',garbage=4,deflate=True);book.close()


if __name__=='__main__':main()
