# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""Evaluates the compression-low detector over synthetic nights (investigation 52).

    python3 investigations/52-compression-lows/evaluate.py            # the tables in docs/33
    python3 investigations/52-compression-lows/evaluate.py --sweep    # the rules on and off, other thresholds
    python3 investigations/52-compression-lows/evaluate.py --svg DIR  # example traces as SVG

What counts, per event (a night can hold several compressions, one of the others):
- alerted: the reading went below the low threshold (70) during the event, so the low alert rang;
- suspect at the alert: the fall stage had fired by the first reading below 70 (what a label shown *with* the alert
  would be based on);
- labelled: a "compression" output whose fall started within the event (the label after the rebound);
- for compression: minutes from the low alert to the label (how long the person is left wondering).
The dangerous mistakes are labels on true lows and on pressure on top of a true low; a missed compression only
means no reassurance (the alert behaves the same either way).
"""

import os
import random
import sys
from statistics import median
from typing import Dict, List

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # also under python3 -I, from any directory
import detector  # noqa: E402
import traces  # noqa: E402

KINDS = ["compression", "true_low_slow", "true_low_fast", "compression_on_low", "correction_fall", "quiet"]
LOW = 70


def _event_window(ev: traces.Event):
    if ev.kind == "compression_on_low":
        return ev.start, ev.end + 30
    return ev.start - 5, ev.end + 30


def evaluate(nights_per_kind: int = 400, step: int = 1, params=None, seed: int = 52) -> Dict[str, dict]:
    rng = random.Random(seed)
    stats: Dict[str, dict] = {}
    for kind in KINDS:
        s = {"events": 0, "alerted": 0, "suspect_at_alert": 0, "labelled": 0, "labelled_alerted": 0,
             "latency": [], "labels_outside_events": 0, "nights": nights_per_kind, "by_treatment": {},
             "labels_on_real_low": 0}
        for _ in range(nights_per_kind):
            night = traces.make_night(rng, kind, step)
            outs = detector.run(night.readings, night.treatments, LOW, params)
            labels = [o for o in outs if o.kind == "compression"]
            suspects = [o for o in outs if o.kind == "suspect"]
            used = set()
            for ev in night.events:
                s["events"] += 1
                a, b = _event_window(ev)
                lows = [t for t, v in night.readings if v is not None and a <= t <= b and v < LOW]
                alert_t = lows[0] if lows else None
                if alert_t is not None:
                    s["alerted"] += 1
                    if any(o.t <= b and o.t >= a and o.t <= alert_t for o in suspects):
                        s["suspect_at_alert"] += 1
                mine = [o for o in labels if o.t >= a and o.start <= b]  # overlaps the event
                used.update(id(o) for o in mine)
                if mine:
                    s["labelled"] += 1
                    if alert_t is not None:
                        s["labelled_alerted"] += 1
                        s["latency"].append(mine[0].t - alert_t)
                if ev.kind == "true_low":
                    tr = s["by_treatment"].setdefault(ev.detail["treated"], [0, 0])
                    tr[0] += 1
                    tr[1] += 1 if mine else 0
            s["labels_outside_events"] += sum(1 for o in labels if id(o) not in used)
            # the one that matters: a label over minutes when the blood glucose itself was below the threshold
            s["labels_on_real_low"] += sum(
                1 for o in labels if min(night.true[int(o.start):int(o.t) + 1] or [999]) < LOW)
        stats[kind] = s
    return stats


def _pct(n: int, d: int) -> str:
    return "—" if d == 0 else "%d / %d (%.0f %%)" % (n, d, 100.0 * n / d)


def report(stats: Dict[str, dict], title: str) -> str:
    rows = ["### " + title, "",
            "| Night kind | Events | Low alert rang | Suspect at the alert | Labelled | Labelled, of those that rang "
            "| Alert → label (median, min) | Labels over a real low | Labels outside events |",
            "|---|---|---|---|---|---|---|---|---|"]
    for kind in KINDS:
        s = stats[kind]
        lat = "%.0f" % median(s["latency"]) if s["latency"] else "—"
        rows.append("| %s | %d | %s | %s | %s | %s | %s | %d | %d in %d nights |" % (
            kind, s["events"], _pct(s["alerted"], s["events"]), _pct(s["suspect_at_alert"], s["alerted"]),
            _pct(s["labelled"], s["events"]), _pct(s["labelled_alerted"], s["alerted"]), lat, s["labels_on_real_low"],
            s["labels_outside_events"], s["nights"]))
    for kind in ("true_low_slow", "true_low_fast"):
        bt = stats[kind]["by_treatment"]
        rows.append("")
        rows.append("%s labelled, by treatment: %s" % (kind, ", ".join(
            "%s %s" % (k, _pct(v[1], v[0])) for k, v in sorted(bt.items()))))
    return "\n".join(rows)


def sweep(nights: int = 200) -> str:
    rows = ["### Thresholds (%d nights per kind; true lows = slow and fast together)" % nights, "",
            "| Readings | Fall rate ≥ | Abrupt onset ≥ | Rise within (min of the alert) | Compressions labelled | Real lows labelled (carbs not logged) "
            "| Labels over a real low (all kinds) | Suspect at the alert: real fast lows | Suspect at the alert: pressure on a real low |",
            "|---|---|---|---|---|---|---|---|---|"]
    for step in (1, 5):
        for rate, jump, wait in ((2.0, None, None), (2.0, 2.0, None), (2.0, None, 10.0), (2.0, 2.0, 10.0),
                                 (2.5, 2.0, 10.0), (3.0, 2.0, 10.0)):
            params = detector.Params(min_fall_rate=rate, min_jump=jump, max_wait_after_alert=wait)
            st = evaluate(nights, step, params, seed=7)
            c, o = st["compression"], st["compression_on_low"]
            f, sl = st["true_low_fast"], st["true_low_slow"]
            unlogged = [0, 0]
            for kind in (f, sl):
                for how in ("unlogged", "early_unlogged"):
                    n, lab = kind["by_treatment"].get(how, [0, 0])
                    unlogged[0] += n
                    unlogged[1] += lab
            rows.append("| %d min | %.1f | %s | %s | %s | %s | %d | %s | %s |" % (
                step, rate, "off" if jump is None else "%.1f" % jump, "off" if wait is None else "%.0f" % wait,
                _pct(c["labelled"], c["events"]),
                _pct(unlogged[1], unlogged[0]), sum(k["labels_on_real_low"] for k in st.values()),
                _pct(f["suspect_at_alert"], f["alerted"]), _pct(o["suspect_at_alert"], o["alerted"])))
    return "\n".join(rows)


def main(argv: List[str]) -> None:
    if "--svg" in argv:
        import plot
        plot.examples(argv[argv.index("--svg") + 1])
        return
    if "--sweep" in argv:
        print(sweep())
        return
    print(report(evaluate(400, 1), "Libre 3: a reading every minute (400 nights per kind)"))
    print()
    print(report(evaluate(400, 5), "Dexcom / Nightscout follower: a reading every 5 min (400 nights per kind)"))


if __name__ == "__main__":
    main(sys.argv[1:])
