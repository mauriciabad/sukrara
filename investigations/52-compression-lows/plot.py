# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Example nights as one SVG (standard library only): python3 evaluate.py --svg docs/img/33"""

import os
import random
from typing import List

import detector
import traces

W, PANEL_H, LEFT, RIGHT, TOP = 760, 190, 46, 14, 28
Y_MIN, Y_MAX = 30, 230
INK, MUTED, GRID, LOW_C, TRUE_C, LABEL_C, SUSPECT_C = "#1f2328", "#59636e", "#d8dee4", "#cf222e", "#8c959f", "#1a7f37", "#9a6700"


def _x(t: float, t0: float, t1: float) -> float:
    return LEFT + (t - t0) / (t1 - t0) * (W - LEFT - RIGHT)


def _y(v: float, top: float) -> float:
    v = max(Y_MIN, min(Y_MAX, v))
    return top + TOP + (Y_MAX - v) / (Y_MAX - Y_MIN) * (PANEL_H - TOP - 22)


def _panel(night: traces.Night, title: str, top: float, t0: float, t1: float) -> List[str]:
    outs = detector.run(night.readings, night.treatments)
    s = ['<text x="%d" y="%.0f" font-size="13" font-weight="600" fill="%s">%s</text>' % (LEFT, top + 16, INK, title)]
    for v in (40, 70, 100, 150, 200):
        y = _y(v, top)
        s.append('<line x1="%d" x2="%d" y1="%.1f" y2="%.1f" stroke="%s" stroke-width="%s"/>'
                 % (LEFT, W - RIGHT, y, y, LOW_C if v == 70 else GRID, "1.2" if v == 70 else "0.6"))
        s.append('<text x="%d" y="%.1f" font-size="10" text-anchor="end" fill="%s">%d</text>' % (LEFT - 5, y + 3, MUTED, v))
    for t in range(int(t0) - int(t0) % 30 + 30, int(t1), 30):
        hh = (23 + t // 60) % 24
        s.append('<text x="%.1f" y="%.0f" font-size="10" text-anchor="middle" fill="%s">%02d:%02d</text>'
                 % (_x(t, t0, t1), top + PANEL_H - 6, MUTED, hh, t % 60))
    for o in outs:
        if o.kind == "compression" and o.t >= t0 and o.start <= t1:
            x0, x1 = _x(max(t0, o.start), t0, t1), _x(min(t1, o.t), t0, t1)
            s.append('<rect x="%.1f" y="%.0f" width="%.1f" height="%d" fill="%s" opacity="0.12"/>'
                     % (x0, top + TOP, x1 - x0, PANEL_H - TOP - 22, LABEL_C))
            s.append('<text x="%.1f" y="%.0f" font-size="10" fill="%s">labelled: possible compression</text>'
                     % (x0 + 3, top + TOP + 11, LABEL_C))
    pts = " ".join("%.1f,%.1f" % (_x(t, t0, t1), _y(v, top)) for t, v in enumerate(night.true) if t0 <= t <= t1)
    s.append('<polyline points="%s" fill="none" stroke="%s" stroke-width="1.2" stroke-dasharray="4 3"/>' % (pts, TRUE_C))
    seg: List[str] = []
    for t, v in night.readings:
        if not (t0 <= t <= t1):
            continue
        if v is None:
            if seg:
                s.append('<polyline points="%s" fill="none" stroke="%s" stroke-width="1.8"/>' % (" ".join(seg), INK))
            seg = []
            continue
        seg.append("%.1f,%.1f" % (_x(t, t0, t1), _y(v, top)))
    if seg:
        s.append('<polyline points="%s" fill="none" stroke="%s" stroke-width="1.8"/>' % (" ".join(seg), INK))
    for o in outs:
        if o.kind == "suspect" and t0 <= o.t <= t1:
            x, y = _x(o.t, t0, t1), _y(o.nadir, top)
            s.append('<path d="M%.1f %.1f l-5 9 h10 z" fill="%s"/>' % (x, y + 4, SUSPECT_C))
    for tt, kind, amount in night.treatments:
        if t0 <= tt <= t1:
            x = _x(tt, t0, t1)
            s.append('<text x="%.1f" y="%.0f" font-size="10" text-anchor="middle" fill="%s">%s %g%s</text>'
                     % (x, _y(Y_MIN, top) - 4, MUTED, kind, amount, " g" if kind == "carbs" else " U"))
    return s


def _find(kind: str, want, seed: int, step: int = 1) -> traces.Night:
    rng = random.Random(seed)
    for _ in range(500):
        night = traces.make_night(rng, kind, step)
        if want(night):
            return night
    raise RuntimeError("no example for " + kind)


def examples(out_dir: str) -> None:
    os.makedirs(out_dir, exist_ok=True)

    def alerted(n):
        return any(v is not None and v < 70 for _, v in n.readings)

    def labelled(n):
        return any(o.kind == "compression" for o in detector.run(n.readings, n.treatments))

    def treated(how):
        return lambda n: n.events[0].detail["treated"] == how

    panels = [
        (_find("compression", lambda n: alerted(n) and labelled(n) and n.events[0].detail["woke"], 3),
         "Pressure on the sensor: the alarm wakes them, they turn, the reading comes back. Labelled after the rebound"),
        (_find("true_low_fast", lambda n: treated("unlogged")(n) and not labelled(n), 5),
         "A real fast low, carbs eaten but not logged: a V too, but it stays low 15+ min after the alarm. Not labelled"),
        (_find("compression_on_low", lambda n: alerted(n) and not labelled(n), 11),
         "Pressure on top of a real low: the dip comes back only to a low value. Not labelled"),
        (_find("true_low_fast", lambda n: treated("early_unlogged")(n) and labelled(n), 13),
         "What still fools it: awake, ate as it fell, didn't log it. Wrongly labelled, so Sukrara asks, never states"),
    ]
    parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d" '
             'font-family="-apple-system, Segoe UI, Roboto, sans-serif">' % (W, PANEL_H * len(panels) + 30, W, PANEL_H * len(panels) + 30),
             '<rect width="100%" height="100%" fill="#ffffff"/>']
    for i, (night, title) in enumerate(panels):
        ev = night.events[0]
        t0 = max(0.0, ev.start - 40)
        t1 = min(traces.NIGHT_MIN, t0 + 180)
        parts += _panel(night, title, i * PANEL_H, t0, t1)
    y = PANEL_H * len(panels) + 18
    parts.append('<text x="%d" y="%d" font-size="11" fill="%s">— reading (1 a minute)   - - true glucose   '
                 '<tspan fill="%s">▲ fall suspected</tspan>   <tspan fill="%s">■ labelled after the rebound</tspan>   '
                 '<tspan fill="%s">— low threshold 70 mg/dL</tspan>   synthetic data</text>'
                 % (LEFT, y, MUTED, SUSPECT_C, LABEL_C, LOW_C))
    parts.append("</svg>")
    path = os.path.join(out_dir, "examples.svg")
    with open(path, "w") as f:
        f.write("\n".join(parts) + "\n")
    print("wrote", path)
