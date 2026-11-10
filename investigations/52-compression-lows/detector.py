# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compression-low detector (investigation 52): reads one reading at a time, never changes an alert.

Two stages, both only annotations:

1. **Suspect** (at the fall): the reading fell at least `min_drop` mg/dL within `fall_window` min, at a rate faster
   than `min_fall_rate` somewhere in that fall, starting from a level that wasn't already falling, with no rapid
   insulin logged in the last `insulin_lookback` min (insulin makes a fast real fall plausible). With `min_jump`, the
   fall must also start abruptly: two 5-min rates in a row that differ by `min_jump` mg/dL/min or more (Baysal 2014's
   slope ratio; glucose itself can't change its rate faster than ~0.1-0.2 mg/dL/min², so a real fall speeds up over
   10-30 min, while pressure switches the reading's slope within minutes).
2. **Compression** (after the rebound): the reading came back fast (a stretch of `min_rise_rate` or faster) to within
   `recover_margin` of the level before the fall, above the low threshold by `above_low`, and stayed there without
   overshooting for `hold` min, within `max_episode` min of the fall, with no carbs logged since the fall started
   (carbs explain a rise: that dip was a real low, treated). If the dip went below the low threshold (the alert
   rang), the rise must have started within `max_wait_after_alert` min of the first low reading: someone the alarm
   wakes turns over at once and a pressure dip comes straight back, while carbs need 15-30 min to show on a sensor.

Never labelled: a dip that comes back only to a low value (pressure on top of a real low), one followed by carbs, one
that stays low for a while after the alert, or one that takes longer than `max_episode`.
"""

from collections import deque
from dataclasses import dataclass, field
from statistics import median
from typing import List, Optional, Tuple


@dataclass
class Params:
    fall_window: float = 20.0  # min: where the level before the fall is looked for
    min_drop: float = 25.0  # mg/dL from that level to now
    min_fall_rate: float = 2.0  # mg/dL/min over the steepest ~5 min of the fall
    min_jump: Optional[float] = 2.0  # mg/dL/min between two 5-min rates in a row (None: no onset test)
    max_pre_slope: float = -0.8  # mg/dL/min: the 15 min before the fall were not already falling faster than this
    insulin_lookback: float = 180.0  # min
    recover_margin: float = 15.0  # mg/dL (or 12 % of the level, whichever is larger)
    above_low: float = 10.0  # mg/dL above the low threshold
    min_rise_rate: float = 1.5  # mg/dL/min over the steepest ~5 min of the rise
    overshoot: float = 35.0  # mg/dL above the level before the fall: a rise like that looks like carbs
    hold: float = 10.0  # min the recovered reading must stay in the band (an overshoot shows by then)
    max_episode: float = 150.0  # min from the fall to the recovery
    max_wait_after_alert: Optional[float] = 10.0  # min from the first low reading to the start of the rise (None: off)


@dataclass
class Output:
    kind: str  # suspect | compression | dropped
    t: float  # minute it was decided
    start: float  # the fall's start (the level's minute)
    level: float  # mg/dL before the fall
    nadir: float
    detail: dict = field(default_factory=dict)


@dataclass
class _Dip:
    start: float
    level: float
    fall_rate: float
    nadir: float
    nadir_t: float
    recovered_at: Optional[float] = None
    rise_rate: float = 0.0
    first_low: Optional[float] = None  # the first reading below the threshold in this dip (when the alert rang)


def _steepest(points: List[Tuple[float, float]], span: float, sign: int) -> float:
    """The steepest change over ~`span` min (sign -1: fall, +1: rise), as a positive mg/dL/min; 0 if none.
    Each reading is paired with the first one at least `span` min later (with 5-min data: the next reading)."""
    best = 0.0
    j = 0
    for i, (ti, vi) in enumerate(points):
        j = max(j, i + 1)
        while j < len(points) and points[j][0] - ti < span - 0.01:
            j += 1
        if j == len(points):
            break
        tj, vj = points[j]
        best = max(best, sign * (vj - vi) / (tj - ti))
    return best


def _jump(points: List[Tuple[float, float]], fall_rate: float) -> float:
    """How abruptly the fall started: the largest drop from one 5-min rate to the next 5-min rate (mg/dL/min), among
    the steps where the reading falls faster than `fall_rate`; 0 if none. A 5-min rate is the least-squares slope of
    the readings in those 5 min (1-min data: 6 readings, so noise doesn't fake a jump; 5-min data: the difference of
    two readings)."""
    rates = []  # (minute, slope over the 5 min up to it)
    for i, (ti, _) in enumerate(points):
        seg = [x for x in points[: i + 1] if x[0] >= ti - 5.01]
        if len(seg) >= 2 and seg[-1][0] - seg[0][0] >= 4.99:
            rates.append((ti, _slope(seg)))
    best = 0.0
    for t, r in rates:
        if r > -fall_rate:
            continue
        prev = [x for x in rates if t - 5.01 <= x[0] <= t - 4.99]
        if prev:
            best = max(best, prev[-1][1] - r)
    return best


def _slope(points: List[Tuple[float, float]]) -> float:
    """Least-squares slope in mg/dL/min (0 with fewer than 2 points)."""
    if len(points) < 2:
        return 0.0
    n = len(points)
    mt = sum(t for t, _ in points) / n
    mv = sum(v for _, v in points) / n
    den = sum((t - mt) ** 2 for t, _ in points)
    return 0.0 if den == 0 else sum((t - mt) * (v - mv) for t, v in points) / den


class Detector:
    def __init__(self, low: float = 70.0, params: Optional[Params] = None):
        self.low = low
        self.p = params or Params()
        self.history: deque = deque()
        self.dip: Optional[_Dip] = None
        self.after = -1e9  # a new fall starts only after the last dip ended (labelled or dropped)

    def _since(self, t0: float) -> List[Tuple[float, float]]:
        """The readings from minute t0 on (newest last)."""
        out = []
        for x in reversed(self.history):
            if x[0] < t0:
                break
            out.append(x)
        out.reverse()
        return out

    def feed(self, t: float, value: Optional[float], treatments: List[Tuple[float, str, float]]) -> List[Output]:
        """One reading (None = missed) with the treatments logged so far. Returns what was decided now."""
        if value is None:
            return []
        p = self.p
        self.history.append((t, float(value)))
        while self.history[0][0] < t - 240:
            self.history.popleft()
        out: List[Output] = []
        if self.dip is None:
            window = self._since(max(t - p.fall_window, self.after))
            if len(window) < 2:
                return out
            pt, pv = max(window, key=lambda x: (x[1], x[0]))
            drop = pv - value
            if drop < p.min_drop:
                return out
            fall = _steepest([x for x in window if x[0] >= pt], 5.0, -1)
            if fall < p.min_fall_rate:
                return out
            if p.min_jump is not None and _jump(self._since(pt - 10), p.min_fall_rate) < p.min_jump:
                return out
            before = [x for x in self._since(pt - 15) if x[0] <= pt]
            if len(before) >= 2 and _slope(before) < p.max_pre_slope:
                return out
            if any(k == "insulin" and t - p.insulin_lookback <= tt <= t for tt, k, _ in treatments):
                return out
            level = median(hv for _, hv in before) if before else pv
            self.dip = _Dip(pt, level, fall, value, t, first_low=t if value < self.low else None)
            out.append(Output("suspect", t, pt, level, value, {"drop": drop, "fall_rate": round(fall, 2)}))
            return out

        d = self.dip
        if value < self.low and d.first_low is None:
            d.first_low = t
        if value < d.nadir:
            d.nadir, d.nadir_t = value, t
            d.recovered_at = None
        why = None
        if any(k == "carbs" and tt >= d.start - 10 for tt, k, _ in treatments if tt <= t):
            why = "carbs"
        elif t - d.start > p.max_episode:
            why = "too long"
        elif value > d.level + p.overshoot:
            why = "overshoot"
        if why:
            self.dip, self.after = None, t
            return [Output("dropped", t, d.start, d.level, d.nadir, {"why": why})]
        margin = max(p.recover_margin, 0.12 * d.level)
        recovered = value >= d.level - margin and value >= self.low + p.above_low
        if not recovered:
            d.recovered_at = None
            return out
        if d.recovered_at is None:
            since_nadir = self._since(d.nadir_t)
            rise = _steepest(since_nadir, 5.0, +1)
            if rise < p.min_rise_rate:
                return out
            rise_start = max(x[0] for x in since_nadir if x[1] <= d.nadir + 5)
            if p.max_wait_after_alert is not None and d.first_low is not None \
                    and rise_start - d.first_low > p.max_wait_after_alert:
                self.dip, self.after = None, t
                return [Output("dropped", t, d.start, d.level, d.nadir, {"why": "low for a while after the alert"})]
            d.recovered_at, d.rise_rate = t, rise
        if t - d.recovered_at >= p.hold:
            self.dip, self.after = None, t
            out.append(Output("compression", t, d.start, d.level, d.nadir,
                              {"nadir_t": d.nadir_t, "fall_rate": round(d.fall_rate, 2),
                               "rise_rate": round(d.rise_rate, 2), "back_at": d.recovered_at}))
        return out


def run(readings, treatments, low: float = 70.0, params: Optional[Params] = None) -> List[Output]:
    """The detector over a whole trace, treatments revealed as they are logged."""
    det = Detector(low, params)
    out: List[Output] = []
    for t, v in readings:
        out += det.feed(t, v, [x for x in treatments if x[0] <= t])
    return out
