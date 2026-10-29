"""Update the user-authorized local draw.io MCP document, then read edges back.

Only replaces the five known project pages or same-title generated appendix pages.
Unrelated pages are retained. Requires the existing local MCP bridge on port 4317.
"""
import json
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'docs/design/drawio'
DOCUMENT = '31c24cc6-7010-4629-9913-82c76e093309'
MAIN_IDS = {
    '00-relationships-overview': 'N41ylznPhZ87A-rPrKVu',
    'broomsharing-accounts': '_zuNtEPcwCrxTB9iPgxX',
    '02-booking': 'B9VDVWD0aiZ8Sz0Bb9O7',
    '03-chat': '7DYU1Rszeyx7WCkdU3cw',
    '04-administration-reference': '2ntjwlpEiRYSW8ZyPiYj',
}


def rpc(name, args):
    payload = {'jsonrpc':'2.0','id':1,'method':'tools/call','params':{'name':name,'arguments':args}}
    request = urllib.request.Request('http://127.0.0.1:4317/mcp', data=json.dumps(payload).encode(),
                                    headers={'Content-Type':'application/json','Accept':'application/json, text/event-stream'})
    raw = urllib.request.urlopen(request, timeout=45).read().decode()
    message = json.loads(next(line[6:] for line in raw.splitlines() if line.startswith('data: ')))
    if 'error' in message: raise RuntimeError(message['error'])
    if message['result'].get('isError'): raise RuntimeError(message['result'])
    text = next(c['text'] for c in message['result']['content'] if c['type']=='text')
    if text.lstrip().startswith('<?xml') or text.lstrip().startswith('<svg'):
        return text
    result = json.loads(text)
    if not result.get('success'): raise RuntimeError(result)
    return result['result']


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    target = {'target_document':{'id':DOCUMENT}}
    current = rpc('list-pages', target)
    known = {p['id'] for p in current}
    if not set(MAIN_IDS.values()) <= known: raise RuntimeError('Known project pages changed; inspect before replacing.')
    manifest = json.loads((ROOT/'remaining-pages-manifest.json').read_text(encoding='utf-8'))
    pages = manifest['pages'] + [p for p in manifest.get('appendixPages',[])
                                  if p['slug'].startswith('relations-') or p['slug']=='storage-and-queries']
    verified = []
    for p in pages:
        xml = (ROOT/(p['slug']+'-model.xml')).read_text(encoding='utf-8')
        model = ET.fromstring(xml)
        page_id = MAIN_IDS.get(p['slug'])
        if page_id is None:
            match = next((q for q in current if q['name']==p['title']),None)
            if match:page_id=match['id']
        if page_id:
            rpc('import-diagram',dict(target,target_page={'id':page_id},data=xml,format='xml',mode='replace'))
        else:
            before={q['id'] for q in current}
            rpc('import-diagram',dict(target,data=xml,format='xml',mode='new-page'))
            current=rpc('list-pages',target)
            added=[q for q in current if q['id'] not in before]
            if len(added)!=1:raise RuntimeError('Unexpected new-page result')
            page_id=added[0]['id']
        rpc('rename-page',dict(target,page={'id':page_id},name=p['title']))
        edges = rpc('list-paged-model',dict(target,target_page={'id':page_id},page=0,page_size=1000,filter={'cell_type':'edge'}))
        expected = {c.get('id'):(c.get('source'),c.get('target'),'dashed=1' in c.get('style',''))
                    for c in model.findall('root/mxCell') if c.get('edge')=='1'}
        actual = {c['id']:(c['source']['id'],c['target']['id'],'dashed=1' in c.get('style','')) for c in edges}
        if actual != expected: raise RuntimeError('Live edge mismatch: '+p['slug'])
        verified.append({'slug':p['slug'],'id':page_id,'name':p['title'],'edgesVerified':len(actual),
                         'dashedEdgesVerified':sum(v[2] for v in actual.values())})
        print('Updated and read back:',p['slug'],len(actual),'edges',flush=True)
        current=rpc('list-pages',target)
    (ROOT/'drawio-connected-pages.json').write_text(json.dumps({'success':True,'result':current},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (ROOT/'live-diagram-verification.json').write_text(json.dumps({'document':DOCUMENT,'verifiedPages':verified,
        'scope':'MCP import followed by endpoint and dash-style readback; not screenshot or cloud verification.'},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':main()
