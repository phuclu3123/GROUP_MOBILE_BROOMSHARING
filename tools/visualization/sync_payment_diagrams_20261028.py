"""Update native draw.io objects while retaining the accepted report style/layout."""
from pathlib import Path
import base64
import copy
import hashlib
import html
import importlib.util
import json
import re
import urllib.parse
import xml.etree.ElementTree as ET
import zlib

ROOT=Path(__file__).resolve().parents[2]
CH2=ROOT/'docs/design/chapter2'
RES=ROOT/'Latex/FINAL_ANDROID (1)/Resources/chapter4'
spec=importlib.util.spec_from_file_location('chapter2_style',Path(__file__).with_name('revise_chapter2_diagrams.py'))
draw=importlib.util.module_from_spec(spec);spec.loader.exec_module(draw)


def write(tree,path):
    ET.indent(tree,space='  ')
    tree.write(path,encoding='utf-8',xml_declaration=True)


def graph(diagram):
    g=diagram.find('mxGraphModel')
    if g is None:
        g=ET.fromstring(urllib.parse.unquote(zlib.decompress(base64.b64decode(diagram.text),-15).decode()))
        diagram.text=None;diagram.append(g)
    return g




TEXT='text;html=0;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontSize={size};fontColor={color};fontStyle={bold};align={align};verticalAlign=middle;spacingLeft=14;spacingRight=10;'


def cell(root,ident,value,x,y,w,h,style,parent='1',**attrs):
    c=ET.SubElement(root,'mxCell',id=ident,value=value,style=style,vertex='1',parent=parent,**attrs)
    ET.SubElement(c,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),attrib={'as':'geometry'})
    return c


def text(root,ident,value,x,y,w,h,parent='1',size=15,color='#243746',bold=0,align='left',**attrs):
    return cell(root,ident,value,x,y,w,h,TEXT.format(size=size,color=color,bold=bold,align=align),parent,**attrs)


def descendants(root,ident):
    ids={ident}
    while True:
        extra={c.get('id') for c in root if c.get('parent') in ids}
        if extra<=ids:break
        ids.update(extra)
    return ids


def remove(root,ident):
    ids=descendants(root,ident)
    for c in list(root):
        if c.get('id') in ids or c.get('source') in ids or c.get('target') in ids:root.remove(c)


def card(root,ident,path,x,y,w,fields,palette='#EAF1F8',badge='DOCUMENT'):
    old=next((c for c in root if c.get('id')==ident),None)
    if old is not None:
        for c in list(root):
            if c.get('parent')==ident:root.remove(c)
        root.remove(old)  # Edges targeting this ID remain valid.
    h=92+len(fields)*32
    cell(root,ident,'',x,y,w,h,'rounded=1;arcSize=5;fillColor=#FFFFFF;strokeColor=#CBD5E1;strokeWidth=1.5;',schemaPath=path)
    cell(root,ident+'-head','',0,0,w,80,'fillColor='+palette+';strokeColor=none;',ident)
    label=path.rsplit('/',1)[-1]
    text(root,ident+'-title',label,3,1,w-200,42,ident,21,'#16324F',1)
    text(root,ident+'-kind',badge,w-200,2,194,38,ident,11,'#64748B',1,'right')
    text(root,ident+'-path',path,3,39,w-6,32,ident,13,'#61778B')
    for i,f in enumerate(fields):
        yy=86+i*32
        if i%2==0:cell(root,ident+f'-bg{i}','',1,yy,w-2,32,'fillColor=#F7F9FC;strokeColor=none;',ident)
        name=f['name'];label=name
        if '.' in name:label='↳ '+name.split('.',1)[1]
        if f.get('notes','').startswith(('D;','D-only;')):label+=' [D]'
        text(root,ident+f'-name{i}',label,3,yy,w-250,32,ident,15,fieldPath=name,schemaPath=path,schemaType=f['type'],tooltip=f.get('notes',''))
        text(root,ident+f'-type{i}',f['type'],w-250,yy,242,32,ident,14,'#557188',0,'right')
    return h


def block(root,ident,title,subtitle,x,y,w=290,color='#FFFFFF'):
    cell(root,ident,'',x,y,w,74,'rounded=1;arcSize=6;fillColor='+color+';strokeColor=#CBD5E1;strokeWidth=1.5;')
    text(root,ident+'-kind',title,2,2,w-4,27,ident,11,'#64748B',1)
    text(root,ident+'-label',subtitle,2,29,w-4,38,ident,17,'#16324F',1)


def edge(root,ident,src,dst,points=None,ex=(.5,1),en=(.5,0)):
    old=next((c for c in root if c.get('id')==ident),None)
    if old is not None:root.remove(old)
    c=ET.SubElement(root,'mxCell',id=ident,value='',edge='1',parent='1',source=src,target=dst,style=f'edgeStyle=orthogonalEdgeStyle;rounded=0;html=0;endArrow=block;endFill=1;endSize=6;strokeColor=#7B8FA1;strokeWidth=1.6;exitX={ex[0]};exitY={ex[1]};entryX={en[0]};entryY={en[1]};exitPerimeter=0;entryPerimeter=0;')
    g=ET.SubElement(c,'mxGeometry',relative='1',attrib={'as':'geometry'})
    if points:
        a=ET.SubElement(g,'Array',attrib={'as':'points'})
        for x,y in points:ET.SubElement(a,'mxPoint',x=str(x),y=str(y))


def chapter4():
    schema=json.loads((ROOT/'docs/design/firestore-visualization-schema.json').read_text(encoding='utf-8'))
    docs={d['path']:d for d in schema['documents']}
    tree=ET.parse(RES/'source-pages.drawio');pages=tree.findall('diagram')
    for page in pages:
        g=graph(page);root=g.find('root')
        # Refresh all existing cards against the source inventory, keeping each position and ID.
        cards=[c for c in list(root) if c.get('parent')=='1' and c.get('schemaPath') in docs and c.get('vertex')=='1']
        for c in cards:
            path=c.get('schemaPath');geo=c.find('mxGeometry');children=[x for x in root if x.get('parent')==c.get('id')]
            palette=next((re.search(r'fillColor=(#[A-Fa-f0-9]+)',x.get('style','')).group(1) for x in children if x.get('value')=='' and re.search(r'fillColor=(#[A-Fa-f0-9]+)',x.get('style',''))),'#EAF1F8')
            card(root,c.get('id'),path,float(geo.get('x','0')),float(geo.get('y','0')),float(geo.get('width')),docs[path]['fields'],palette,'RULES ONLY' if docs[path]['status']=='rules_only' else 'DOCUMENT')
    # Overview: same collection -> document hierarchy, with current server collections.
    root=graph(pages[0]).find('root')
    for c in root:
        if c.get('id') in ['sgGYglO-yxxRlr8P9pXx-134','sgGYglO-yxxRlr8P9pXx-137']:
            c.set('style',re.sub(r'fillColor=#[A-Fa-f0-9]+','fillColor=#FFFFFF',c.get('style','')))
        if c.get('parent')=='sgGYglO-yxxRlr8P9pXx-134' and c.get('value')=='COLLECTION / RULES ONLY':c.set('value','COLLECTION')
        if c.get('parent')=='sgGYglO-yxxRlr8P9pXx-137':
            if c.get('value')=='DOCUMENT / RULES ONLY':c.set('value','DOCUMENT PATTERN')
            if c.get('value')=='{paymentId}':c.set('value','{bookingId}')
    for i,(name,document) in enumerate([('reviews','{bookingId}'),('paymentOrders','{orderCode}'),('appConfig','payments')]):
        y=2290+i*150;cid='instant-overview-c'+str(i);did='instant-overview-d'+str(i)
        remove(root,cid);remove(root,did)
        block(root,cid,'COLLECTION',name,410,y)
        block(root,did,'DOCUMENT PATTERN',document,830,y,360)
        edge(root,'instant-overview-db'+str(i),'sgGYglO-yxxRlr8P9pXx-3',cid,[(355,147),(355,y+37)],ex=(1,.5),en=(0,.5))
        edge(root,'instant-overview-cd'+str(i),cid,did,ex=(1,.5),en=(0,.5))
    # Booking page: use the blank space beneath Building and AvailabilityMonth.
    root=graph(pages[2]).find('root')
    for ident,path,x,y,w in [('instant-payment-card','payments/{bookingId}',0,1130,640),('instant-review-card','reviews/{bookingId}',2790,930,700)]:
        card(root,ident,path,x,y,w,docs[path]['fields'],'#EFEEF9' if 'payments/' in path else '#E7F3ED')
        cid=ident+'-collection';remove(root,cid);block(root,cid,'COLLECTION',path.split('/')[0],x+(w-270)/2,y-115,270)
        # Every added collection is a root child; never connect it beneath another document.
        trunk=-65 if x==0 else 2700
        db=next(c.get('parent') for c in root if c.get('value')=='DATABASE')
        edge(root,ident+'-db',db,cid,[(trunk,165),(trunk,y-78)],ex=(0,.5) if x==0 else (1,.5),en=(0,.5))
        edge(root,ident+'-cd',cid,ident)
    # Admin page: replace obsolete payments placeholder with backend order/config cards.
    root=graph(pages[4]).find('root')
    oldid='TEE0aiBO3Rl-2ukVxgoR-145';old=next((c for c in root if c.get('id')==oldid),None)
    if old is not None:
        geo=old.find('mxGeometry');x,y,w=[float(geo.get(k)) for k in ['x','y','width']]
        remove(root,'TEE0aiBO3Rl-2ukVxgoR-142');remove(root,oldid)
        db=next(c.get('parent') for c in root if c.get('value')=='DATABASE')
        for i,path in enumerate(['paymentOrders/{orderCode}','appConfig/payments']):
            yy=y+i*300;ident='instant-admin-'+str(i);cid=ident+'-collection'
            card(root,ident,path,x,yy,w,docs[path]['fields'],'#E7F3ED','SERVER')
            remove(root,cid);block(root,cid,'COLLECTION',path.split('/')[0],x+(w-300)/2,yy-115,300)
            edge(root,ident+'-db',db,cid,[(x-80,100),(x-80,yy-78)],ex=(1,.5),en=(0,.5))
            edge(root,ident+'-cd',cid,ident)
    write(tree,RES/'source-pages.drawio')
    # Also publish a clearly named editable source next to the report design artifacts.
    dest=ROOT/'docs/design/drawio/BroomSharing-current-20261028.drawio'
    dest.write_bytes((RES/'source-pages.drawio').read_bytes())
    manifest={'source':str(dest),'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'includedPages':[{'sourcePage':i+2,'name':p.get('name'),'id':p.get('id')} for i,p in enumerate(pages)],'note':'Updated native cells from current app/backend. Existing page identities, hierarchy and style retained.'}
    (RES/'source-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':
    chapter4()
