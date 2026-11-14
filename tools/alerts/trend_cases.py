# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Writes contracts/alerts/trend.json: the cases of the trend both falling / rising quiets read from the readings
(contracts/alerts/SPEC.md §2 "Trend from the readings", #193), computed by this reference of the rule (the same sums in
the same order as Android `Trend` and the Mac's `Trend`). Python 3 stdlib only.

    python3 tools/alerts/trend_cases.py        write the file (and list each case's verdict)
"""
import json
import os

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")) + "/"
W, MINN, SPAN, GAP, AGE, SLOPE, NOISE, MOVING = 300, 4, 180, 150, 150, 0.5, 1, 90


def slope(w):
    """Least squares, mg/dL/min: time from the newest reading in s, sums oldest first, Σ(dt·dv) × 60 / Σdt²."""
    t0 = w[-1][0]
    n = float(len(w))
    st = 0.0
    sv = 0.0
    for t, v in w:
        st += float(t - t0)
        sv += float(v)
    mt = st / n
    mv = sv / n
    num = 0.0
    den = 0.0
    for t, v in w:
        dt = float(t - t0) - mt
        num += dt * (float(v) - mv)
        den += dt * dt
    return 0.0 if den == 0.0 else num * 60.0 / den


def clear(rs, now, up):
    """(slope, 'clear') of a clear move up / down in `rs` ([(t, mg/dL)] ascending) at `now`, else (None, why)."""
    if not rs:
        return None, 'no readings'
    nt = rs[-1][0]
    if now - nt > AGE:
        return None, 'the newest reading is too old'
    w = [r for r in rs if nt - r[0] < W and r[0] <= nt]
    if len(w) < MINN:
        return None, 'too few readings'
    if nt - w[0][0] < SPAN:
        return None, 'too short a span'
    if any(v < 40 or v > 400 for _, v in w):
        return None, 'a LO / HI'
    d = 1 if up else -1
    for a, b in zip(w, w[1:]):
        if b[0] - a[0] > GAP:
            return None, 'a gap'
        if d * (b[1] - a[1]) < -NOISE:
            return None, 'a step against the direction'
    for r in w[-2:]:
        if any(o[0] < r[0] - MOVING and d * (r[1] - o[1]) <= 0 for o in w):
            return None, 'not moving any more'
    s = slope(w)
    if d * s < SLOPE:
        return None, 'too slow'
    return s, 'clear'


def sec(s):
    h, m, x = map(int, s.split(':'))
    return 1_762_000_000 + h * 3600 + m * 60 + x  # a fixed day; only differences matter


EVENING = """20:08:18 57 · 20:09:17 54 · 20:10:18 54 · 20:11:19 54 · 20:12:18 54 · 20:13:20 55 · 20:14:20 55 · 20:15:19 55 · 20:16:21 54 · 20:17:21 54 · 20:18:20 54 · 20:19:21 54 · 20:23:22 54 · 20:24:24 53 · 20:25:24 54 · 20:26:22 55 · 20:27:24 57 · 20:28:24 57 · 20:29:23 58 · 20:30:25 59 · 20:31:25 59 · 20:32:25 61 · 20:33:25 63 · 20:34:26 66 · 20:35:26 69 · 20:36:26 72 · 20:37:27 75 · 20:38:28 76 · 20:39:27 76 · 20:40:27 83"""
ev = [(sec(p.split()[0]), int(p.split()[1])) for p in EVENING.split('·')]

cases = []


def case(name, readings, now, up=True):
    s, why = clear(readings, now, up)
    cases.append({"name": name, "readings": [[t, v] for t, v in readings], "now": now, "up": up,
                  "slope": s, "why": why})


# the owner's evening (#193): at each reading and when the 20:26 / 20:31 / 20:36 alerts came due
times = sorted([t for t, _ in ev] + [sec('20:26:03'), sec('20:31:03'), sec('20:36:03')])
for now in times:
    upto = [r for r in ev if r[0] <= now]
    keep = [r for r in upto if upto[-1][0] - r[0] < W]
    h = (now - 1_762_000_000)
    case(f"the owner's evening at {h // 3600}:{h % 3600 // 60:02d}:{h % 60:02d}", keep, now)

T = 1_762_100_000


def run(values, step=60, start=T):
    return [(start + i * step, v) for i, v in enumerate(values)]


r = run([60, 61, 62, 63, 64])
case("a steady rise of 1 a minute", r, r[-1][0])
case("the same readings judged as a fall", r, r[-1][0], up=False)
case("a steady fall of 1 a minute", run([200, 199, 198, 197, 196]), T + 240, up=False)
case("the newest reading exactly 150 s old", r, r[-1][0] + 150)
case("the newest reading 151 s old", r, r[-1][0] + 151)
case("4 readings spanning 3 min", run([60, 61, 62, 63]), T + 180)
case("3 readings", run([60, 62, 64]), T + 120)
case("4 readings spanning 2 min 59 s", [(T, 60), (T + 59, 61), (T + 119, 62), (T + 179, 63)], T + 179)
case("a reading exactly 5 min before the newest is outside the window",
     [(T, 40 + 1), (T + 60, 62), (T + 120, 63), (T + 180, 64), (T + 300, 66)], T + 300)
case("a gap of 150 s is one missed reading", [(T, 60), (T + 60, 61), (T + 210, 63), (T + 270, 64)], T + 270)
case("a gap of 151 s", [(T, 60), (T + 60, 61), (T + 211, 63), (T + 271, 64)], T + 271)
case("a LO in the window", run([39, 52, 56, 60, 64]), T + 240)
case("a HI in the window", run([401, 390, 380, 370, 360]), T + 240, up=False)
case("a step of 1 against the rise is noise", run([60, 62, 61, 63, 65]), T + 240)
case("a step of 2 against the rise", run([60, 63, 61, 64, 66]), T + 240)
case("noisy up and down", run([62, 57, 63, 58, 64]), T + 240)
case("a single jump", run([60, 60, 60, 65]), T + 180)
case("a rise that stalls: the first flat reading", run([58, 60, 62, 64, 64]), T + 240)
case("a rise that stalls: the second flat reading", run([60, 62, 64, 64, 64]), T + 240)
case("a slow drift up", run([60, 60, 61, 61, 61]), T + 240)
case("0.5 a minute exactly", run([56, 56, 57, 57, 58]), T + 240)
case("a slow rise from frequent readings (every 50 s): too slow", run([60, 60, 60, 60, 61, 62], step=50), T + 250)
case("a clock-shifted window (readings an hour back, now too)", [(t - 3600, v) for t, v in run([60, 61, 62, 63, 64])],
     T + 240 - 3600)
case("no readings", [], T)

doc = {
    "_about": [
        "The trend both falling / rising quiets read from the readings (contracts/alerts/SPEC.md §2 'Trend from the",
        "readings', #193). Each case: the readings ([epoch s, mg/dL], oldest first, as the engine keeps them), now, the",
        "direction (up = rising), and the expected least-squares slope in mg/dL/min of a clear move, or null with why.",
        "Run by Android TrendTest and Mac TrendTests (to 1e-9). Written by python3 tools/alerts/trend_cases.py (a reference",
        "of the rule): a change to the rule changes it there and in both apps.",
    ],
    "cases": cases,
}
p = ROOT + 'contracts/alerts/trend.json'
with open(p, 'w', encoding='utf-8') as f:
    about = ",\n    ".join(json.dumps(x, ensure_ascii=False) for x in doc["_about"])
    rows = ",\n    ".join(json.dumps(c, ensure_ascii=False) for c in cases)
    f.write('{\n  "_about": [\n    ' + about + '\n  ],\n  "cases": [\n    ' + rows + '\n  ]\n}\n')
json.load(open(p, encoding='utf-8'))
for c in cases:
    print(c['name'], '→', c['slope'], c['why'])
