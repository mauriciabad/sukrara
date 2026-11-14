# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The trend estimators compared in investigation 34 (issue #204). Python 3.9 standard library only.

Every estimator is causal and streaming, like the apps would run it: `update(t, v)` is called once per reading, in
time order (t in epoch seconds, v in mg/dL, or None for LO / HI), and returns the rate in mg/dL/min at that reading, or
None when it can't tell. Nothing looks at a later reading.

Shared rules of the window methods (`Window`): the readings taken in the `w` seconds up to the newest one, at least
`min_n` of them, spanning at least `min_span` seconds (default half the window), no step between two of them longer than `max_gap` and no
LO / HI among them (a LO / HI empties the window: the next readings start a new one).
"""

import math
from collections import deque
from typing import Deque, List, Optional, Tuple

Reading = Tuple[int, float]


class Window:
    """The readings of the last `w` seconds, with the shared validity rules."""

    def __init__(self, w: int, min_n: int = 3, min_span: Optional[int] = None, max_gap: int = 150):
        self.w, self.min_n, self.max_gap = w, min_n, max_gap
        self.min_span = w // 2 if min_span is None else min_span
        self.buf: Deque[Reading] = deque()

    def push(self, t: int, v: Optional[float]) -> Optional[List[Reading]]:
        if v is None:  # LO / HI: no number, and the readings before it are no longer comparable
            self.buf.clear()
            return None
        if self.buf and t - self.buf[-1][0] > self.max_gap:
            self.buf.clear()  # a gap: start over
        self.buf.append((t, v))
        while self.buf and t - self.buf[0][0] >= self.w:
            self.buf.popleft()
        if len(self.buf) < self.min_n or t - self.buf[0][0] < self.min_span:
            return None
        return list(self.buf)


def lsq(pts: List[Reading], weights: Optional[List[float]] = None) -> float:
    """(Weighted) least-squares slope in mg/dL/min, time measured from the newest reading."""
    t0 = pts[-1][0]
    ws = weights or [1.0] * len(pts)
    sw = sum(ws)
    mt = sum(w * (t - t0) for w, (t, _) in zip(ws, pts)) / sw
    mv = sum(w * v for w, (_, v) in zip(ws, pts)) / sw
    num = sum(w * ((t - t0) - mt) * (v - mv) for w, (t, v) in zip(ws, pts))
    den = sum(w * ((t - t0) - mt) ** 2 for w, (t, _) in zip(ws, pts))
    return 0.0 if den == 0 else num * 60.0 / den


def quad_at_end(pts: List[Reading], weights: Optional[List[float]] = None) -> float:
    """Derivative at the newest reading of the (weighted) least-squares parabola (Savitzky–Golay's end-point form for
    evenly spaced readings), mg/dL/min. Time in minutes from the newest reading, so the derivative there is `b`."""
    t0 = pts[-1][0]
    ws = weights or [1.0] * len(pts)
    s = [0.0] * 5  # Σw·x^k
    r = [0.0] * 3  # Σw·x^k·y
    for w, (t, v) in zip(ws, pts):
        x = (t - t0) / 60.0
        xk = 1.0
        for k in range(5):
            s[k] += w * xk
            if k < 3:
                r[k] += w * xk * v
            xk *= x
    # normal equations [[s0 s1 s2][s1 s2 s3][s2 s3 s4]] · [a b c] = [r0 r1 r2], solved by Cramer for b
    m = [[s[0], s[1], s[2]], [s[1], s[2], s[3]], [s[2], s[3], s[4]]]
    det = _det3(m)
    if abs(det) < 1e-9:
        return lsq(pts, weights)
    mb = [[m[0][0], r[0], m[0][2]], [m[1][0], r[1], m[1][2]], [m[2][0], r[2], m[2][2]]]
    return _det3(mb) / det


def _det3(m: List[List[float]]) -> float:
    return (
        m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
        - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
        + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
    )


class Delta:
    """Two-point delta: (now − the reading about `d` s ago) / minutes between them (Nightscout's delta, d = 5 min;
    xDrip+'s last-two-readings slope, d = 1 min). The older reading must be within `tol` s of `d` ago."""

    def __init__(self, d: int = 300, tol: int = 90):
        self.d, self.tol = d, tol
        self.buf: Deque[Reading] = deque()

    def update(self, t: int, v: Optional[float]) -> Optional[float]:
        if v is None:
            self.buf.clear()
            return None
        self.buf.append((t, v))
        while self.buf and t - self.buf[0][0] > self.d + self.tol:
            self.buf.popleft()
        best = min(self.buf, key=lambda p: abs(t - p[0] - self.d))
        if abs(t - best[0] - self.d) > self.tol or t == best[0]:
            return None
        return (v - best[1]) * 60.0 / (t - best[0])


class AapsDeltas:
    """AndroidAPS' `delta` / `shortAvgDelta` (`DeltaCalculator`, the oref0 `glucose-get-last.js` windows): readings
    first resampled onto a 5-min grid ending at the newest one (`AutosensDataStoreObject.createBucketedDataRecalculated`
    interpolates 1-min data onto the grid), then each grid point 2.5–17.5 min old gives `avgdelta` = change / minutes
    × 5; `delta` = the mean of those 2.5–7.5 min old (the one 5 min old), `short_avgdelta` = the mean of the 5, 10 and
    15 min ones (`kind` = "delta" or "short"); ÷ 5 here for mg/dL/min. oref0 itself, fed 1-min readings, averages every
    reading into "now" (its running `now_date` keeps every next reading < 2.5 min away) and returns 0: AAPS buckets
    first for that reason, and so does this."""

    def __init__(self, kind: str = "delta", max_gap: int = 150):
        self.kind, self.max_gap = kind, max_gap
        self.buf: Deque[Reading] = deque()

    def at(self, t: float) -> Optional[float]:
        pts = self.buf
        for k in range(len(pts) - 1, 0, -1):
            (t0, v0), (t1, v1) = pts[k - 1], pts[k]
            if t0 <= t <= t1:
                if t1 - t0 > max(self.max_gap, 150) and min(t - t0, t1 - t) > 30:
                    return None
                return v0 + (v1 - v0) * (t - t0) / (t1 - t0) if t1 > t0 else v1
        return None

    def update(self, t: int, v: Optional[float]) -> Optional[float]:
        if v is None:
            self.buf.clear()
            return None
        self.buf.append((t, v))
        while self.buf and t - self.buf[0][0] > 20 * 60:
            self.buf.popleft()
        ks = [1] if self.kind == "delta" else [1, 2, 3]
        ds = []
        for k in ks:
            old = self.at(t - 300 * k)
            if old is not None:
                ds.append((v - old) / (5 * k))
        return sum(ds) / len(ds) if ds else None


class LeastSquares:
    """Ordinary least-squares slope over the last `w` s (Sukrara's #193 `Trend` with w = 5 min; Loop's glucose momentum
    with w = 15 min)."""

    def __init__(self, w: int = 300, max_gap: int = 150, min_n: int = 3):
        self.win = Window(w, min_n=min_n, max_gap=max_gap)

    def update(self, t, v):
        pts = self.win.push(t, v)
        return lsq(pts) if pts else None


class WeightedLeastSquares:
    """Least squares with recency weights exp(−age / tau) over the last `w` s."""

    def __init__(self, w: int = 900, tau: float = 180, max_gap: int = 150, min_n: int = 3, min_span: Optional[int] = None):
        self.win, self.tau = Window(w, min_n=min_n, min_span=min_span, max_gap=max_gap), tau

    def update(self, t, v):
        pts = self.win.push(t, v)
        if not pts:
            return None
        return lsq(pts, [math.exp(-(t - pt) / self.tau) for pt, _ in pts])


class LocalQuadratic:
    """Savitzky–Golay style: the least-squares parabola over the last `w` s, its slope at the newest reading. With
    `tau`, recency weights exp(−age / tau)."""

    def __init__(self, w: int = 900, tau: Optional[float] = None, max_gap: int = 150, min_n: int = 4):
        self.win, self.tau = Window(w, min_n=min_n, max_gap=max_gap), tau

    def update(self, t, v):
        pts = self.win.push(t, v)
        if not pts:
            return None
        ws = [math.exp(-(t - pt) / self.tau) for pt, _ in pts] if self.tau else None
        return quad_at_end(pts, ws)


class TheilSen:
    """Theil–Sen: the median of the slopes of every pair of readings in the last `w` s (robust to a single outlier)."""

    def __init__(self, w: int = 600, max_gap: int = 150, min_n: int = 3):
        self.win = Window(w, min_n=min_n, max_gap=max_gap)

    def update(self, t, v):
        pts = self.win.push(t, v)
        if not pts:
            return None
        s = sorted(
            (pts[j][1] - pts[i][1]) * 60.0 / (pts[j][0] - pts[i][0])
            for i in range(len(pts))
            for j in range(i + 1, len(pts))
        )
        n = len(s)
        return s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2


class Holt:
    """Holt's double exponential smoothing for uneven steps: `alpha` and `beta` are per minute and scaled to the step
    (1 − (1 − a)^dt). Restarts after `max_gap` s or a LO / HI; reports once it has seen `warm` s of readings."""

    def __init__(self, alpha: float = 0.5, beta: float = 0.2, max_gap: int = 600, warm: int = 300):
        self.alpha, self.beta, self.max_gap, self.warm = alpha, beta, max_gap, warm
        self.reset()

    def reset(self):
        self.level = self.slope = self.t = self.t_start = None

    def update(self, t, v):
        if v is None:
            self.reset()
            return None
        if self.t is None or t - self.t > self.max_gap:
            self.level, self.slope, self.t, self.t_start = v, 0.0, t, t
            return None
        dt = (t - self.t) / 60.0
        a = 1 - (1 - self.alpha) ** dt
        b = 1 - (1 - self.beta) ** dt
        pred = self.level + self.slope * dt
        new_level = a * v + (1 - a) * pred
        self.slope = b * (new_level - self.level) / dt + (1 - b) * self.slope
        self.level, self.t = new_level, t
        return self.slope if t - self.t_start >= self.warm else None


class Kalman:
    """Constant-velocity Kalman filter: state (glucose, rate), white-noise acceleration of spectral density `q`
    ((mg/dL/min²)² per min), reading noise `r` (mg/dL)². Restarts after `max_gap` s or a LO / HI; reports once the
    rate's standard deviation is below `max_sd` mg/dL/min (its warm-up, and after a restart)."""

    def __init__(self, q: float = 0.005, r: float = 1.0, max_gap: int = 600, max_sd: float = 0.5):
        self.q, self.r, self.max_gap, self.max_sd = q, r, max_gap, max_sd
        self.reset()

    def reset(self):
        self.x = None  # [g, rate]
        self.p = None  # [[p00 p01][p01 p11]]
        self.t = None

    def update(self, t, v):
        if v is None:
            self.reset()
            return None
        if self.x is None or t - self.t > self.max_gap:
            self.x = [v, 0.0]
            self.p = [[self.r, 0.0], [0.0, 4.0]]  # rate unknown: sd 2 mg/dL/min
            self.t = t
            return None
        dt = (t - self.t) / 60.0
        g, rt = self.x
        p00, p01, p11 = self.p[0][0], self.p[0][1], self.p[1][1]
        # predict: F = [[1 dt][0 1]], Q = q·[[dt³/3 dt²/2][dt²/2 dt]]
        g = g + rt * dt
        p00 = p00 + 2 * dt * p01 + dt * dt * p11 + self.q * dt ** 3 / 3
        p01 = p01 + dt * p11 + self.q * dt ** 2 / 2
        p11 = p11 + self.q * dt
        # update with the reading
        s = p00 + self.r
        k0, k1 = p00 / s, p01 / s
        y = v - g
        g, rt = g + k0 * y, rt + k1 * y
        p00, p01, p11 = (1 - k0) * p00, (1 - k0) * p01, p11 - k1 * p01
        self.x, self.p, self.t = [g, rt], [[p00, p01], [p01, p11]], t
        return rt if p11 <= self.max_sd ** 2 else None


TAU_S = 180  # the recommended weighting: 3 min
WINDOW_TAUS = 5  # the window: 5 tau
MIN_SPAN_S = 180  # at least 3 min of readings ...
MIN_READINGS = 2  # ... at least two of them
MAX_STEP_S = 750  # a longer step between two readings starts over (2.5 readings of a 5-min sensor)


def reference(readings: List[Tuple[int, Optional[float]]], tau: int = TAU_S) -> Optional[float]:
    """The recommendation as the pure function the apps would run on every reading: [readings] = (epoch s, mg/dL or
    None for LO / HI) in any order; the rate (mg/dL/min) at the newest reading, or None. Walks back from the newest
    reading while readings are within 5 tau of it, stopping at a LO / HI or a step over 12.5 min; needs at least two
    readings spanning at least 3 min. Weights exp(−age / tau), time in seconds from the newest reading, sums oldest
    first, then Σw·(dt − t̄)·(v − v̄) × 60 / Σw·(dt − t̄)². Same readings, same number: no state, so a restart, a backfilled
    reading or a follower with the same readings gets the same trend."""
    by_t = {}
    for t, v in readings:
        by_t[t] = v  # one reading per time: the last one given wins
    ts = sorted(by_t)
    if not ts or by_t[ts[-1]] is None:
        return None
    t0 = ts[-1]
    w: List[Reading] = [(t0, by_t[t0])]
    for i in range(len(ts) - 2, -1, -1):
        t = ts[i]
        if t0 - t >= WINDOW_TAUS * tau or by_t[t] is None or ts[i + 1] - t > MAX_STEP_S:
            break
        w.append((t, by_t[t]))
    w.reverse()
    if len(w) < MIN_READINGS or t0 - w[0][0] < MIN_SPAN_S:
        return None
    return lsq(w, [math.exp(-(t0 - t) / tau) for t, _ in w])


def recommended(tau: int = 180) -> "WeightedLeastSquares":
    """Investigation 34's recommendation: least squares weighted exp(−age / tau) over the readings of the last
    5 tau (tau = 3 min: 15 min), at least two readings spanning at least 3 min, a step over 12.5 min (2.5 readings of a
    5-min sensor) or a LO / HI starting over. The same numbers for 1-min and 5-min sensors."""
    return WeightedLeastSquares(WINDOW_TAUS * tau, tau, max_gap=MAX_STEP_S, min_n=MIN_READINGS, min_span=MIN_SPAN_S)


class SensorRate:
    """The sensor's own rate (Abbott's for Libre 3), passed through: the reference."""

    def update(self, t, v, rate=None):
        return rate


def run(est, ts: List[int], vs: List[Optional[float]], rates: Optional[List[Optional[float]]] = None):
    """The estimator over a whole series: one rate (or None) per reading."""
    if isinstance(est, SensorRate):
        return list(rates) if rates else [None] * len(ts)
    return [est.update(t, v) for t, v in zip(ts, vs)]
