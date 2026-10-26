"""Download the curated 30-photo demo pack, with provenance and image checks.

Uses only public CDN URLs linked by the free Unsplash photo pages in sources.json.
Does not upload, modify Firestore, or read credentials.
"""
import concurrent.futures as cf
import datetime as dt
import hashlib
import io
import json
from pathlib import Path
import urllib.request
from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / 'data/demo/media'


def main():
    source = json.loads((PACK / 'sources.json').read_text(encoding='utf-8'))
    assert len(source['files']) == 30
    assert len({r['id'] for r in source['files']}) == 30

    def download(row):
        target = PACK / row['file']
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            with urllib.request.urlopen(row['downloadUrl'], timeout=45) as response:
                content = response.read(5 * 1024 * 1024 + 1)
            assert 0 < len(content) <= 5 * 1024 * 1024
            image = Image.open(io.BytesIO(content))
            image.load()
            assert image.format == 'JPEG'
            target.write_bytes(content)
        content = target.read_bytes()
        with Image.open(target) as image:
            image.load()
            assert image.format == 'JPEG' and max(image.size) >= 1200
            row.update(width=image.width, height=image.height, bytes=len(content),
                       sha256=hashlib.sha256(content).hexdigest())
        assert len(content) <= 5 * 1024 * 1024
        print('Ready', row['group'], row['target'], row['id'], flush=True)
        return row

    with cf.ThreadPoolExecutor(max_workers=3) as pool:
        files = list(pool.map(download, source['files']))
    assert len({r['sha256'] for r in files}) == 30, 'Duplicate photo bytes'
    receipt = dict(preparedAt=dt.datetime.now(dt.timezone.utc).isoformat(),
                   purpose=source['purpose'], files=files)
    (PACK / 'manifest.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    # A visual index helps select files in the Android picker and audit the pack.
    font_path = Path('C:/Windows/Fonts/arial.ttf')
    from PIL import ImageFont
    font = ImageFont.truetype(str(font_path), 17) if font_path.exists() else None
    sheet = Image.new('RGB', (1600, 6 * 230 + 56), 'white')
    draw = ImageDraw.Draw(sheet)
    draw.text((18, 14), 'BROOMSHARING | 30 DEMO PHOTOS | Unsplash | Source details: sources.json', fill='#172b40', font=font)
    for i, row in enumerate(files):
        x = (i % 5) * 320; y = 56 + (i // 5) * 230
        with Image.open(PACK / row['file']) as original:
            thumbnail = ImageOps.contain(original, (306, 184))
            sheet.paste(thumbnail, (x + (320 - thumbnail.width) // 2, y))
        draw.text((x + 8, y + 190), row['group'] + '/' + row['target'] + ' #' + str(row['order']), fill='black', font=font)
        draw.text((x + 8, y + 212), row['id'], fill='#555555', font=font)
    sheet.save(PACK / 'contact-sheet.jpg', quality=92)
    print('Verified 30 unique JPEG photos, total bytes:', sum(r['bytes'] for r in files))


if __name__ == '__main__':
    main()
