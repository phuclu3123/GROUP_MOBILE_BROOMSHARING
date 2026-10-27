"""Read deployed Firestore rules and index metadata; never deploy or write data."""
from pathlib import Path
import hashlib
import importlib.util
import json
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('demo_auth_helpers', ROOT / 'tools/testing/prepare_cloud_business_demo.py')
helpers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helpers)


def main():
    token = helpers.fixture_admin_token()

    def get(url):
        with urllib.request.urlopen(urllib.request.Request(url, headers={'Authorization': 'Bearer ' + token}), timeout=30) as r:
            return json.load(r)

    project = 'broom-sharing-firebase-mobile'
    release = get(f'https://firebaserules.googleapis.com/v1/projects/{project}/releases/cloud.firestore/broomsharing')
    ruleset = get('https://firebaserules.googleapis.com/v1/' + release['rulesetName'])
    files = ruleset['source']['files']
    local = (ROOT / 'firestore.rules').read_text(encoding='utf-8')
    deployed = next(f['content'] for f in files if f['name'].endswith('firestore.rules'))
    indexes = get(f'https://firestore.googleapis.com/v1/projects/{project}/databases/broomsharing/collectionGroups/-/indexes')
    report = {'rulesetName': release['rulesetName'], 'localRulesMatchDeployed': local == deployed,
              'localRulesSha256': hashlib.sha256(local.encode()).hexdigest(),
              'deployedRulesSha256': hashlib.sha256(deployed.encode()).hexdigest(),
              'indexStates': [{'collection': x['name'].split('/collectionGroups/')[1].split('/')[0],
                               'state': x.get('state'), 'fields': x.get('fields')} for x in indexes.get('indexes', [])],
              'readOnly': True}
    out = ROOT / 'output/verification/pre-submission-2026-10-27'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'deployed-rules-indexes.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
