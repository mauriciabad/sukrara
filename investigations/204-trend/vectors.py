# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The proposed shared test vectors for the trend from the readings (investigation 34, issue #204): synthetic readings
only, the expected rate from `methods.reference`, the text band with hysteresis and the drawn angle. The build moves
this to `tools/` and its output to `contracts/` (docs/34 "In Sukrara"); here it prints the JSON.

    python3 -I vectors.py            the JSON on stdout
    python3 -I vectors.py OUT.json   written to OUT.json
"""

import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # also under python3 -I, from any directory
import methods as M  # noqa: E402

T0 = 1_800_000_000
HYSTERESIS = 0.2
FULL_SCALE = 3.0


def ramp(per_min, minutes, step=60, start=120.0):
    return [[T0 + i * step, round(start + per_min * i * step / 60)] for i in range(minutes * 60 // step + 1)]


def cases():
    rnd = random.Random(204)
    c = []

    def add(name, readings, about):
        rate = M.reference([(t, None if isinstance(v, str) else float(v)) for t, v in readings])
        c.append({"name": name, "about": about, "readings": readings,
                  "expect": {"rate": None if rate is None else round(rate, 9)}})

    add("flat", ramp(0, 20), "no change: 0")
    add("rising 2 per min", ramp(2, 20), "a straight line is read exactly")
    add("falling 1.6 per min, 5-min sensor", ramp(-1.6, 30, step=300), "the same numbers for a 5-min sensor")
    jit = [[T0 + i * 60 + (0 if i == 0 else rnd.randint(-3, 3)), 100 + i] for i in range(16)]
    add("uneven timing", jit, "readings 57-63 s apart: time is taken from each reading")
    add("noisy rise", [[T0 + i * 60, 100 + i + (1 if i % 2 else -1)] for i in range(16)], "±1 mg/dL alternating noise")
    acc = [[T0 + i * 60, round(100 + 0.1 * i * i)] for i in range(31)]
    add("accelerating", acc, "0.1 mg/dL/min²: the rate is read about 4.5 min behind")
    turn = [[T0 + i * 60, round(150 - 0.08 * (i - 20) ** 2)] for i in range(31)]
    add("turning down", turn, "a peak 10 min ago: falling again")
    add("just started", ramp(1, 2), "3 readings over 2 min: not yet")
    add("three minutes", ramp(1, 3), "4 readings over 3 min: enough")
    add("5-min sensor, second reading", ramp(-2, 5, step=300), "two readings 5 min apart: their difference")
    hole = ramp(1, 10) + [[T0 + 10 * 60 + 8 * 60 + i * 60, 138 + i] for i in range(3)]
    add("8-min hole bridged", hole, "a step under 12.5 min doesn't start over")
    gap = ramp(1, 10) + [[T0 + 10 * 60 + 13 * 60 + i * 60, 140 + i] for i in range(3)]
    add("13-min gap", gap, "a step over 12.5 min starts over: 2 min of readings after it are not enough")
    lo = ramp(-2, 10, start=60) + [[T0 + 11 * 60, "LO"]]
    add("newest LO", lo, "no number: no trend from the readings (the sensor's rate, if any, is the fallback)")
    after_hi = [[T0, "HI"]] + [[T0 + (i + 1) * 60, 400 - i] for i in range(5)]
    add("after HI", after_hi, "the readings before a HI don't count; 4 min after it there is a trend")
    old = ramp(3, 30)
    add("only the last 15 min count", old[:10] + [[t, 160] for t, _ in old[10:]], "a rise 20-30 min ago, flat since: 0")
    dup = ramp(1, 10)
    add("duplicate and out of order", list(reversed(dup)) + [dup[4]], "order and repeats don't matter")
    comp = [[T0 + i * 60, round(110 - 30 * (1 - math.exp(-i / 4)) if i < 15 else 80 + 30 * (1 - math.exp(-(i - 15) / 3)))]
            for i in range(25)]
    add("compression-like dip, rebounding", comp, "a fast fall then a rebound: the arrow follows the readings, nothing more")
    return c


def bands():
    """The text band with hysteresis: a sequence of rates and the band shown after each."""
    seq = [0.0, 0.9, 1.0, 1.1, 1.25, 1.1, 0.85, 0.75, -0.95, -1.0, -1.25, -0.95, -0.7, 2.1, 2.25, 3.1, 3.25, 2.9, 2.7,
           -3.3, 0.0]
    out, prev = [], None
    for r in seq:
        b = _band(r)
        if prev is not None and b != prev:
            b = max(prev, _band(r - HYSTERESIS)) if b > prev else min(prev, _band(r + HYSTERESIS))
        out.append({"rate": r, "band": b})
        prev = b
    return out


def _band(r):
    a = abs(r)
    s = 3 if a >= 3 else 2 if a >= 2 else 1 if a >= 1 else 0
    return s if r >= 0 else -s


def angles():
    """The drawn arrow: linear, 0° flat, ±90° (y-down: negative = up) from ±3 mg/dL/min, doubled from 3."""
    return [{"rate": r, "deg": round(-max(-1.0, min(1.0, r / FULL_SCALE)) * 90.0, 6) + 0.0, "double": abs(r) >= FULL_SCALE}
            for r in [0.0, 0.5, 1.0, 1.5, 2.0, 2.9, 3.0, 5.0, -0.5, -1.0, -2.5, -3.0, -4.0]]


def main():
    doc = {
        "_about": [
            "Trend from the readings (docs/34-trend.md, #204), proposed shared cases; synthetic readings only.",
            "readings: [epoch s, mg/dL or \"LO\" / \"HI\"]; expect.rate: mg/dL/min at the newest reading (null = none),",
            f"weights exp(-age / {M.TAU_S} s) over {M.WINDOW_TAUS * M.TAU_S} s, >= {M.MIN_READINGS} readings over >= "
            f"{M.MIN_SPAN_S} s, a step over {M.MAX_STEP_S} s or a LO / HI starts over; compare within 1e-9.",
            f"bands: the text band after each rate in order, hysteresis {HYSTERESIS}; angles: the drawn arrow.",
        ],
        "cases": cases(),
        "bands": bands(),
        "angles": angles(),
    }
    text = json.dumps(doc, ensure_ascii=False, indent=1)
    if len(sys.argv) > 1:
        with open(sys.argv[1], "w") as f:
            f.write(text + "\n")
    else:
        print(text)


if __name__ == "__main__":
    main()
