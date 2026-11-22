// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Lang;
import Toybox.System;
import Toybox.Test;

// The glance's and the background's memory with the heaviest app payload (a full day of history, 64 treatments, the
// push's key and watch id): a Descent Mk3 43's glance (64 KB, its code and data 40 of them) ran out of memory reading
// the full payload from storage beside the one Garmin handed it (#217). Now the background keeps the payload, writes the
// glance's copy and hands over only what came; the glance reads only its copy. Measured as objects held
// (System.getSystemStats), the same on the watch. apps/garmin/tools/link-test.sh app.
(:test)
module MemCases {
    // what the glance's data may hold: the simulator's glance on the Descent Mk3 43 has 61248 B; its code, data and
    // view take 48.5 KB when it starts to draw, 58 KB at the end of a draw from its copy (2026-11-22). 12.7 KB for the
    // copy, the hand-off, the input built from them and the draw (~3 KB): the full payload alone is 12.9 KB
    const GLANCE_DATA = 9216;
    // what a background run may hold beyond the payload it got: its glance copy and hand-off, never the stored
    // payload (46 KB used at the hand-off in the simulator, of 61248 B)
    const BG_EXTRA = 2048;

    function heavy(sent as Number) as Dictionary {
        var p = (LinkCases.app()[0] as Dictionary).get("payload") as Dictionary;
        p.put("sent", sent);
        var t = [] as Array;
        var k = [] as Array;
        var a = [] as Array;
        var id = [] as Array;
        var kinds = ["rapid", "long", "carbs"];
        for (var j = 0; j < 64; j++) {
            t.add(sent - 86000 + j * 1300);
            k.add(kinds[j % 3]);
            a.add(4.5 + j);
            id.add("a" + (1000000 + j) + "b");
        }
        p.put("tx", {"t" => t, "k" => k, "a" => a, "i" => id});
        p.put("key", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        p.put("w", "3912345678");
        return p;
    }

    // the glance's two places
    function two(a as Array) as Array {
        return a.size() > 2 ? a.slice(0, 2) : a;
    }

    function used() as Number {
        return System.getSystemStats().usedMemory;
    }

    // a background run with [p]: its hand-off, left in storage as AppService.hand leaves it
    function run(p as Dictionary) as Dictionary {
        var out = AppRun.keep({"k" => "push", "d" => p, "n" => 0}, p.get("sent") as Number);
        Application.Storage.setValue(LinkBackground.HANDOFF, out);
        return out;
    }

    // the Link as a new process finds it: nothing read from storage yet
    function fresh() as Void {
        Link._payload = null;
        Link._loaded = false;
    }
}

(:test)
function memGlanceReadsItsCopyOnly(logger as Logger) as Boolean {
    var T = 1794823776;
    Link.reset();
    var out = MemCases.run(MemCases.heavy(T));
    Test.assertEqualMessage(out.get("kept"), true, "kept by the background");
    Test.assertMessage(!out.hasKey("d"), "the hand-off carries no payload");
    MemCases.fresh();
    // the full payload, as the glance used to read it
    var m0 = MemCases.used();
    var full = Application.Storage.getValue(Link.STORE);
    var fullB = MemCases.used() - m0;
    full = null;
    // what the glance holds now: the hand-off, its copy, the input it draws from
    m0 = MemCases.used();
    var hand = Application.Storage.getValue(LinkBackground.HANDOFF);
    var handB = MemCases.used() - m0;
    m0 = MemCases.used();
    Link.use(GlanceCopy.load());
    var copyB = MemCases.used() - m0;
    m0 = MemCases.used();
    var d = AppInput.build(T + 60, false);
    var inB = MemCases.used() - m0;
    logger.debug("glance: full payload " + fullB + " B; hand-off " + handB + ", copy " + copyB + ", input " + inB
        + " B (budget " + MemCases.GLANCE_DATA + ")");
    Test.assertMessage(handB + copyB + inB <= MemCases.GLANCE_DATA, "the glance's data over its budget");
    Test.assertMessage(fullB + inB > MemCases.GLANCE_DATA, "the full payload would be over it (the check checks)");
    Test.assertEqual(d.g, 120);
    Test.assertMessage(hand instanceof Dictionary, "the hand-off is there");
    Link.reset();
    return true;
}

// the glance draws the same from its copy as from the full payload (every app case)
(:test)
function memGlanceCopyDrawsTheSame(logger as Logger) as Boolean {
    var cases = LinkCases.app();
    for (var i = 0; i < cases.size(); i++) {
        var p = (cases[i] as Dictionary).get("payload") as Dictionary;
        var T = p.get("sent") as Number;
        Link.reset();
        MemCases.run(p);
        var a = AppInput.build(T + 90, false);
        Link.use(GlanceCopy.load());
        var b = AppInput.build(T + 90, false);
        var what = (cases[i] as Dictionary).get("name") as String;
        Test.assertEqualMessage("" + b.g, "" + a.g, what);
        Test.assertEqualMessage("" + b.readAt, "" + a.readAt, what);
        Test.assertEqualMessage("" + b.sensorState, "" + a.sensorState, what);
        Test.assertEqualMessage("" + b.sensorDays, "" + a.sensorDays, what);
        Test.assertEqualMessage("" + b.pill, "" + a.pill, what);
        Test.assertEqualMessage("" + MemCases.two(b.slots), "" + MemCases.two(a.slots), what);
        Test.assertEqualMessage("" + b.ro, "" + a.ro, what);
        Test.assertEqualMessage("" + b.unit, "" + a.unit, what);
    }
    Link.reset();
    return true;
}

// a background run holds the payload it got, never the stored one beside it (Link.accept reads its time only)
(:test)
function memBackgroundRun(logger as Logger) as Boolean {
    var T = 1794823776;
    Link.reset();
    MemCases.run(MemCases.heavy(T));
    MemCases.fresh();
    var m0 = MemCases.used();
    var p = MemCases.heavy(T + 300);
    var newB = MemCases.used() - m0;
    var out = MemCases.run(p);
    var runB = MemCases.used() - m0;
    logger.debug("background: the new payload " + newB + " B, the run holds " + runB + " B with it (budget +"
        + MemCases.BG_EXTRA + ")");
    Test.assertEqual(out.get("kept"), true);
    Test.assertMessage(Link.payload() == p, "kept the new one");
    Test.assertMessage(runB <= newB + MemCases.BG_EXTRA, "the run held more than its payload: the stored one too");
    // the order rule without the stored payload: an older one is refused
    MemCases.fresh();
    Test.assertEqual(MemCases.run(MemCases.heavy(T + 100)).get("kept"), false);
    Link.reset();
    Application.Storage.deleteValue(GlanceCopy.STORE);
    return true;
}
