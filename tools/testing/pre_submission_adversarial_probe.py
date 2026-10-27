"""Reproduce malformed-data risks on an isolated local emulator database only."""
import base64
import hashlib
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
BASE = 'http://127.0.0.1:8085'
DB = 'projects/broom-sharing-firebase-mobile/databases/pre_submission_adversarial_qa'


def value(x):
    if isinstance(x, bool): return {'booleanValue': x}
    if isinstance(x, int): return {'integerValue': str(x)}
    if isinstance(x, dict): return {'mapValue': {'fields': {k: value(v) for k, v in x.items()}}}
    if isinstance(x, list): return {'arrayValue': {'values': [value(v) for v in x]}}
    return {'stringValue': x}


def token(uid):
    def part(x): return base64.urlsafe_b64encode(json.dumps(x).encode()).decode().rstrip('=')
    return part({'alg': 'none', 'typ': 'JWT'}) + '.' + part({
        'sub': uid, 'user_id': uid, 'aud': 'broom-sharing-firebase-mobile',
        'iss': 'https://securetoken.google.com/broom-sharing-firebase-mobile',
        'iat': int(time.time()), 'exp': int(time.time()) + 3600,
        'firebase': {'sign_in_provider': 'password'}}) + '.'


def call(path, body, actor=None, method='POST'):
    request = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), method=method,
        headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (token(actor) if actor else 'owner')})
    try:
        with urllib.request.urlopen(request, timeout=30) as response: return response.status
    except urllib.error.HTTPError as error:
        return error.code


def seed(path, fields):
    assert call('/v1/' + DB + '/documents/' + path,
                {'fields': {k: value(v) for k, v in fields.items()}}, method='PATCH') == 200


def write(path, fields):
    return {'update': {'name': DB + '/documents/' + path,
                      'fields': {k: value(v) for k, v in fields.items()}},
            'updateMask': {'fieldPaths': list(fields)}}


def main():
    rules = (ROOT / 'firestore.rules').read_text(encoding='utf-8')
    assert call('/emulator/v1/' + DB + ':securityRules',
                {'rules': {'files': [{'name': 'firestore.rules', 'content': rules}]}}, method='PUT') == 200
    results = []
    for label, payload in [('profile_name_map', {'name': {'unexpected': 'map'}}),
                           ('profile_phone_number', {'phone': 12345}),
                           ('profile_phone_invalid_string', {'phone': 'not-a-phone'})]:
        uid = 'qa_' + label
        seed('users/' + uid, {'uid': uid, 'role': 'GUEST', 'status': 'ACTIVE',
                            'name': 'QA', 'phone': '+84900000000', 'phoneVerified': False})
        code = call('/v1/' + DB + '/documents:commit', {'writes': [write('users/' + uid, payload)]}, uid)
        results.append({'case': label, 'expectedForHardening': 403, 'actual': code,
                        'unexpectedAccepted': code == 200})
    uid = 'qa_host_name_map'
    seed('users/' + uid, {'uid': uid, 'role': 'HOST', 'status': 'ACTIVE', 'name': 'QA',
                         'phone': '+84900000000', 'phoneVerified': False})
    seed('publicHosts/' + uid, {'uid': uid, 'name': 'QA', 'status': 'ACTIVE', 'photoUrl': ''})
    code = call('/v1/' + DB + '/documents:commit', {'writes': [
        write('users/' + uid, {'name': {'unexpected': 'map'}}),
        write('publicHosts/' + uid, {'name': {'unexpected': 'map'}})]}, uid)
    results.append({'case': 'host_can_publish_map_name', 'expectedForHardening': 403,
                    'actual': code, 'unexpectedAccepted': code == 200})
    out = ROOT / 'output/verification/pre-submission-2026-10-27'
    out.mkdir(parents=True, exist_ok=True)
    report = {'emulatorOnly': True, 'database': DB.rsplit('/', 1)[-1],
              'rulesSha256': hashlib.sha256(rules.encode()).hexdigest(), 'results': results,
              'note': 'Diagnostic finding reproduction, not acceptance passes. No production writes.'}
    (out / 'malformed-profile-reproduction.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
