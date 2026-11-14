# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Writes contracts/trend/: the shared cases of THE trend (docs/34-trend.md, #204), computed by this reference of the
rules (the same sums in the same order as Android `TrendRate` and the Mac's `TrendRate`). Python 3 stdlib only.

    python3 tools/trend/cases.py        write rate.json, bands.json and clear.json (and list each case)

- rate.json: readings (+ the sensor's own rate) and the setting -> at every reading, from the readings at or before it:
  the effective rate, where it came from, the text band (hysteresis, doubled only once held) and the rate the
  falling / rising fast alerts judge (held for 5 min).
- bands.json: the arrow of each band (text, words, Nightscout direction, doubled), the drawn angle of a rate, the plain
  band of a rate and band sequences with hysteresis and the doubled arrow's hold.
- clear.json: the quiets' "clear move" rule (#193, contracts/alerts/SPEC.md §2), unchanged, moved here from
  contracts/alerts/trend.json.
"""
import json
import math
import os
import random

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")) + "/"
OUT = ROOT + "contracts/trend/"

# ---------------------------------------------------------------------------------------------------------------
# THE trend (docs/34 §5, the owner's decisions of #204)

TAU_S = {"quick": 120, "balanced": 180, "smooth": 300}  # the setting's weighting; "sensor" = the sensor's own rate
SETTINGS = ["quick", "balanced", "smooth", "sensor"]
DEFAULT = "balanced"
WINDOW_TAUS = 5  # the window: the readings of the last 5 tau
MIN_READINGS = 2  # at least two readings ...
MIN_SPAN_S = 180  # ... spanning at least 3 min
MAX_STEP_S = 750  # a longer step between two readings starts over (2.5 readings of a 5-min sensor)
HYSTERESIS = 0.2  # a text band is left only once the rate is this far past its edge
FULL_SCALE = 3.0  # the drawn arrow is vertical from here, and doubles (band 3)
DOUBLE_HOLD_S = 120  # the doubled arrow: the rate at least 3 at every reading of the last 2 min
ALERT_HOLD_S = 300  # the fast alerts: the rate past their threshold at every reading of the last 5 min


def weighted_slope(w, tau):
    """Least squares weighted exp(-age / tau), mg/dL/min: time in s from the newest reading, sums oldest first,
    Σw·(dt − t̄)·(v − v̄) × 60 / Σw·(dt − t̄)², t̄ and v̄ the weighted means."""
    t0 = w[-1][0]
    ws = [math.exp(-float(t0 - t) / float(tau)) for t, _ in w]
    sw = 0.0
    st = 0.0
    sv = 0.0
    for (t, v), x in zip(w, ws):
        sw += x
        st += x * float(t - t0)
        sv += x * float(v)
    mt = st / sw
    mv = sv / sw
    num = 0.0
    den = 0.0
    for (t, v), x in zip(w, ws):
        dt = float(t - t0) - mt
        num += x * dt * (float(v) - mv)
        den += x * dt * dt
    return 0.0 if den == 0.0 else num * 60.0 / den


def from_readings(pts, i, tau):
    """The rate at pts[i] from the readings at or before it ([(t, mg/dL or None)], ascending, one per t), or None:
    walk back while readings are less than 5 tau older, stopping at a LO / HI or a step over 12.5 min; at least two
    readings spanning at least 3 min."""
    t0, v0 = pts[i]
    if v0 is None:
        return None
    w = [pts[i]]
    j = i - 1
    while j >= 0:
        t, v = pts[j]
        if t0 - t >= WINDOW_TAUS * tau or v is None or pts[j + 1][0] - t > MAX_STEP_S:
            break
        w.append(pts[j])
        j -= 1
    w.reverse()
    if len(w) < MIN_READINGS or t0 - w[0][0] < MIN_SPAN_S:
        return None
    return weighted_slope(w, tau)


def plain_band(r):
    """Nightscout's direction bands ±1 / 2 / 3 (a boundary belongs to the steeper band): -3 … 3."""
    a = abs(r)
    s = 3 if a >= 3 else 2 if a >= 2 else 1 if a >= 1 else 0
    return s if r >= 0 else -s


def band_h(r, prev):
    """The band with hysteresis: the previous band is left only once the rate is HYSTERESIS past its edge."""
    b = plain_band(r)
    if prev is None or b == prev:
        return b
    return max(prev, plain_band(r - HYSTERESIS)) if b > prev else min(prev, plain_band(r + HYSTERESIS))


def back(ts, i, hold):
    """Indices from i back to the first reading at or before ts[i] - hold, none more than 12.5 min after the one
    before it; None when the readings don't reach that far."""
    out = [i]
    j = i
    while ts[i] - ts[j] < hold:
        if j == 0 or ts[j] - ts[j - 1] > MAX_STEP_S:
            return None
        j -= 1
        out.append(j)
    return out


def bands(points):
    """[(t, rate or None)] ascending, one per t -> the text band at each, or None without a rate. The band before is
    remembered while readings follow within 12.5 min with a rate (else it starts fresh: the plain band); a band is left
    only once the rate is 0.2 past its edge. Band ±3 (the doubled arrow) is entered when the rate was at least 3 that
    way at every reading of the last 2 min (that hold is its way in, instead of the 0.2), and left with the 0.2."""
    ts = [t for t, _ in points]
    out = []
    for i, (t, r) in enumerate(points):
        if r is None:
            out.append(None)
            continue
        prev = out[i - 1] if i > 0 and t - ts[i - 1] <= MAX_STEP_S else None
        b = band_h(r, prev)
        if prev is None or abs(prev) != 3 or (prev > 0) != (r > 0):
            sign = 1 if r > 0 else -1
            idx = back(ts, i, DOUBLE_HOLD_S)
            doubled = idx is not None and all(points[k][1] is not None and sign * points[k][1] >= FULL_SCALE for k in idx)
            b = 3 * sign if doubled else max(-2, min(2, b))
        out.append(b)
    return out


def held(points, i):
    """The rate the fast alerts judge at reading i: over the readings back to the first at or before 5 min earlier
    (none more than 12.5 min after the one before), all with a rate, the one nearest zero when they agree in sign,
    0 when they don't; None when they don't reach back 5 min or one has no rate."""
    ts = [t for t, _ in points]
    idx = back(ts, i, ALERT_HOLD_S)
    if idx is None or any(points[k][1] is None for k in idx):
        return None
    rs = [points[k][1] for k in idx]
    if all(r >= 0 for r in rs):
        return min(rs)
    if all(r <= 0 for r in rs):
        return max(rs)
    return 0.0


def annotate(readings, setting):
    """readings [(t, mg/dL or None for LO / HI, the sensor's rate or None)] in any order (the last one given for a
    time wins) -> per time, ascending: (t, rate, from, band, held). The rate: from the readings (the setting's tau),
    else the sensor's own rate, else none; "sensor": the sensor's own rate only; a LO / HI: none."""
    by_t = {}
    for t, v, s in readings:
        by_t[t] = (v, s)
    ts = sorted(by_t)
    pts = [(t, by_t[t][0]) for t in ts]
    rates = []
    for i, t in enumerate(ts):
        v, s = by_t[t]
        r, frm = None, "none"
        if v is not None:
            if setting != "sensor":
                r = from_readings(pts, i, TAU_S[setting])
                frm = "readings" if r is not None else "none"
            if r is None and s is not None:
                r, frm = s, "sensor"
        rates.append((r, frm))
    points = [(t, r) for t, (r, _) in zip(ts, rates)]
    bs = bands(points)
    out = []
    for i, t in enumerate(ts):
        r, frm = rates[i]
        h = (r if setting == "sensor" else held(points, i)) if r is not None else None
        out.append((t, r, frm, bs[i], h))
    return out


def deg(r):
    """The drawn arrow: linear, 0° flat, ±90° (y-down: negative = up) from ±3 mg/dL/min."""
    return -max(-1.0, min(1.0, r / FULL_SCALE)) * 90.0 + 0.0


# ---------------------------------------------------------------------------------------------------------------
# rate.json

T0 = 1_800_000_000


def ramp(per_min, minutes, step=60, start=120.0, sensor=None):
    return [[T0 + i * step, round(start + per_min * i * step / 60), sensor] for i in range(minutes * 60 // step + 1)]


def r9(x):
    return None if x is None else round(x, 9) + 0.0


def local(day, hms):
    """A wall-clock time of the owner's readings (UTC+2, where they were taken) as epoch s."""
    y, mo, d = map(int, day.split("-"))
    h, mi, s = map(int, hms.split(":"))
    import calendar
    return calendar.timegm((y, mo, d, h - 2, mi, s))


EVENING = (
    "19:55:14|91|-2.98 19:56:13|87|-3.0 19:57:14|85|-3.04 19:58:15|81|-3.18 19:59:15|77|-3.37 20:00:16|74|-3.5 "
    "20:01:16|72|-3.55 20:02:15|70|-3.53 20:08:18|57|-3.09 20:09:17|54|-3.0 20:10:18|54|-2.84 20:11:19|54|-2.67 "
    "20:12:18|54|-2.49 20:13:20|55|-2.35 20:14:20|55|-2.26 20:15:19|55|0.0 20:16:21|54|0.0 20:17:21|54|0.0 "
    "20:18:20|54|0.0 20:19:21|54|0.0 20:23:22|54|-1.03 20:24:24|53|-1.09 20:25:24|54|-0.92 20:26:22|55|-0.82 "
    "20:27:24|57|-0.73 20:28:24|57|-0.66 20:29:23|58|-0.61 20:30:25|59|-0.56 20:31:25|59|-0.51 20:32:25|61|-0.42 "
    "20:33:25|63|-0.31 20:34:26|66|-0.14 20:35:26|69|0.0 20:36:26|72|0.0 20:37:27|75|0.0 20:38:28|76|0.47 "
    "20:39:27|76|0.44 20:40:27|83|0.66 20:41:28|89|0.82 20:42:27|93|0.94 20:43:28|95|0.97 20:44:28|97|1.0 "
    "20:45:28|99|1.06"
)
RISE = (
    "14:30:37|148|-0.09 14:31:39|146|-0.19 14:32:38|144|-0.27 14:33:39|143|-0.29 14:34:38|144|-0.22 "
    "14:35:40|146|-0.12 14:36:39|148|-0.06 14:37:40|148|-0.04 14:38:41|147|-0.05 14:39:41|147|-0.04 "
    "14:40:41|147|-0.06 14:41:41|146|-0.1 14:42:42|146|-0.14 14:43:42|145|-0.13 14:44:41|147|-0.07 "
    "14:45:43|150|0.07 14:46:43|153|0.18 14:47:43|154|0.24 14:48:43|154|0.24 14:49:43|154|0.21 14:50:44|154|0.2 "
    "14:51:44|155|0.2 14:52:45|155|0.21 14:53:45|156|0.24 14:54:46|158|0.31 14:55:46|160|0.37 14:56:45|161|0.41 "
    "14:57:46|163|0.45 14:58:46|164|0.48 14:59:46|165|0.52 15:00:47|169|0.63 15:01:47|171|0.68 15:02:47|172|0.7 "
    "15:03:47|174|0.73 15:04:48|175|0.76 15:05:48|176|0.76 15:06:48|177|0.74 15:07:50|178|0.74 15:08:48|179|0.78 "
    "15:09:50|180|0.79 15:10:51|181|0.8 15:11:50|182|0.79 15:12:52|182|0.79"
)


def owner(day, text):
    out = []
    for p in text.split():
        hms, v, r = p.split("|")
        out.append([local(day, hms), int(v), float(r)])
    return out


def rate_cases():
    rnd = random.Random(204)
    c = []

    def add(name, about, readings, setting=DEFAULT):
        ins = [(t, None if isinstance(v, str) else v, s) for t, v, s in readings]
        exp = [[t, r9(r), frm, b, r9(h)] for t, r, frm, b, h in annotate(ins, setting)]
        c.append({"name": name, "about": about, "trend": setting, "readings": readings, "expect": exp})

    # docs/34's vectors (synthetic readings, no sensor rate)
    add("flat", "no change: 0", ramp(0, 20))
    add("rising 2 per min", "a straight line is read exactly", ramp(2, 20))
    add("falling 1.6 per min, 5-min sensor", "the same numbers for a 5-min sensor", ramp(-1.6, 30, step=300))
    jit = [[T0 + i * 60 + (0 if i == 0 else rnd.randint(-3, 3)), 100 + i, None] for i in range(16)]
    add("uneven timing", "readings 57-63 s apart: time is taken from each reading", jit)
    add("noisy rise", "±1 mg/dL alternating noise", [[T0 + i * 60, 100 + i + (1 if i % 2 else -1), None] for i in range(16)])
    add("accelerating", "0.1 mg/dL/min²: the rate is read about 4.5 min behind",
        [[T0 + i * 60, round(100 + 0.1 * i * i), None] for i in range(31)])
    add("turning down", "a peak 10 min ago: falling again",
        [[T0 + i * 60, round(150 - 0.08 * (i - 20) ** 2), None] for i in range(31)])
    add("just started", "3 readings over 2 min: not yet, and no sensor rate: no trend", ramp(1, 2))
    add("three minutes", "4 readings over 3 min: enough", ramp(1, 3))
    add("5-min sensor, second reading", "two readings 5 min apart: their difference", ramp(-2, 5, step=300))
    hole = ramp(1, 10) + [[T0 + 18 * 60 + i * 60, 138 + i, None] for i in range(3)]
    add("8-min hole bridged", "a step under 12.5 min doesn't start over", hole)
    gap = ramp(1, 10) + [[T0 + 23 * 60 + i * 60, 140 + i, None] for i in range(3)]
    add("13-min gap", "a step over 12.5 min starts over: 2 min of readings after it are not enough", gap)
    add("newest LO", "no number: no trend", ramp(-2, 10, start=60) + [[T0 + 11 * 60, "LO", None]])
    add("after HI", "the readings before a HI don't count; 3 min after it there is a trend",
        [[T0, "HI", None]] + [[T0 + (i + 1) * 60, 400 - i, None] for i in range(5)])
    old = ramp(3, 30)
    add("only the last 15 min count", "a rise 20-30 min ago, flat since: 0",
        old[:10] + [[t, 160, None] for t, _, _ in old[10:]])
    dup = ramp(1, 10)
    add("duplicate and out of order", "order and repeats don't matter", list(reversed(dup)) + [dup[4]])
    comp = [[T0 + i * 60, round(110 - 30 * (1 - math.exp(-i / 4)) if i < 15 else 80 + 30 * (1 - math.exp(-(i - 15) / 3))), None]
            for i in range(25)]
    add("compression-like dip, rebounding", "a fast fall then a rebound: the arrow follows the readings", comp)

    # the fallback and the setting
    add("too few readings: the sensor's rate", "the first 3 min have the sensor's rate, then the readings'",
        ramp(1, 6, sensor=0.4))
    add("a LO in the window: the sensor's rate", "after a LO the window starts over: the sensor's rate until 3 min of readings",
        ramp(-1, 4, start=60, sensor=-0.8) + [[T0 + 5 * 60, "LO", None]] +
        [[T0 + (6 + i) * 60, 41 + 2 * i, 1.1] for i in range(5)])
    add("newest LO with a sensor rate", "a LO has no rate at all", [[T0, 50, -2.0], [T0 + 60, "LO", -2.5]])
    for s in SETTINGS:
        add(f"rising 2 per min, {s}", "the same rise under each choice (the sensor says 1.5)", ramp(2, 16, sensor=1.5), s)
    add("the sensor's rate, no readings needed", "From the sensor: every reading has the sensor's rate at once",
        ramp(0, 4, sensor=-3.0), "sensor")
    add("the sensor's rate missing", "From the sensor without a rate: no trend", ramp(1, 4), "sensor")

    # the doubled arrow and the fast alerts' hold
    add("falling 3.5 per min", "doubled only once the rate was 3 or more for 2 min; the alerts' rate once held 5 min",
        ramp(-3.5, 14, start=250))
    add("one jump", "a Libre-like step of 8 mg/dL: the rate passes 3 for a reading or two, never doubled",
        [[T0 + i * 60, 120 + (8 if i >= 10 else 0), None] for i in range(16)])
    add("falling 3.5 per min, 5-min sensor", "a 5-min sensor: the 2 min reach back to the reading before",
        ramp(-3.5, 25, step=300, start=250))
    add("fall that slows", "the alerts' rate is the slowest of the last 5 min",
        [[T0 + i * 60, round(200 - (2.6 * i if i <= 10 else 26 + 1.2 * (i - 10))), None] for i in range(20)])
    add("sensor fall, alert rate at once", "From the sensor: the alerts judge the sensor's rate as it is (no hold)",
        ramp(-2.5, 3, sensor=-2.5), "sensor")

    # the owner's readings (Libre 3, 1 a minute, with Abbott's rate; #193, #204)
    ev = owner("2026-11-13", EVENING)
    for s in SETTINGS:
        add(f"the owner's evening, {s}", "2026-11-13 19:55-20:45 (#193): the fall, the low and the rebound", ev, s)
    rise = owner("2026-11-14", RISE)
    for s in ["balanced", "sensor"]:
        add(f"the owner's rise, {s}", "2026-11-14 14:30-15:12 (#204): about +1 a minute while the sensor said 0.8", rise, s)
    return c


# ---------------------------------------------------------------------------------------------------------------
# bands.json

ARROWS = [
    (3, "⇈", "rising very fast", "subiendo muy rápido", "DoubleUp"),
    (2, "↑", "rising fast", "subiendo rápido", "SingleUp"),
    (1, "↗", "rising", "subiendo", "FortyFiveUp"),
    (0, "→", "steady", "estable", "Flat"),
    (-1, "↘", "falling", "bajando", "FortyFiveDown"),
    (-2, "↓", "falling fast", "bajando rápido", "SingleDown"),
    (-3, "⇊", "falling very fast", "bajando muy rápido", "DoubleDown"),
    (None, "○", "trend unknown", "tendencia desconocida", "NONE"),
]


def band_doc():
    arrows = [{"band": b, "text": tx, "double": None if b is None else abs(b) == 3, "words": {"en": en, "es": es},
               "nightscout": ns} for b, tx, en, es, ns in ARROWS]
    plain = [{"rate": r, "band": plain_band(r)} for r in
             [5.0, 3.0, 2.9, 2.0, 1.99, 1.5, 1.0, 0.99, 0.0, -0.6, -0.99, -1.0, -1.5, -1.99, -2.0, -2.9, -3.0, -5.0]]
    angles = [{"rate": r, "deg": round(deg(r), 6) + 0.0} for r in
              [0.0, 0.1, 0.5, 1.0, 1.5, 2.0, 2.9, 3.0, 5.0, -0.5, -1.0, -2.5, -3.0, -4.0]]
    seqs = []

    def seq(name, about, rates, step=60):
        pts = [(T0 + i * step, r) for i, r in enumerate(rates)]
        seqs.append({"name": name, "about": about, "points": [[t, r] for t, r in pts], "bands": bands(pts)})

    seq("hysteresis", "a band is left only 0.2 past its edge; the doubled arrow needs the rate at 3 for 2 min",
        [0.0, 0.9, 1.0, 1.1, 1.25, 1.1, 0.85, 0.75, -0.95, -1.0, -1.25, -0.95, -0.7, 2.1, 2.25, 3.1, 3.25, 2.9, 2.7,
         -3.3, 0.0])
    seq("fresh start", "no band before: the plain band", [1.0, 0.9, 0.85])
    seq("no rate restarts", "after a reading without a rate the band starts fresh", [1.1, 0.9, None, 0.9, 1.1])
    seq("doubled after 2 min", "3.4 at every reading of 2 min (3 readings of a 1-min sensor)",
        [2.5, 3.4, 3.4, 3.4, 3.4, 2.9, 2.75, 2.5])
    seq("a blip over 3", "one or two readings over 3: not doubled", [2.5, 3.5, 3.5, 2.5, 3.5, 2.5])
    seq("falling, doubled", "the same downwards", [-2.0, -3.3, -3.3, -3.3, -2.85, -2.7])
    seq("doubled at once from a 5-min sensor's second reading", "2 min reach back to the reading 5 min before",
        [-3.5, -3.5, -3.5], step=300)
    seq("a gap restarts", "a step over 12.5 min: no band before, and the hold starts over", [1.1, 0.9, 0.9], step=800)
    return {"arrows": arrows, "plain": plain, "angles": angles, "sequences": seqs}


# ---------------------------------------------------------------------------------------------------------------
# clear.json: the quiets' rule (#193), unchanged (was tools/alerts/trend_cases.py)

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


def clear_cases():
    evening = ("20:08:18 57 · 20:09:17 54 · 20:10:18 54 · 20:11:19 54 · 20:12:18 54 · 20:13:20 55 · 20:14:20 55 · "
               "20:15:19 55 · 20:16:21 54 · 20:17:21 54 · 20:18:20 54 · 20:19:21 54 · 20:23:22 54 · 20:24:24 53 · "
               "20:25:24 54 · 20:26:22 55 · 20:27:24 57 · 20:28:24 57 · 20:29:23 58 · 20:30:25 59 · 20:31:25 59 · "
               "20:32:25 61 · 20:33:25 63 · 20:34:26 66 · 20:35:26 69 · 20:36:26 72 · 20:37:27 75 · 20:38:28 76 · "
               "20:39:27 76 · 20:40:27 83")
    ev = [(sec(p.split()[0]), int(p.split()[1])) for p in evening.split('·')]
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

    t = 1_762_100_000

    def run(values, step=60, start=t):
        return [(start + i * step, v) for i, v in enumerate(values)]

    r = run([60, 61, 62, 63, 64])
    case("a steady rise of 1 a minute", r, r[-1][0])
    case("the same readings judged as a fall", r, r[-1][0], up=False)
    case("a steady fall of 1 a minute", run([200, 199, 198, 197, 196]), t + 240, up=False)
    case("the newest reading exactly 150 s old", r, r[-1][0] + 150)
    case("the newest reading 151 s old", r, r[-1][0] + 151)
    case("4 readings spanning 3 min", run([60, 61, 62, 63]), t + 180)
    case("3 readings", run([60, 62, 64]), t + 120)
    case("4 readings spanning 2 min 59 s", [(t, 60), (t + 59, 61), (t + 119, 62), (t + 179, 63)], t + 179)
    case("a reading exactly 5 min before the newest is outside the window",
         [(t, 40 + 1), (t + 60, 62), (t + 120, 63), (t + 180, 64), (t + 300, 66)], t + 300)
    case("a gap of 150 s is one missed reading", [(t, 60), (t + 60, 61), (t + 210, 63), (t + 270, 64)], t + 270)
    case("a gap of 151 s", [(t, 60), (t + 60, 61), (t + 211, 63), (t + 271, 64)], t + 271)
    case("a LO in the window", run([39, 52, 56, 60, 64]), t + 240)
    case("a HI in the window", run([401, 390, 380, 370, 360]), t + 240, up=False)
    case("a step of 1 against the rise is noise", run([60, 62, 61, 63, 65]), t + 240)
    case("a step of 2 against the rise", run([60, 63, 61, 64, 66]), t + 240)
    case("noisy up and down", run([62, 57, 63, 58, 64]), t + 240)
    case("a single jump", run([60, 60, 60, 65]), t + 180)
    case("a rise that stalls: the first flat reading", run([58, 60, 62, 64, 64]), t + 240)
    case("a rise that stalls: the second flat reading", run([60, 62, 64, 64, 64]), t + 240)
    case("a slow drift up", run([60, 60, 61, 61, 61]), t + 240)
    case("0.5 a minute exactly", run([56, 56, 57, 57, 58]), t + 240)
    case("a slow rise from frequent readings (every 50 s): too slow", run([60, 60, 60, 60, 61, 62], step=50), t + 250)
    case("a clock-shifted window (readings an hour back, now too)", [(x - 3600, v) for x, v in run([60, 61, 62, 63, 64])],
         t + 240 - 3600)
    case("no readings", [], t)
    return cases


# ---------------------------------------------------------------------------------------------------------------


def write(name, about, body):
    """One JSON file: '_about' lines, then each list's items one per line (readable diffs)."""
    parts = ['  "_about": [\n    ' + ",\n    ".join(json.dumps(x, ensure_ascii=False) for x in about) + "\n  ]"]
    for k, v in body.items():
        if isinstance(v, list):
            rows = ",\n    ".join(json.dumps(x, ensure_ascii=False, separators=(", ", ": ")) for x in v)
            parts.append(f'  "{k}": [\n    {rows}\n  ]')
        else:
            parts.append(f'  "{k}": {json.dumps(v, ensure_ascii=False, separators=(", ", ": "))}')
    p = OUT + name
    with open(p, "w", encoding="utf-8") as f:
        f.write("{\n" + ",\n".join(parts) + "\n}\n")
    json.load(open(p, encoding="utf-8"))
    return p


def main():
    os.makedirs(OUT, exist_ok=True)
    constants = {"tauS": TAU_S, "settings": SETTINGS, "default": DEFAULT, "windowTaus": WINDOW_TAUS,
                 "minReadings": MIN_READINGS, "minSpanS": MIN_SPAN_S, "maxStepS": MAX_STEP_S, "hysteresis": HYSTERESIS,
                 "fullScale": FULL_SCALE, "doubleHoldS": DOUBLE_HOLD_S, "alertHoldS": ALERT_HOLD_S}
    rc = rate_cases()
    write("rate.json", [
        "THE trend at every reading (contracts/trend/README.md, docs/34-trend.md, #204). A case: the setting ('trend':",
        "quick / balanced / smooth = tau 2 / 3 / 5 min, sensor = the sensor's own rate) and the readings [epoch s, mg/dL",
        "or \"LO\" / \"HI\", the sensor's rate or null] in any order (the last one given for a time wins); 'expect' per time,",
        "ascending: [t, rate (mg/dL/min, null = no trend), from (readings / sensor / none), band (-3 … 3 with hysteresis",
        "and the doubled arrow's hold, null), held (what the fast alerts judge, null)]. Rates and held compare within",
        "1e-9. Written by python3 tools/trend/cases.py; run by Android TrendRateTest and Mac TrendRateTests.",
    ], {"constants": constants, "cases": rc})
    write("bands.json", [
        "THE arrow of a trend (contracts/trend/README.md): 'arrows' = per band (-3 … 3, null = no trend) the text arrow",
        "every text place writes, whether it is drawn doubled, the words TalkBack / VoiceOver say (en, es) and the",
        "Nightscout direction the upload and the local endpoints send; 'plain' = a rate's band with nothing before it;",
        "'angles' = the drawn arrow's rotation (degrees, y-down: negative = up), continuous; 'sequences' = rates at",
        "readings [epoch s, rate or null] and the band shown at each (hysteresis 0.2; band ±3 only once the rate was",
        "at least 3 for 2 min). Written by python3 tools/trend/cases.py; run by both apps' TrendRate and arrow tests.",
    ], band_doc())
    cc = clear_cases()
    write("clear.json", [
        "The trend both falling / rising quiets read from the readings (contracts/alerts/SPEC.md §2 'Trend from the",
        "readings', #193). Each case: the readings ([epoch s, mg/dL], oldest first, as the engine keeps them), now, the",
        "direction (up = rising), and the expected least-squares slope in mg/dL/min of a clear move, or null with why.",
        "Run by Android TrendRateTest and Mac TrendRateTests (to 1e-9). Written by python3 tools/trend/cases.py (a",
        "reference of the rule): a change to the rule changes it there and in both apps.",
    ], {"cases": cc})
    for c in rc:
        last = c["expect"][-1]
        print(f'{c["name"]} ({c["trend"]}) → newest: rate {last[1]} from {last[2]} band {last[3]} held {last[4]}')


if __name__ == "__main__":
    main()
