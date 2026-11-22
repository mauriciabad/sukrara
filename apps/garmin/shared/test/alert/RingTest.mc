// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Test;

// The ring decision every app makes (shared/alert Ring): what rings at a watch time, what is new since the kinds an app
// last announced, what logging answers it. apps/garmin/tools/link-test.sh app | field.
(:test)
function ringOnlyWhileFresh(logger as Logger) as Boolean {
    var t = 1794823776;
    var low = {"k" => "low", "side" => "low", "relaxed" => false, "less" => true};
    var nodata = {"k" => "no_data", "side" => "none", "relaxed" => false, "less" => false};
    var p = {"g" => {"t" => t, "v" => 62}, "ring" => [low, nodata]};
    Test.assertEqual((Ring.kinds(Ring.now(p, t + 60, 0))).toString(), (["low", "no_data"]).toString());
    Test.assertEqualMessage((Ring.kinds(Ring.now(p, t + Glucose.STRUCK_S, 0))).toString(), (["low", "no_data"]).toString(), "5 min: still fresh");
    Test.assertEqualMessage((Ring.kinds(Ring.now(p, t + Glucose.STRUCK_S + 1, 0))).toString(), (["no_data"]).toString(), "aged: only no data");
    Test.assertEqualMessage((Ring.kinds(Ring.now(p, t + 200, 120))).toString(), (["no_data"]).toString(), "the phone's clock ahead: older");
    Test.assertEqualMessage(Ring.now({"g" => null, "ring" => [nodata]}, t, 0).size(), 1, "no reading: no data rings");
    Test.assertEqualMessage(Ring.now({"g" => null, "ring" => [low]}, t, 0).size(), 0, "no reading: nothing else");
    Test.assertEqualMessage(Ring.now(null, t, 0).size(), 0, "no payload");
    Test.assertEqualMessage(Ring.now({"g" => {"t" => t, "v" => 62}}, t, 0).size(), 0, "the face's profile: no ring");
    return true;
}

(:test)
function ringNewKinds(logger as Logger) as Boolean {
    var low = {"k" => "low", "side" => "low"};
    var falling = {"k" => "falling_fast", "side" => "low"};
    Test.assertEqualMessage((Ring.kinds(Ring.fresh([low], []))).toString(), (["low"]).toString(), "first seen: new");
    Test.assertEqualMessage(Ring.fresh([low], ["low"]).size(), 0, "still ringing: not again");
    Test.assertEqualMessage((Ring.kinds(Ring.fresh([low, falling], ["low"]))).toString(), (["falling_fast"]).toString(), "another kind: new");
    Test.assertEqualMessage((Ring.kinds(Ring.fresh([low], ["very_low"]))).toString(), (["low"]).toString(), "very low went, low came: new");
    Test.assertEqual(Ring.treatment("low"), "carbs");
    Test.assertEqual(Ring.treatment("high"), "rapid");
    Test.assertMessage(Ring.treatment("none") == null, "no data: none");
    return true;
}
