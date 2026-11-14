# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The estimators on hand-made readings (investigation 34). python3 -I test_methods.py"""

import math
import os
import random
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # also under python3 -I, from any directory
import methods as M  # noqa: E402

T0 = 1_800_000_000


def series(f, minutes, step=60, jitter=0):
    rnd = random.Random(1)
    ts = [T0 + i * step + (rnd.randint(-jitter, jitter) if jitter and i else 0) for i in range(minutes * 60 // step + 1)]
    return ts, [f((t - T0) / 60) for t in ts]


class Ramps(unittest.TestCase):
    def test_every_window_method_reads_a_straight_line_exactly(self):
        ts, vs = series(lambda m: 100 + 2 * m, 30, jitter=3)
        for est in [M.LeastSquares(300), M.LeastSquares(900), M.WeightedLeastSquares(900, 180), M.LocalQuadratic(900),
                    M.TheilSen(600), M.recommended()]:
            self.assertAlmostEqual(M.run(est, ts, vs)[-1], 2.0, places=9, msg=type(est).__name__)

    def test_deltas_holt_and_kalman_converge_on_a_line(self):
        ts, vs = series(lambda m: 200 - 1.5 * m, 60)
        for est in [M.Delta(300), M.AapsDeltas("delta"), M.AapsDeltas("short"), M.Holt(0.5, 0.2), M.Kalman(0.01)]:
            self.assertAlmostEqual(M.run(est, ts, vs)[-1], -1.5, places=3, msg=type(est).__name__)

    def test_least_squares_lags_half_its_window_when_the_rate_changes(self):
        # glucose accelerating at 0.1 mg/dL/min²: the slope at minute m is 0.2 m
        ts, vs = series(lambda m: 100 + 0.1 * m * m, 30)
        ls15 = M.run(M.LeastSquares(900), ts, vs)[-1]
        self.assertAlmostEqual(ls15, 0.2 * (30 - 7), places=6)  # the slope 7 min ago (window 14 min of readings)
        quad = M.run(M.LocalQuadratic(900), ts, vs)[-1]
        self.assertAlmostEqual(quad, 0.2 * 30, places=6)  # the parabola has no lag on a parabola
        rec = M.run(M.recommended(), ts, vs)[-1]
        self.assertAlmostEqual(30 - rec / 0.2, 4.5, delta=0.05)  # recency weights (tau 3): 4.5 min behind, not 7

    def test_five_minute_readings_use_the_same_numbers(self):
        ts, vs = series(lambda m: 150 - 1.0 * m, 30, step=300)
        self.assertAlmostEqual(M.run(M.recommended(), ts, vs)[-1], -1.0, places=9)


class Rules(unittest.TestCase):
    def test_reference_and_streaming_agree(self):
        rnd = random.Random(7)
        ts, vs = series(lambda m: 120 + 30 * math.sin(m / 20), 240, jitter=2)
        vs = [round(v + rnd.gauss(0, 1.5)) for v in vs]
        for k, n in reversed([(40, 2), (60, 4), (80, 6), (100, 8), (120, 10), (140, 12), (170, 14)]):  # holes (min)
            del ts[k:k + n], vs[k:k + n]
        vs[150] = None  # a LO
        stream = M.run(M.recommended(), ts, vs)
        for i in range(len(ts)):
            ref = M.reference(list(zip(ts[: i + 1], vs[: i + 1])))
            if stream[i] is None or ref is None:
                self.assertEqual(stream[i], ref, i)
            else:
                self.assertAlmostEqual(stream[i], ref, places=12)

    def test_needs_three_minutes_of_readings(self):
        self.assertIsNone(M.reference([(T0, 100), (T0 + 60, 101), (T0 + 120, 102)]))
        self.assertAlmostEqual(M.reference([(T0, 100), (T0 + 60, 101), (T0 + 120, 102), (T0 + 180, 103)]), 1.0)
        self.assertAlmostEqual(M.reference([(T0, 100), (T0 + 300, 95)]), -1.0)  # a 5-min sensor's second reading

    def test_a_long_step_or_a_lo_starts_over(self):
        base = [(T0 + i * 60, 100 + i) for i in range(10)]
        later = [(T0 + 9 * 60 + 751 + i * 60, 200) for i in range(3)]
        self.assertIsNone(M.reference(base + later))  # 12.5 min hole, then 2 min of readings
        lo = [(T0 + 10 * 60, None)] + [(T0 + (11 + i) * 60, 100) for i in range(3)]
        self.assertIsNone(M.reference(base + lo))
        self.assertIsNone(M.reference(base + [(T0 + 10 * 60, None)]))  # newest is LO

    def test_order_and_duplicates_dont_matter(self):
        r = [(T0 + i * 60, 100 + 2 * i) for i in range(10)]
        self.assertEqual(M.reference(r), M.reference(list(reversed(r)) + [r[3]]))


if __name__ == "__main__":
    unittest.main()
