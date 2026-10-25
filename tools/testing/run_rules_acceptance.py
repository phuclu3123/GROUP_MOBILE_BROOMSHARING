"""Firestore emulator boundary tests. Never connects to the cloud database."""
import json
import pathlib
import subprocess
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'output/verification/business-completion-2026-10-24'
OUTPUT.mkdir(parents=True, exist_ok=True)
rules = (ROOT / 'firestore.rules').read_text(encoding='utf-8')
endpoint = 'http://127.0.0.1:8085/emulator/v1/projects/broom-sharing-firebase-mobile/databases/broomsharing:securityRules'
request = urllib.request.Request(endpoint, method='PUT', headers={'Content-Type':'application/json'},
        data=json.dumps({'rules': {'files': [{'name':'firestore.rules','content':rules}]}}).encode())
with urllib.request.urlopen(request, timeout=30) as response:
    assert response.status == 200

cases = [
    ('max_blackouts', ['--dates','16','--ranges','8'], 0),
    ('blocked_iso_day', ['--dates','16','--ranges','8','--blocked','date'], 1),
    ('blocked_legacy_day', ['--blocked','legacy'], 1),
    ('blocked_interval', ['--dates','16','--ranges','8','--blocked','range'], 1),
    ('48_technical_slots', ['--hours','24'], 0),
    ('non_contiguous_slots', ['--forged-slots'], 1),
    ('quarter_max_blackouts', ['--months','3','--dates','16','--ranges','8'], 0),
    ('quarter_final_month_blocked', ['--months','3','--blocked','date','--block-month-offset','2'], 1),
    ('quarter_exclusive_end', ['--months','3','--blocked','date','--block-month-offset','3'], 0),
    ('fixed_legacy_day', ['--months','1','--blocked','legacy'], 1),
    ('forged_price', ['--tamper-price'], 1),
    ('missing_room_write', ['--omit-room'], 1),
    ('missing_audit_write', ['--omit-audit'], 1),
]
results = []
for name, args, expected in cases:
    result = subprocess.run([sys.executable, str(ROOT / 'tools/testing/firestore_rules_probe.py')] + args,
            cwd=ROOT, text=True, encoding='utf-8', capture_output=True, timeout=45)
    passed = result.returncode == expected and 'maximum' not in result.stdout
    if expected != 0:
        passed = passed and 'PERMISSION_DENIED' in result.stdout
    results.append({'name': name, 'args': args, 'pass': passed,
        'output': result.stdout, 'stderr': result.stderr})
    print(name, 'PASS' if passed else 'FAIL', flush=True)
(OUTPUT / 'rules-boundaries.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
raise SystemExit(0 if all(x['pass'] for x in results) else 1)
