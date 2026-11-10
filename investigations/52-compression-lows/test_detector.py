# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
"""The detector's rules on hand-made traces: python3 -I investigations/52-compression-lows/test_detector.py"""

import math
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import detector  # noqa: E402


def v_shape(level=140.0, depth=85.0, tau=8.0, onset=30, release=55, floor=None, after=None, minutes=150, step=1):
    """A first-order fall at `onset`, a return at `release` (to `after`, default the level)."""
    out = []
    at_release = depth * (1 - math.exp(-(release - onset) / tau))
    back_to = level if after is None else after
    for t in range(0, minutes, step):
        if t < onset:
            v = level
        elif t < release:
            v = level - depth * (1 - math.exp(-(t - onset) / tau))
        else:
            low = level - at_release
            v = back_to - (back_to - low) * math.exp(-(t - release) / tau)
        if floor is not None:
            v = max(v, floor) if t < onset else v
        out.append((t, round(v)))
    return out


def kinds(outs):
    return [o.kind for o in outs]


class DetectorTest(unittest.TestCase):
    def test_pressure_dip_that_comes_back_is_labelled_after_the_rebound(self):
        outs = detector.run(v_shape(release=48), [])  # below 70 from ~44: the alarm wakes them, they turn at 48
        self.assertEqual(kinds(outs), ["suspect", "compression"])
        suspect, label = outs
        self.assertLess(suspect.t, 40)  # flagged during the fall...
        self.assertGreater(label.t, 48)  # ...labelled only once it came back
        self.assertGreaterEqual(label.detail["back_at"], 48)

    def test_five_minute_readings(self):
        self.assertEqual(kinds(detector.run(v_shape(release=48, step=5), [])), ["suspect", "compression"])

    def test_carbs_logged_after_the_fall_mean_a_real_low_treated(self):
        outs = detector.run(v_shape(release=48), [(40, "carbs", 15)])
        self.assertEqual(kinds(outs), ["suspect", "dropped"])
        self.assertEqual(outs[1].detail["why"], "carbs")

    def test_a_dip_that_comes_back_only_to_a_low_value_is_never_labelled(self):
        # pressure on top of a real low: back to 66, below the low threshold
        outs = detector.run(v_shape(level=95, depth=50, after=66, release=48), [])
        self.assertNotIn("compression", kinds(outs))

    def test_back_just_above_the_threshold_is_not_enough(self):
        # back to 75: not low any more, but not 10 above the threshold and far from the level before the fall
        outs = detector.run(v_shape(level=130, depth=70, after=75, release=48), [])
        self.assertNotIn("compression", kinds(outs))

    def test_a_rise_far_above_the_level_looks_like_carbs(self):
        outs = detector.run(v_shape(level=120, depth=60, after=190, release=48), [])
        self.assertEqual(kinds(outs)[-1], "dropped")
        self.assertEqual(outs[-1].detail["why"], "overshoot")

    def test_rapid_insulin_in_the_last_three_hours_makes_a_fast_fall_plausible(self):
        outs = detector.run(v_shape(release=48), [(-60, "insulin", 2)])
        self.assertEqual(kinds(outs), [])

    def test_a_real_fall_that_speeds_up_gradually_is_not_suspected(self):
        # glucose can't change its rate faster than ~0.2 mg/dL/min²: 0 → 3 mg/dL/min over 15 min
        g, r, trace = 160.0, 0.0, []
        for t in range(120):
            if t > 20 and g > 55:
                r = min(3.0, r + 0.2)
                g -= r
            trace.append((t, round(g)))
        self.assertEqual(kinds(detector.run(trace, [])), [])

    def test_without_the_onset_test_the_same_real_fall_is_suspected(self):
        g, r, trace = 160.0, 0.0, []
        for t in range(120):
            if t > 20 and g > 55:
                r = min(3.0, r + 0.2)
                g -= r
            trace.append((t, round(g)))
        self.assertIn("suspect", kinds(detector.run(trace, [], params=detector.Params(min_jump=None))))

    def test_a_slow_dip_is_never_suspected(self):
        trace = [(t, round(140 - (0.8 * (t - 20) if 20 <= t < 100 else (64 if t >= 100 else 0)))) for t in range(160)]
        self.assertEqual(kinds(detector.run(trace, [])), [])

    def test_a_dip_that_stays_low_for_a_while_after_the_alert_looks_treated(self):
        # below 70 from about minute 44, coming back only at 70: the alarm woke them and they ate (not logged)
        outs = detector.run(v_shape(release=70), [])
        self.assertEqual(kinds(outs), ["suspect", "dropped"])
        self.assertEqual(outs[1].detail["why"], "low for a while after the alert")

    def test_a_dip_that_never_reached_the_threshold_can_take_its_time(self):
        outs = detector.run(v_shape(level=150, depth=60, release=100), [])
        self.assertEqual(kinds(outs), ["suspect", "compression"])

    def test_pressure_held_longer_than_the_episode_limit_is_dropped(self):
        outs = detector.run(v_shape(release=200, minutes=260), [], params=detector.Params(max_wait_after_alert=None))
        self.assertEqual(kinds(outs), ["suspect", "dropped"])
        self.assertEqual(outs[1].detail["why"], "too long")


if __name__ == "__main__":
    unittest.main()
