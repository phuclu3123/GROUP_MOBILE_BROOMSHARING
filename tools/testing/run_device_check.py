"""Run one explicit Android cohort with bounded ADB calls and immutable APK copies."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/verification/final-qa-2026-10-28'
ADB = Path('C:/Users/ADMIN/AppData/Local/Android/Sdk/platform-tools/adb.exe')
RUNNER = 'vn.edu.ueh.phuclu.broomsharing.test/androidx.test.runner.AndroidJUnitRunner'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--name', required=True)
    parser.add_argument('--class-name', required=True)
    parser.add_argument('--variant', choices=['cloud', 'emulator'], required=True)
    parser.add_argument('--flag', action='append', default=[])
    args = parser.parse_args()
    assert re.fullmatch(r'[a-z0-9-]+', args.name)
    OUT.mkdir(parents=True, exist_ok=True)
    build_config = next((ROOT / 'app/build/generated/source/buildConfig/debug').rglob('BuildConfig.java')).read_text(encoding='utf-8')
    actual = re.search(r'FIRESTORE_EMULATOR_HOST\s*=\s*"([^"]*)"', build_config)[1]
    assert actual == ('' if args.variant == 'cloud' else '10.0.2.2'), 'Build both APKs with the requested variant first'
    snapshots = ROOT / 'tmp/final-qa-apks' / args.variant
    snapshots.mkdir(parents=True, exist_ok=True)
    evidence = {'variant': args.variant, 'class': args.class_name, 'passed': False, 'apkHashes': {}}
    for filename, source in [
        ('app.apk', 'app/build/outputs/apk/debug/app-debug.apk'),
        ('test.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
    ]:
        target = snapshots / filename
        shutil.copy2(ROOT / source, target)
        evidence['apkHashes'][filename] = hashlib.sha256(target.read_bytes()).hexdigest()
    # No streaming: avoid a stalled abb_exec stream sharing an APK being rebuilt in place.
    try:
        # A System UI ANR steals focus from Espresso even when Activity data loads correctly.
        state = subprocess.run([str(ADB), '-s', 'emulator-5554', 'shell', 'dumpsys', 'window'],
                               capture_output=True, timeout=15).stdout.decode('utf-8', errors='replace')
        focus = next((line for line in state.splitlines() if 'mCurrentFocus=' in line), '')
        assert focus and 'Application Not Responding' not in focus, 'Emulator has an ANR dialog; recover before running UI tests'
        for filename in ['app.apk', 'test.apk']:
            print('Installing pinned ' + args.variant + ' ' + filename, flush=True)
            r = subprocess.run([str(ADB), '-s', 'emulator-5554', 'install', '--no-streaming', '-r', str(snapshots / filename)],
                               capture_output=True, timeout=90)
            assert r.returncode == 0 and b'Success' in r.stdout, 'APK installation failed'
        command = [str(ADB), '-s', 'emulator-5554', 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', args.class_name]
        for flag in args.flag:
            assert re.fullmatch(r'[A-Za-z][A-Za-z0-9]+', flag)
            command += ['-e', flag, 'true']
        command.append(RUNNER)
        print('Running ' + args.class_name, flush=True)
        r = subprocess.run(command, capture_output=True, timeout=540)
        text = (r.stdout + r.stderr).decode('utf-8', errors='replace')
        (OUT / (args.name + '.log')).write_text(text, encoding='utf-8')
        count = re.search(r'OK \((\d+) tests?\)', text)
        evidence['tests'] = int(count[1]) if count else 0
        evidence['passed'] = (r.returncode == 0 and count is not None and evidence['tests'] > 0
                              and 'FAILURES!!!' not in text and 'INSTRUMENTATION_STATUS_CODE: -3' not in text)
        print(text[-3500:], flush=True)
    except subprocess.TimeoutExpired:
        evidence['error'] = 'ADB/cohort timeout; no acceptance claimed'
    finally:
        (OUT / (args.name + '.json')).write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(evidence), flush=True)
    return 0 if evidence['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
