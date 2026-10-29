"""Prepare retained real Auth accounts and a loopback-only historical-fixture bridge.

Business actions are performed by Android repositories with each user's ID token.
The bridge only prepares isolated time fixtures; it never proxies business writes.
"""
import argparse
import datetime as dt
import hashlib
import http.server
import json
import pathlib
import re
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
PROJECT = 'broom-sharing-firebase-mobile'
DATABASE = 'broomsharing'
PRIVATE = ROOT / 'data/demo/video-credentials.local.properties'
MANIFEST = ROOT / 'data/demo/video-business.local.json'
OUTPUT = ROOT / 'output/verification/cloud-business-2026-10-24'
API = 'https://firestore.googleapis.com/v1/projects/' + PROJECT + '/databases/' + DATABASE + '/documents/'
_oauth = None
_oauth_at = 0


def fixture_admin_token():
    global _oauth, _oauth_at
    if _oauth and time.time() - _oauth_at < 3000:
        return _oauth
    config = json.loads(pathlib.Path('C:/Users/ADMIN/.config/configstore/firebase-tools.json').read_text(encoding='utf-8'))
    source = urllib.request.urlopen('https://raw.githubusercontent.com/firebase/firebase-tools/master/src/api.ts', timeout=30).read().decode()
    client_id = re.search(r'"([0-9]+-[A-Za-z0-9]+\.apps\.googleusercontent\.com)"', source).group(1)
    client_secret = re.search(r'FIREBASE_CLIENT_SECRET", "([^"]+)"', source).group(1)
    body = urllib.parse.urlencode({'grant_type': 'refresh_token', 'refresh_token': config['tokens']['refresh_token'],
                                  'client_id': client_id, 'client_secret': client_secret}).encode()
    with urllib.request.urlopen(urllib.request.Request('https://oauth2.googleapis.com/token', data=body), timeout=30) as response:
        _oauth = json.load(response)['access_token']
    _oauth_at = time.time()
    return _oauth


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8').splitlines()
                if line and not line.startswith('#') and '=' in line)


def save_private(data):
    PRIVATE.write_text('# Private retained VIDEO DEMO accounts. Never commit or show in recordings.\n'
                       + '\n'.join(k + '=' + str(v) for k, v in sorted(data.items())) + '\n', encoding='utf-8')


def request(url, body=None, token=None, method=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    try:
        with urllib.request.urlopen(urllib.request.Request(url, method=method, headers=headers,
                data=None if body is None else json.dumps(body).encode()), timeout=45) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        # Do not expose an API key, password, token or a server error payload in a log.
        raise RuntimeError('Firebase HTTP ' + str(error.code)) from None


def encode(value):
    if value is None:
        return {'nullValue': None}
    if isinstance(value, bool):
        return {'booleanValue': value}
    if isinstance(value, int):
        return {'integerValue': str(value)}
    if isinstance(value, list):
        return {'arrayValue': {'values': [encode(v) for v in value]}}
    return {'stringValue': value}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serve', action='store_true')
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    config = json.loads((ROOT / 'app/google-services.json').read_text(encoding='utf-8'))
    assert config['project_info']['project_id'] == PROJECT
    key = next(c for c in config['client'] if c['client_info']['android_client_info']['package_name']
               == 'vn.edu.ueh.phuclu.broomsharing')['api_key'][0]['current_key']

    def auth(action, email, password):
        return request('https://identitytoolkit.googleapis.com/v1/accounts:' + action + '?key=' + key,
                       {'email': email, 'password': password, 'returnSecureToken': True}, method='POST')

    # Privileged bootstrap is restricted to these new demo users, not business acceptance actions.
    initial_admin = fixture_admin_token()
    credentials = properties(PRIVATE) if PRIVATE.exists() else {}
    names = {'host': '[DEMO] Hoàng Nam', 'guest': '[DEMO] Minh Anh',
             'other': '[DEMO] Linh Chi', 'admin': '[DEMO] Quản trị video'}
    roles = {'host': 'HOST', 'guest': 'GUEST', 'other': 'GUEST', 'admin': 'ADMIN'}
    now = dt.datetime.now(dt.timezone.utc).isoformat()
    for number, alias in enumerate(roles):
        credentials.setdefault(alias + '.email', 'broom.video.' + alias + '.20261024@example.com')
        credentials.setdefault(alias + '.password', secrets.token_urlsafe(21) + 'aA1!')
        credentials[alias + '.name'] = names[alias]
        credentials[alias + '.role'] = roles[alias]
        credentials[alias + '.username'] = 'video_' + alias + '_20261024'
        save_private(credentials)
        action = 'signInWithPassword' if alias + '.uid' in credentials else 'signUp'
        result = auth(action, credentials[alias + '.email'], credentials[alias + '.password'])
        credentials[alias + '.uid'] = result['localId']
        save_private(credentials)
        uid = result['localId']
        profile = {'uid': uid, 'schemaVersion': 1, 'name': names[alias],
                   'username': 'video_' + alias + '_20261024', 'email': credentials[alias + '.email'],
                   'phone': '+84900000900' + str(number), 'phoneVerified': False,
                   'role': roles[alias], 'status': 'ACTIVE'}
        fields = {k: encode(v) for k, v in profile.items()}
        fields.update(createdAt={'timestampValue': now}, updatedAt={'timestampValue': now})
        # New dedicated accounts only; the existing bootstrap Admin is never changed.
        request(API + 'users/' + uid, {'fields': fields}, initial_admin, 'PATCH')
        request(API + 'usernames/' + profile['username'], {'fields': {
            'uid': encode(uid), 'email': encode(profile['email'])}}, initial_admin, 'PATCH')
        if alias == 'host':
            request(API + 'publicHosts/' + uid, {'fields': {
                k: encode(profile[k]) for k in ['uid', 'name', 'status']}}, initial_admin, 'PATCH')
        print('Retained real Auth account ready:', alias, roles[alias], flush=True)

    dataset = json.loads((ROOT / 'data/demo/firestore-dataset.json').read_text(encoding='utf-8'))
    photos = [image['stringValue'] for d in dataset['documents'] if d['path'].startswith('rooms/')
              for image in d['fields'].get('imageUrls', {}).get('arrayValue', {}).get('values', [])]
    credentials['photoUrl'] = next(p for p in photos if p.startswith('https://') and 'example.com' not in p)
    credentials['bridgeSecret'] = secrets.token_urlsafe(32)
    credentials['businessRun'] = 'video_' + str(int(time.time() * 1000)) + '_'
    save_private(credentials)
    manifest = {'project': PROJECT, 'database': DATABASE, 'run': credentials['businessRun'],
                'preparedAtUTC': now, 'retainedAccounts': [
                    {'alias': a, 'uid': credentials[a + '.uid'], 'role': roles[a]} for a in roles],
                'historicalFixtureWrites': [], 'existingBusinessDocumentsChanged': False}
    MANIFEST.write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    OUTPUT.joinpath('account-preparation.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    if not args.serve:
        return

    class Bridge(http.server.BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_PATCH(self):
            try:
                assert self.headers.get('Authorization') == 'Bearer ' + credentials['bridgeSecret']
                assert int(self.headers.get('Content-Length', '0')) < 100000
                path = self.path.removeprefix('/fixtures/')
                assert self.path == '/fixtures/' + path and len(path.split('/')) == 2
                collection, identity = path.split('/')
                body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                fields = body['fields']
                prefix = credentials['businessRun']
                host = credentials['host.uid']
                if collection == 'bookings':
                    assert len(identity) == 64 and all(c in '0123456789abcdef' for c in identity)
                    assert fields['id']['stringValue'] == identity
                    assert fields['roomId']['stringValue'].startswith(prefix)
                    assert fields['ownerUid']['stringValue'] == host
                    assert fields['guestUid']['stringValue'] in [credentials[a + '.uid'] for a in ['guest', 'other']]
                elif collection == 'rooms':
                    assert identity.startswith(prefix) and fields['ownerUid']['stringValue'] == host
                elif collection == 'availabilityMonths':
                    assert identity.startswith(prefix) and fields['roomId']['stringValue'].startswith(prefix)
                else:
                    raise AssertionError('Fixture collection is not allowed')
                admin_token = fixture_admin_token()
                result = request(API + path, body, admin_token, 'PATCH')
                manifest['historicalFixtureWrites'].append({'path': path, 'atUTC': dt.datetime.now(dt.timezone.utc).isoformat(),
                    'payloadSha256': hashlib.sha256(json.dumps(body, sort_keys=True).encode()).hexdigest()})
                MANIFEST.write_text(json.dumps(manifest, indent=2), encoding='utf-8')
                OUTPUT.joinpath('historical-fixtures.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'prepared': True, 'updateTime': result.get('updateTime')}).encode())
            except Exception as error:
                self.send_response(403)
                self.end_headers()
                self.wfile.write(json.dumps({'fixtureError': type(error).__name__}).encode())

    print('Historical fixture bridge ready on loopback port 8086', flush=True)
    http.server.HTTPServer(('127.0.0.1', 8086), Bridge).serve_forever()


if __name__ == '__main__':
    main()
