// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Lang;
import Toybox.Time;
import Toybox.Time.Gregorian;
import Toybox.WatchUi;

// app.js's INPUT `d` (SPEC §2), from the app's stored payload and the watch clock: what every screen draws from. The
// reference's data() makes it from a made-up day; here AppInput.build makes it from what the phone sent.
(:glance)
class AppData {
    var sent as Boolean = false; // the phone ever sent this app anything
    var g as Number? = null; // the newest reading, mg/dL (39 LO, 401 HI); null = none
    var rate as Float? = null; // mg/dL/min; null = no trend (and LO / HI)
    var deg as Float = 0.0; // the arrow's rotation (negative = up)
    var doubled as Boolean = false;
    var age as Float = 0.0; // minutes since the reading
    var readAt as String = ""; // the reading's time, "20:38"
    var unit as String = "mgdl";
    var bands as Array = [54, 70, 140, 180, 250, 300] as Array;
    var hm as Array = [] as Array; // the history's minutes before now, oldest first (the reading last)
    var hv as Array = [] as Array; // their mg/dL, null = a gap
    var trm as Array = [] as Array; // the treatments of the window: minutes before now, kind, amount
    var trk as Array = [] as Array;
    var tra as Array = [] as Array;
    var pill as Array? = null; // [kind, text, filled, icon]
    var clock as String = "";
    var clockH as Float = 0.0; // the local clock in hours (the chart's hour labels)
    var sensorState as String = "none"; // Link.inputs' sensor: none, warmup, ok, ending, ended
    var sensorDays as Number? = null; // days left
    var readyAt as String = ""; // warming up: when it's ready
    var warm as Float = 0.0; // warming up: the share done
    var slots as Array = [] as Array; // the card's places in priority: ["ring", m item] / ["last", k, minutes]
    var hours as Number = 12; // the chart's window (ds.hours)
    var side as Boolean = true; // the % column (ds.side)
    var ro as Array = [2, 3, 1, 0, 4, 5] as Array; // the rings' band order
    var order as Array = ["rapid", "long", "carbs"] as Array; // the kinds' order (log.order)

    function initialize() {}

    function band(v as Numeric) as String {
        return AppMath.band(v, bands);
    }

    // the value's colour: grey when old (≥ 5 min), else its band's
    function valColour() as Number {
        return age >= 5 ? AppMath.GREY : AppMath.pal(band(g as Number));
    }

    function noValue() as Boolean {
        return g == null || age >= 20 || sensorState.equals("warmup");
    }
}

(:glance)
module AppInput {
    function hm(t as Number) as String {
        var c = Gregorian.info(new Time.Moment(t), Time.FORMAT_SHORT);
        return (c.hour as Number) + ":" + (c.min as Number).format("%02d");
    }

    function build(now as Number) as AppData {
        var d = new AppData();
        var c = Gregorian.info(new Time.Moment(now), Time.FORMAT_SHORT);
        d.clock = (c.hour as Number) + ":" + (c.min as Number).format("%02d");
        d.clockH = (c.hour as Number) + (c.min as Number) / 60.0 + (c.sec as Number) / 3600.0;
        var x = Link.inputs(now);
        var p = Link.payload();
        if (x == null || p == null) {
            return d;
        }
        d.sent = true;
        var t = now + Link.skew;
        var u = p.get("unit");
        d.unit = u instanceof String ? u : "mgdl";
        var b = p.get("bands");
        if (b instanceof Array && b.size() == 6) {
            d.bands = b;
        }
        var g = p.get("g");
        if (g instanceof Dictionary) {
            var gt = g.get("t") as Number;
            d.g = g.get("v") as Number;
            var r = g.get("rate");
            d.rate = r != null && (d.g as Number) >= 40 && (d.g as Number) <= 400 ? (r as Numeric).toFloat() : null;
            var deg = g.get("deg");
            d.deg = deg != null ? (deg as Numeric).toFloat() : 0.0;
            d.doubled = x.get("double") == true;
            d.age = ((t - gt) < 0 ? 0 : (t - gt)) / 60.0;
            d.readAt = hm(gt - Link.skew);
            var h = p.get("hist");
            if (h instanceof Array) {
                var n = h.size();
                for (var i = 0; i < n; i++) {
                    d.hm.add((t - (gt - 300 * (n - i))) / 60.0);
                    d.hv.add(h[i]);
                }
            }
            d.hm.add(d.age);
            d.hv.add(d.g);
        }
        var tx = p.get("tx");
        if (tx instanceof Dictionary) {
            var tt = tx.get("t") as Array;
            var tk = tx.get("k") as Array;
            var ta = tx.get("a") as Array;
            for (var i = 0; i < tt.size(); i++) {
                d.trm.add((t - (tt[i] as Number)) / 60.0);
                d.trk.add(tk[i]);
                d.tra.add(ta[i]);
            }
        }
        pill(d, x);
        var s = x.get("sensor");
        d.sensorState = s instanceof String ? s : "none";
        var left = x.get("sensorLeft");
        if (left instanceof Float) {
            d.sensorDays = (left / 24.0 + 0.5).toNumber();
        }
        var warm = x.get("warm");
        d.warm = warm instanceof Float ? warm : 0.0;
        var ready = x.get("ready");
        if (ready instanceof Number) {
            d.readyAt = hm(ready - Link.skew);
        }
        var ds = p.get("ds");
        if (ds instanceof Dictionary) {
            var hrs = ds.get("hours");
            d.hours = hrs instanceof Number ? hrs : 12;
            d.side = ds.get("side") != false;
        }
        var ro = p.get("ro");
        if (ro instanceof Array && ro.size() == 6) {
            d.ro = ro;
        }
        var log = p.get("log");
        if (log instanceof Dictionary && log.get("order") instanceof Array) {
            d.order = log.get("order") as Array;
        }
        slots(d, p, t);
        return d;
    }

    // the pill (Aura's): Sukrara's need while out of range, or what holds it (quiet, muted) with the time left
    function pill(d as AppData, x as Dictionary) as Void {
        if (d.g == null || d.age >= 5) {
            return;
        }
        var bk = d.band(d.g as Number);
        var out = !bk.equals("tight") && !bk.equals("target");
        var low = bk.equals("veryLow") || bk.equals("low");
        var st = x.get("status");
        var lf = x.get("left");
        var left = lf instanceof Number ? Durations.short(lf, " ") : AppWords.muted();
        if ("auto".equals(st) && out) {
            d.pill = low ? ["carbs", AppWords.carbs(), true, "carbs"] : ["rapid", AppWords.insulin(), true, "rapid"];
        } else if ("quiet".equals(st) && out) {
            d.pill = [low ? "carbs" : "rapid", left, false, "bellPause"];
        } else if ("muted".equals(st)) {
            d.pill = out ? [low ? "carbs" : "rapid", left, false, "bellPause"] : ["note", left, false, "bellOff"];
        }
    }

    // the card's places in priority (SPEC §4.1, §8): the main metric; the last treatment (never dimmed) when one is
    // still ahead, else what the card's last place shows meanwhile (`else`); then the other metrics
    function slots(d as AppData, p as Dictionary, t as Number) as Void {
        var m = p.get("m");
        var list = m instanceof Array ? m : [] as Array;
        if (list.size() > 0) {
            d.slots.add(["ring", list[0]]);
        }
        var step = Link.treatment(p, t);
        var items = step != null ? step.get("items") : null;
        if (items instanceof Array && items.size() > 0) {
            var it = items[0] as Dictionary;
            d.slots.add(["last", it.get("k"), (t - (it.get("t") as Number)) / 60]);
        } else {
            var e = p.get("else");
            if (e instanceof Dictionary && e.get("v") != null) {
                d.slots.add(["ring", e]);
            }
        }
        for (var i = 1; i < list.size(); i++) {
            d.slots.add(["ring", list[i]]);
        }
    }
}

// the words the screens draw (strings.xml, en / es)
(:glance)
module AppWords {
    function s(id as ResourceId) as String {
        return WatchUi.loadResource(id) as String;
    }

    function now() as String {
        return s(Rez.Strings.WNow);
    }

    function carbs() as String {
        return s(Rez.Strings.WCarbs);
    }

    function insulin() as String {
        return s(Rez.Strings.WInsulin);
    }

    function muted() as String {
        return s(Rez.Strings.WMuted);
    }
}
