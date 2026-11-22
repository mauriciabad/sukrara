// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

// The complications' values per state (Slots, SPEC §10), the expiry's next run (Expiry), and what a new alert's
// notification offers (Alerts.choices). apps/garmin/tools/link-test.sh app.
(:test)
module SlotCases {
    const T = 1794823776;

    // an app payload at the phone's time T with a reading [v] mg/dL taken [ago] s before, in [unit]
    function payload(v as Number, ago as Number, unit as String) as Dictionary {
        var hist = [] as Array;
        for (var i = 0; i < 288; i++) {
            hist.add(i == 287 ? 110 : (i == 286 ? null : 100));
        }
        return {"v" => SukraraFace.VERSION, "p" => "app", "sent" => T, "unit" => unit, "bands" => [55, 70, 140, 180, 250, 300],
            "g" => {"t" => T - ago, "v" => v, "rate" => 1.0, "band" => 1, "deg" => -45}, "hist" => hist, "st" => null,
            "sensor" => {"s" => "active"}, "ring" => [], "last" => null};
    }

    function take(p as Dictionary) as Void {
        Link.reset();
        Link.accept(p, T);
    }
}

(:test)
function slotsValue(logger as Logger) as Boolean {
    var T = SlotCases.T;
    SlotCases.take(SlotCases.payload(175, 60, "mgdl"));
    Test.assertEqual(Slots.value(Link.payload(), T), "175");
    Test.assertEqualMessage(Slots.value(Link.payload(), T + 239), "175", "4 min 59 s");
    Test.assertEqualMessage(Slots.value(Link.payload(), T + 240), "---", "old from 5 min");
    SlotCases.take(SlotCases.payload(175, 60, "mmol"));
    Test.assertEqual(Slots.value(Link.payload(), T), "9.7");
    SlotCases.take(SlotCases.payload(38, 0, "mgdl"));
    Test.assertEqual(Slots.value(Link.payload(), T), "LO");
    SlotCases.take(SlotCases.payload(420, 0, "mgdl"));
    Test.assertEqual(Slots.value(Link.payload(), T), "HI");
    var p = SlotCases.payload(120, 0, "mgdl");
    p.put("g", null);
    SlotCases.take(p);
    Test.assertEqualMessage(Slots.value(Link.payload(), T), "---", "no reading");
    p = SlotCases.payload(120, 0, "mgdl");
    p.put("sensor", {"s" => "warmup", "start" => T - 120, "ready" => T + 3000, "hold" => T + 3600});
    p.put("g", {"t" => T - 200, "v" => 120});
    SlotCases.take(p);
    Test.assertEqualMessage(Slots.value(Link.payload(), T), "---", "warming up");
    Link.reset();
    Test.assertEqualMessage(Slots.value(null, T), "---", "nothing from the phone");
    return true;
}

(:test)
function slotsDataAndGraph(logger as Logger) as Boolean {
    var T = SlotCases.T;
    SlotCases.take(SlotCases.payload(175, 60, "mgdl"));
    var d = Slots.data(Link.payload(), T);
    Test.assertEqual(d.size(), 9);
    Test.assertEqualMessage(d[0], 0.0, "mg/dL");
    Test.assertEqualMessage(d[1], 3.0, "band 1: forty-five up");
    Test.assertEqualMessage(([d[2], d[3], d[4], d[5]]).toString(), ([70.0, 180.0, 55.0, 250.0]).toString(), "low, high, very low, very high");
    // Floats, as CGM+ GB's ranges are: each time the largest Float not after the true one (128 s steps there)
    var times = [T - 60, T - 60 + Slots.OLD_S, T - 60 + Slots.TOO_OLD_S];
    for (var i = 0; i < 3; i++) {
        var f = d[6 + i] as Float;
        Test.assertMessage(f.toLong() <= times[i].toLong(), "time " + i + ": never later");
        Test.assertMessage(times[i].toLong() - f.toLong() < 128, "time " + i + ": under 128 s earlier");
    }
    var g = Slots.graph(Link.payload());
    Test.assertEqual(g.size(), 288);
    Test.assertEqualMessage(([g[0], g[1], g[2], g[3]]).toString(), ([175.0, 110.0, 0.0, 100.0]).toString(), "newest first, a gap is 0");
    SlotCases.take(SlotCases.payload(175, 60, "mmol"));
    d = Slots.data(Link.payload(), T);
    Test.assertEqualMessage(([d[0], d[2], d[3]]).toString(), ([1.0, 3.9, 10.0]).toString(), "mmol/L");
    Test.assertEqualMessage(Slots.graph(Link.payload())[0], 9.7, "the graph in mmol/L");
    Test.assertEqual(Slots.trend({"v" => 100, "band" => 3}), 1);
    Test.assertEqual(Slots.trend({"v" => 100, "band" => 0}), 4);
    Test.assertEqual(Slots.trend({"v" => 100, "band" => -3}), 7);
    Test.assertEqual(Slots.trend({"v" => 100, "band" => null}), 0);
    Test.assertEqual(Slots.trend({"v" => 39, "band" => null}), 9);
    return true;
}

// the Log complication: the watch's last treatment (the card's chain), as its setting says
(:test)
function slotsLog(logger as Logger) as Boolean {
    var T = SlotCases.T;
    var p = SlotCases.payload(120, 0, "mgdl");
    var rapid = {"k" => "rapid", "a" => 4.5, "t" => T - 3900, "m" => T + 600};
    var carbs = {"k" => "carbs", "a" => 50.0, "t" => T - 3900, "m" => T - 100};
    p.put("last", {"t" => T - 3900, "items" => [rapid, carbs], "until" => T + 6000, "next" => null});
    SlotCases.take(p);
    Test.assertEqual(Slots.log(Link.payload(), T, "since"), "1h 5m");
    Test.assertEqual(Slots.log(Link.payload(), T, "amount"), "4.5u");
    Test.assertEqual(Slots.log(Link.payload(), T, "both"), "4.5u 1h5m");
    Test.assertEqual(Slots.log(Link.payload(), T - 3900 + 720, "since"), "12m");
    Test.assertEqual(Slots.log(Link.payload(), T - 3900 + 7 * 3600, "since"), "Log");
    Test.assertEqualMessage(Slots.log(Link.payload(), T + 6000, "since"), "Log", "the chain's step ended: nothing");
    p.put("last", {"t" => T - 600, "items" => [{"k" => "carbs", "a" => 50.0, "t" => T - 600, "m" => T + 600}],
        "until" => -1, "next" => null});
    SlotCases.take(p);
    Test.assertEqual(Slots.log(Link.payload(), T, "amount"), "50g");
    Test.assertEqual(Durations.short(7 * 3600, " "), "7h");
    p.put("last", null);
    SlotCases.take(p);
    Test.assertEqual(Slots.log(Link.payload(), T, "both"), "Log");
    return true;
}

// the next background run: the reading's time + 5 min, never within 5 min of the last temporal run, never more than
// 5 min after this one
(:test)
function slotsExpiry(logger as Logger) as Boolean {
    var now = 100000;
    Test.assertEqualMessage(Expiry.next(now - 300, now - 60, now), now + 240, "a reading 1 min old: in 4 min");
    Test.assertEqualMessage(Expiry.next(now - 60, now - 200, now), now + 240, "Garmin's floor: 5 min after the last run");
    Test.assertEqualMessage(Expiry.next(now - 60, now - 30, now), now + 270, "the expiry after the floor");
    Test.assertEqualMessage(Expiry.next(now - 600, now - 900, now), now + 300, "already old: every 5 min");
    Test.assertEqualMessage(Expiry.next(null, null, now), now + 300, "nothing yet: in 5 min");
    Test.assertEqualMessage(Expiry.next(null, now, now), now + 300, "a reading just now: in 5 min");
    return true;
}

// what a run or the app's start registers (#217): the expiry, then 5 min from now, then the Duration, so a refused
// Moment never leaves the background without a next run; nothing when the right Moment stands
(:test)
function slotsExpiryPlan(logger as Logger) as Boolean {
    var now = 100000;
    var d = -1;
    Test.assertEqualMessage(Expiry.plan(null, now, now - 60, now).toString(), [now + 300, d].toString(),
        "a temporal run, nothing registered (its Moment ran): 5 min after it, else the Duration");
    Test.assertEqualMessage(Expiry.plan(null, null, now - 60, now).toString(), [now + 240, now + 300, d].toString(),
        "the app's start (the watch tells no last run): the expiry, else 5 min from now, else the Duration");
    Test.assertEqualMessage(Expiry.plan(d, now - 120, now - 30, now).toString(), [now + 270, now + 300, d].toString(),
        "a push while the Duration stands: the expiry replaces it");
    Test.assertEqualMessage(Expiry.plan(now + 270, now - 120, now - 30, now).size(), 0,
        "the expiry already registered: left alone");
    Test.assertEqualMessage(Expiry.plan(now + 200, now - 120, now - 30, now).toString(), [now + 270, now + 300, d].toString(),
        "a newer reading: its own expiry");
    Test.assertEqualMessage(Expiry.plan(now - 3600, null, null, now).toString(), [now + 300, d].toString(),
        "a Moment that never ran: 5 min from now");
    return true;
}

// what Garmin's notification offers for each ring item
(:test)
function slotsAlertChoices(logger as Logger) as Boolean {
    var t = Alerts.TREAT;
    var l = Alerts.LESS;
    var u = Alerts.USUAL;
    var m = Alerts.MUTE30;
    Test.assertEqual((Alerts.choices({"k" => "low", "side" => "low", "less" => true, "relaxed" => false})).toString(), ([t, l, m]).toString());
    Test.assertEqual((Alerts.choices({"k" => "low", "side" => "low", "less" => false, "relaxed" => true})).toString(), ([t, u, m]).toString());
    Test.assertEqual((Alerts.choices({"k" => "high", "side" => "high", "less" => false, "relaxed" => false})).toString(), ([t, m]).toString());
    Test.assertEqual((Alerts.choices({"k" => "no_data", "side" => "none", "less" => false, "relaxed" => false})).toString(), ([]).toString());
    // a new kind is announced once; one that went and came back again
    var T = SlotCases.T;
    var p = SlotCases.payload(62, 30, "mgdl");
    p.put("ring", [{"k" => "low", "side" => "low", "less" => true, "relaxed" => false}]);
    SlotCases.take(p);
    Application.Storage.deleteValue(Alerts.RUNG);
    Test.assertEqual((Ring.kinds(Alerts.news(T))).toString(), (["low"]).toString());
    Test.assertEqual(Alerts.news(T + 60).size(), 0);
    Test.assertEqualMessage(Alerts.news(T + 400).size(), 0, "the reading aged: nothing rings");
    Test.assertEqualMessage((Ring.kinds(Alerts.news(T + 60))).toString(), (["low"]).toString(), "rings again: new");
    Application.Storage.deleteValue(Alerts.RUNG);
    Link.reset();
    return true;
}

// CGM-GB-data's times round toward earlier: 2^30 + 100 is nearest the Float 2^30 + 128 (later); it gives 2^30
(:test)
function slotsTimesNeverLater(logger as Logger) as Boolean {
    var b = 1073741824;
    Test.assertMessage((b + 100).toFloat().toLong() > (b + 100).toLong(), "a plain toFloat rounds this one later");
    Test.assertMessage(Slots.earlier(b + 100).toLong() == b.toLong(), "earlier: the step before");
    Test.assertMessage(Slots.earlier(b + 128).toLong() == (b + 128).toLong(), "a Float time stays");
    Test.assertMessage(Slots.earlier(1794825276).toLong() <= 1794825276l, "today's times: never later");
    Test.assertMessage(1794825276l - Slots.earlier(1794825276).toLong() < 128, "under 128 s earlier");
    Test.assertMessage(Slots.earlier(1000).toLong() == 1000l, "small numbers exact");
    return true;
}
