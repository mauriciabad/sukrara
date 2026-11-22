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
    // view take 47.4 KB between draws (memGlanceDraw). The copy, the hand-off and the input built from them: the full
    // payload alone is 12.9 KB
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

    // the glance's two places as it draws them: a ring's kind, number, dimming and bands; the last treatment's kind
    // and minutes
    function two(a as Array) as String {
        var s = "";
        for (var i = 0; i < a.size() && i < 2; i++) {
            var x = a[i] as Array;
            if ("ring".equals(x[0])) {
                var m = x[1] as Dictionary;
                s += "ring " + m.get("k") + " " + m.get("v") + " " + (m.get("dim") == true) + " " + m.get("b") + "; ";
            } else {
                s += x.toString() + "; ";
            }
        }
        return s;
    }

    // the glance's draw (AppGlance.onUpdate, AppInput.glance, in its order): what it holds at each step, in bytes.
    // [copy + input] the copy read and the input built from it; [list] the input and the display list; [font] the most
    // one text's atlas and glyph table add while it paints
    const GLANCE_STEPS = 3840;
    const GLANCE_LIST = 1600;
    const GLANCE_FONT = 1792;

    function glanceDraw(T as Number) as Array<Number> {
        var m0 = used();
        Link.use(GlanceCopy.load());
        var d = AppInput.build(T + 60, false);
        var steps = used() - m0;
        Link.use(null);
        AppDraw.glance = true;
        AppDraw.once = false;
        AppDraw.table = null;
        // at the watch's glance scale (its fonts are made at it), as AppGlance.glanceArea
        AppDraw.scale(GlanceFontTable.GS);
        AppDraw.ox = 83 * AppDraw.s - 0.5;
        AppDraw.oy = 51.0 * AppDraw.s;
        var g = new AppG();
        AppParts.glance(g, d);
        d = null;
        var list = used() - m0;
        // each text's font, one at a time as the glance holds them
        var font = 0;
        for (var i = 0; i < g.els.size(); i++) {
            var e = g.els[i] as Array;
            if ((e[0] as Number) == 5) {
                AppDraw.release();
                var f0 = used();
                AppDraw.loadFont(e[4] as String);
                var f = used() - f0;
                font = f > font ? f : font;
            }
        }
        AppDraw.release();
        AppDraw.glance = false;
        return [steps, list, font];
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
        Test.assertEqualMessage(MemCases.two(b.slots), MemCases.two(a.slots), what);
        Test.assertEqualMessage("" + b.ro, "" + a.ro, what);
        Test.assertEqualMessage("" + b.unit, "" + a.unit, what);
    }
    Link.reset();
    return true;
}

// The glance's draw, over several draws of the heaviest payload: the Descent Mk3 43's glance has 61248 B and its code,
// data and view take 47.4 KB between draws (simulator, release build, 2026-11-22); a draw held 2–3 KB free at its
// peak, the copy and three glyph tables decoded from base64 together, and ran out of memory on the watch from its
// second draw (#217). Held free with a ballast through every draw in the simulator: 9 KB passes, 10 KB doesn't. The
// steps' budgets keep that: each is what its step held then (3472, 1432, 1712 B), with a little to spare; a glyph table
// decoded from base64 again held 1832.
(:test)
function memGlanceDraw(logger as Logger) as Boolean {
    var T = 1794823776;
    Link.reset();
    MemCases.run(MemCases.heavy(T));
    MemCases.fresh();
    // the first draw also loads what stays for the next ones (modules, classes): the glance's 47.4 KB between draws
    MemCases.glanceDraw(T);
    var first = null;
    for (var n = 1; n < 4; n++) {
        var r = MemCases.glanceDraw(T + 30 * n);
        logger.debug("glance draw " + n + ": copy + input " + r[0] + " B (budget " + MemCases.GLANCE_STEPS + "), input + list "
            + r[1] + " (" + MemCases.GLANCE_LIST + "), a font " + r[2] + " (" + MemCases.GLANCE_FONT + ")");
        Test.assertMessage(r[0] <= MemCases.GLANCE_STEPS, "the copy and the input over their budget");
        Test.assertMessage(r[1] <= MemCases.GLANCE_LIST, "the input and the display list over their budget");
        Test.assertMessage(r[2] <= MemCases.GLANCE_FONT, "a text's font over its budget (a glyph table decoded?)");
        if (first == null) {
            first = r;
        } else {
            // nothing kept from one draw to the next
            Test.assertMessage(r[0] <= (first as Array)[0] + 64, "a draw kept something");
        }
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

// An install over another build's storage (2026-11-22: cc0468726 over c5c3c558b on a Descent Mk3 43, #217): the
// payload kept, the glance's copy in the other build's form, which this one doesn't read. The glance never says
// "Waiting for your phone" while a payload is kept; the background's next 5-minute run or the app's view writes the
// copy again (GlanceCopy.heal), and the glance then draws what the payload says.
(:test)
function memGlanceCopyHeals(logger as Logger) as Boolean {
    var p = (LinkCases.app()[0] as Dictionary).get("payload") as Dictionary;
    var T = p.get("sent") as Number;
    Link.reset();
    MemCases.run(p);
    var full = AppInput.build(T + 90, false);
    // the copy as c5c3c558b wrote it: a dictionary around it
    Application.Storage.setValue(GlanceCopy.STORE, {"p" => {"v" => 5, "p" => "app", "sent" => T}, "skew" => 0});
    MemCases.fresh();
    var d = AppInput.glance(T + 90);
    Test.assertMessage(d.sent, "a payload is kept: not Waiting for your phone");
    Test.assertMessage(d.g == null, "no value without the copy");
    // the background's run (or the app's view) holds the kept payload: the copy again
    MemCases.fresh();
    GlanceCopy._healed = null;
    GlanceCopy.heal(Link.payload(), Link.skew);
    MemCases.fresh();
    d = AppInput.glance(T + 90);
    Test.assertEqual(d.g, full.g);
    Test.assertEqual(d.readAt, full.readAt);
    Test.assertEqual(MemCases.two(d.slots), MemCases.two(full.slots));
    // a copy that is the payload's stays as it is (once per payload)
    var s = Application.Storage.getValue(GlanceCopy.STORE);
    MemCases.fresh();
    GlanceCopy._healed = null;
    GlanceCopy.heal(Link.payload(), Link.skew);
    Test.assertMessage(s instanceof Array, "the copy in this build's form");
    // the copy of an older payload: written again from the newer one
    var q = (LinkCases.app()[1] as Dictionary).get("payload") as Dictionary;
    q.put("sent", T + 60);
    MemCases.run(q);
    GlanceCopy.save(p, 0);
    MemCases.fresh();
    GlanceCopy._healed = null;
    GlanceCopy.heal(Link.payload(), Link.skew);
    d = AppInput.glance(T + 90);
    Test.assertEqual(d.g, 52);
    // nothing from the phone ever (a fresh install): Waiting for your phone
    Link.reset();
    Application.Storage.deleteValue(GlanceCopy.STORE);
    MemCases.fresh();
    d = AppInput.glance(T + 90);
    Test.assertMessage(!d.sent, "nothing kept: Waiting for your phone");
    GlanceCopy._healed = null;
    return true;
}
