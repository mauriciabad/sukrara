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
// one phone state's payload at a watch time), each PERIOD_MS after it is out, after a calibration frame, printing each case's input
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
    var shownAt as Number = 0; // when its input was out
    var finished as Boolean = false;
    var again as Boolean = false; // the case is laid out again once its % column is prepared
    var laying as Number = -1; // the case loaded, to lay out in the next callback
    var cp as Dictionary? = null; // its payload
    var cnow as Number = 0; // its watch time
    var dumped as Number = -1; // the case whose input is printed
    var frames as Timer.Timer? = null;
    var g as AppG? = null;
    var d as AppData? = null;
    var scr as String = "";
    var days as Number = 14;
    var sel as Number = 0;
    var id as String = "";
    var layoutMs as Number = 0;
    var cfg as Dictionary = {};
    var ga as Array = [0, 0, 0, 0]; // the glance's content area (the device file's: x, y, w, h)
    var xs as Dictionary = {}; // the screen's own words, for the reference (app.js's hooks)

    function initialize() {
        View.initialize();
        start = System.getTimer();
        frames = new Timer.Timer();
        (frames as Timer.Timer).start(method(:frame), 250, true);
    }

    // each step in its own callback (the watchdog counts each): the case's display list, its paint, its input printed
    // A case stays PERIOD_MS after its paint and its input are out, however slow the simulator is (a busy Mac made
    // cases go by unseen); the calibration frame the same after the start.
    function frame() as Void {
        var now = System.getTimer();
        var next = built < 0 ? (now - start >= PERIOD_MS ? 0 : -1) : (dumped == built && now - shownAt >= PERIOD_MS ? built + 1 : -1);
        if (AppScreens.want != null) {
            // the % column's work, a step per callback, then the case laid out again with it (as AppView does)
            again = AppScreens.prepare();
        } else if (again) {
            // the widget page (the only screen with the column) again, from the same input
            again = false;
            var gg = new AppG();
            AppScreens.appA(gg, d as AppData);
            g = AppScreens.want != null ? null : gg;
        } else if (laying >= 0) {
            lay();
        } else if (next >= 0 && next < AppCaseTable.COUNT) {
            build(next);
        } else if (next >= AppCaseTable.COUNT) {
            finished = true;
        } else if (painted == built && built >= 0 && dumped != built) {
            dumped = built;
            System.print("IN|" + id + "|");
            AppCaseDump.input(d as AppData, scr, days, sel, cfg, xs);
            System.println("");
            shownAt = System.getTimer();
        }
        WatchUi.requestUpdate();
    }

    function build(i as Number) as Void {
        AppDraw.release();
        var c = WatchUi.loadResource(AppCaseTable.res(i)) as Dictionary;
        Link.reset();
        Outbox.reset();
        var p = c["payload"] as Dictionary;
        var now = (p["sent"] as Number) + (c["at"] as Number);
        // a payload of another version (cfg update: "app" a newer one, "phone" an older one), read as the link reads it
        var u = (c["cfg"] as Dictionary)["update"];
        if (u != null && p["none"] != true) {
            var q = {};
            var ks = p.keys();
            for (var k = 0; k < ks.size(); k++) {
                q[ks[k]] = p[ks[k]];
            }
            q["v"] = SukraraFace.VERSION + ("app".equals(u) ? 1 : -1);
            p = Link.readable(q) as Dictionary;
        }
        Application.Storage.deleteValue(GlanceCopy.STORE);
        if (p["none"] != true && Link.accept(p, now)) {
            GlanceCopy.save(p, Link.skew);
        }
        cfg = c["cfg"] as Dictionary;
        ga = c["ga"] as Array;
        xs = {};
        // the outbox as the case says (the glance's sending line)
        var ob = cfg["outbox"];
        if ("sent".equals(ob)) {
            Application.Storage.setValue(Outbox.SENT, now - 60);
        } else if (ob != null) {
            Outbox.save([{"w" => "e", "id" => "c", "st" => "ask".equals(ob) ? "pen" : "new", "c" => now}]);
        }
        id = c["id"] as String;
        scr = c["screen"] as String;
        days = cfg["days"] instanceof Number ? cfg["days"] as Number : p["repD"] instanceof Number ? p["repD"] as Number : 14;
        sel = cfg["sel"] instanceof Number ? cfg["sel"] as Number : 0;
        var w = System.getDeviceSettings().screenWidth;
        var h = System.getDeviceSettings().screenHeight;
        AppDraw.scale(AppFontTable.S);
        AppDraw.ox = w / 2.0 - 0.5;
        AppDraw.oy = h / 2.0 - 0.5;
        // nothing painted until it is laid out (the last case's list would paint with this one's tables)
        g = null;
        cp = p;
        cnow = now;
        laying = i;
    }

    // the case's screen from its payload, in a callback of its own as the app's (AppView.build: the input, the layout)
    function lay() as Void {
        var i = laying;
        laying = -1;
        var p = cp as Dictionary;
        var now = cnow;
        var t0 = System.getTimer();
        if (scr.equals("glance")) {
            // the glance draws from its own copy only (AppGlance)
            Link.use(GlanceCopy.load());
        }
        d = AppInput.build(now, scr.equals("appA") || scr.equals("alert"));
        var gg = new AppG();
        if (scr.equals("glance")) {
            // the glance's own area where the watch's glance list puts it (the device file's content area, cases.py;
            // app.js glanceBox), vertically centred
            AppGlance.glanceArea(ga[2] as Number, ga[3] as Number);
            AppDraw.ox += ga[0] as Number;
            AppDraw.glance = true;
            AppDraw.table = null;
            AppParts.glance(gg, d as AppData);
            if (!(d as AppData).sent) {
                xs = {"setup" => true};
            }
            var snd = Sending.state(now);
            if (snd != null) {
                xs = {"sending" => {"text" => AppParts.sendingText(snd), "ask" => snd.equals("ask")}};
            }
        } else {
            AppDraw.glance = false;
            AppDraw.table = new Lang.Method(AppFontTable, :atlas);
            AppDraw.iconAt = new Lang.Method(AppFontTable, :icon);
            AppDraw.sheetAt = new Lang.Method(AppFontTable, :sheet);
            AppDraw.bitmapAt = new Lang.Method(AppDrawApp, :bitmap);
            if (scr.substring(0, 3).equals("log")) {
                logScreen(gg, scr, cfg, p);
            } else if (scr.equals("muteLengths") || scr.equals("muteDone") || scr.equals("muted") || scr.equals("remindDone")) {
                muteScreen(gg, scr, now);
            } else if (scr.equals("setup")) {
                AppScreens.setup(gg, cfg["opened"] == true);
            } else if (scr.equals("update")) {
                AppScreens.update(gg, d as AppData, cfg["opened"] == true);
            } else if (scr.equals("alert")) {
                var items = Ring.now(p, now, Link.skew);
                var it = items[cfg["item"] instanceof Number ? cfg["item"] as Number : 0];
                AppAlert.draw(gg, d as AppData, it, cfg["spark"] != false, sel);
                var labels = [];
                var codes = AppAlert.rows(it);
                for (var j = 0; j < codes.size(); j++) {
                    labels.add(codes[j]);
                }
                xs = {"word" => AppAlert.word(it["k"]), "rows" => labels, "tk" => Ring.treatment(it["side"]), "every" => null,
                    "spark" => cfg["spark"] != false, "noValue" => (d as AppData).noValue(),
                    "ring" => AppAlert.colour(it["k"], d as AppData), "very" => AppAlert.very(it["k"])};
            } else if (scr.equals("actions")) {
                AppLists.actions(gg, d as AppData, sel);
            } else if (scr.equals("appNums")) {
                AppScreens.appNums(gg, d as AppData, days);
            } else {
                AppScreens.appA(gg, d as AppData);
            }
        }
        layoutMs = System.getTimer() - t0;
        // not shown before its % column is in (want: prepared in the next callback)
        g = AppScreens.want != null ? null : gg;
        built = i;
    }

    // a mute screen (AppMute) from the case's cfg: side (all / low / high; muted: the payload's), sel, min, left, act
    function muteScreen(g as AppG, scr as String, now as Number) as Void {
        var side = cfg["side"] instanceof String ? cfg["side"] as String : "all";
        var left = cfg["left"] != null ? (cfg["left"] as Numeric).toFloat() : 0.611;
        if (scr.equals("muteLengths")) {
            AppMute.lengths(g, side, sel);
            var items = [];
            var ms = AppMute.mins(side);
            for (var i = 0; i < ms.size(); i++) {
                items.add(AppMute.length(ms[i]));
            }
            xs = {"items" => items, "sel" => sel};
        } else if (scr.equals("muteDone")) {
            var min = cfg["min"] instanceof Number ? cfg["min"] as Number : 30;
            AppMute.done(g, "bellOff", AppMath.pal("note"), AppMute.title(side), AppMute.doneLine(min, now), left);
            xs = {"title" => AppMute.title(side), "line" => AppMute.doneLine(min, now)};
        } else if (scr.equals("muted")) {
            var x = Link.inputs(now) as SukraraFace.Reading;
            var s = x.side != null ? x.side as String : "all";
            var line = AppMute.leftLine(x.left, now);
            AppMute.muted(g, s, line, sel);
            xs = {"title" => AppMute.title(s), "line" => line, "sel" => sel, "foot" => s.equals("all")};
        } else {
            var act = cfg["act"] instanceof String ? cfg["act"] as String : "less";
            AppMute.done(g, act.equals("less") ? "bellPause" : "bell", AppMath.pal(side), AppMute.remindTitle(act, side), null, left);
            xs = {"title" => AppMute.remindTitle(act, side), "line" => null, "col" => side, "icon" => act.equals("less") ? "bellPause" : "bell"};
        }
    }

    // a logging screen (AppLog) from the case's cfg: kind, amount (default the kind's last), progress, dup, left, pen
    function logScreen(g as AppG, scr as String, cfg as Dictionary, p as Dictionary) as Void {
        var k = cfg["kind"] instanceof String ? cfg["kind"] as String : "rapid";
        var log = p["log"];
        var kd = log instanceof Dictionary ? log[k] : null;
        var usual = kd instanceof Dictionary && kd["usual"] instanceof Array ? kd["usual"] as Array : [] as Array;
        var a = cfg["amount"] != null ? (cfg["amount"] as Numeric).toFloat()
            : kd instanceof Dictionary && kd["last"] != null ? (kd["last"] as Numeric).toFloat() : 2.0;
        if (scr.equals("logAmount")) {
            AppLog.amount(g, k, a, usual, 0);
            var chips = [];
            for (var j = 0; j < usual.size(); j++) {
                chips.add(AppLog.num(k, (usual[j] as Numeric).toFloat()));
            }
            xs = {"amount" => AppLog.num(k, a), "usual" => chips};
        } else if (scr.equals("logConfirm")) {
            var dup = cfg["dup"] == true ? Lang.format(AppWords.s(Rez.Strings.SameKind), [AppMath.amt(k, a), "3 min"]) : null;
            AppLog.confirm(g, k, a, 0, dup, cfg["progress"] != null ? (cfg["progress"] as Numeric).toFloat() : 0.45, AppLog.quiet(k, 0));
            xs = {"quiet" => AppLog.quiet(k, 0)};
        } else if (scr.equals("logSending")) {
            AppLog.sending(g, k, a, 0);
        } else if (scr.equals("logDone")) {
            AppLog.done(g, k, a, cfg["left"] != null ? (cfg["left"] as Numeric).toFloat() : 0.611, AppLog.quiet(k, 0));
            xs = {"quiet" => AppLog.quiet(k, 0)};
        } else if (scr.equals("logQueued")) {
            AppLog.queued(g, k, a, (d as AppData).clock);
        } else if (scr.equals("logPen")) {
            AppLog.pen(g, k, Lang.format(AppWords.s(Rez.Strings.PenAt), [AppMath.amt(k, a), "20:31"]), 0);
        }
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        if (built < 0) {
            // the calibration frame: four 2 x 2 white squares at known pixels (tools/simshot.py finds the screen with them)
            // (the 390's at 100 and 288, scaled by the screen's width)
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            var w = dc.getWidth();
            var a = w * 100 / 390;
            var b = w * 288 / 390;
            dc.fillRectangle(a, a, 2, 2);
            dc.fillRectangle(b, a, 2, 2);
            dc.fillRectangle(a, b, 2, 2);
            dc.fillRectangle(b, b, 2, 2);
            if (painted != -2) {
                painted = -2;
                System.println("CAL");
            }
            return;
        }
        if (finished) {
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

    function input(d as AppData, scr as String, days as Number, sel as Number, cfg as Dictionary, xs as Dictionary) as Void {
        var rows = [];
        if (scr.equals("actions")) {
            var rs = AppLists.rows(d);
            for (var i = 0; i < rs.size(); i++) {
                rows.add([rs[i][0], rs[i][1], rs[i][4]]);
            }
        }
        json({
            "screen" => scr, "days" => days, "sel" => sel, "sent" => d.sent, "update" => d.update, "g" => d.g, "rate" => d.rate,
            "deg" => d.deg, "doubled" => d.doubled, "age" => d.age, "readAt" => d.readAt, "unit" => d.unit,
            "bands" => d.bands, "hm" => d.hm, "hv" => d.hv, "trm" => d.trm, "trk" => d.trk, "tra" => d.tra,
            "pill" => d.pill, "clock" => d.clock, "clockH" => d.clockH, "sensorState" => d.sensorState,
            "sensorDays" => d.sensorDays, "readyAt" => d.readyAt, "warm" => d.warm, "slots" => d.slots,
            "hours" => d.hours, "side" => d.side, "ro" => d.ro, "order" => d.order, "rows" => rows,
            "rep" => scr.equals("appNums") ? AppScreens.report(days) : null, "cfg" => cfg, "x" => xs,
        });
    }
}
