"""Read-only source comparison; writes schema names/counts, never document values.

This is a static audit aid, not a Java parser, Rules evaluator or cloud scan.
Review dynamic writes and property serialization separately.
"""
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.stdout.reconfigure(encoding='utf-8')

BASE = Path(__file__).resolve().parents[2]
DESIGN = BASE / 'docs/design'
JAVA = BASE / 'app/src/main/java/vn/edu/ueh/phuclu/broomsharing'
schema = json.loads((DESIGN / 'firestore-visualization-schema.json').read_text(encoding='utf-8'))
docs = {d['path']: d for d in schema['documents']}
model_paths = {
    'UserProfile': 'users/{uid}', 'PublicHost': 'publicHosts/{uid}',
    'HostApplication': 'hostApplications/{uid}', 'Building': 'buildings/{buildingId}',
    'Room': 'rooms/{roomId}', 'Booking': 'bookings/{bookingId}',
    'AvailabilityMonth': 'availabilityMonths/{roomId_yyyyMM}',
    'Conversation': 'conversations/{guestUid:hostUid}',
    'ChatMessage': 'conversations/{guestUid:hostUid}/messages/{messageId}',
    'Favorite': 'users/{uid}/favorites/{roomId}', 'RoomReport': 'reports/{reportId}',
    'AuditLog': 'auditLogs/{auditId}', 'RentalPlace': 'rentalPlaces/{placeId}',
}
model_checks = []
for name, path in model_paths.items():
    text = (JAVA / f'model/{name}.java').read_text(encoding='utf-8')
    text = re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)
    properties = set()
    for line in text.splitlines():
        if '@Exclude' in line:
            continue
        match = re.search(r'\bpublic\s+(String|long|int|double|boolean|Date|GeoPoint|List<[^>]+>|Map<.+>)\s+([^;{}]+);', line)
        if match and '(' not in match[2].split('=', 1)[0]:
            for declaration in re.split(r',\s*(?=\w+\s*(?:=|,|$))', match[2]):
                prop = re.match(r'\s*(\w+)', declaration)
                if prop:
                    properties.add(prop[1])
    for getter in re.finditer(r'public\s+[\w<>?, ]+\s+get([A-Z]\w*)\s*\(\s*\)', text):
        if '@Exclude' not in text[max(0, getter.start()-30):getter.start()]:
            properties.add(getter[1][0].lower() + getter[1][1:])
    known = {f['name'] for f in docs[path]['fields'] if '.' not in f['name']}
    model_checks.append({'model': name, 'path': path, 'properties': sorted(properties),
                         'missingFromInventory': sorted(properties-known)})

dataset = json.loads((BASE / 'data/demo/firestore-dataset.json').read_text(encoding='utf-8'))
dataset_checks = {}
for document in dataset['documents']:
    parts = document['path'].split('/')
    candidates = [p for p in docs if len(p.split('/')) == len(parts)
                  and p.split('/')[::2] == parts[::2]]
    if len(candidates) != 1:
        raise ValueError('Unrecognized dataset document pattern')
    path = candidates[0]
    dataset_checks.setdefault(path, set()).update(document['fields'])
dataset_results = [{'path': path, 'topLevelFieldNames': sorted(fields),
                    'missingFromInventory': sorted(fields - {f['name'] for f in docs[path]['fields']})}
                   for path, fields in sorted(dataset_checks.items())]
reference_data = json.loads((BASE / 'data/reference/rental-places-hcm.json').read_text(encoding='utf-8'))
reference_fields = set().union(*(set(row) for row in reference_data))
references = sorted(set(re.findall(r'\.collection\(\s*"([^"]+)"',
    '\n'.join(p.read_text(encoding='utf-8') for p in JAVA.rglob('*.java')))))

workbook = DESIGN / 'drawio/broomsharing-data-design.drawio'
pages = []
native_field_checks = []
for diagram in ET.parse(workbook).getroot().findall('diagram'):
    model = diagram.find('mxGraphModel')
    cells = model.findall('root/mxCell')
    sizes = [int(re.search(r'fontSize=(\d+)',c.get('style'))[1]) for c in cells
             if c.get('style','').startswith('text;')]
    pages.append({'name': diagram.get('name'), 'pageWidth': float(model.get('pageWidth')),
                  'pageHeight': float(model.get('pageHeight')),
                  'edges': [{'source': c.get('source'), 'target': c.get('target'),
                             'kind': c.get('relationshipKind'),
                             'dashed': 'dashed=1' in c.get('style', '')}
                            for c in cells if c.get('edge') == '1'],
                  'minimumFontPointsAt160mm': round(min(sizes) * 160 / 25.4 * 72 / float(model.get('pageWidth')), 2)})
all_fields = {}
for diagram in ET.parse(workbook).getroot().findall('diagram'):
    for c in diagram.findall('mxGraphModel/root/mxCell'):
        if c.get('fieldPath'):
            all_fields.setdefault(c.get('schemaPath'), []).append(c)
for path,doc in docs.items():
    actual=all_fields.get(path,[])
    expected={f['name']:f['type'] for f in doc['fields']}
    fields={c.get('fieldPath'):c.get('schemaType') for c in actual}
    assert len(actual)==len(fields)
    native_field_checks.append({'path':path,'fieldEntries':len(fields),
        'missingFromNative':sorted(set(expected)-set(fields)),
        'unexpectedNativeFields':sorted(set(fields)-set(expected)),
        'typeMismatches':[k for k in fields if k in expected and fields[k]!=expected[k]]})

# Check all collection -> document and document -> subcollection endpoints in the overview.
overview = ET.parse(workbook).getroot().find('diagram/mxGraphModel/root')
endpoints = {(c.get('source'),c.get('target')) for c in overview if c.get('edge')=='1'}
for path in docs:
    parts=path.split('/');name=parts[-2]
    assert ('c-'+name,'d-'+name) in endpoints
    owner='database' if len(parts)==2 else 'd-'+parts[-4]
    assert (owner,'c-'+name) in endpoints

evidence = {
    'reviewDate': '2026-10-23', 'scope': 'Current local checkout; no cloud reads; no source mutations',
    'limitations': ['Regex property extraction needs manual review; not a Java compiler.',
                    'Model/dataset checks compare names, not full type or runtime validation.',
                    'Drawio tooltips are inventory-generated, not independent source evidence.',
                    '160mm estimate fits full page width, including margins; actual cropped placement can differ.'],
    'javaCollectionNames': references, 'modelChecks': model_checks,
    'datasetDocumentCount': len(dataset['documents']), 'datasetChecks': dataset_results,
    'referenceRecordCount': len(reference_data),
    'referenceFieldNames': sorted(reference_fields),
    'referenceFieldsAbsentFromRentalPlaceContract': sorted(reference_fields -
        {f['name'] for f in docs['rentalPlaces/{placeId}']['fields']}),
    'inventoryDocumentPatterns': len(docs),
    'inventoryFieldEntriesIncludingNested': sum(len(d['fields']) for d in docs.values()),
    'pages': pages, 'nativeFieldChecksAgainstInventory': native_field_checks,
    'completeRelationshipsInMarkdown':len(schema['relationships']),
    'logicalConnectorCoverage':'Not complete in classic primary diagrams; full specifications remain in Markdown.',
    'sha256': {str(p.relative_to(BASE)): hashlib.sha256(p.read_bytes()).hexdigest()
               for p in [workbook, DESIGN / 'firestore-visualization-schema.json',
                         BASE / 'firestore.rules', BASE / 'storage.rules', BASE / 'firebase.json',
                         BASE / 'data/demo/firestore-dataset.json', BASE / 'data/reference/rental-places-hcm.json',
                         *sorted((JAVA / 'model').glob('*.java')),
                         *sorted((JAVA / 'repository').glob('*.java')),
                         JAVA / 'utils/BookingPolicy.java', JAVA / 'utils/CancellationPolicy.java']},
}
out = DESIGN / 'drawio/diagram-audit-evidence.json'
out.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'pages': len(pages), 'patterns': len(docs),
                  'modelMissing': [m for m in model_checks if m['missingFromInventory']],
                  'datasetMissing': [d for d in dataset_results if d['missingFromInventory']],
                  'javaCollections': references,
                  'fontAt160mm': {p['name']: p['minimumFontPointsAt160mm'] for p in pages}}, ensure_ascii=False))
