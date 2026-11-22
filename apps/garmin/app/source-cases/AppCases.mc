// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Timer;
import Toybox.WatchUi;

// THE DEBUG CASE LOADER: the app showing every case of contracts/watchface/app/cases.json in turn (a screen drawn from
// one phone state's payload at a watch time), PERIOD_MS each, after a calibration frame, printing each case's input
// (AppData) once as JSON ("IN|<id>|<json>") for tools/watchface/app.js's reference and the case's frame cost and memory
// ("PERF|…"). Only in the 'cases' build (build/cases/cases.jungle, from apps/garmin/app/tools/cases.py): the release
// app never has it, and every symbol here is (:debug).
(:debug)
class AppCasesApp extends SukraraApp {
    function initialize() {
        SukraraApp.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        return [new AppCasesView()];
    }
}

(:debug)
class AppCasesView extends WatchUi.View {
    const PERIOD_MS = 3000;
    var start as Number = 0;
    var built as Number = -1; // the case whose display list is ready
    var painted as Number = -1; // the case on screen
    var dumped as Number = -1; // the case whose input is printed
    var frames as Timer.Timer? = null;
    var g as AppG? = null;
    var d as AppData? = null;
    var scr as String = "";
    var days as Number = 14;
    var sel as Number = 0;
    var id as String = "";
    var layoutMs as Number = 0;

    function initialize() {
        View.initialize();
        start = System.getTimer();
        frames = new Timer.Timer();
        (frames as Timer.Timer).start(method(:frame), 250, true);
    }

    // each step in its own callback (the watchdog counts each): the case's display list, its paint, its input printed
    function frame() as Void {
        var k = (System.getTimer() - start) / PERIOD_MS;
        var i = k - 1;
        if (i >= 0 && i < AppCaseTable.COUNT && i != built) {
            build(i);
        } else if (painted == built && built >= 0 && dumped != built) {
            dumped = built;
            System.print("IN|" + id + "|");
            AppCaseDump.input(d as AppData, scr, days, sel);
            System.println("");
        }
        WatchUi.requestUpdate();
    }

    function build(i as Number) as Void {
        AppDraw.release();
        var c = WatchUi.loadResource(AppCaseTable.res(i)) as Dictionary;
        Link.reset();
        var p = c["payload"] as Dictionary;
        var now = (p["sent"] as Number) + (c["at"] as Number);
        Link.accept(p, now);
        var cfg = c["cfg"] as Dictionary;
        id = c["id"] as String;
        scr = c["screen"] as String;
        days = cfg["days"] instanceof Number ? cfg["days"] as Number : p["repD"] instanceof Number ? p["repD"] as Number : 14;
        sel = cfg["sel"] instanceof Number ? cfg["sel"] as Number : 0;
        var w = System.getDeviceSettings().screenWidth;
        var h = System.getDeviceSettings().screenHeight;
        AppDraw.s = w / 390.0;
        AppDraw.ox = w / 2.0 - 0.5;
        AppDraw.oy = h / 2.0 - 0.5;
        var t0 = System.getTimer();
        d = AppInput.build(now);
        var gg = new AppG();
        if (scr.equals("glance")) {
            // the glance's own area at the reference's place on the screen (app.js S.glance: x −83 … 165)
            AppDraw.glance = true;
            AppDraw.table = null;
            AppParts.glance(gg, d as AppData);
        } else {
            AppDraw.glance = false;
            AppDraw.table = new Lang.Method(AppFontTable, :atlas);
            AppDraw.iconAt = new Lang.Method(AppFontTable, :icon);
            AppDraw.sheetId = AppFontTable.sheet();
            if (scr.equals("actions")) {
                AppLists.actions(gg, d as AppData, sel);
            } else if (scr.equals("appNums")) {
                AppScreens.appNums(gg, d as AppData, days);
            } else {
                AppScreens.appA(gg, d as AppData);
            }
        }
        layoutMs = System.getTimer() - t0;
        g = gg;
        built = i;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var k = (System.getTimer() - start) / PERIOD_MS;
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        if (k == 0) {
            // the calibration frame: four 2 x 2 white squares at known pixels (tools/simshot.py finds the screen with them)
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.fillRectangle(100, 100, 2, 2);
            dc.fillRectangle(288, 100, 2, 2);
            dc.fillRectangle(100, 288, 2, 2);
            dc.fillRectangle(288, 288, 2, 2);
            if (painted != -2) {
                painted = -2;
                System.println("CAL");
            }
            return;
        }
        if (k > AppCaseTable.COUNT) {
            if (painted != -3) {
                painted = -3;
                System.println("DONE");
            }
            return;
        }
        if (g == null) {
            return;
        }
        var t1 = System.getTimer();
        AppDraw.paint(dc, g as AppG);
        if (painted != built) {
            painted = built;
            var st = System.getSystemStats();
            System.println("CASE " + built + " " + id);
            System.println("PERF|" + id + "|" + layoutMs + "|" + (System.getTimer() - t1) + "|" + st.usedMemory + "|" + st.totalMemory + "|" + (g as AppG).els.size());
        }
    }
}

// An AppData as JSON on the log (the reference's input, tools/watchface/app.js), printed piece by piece
(:debug)
module AppCaseDump {
    function json(v) as Void {
        if (v == null) {
            System.print("null");
        } else if (v instanceof Boolean) {
            System.print(v ? "true" : "false");
        } else if (v instanceof Float || v instanceof Double) {
            System.print(v.format("%.3f"));
        } else if (v instanceof Number || v instanceof Long) {
            System.print(v.toString());
        } else if (v instanceof String) {
            var s = "";
            var cs = v.toCharArray();
            for (var i = 0; i < cs.size(); i++) {
                var ch = cs[i];
                s += ch == '"' ? "\\\"" : ch == '\\' ? "\\\\" : ch.toString();
            }
            System.print("\"" + s + "\"");
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

    function input(d as AppData, scr as String, days as Number, sel as Number) as Void {
        var rows = [];
        if (scr.equals("actions")) {
            var rs = AppLists.rows(d);
            for (var i = 0; i < rs.size(); i++) {
                rows.add([rs[i][0], rs[i][1], rs[i][4]]);
            }
        }
        json({
            "screen" => scr, "days" => days, "sel" => sel, "sent" => d.sent, "g" => d.g, "rate" => d.rate,
            "deg" => d.deg, "doubled" => d.doubled, "age" => d.age, "readAt" => d.readAt, "unit" => d.unit,
            "bands" => d.bands, "hm" => d.hm, "hv" => d.hv, "trm" => d.trm, "trk" => d.trk, "tra" => d.tra,
            "pill" => d.pill, "clock" => d.clock, "clockH" => d.clockH, "sensorState" => d.sensorState,
            "sensorDays" => d.sensorDays, "readyAt" => d.readyAt, "warm" => d.warm, "slots" => d.slots,
            "hours" => d.hours, "side" => d.side, "ro" => d.ro, "order" => d.order, "rows" => rows,
            "rep" => scr.equals("appNums") ? AppScreens.report(days) : null,
        });
    }
}
