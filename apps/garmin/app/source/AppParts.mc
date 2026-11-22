// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Math;
import Toybox.WatchUi;

// app.js's parts that the glance and the app share, ported function by function: value(), ring(), lastStack(),
// drawSlot(), pill(), the glance (S.glance).
(:glance)
module AppParts {
    const START = -1;
    const MIDDLE = 0;
    const END = 1;
    const BANDS = ["veryLow", "low", "tight", "target", "high", "veryHigh"];

    // the width of a value [str] at [fs] with its arrow when [arrow] (app.js valueWidth())
    function width(str as String, arrow as Boolean, fs as Numeric) as Float {
        var cap = fs * 0.668;
        return (AppMath.bw(str, fs) + (arrow ? fs * 0.1 + cap * 0.78 : 0)).toFloat();
    }

    // the value and its arrow as one group (app.js value()); returns [x0, x1, top]. [col] null = its colour
    function value(g as AppG, d as AppData, x as Numeric, y as Numeric, fs as Numeric, anchor as Number, arrow as Boolean, col as Number?) as Array<Float> {
        var str = AppMath.fmtG(d.g as Number, d.unit);
        var vw = AppMath.bw(str, fs);
        var cap = fs * 0.668;
        var box = cap * 0.78;
        var gap = fs * 0.1;
        var showArrow = arrow && d.rate != null;
        var w = vw + (showArrow ? gap + box : 0);
        var x0 = anchor == START ? x : anchor == END ? x - w : x - w / 2;
        var c = col != null ? col : d.valColour();
        g.n(x0, y, str, fs, c, START);
        // old: struck through and a hollow ring for the arrow, in the value's own colour (grey, or the ink on a block)
        var old = d.age >= 5;
        if (old) {
            g.line(x0 - 4, y - cap / 2, x0 + vw + 4, y - cap / 2, c, fs * 0.05 > 3 ? fs * 0.05 : 3);
        }
        if (showArrow) {
            var ax = x0 + vw + gap + box / 2;
            if (old) {
                g.c(ax, y - cap / 2, box * 0.28, c, 1.0, box * 0.1);
            } else {
                g.arrow(ax, y - cap / 2, box, d.deg, d.doubled, c);
            }
        }
        return [x0.toFloat(), (x0 + w).toFloat(), (y - cap).toFloat()];
    }

    // a metric's ring (the card's): a time-in-range one's segments clockwise from 12 in the rings' order, its number
    // inside; another metric only its number in the empty ring
    function ring(g as AppG, d as AppData, cx as Numeric, cy as Numeric, r as Numeric, sw as Numeric, m as Dictionary, nfs as Numeric) as Void {
        var dim = m.get("dim") == true;
        g.c(cx, cy, r, AppMath.GRID, 1.0, sw);
        var b = m.get("b");
        if ("tir".equals(m.get("k")) && b instanceof Array) {
            var tot = 0;
            for (var i = 0; i < d.ro.size(); i++) {
                tot += b[d.ro[i] as Number] as Number;
            }
            var a = 0.0;
            for (var i = 0; tot > 0 && i < d.ro.size(); i++) {
                var k = d.ro[i] as Number;
                var v = b[k] as Number;
                if (v == 0) {
                    continue;
                }
                var span = v * 360.0 / tot;
                if (span > 4) {
                    g.a(cx, cy, r, a + 2, a + span - 2, dim ? AppMath.DIM : AppMath.pal(BANDS[k]), 1.0, sw, false);
                }
                a += span;
            }
        }
        g.t(cx, cy + nfs * 0.34, number(d, m), nfs, 700, dim ? AppMath.SOFT : AppMath.INK, MIDDLE);
    }

    // a metric's number as the card shows it: tir %, gmi %, avg in the unit, cv %
    function number(d as AppData, m as Dictionary) as String {
        var v = m.get("v");
        if (v == null) {
            return "--";
        }
        var k = m.get("k");
        if ("avg".equals(k)) {
            return AppMath.fmtG(v as Numeric, d.unit);
        }
        if ("gmi".equals(k)) {
            return (v as Numeric).toFloat().format("%.1f");
        }
        return (v as Numeric).toNumber().toString();
    }

    // the last treatment as a stack the size of a ring (app.js lastStack): the kind's mark, the time since
    function lastStack(g as AppG, cx as Numeric, cy as Numeric, size as Numeric, k as String, m as Number) as Void {
        var parts = AppMath.timeSince(m);
        var col = AppMath.pal(k);
        var ms = size * 0.3;
        var two = parts.size() == 2;
        var f1 = size * (two ? 0.4 : 0.46);
        var f2 = size * 0.32;
        var h = ms + 4 + f1 * 0.72 + (two ? 4 + f2 * 0.72 : 0);
        var y0 = cy - h / 2;
        if (k.equals("carbs")) {
            g.c(cx, y0 + ms / 2, ms * 0.4, col, 1.0, 0);
        } else {
            var tri = [cx - ms / 2, y0 + ms * 0.08, cx + ms / 2, y0 + ms * 0.08, cx, y0 + ms * 0.92];
            if (k.equals("long")) {
                g.l(tri.addAll([tri[0], tri[1]]), col, 1.0, 2, null);
            } else {
                g.p(tri, col, 1.0);
            }
        }
        var yb = y0 + ms + 4 + f1 * 0.72;
        g.t(cx, yb, parts[0], f1, 700, AppMath.INK, MIDDLE);
        if (two) {
            g.t(cx, yb + 4 + f2 * 0.72, parts[1], f2, 600, AppMath.SOFT, MIDDLE);
        }
    }

    function drawSlot(g as AppG, d as AppData, s as Array, cx as Numeric, cy as Numeric, r as Numeric, sw as Numeric, nfs as Numeric) as Void {
        if ("last".equals(s[0])) {
            lastStack(g, cx, cy, 2 * r + sw, s[1] as String, s[2] as Number);
        } else {
            ring(g, d, cx, cy, r, sw, s[1] as Dictionary, nfs);
        }
    }

    // Sukrara's pill (app.js pill()): [p] = [kind, text, filled, icon]; returns its width
    function pill(g as AppG, x as Numeric, y as Numeric, p as Array, fs as Numeric, anchor as Number) as Float {
        var h = fs * 1.36;
        var iw = fs * 0.78;
        var padL = fs * 0.5;
        var padR = fs * 0.6;
        var text = p[1] as String;
        var w = padL + iw + fs * 0.3 + AppMath.tw(text, fs) + padR;
        var x0 = anchor == START ? x : anchor == END ? x - w : x - w / 2;
        var col = AppMath.pal(p[0] as String);
        var filled = p[2] as Boolean;
        g.r(x0, y - h / 2, w, h, h / 2, col, filled ? 1.0 : 0.24, 0);
        if (GlanceFontTable.MIP && !filled) {
            // MIP: the tonal pill's tint is a dither; its edge an outline in the kind's colour
            g.r(x0, y - h / 2, w, h, h / 2, col, 1.0, 2);
        }
        var fg = filled ? AppMath.ON : col;
        g.icon(p[3] as String, x0 + padL + iw / 2, y, iw, fg);
        g.t(x0 + padL + iw + fs * 0.3, y + fs * 0.35, text, fs, 600, fg, START);
        return w.toFloat();
    }

    // the warm-up's pie: a ring and the share done filled from 12 (app.js noValue)
    function pie(g as AppG, cx as Numeric, cy as Numeric, r as Numeric, sw as Numeric, warm as Float) as Void {
        g.c(cx, cy, r, AppMath.SOFT, 1.0, sw);
        var ri = r * 0.72;
        var pts = [cx, cy];
        var n = (warm * 24).toNumber() + 1;
        for (var i = 0; i <= n; i++) {
            var q = AppMath.P(ri, warm * 360.0 * i / n);
            pts.add(cx + q[0]);
            pts.add(cy + q[1]);
        }
        if (warm > 0) {
            g.p(pts, AppMath.SOFT, 1.0);
        }
    }

    function sendingText(snd as String) as String {
        return AppWords.s(snd.equals("ask") ? Rez.Strings.SendAsk : snd.equals("wait") ? Rez.Strings.SendWait : Rez.Strings.SendSent);
    }

    // ---- the glance (S.glance): its own area right of Sukrara's icon, x −83 … 165, y −51.5 … 51.5 -------------
    function glance(g as AppG, d as AppData) as Void {
        var x0 = -83;
        var x1 = x0 + AppDraw.gw;
        // waiting for the phone (SPEC §4.3): it never sent anything; selecting it opens the app's waiting page
        if (!d.sent || d.g == null && !d.sensorState.equals("warmup")) {
            g.t(x0, 8, AppWords.s(d.sent ? Rez.Strings.NoReadings : Rez.Strings.SetupTitle), 18, 600, AppMath.SOFT, START);
            return;
        }
        if (d.noValue()) {
            if (d.sensorState.equals("warmup")) {
                pie(g, x0 + 14, -14, 14, 2.2, d.warm);
                g.t(x0 + 36, -6, AppWords.s(Rez.Strings.WWarming), 24, 600, AppMath.INK, START);
                g.t(x0, 28, Lang.format(AppWords.s(Rez.Strings.WReadyAt), [d.readyAt]), 18, 500, AppMath.SOFT, START);
            } else {
                g.t(x0, -14, AppWords.s(Rez.Strings.WNoSince), 18, 600, AppMath.SOFT, START);
                g.t(x0, 26, d.readAt, 38, 600, AppMath.INK, START);
            }
            return;
        }
        var snd = Sending.state(d.now);
        var under = d.pill != null || d.age >= 1.5 || snd != null;
        var vy = under ? 4 : 20;
        var fs = under ? 54 : 58;
        // the same two places as page 1 (main, then the last treatment or the second metric), from the right edge;
        // decided with the widest value (288 and its arrow) at the smallest size, so they never change with the
        // reading; the value then takes the largest size that fits beside them (app.js S.glance, SPEC §14.1)
        var rr = 23;
        var sw = 5;
        var w = 2 * rr + sw;
        var n = d.slots.size() < 2 ? d.slots.size() : 2;
        var need = 2 * w + 6 + 10;
        var floor = fs - 12;
        if (x1 - (x0 + width("288", true, floor)) < need) {
            n = n < 1 ? n : 1;
            need = w + 10;
        }
        var str = AppMath.fmtG(d.g as Number, d.unit);
        while (fs > floor && x1 - (x0 + width(str, d.rate != null, fs)) < need) {
            fs -= 2;
        }
        var v = value(g, d, x0, vy, fs, START, true, null);
        var room = x1 - (v[1] + 10);
        var x = x1;
        for (var i = 0; i < n && room >= w; i++) {
            drawSlot(g, d, d.slots[i] as Array, x - w / 2.0, 0, rr, sw, 23);
            x -= w + 6;
            room -= w + 6;
        }
        if (under) {
            var cy = 34;
            var ax = x0.toFloat();
            if (d.age >= 1.5) {
                g.t(ax, cy + 6, d.readAt, 17, 500, AppMath.SOFT, START);
                ax += AppMath.tw(d.readAt, 17) + 10;
            }
            if (d.pill != null) {
                ax += pill(g, ax, cy, d.pill as Array, 15, START) + 10;
            }
            // the outbox: Waiting for the phone, Sent, The phone has a question; where it fits after the rest
            if (snd != null) {
                var s = sendingText(snd);
                if (ax + AppMath.tw(s, 15) <= x1) {
                    g.t(ax, cy + 5, s, 15, 600, snd.equals("ask") ? AppMath.pal("high") : AppMath.SOFT, START);
                }
            }
        }
    }
}
