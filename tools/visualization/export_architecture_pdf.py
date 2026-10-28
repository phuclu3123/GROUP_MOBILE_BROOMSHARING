"""Print the approved draw.io SVG as a vector PDF for the main report."""
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

import pymupdf
from draw_architecture_mcp import Canvas, rpc

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'docs/design/drawio/05-application-architecture.svg'
TARGET = ROOT / 'Latex/FINAL_ANDROID (1)/Resources/chapter5/application-architecture.pdf'
WORK = ROOT / 'tmp/pdfs/architecture'


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    # Always export the open page first: local images may predate the user's edits.
    canvas = Canvas()
    for fmt, extension in [('xml', 'drawio'), ('svg', 'svg'), ('png', 'png')]:
        rpc('export-diagram', dict(canvas.target, format=fmt,
            scale=2 if fmt == 'png' else 1, border=10, background='#ffffff',
            crop=True, size='page', embed_xml=True,
            output_path=str(SOURCE.with_suffix('.' + extension))))
    svg = ET.parse(SOURCE).getroot()
    _, _, width, height = map(float, svg.get('viewBox').split())
    html = WORK / 'architecture-print.html'
    html.write_text(
        '<!doctype html><html><head><meta charset="utf-8"><style>'
        f'@page{{size:{width}px {height}px;margin:0}}'
        'html,body{margin:0;padding:0;background:white;line-height:0}'
        f'img{{display:block;width:{width}px;height:{height}px}}'
        '</style></head><body>'
        f'<img src="{SOURCE.as_uri()}"></body></html>', encoding='utf-8')
    chrome = Path('C:/Program Files/Google/Chrome/Application/chrome.exe')
    subprocess.run([
        str(chrome), '--headless', '--disable-gpu', '--no-first-run',
        '--no-default-browser-check', '--no-pdf-header-footer',
        '--run-all-compositor-stages-before-draw',
        '--user-data-dir=' + str(WORK / 'chrome-profile'),
        '--print-to-pdf=' + str(TARGET), html.as_uri(),
    ], capture_output=True, timeout=60, check=True,
        creationflags=subprocess.CREATE_NO_WINDOW)
    with pymupdf.open(TARGET) as pdf:
        assert len(pdf) == 1
        text = pdf[0].get_text()
        assert all(label in text for label in ['ViewModel', 'Repository', 'Model', 'Firestore'])
        # The figure must retain vector drawing/text rather than a full-page screenshot.
        assert len(pdf[0].get_drawings()) > 10
        assert not pdf[0].get_images()
        pdf[0].get_pixmap(matrix=pymupdf.Matrix(1.4, 1.4), alpha=False).save(WORK / 'architecture-pdf.png')
        print(json.dumps({'pdf': str(TARGET.relative_to(ROOT)), 'pages': len(pdf),
                          'vectorPaths': len(pdf[0].get_drawings()), 'rasterImages': 0}))


if __name__ == '__main__':
    main()
