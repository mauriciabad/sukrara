// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Activity;
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

// THE DEBUG CASE LOADER: a data field filling the simulator's screen that shows every case of
// contracts/watchface/field/cases.json in turn (one form of the field in its slot of the reference's layout, or its
// alert, drawn from one phone state at a watch time), PERIOD_MS each, after a calibration frame; it prints each case's
// input once as JSON ("IN|<id>|<json>") for tools/watchface/app.js's reference (field-input.js) and the case's frame
// cost and memory ("PERF|…"). Only in the 'cases' build (build/cases/cases.jungle, from apps/garmin/field/tools/cases.py):
// the release field never has it, and every symbol here is (:debug). A data field: the same memory as the field.
(:debug)
class FieldCasesApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        return [new FieldCasesView()];
    }
}

(:debug)
class FieldCasesView extends WatchUi.DataField {
    const PERIOD_MS = 3000;
    var start as Number = 0;
    var built as Number = -1; // the case drawn
    var dumped as Number = -1; // the case whose input is printed
    var painted as Number = -1; // the case on screen
    var c as Dictionary = {};
    var d as FieldInput? = null;
    var g as FieldG? = null;
    var word as String? = null;
    var layoutMs as Number = 0;

    function initialize() {
        DataField.initialize();
        start = System.getTimer();
    }

    function compute(info as Activity.Info) as Numeric or Duration or String or Null {
        return null;
    }

    function build(i as Number) as Void {
        FieldDraw.release();
        c = WatchUi.loadResource(FieldCaseTable.res(i)) as Dictionary;
        Link.reset();
        var p = c["payload"];
        var now = 1794823776 + (c["at"] as Number);
        if (p instanceof Dictionary) {
            now = (p["sent"] as Number) + (c["at"] as Number);
            Link.accept(p, now);
        }
        var t0 = System.getTimer();
        var ds = System.getDeviceSettings();
        var W = ds.screenWidth;
        var H = ds.screenHeight;
        d = new FieldInput().from(Readings.input(now));
        if ((c["screen"] as String).equals("dfAlert")) {
            var kind = c["kind"] as String;
            g = FieldWords.alert(d as FieldInput, kind);
            word = (g as FieldG).els[2][3] as String; // the word, as drawn (the bell, the word: FieldLayout.alert)
        } else {
            var sl = c["slots"] as Dictionary;
            var f = (sl["f"] as Array)[sl["i"] as Number] as Array;
            var slot = new FieldSlot(f[0], f[1], f[2], f[3], W, H, obs(f[4] as String));
            g = FieldLayout.field(d as FieldInput, slot);
            word = null;
        }
        layoutMs = System.getTimer() - t0;
        built = i;
    }

    function obs(s as String) as Number {
        return (s.find("L") != null ? 1 : 0) | (s.find("T") != null ? 2 : 0) | (s.find("R") != null ? 4 : 0)
            | (s.find("B") != null ? 8 : 0);
    }

    // the slots' borders, as the watch draws them (app.js S.dfSlot)
    function borders(W as Number, H as Number) as FieldG {
        var b = new FieldG();
        var sl = c["slots"] as Dictionary;
        var fs = sl["f"] as Array;
        for (var i = 0; i < fs.size(); i++) {
            var f = fs[i] as Array;
            var s = new FieldSlot(f[0], f[1], f[2], f[3], W, H, 0);
            var hw = FieldDraw.hw();
            if ((f[1] as Number) > 2) {
                b.l(FieldMath.r1(-hw), FieldMath.r1(s.y0 - 1 / FieldFontTable.S), FieldMath.r1(hw), FieldMath.r1(s.y0 - 1 / FieldFontTable.S),
                    FieldLayout.pal("faint"), 1.0, 2, 0, 0, true);
            }
            if ((f[0] as Number) > 2) {
                b.l(FieldMath.r1(s.x0 - 1 / FieldFontTable.S), FieldMath.r1(s.y0), FieldMath.r1(s.x0 - 1 / FieldFontTable.S), FieldMath.r1(s.y1),
                    FieldLayout.pal("faint"), 1.0, 2, 0, 0, true);
            }
        }
        return b;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var k = (System.getTimer() - start) / PERIOD_MS;
        var W = dc.getWidth();
        var H = dc.getHeight();
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        if (k == 0) {
            // the calibration frame: four 2 x 2 white squares at known pixels (tools/simshot.py finds the screen with them)
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            var a = W * 100 / 390;
            var b = W * 288 / 390;
            dc.fillRectangle(a, a, 2, 2);
            dc.fillRectangle(b, a, 2, 2);
            dc.fillRectangle(a, b, 2, 2);
            dc.fillRectangle(b, b, 2, 2);
            if (built != -2) {
                built = -2;
                System.println("CAL");
            }
            return;
        }
        var i = k - 1;
        if (i >= FieldCaseTable.COUNT) {
            if (dumped != -3) {
                dumped = -3;
                System.println("DONE");
            }
            return;
        }
        // the case's model and its paint (each its own call: the data field's stack is small), its input a frame later
        if (i != built) {
            build(i);
            painted = -1;
        } else if (painted == built && dumped != built) {
            dumped = built;
            dump(W, H);
        }
        paint(dc, W, H);
    }

    function paint(dc as Graphics.Dc, W as Number, H as Number) as Void {
        var t1 = System.getTimer();
        if ((c["screen"] as String).equals("dfAlert")) {
            FieldPlace.paint(dc, g as FieldG, 0, 0, W, H);
        } else {
            var sl = c["slots"] as Dictionary;
            var f = (sl["f"] as Array)[sl["i"] as Number] as Array;
            dc.setClip(f[0], f[1], f[2], f[3]);
            FieldPlace.paint(dc, g as FieldG, 0, 0, W, H);
            dc.clearClip();
            FieldDraw.paint(dc, borders(W, H));
        }
        if (painted != built) {
            painted = built;
            var st = System.getSystemStats();
            System.println("CASE " + built + " " + c["id"]);
            System.println("PERF|" + c["id"] + "|" + layoutMs + "|" + (System.getTimer() - t1) + "|" + st.usedMemory + "|"
                + st.totalMemory + "|" + (g as FieldG).els.size());
        }
    }

    // the input as JSON on the log (field-input.js)
    function dump(W as Number, H as Number) as Void {
        var x = d as FieldInput;
        System.print("IN|" + c["id"] + "|");
        FieldCaseDump.json({
            "field" => true, "cls" => FieldFontTable.CLS, "screen" => c["screen"], "form" => c["form"], "slots" => c["slots"],
            "kind" => c["kind"], "word" => word, "g" => x.g, "unit" => x.unit, "age" => x.age, "readAt" => x.readAt,
            "deg" => x.deg, "doubled" => x.doubled, "hist" => hist(x), "noPhone" => x.noPhone,
        });
        System.println("");
    }

    // the history slots as the payload has them (null = a gap), oldest first, without the reading
    function hist(x as FieldInput) as Array {
        var p = Link.payload();
        var h = p != null ? p.get("hist") : null;
        return h instanceof Array && x.g != null ? h as Array : [] as Array;
    }
}

// A value as JSON on the log, printed piece by piece
(:debug)
module FieldCaseDump {
    function json(v) as Void {
        if (v == null) {
            System.print("null");
        } else if (v instanceof Boolean) {
            System.print(v ? "true" : "false");
        } else if (v instanceof Float || v instanceof Double) {
            System.print(v.format("%.4f"));
        } else if (v instanceof Number || v instanceof Long) {
            System.print(v.toString());
        } else if (v instanceof String) {
            System.print("\"" + v + "\"");
        } else if (v instanceof Array) {
            System.print("[");
            for (var i = 0; i < v.size(); i++) {
                if (i > 0) {
                    System.print(",");
                }
                json(v[i]);
            }
            System.print("]");
        } else if (v instanceof Dictionary) {
            var ks = v.keys();
            System.print("{");
            for (var i = 0; i < ks.size(); i++) {
                if (i > 0) {
                    System.print(",");
                }
                json(ks[i].toString());
                System.print(":");
                json(v[ks[i]]);
            }
            System.print("}");
        } else {
            json(v.toString());
        }
    }
}
