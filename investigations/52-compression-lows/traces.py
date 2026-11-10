# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Synthetic CGM nights with known truth (investigation 52). No real health data: every trace is made up here.

A night is 23:00-07:00 (minute 0 = 23:00), one reading a minute (Libre 3) or every 5 min (Dexcom / Nightscout
followers). Three layers, so the truth is known:

1. blood glucose: a slow drift (waves, a dawn rise) plus the night's event, if it changes the glucose;
2. what the sensor measures: interstitial glucose, the blood glucose ~5-10 min late (a first-order lag);
3. what it reports: that, minus what pressure takes off (compression), plus smooth noise; LO / HI at the limits,
   missed readings.

Events, and how the person behaves (the low alert rings at the first reading below 70):
- compression: pressure lowers the reading, not the glucose. It falls fast, stays down while they lie on it, and comes
  back once they turn: by themselves, or because the alert woke them (most do, within minutes).
- true_low_slow / true_low_fast: the glucose itself falls (0.3-1.2 / 1.5-3.5 mg/dL/min) and stays low until treated:
  carbs eaten a few minutes after the alert (logged in the app or not), carbs eaten before the alert by someone
  already awake and watching it fall, or nothing (the body's counter-regulation brings it back, slowly).
- compression_on_low: pressure while the glucose is already sliding into a real low (the dangerous case).
- correction_fall: a fast fall from a high after a correction bolus that levels off in range (no low).

Ranges come from the sources in docs/33-compression-lows.md; every parameter is drawn at random, so the evaluation
sees many shapes, not one tuned case.
"""

import math
import random
from dataclasses import dataclass, field
from typing import List, Optional, Tuple

NIGHT_MIN = 8 * 60  # 23:00-07:00
LO, HI = 39, 401  # the sensor's "below / above my range" values (contracts/alerts/SPEC.md §1)
LOW = 70  # the default low alert (strictly below)


@dataclass
class Event:
    kind: str
    start: float  # minute the reading starts to fall
    end: float  # minute the reading is back, or the event is over
    detail: dict = field(default_factory=dict)


@dataclass
class Night:
    readings: List[Tuple[int, Optional[int]]]  # (minute, mg/dL or None = missed)
    treatments: List[Tuple[float, str, float]]  # (minute, "carbs" | "insulin", amount), as logged in the app
    events: List[Event]
    true: List[float]  # blood glucose each minute (plots only; the detector never sees it)
    step: int


def _lag(series: List[float], tau: float) -> List[float]:
    """First-order lag, one-minute steps: interstitial glucose follows blood glucose."""
    out, x = [], series[0]
    a = 1.0 - math.exp(-1.0 / tau)
    for v in series:
        x += a * (v - x)
        out.append(x)
    return out


def _first_below(series: List[float], start: float, threshold: float) -> Optional[int]:
    for t in range(max(0, int(start)), len(series)):
        if series[t] < threshold:
            return t
    return None


def _attenuation(n: int, onset: float, fall_rate: float, depth: float, release: float, rise_rate: float) -> List[float]:
    """How much pressure takes off the reading each minute: an exponential fall towards `depth` (fastest at the
    start, `fall_rate` mg/dL/min), held until `release`, then an exponential return (fastest at the start)."""
    fall_tau, rise_tau = depth / fall_rate, depth / rise_rate
    out = [0.0] * n
    at_release = depth * (1 - math.exp(-max(0.0, release - onset) / fall_tau))
    for t in range(int(math.ceil(onset)), n):
        if t < release:
            out[t] = depth * (1 - math.exp(-(t - onset) / fall_tau))
        else:
            out[t] = at_release * math.exp(-(t - release) / rise_tau)
    return out


class _Drift:
    """Slow drift: a few slow waves and a dawn rise, all well under 1 mg/dL/min."""

    def __init__(self, rng: random.Random, start: float):
        self.start = start
        self.waves = [(rng.uniform(4, 18), rng.uniform(120, 400), rng.uniform(0, 2 * math.pi)) for _ in range(2)]
        self.dawn = rng.uniform(0, 30) if rng.random() < 0.6 else 0.0

    def at(self, t: float) -> float:
        g = self.start + sum(a * math.sin(2 * math.pi * t / p + ph) - a * math.sin(ph) for a, p, ph in self.waves)
        if t > 300:  # 04:00 onwards
            g += self.dawn * min(1.0, (t - 300) / 180)
        return g


def make_night(rng: random.Random, kind: str, step: int = 1) -> Night:
    n = NIGHT_MIN + 1
    start = rng.uniform(220, 280) if kind == "correction_fall" else rng.uniform(95, 170)
    drift = _Drift(rng, start)
    bg = [drift.at(t) for t in range(n)]
    tau = rng.uniform(4, 9)  # blood → interstitial lag, min
    att = [0.0] * n
    events: List[Event] = []
    treatments: List[Tuple[float, str, float]] = []

    def isf() -> List[float]:
        return _lag(bg, tau)

    def compression(onset: float) -> Event:
        level = isf()[int(onset)]
        # a first-order response to a rectangle of pressure (Facchinetti 2016), the same time constant down and up
        tau_c = rng.uniform(5, 20)
        depth = min(rng.uniform(15, 90), max(12.0, level - 40 + rng.uniform(-5, 15)))  # a few reach LO
        fall_rate = rise_rate = depth / tau_c
        hold = min(180.0, max(10.0, rng.lognormvariate(math.log(45), 0.5)))  # median 45 min
        release = onset + hold  # turning over by themselves
        woke = False
        trial = _attenuation(n, onset, fall_rate, depth, release, rise_rate)
        signal = [i - a - b for i, a, b in zip(isf(), att, trial)]
        alert = _first_below(signal, onset, LOW)
        if alert is not None and alert < release and rng.random() < 0.75:
            release, woke = alert + rng.uniform(0.5, 4.0), True  # the alert woke them; they turned
        for t, a in enumerate(_attenuation(n, onset, fall_rate, depth, release, rise_rate)):
            att[t] += a
        return Event("compression", onset, release + 3 * tau_c,
                     {"depth": round(depth), "tau": round(tau_c, 1), "fall_rate": round(fall_rate, 1),
                      "hold": round(release - onset), "woke": woke, "alert": alert})

    def ramp(t0: float, g0: float, goal: float, top: float, acc: float) -> List[Tuple[int, float]]:
        """Glucose moving from g0 towards goal: the rate builds up and eases off at most `acc` mg/dL/min² (real
        glucose can't change its rate faster: ~0.1-0.2 mg/dL/min², Dexcom US 10,231,659), up to `top` mg/dL/min."""
        out, g, r, sign = [], g0, 0.0, (1 if goal > g0 else -1)
        t = int(t0)
        while t < n and (goal - g) * sign > 0.5:
            left = abs(goal - g)
            r = min(top, r + acc) if left > r * r / (2 * acc) else max(0.2, r - acc)
            g += sign * min(r, left)
            out.append((t, g))
            t += 1
        return out

    def true_low(onset: float, rate: float) -> Event:
        g0, nadir = bg[int(onset)], rng.uniform(42, 66)
        acc = rng.uniform(0.08, 0.2)
        fall = ramp(onset, g0, nadir, rate, acc)
        for t, g in fall:
            bg[t] = g
        bottom = fall[-1][0] if fall else int(onset)
        for t in range(bottom + 1, n):
            bg[t] = nadir + rng.uniform(-1, 1)
        how = rng.choices(["logged", "unlogged", "early_unlogged", "none"], [0.45, 0.3, 0.1, 0.15])[0]
        reading = isf()
        alert = _first_below(reading, onset, LOW)
        if how == "none":
            rise_at, rise, target = bottom + rng.uniform(40, 120), rng.uniform(0.3, 0.8), g0 + rng.uniform(-20, 10)
            carbs_at = None
        else:
            if how == "early_unlogged":  # awake, watching it fall, eats before the alert
                passing = _first_below(reading, onset, 90) or bottom
                carbs_at = passing + rng.uniform(0, 6)
            else:
                carbs_at = (alert if alert is not None else bottom) + rng.uniform(2, 10)
            rise_at = carbs_at + rng.uniform(5, 12)  # absorption; the sensor's lag comes on top
            rise, target = rng.uniform(1.5, 4.0), g0 + rng.uniform(-10, 60)
            if how == "logged":
                treatments.append((carbs_at, "carbs", rng.choice([10, 15, 20])))
        level = bg[int(min(rise_at, n - 1))]
        up = ramp(rise_at, level, target, rise, rng.uniform(0.1, 0.3))
        for t, g in up:
            bg[t] = g
        back = up[-1][0] if up else int(rise_at)
        for t in range(back + 1, n):
            bg[t] = target + (drift.at(t) - drift.at(back))
        return Event("true_low", onset, back + 2 * tau,
                     {"rate": round(rate, 1), "nadir": round(nadir), "treated": how, "alert": alert,
                      "carbs_at": carbs_at})

    if kind == "compression":
        t = rng.uniform(20, 120)
        for _ in range(rng.choice([1, 1, 1, 2, 2, 3])):
            if t > NIGHT_MIN - 90:
                break
            ev = compression(t)
            events.append(ev)
            t = ev.end + rng.uniform(20, 150)
    elif kind in ("true_low_slow", "true_low_fast"):
        slow = kind == "true_low_slow"
        rate = rng.uniform(0.3, 1.2) if slow else rng.uniform(1.5, 3.5)
        events.append(true_low(rng.uniform(30, 220) if slow else rng.uniform(60, 330), rate))
    elif kind == "compression_on_low":
        low = true_low(rng.uniform(60, 300), rng.uniform(0.4, 1.5))
        onset = low.start + (low.end - low.start) * rng.uniform(0.1, 0.35)  # while it slides into the low
        comp = compression(onset)
        low.kind = "compression_on_low"
        low.start = min(low.start, comp.start)
        low.detail["compression"] = comp.detail
        events.append(low)
    elif kind == "correction_fall":
        onset = rng.uniform(0, 60)
        if rng.random() < 0.7:
            treatments.append((max(0.0, onset - rng.uniform(20, 40)), "insulin", rng.choice([1, 2, 3])))
        g0, level, rate = bg[int(onset)], rng.uniform(110, 150), rng.uniform(1.5, 3.0)
        for t, g in ramp(onset, g0, level, rate, rng.uniform(0.08, 0.2)):
            bg[t] = g
        settled = int(onset + (g0 - level) / rate * 1.5)
        for t in range(min(n, settled), n):
            bg[t] = level + (drift.at(t) - drift.at(settled)) * 0.3
        events.append(Event("correction_fall", onset, onset + (g0 - level) / rate, {"rate": round(rate, 1)}))
    elif kind != "quiet":
        raise ValueError(kind)

    signal = isf()
    readings: List[Tuple[int, Optional[int]]] = []
    ar, gap_until = 0.0, -1
    for t in range(n):
        ar = 0.85 * ar + rng.gauss(0, 0.9)
        if t % step:
            continue
        if t < gap_until or rng.random() < 0.01:
            readings.append((t, None))
            continue
        if rng.random() < 0.002:
            gap_until = t + rng.randint(3, 12)
        v = signal[t] - att[t] + ar + rng.gauss(0, 0.8)
        readings.append((t, LO if v < 40 else HI if v > 400 else int(round(v))))
    return Night(readings, sorted(treatments), events, bg, step)
