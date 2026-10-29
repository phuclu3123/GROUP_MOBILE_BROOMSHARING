"""Observe the emulator only. No test navigation, business writes or credential output."""
import argparse
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ADB = Path('C:/Users/ADMIN/AppData/Local/Android/Sdk/platform-tools/adb.exe')
parser = argparse.ArgumentParser()
parser.add_argument('--image')
args = parser.parse_args()
sys.stdout.reconfigure(encoding='utf-8')


def adb(*command):
    return subprocess.run([str(ADB), '-s', 'emulator-5554', *command],
                          check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=35)


adb('shell', 'rm', '-f', '/sdcard/qa-current.xml')
dump = adb('shell', 'uiautomator', 'dump', '/sdcard/qa-current.xml')
output = dump.stdout + dump.stderr
if b'ERROR' in output or b'dumped to:' not in output:
    raise RuntimeError('No fresh UI snapshot; no cached XML was used')
tree = ET.fromstring(adb('exec-out', 'cat', '/sdcard/qa-current.xml').stdout)
for node in tree.iter('node'):
    if node.get('password') == 'true' or 'password' in node.get('resource-id', '').lower():
        node.set('text', '')
        continue
    if node.get('text'):
        print(node.get('text'), node.get('enabled'), node.get('bounds'))
(ROOT / 'tmp/qa-current.xml').write_bytes(ET.tostring(tree, encoding='utf-8'))
if args.image:
    target = (ROOT / args.image).resolve()
    target.relative_to(ROOT)
    target.parent.mkdir(parents=True, exist_ok=True)
    adb('shell', 'screencap', '-p', '/sdcard/qa-observed.png')
    adb('pull', '/sdcard/qa-observed.png', str(target))
