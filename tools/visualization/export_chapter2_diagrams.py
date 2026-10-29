"""Export editable diagrams through draw.io, then inspect the resulting vector PDFs."""
from pathlib import Path
import os
import subprocess
import sys
import json
import re
import html
import unicodedata
import xml.etree.ElementTree as ET
import pymupdf
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'docs/design/chapter2'
OUT=ROOT/'output/pdf/chapter2'
QA=ROOT/'tmp/chapter2-diagrams/qa'


def normalize(text):
    return ''.join(c for c in unicodedata.normalize('NFKC',text) if not c.isspace())


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    env=os.environ.copy()
    env.pop('ELECTRON_RUN_AS_NODE',None)
    OUT.mkdir(parents=True,exist_ok=True)
    QA.mkdir(parents=True,exist_ok=True)
    selected=set(sys.argv[1:])
    previous=SOURCE/'pdf-validation.json'
    checks=json.loads(previous.read_text(encoding='utf-8')) if selected and previous.exists() else []
    if selected: checks=[row for row in checks if row['file'] not in selected]
    for source in sorted(SOURCE.glob('*.drawio')):
        if selected and source.name not in selected: continue
        dest=OUT/(source.stem+'.pdf')
        result=subprocess.run([r'C:\Program Files\draw.io\draw.io.exe','--export',
            '--format','pdf','--all-pages','--crop','--border','20','--output',str(dest),str(source)],
            env=env,capture_output=True,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW)
        if result.returncode!=0:
            raise RuntimeError(source.name+': '+result.stderr.decode(errors='replace'))
        model=ET.parse(source).getroot()
        doc=pymupdf.open(dest)
        assert len(doc)==len(model.findall('diagram'))
        sheets=[]
        for i,(page,diagram) in enumerate(zip(doc,model.findall('diagram'))):
            txt=normalize(page.get_text())
            missing=[]
            for cell in diagram.findall('.//mxCell'):
                value=html.unescape(re.sub('<[^>]+>',' ',cell.get('value','')))
                if value and normalize(value) not in txt:missing.append(value)
            assert not missing,(source.name,diagram.get('name'),'missing text',missing)
            assert not page.get_images(),(source.name,'Raster-only output')
            assert len(page.get_drawings())>10
            fonts=page.get_fonts(full=True)
            assert fonts and all(doc.extract_font(f[0])[3] for f in fonts),'Fonts must be embedded'
            preview=QA/f'{source.stem}-{i+1:02}.png'
            page.get_pixmap(matrix=pymupdf.Matrix(1.25,1.25),alpha=False).save(preview)
            im=Image.open(preview).convert('RGB');im.thumbnail((640,450))
            tile=Image.new('RGB',(660,480),'#e5e9ed');tile.paste(im,((660-im.width)//2,10))
            ImageDraw.Draw(tile).text((12,460),str(i+1),fill='black');sheets.append(tile)
            checks.append({'file':source.name,'page':diagram.get('name'),'pdfPage':i+1,
                'missingText':[],'rasterImages':0,'vectorPaths':len(page.get_drawings()),
                'fontsEmbedded':True})
        cols=3 if len(sheets)>6 else 2
        contact=Image.new('RGB',(cols*660,((len(sheets)+cols-1)//cols)*480),'white')
        for i,tile in enumerate(sheets):contact.paste(tile,((i%cols)*660,(i//cols)*480))
        contact.save(QA/f'{source.stem}-contact.jpg')
        print(json.dumps({'pdf':str(dest.relative_to(ROOT)),'pages':len(doc),'vector':True,
                          'textCoverage':'PASS','embeddedFonts':True}),flush=True)
    (SOURCE/'pdf-validation.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')


if __name__=='__main__':main()
