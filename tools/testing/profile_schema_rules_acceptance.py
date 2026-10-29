"""Regression for malformed profiles; isolated emulator only, never cloud writes."""
import hashlib
import json
import pre_submission_adversarial_probe as probe


def main():
    probe.DB = 'projects/broom-sharing-firebase-mobile/databases/profile_schema_qa'
    rules = (probe.ROOT / 'firestore.rules').read_text(encoding='utf-8')
    assert probe.call('/emulator/v1/' + probe.DB + ':securityRules',
                      {'rules': {'files': [{'name': 'firestore.rules', 'content': rules}]}},
                      method='PUT') == 200
    results = []
    cases = [('name map', {'name': {'unexpected': 'map'}}, 403),
             ('name empty', {'name': ''}, 403),
             ('name whitespace', {'name': ' \t\n '}, 403),
             ('name too long', {'name': 'x' * 101}, 403),
             ('phone number', {'phone': 12345}, 403),
             ('phone invalid', {'phone': 'not-a-phone'}, 403),
             ('phone missing country code', {'phone': '0900000000'}, 403),
             ('valid name', {'name': 'Người dùng kiểm thử'}, 200),
             ('valid phone', {'phone': '+84900000111'}, 200)]
    for i, (name, data, expected) in enumerate(cases):
        uid = 'schema_guest_' + str(i)
        probe.seed('users/' + uid, dict(uid=uid, role='GUEST', status='ACTIVE',
                   name='QA', phone='+84900000000', phoneVerified=False))
        code = probe.call('/v1/' + probe.DB + '/documents:commit',
                          {'writes': [probe.write('users/' + uid, data)]}, uid)
        results.append(dict(case=name, expected=expected, actual=code, passed=code == expected))
    for name, value, expected in [('map projection', {'unexpected': 'map'}, 403),
                                  ('valid projection', 'Host kiểm thử', 200)]:
        uid = 'schema_host_' + str(expected)
        probe.seed('users/' + uid, dict(uid=uid, role='HOST', status='ACTIVE', name='QA',
                   phone='+84900000000', phoneVerified=False))
        probe.seed('publicHosts/' + uid, dict(uid=uid, name='QA', status='ACTIVE', photoUrl=''))
        code = probe.call('/v1/' + probe.DB + '/documents:commit', {'writes': [
            probe.write('users/' + uid, {'name': value}),
            probe.write('publicHosts/' + uid, {'name': value})]}, uid)
        results.append(dict(case=name, expected=expected, actual=code, passed=code == expected))
    for i, (name, phone, expected) in enumerate([('New Guest', '+84900000000', 200),
                                                ({'wrong': 'map'}, '+84900000000', 403),
                                                ('New Guest', 12345, 403)]):
        uid = 'schema_create_' + str(i)
        probe.seed('usernames/' + uid, dict(uid=uid, email='qa@example.com'))
        data = dict(uid=uid, schemaVersion=1, name=name, username=uid,
                    email='qa@example.com', phone=phone, phoneVerified=False,
                    role='GUEST', status='ACTIVE')
        code = probe.call('/v1/' + probe.DB + '/documents:commit',
                          {'writes': [probe.write('users/' + uid, data)]}, uid)
        results.append(dict(case='registration ' + str(i), expected=expected,
                            actual=code, passed=code == expected))
    uid = 'schema_legacy_guest'
    probe.seed('users/' + uid, dict(uid=uid, name='Legacy', role='GUEST', status='ACTIVE'))
    code = probe.call('/v1/' + probe.DB + '/documents:commit',
                      {'writes': [probe.write('users/' + uid, {'photoUrl': ''})]}, uid)
    results.append(dict(case='legacy missing phone unrelated update', expected=200,
                        actual=code, passed=code == 200))
    out = probe.ROOT / 'output/verification/stabilization-2026-10-27'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'profile-schema-rules.json').write_text(json.dumps({
        'emulatorOnly': True, 'rulesSha256': hashlib.sha256(rules.encode()).hexdigest(),
        'results': results}, ensure_ascii=False, indent=2), encoding='utf-8')
    for row in results:
        print(row['case'], 'PASS' if row['passed'] else 'FAIL', flush=True)
    assert all(r['passed'] for r in results), 'Profile schema regression failed'


if __name__ == '__main__':
    main()
