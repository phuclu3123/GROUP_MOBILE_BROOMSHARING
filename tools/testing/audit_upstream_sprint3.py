"""Read-only comparison of the fetched Guest sprint against the integrated app."""
import hashlib
import json
import pathlib
import subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'docs/testing/evidence/upstream-truc-sprint3-2026-10-25.json'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode('utf-8')


def main():
    upstream = git('rev-parse', 'origin/main').strip()
    sprint = '693688e7c1f62e4b1868a86ff3dfdb2043e38c9e'
    assert git('merge-base', '--is-ancestor', sprint, upstream) == ''
    files = git('diff', '--name-only', sprint + '^', sprint).splitlines()
    items = []
    for path in files:
        data = subprocess.check_output(['git', 'show', upstream + ':' + path], cwd=ROOT)
        record = {'path': path, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)}
        if path.endswith('.xml'):
            try:
                ET.fromstring(data)
                record['xmlParse'] = 'passed'
            except ET.ParseError as error:
                record['xmlParse'] = str(error)
        items.append(record)
    base = 'app/src/main/java/vn/edu/ueh/phuclu/broomsharing/'
    sources = {name: git('show', upstream + ':' + base + name + '.java')
               for name in ['MessagesActivity', 'ChatActivity', 'NotificationActivity', 'BookingSuccessActivity', 'MapActivity']}
    findings = {
        'inboxUsesParticipantUids': '"participantUids"' in sources['MessagesActivity'],
        'inboxUsesLastMessage': '"lastMessage"' in sources['MessagesActivity'],
        'messageWriteHasNoSequenceField': 'message.put("sequence"' not in sources['ChatActivity'],
        'messageAndPreviewAreSeparateWrites': '.set(message)' in sources['ChatActivity'] and '.update(' in sources['ChatActivity'],
        'notificationReadsSeparateCollection': 'collection("notifications")' in sources['NotificationActivity'],
        'notificationWritesReadFlag': '"read", true' in sources['NotificationActivity'],
        'confirmationReadsAmountsFromIntent': 'getDoubleExtra("totalAmount"' in sources['BookingSuccessActivity'],
        'calendarDescriptionHardcodesWaitingPayment': 'Trạng thái: Chờ thanh toán' in sources['BookingSuccessActivity'],
        'mapScreenHasNoNativeMapWidget': all(x not in sources['MapActivity'] for x in ['GoogleMap', 'MapView', 'SupportMapFragment']),
    }
    result = {'reviewDate': '2026-10-25', 'localHead': git('rev-parse', 'HEAD').strip(),
              'upstreamMain': upstream, 'sprintCommit': sprint,
              'commonAncestor': git('merge-base', 'HEAD', upstream).strip(),
              'upstreamOnlyCommits': git('log', '--right-only', '--format=%h %s', 'HEAD...' + upstream).splitlines(),
              'files': items, 'staticFindings': findings,
              'scope': 'Source and XML parser review; fetched revision not installed or accepted as a working app',
              'decision': 'Keep integrated Repository/Rules/MapLibre implementation; do not copy incompatible old classes/schema or unused SDK dependencies'}
    host_commit = 'c74d963506d6b1c0020723ca1b6b5dd2e26f35ee'
    host_files = []
    for path in git('diff', '--name-only', host_commit + '^', host_commit).splitlines():
        data = subprocess.check_output(['git', 'show', host_commit + ':' + path], cwd=ROOT)
        entry = {'path': path, 'sha256': hashlib.sha256(data).hexdigest()}
        if path.endswith('.xml'):
            try:
                ET.fromstring(data)
                entry['xmlParse'] = 'passed'
            except ET.ParseError as error:
                entry['xmlParse'] = str(error)
        host_files.append(entry)
    host_repository = git('show', host_commit + ':' + base + 'repository/HostRepository.java')
    host_calendar = git('show', host_commit + ':' + base + 'ui/host/HostBookingsActivity.java')
    host_report = host_repository.split('public void submitHostReportResponse', 1)[1]
    current_admin = (ROOT / (base + 'repository/AdminRepository.java')).read_text(encoding='utf-8')
    result['hostBranchReview'] = {
        'commit': host_commit, 'files': host_files,
        'usefulCandidates': ['Monthly calendar grid', 'Property verification form and submitted list', 'Host report card layout'],
        'staticFindings': {
            'verificationWritesEvidencePaths': 'data.put("evidencePaths"' in host_repository,
            'currentAdminReadsEvidenceRefsInstead': 'doc.get("evidenceRefs")' in current_admin and 'doc.get("evidencePaths")' not in current_admin,
            'calendarEndDateInclusive': '!date.isAfter(e)' in host_calendar,
            'calendarHasNoExpiredHandling': 'EXPIRED' not in host_calendar,
            'hostReplyDoesNotWriteRespondedAt': 'hostRespondedAt' not in host_report,
            'verificationStillUsesFirebaseStorage': 'verification/' in host_repository and 'getInstance()' in host_repository,
        },
        'decision': 'Candidate UI must be adapted to current contracts and third-party media plan before integration; do not replace tested booking/Rules wholesale'}
    OUTPUT.write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding='utf-8')
    print(json.dumps({'filesReviewed': len(items), 'xmlParseErrors': [i['path'] for i in items if i.get('xmlParse', 'passed') != 'passed'],
                      'staticFindings': findings}, ensure_ascii=False))


if __name__ == '__main__':
    main()
