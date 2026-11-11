# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Turns the PoC's widget layout dump (PocWidgetDump.kt) into contracts/graph/widgets/poc.json:
python3 contract.py DUMP.json OUT.json (contracts/graph/README.md, "The PoC's widgets")."""
import json
import sys

dump = json.load(open(sys.argv[1]))
out_path = sys.argv[2]


def drawn(e):
    return '(no room)' not in e['i']


def compact(c):
    els = c['elements']

    def first(n):
        return next((e for e in els if e['n'] == n), None)

    v = first('value.number')
    t = [e for e in els if e['n'] == 'value.time' and drawn(e)]
    tr = first('value.trend')
    ch = first('chart')
    exp = {
        'value': v['i'] if v else None,
        'time': bool(t),
        'trend': tr['i'] if tr else None,
        'slots': [e['i'] for e in els if e['n'] == 'slot'],
        'chart': ch['i'] if ch else None,
        'pct': [e['i'] for e in els if e['n'] == 'chart.pct.label'],
        # what each time-in-range ring wrote in its centre: the PoC drew the number alone ('85', never '85%')
        'rings': [('–' if e['i'].split(' ')[3] == '-' else e['i'].split(' ')[3]) for e in els
                  if e['n'] == 'slot' and e['i'].startswith('TIR ')],
    }
    boxes = {}
    for n in ('value.number', 'slots', 'chart.plot'):
        e = first(n)
        if e and e['b']:
            boxes[n] = e['b']
    if boxes:
        exp['boxes'] = boxes
    return exp


cases = []
for c in dump:
    kind = c['surface']
    if c['settings'] == 'rings-off' and kind == 'widget-graph':
        continue
    if c['settings'] == 'three-end' and kind != 'widget-combined':
        continue
    cases.append({'surface': kind, 'card': c['card'], 'scene': c['scene'], 'settings': c['settings'],
                  'expect': compact(c)})

doc = {
    'about': [
        "The PoC's home-screen widgets as the reference look (#181: 'the widget was already looking good in the PoC, ensure",
        "all elements look exactly the same ... ensured deterministically'). Every case is one widget card the PoC's renderer",
        "(poc/spikes/showcase-android, GlucoseCard through its renderWidget, 10 dp margin) drew for a scene of",
        "../layout/scenes.json with a settings variant below, at 'card' (dp, the card inside the widget's margin; the",
        "Android and Mac widget sizes of ../surfaces/surfaces.json, margins off). Generated once from the frozen PoC with",
        "tools/graph/poc-widgets (../README.md, 'The PoC's widgets'); never edited by hand. expect: value (the number drawn, 'struck' when struck",
        "through; null = none), time (a time drawn in the value's place), trend (the arrow or the no-trend ring), slots",
        "(left to right as Slots.describe writes them: kind, window, range, the number in the ring without '%', [main]),",
        "chart (the window shown, null = no chart), pct (the % column's numbers), rings (exactly what each time-in-range ring",
        "writes in its centre, layout element slot.text: the number alone, no '%'), boxes (dp, card origin: the number, the",
        "slots' row, the plot). Android (PocWidgetsTest, :core:graph) must match every field and the boxes within",
        "'boxTolerance' dp (the same fonts); the Mac (PocWidgetsTests, GraphTests) every field but the boxes (SF Pro is",
        "narrower than Google Sans Flex). A deliberate change from the PoC is listed in 'differences' with its reason and",
        "decides those fields instead."
    ],
    'boxTolerance': 2,
    'settings': {
        'default': {},
        'rings-off': {'ringCount': 0},
        'three-end': {'ringCount': 3, 'lastPlace': 'END'},
    },
    'differences': [
        {'fields': ['value', 'trend', 'boxes'], 'scenes': ['ended-sending'],
         'reason': "#3: a sensor past its end whose readings keep arriving keeps its value (with 'Ended · Replace sensor'); the PoC showed 'Sensor ended' at once"},
    ],
    'cases': cases,
}
with open(out_path, 'w') as f:
    f.write('{\n')
    keys = list(doc.keys())
    for i, k in enumerate(keys):
        if k == 'cases':
            f.write(' "cases": [\n')
            for j, c in enumerate(cases):
                f.write('  ' + json.dumps(c, ensure_ascii=False, separators=(', ', ': ')) + (',' if j < len(cases) - 1 else '') + '\n')
            f.write(' ]')
        else:
            f.write(' ' + json.dumps(k) + ': ' + json.dumps(doc[k], ensure_ascii=False))
        f.write(',\n' if i < len(keys) - 1 else '\n')
    f.write('}\n')
print(len(cases), 'cases')
