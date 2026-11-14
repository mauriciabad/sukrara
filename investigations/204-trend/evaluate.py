# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compares the trend estimators of `methods.py` on a readings file (investigation 34, issue #204).

    python3 -I evaluate.py READINGS.csv                     the main table (1-min readings)
    python3 -I evaluate.py READINGS.csv --sweep             every parameter set
    python3 -I evaluate.py READINGS.csv --every 5           the readings decimated to one every 5 (a 5-min sensor)
    python3 -I evaluate.py READINGS.csv --noise 2           Gaussian noise (sd mg/dL) added, then rounded
    python3 -I evaluate.py READINGS.csv --gaps 40           40 random 3–20 min stretches removed
    python3 -I evaluate.py READINGS.csv --hysteresis        the text bands with hysteresis 0 … 0.3
    python3 -I evaluate.py READINGS.csv --episode "2026-11-13 20:08" "2026-11-13 20:45"   one stretch, local time

READINGS.csv: columns t_ms, mgdl, rate (the sensor's own rate; may be empty), as the Mac writes it. A non-numeric
mgdl (LO / HI) is a reading without a number. The output is aggregate numbers only: nothing per reading.

"Truth" is a hindsight estimate the apps can't have: at each reading, the slope at that reading of a parabola fitted
with tricube weights to the readings `--truth` minutes either side (default 7), when both sides are covered without a
gap. It is the derivative of the sensor's own signal (what the person sees), not of blood glucose.
"""

import argparse
import bisect
import csv
import math
import os
import random
import sys
import time
from datetime import datetime
from statistics import median
from typing import List, Optional

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # also under python3 -I, from any directory
import methods as M  # noqa: E402

MIN = 60


def load(path):
    ts, vs, rs = [], [], []
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            ts.append(int(row["t_ms"]) // 1000)
            try:
                vs.append(float(row["mgdl"]))
            except ValueError:
                vs.append(None)
            try:
                rs.append(float(row["rate"]))
            except (ValueError, KeyError, TypeError):
                rs.append(None)
    return ts, vs, rs


def truth(ts, vs, half=7 * MIN, max_gap=150):
    """The hindsight slope at every reading (None where it can't be judged)."""
    out: List[Optional[float]] = []
    n = len(ts)
    lo = 0
    for i in range(n):
        t = ts[i]
        while ts[lo] < t - half:
            lo += 1
        hi = i
        while hi + 1 < n and ts[hi + 1] <= t + half:
            hi += 1
        w = list(range(lo, hi + 1))
        ok = (
            vs[i] is not None
            and t - ts[lo] >= 0.7 * half
            and ts[hi] - t >= 0.7 * half
            and all(vs[j] is not None for j in w)
            and all(ts[w[k + 1]] - ts[w[k]] <= max_gap for k in range(len(w) - 1))
        )
        if not ok:
            out.append(None)
            continue
        pts = [(ts[j] - t, vs[j]) for j in w]
        wts = [(1 - (abs(dt) / (half + 1)) ** 3) ** 3 for dt, _ in pts]
        out.append(_quad_slope_at_zero(pts, wts))
    return out


def _quad_slope_at_zero(pts, wts):
    s = [0.0] * 5
    r = [0.0] * 3
    for (dt, v), w in zip(pts, wts):
        x = dt / 60.0
        xk = 1.0
        for k in range(5):
            s[k] += w * xk
            if k < 3:
                r[k] += w * xk * v
            xk *= x
    m = [[s[0], s[1], s[2]], [s[1], s[2], s[3]], [s[2], s[3], s[4]]]
    mb = [[m[0][0], r[0], m[0][2]], [m[1][0], r[1], m[1][2]], [m[2][0], r[2], m[2][2]]]
    return M._det3(mb) / M._det3(m)


# ---------------------------------------------------------------------------------------------------------------
# the candidates; gap = the longest step a window may hold (1-min: 150 s, 5-min: 450 s)


def configs(gap: int, every: int, sweep: bool):
    c = {}
    d_tol = 90 if every == 1 else 150
    c["sensor rate (Abbott)"] = lambda: M.SensorRate()
    for tau in [2, 3, 5]:
        c[f"RECOMMENDED weighted LS tau {tau}"] = (lambda tau=tau: lambda: M.recommended(tau * MIN))()
    if every == 1:
        c["delta 1 reading"] = lambda: M.Delta(60, tol=d_tol)
    c["delta 5 min"] = lambda: M.Delta(300, tol=d_tol)
    if sweep:
        c["delta 10 min"] = lambda: M.Delta(600, tol=d_tol)
        c["delta 15 min"] = lambda: M.Delta(900, tol=d_tol)
    c["AAPS delta"] = lambda: M.AapsDeltas("delta", max_gap=gap)
    c["AAPS shortAvgDelta"] = lambda: M.AapsDeltas("short", max_gap=gap)
    lsw = [4, 5, 6, 7, 8, 10, 12, 15] if sweep else [5, 10, 15]
    if every > 1:
        lsw = [15, 20] if not sweep else [15, 20, 25, 30]
    for w in lsw:
        c[f"least squares {w} min"] = (lambda w=w: lambda: M.LeastSquares(w * MIN, max_gap=gap))()
    # the window is 5 tau: the weight at its far end is exp(-5) = 0.7 %, so it only bounds the work
    wls = [(10, 2), (15, 3), (20, 4), (25, 5), (15, 2), (15, 4)] if sweep else [(10, 2), (15, 3), (25, 5)]
    if every > 1:
        wls = [(15, 3), (20, 4), (25, 5), (30, 6), (40, 8)]
    for w, tau in wls:
        c[f"weighted LS {w} min, tau {tau}"] = (lambda w=w, tau=tau: lambda: M.WeightedLeastSquares(w * MIN, tau * MIN, max_gap=gap))()
    qw = [7, 10, 15, 20, 25] if sweep else [10, 15, 20]
    if every > 1:
        qw = [20, 25, 30] if not sweep else [15, 20, 25, 30, 40]
    for w in qw:
        c[f"quadratic {w} min (S-G end)"] = (lambda w=w: lambda: M.LocalQuadratic(w * MIN, max_gap=gap, min_n=4))()
    if sweep:
        for w, tau in [(20, 8), (25, 10), (30, 10)]:
            c[f"weighted quadratic {w} min, tau {tau}"] = (lambda w=w, tau=tau: lambda: M.LocalQuadratic(w * MIN, tau * MIN, max_gap=gap))()
    tsw = [5, 10] if every == 1 else [15, 20]
    for w in tsw:
        c[f"Theil-Sen {w} min"] = (lambda w=w: lambda: M.TheilSen(w * MIN, max_gap=gap))()
    holt = [(0.5, 0.2), (0.3, 0.1)] if not sweep else [(0.7, 0.3), (0.5, 0.3), (0.5, 0.2), (0.4, 0.15), (0.3, 0.2), (0.3, 0.1), (0.2, 0.1)]
    for a, b in holt:
        c[f"Holt a {a} b {b}"] = (lambda a=a, b=b: lambda: M.Holt(a, b, max_gap=max(600, 3 * gap)))()
    kq = [0.002, 0.005, 0.01] if not sweep else [0.001, 0.002, 0.003, 0.005, 0.007, 0.01, 0.02, 0.05]
    for q in kq:
        c[f"Kalman q {q}"] = (lambda q=q: lambda: M.Kalman(q=q, r=1.0, max_gap=max(600, 3 * gap)))()
    return c


# ---------------------------------------------------------------------------------------------------------------
# metrics


def band(rate: float) -> int:
    """Nightscout's direction bands (a boundary belongs to the steeper band), -3 … 3."""
    a = abs(rate)
    s = 3 if a >= 3 else 2 if a >= 2 else 1 if a >= 1 else 0
    return s if rate >= 0 else -s


def band_h(rate: float, prev: Optional[int], h: float) -> int:
    """The band with hysteresis h: leave the previous band only once the rate is h beyond its edge."""
    b = band(rate)
    if prev is None or h == 0 or b == prev:
        return b
    return max(prev, band(rate - h)) if b > prev else min(prev, band(rate + h))


def angle(rate: float) -> float:
    """The PoC's continuous arrow: 0° flat, ±90° at ±3 mg/dL/min, linear."""
    return max(-1.0, min(1.0, rate / 3.0)) * 90.0


def flips(ts, est, h=0.0):
    """Band changes per hour over the readings that have an estimate (a None restarts the count)."""
    n, prev, hours, last_t = 0, None, 0.0, None
    for t, e in zip(ts, est):
        if e is None:
            prev, last_t = None, None
            continue
        b = band_h(e, prev, h)
        if prev is not None and b != prev:
            n += 1
        if last_t is not None:
            hours += (t - last_t) / 3600
        prev, last_t = b, t
    return n / hours if hours else float("nan")


def angle_jitter(ts, est, gap=150):
    """Median |change of the continuous angle| between consecutive readings with an estimate, degrees."""
    d = [abs(angle(b) - angle(a)) for a, b, t0, t1 in zip(est, est[1:], ts, ts[1:]) if a is not None and b is not None and t1 - t0 <= gap]
    return median(d) if d else float("nan")


def interp(ts, xs, t):
    i = bisect.bisect_left(ts, t)
    if i < len(ts) and ts[i] == t:
        return xs[i]
    if i == 0 or i >= len(ts) or xs[i - 1] is None or xs[i] is None or ts[i] - ts[i - 1] > 150:
        return None
    f = (t - ts[i - 1]) / (ts[i] - ts[i - 1])
    return xs[i - 1] + f * (xs[i] - xs[i - 1])


def lag_fit(ts_e, est, ts_t, tru, idx):
    """The delay (min) that best lines the estimate up with the truth, and the gain there (the least-squares factor
    from the delayed truth to the estimate: below 1 = the estimate understates how fast glucose moves)."""
    best = (float("inf"), 0.0, float("nan"))
    for k in range(0, 31):
        tau = k * 30
        se, n, sxy, sxx = 0.0, 0, 0.0, 0.0
        for i in idx:
            tv = interp(ts_t, tru, ts_e[i] - tau)
            if tv is None:
                continue
            se += (est[i] - tv) ** 2
            sxy += est[i] * tv
            sxx += tv * tv
            n += 1
        if n and se / n < best[0]:
            best = (se / n, tau / 60, sxy / sxx if sxx else float("nan"))
    return best[1], best[2]


def crossings(ts, xs, theta, up, settle=5 * MIN, quiet=10 * MIN):
    """Times the series crosses theta (upwards if up) and stays beyond it for `settle`, at least `quiet` after its
    previous crossing of theta either way."""
    out, last_cross = [], -math.inf
    for i in range(1, len(xs)):
        a, b = xs[i - 1], xs[i]
        if a is None or b is None or ts[i] - ts[i - 1] > 150:
            continue
        crossed_up, crossed_dn = a < theta <= b, a > theta >= b
        if not (crossed_up or crossed_dn):
            continue
        if (crossed_up if up else crossed_dn) and ts[i] - last_cross >= quiet:
            j, held = i, True
            while j < len(xs) and ts[j] - ts[i] <= settle:
                if xs[j] is None or (xs[j] < theta if up else xs[j] > theta):
                    held = False
                    break
                j += 1
            if held:
                out.append(ts[i])
        last_cross = ts[i]
    return out


def cross_delay(ts, est, tc, theta, up, before=5 * MIN, cap=20 * MIN):
    """Minutes from the truth's crossing at tc to the estimate's first reading beyond theta (from 5 min before); cap = missed."""
    i = bisect.bisect_left(ts, tc - before)
    while i < len(ts) and ts[i] <= tc + cap:
        e = est[i]
        if e is not None and (e >= theta if up else e <= theta):
            return (ts[i] - tc) / 60
        i += 1
    return None


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(round(p * (len(xs) - 1))))] if xs else float("nan")


def evaluate(ts, vs, rs, ts_t, tru, cfgs, h=0.2, gap=150):
    """One row of numbers per candidate."""
    ests = {name: M.run(f(), ts, vs, rs) for name, f in cfgs.items()}
    t_at = {t: i for i, t in enumerate(ts_t)}
    tru_e = [tru[t_at[t]] if t in t_at else None for t in ts]
    judged = [i for i in range(len(ts)) if tru_e[i] is not None]
    common = [i for i in judged if all(e[i] is not None for e in ests.values())]
    events = []
    for theta, up in [(0, True), (0, False), (1, True), (-1, False), (2, True), (-2, False)]:
        for tc in crossings(ts_t, tru, theta, up):
            events.append((theta, up, tc))
    rows = {}
    for name, est in ests.items():
        err = [abs(est[i] - tru_e[i]) for i in common]
        lag, gain = lag_fit(ts, est, ts_t, tru, common)
        delays = {"0": [], "1": [], "2": []}
        missed = {"0": 0, "1": 0, "2": 0}
        for theta, up, tc in events:
            d = cross_delay(ts, est, tc, theta, up)
            key = str(abs(theta))
            if d is None:
                missed[key] += 1
            else:
                delays[key].append(d)
        agree = [band(est[i]) == band(tru_e[i]) for i in common]
        rows[name] = {
            "avail": sum(est[i] is not None for i in judged) / max(1, len(judged)),
            "mae": sum(err) / max(1, len(err)),
            "p95": pct(err, 0.95),
            "lag": lag,
            "gain": gain,
            "d0": median(delays["0"]) if delays["0"] else float("nan"),
            "d1": median(delays["1"]) if delays["1"] else float("nan"),
            "d1p90": pct(delays["1"], 0.9),
            "d2": median(delays["2"]) if delays["2"] else float("nan"),
            "missed": sum(missed.values()),
            "flips": flips(ts, est),
            "flips_h": flips(ts, est, h),
            "agree": sum(agree) / max(1, len(agree)),
            "jitter": angle_jitter(ts, est, gap),
        }
    n_events = {k: sum(1 for th, _, _ in events if str(abs(th)) == k) for k in "012"}
    truth_row = {"flips": flips(ts_t, tru), "flips_h": flips(ts_t, tru, h), "jitter": angle_jitter(ts_t, tru)}
    return rows, n_events, truth_row, len(judged), len(common)


def fmt(x, d=2):
    return "—" if x is None or (isinstance(x, float) and math.isnan(x)) else f"{x:.{d}f}"


def print_table(rows, n_events, truth_row, n_judged, n_common, h):
    print(f"\nreadings judged (truth known): {n_judged}; errors over the {n_common} where every candidate has a rate")
    print(f"turning points in the truth: 0 crossings {n_events['0']}, ±1 {n_events['1']}, ±2 {n_events['2']}")
    print(f"truth itself: {fmt(truth_row['flips'])} band changes/h ({fmt(truth_row['flips_h'])} with hysteresis {h}), "
          f"angle jitter {fmt(truth_row['jitter'], 1)}°\n")
    print("| Candidate | Has a rate | Mean abs error | 95th pct error | Delay (best fit) | Gain there | "
          "Delay to cross 0 | … ±1 (median / p90) | … ±2 | Missed | Band changes/h | … with hysteresis | "
          "Same band as truth | Angle jitter |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for name, r in rows.items():
        print(f"| {name} | {r['avail'] * 100:.0f} % | {fmt(r['mae'])} | {fmt(r['p95'])} | {fmt(r['lag'], 1)} min | "
              f"{fmt(r['gain'])} | {fmt(r['d0'], 1)} | {fmt(r['d1'], 1)} / {fmt(r['d1p90'], 1)} | {fmt(r['d2'], 1)} | "
              f"{r['missed']} | {fmt(r['flips'], 1)} | {fmt(r['flips_h'], 1)} | {r['agree'] * 100:.0f} % | "
              f"{fmt(r['jitter'], 1)}° |")


def local_ts(s):
    return int(time.mktime(datetime.fromisoformat(s).timetuple()))


def episode(ts, vs, rs, ts_t, tru, cfgs, start, end):
    """Within [start, end]: for every candidate, minutes after the truth that it first crossed 0 / +1 / -1 the way the
    truth did (the episode's own turning points), and its mean abs error there."""
    a, b = local_ts(start), local_ts(end)
    print(f"\nepisode {start} – {end}")
    sel = [i for i, t in enumerate(ts_t) if a <= t <= b]
    if not sel:
        print("no readings")
        return
    seg_t, seg = [ts_t[i] for i in sel], [tru[i] for i in sel]
    ev = []
    for theta, up in [(0, True), (0, False), (1, True), (-1, False)]:
        for tc in crossings(seg_t, seg, theta, up, settle=3 * MIN, quiet=5 * MIN):
            ev.append((theta, up, tc))
    ev.sort(key=lambda e: e[2])
    print("truth's crossings (min from start): " + ", ".join(f"{'+' if up else '-'}{abs(th)} at {(tc - a) / 60:.0f}" for th, up, tc in ev))
    for name, f in cfgs.items():
        est = M.run(f(), ts, vs, rs)
        ds = []
        for theta, up, tc in ev:
            d = cross_delay(ts, est, tc, theta, up)
            ds.append("missed" if d is None else f"{d:+.0f}")
        ix = [i for i, t in enumerate(ts) if a <= t <= b and est[i] is not None]
        tmap = {t: v for t, v in zip(ts_t, tru)}
        err = [abs(est[i] - tmap[ts[i]]) for i in ix if tmap.get(ts[i]) is not None]
        print(f"  {name}: delays {', '.join(ds)} min; mean abs error {fmt(sum(err) / len(err) if err else None)}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv")
    ap.add_argument("--sweep", action="store_true")
    ap.add_argument("--every", type=int, default=1)
    ap.add_argument("--noise", type=float, default=0.0)
    ap.add_argument("--gaps", type=int, default=0)
    ap.add_argument("--truth", type=float, default=7.0, help="hindsight half-width, minutes")
    ap.add_argument("--hysteresis", action="store_true")
    ap.add_argument("--episode", nargs=2, action="append", metavar=("START", "END"))
    ap.add_argument("--only", help="comma-separated substrings of candidate names")
    ap.add_argument("--seed", type=int, default=204)
    o = ap.parse_args()

    ts, vs, rs = load(o.csv)
    ts_t, tru = ts, truth(ts, vs, half=int(o.truth * MIN))
    rnd = random.Random(o.seed)
    if o.noise:
        vs = [None if v is None else float(round(v + rnd.gauss(0, o.noise))) for v in vs]
    if o.gaps:
        drop = set()
        for _ in range(o.gaps):
            s = rnd.randrange(len(ts))
            ln = rnd.randint(3, 20)
            drop.update(range(s, min(len(ts), s + ln)))
        keep = [i for i in range(len(ts)) if i not in drop]
        ts, vs, rs = [ts[i] for i in keep], [vs[i] for i in keep], [rs[i] for i in keep]
    if o.every > 1:
        keep = list(range(0, len(ts), o.every))
        ts, vs, rs = [ts[i] for i in keep], [vs[i] for i in keep], [rs[i] for i in keep]
    gap = 150 if o.every == 1 else int(150 * o.every * 1.0)  # 2.5 readings
    cfgs = configs(gap, o.every, o.sweep)
    if o.only:
        keys = [k.strip() for k in o.only.split(",")]
        cfgs = {n: f for n, f in cfgs.items() if any(k in n for k in keys)}
    if o.episode:
        for s, e in o.episode:
            episode(ts, vs, rs, ts_t, tru, cfgs, s, e)
        return
    if o.hysteresis:
        for h in [0.0, 0.1, 0.15, 0.2, 0.3]:
            rows, n_ev, tr, nj, nc = evaluate(ts, vs, rs, ts_t, tru, cfgs, h, gap)
            print(f"\nhysteresis {h}: truth {fmt(tr['flips_h'])}/h; " + "; ".join(f"{n} {fmt(r['flips_h'])}/h" for n, r in rows.items()))
        return
    rows, n_ev, tr, nj, nc = evaluate(ts, vs, rs, ts_t, tru, cfgs, 0.2, gap)
    print_table(rows, n_ev, tr, nj, nc, 0.2)
    if o.gaps or o.every > 1:
        after_gap(ts, vs, rs, cfgs, gap)


def after_gap(ts, vs, rs, cfgs, gap):
    """After every gap (a step over `gap`, and at least 3 min): minutes until each candidate has a rate again."""
    starts = [i for i in range(1, len(ts)) if ts[i] - ts[i - 1] > max(gap, 3 * MIN)]
    print(f"\n{len(starts)} gaps; minutes from the first reading after a gap to a rate (median / max):")
    for name, f in cfgs.items():
        est = M.run(f(), ts, vs, rs)
        ws = []
        for s in starts:
            j = s
            while j < len(ts) and est[j] is None and ts[j] - ts[s] < 60 * MIN:
                j += 1
            ws.append((ts[j] - ts[s]) / 60 if j < len(ts) else 60)
        print(f"  {name}: {fmt(median(ws), 1)} / {fmt(max(ws), 1)}")


if __name__ == "__main__":
    main()
