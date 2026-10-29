"""Provision the scoped Java backend identity; defaults to a read-only plan.

An OAuth access token is read from stdin, never command-line arguments or logs.
This tool does not change Security Rules, payOS webhooks or Firestore documents.
"""

import argparse
import base64
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
PROJECT = "broom-sharing-firebase-mobile"
ACCOUNT_ID = "broomsharing-payments"
EMAIL = f"{ACCOUNT_ID}@{PROJECT}.iam.gserviceaccount.com"
ACCOUNT = f"projects/{PROJECT}/serviceAccounts/{EMAIL}"
CONDITION = {
    "title": "BroomSharingPaymentDatabase",
    "description": "Java payment backend accesses only the broomsharing Firestore database",
    "expression": f'resource.name=="projects/{PROJECT}/databases/broomsharing"',
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    token = sys.stdin.readline().strip()
    if not token:
        raise SystemExit("OAuth token must be supplied through stdin.")

    def call(host, path, body=None, method=None):
        assert host in ("iam.googleapis.com", "cloudresourcemanager.googleapis.com")
        assert path.startswith(f"/v1/projects/{PROJECT}")
        request = urllib.request.Request(
            "https://" + host + path,
            data=None if body is None else json.dumps(body).encode(),
            method=method,
            headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=45) as response:
            return json.load(response)

    def current_policy():
        return call("cloudresourcemanager.googleapis.com", f"/v1/projects/{PROJECT}:getIamPolicy",
                    {"options": {"requestedPolicyVersion": 3}}, "POST")

    def desired_bindings():
        member = "serviceAccount:" + EMAIL
        return [
            {"role": "roles/datastore.user", "members": [member], "condition": CONDITION},
            {"role": "roles/firebaseauth.viewer", "members": [member]},
        ]

    policy = current_policy()
    accounts = call("iam.googleapis.com", f"/v1/projects/{PROJECT}/serviceAccounts").get("accounts", [])
    exists = any(a.get("email") == EMAIL for a in accounts)
    plan = {"project": PROJECT, "database": "broomsharing", "serviceAccount": EMAIL,
            "accountExists": exists, "bindings": desired_bindings(),
            "credentialFile": "backend/payments/credentials/service-account.json",
            "changes": "Dedicated service account, scoped IAM grants and one local JSON key",
            "firestoreDocumentsModified": False, "payosWebhookModified": False}
    print(json.dumps(plan, ensure_ascii=False, indent=2), flush=True)
    if not args.apply:
        return

    private = ROOT / "backend/payments/credentials"
    private.mkdir(parents=True, exist_ok=True)
    credential = private / "service-account.json"
    if credential.exists():
        saved = json.loads(credential.read_text(encoding="utf-8"))
        if saved.get("client_email") != EMAIL or saved.get("project_id") != PROJECT:
            raise SystemExit("Existing credential has a different identity; preserved unchanged.")
    backup = private / "iam-before.private.json"
    if not backup.exists():
        backup.write_text(json.dumps(policy, indent=2), encoding="utf-8")
    if not exists:
        call("iam.googleapis.com", f"/v1/projects/{PROJECT}/serviceAccounts",
             {"accountId": ACCOUNT_ID, "serviceAccount": {"displayName": "BroomSharing Java payments"}}, "POST")

    # Read the latest etag before adding our bindings; preserve every other principal and grant.
    policy = current_policy()
    policy["version"] = 3
    bindings = policy.setdefault("bindings", [])
    for desired in desired_bindings():
        match = next((b for b in bindings if b.get("role") == desired["role"]
                      and b.get("condition") == desired.get("condition")), None)
        if match is None:
            bindings.append(desired)
        elif desired["members"][0] not in match["members"]:
            match["members"].append(desired["members"][0])
    call("cloudresourcemanager.googleapis.com", f"/v1/projects/{PROJECT}:setIamPolicy", {"policy": policy}, "POST")
    # A rerun keeps its existing private key instead of creating unnecessary long-lived keys.
    if not credential.exists():
        key = call("iam.googleapis.com", f"/v1/{ACCOUNT}/keys",
                   {"privateKeyType": "TYPE_GOOGLE_CREDENTIALS_FILE", "keyAlgorithm": "KEY_ALG_RSA_2048"}, "POST")
        content = base64.b64decode(key["privateKeyData"])
        parsed = json.loads(content)
        if parsed.get("client_email") != EMAIL or parsed.get("project_id") != PROJECT:
            raise SystemExit("Unexpected identity returned by IAM.")
        credential.write_bytes(content)
    print("Dedicated backend credential saved locally; no key material printed.")


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as error:
        print("Firebase IAM HTTP", error.code, file=sys.stderr)
        raise SystemExit(1)
