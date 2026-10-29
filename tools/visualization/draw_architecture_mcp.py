"""Build native, editable architecture shapes through the connected draw.io MCP.

Uses add-rectangle / add-edge; no Mermaid, generated SVG or XML import.
Only the new architecture page is written. Other project pages are fingerprinted.
"""
import hashlib
import json
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'tmp/architecture'
WORK.mkdir(parents=True, exist_ok=True)
STATE = WORK / 'native-state.json'
DOCUMENT = '31c24cc6-7010-4629-9913-82c76e093309'


def rpc(name, arguments):
    payload = {'jsonrpc': '2.0', 'id': 1, 'method': 'tools/call',
               'params': {'name': name, 'arguments': arguments}}
    request = urllib.request.Request('http://127.0.0.1:4317/mcp',
        data=json.dumps(payload).encode(), headers={
            'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream'})
    raw = urllib.request.urlopen(request, timeout=40).read().decode()
    envelope = json.loads(next((line[6:] for line in raw.splitlines() if line.startswith('data: ')), raw))
    if 'error' in envelope:
        raise RuntimeError(envelope['error'])
    result = envelope['result']
    if result.get('isError'):
        raise RuntimeError(result)
    text = next(c['text'] for c in result['content'] if c['type'] == 'text')
    if text.lstrip().startswith('<'):
        return text
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return text
    if isinstance(data, dict) and 'success' in data:
        if not data['success']:
            raise RuntimeError(data)
        return data['result']
    return data


class Canvas:
    def __init__(self):
        self.target = {'target_document': {'id': DOCUMENT}}
        if STATE.exists():
            self.state = json.loads(STATE.read_text(encoding='utf-8'))
        else:
            pages = rpc('list-pages', self.target)
            baseline = {}
            for page in pages[:5]:
                data = rpc('list-paged-model', dict(self.target, target_page={'id': page['id']}, page_size=1000))
                baseline[page['id']] = hashlib.sha256(json.dumps(data, sort_keys=True).encode()).hexdigest()
            page = rpc('create-page', dict(self.target, name='05 · KIẾN TRÚC ỨNG DỤNG'))
            self.state = {'document': DOCUMENT, 'page': page, 'cells': {}, 'specs': {}, 'previousPages': baseline}
            self.save()
        page = self.state['page']
        page_id = page['id'] if isinstance(page, dict) else page
        self.target['target_page'] = {'id': page_id}

    def save(self):
        STATE.write_text(json.dumps(self.state, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    def box(self, key, x, y, width, height, text='', fill='#ffffff', stroke='#aeb6bf',
            size=20, parent=None, extra='', bold=False, align='center'):
        style = (f'rounded=1;arcSize=10;whiteSpace=wrap;html=0;fillColor={fill};'
                 f'strokeColor={stroke};strokeWidth=1.3;fontFamily=Times New Roman;'
                 f'fontSize={size};fontColor=#20252b;fontStyle={1 if bold else 0};'
                 f'align={align};verticalAlign=middle;spacing=8;shadow=0;{extra}')
        # The bridge substitutes its default position for numeric zero on insert.
        # A visually identical nonzero coordinate preserves nested card bounds.
        args = dict(self.target, x=x if x else 0.01, y=y if y else 0.01,
                    width=width, height=height, text=text, style=style)
        if parent:
            args['parent_id'] = self.state['cells'][parent]
        self.state['specs'][key] = args
        if key in self.state['cells']:
            args['cell_id'] = self.state['cells'][key]
            args.pop('parent_id', None)
            result = rpc('edit-cell', args)
        else:
            result = rpc('add-rectangle', args)
            self.state['cells'][key] = result['id'] if isinstance(result, dict) else result
        self.save()
        return result

    def label(self, key, x, y, width, height, text, size=20, parent=None, bold=False, color='#20252b', align='center'):
        return self.box(key, x, y, width, height, text, fill='none', stroke='none', size=size,
            parent=parent, bold=bold, align=align,
            extra=f'rounded=0;spacing=0;resizable=1;rotatable=0;fontColor={color};')

    def edge(self, key, source, target, text='', extra='', points=None, dashed=False, color='#727d88'):
        style = ('edgeStyle=orthogonalEdgeStyle;rounded=0;html=0;endArrow=block;endFill=1;'
                 'strokeWidth=1.6;fontFamily=Times New Roman;fontSize=18;fontColor=#4b5560;'
                 f'strokeColor={color};dashed={1 if dashed else 0};'
                 'labelBackgroundColor=#ffffff;spacing=4;'+extra)
        args = dict(self.target, source_id=self.state['cells'][source],
                    target_id=self.state['cells'][target], text=text, style=style)
        if points is not None:
            args['points'] = [{'x': x, 'y': y} for x, y in points]
        self.state['specs'][key] = args
        if key in self.state['cells']:
            result = rpc('edit-edge', dict(args, cell_id=self.state['cells'][key]))
        else:
            result = rpc('add-edge', args)
            self.state['cells'][key] = result['id'] if isinstance(result, dict) else result
        self.save()
        return result


def reset_owned_page(c):
    if c.state['page']['id'] != '_ZJgtB_Fn-RRx8Ujbytb':
        raise RuntimeError('Unexpected target page; refusing to clear.')
    cells = rpc('list-paged-model', dict(c.target, page_size=1000))
    roots = [cell for cell in cells if (cell.get('parent') or {}).get('id') == '1']
    for cell in sorted(roots, key=lambda item: not item.get('edge', False)):
        rpc('delete-cell-by-id', dict(c.target, cell_id=cell['id']))
    c.state['cells'] = {}
    c.state['specs'] = {}
    c.save()


def icon(c, key, name, x, y, width, height, parent=None):
    from urllib.parse import quote
    path = ROOT / 'docs/design/drawio/assets/architecture' / (name + '.svg')
    image = 'data:image/svg+xml,' + quote(path.read_text(encoding='utf-8'), safe='')
    c.box(key, x, y, width, height, fill='none', stroke='none', parent=parent,
          extra='shape=image;imageAspect=0;aspect=fixed;spacing=0;image=' + image + ';')


def core(c):
    c.box('app', 130, 90, 900, 485, fill='#f4f5f6', stroke='none', extra='arcSize=8;')
    c.label('app-title', 40, 20, 820, 40, 'ỨNG DỤNG ANDROID', 25, parent='app', align='left')
    for key, x, fill in [('view',40,'#e9f0fb'),('vm',340,'#edf4e8'),('repo',640,'#fff1df')]:
        c.box(key, x, 90, 210, 275, fill='none', stroke='none', parent='app')
        c.box(key+'-surface', 0, 0, 210, 225, fill=fill, stroke='none', parent=key, extra='arcSize=12;')
    icon(c,'view-icon','phone',60,26,90,90,'view')
    icon(c,'vm-icon','code-block',60,26,90,90,'vm')
    icon(c,'repo-icon','toolbox',60,26,90,90,'repo')
    c.label('view-title', 5, 143, 200, 38, 'View', 27, parent='view')
    c.label('vm-title', 5, 143, 200, 38, 'ViewModel', 27, parent='vm')
    c.label('repo-title', 5, 143, 200, 38, 'Repository', 27, parent='repo')
    c.label('view-role', 0, 239, 210, 30, 'Activity · XML', 22, parent='view')
    c.label('vm-role', 0, 239, 210, 30, 'Trạng thái · LiveData', 20, parent='vm')
    c.label('repo-role', 0, 239, 210, 30, 'Truy cập dữ liệu', 21, parent='repo')
    icon(c,'actor','people',20,220,78,78)
    c.label('actor-caption', 0, 310, 120, 32, 'Người dùng', 21)
    c.edge('user-action','actor','view',extra='exitX=1;exitY=0.7;entryX=0;entryY=0.35;')
    labels='labelBackgroundColor=#f4f5f6;verticalAlign=bottom;spacingBottom=7;fontSize=17;'
    c.edge('view-command','view','vm','Sự kiện',extra='exitX=1;exitY=0.24;entryX=0;entryY=0.24;'+labels)
    c.edge('vm-state','vm','view','LiveData',extra='exitX=0;exitY=0.62;entryX=1;entryY=0.62;'+labels)
    c.edge('vm-request','vm','repo','Yêu cầu',extra='exitX=1;exitY=0.24;entryX=0;entryY=0.24;'+labels)
    c.edge('repo-callback','repo','vm','Kết quả',extra='exitX=0;exitY=0.62;entryX=1;entryY=0.62;'+labels)
    c.box('models',40,390,810,75,fill='#ebe8f2',stroke='none',parent='app')
    icon(c,'models-icon','document-text',24,13,49,49,'models')
    c.label('models-title',89,17,130,38,'Model',25,parent='models',align='left')
    c.label('models-description',260,21,520,32,'Đối tượng nghiệp vụ',22,parent='models',align='left')
    for key, at in [('view',105/810),('vm',405/810),('repo',705/810)]:
        c.edge(key+'-model',key,'models',color='#b8aec9',extra=f'exitX=0.5;exitY=1;entryX={at};entryY=0;endArrow=none;strokeWidth=1.2;')
    print('Built overview: three MVVM responsibilities and shared Model.',flush=True)


def services(c):
    c.box('firebase',1110,130,370,200,fill='#edf0f8',stroke='none',extra='arcSize=10;')
    c.label('firebase-title',15,9,340,36,'Firebase',26,parent='firebase')
    icon(c,'auth-icon','person-key',55,65,70,70,'firebase')
    icon(c,'firestore-icon','database',245,65,70,70,'firebase')
    c.label('auth-title',5,150,170,32,'Xác thực',21,parent='firebase')
    c.label('firestore-title',195,150,170,32,'Firestore',21,parent='firebase')
    c.box('media',1110,420,165,155,fill='#fff2df',stroke='none',extra='arcSize=12;')
    icon(c,'media-icon','image',49,16,67,67,'media')
    c.label('media-title',5,103,155,32,'Lưu trữ ảnh',21,parent='media')
    c.box('payment',1315,420,165,155,fill='#f6f6f6',stroke='#a6abb2',extra='dashed=1;dashPattern=6 5;')
    icon(c,'payment-icon','receipt',49,12,67,67,'payment')
    c.label('payment-title',5,93,155,32,'Thanh toán',21,parent='payment')
    c.label('payment-plan',5,126,155,23,'Dự kiến',18,parent='payment',color='#9297a0')
    c.box('maps',1110,640,370,110,fill='#edf4f4',stroke='none',extra='arcSize=12;')
    icon(c,'maps-icon','globe',21,22,66,66,'maps')
    c.label('maps-title',108,18,242,34,'Dịch vụ bản đồ',23,parent='maps',align='left')
    c.label('maps-role',108,61,242,28,'Vị trí · tìm đường',20,parent='maps',align='left')
    c.box('bus',1048,278,4,4,fill='#7b848e',stroke='none',extra='shape=ellipse;')
    c.edge('repo-service-bus','repo','bus',extra='exitX=1;exitY=0.364;entryX=0;entryY=0.5;endArrow=none;')
    c.edge('bus-firebase','bus','firebase',extra='exitX=0.5;exitY=0;entryX=0;entryY=0.5;startArrow=none;',points=[(1050,230)])
    c.edge('bus-media','bus','media',extra='exitX=0.5;exitY=1;entryX=0;entryY=0.5;',points=[(1050,497.5)])
    c.edge('bus-maps','bus','maps',extra='exitX=0.5;exitY=1;entryX=0;entryY=0.5;',points=[(1050,695)])
    c.edge('bus-payment','bus','payment',dashed=True,color='#a6abb2',extra='exitX=0.5;exitY=1;entryX=0.5;entryY=0;endArrow=open;endFill=0;',points=[(1050,380),(1397.5,380)])
    print('Built provider-neutral service groups and the planned payment branch.',flush=True)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    canvas = Canvas()
    if canvas.state.get('liveEdited') and '--reset' not in sys.argv:
        raise SystemExit('Live user edits detected. Export the current page; do not rebuild it from old specs.')
    if '--reset' in sys.argv:
        reset_owned_page(canvas)
    core(canvas)
    services(canvas)
    print(json.dumps({'page': canvas.state['page'], 'nativeCells': len(canvas.state['cells'])}, ensure_ascii=False))
