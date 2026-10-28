"""Export Chapter 4 using the full report's page numbers and shared citations."""
import hashlib
import json
import unicodedata
from pathlib import Path
import pymupdf

ROOT=Path(__file__).resolve().parents[2]
source=ROOT/'tmp/chapter4/build-main/main.pdf'
doc=pymupdf.open(source)
toc=doc.get_toc()
def plain(text):
    return unicodedata.normalize('NFD',text).encode('ascii','ignore').decode()
start=next(t[2]-1 for t in toc if t[0]==1 and plain(t[1])=='THIET KE CO SO DU LIEU')
end=next(t[2]-2 for t in toc if t[0]==1 and plain(t[1])=='XAY DUNG UNG DUNG VA THIET KE KIEN TRUC')
refs=[t for t in toc if t[0]==1 and plain(t[1])=='TAI LIEU THAM KHAO']
assert len(refs)==1 and refs[0][2]>end+1
assert not any('THAM KHAO CUA CHUONG' in plain(p.get_text()).upper() for p in doc)
chapter=pymupdf.open()
chapter.insert_pdf(doc,from_page=start,to_page=end)
chapter.set_toc([[level,title,page-start] for level,title,page in toc if start<page<=end+1])
target=ROOT/'output/chapter4/BroomSharing-Chuong-4.pdf'
target.parent.mkdir(parents=True,exist_ok=True)
chapter.save(target,garbage=4,deflate=True)
meta={'sourceReport':str(source.relative_to(ROOT)),
      'sourceReportSha256':hashlib.sha256(source.read_bytes()).hexdigest(),
      'chapterPagesInReport':[start+1,end+1],'chapterPageCount':len(chapter),
      'globalBibliographyPageInReport':refs[0][2],'chapterBibliography':False,
      'appendixAdded':False,
      'note':'Extracted from the full report to retain shared citation labels and folios.'}
(target.parent/'export-manifest.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(meta))
