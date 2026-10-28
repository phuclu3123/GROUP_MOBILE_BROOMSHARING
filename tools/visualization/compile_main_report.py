"""Build the complete report, including bibliography and cross-references."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / 'Latex/FINAL_ANDROID (1)'
BUILD = ROOT / 'tmp/chapters123/build'
BUILD.mkdir(parents=True, exist_ok=True)
env = os.environ.copy()
env['TEXMF_OUTPUT_DIRECTORY'] = str(BUILD)
steps = [('pdflatex-1', ['pdflatex', '-shell-escape', '-interaction=nonstopmode',
                       '-halt-on-error', f'-output-directory={BUILD}', 'main.tex']),
         ('biber', ['biber', '--input-directory', str(BUILD), '--output-directory', str(BUILD), 'main']),
         ('pdflatex-2', ['pdflatex', '-shell-escape', '-interaction=nonstopmode',
                       '-halt-on-error', f'-output-directory={BUILD}', 'main.tex']),
         ('pdflatex-3', ['pdflatex', '-shell-escape', '-interaction=nonstopmode',
                       '-halt-on-error', f'-output-directory={BUILD}', 'main.tex'])]
for name, args in steps:
    with (BUILD / f'{name}.txt').open('w', encoding='utf-8') as log:
        result = subprocess.run(args, cwd=REPORT, env=env, stdout=log, stderr=subprocess.STDOUT)
    print(f'{name}: exit {result.returncode}', flush=True)
    if result.returncode:
        print((BUILD / f'{name}.txt').read_text(encoding='utf-8', errors='replace')[-4500:])
        raise SystemExit(result.returncode)
print(BUILD / 'main.pdf', flush=True)
