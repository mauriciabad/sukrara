# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The two holds the #204 build chose from data (docs/34 "In Sukrara"): when the arrow doubles, and when the falling /
rising fast alerts ring on the trend from the readings. Aggregates only, from a readings file you point it at.

    python3 -I holds.py READINGS.csv [THRESHOLD]     THRESHOLD = the fast alerts' rate, default 2.0

Doubled arrow: entered when the rate was >= 3 at every reading of the last N min (left with the 0.2 hysteresis); per
day the doubled arrows, those with no hindsight move >= 2.5 within 3 readings ("false"), the minutes doubled, and the
delay / misses against hindsight moves >= 3 held >= 3 min. Fast alerts: the rule's alert episodes a day (an episode ends
after 30 min without a match, as the engine's), those with no hindsight move >= threshold - 0.5 within 5 min, and the
delay / misses against hindsight moves >= threshold held >= 5 min (merged 30 min apart).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # also under python3 -I, from any directory
import evaluate as E  # noqa: E402
import methods as M  # noqa: E402

MAX_STEP = 750


def main():
    ts, vs, rs = E.load(sys.argv[1])
    thr = float(sys.argv[2]) if len(sys.argv) > 2 else 2.0
    days = (ts[-1] - ts[0]) / 86400
    tru7 = E.truth(ts, vs, half=7 * 60)
    tru10 = E.truth(ts, vs, half=10 * 60)
    series = {"sensor": rs}
    for tau in (2, 3, 5):
        series[f"tau {tau}"] = M.run(M.recommended(tau * 60), ts, vs, rs)

    def back(i, hold):
        """indices back to the first reading at or before ts[i] - hold, no step over MAX_STEP; None if not reached"""
        out, j = [i], i
        while ts[i] - ts[j] < hold:
            if j == 0 or ts[j] - ts[j - 1] > MAX_STEP:
                return None
            j -= 1
            out.append(j)
        return out

    def runs(flags):
        out, s = [], None
        for i, f in enumerate(flags + [False]):
            if f and s is None:
                s = i
            if not f and s is not None:
                out.append((s, i - 1))
                s = None
        return out

    def truth_events(sgn, level, held, merge=0):
        ev, s = [], None
        for i, x in enumerate(tru10 + [None]):
            ok = x is not None and sgn * x >= level and (s is None or ts[i] - ts[i - 1] <= 150)
            if ok and s is None:
                s = i
            if not ok and s is not None:
                if ts[i - 1] - ts[s] >= held * 60:
                    ev.append((s, i - 1))
                s = None
                if x is not None and sgn * x >= level:
                    s = i
        if merge:
            m = []
            for a, b in ev:
                if m and ts[a] - ts[m[-1][1]] <= merge:
                    m[-1] = (m[-1][0], b)
                else:
                    m.append((a, b))
            ev = m
        return ev

    def delay(starts, events, after):
        ds, missed = [], 0
        for a, b in events:
            hit = [i for i in starts if ts[a] - 300 <= ts[i] <= ts[b] + after]
            if hit:
                ds.append((ts[hit[0]] - ts[a]) / 60)
            else:
                missed += 1
        ds.sort()
        return (ds[len(ds) // 2] if ds else float("nan")), (ds[int(0.9 * (len(ds) - 1))] if ds else float("nan")), missed

    print(f"{len(ts)} readings over {days:.2f} days")
    ev3 = {s: truth_events(s, 3.0, 3) for s in (1, -1)}
    print(f"\ndoubled arrow (hindsight moves >= 3 held >= 3 min: {sum(map(len, ev3.values()))}, {sum(map(len, ev3.values())) / days:.1f} a day)")
    for name, ser in series.items():
        if name == "sensor":
            continue
        for hold in (0, 1, 2, 3):
            flags, prev = [], False
            for i, x in enumerate(ser):
                if x is None:
                    flags.append(False)
                    prev = False
                    continue
                on = abs(x) >= (2.8 if prev else 3.0)
                if on and not prev and hold:
                    idx = back(i, hold * 60)
                    on = idx is not None and all(ser[k] is not None and abs(ser[k]) >= 3 and (ser[k] > 0) == (x > 0) for k in idx)
                flags.append(on)
                prev = on
            eps = runs(flags)
            false = sum(1 for a, b in eps if not any(tru7[k] is not None and (tru7[k] >= 2.5 if ser[a] > 0 else tru7[k] <= -2.5)
                                                   for k in range(max(0, a - 3), min(len(ts), b + 4))))
            med, p90, missed = delay([a for a, _ in eps], [e for s in (1, -1) for e in ev3[s]], 300)
            print(f"  {name} held {hold} min: {len(eps) / days:5.1f} a day, false {false / days:4.1f}, "
                  f"{sum(flags) / days:5.1f} min a day, delay {med:.0f} (p90 {p90:.0f}) min, missed {missed}")

    evs = {s: truth_events(s, thr, 5, merge=1800) for s in (1, -1)}
    print(f"\nfast alerts at {thr} (hindsight moves held >= 5 min: {sum(map(len, evs.values()))}, {sum(map(len, evs.values())) / days:.1f} a day)")
    for name, ser in series.items():
        for hold in (0, 2, 3, 5, 10):
            tot, dl, missed, false = 0, [], 0, 0
            for sgn in (1, -1):
                match = []
                for i, x in enumerate(ser):
                    if x is None or sgn * x < thr:
                        match.append(False)
                        continue
                    idx = back(i, hold * 60) if hold else [i]
                    match.append(idx is not None and all(ser[k] is not None and sgn * ser[k] >= thr for k in idx))
                starts, last = [], None
                for i, m in enumerate(match):
                    if m:
                        if last is None or ts[i] - last > 1800:
                            starts.append(i)
                        last = ts[i]
                tot += len(starts)
                for a, b in evs[sgn]:
                    hit = [i for i in starts if ts[a] - 300 <= ts[i] <= ts[b] + 300]
                    if hit:
                        dl.append((ts[hit[0]] - ts[a]) / 60)
                    else:
                        missed += 1
                false += sum(1 for i in starts if not any(tru7[k] is not None and sgn * tru7[k] >= thr - 0.5
                                                          for k in range(max(0, i - 8), min(len(ts), i + 8))
                                                          if abs(ts[k] - ts[i]) <= 300))
            dl.sort()
            med = dl[len(dl) // 2] if dl else float("nan")
            p90 = dl[int(0.9 * (len(dl) - 1))] if dl else float("nan")
            print(f"  {name} held {hold:2d} min: {tot / days:5.1f} a day, false {false / days:4.1f}, "
                  f"delay {med:.0f} (p90 {p90:.0f}) min, missed {missed}")


if __name__ == "__main__":
    main()
