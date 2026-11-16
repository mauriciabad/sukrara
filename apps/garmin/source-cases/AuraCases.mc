// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.Time.Gregorian;
import Toybox.Timer;
import Toybox.WatchUi;

// THE DEBUG CASE LOADER (SPEC.md §10): the face showing every case of contracts/watchface/aura/cases.json in turn, 6 s
// each, the reference's fixed clock (Mon 16 Nov 2026, UTC), and printing each case's layout once (AuraDump) for
// apps/garmin/tools/garmin_test.py. Only in the 'cases' build (cases.jungle, manifest-cases.xml): the release face
// never has it, and every symbol here is (:debug), so a release build of these files strips it too.
(:debug)
module AuraCases {
    const PERIOD_MS = 6000;

    // a case's input model: the reference's defaults, then the case's cfg (SPEC.md §3)
    function input(i as Number) as AuraInput {
        var c = WatchUi.loadResource(AuraCaseTable.res(i)) as Dictionary;
        var cfg = c["cfg"] as Dictionary;
        var inp = new AuraInput();
        inp.utc = true;
        inp.is24 = true;
        var t = str(cfg, "time", "10:09:36");
        if (t.equals("live")) {
            inp.now = Time.now().value();
        } else {
            var h = t.substring(0, t.find(":")).toNumber();
            var rest = t.substring(t.find(":") + 1, t.length());
            var m = rest.substring(0, 2).toNumber();
            var s = rest.substring(3, 5).toNumber();
            inp.now = Gregorian.moment({:year => 2026, :month => 11, :day => 16, :hour => h, :minute => m, :second => s}).value();
        }
        if (cfg.hasKey("glucose")) { inp.glucose = num(cfg["glucose"]); }
        if (cfg.hasKey("rate")) { inp.rate = cfg["rate"] == null ? null : num(cfg["rate"]); }
        if (cfg.hasKey("trend")) { inp.trend = cfg["trend"] as Boolean; }
        if (cfg.hasKey("age")) { inp.age = num(cfg["age"]); }
        if (cfg.hasKey("history")) { inp.history = cfg["history"] as Array; }
        if (cfg.hasKey("spark")) { inp.spark = cfg["spark"] as Array; }
        inp.sensor = str(cfg, "sensor", inp.sensor);
        inp.status = str(cfg, "status", inp.status);
        if (cfg.hasKey("left")) { inp.left = cfg["left"] instanceof String ? null : num(cfg["left"]); }
        inp.mode = str(cfg, "mode", inp.mode);
        inp.privacy = str(cfg, "privacy", inp.privacy);
        if (cfg.hasKey("reveal")) { inp.reveal = num(cfg["reveal"]); }
        inp.unit = str(cfg, "unit", inp.unit);
        inp.lang = str(cfg, "lang", inp.lang);
        if (cfg.hasKey("battery")) { inp.battery = num(cfg["battery"]); }
        if (cfg.hasKey("batteryPct")) { inp.batteryPct = num(cfg["batteryPct"]).toNumber(); }
        inp.battLabel = str(cfg, "battLabel", inp.battLabel);
        if (cfg.hasKey("rangeColour")) { inp.rangeColour = cfg["rangeColour"] as Boolean; }
        if (cfg.hasKey("seconds")) { inp.seconds = cfg["seconds"] as Boolean; }
        inp.halo = str(cfg, "halo", inp.halo);
        inp.topField = str(cfg, "topField", inp.topField);
        if (cfg.hasKey("fields")) { inp.fields = cfg["fields"] as Array; }
        if (cfg.hasKey("last")) {
            var last = cfg["last"] as Dictionary;
            var items = last["items"] as Array;
            inp.lastItems = [];
            for (var n = 0; n < items.size(); n++) {
                var it = items[n] as Dictionary;
                inp.lastItems.add([it["k"], it["a"]]);
            }
            if (last.hasKey("ago")) { inp.lastAgo = num(last["ago"]); }
        } else {
            inp.lastItems = [[str(cfg, "lastKind", "rapid"), cfg.hasKey("lastAmount") ? cfg["lastAmount"] : 2]];
            if (cfg.hasKey("lastAgo")) { inp.lastAgo = num(cfg["lastAgo"]); }
        }
        if (cfg.hasKey("steps")) { inp.steps = num(cfg["steps"]).toNumber(); }
        if (cfg.hasKey("limits")) { inp.limits = cfg["limits"] as Array; }
        if (cfg.hasKey("tir")) { inp.tir = cfg["tir"] == null ? null : num(cfg["tir"]).toNumber(); }
        if (cfg.hasKey("phone")) { inp.phone = cfg["phone"] as Boolean; }
        inp.dial = dial(str(cfg, "dial", "halftone"));
        inp.trendStyle = str(cfg, "trendStyle", inp.trendStyle);
        if (cfg.hasKey("warm")) { inp.warm = num(cfg["warm"]); }
        if (cfg.hasKey("sensorLeft")) { inp.sensorLeft = num(cfg["sensorLeft"]); }
        return inp;
    }

    function id(i as Number) as String {
        return (WatchUi.loadResource(AuraCaseTable.res(i)) as Dictionary)["id"] as String;
    }

    function filter(i as Number) as String {
        var cfg = (WatchUi.loadResource(AuraCaseTable.res(i)) as Dictionary)["cfg"] as Dictionary;
        return str(cfg, "filter", "none");
    }

    // the reference's dial names → the setting's (SPEC.md §11)
    function dial(d as String) as String {
        return d.equals("halftone") ? "grain" : d.equals("topo") ? "contours" : d.equals("water") ? "waterline" : d.equals("laser") ? "lasers" : d;
    }

    function num(v) as Float {
        return v instanceof String ? (v as String).toFloat() : (v as Numeric).toFloat();
    }

    function str(cfg as Dictionary, k as String, d as String) as String {
        return cfg.hasKey(k) && cfg[k] != null ? cfg[k].toString() : d;
    }
}

(:debug)
class AuraCasesApp extends AuraApp {
    function initialize() {
        AuraApp.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        return [new AuraCasesView()];
    }
}

(:debug)
class AuraCasesView extends AuraView {
    var shown as Number = -1;
    var timed as Number = -1;
    var start as Number = 0;
    var combo as Number = 0;
    var comboBad as Number = 0;
    var timer as Timer.Timer? = null;
    var frames as Timer.Timer? = null;

    function combos() as Void {
        comboBad += AuraCombos.run(combo, combo + 3);
        combo += 3;
        if (combo >= AuraCombos.count()) {
            timer.stop();
            System.println("COMBOS|" + AuraCombos.count() + "|" + comboBad);
        }
    }

    function initialize() {
        AuraView.initialize();
        start = System.getTimer();
        // a watch-app (a face's 128 KB can't hold the face and this loader's layout dump): its frames come from a
        // timer, 1 Hz like an awake face
        frames = new Timer.Timer();
        frames.start(method(:frame), 1000, true);
    }

    function frame() as Void {
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var k = (System.getTimer() - start) / AuraCases.PERIOD_MS;
        if (k == 0) {
            // the calibration frame: four 2 x 2 white squares at known pixels (tools/simshot.py finds the screen with them)
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
            dc.clear();
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.fillRectangle(100, 100, 2, 2);
            dc.fillRectangle(288, 100, 2, 2);
            dc.fillRectangle(100, 288, 2, 2);
            dc.fillRectangle(288, 288, 2, 2);
            if (shown != -2) {
                shown = -2;
                System.println("CAL");
            }
            return;
        }
        if (k > AuraCaseTable.COUNT && combo < AuraCombos.count()) {
            // after the cases: the battery label against every row (AuraCombos), a few per timer tick (the watchdog
            // allows only a handful of layouts per call)
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
            dc.clear();
            if (timer == null) {
                timer = new Timer.Timer();
                timer.start(method(:combos), 50, true);
            }
            return;
        }
        var i = (k - 1) % AuraCaseTable.COUNT;
        var t0 = System.getTimer();
        var m = new AuraModel(AuraCases.input(i));
        var t1 = System.getTimer();
        AuraDraw.draw(dc, m, 0, 0);
        var t2 = System.getTimer();
        // the cost of a frame (SPEC.md §13): the layout and the paint in ms (the frames of a case that paint the dial's
        // cache, then one that copies it) and the memory in use (bytes, of the total)
        if (i != shown || timed != i) {
            if (i == shown) {
                timed = i;
            }
            var st = System.getSystemStats();
            System.println("PERF|" + AuraCases.id(i) + "|" + (i != shown ? "first" : "next") + "|" + (t1 - t0) + "|" +
                (t2 - t1) + "|" + st.usedMemory + "|" + st.totalMemory);
        }
        if (i != shown && !AuraDials.painting()) {
            // the case is on screen: its layout (the dial paints a few units a frame, so this call has room)
            shown = i;
            System.println("CASE " + i + " " + AuraCases.id(i) + " " + AuraCases.filter(i));
            AuraDump.dump(AuraCases.id(i), m);
        }
    }
}