// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.WatchUi;

// The app's screens, ported from app.js function by function (S.appA, chart(), pctColumn(), chipRow() …): each one
// pushes its display list from the input `d` (AppData).
module AppScreens {
    const START = -1;
    const MIDDLE = 0;
    const END = 1;
    const LO = 40;
    const HI = 300;

    // ---- the screen class (SPEC §14, app.js CLS) ---------------------------------------------------------------
    // the screen's half width and height in layout units (round: 195; the Venu Sq 2 160 × 180, the X1 187 × 203)
    function hw() as Float {
        return System.getDeviceSettings().screenWidth / 2.0 / AppFontTable.S;
    }

    function hh() as Float {
        return System.getDeviceSettings().screenHeight / 2.0 / AppFontTable.S;
    }

    // no UP / DOWN (the Venu, vívoactive, Approach S50 / S72 and D2 Air X10 rounds, the rectangles): pages and amounts
    // by swipe and tap, no + / − hints (SPEC §14.2)
    function two() as Boolean {
        var ds = System.getDeviceSettings();
        if (AppFontTable.RECT) {
            return true;
        }
        return ds has :inputButtons && (ds.inputButtons & System.BUTTON_INPUT_UP) == 0;
    }

    // what a small round screen leaves out of page 1 (app.js drops()): the sensor's days (MIP ≤ 260), the % column
    // and the chart's 70 / 180 labels (≤ 240), the clock (218); [sensor, dist, yLabels, clock]
    function drops() as Array<Boolean> {
        var w = System.getDeviceSettings().screenWidth;
        var r = !AppFontTable.RECT;
        return [r && GlanceFontTable.MIP && w <= 260, r && w <= 240, r && w <= 240, r && w <= 218];
    }

    // the ink on a colour block: white; MIP white or black, whichever has the higher contrast (app.js inkOn)
    function inkOn(c as Number) as Number {
        if (!GlanceFontTable.MIP) {
            return AppMath.INK;
        }
        var m = AppDraw.col(c, 1.0, 1)[0] as Number;
        var l = lum(m);
        return (1.05 / (l + 0.05)) >= ((l + 0.05) / 0.05) ? AppMath.INK : AppMath.BLACK;
    }

    function lum(c as Number) as Float {
        var v = 0.0;
        var wts = [0.2126, 0.7152, 0.0722];
        for (var i = 0; i < 3; i++) {
            var x = ((c >> (16 - 8 * i)) & 255) / 255.0;
            x = x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
            v += wts[i] * x;
        }
        return v.toFloat();
    }

    // the whole screen in one colour (the very-low block)
    function full(g as AppG, c as Number) as Void {
        g.r(-hw() - 2, -hh() - 2, 2 * hw() + 4, 2 * hh() + 4, 0, c, 1.0, 0);
    }

    // the screen's edge (app.js rim()): a ring on round screens, on rectangles a rounded rectangle 6 units in; [p] < 1
    // draws that share of it clockwise from START (hold to log fills from the button that is held)
    function rim(g as AppG, col as Number, sw as Numeric, p as Float) as Void {
        var a0 = AppFontTable.KEYS[0] as Number;
        if (!AppFontTable.RECT) {
            if (p >= 1) {
                g.a(0, 0, 189, 0, 360, col, 1.0, sw, false);
            } else if (p > 0) {
                g.a(0, 0, 189, a0, a0 + 359.9 * p, col, 1.0, sw, true);
            }
            return;
        }
        var x1 = hw() - 6;
        var y1 = hh() - 6;
        var rc = 22;
        if (p >= 1) {
            g.r(-x1, -y1, 2 * x1, 2 * y1, rc, col, 1.0, sw);
            return;
        }
        // the outline from START (on the right edge) clockwise, as a polyline, [p] of its length
        var ys = a0 / AppFontTable.S - hh();
        var pts = [x1, ys];
        var corners = [[x1 - rc, y1 - rc, 0], [-x1 + rc, y1 - rc, 90], [-x1 + rc, -y1 + rc, 180], [x1 - rc, -y1 + rc, 270]];
        for (var c = 0; c < 4; c++) {
            var k = corners[c] as Array;
            for (var j = 0; j <= 6; j++) {
                var t = ((k[2] as Number) + j * 15) * Math.PI / 180.0;
                pts.add((k[0] as Numeric) + rc * Math.cos(t));
                pts.add((k[1] as Numeric) + rc * Math.sin(t));
            }
        }
        pts.add(x1);
        pts.add(ys);
        var len = 0.0;
        for (var j = 2; j < pts.size(); j += 2) {
            len += Math.sqrt(Math.pow(pts[j] - pts[j - 2], 2) + Math.pow(pts[j + 1] - pts[j - 1], 2));
        }
        var left = len * p;
        var out = [pts[0], pts[1]];
        for (var j = 2; j < pts.size() && left > 0; j += 2) {
            var dx = pts[j] - pts[j - 2];
            var dy = pts[j + 1] - pts[j - 1];
            var l = Math.sqrt(dx * dx + dy * dy);
            var f = l > left ? left / l : 1.0;
            out.add(pts[j - 2] + dx * f);
            out.add(pts[j - 1] + dy * f);
            left -= l;
        }
        if (out.size() >= 4) {
            g.l(out, col, 1.0, sw, null);
        }
    }

    // ---- page 1: the widget (S.appA) ---------------------------------------------------------------------------
    function appA(g as AppG, d as AppData) as Void {
        if (AppFontTable.RECT) {
            rectA(g, d);
            return;
        }
        var dr = drops();
        if (!dr[3]) {
            g.t(0, -150, d.clock, 22, 600, AppMath.SOFT, MIDDLE);
        }
        if (!d.sent || d.g == null && !d.sensorState.equals("warmup")) {
            g.t(0, -60, AppWords.s(Rez.Strings.NoReadings), 22, 600, AppMath.SOFT, MIDDLE);
            pageDots(g, 2, 0);
            return;
        }
        if (d.noValue()) {
            noValue(g, d, -66);
        } else {
            AppParts.value(g, d, 0, -58, 88, MIDDLE, true, null);
        }
        if (d.slots.size() > 1) {
            AppParts.drawSlot(g, d, d.slots[1] as Array, -142, -82, 25, 6, 22);
        }
        if (d.slots.size() > 0) {
            AppParts.drawSlot(g, d, d.slots[0] as Array, 142, -82, 25, 6, 22);
        }
        // without the % column and the 70 / 180 labels the plot takes their room
        var bx = [dr[1] ? -150 : -134, dr[2] ? 158 : 146, -22, 88];
        chart(g, d, bx, d.hours, 14, 10, !dr[2]);
        if (d.side && !dr[1]) {
            pctColumn(g, d, bx, d.hours, 14);
        }
        if (!chipRow(g, d, 146, 19) && !dr[0]) {
            sensorTag(g, d, 0, 152, 17);
        }
        pageDots(g, 2, 0);
    }

    // the rectangles' page 1 (app.js R.appA): the two places in the top corners beside the clock, the value the whole
    // width, the chart edge to edge with its % column, the pill or the sensor at the bottom
    function rectA(g as AppG, d as AppData) as Void {
        var w = hw();
        var h = hh();
        g.t(0, -h + 34, d.clock, 22, 600, AppMath.SOFT, MIDDLE);
        if (!d.sent || d.g == null && !d.sensorState.equals("warmup")) {
            g.t(0, -h + 116, AppWords.s(Rez.Strings.NoReadings), 22, 600, AppMath.SOFT, MIDDLE);
            pageDots(g, 2, 0);
            return;
        }
        var rr = 24;
        var sw = 6;
        if (d.slots.size() > 1) {
            AppParts.drawSlot(g, d, d.slots[1] as Array, -w + 14 + rr + sw / 2, -h + 34, rr, sw, 22);
        }
        if (d.slots.size() > 0) {
            AppParts.drawSlot(g, d, d.slots[0] as Array, w - 14 - rr - sw / 2, -h + 34, rr, sw, 22);
        }
        var vy = -h + 150;
        if (d.noValue()) {
            noValue(g, d, vy - 34);
        } else {
            AppParts.value(g, d, 0, vy, 96, MIDDLE, true, null);
        }
        var bx = [-w + 44, w - 34, vy + 26, h - 64];
        chart(g, d, bx, d.hours, 14, 10, true);
        if (d.side) {
            pctColumn(g, d, bx, d.hours, 14);
        }
        if (!chipRow(g, d, h - 22, 19)) {
            sensorTag(g, d, 0, h - 16, 17);
        }
        pageDots(g, 2, 0);
    }

    // the app's message where the value would be (Aura's words)
    function noValue(g as AppG, d as AppData, y as Numeric) as Void {
        if (d.sensorState.equals("warmup")) {
            var w0 = AppMath.tw(AppWords.s(Rez.Strings.WWarming), 26);
            var mx = -(34 + 12 + w0) / 2;
            AppParts.pie(g, mx + 17, y - 12, 17, 2.5, d.warm);
            g.t(mx + 46, y - 3, AppWords.s(Rez.Strings.WWarming), 26, 600, AppMath.INK, START);
            g.t(0, y + 30, Lang.format(AppWords.s(Rez.Strings.WReadyAt), [d.readyAt]), 20, 500, AppMath.SOFT, MIDDLE);
            return;
        }
        g.t(0, y - 26, AppWords.s(Rez.Strings.WNoSince), 22, 600, AppMath.SOFT, MIDDLE);
        g.t(0, y + 20, d.readAt, 48, 600, AppMath.INK, MIDDLE);
    }

    // Kovatchev's scale at its ends, K(LO) and K(HI) − K(LO): once (every point of the chart takes them)
    var _k0 as Float? = null;
    var _kspan as Float = 1.0;

    function k0() as Float {
        if (_k0 == null) {
            _k0 = AppMath.K(LO);
            _kspan = AppMath.K(HI) - (_k0 as Float);
        }
        return _k0 as Float;
    }

    // the first of [hm] (minutes before now, oldest first) inside the window's [span] minutes; hm.size() if none
    function first(hm as Array, span as Number) as Number {
        var lo = 0;
        var hi = hm.size();
        while (lo < hi) {
            var mid = (lo + hi) / 2;
            if ((hm[mid] as Float) > span) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    // Y of every whole mg/dL from LO to HI in [bx]: once per plot
    var _yl as Array? = null;
    var _ylKey as String = "";

    function yTable(bx as Array) as Array {
        var key = bx.toString();
        if (_yl == null || !key.equals(_ylKey)) {
            // Y(bx, v) inlined (Kovatchev's K): 261 calls of it took a sixth of a callback
            var t = new [HI - LO + 1];
            var k = k0();
            var f = (bx[3] - bx[2]) / _kspan;
            var y0 = bx[3] + k * f;
            for (var v = LO; v <= HI; v++) {
                t[v - LO] = (y0 - 1.509 * (Math.pow(Math.ln(v.toFloat()), 1.084) - 5.381) * f).toFloat();
            }
            _yl = t;
            _ylKey = key;
        }
        return _yl as Array;
    }

    // the bands' colours by index (veryLow … extremelyHigh)
    function bandPals() as Array {
        return [AppMath.pal("veryLow"), AppMath.pal("low"), AppMath.pal("tight"), AppMath.pal("target"),
            AppMath.pal("high"), AppMath.pal("veryHigh"), AppMath.pal("extremelyHigh")];
    }

    // the chart's scale: Kovatchev's symmetrised risk over 40–300 (app.js chart(): above 300 pins to the top)
    function Y(bx as Array, v as Numeric) as Float {
        var k = k0();
        return (bx[3] - (AppMath.K(v < LO ? LO : v > HI ? HI : v) - k) / _kspan * (bx[3] - bx[2])).toFloat();
    }

    // its inverse: the mg/dL at a height
    function G(bx as Array, y as Numeric) as Float {
        var k = k0() + (bx[3] - y) / (bx[3] - bx[2]).toFloat() * _kspan;
        return Math.pow(Math.E, Math.pow(k / 1.509 + 5.381, 1 / 1.084)).toFloat();
    }

    function X(bx as Array, hours as Number, m as Numeric) as Float {
        return (bx[1] - m / (hours * 60.0) * (bx[1] - bx[0])).toFloat();
    }

    function marks(d as AppData, k as String, part as String) as Boolean {
        var ds = Link.payload() != null ? (Link.payload() as Dictionary).get("ds") : null;
        var mk = ds instanceof Dictionary ? ds.get("marks") : null;
        var list = mk instanceof Dictionary ? mk.get(k) : null;
        return list instanceof Array ? list.indexOf(part) >= 0 : part.equals("point") || part.equals("amount");
    }

    // the chart: bands, limits, the line in its bands' colours, the bead, the treatment marks, the hours (app.js chart())
    function chart(g as AppG, d as AppData, bx as Array, hours as Number, lfs as Number, ms as Number, ylab as Boolean) as Void {
        var b = d.bands;
        var w = bx[1] - bx[0];
        g.r(bx[0], Y(bx, b[2]), w, Y(bx, b[1]) - Y(bx, b[2]), 0, AppMath.pal("tight"), 0.13, 0);
        g.r(bx[0], Y(bx, b[3]), w, Y(bx, b[2]) - Y(bx, b[3]), 0, AppMath.pal("target"), 0.075, 0);
        var lims = [b[1], b[3]];
        for (var i = 0; i < 2; i++) {
            var y = Y(bx, lims[i]);
            g.l([bx[0], y, bx[1], y], AppMath.FAINT, 1.0, 1, [3, 4]);
            if (ylab) {
                var lab = d.unit.equals("mmol") ? ((lims[i] as Number) / 18.0182).format("%.1f") : lims[i].toString();
                g.t(bx[1] + 5, y + 5, lab, lfs, 500, AppMath.DIM, START);
            }
        }
        // the line: one polyline per run of one colour; a gap breaks it. Up to 288 readings in the callback that lays
        // the page out (the watchdog counts it, #217): the scale from a table of whole mg/dL, each segment's band (of
        // its two readings' mean) by comparing their sum with the doubled limits
        var old = d.age >= 5;
        var lw = 3.4;
        var run = [] as Array;
        var col = -1;
        var lastX = null;
        var lastY = null;
        var lastV = null;
        var yl = yTable(bx);
        var pals = bandPals();
        var c0 = 2 * b[0];
        var c1 = 2 * b[1];
        var c2 = 2 * b[2];
        var c3 = 2 * b[3];
        var c4 = 2 * b[4];
        var c5 = 2 * b[5];
        var span = hours * 60;
        var sx = (bx[1] - bx[0]) / (hours * 60.0);
        var xr = bx[1];
        var hm = d.hm;
        var hv = d.hv;
        var n = hm.size();
        var on = false; // a run is open
        for (var k = first(hm, span) + 1; k < n; k++) {
            var va = hv[k - 1];
            var vb = hv[k];
            if (va == null || vb == null) {
                if (on) {
                    g.l(run, old ? AppMath.GREY : col, 1.0, lw, null);
                    run = [];
                    on = false;
                }
                continue;
            }
            var sum = (va as Number) + (vb as Number);
            var c = pals[sum <= c0 ? 0 : sum < c1 ? 1 : sum <= c2 ? 2 : sum < c3 ? 3 : sum < c4 ? 4 : sum < c5 ? 5 : 6];
            if (on && c != col) {
                g.l(run, old ? AppMath.GREY : col, 1.0, lw, null);
                run = [];
                on = false;
            }
            if (!on) {
                run.add(xr - (hm[k - 1] as Float) * sx);
                run.add(yl[va < LO ? 0 : va > HI ? HI - LO : va - LO]);
                on = true;
            }
            col = c;
            lastX = xr - (hm[k] as Float) * sx;
            lastY = yl[vb < LO ? 0 : vb > HI ? HI - LO : vb - LO];
            lastV = vb;
            run.add(lastX);
            run.add(lastY);
        }
        if (on) {
            g.l(run, old ? AppMath.GREY : col, 1.0, lw, null);
        }
        // the bead on the newest reading in the window, a lone one too
        for (var k = d.hm.size() - 1; k >= 0 && lastX == null; k--) {
            if (d.hv[k] != null && (d.hm[k] as Float) <= hours * 60) {
                lastX = X(bx, hours, d.hm[k] as Float);
                lastY = Y(bx, d.hv[k] as Number);
                lastV = d.hv[k];
            }
        }
        if (lastX != null) {
            if (old) {
                g.c(lastX, lastY, lw * 1.9, AppMath.BLACK, 1.0, 0);
                g.c(lastX, lastY, lw * 1.9, AppMath.GREY, 1.0, 2);
            } else {
                g.c(lastX, lastY, lw * 1.9 + 1.2, AppMath.BLACK, 1.0, 0);
                g.c(lastX, lastY, lw * 1.9 - 1.2, AppMath.pal(d.band(lastV as Number)), 1.0, 0);
            }
        }
        // the treatments, their labels in the hours' row (an overlapping one is dropped)
        var ly = bx[3] + lfs + 5;
        var labels = [] as Array;
        var shown = {} as Dictionary; // per kind: [point, amount]
        for (var i = d.trm.size() - 1; i >= 0; i--) {
            var m = d.trm[i] as Float;
            var k = d.trk[i] as String;
            var mk = shown.get(k);
            if (mk == null) {
                mk = [marks(d, k, "point"), marks(d, k, "amount")];
                shown.put(k, mk);
            }
            if (m > hours * 60 || m < 0 || !(mk[0] as Boolean)) {
                continue;
            }
            var x = X(bx, hours, m);
            var y = bx[3] - 7;
            var c = AppMath.pal(k);
            if (k.equals("carbs")) {
                var with = false;
                for (var j = i - 1; j >= 0 && (d.trm[j] as Float) - m < 0.5; j--) {
                    with = with || !"carbs".equals(d.trk[j]);
                }
                for (var j = i + 1; j < d.trm.size() && m - (d.trm[j] as Float) < 0.5; j++) {
                    with = with || !"carbs".equals(d.trk[j]);
                }
                g.c(x, y - (with ? ms * 1.3 : 0), ms * 0.4, c, 1.0, 0);
                continue;
            }
            var tri = [x - ms / 2.0, y - ms * 0.42, x + ms / 2.0, y - ms * 0.42, x, y + ms * 0.45];
            if (k.equals("long")) {
                g.l(tri.addAll([tri[0], tri[1]]), c, 1.0, 2, null);
            } else {
                g.p(tri, c, 1.0);
            }
            if (mk[1] as Boolean) {
                var s = AppMath.amt(k, d.tra[i] as Numeric);
                var tw = AppMath.tw(s, lfs);
                var x0 = x - tw / 2;
                var x1 = x + tw / 2;
                var hit = false;
                for (var j = 0; j < labels.size(); j += 2) {
                    if (x1 + 4 > labels[j] && x0 - 4 < labels[j + 1]) {
                        hit = true;
                    }
                }
                if (!hit) {
                    labels.add(x0);
                    labels.add(x1);
                    g.t(x, ly, s, lfs, 600, c, MIDDLE);
                }
            }
        }
        var step = hours >= 12 ? 3 : hours >= 6 ? 2 : 1;
        var nowH = d.clockH;
        for (var hh = Math.ceil(nowH - hours).toNumber(); hh <= nowH; hh++) {
            var h24 = ((hh % 24) + 24) % 24;
            if (h24 % step != 0) {
                continue;
            }
            var x = X(bx, hours, (nowH - hh) * 60);
            if (x < bx[0] + 8 || x > bx[1] - 8) {
                continue;
            }
            var s = h24.toString();
            var tw = AppMath.tw(s, lfs);
            var hit = false;
            for (var j = 0; j < labels.size(); j += 2) {
                if (x + tw / 2 + 4 > labels[j] && x - tw / 2 - 4 < labels[j + 1]) {
                    hit = true;
                }
            }
            if (!hit) {
                g.t(x, ly, s, lfs, 500, AppMath.DIM, MIDDLE);
            }
        }
    }

    // ---- the % column: the widget's distribution (Android GlucoseGraph.profileRows, GlucoseStats.profile) ---------
    // Rows PITCH apart from the plot's bottom, dots of radius R ∝ the row's share of time, the line under them masked
    // CUT wider (the widget erases it; here each row repaints the plot's own background, band tint included), the
    // shares below / in / above left of the plot.
    const PITCH = 4.6; // the widget's 3.6 dp, at the reference's 4.6 px per row (app.js dot)
    const R = 1.725; // 1.35 dp
    const CUT = 3.83; // 3 dp
    const MIN_MG = 40; // the plot's range (above 300 pins to the top)
    const MAX_MG = 300;
    // The column's work (the profile over 261 levels, then each row's dots) is too much for the callback that lays the
    // page out: the watchdog stopped a Descent Mk3 43 there (#217). So it is done once per payload, window and plot,
    // in steps, each in a callback of its own (AppView.prep, after the layout asked for it: [want]), and kept: [key,
    // rows' dots, rows' colours, the three shares, the plot, the payload's history it came from]. Meanwhile the page
    // shows the column it has for the same plot (the reading before), or none on the very first frame.
    var _col as Array? = null;
    // the column being prepared: [d, bx, hours, history, key, step, levels, shares, next reading, the one before]
    var want as Array? = null;

    // what the column depends on besides the payload's history: the window and the readings in it, the limits, the plot
    function colKey(d as AppData, bx as Array, hours as Number) as String {
        return "" + hours + ":" + first(d.hm, hours * 60) + ":" + d.hm.size() + ":" + d.bands + ":" + bx;
    }

    // the payload's history, the column's own (its identity: a new payload is a new array)
    function hist() {
        var p = Link.payload();
        return p != null ? p.get("hist") : null;
    }

    // one step of the column's work ([want]); true once the column is ready
    function prepare() as Boolean {
        var w = want;
        if (w == null) {
            return true;
        }
        var d = w[0] as AppData;
        var bx = w[1] as Array;
        var hours = w[2] as Number;
        var st = w[5] as Number;
        var b = d.bands;
        var n = MAX_MG - MIN_MG + 1;
        if (st == 0) {
            // the time at each level (index mg/dL − MIN_MG), followed level by level between readings ≤ 15 min apart,
            // and the readings per band; up to 96 readings a callback ([8] the next one, [9] the one before it)
            var occ = w[6];
            var share = w[7];
            if (occ == null) {
                occ = new [n];
                for (var i = 0; i < n; i++) {
                    occ[i] = 0.0;
                }
                share = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
                w[6] = occ;
                w[7] = share;
                w[8] = first(d.hm, hours * 60);
                w[9] = null;
            }
            var prev = w[9];
            var k = w[8] as Number;
            var end = k + 96 < d.hm.size() ? k + 96 : d.hm.size();
            for (; k < end; k++) {
                var v = d.hv[k];
                if (v == null) {
                    prev = null;
                    continue;
                }
                var vv = v < MIN_MG ? MIN_MG : v > MAX_MG ? MAX_MG : v as Number;
                var si = v <= b[0] ? 0 : v < b[1] ? 1 : v <= b[2] ? 2 : v < b[3] ? 3 : v < b[4] ? 4 : v < b[5] ? 5 : 6;
                share[si] = share[si] + 1.0;
                if (prev != null) {
                    var pv = prev as Number;
                    var lo = pv < vv ? pv : vv;
                    var hi = pv < vv ? vv : pv;
                    var each = 300.0 / (hi - lo + 1);
                    for (var gg = lo - MIN_MG; gg <= hi - MIN_MG; gg++) {
                        occ[gg] += each;
                    }
                }
                prev = vv;
            }
            w[8] = k;
            w[9] = prev;
            if (k < d.hm.size()) {
                return false;
            }
            if (prev != null) {
                occ[(prev as Number) - MIN_MG] += 60.0;
            }
        } else if (st == 1) {
            // smoothed by about 4 mg/dL: two running box sums of 9 and 11 (a Gaussian's σ 4.1)
            var sm = w[6] as Array;
            var widths = [9, 11];
            for (var p = 0; p < 2; p++) {
                var hw = (widths[p] as Number) / 2;
                var out = new [n];
                var acc = 0.0;
                var cnt = 0;
                for (var j = 0; j < hw && j < n; j++) {
                    acc += sm[j];
                    cnt++;
                }
                for (var i = 0; i < n; i++) {
                    if (i + hw < n) {
                        acc += sm[i + hw];
                        cnt++;
                    }
                    if (i - hw - 1 >= 0) {
                        acc -= sm[i - hw - 1];
                        cnt--;
                    }
                    out[i] = acc / cnt;
                }
                sm = out;
            }
            w[6] = sm;
        } else if (st == 2) {
            // each band rescaled to its share of the readings (level i is MIN_MG + i mg/dL, its band by the limits)
            var sm = w[6] as Array;
            var share = w[7] as Array;
            var tot = 0.0;
            for (var i = 0; i < 7; i++) {
                tot += share[i];
            }
            var bix = new [n];
            var area = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
            for (var i = 0; i < n; i++) {
                var v = MIN_MG + i;
                var bi = v <= b[0] ? 0 : v < b[1] ? 1 : v <= b[2] ? 2 : v < b[3] ? 3 : v < b[4] ? 4 : v < b[5] ? 5 : 6;
                bix[i] = bi;
                area[bi] += sm[i];
            }
            for (var i = 0; i < n && tot > 0; i++) {
                var bi = bix[i] as Number;
                if (area[bi] > 0) {
                    sm[i] = sm[i] * (share[bi] / tot) / area[bi];
                }
            }
            // [7]: the shares below / in / above (whole %, largest remainder)
            w[7] = tot > 0 ? pct3((share[0] + share[1]) / tot, (share[2] + share[3]) / tot, (share[4] + share[5] + share[6]) / tot) : [0, 0, 0];
        } else {
            // each row's dots and colour
            var prof = w[6] as Array;
            var sideW = (bx[1] - bx[0]) * 0.22;
            var maxDots = Math.floor(sideW * 0.9 / PITCH).toNumber();
            var rows = Math.floor((bx[3] - bx[2]) / PITCH).toNumber();
            var mass = new [rows];
            var peak = 0.0;
            for (var i = 0; i < rows; i++) {
                var yb = bx[3] - i * PITCH;
                var g0 = G(bx, yb);
                var g1 = G(bx, yb - PITCH);
                var s = 0.0;
                for (var gg = Math.floor(g0).toNumber(); gg < g1; gg++) {
                    var k = gg - MIN_MG;
                    var ov = (gg + 1 < g1 ? gg + 1 : g1) - (gg > g0 ? gg : g0);
                    if (k >= 0 && k < n && ov > 0) {
                        s += (prof[k] as Float) * ov;
                    }
                }
                mass[i] = s;
                if (s > peak) {
                    peak = s;
                }
            }
            var nds = new [rows];
            var cols = new [rows];
            for (var i = 0; i < rows; i++) {
                var nd = peak > 0 ? Math.round(mass[i] / peak * maxDots).toNumber() : 0;
                if (nd == 0 && peak > 0 && mass[i] > peak * 0.004) {
                    nd = 1;
                }
                nds[i] = nd;
                cols[i] = nd > 0 ? AppMath.pal(d.band(G(bx, bx[3] - i * PITCH - PITCH / 2))) : 0;
            }
            _col = [w[4], nds, cols, w[7], bx.toString(), w[3]];
            want = null;
            return true;
        }
        w[5] = st + 1;
        return false;
    }

    // three shares as whole percentages adding up to 100 (largest remainder; GlucoseGraph.pct3)
    function pct3(a as Float, b as Float, c as Float) as Array<Number> {
        var raw = [a * 100, b * 100, c * 100];
        var base = [Math.floor(raw[0]).toNumber(), Math.floor(raw[1]).toNumber(), Math.floor(raw[2]).toNumber()];
        var left = Math.round(raw[0] + raw[1] + raw[2]).toNumber() - base[0] - base[1] - base[2];
        while (left > 0) {
            var best = 0;
            for (var i = 1; i < 3; i++) {
                if (raw[i] - base[i] > raw[best] - base[best]) {
                    best = i;
                }
            }
            base[best] += 1;
            raw[best] = base[best].toFloat();
            left--;
        }
        return base;
    }

    // the % column from its prepared rows (prepare); asks for them when they are not this payload's yet
    function pctColumn(g as AppG, d as AppData, bx as Array, hours as Number, lfs as Number) as Void {
        var key = colKey(d, bx, hours);
        var c = _col;
        var h0 = hist();
        var w = want;
        if ((c == null || !key.equals(c[0]) || c[5] != h0) && (w == null || w[3] != h0 || !key.equals(w[4]))) {
            want = [d, bx, hours, h0, key, 0, null, null, 0, null];
        }
        if (c == null || !bx.toString().equals(c[4])) {
            return;
        }
        var nds = c[1] as Array;
        var cols = c[2] as Array;
        var three = c[3] as Array;
        var rows = nds.size();
        var tint = [[Y(bx, d.bands[2]), Y(bx, d.bands[1]), AppMath.pal("tight"), 0.13],
            [Y(bx, d.bands[3]), Y(bx, d.bands[2]), AppMath.pal("target"), 0.075]];
        var h = R + CUT;
        for (var i = 0; i < rows; i++) {
            var nd = nds[i] as Number;
            if (nd == 0) {
                continue;
            }
            var cy = bx[3] - i * PITCH - PITCH / 2;
            var endX = bx[0] + PITCH / 2 + (nd - 1) * PITCH + R;
            // the mask: the plot's background over the line (black, then the band's tint where the row lies in one)
            var y0 = cy - h;
            var y1 = cy + h;
            g.r(bx[0], y0, endX + CUT - bx[0], y1 - y0, h, AppMath.BLACK, 1.0, 0);
            for (var t = 0; t < 2; t++) {
                var t0 = y0 > tint[t][0] ? y0 : tint[t][0];
                var t1 = y1 < tint[t][1] ? y1 : tint[t][1];
                if (t1 > t0) {
                    g.r(bx[0], t0, endX + CUT - bx[0], t1 - t0, 0, tint[t][2], tint[t][3], 0);
                }
            }
        }
        for (var i = 0; i < rows; i++) {
            var nd = nds[i] as Number;
            var cy = bx[3] - i * PITCH - PITCH / 2;
            for (var k = 0; k < nd; k++) {
                g.c(bx[0] + PITCH * (k + 0.5), cy, R, cols[i] as Number, 1.0, 0);
            }
        }
        var lx = bx[0] - 5;
        var pb = three[0] as Number;
        var pin = three[1] as Number;
        var pa = three[2] as Number;
        var ya = Y(bx, 230) < Y(bx, d.bands[3]) - 2 ? Y(bx, 230) : Y(bx, d.bands[3]) - 2;
        if (pa > 0) {
            g.t(lx, ya + lfs * 0.35, pa + "%", lfs, 600, AppMath.SOFT, END);
        }
        g.t(lx, (Y(bx, d.bands[1]) + Y(bx, d.bands[3])) / 2 + lfs * 0.35, pin + "%", lfs + 1, 700, AppMath.INK, END);
        var yb = Y(bx, 55) > Y(bx, d.bands[1]) + lfs * 0.6 ? Y(bx, 55) : Y(bx, d.bands[1]) + lfs * 0.6;
        if (pb > 0) {
            g.t(lx, yb + lfs * 0.35, pb + "%", lfs, 600, AppMath.SOFT, END);
        }
    }

    // the bottom row: the reading's time when late, then Sukrara's pill (app.js chipRow())
    function chipRow(g as AppG, d as AppData, y as Numeric, fs as Numeric) as Boolean {
        var late = d.g != null && d.age >= 1.5 && d.age < 20;
        var aw = late ? AppMath.tw(d.readAt, fs) : 0.0;
        var pw = 0.0;
        var p = d.pill;
        if (p != null) {
            pw = fs * 0.5 + fs * 0.78 + fs * 0.3 + AppMath.tw(p[1] as String, fs) + fs * 0.6;
        }
        var gap = late && p != null ? 12 : 0;
        var tot = aw + gap + pw;
        var x0 = -tot / 2;
        if (late) {
            g.t(x0, y + fs * 0.35, d.readAt, fs, 500, AppMath.SOFT, START);
        }
        if (p != null) {
            AppParts.pill(g, x0 + aw + gap, y, p, fs, START);
        }
        return tot > 0;
    }

    function sensorTag(g as AppG, d as AppData, x as Numeric, y as Numeric, fs as Numeric) as Void {
        if (d.sensorDays == null) {
            return;
        }
        var s = d.sensorDays + "d";
        var w = fs + 4 + AppMath.tw(s, fs);
        var x0 = x - w / 2;
        g.icon("sensor", x0 + fs / 2.0, y - fs * 0.34, fs, AppMath.SOFT);
        g.t(x0 + fs + 4, y, s, fs, 600, AppMath.SOFT, START);
    }

    // a centred text kept inside the screen (SPEC §14.1) on the font-resource watches (no tinted bitmaps: their glyphs
    // draw about 12 % wider than the atlases' widths, AppMath.tw): the first of [sizes] (units, largest first; sizes the
    // class's fonts carry) at which it fits inside the rim's hints at its line, else two lines at the last. The atlas
    // watches draw it at the first, as designed
    function fitT(g as AppG, y as Numeric, s as String, sizes as Array<Number>, fw as Number, col as Number) as Void {
        var fs = sizes[0];
        if (AppFontTable.PLAIN) {
            for (var i = 0; i < sizes.size(); i++) {
                fs = sizes[i];
                if (AppMath.tw(s, fs) * 1.12 <= 2 * room(y, fs)) {
                    g.t(0, y, s, fs, fw, col, MIDDLE);
                    return;
                }
            }
            // two lines, broken at the space nearest the middle
            var cut = -1;
            var mid = s.length() / 2;
            for (var k = 0; k < s.length(); k++) {
                if (s.substring(k, k + 1).equals(" ") && (cut < 0 || (k - mid).abs() < (cut - mid).abs())) {
                    cut = k;
                }
            }
            if (cut > 0) {
                g.t(0, y - fs * 0.6, s.substring(0, cut) as String, fs, fw, col, MIDDLE);
                g.t(0, y + fs * 0.6, s.substring(cut + 1, s.length()) as String, fs, fw, col, MIDDLE);
                return;
            }
        }
        g.t(0, y, s, fs, fw, col, MIDDLE);
    }

    // the half width a line of [fs] at baseline [y] has inside the screen and clear of the rim's hints (units)
    function room(y as Numeric, fs as Numeric) as Float {
        if (AppFontTable.RECT) {
            return hw() - 8;
        }
        var top = y - fs * 0.75;
        var e = AppMath.abs(top) > AppMath.abs(y) ? top : y;
        var q = 176 * 176 - e * e;
        return q > 0 ? Math.sqrt(q).toFloat() : 0.0;
    }

    function pageDots(g as AppG, n as Number, i as Number) as Void {
        for (var k = 0; k < n; k++) {
            var o = (k - (n - 1) / 2.0) * 13;
            var r = k == i ? 4 : 3;
            var c = k == i ? AppMath.INK : AppMath.DIM;
            if (AppFontTable.RECT) {
                g.c(-hw() + 24 + o, hh() - 9, r, c, 1.0, 0);
            } else {
                g.c(o, 180, r, c, 1.0, 0);
            }
        }
    }

    // ---- page 2: the numbers (S.appNums), the phone's Glucose report simplified; [days] the period shown -------
    function pctText(pm as Number) as String {
        return (pm > 0 && pm < 10 ? (pm / 10.0).format("%.1f") : ((pm + 5) / 10).toString()) + " %";
    }

    // the numbers' geometry (units): round (app.js S.appNums) or the rectangles' own (R.appNums: chips across the
    // top, the bar down the left edge, the three shares, then average / GMI / CV across the bottom):
    // [chip w, chips' top, chip h, bar x, bar top, bar bottom, label x, value x, the rows' y (3), the goal's dy,
    //  the rule's y, its half width, the metrics' y, their spacing]
    function numsAt() as Array {
        if (!AppFontTable.RECT) {
            return [50, -152, 30, -142, -112, 64, -116, 152, [-96, -30, 38], 22, 80, 140, 114, 92];
        }
        var w = hw();
        var h = hh();
        var cw = (2 * w - 50) / 4;
        cw = cw < 64 ? cw : 64;
        var top = -h + 16 + 52;
        var bot = h - 86;
        var st = (bot - top) / 3;
        return [cw, -h + 16, 32, -w + 14, top, bot, -w + 40, w - 12, [top + 22, top + 22 + st, top + 22 + 2 * st], 24,
            bot + 14, w - 12, bot + 48, (2 * w - 40) / 3];
    }

    function appNums(g as AppG, d as AppData, days as Number) as Void {
        var L = numsAt();
        var pers = [1, 7, 14, 30];
        var cw = L[0] as Numeric;
        var cy = L[1] as Numeric;
        var ch = L[2] as Numeric;
        var gp = 6;
        var x0 = -(pers.size() * cw + (pers.size() - 1) * gp) / 2;
        for (var i = 0; i < pers.size(); i++) {
            var on = pers[i] == days;
            var x = x0 + i * (cw + gp);
            if (on) {
                g.r(x, cy, cw, ch, ch / 2, AppMath.CARD2, 1.0, 0);
            }
            g.r(x, cy, cw, ch, ch / 2, on ? AppMath.SOFT : AppMath.FAINT, 1.0, 1.5);
            g.t(x + cw / 2, cy + ch * 0.7, pers[i] + "d", 17, on ? 700 : 500, on ? AppMath.INK : AppMath.SOFT, MIDDLE);
        }
        var rep = report(days);
        if (rep == null) {
            g.t(0, 0, AppWords.s(Rez.Strings.RepNone), 19, 600, AppMath.SOFT, MIDDLE);
            pageDots(g, 2, 1);
            return;
        }
        var b = rep.get("b") as Array;
        // the stacked bar, very low at the bottom
        var tot = 0;
        for (var i = 0; i < 6; i++) {
            tot += b[i] as Number;
        }
        var y = (L[5] as Numeric).toFloat();
        var span = (L[5] as Numeric) - (L[4] as Numeric);
        for (var i = 0; tot > 0 && i < 6; i++) {
            var v = b[i] as Number;
            if (v == 0) {
                continue;
            }
            var h = v * span.toFloat() / tot;
            var hh0 = h - (h > 3 ? 2 : 0);
            g.r(L[3], y - h + (h > 3 ? 1 : 0), 12, hh0 > 1.5 ? hh0 : 1.5, 2, AppMath.pal(AppParts.BANDS[i]), 1.0, 0);
            y -= h;
        }
        var above = (b[4] as Number) + (b[5] as Number);
        var inR = (b[2] as Number) + (b[3] as Number);
        var below = (b[0] as Number) + (b[1] as Number);
        // the goals met, as the phone's report card decides them (rep ok: one bit per Goal)
        var ok = rep.get("ok") instanceof Number ? rep.get("ok") as Number : 0;
        var ys = L[8] as Array;
        row(g, L, ys[0], AppWords.s(Rez.Strings.RepAbove), pctText(above), "< 25%", (ok & 2) != 0, false, null);
        row(g, L, ys[1], AppWords.s(Rez.Strings.RepIn), pctText(inR), "> 70%", (ok & 4) != 0, true,
            Lang.format(AppWords.s(Rez.Strings.RepVeryGood), [pctText(b[2] as Number)]));
        row(g, L, ys[2], AppWords.s(Rez.Strings.RepBelow), pctText(below), "< 4%", (ok & 8) != 0, false, null);
        g.line(-(L[11] as Numeric), L[10], L[11], L[10], AppMath.GRID, 1.5);
        var avg = rep.get("avg");
        var gmi = rep.get("gmi");
        var cv = rep.get("cv");
        var ms = [
            [avg != null ? AppMath.fmtG(avg as Numeric, d.unit) : "--", AppWords.s(Rez.Strings.RepAverage), (ok & 32) != 0],
            [gmi != null ? (gmi as Numeric).toFloat().format("%.1f") + " %" : "--", AppWords.s(Rez.Strings.RepGmi), (ok & 64) != 0],
            [cv != null ? (cv as Numeric).toFloat().format("%.0f") + " %" : "--", AppWords.s(Rez.Strings.RepCv), (ok & 128) != 0]
        ];
        var my = L[12] as Numeric;
        for (var i = 0; i < 3; i++) {
            var x = (i - 1) * (L[13] as Numeric);
            g.t(x, my, ms[i][0] as String, 24, 700, AppMath.INK, MIDDLE);
            var lw = AppMath.tw(ms[i][1] as String, 15) + 18;
            mark(g, x - lw / 2, my + 17, ms[i][2] as Boolean, 0.85);
            g.t(x - lw / 2 + 14, my + 23, ms[i][1] as String, 15, 600, AppMath.SOFT, START);
        }
        pageDots(g, 2, 1);
    }

    // the report of [days] (payload rep), null without a reading in it
    function report(days as Number) as Dictionary? {
        var p = Link.payload();
        var rep = p != null ? p.get("rep") : null;
        if (!(rep instanceof Array)) {
            return null;
        }
        for (var i = 0; i < rep.size(); i++) {
            var r = rep[i];
            if (r instanceof Dictionary && r.get("d") == days) {
                return r;
            }
        }
        return null;
    }

    // a goal's check (met, green) or cross (orange), [x, y] the check's left end, [sc] its size
    function mark(g as AppG, x as Numeric, y as Numeric, met as Boolean, sc as Float) as Void {
        if (met) {
            g.l([x, y, x + 3.5 * sc, y + 3.5 * sc, x + 9.5 * sc, y - 3.5 * sc], AppMath.pal("tight"), 1.0, 2, null);
        } else {
            g.line(x + 1, y - 4 * sc, x + 1 + 7 * sc, y + 3 * sc, AppMath.pal("high"), 2);
            g.line(x + 1 + 7 * sc, y - 4 * sc, x + 1, y + 3 * sc, AppMath.pal("high"), 2);
        }
    }

    function goal(g as AppG, x as Numeric, yy as Numeric, text as String, met as Boolean) as Void {
        var w = 26 + AppMath.tw(text, 14);
        g.r(x, yy - 15, w, 22, 11, met ? 0x173A27 : 0x3D2A10, 1.0, 0);
        mark(g, x + 8, yy - 4.5, met, 1.0);
        g.t(x + 21, yy + 1, text, 14, 600, AppMath.pal(met ? "tight" : "high"), START);
    }

    function row(g as AppG, L as Array, yy as Numeric, label as String, pct as String, gtext as String, met as Boolean, big as Boolean, sub as String?) as Void {
        var lx = L[6] as Numeric;
        var gy = yy + (L[9] as Numeric);
        g.t(lx, yy, label, big ? 22 : 19, big ? 700 : 600, AppMath.INK, START);
        g.t(L[7], yy + (big ? 4 : 0), pct, big ? 32 : 22, 700, AppMath.INK, END);
        goal(g, lx, gy, gtext, met);
        if (sub != null) {
            g.t(lx + 26 + AppMath.tw(gtext, 14) + 10, gy + 1, sub, 15, 500, AppMath.SOFT, START);
        }
    }

    // ---- waiting for the phone (S.setup, SPEC §4.3): Sukrara's mark, what it waits for, Open on phone; after the
    // press ([opened]) Look at your phone. Never a phone screen or setting: those change, the watch app doesn't
    function setup(g as AppG, opened as Boolean) as Void {
        // Sukrara for Android's own icon (the disc, the cube, the dot: the phone app that needs to be open), not the
        // watch's launcher icon (the owner, 2026-11-22); its bitmap made at the class's size (tools/fonts.py)
        g.bmp(Rez.Drawables.PhoneIcon, 0, -92, 60);
        g.t(0, -26, AppWords.s(Rez.Strings.SetupTitle), 26, 700, AppMath.INK, MIDDLE);
        g.t(0, 8, AppWords.s(Rez.Strings.SetupLine1), 18, 500, AppMath.SOFT, MIDDLE);
        g.t(0, 33, AppWords.s(Rez.Strings.SetupLine2), 18, 500, AppMath.SOFT, MIDDLE);
        if (opened) {
            var look = AppWords.s(Rez.Strings.LookPhone);
            var lw = AppMath.tw(look, 22) + 30;
            g.icon("phone", -lw / 2 + 11, 90, 22, AppMath.INK);
            g.t(-lw / 2 + 30, 98, look, 22, 700, AppMath.INK, START);
            return;
        }
        var bt = AppWords.s(Rez.Strings.OpenOnPhone);
        var bw0 = AppMath.tw(bt, 22) + 26;
        var w = bw0 + 56;
        g.r(-w / 2, 70, w, 52, 26, AppMath.INK, 1.0, 0);
        g.icon("phone", -bw0 / 2 + 10, 96, 20, AppMath.ON);
        g.t(-bw0 / 2 + 26, 104, bt, 22, 700, AppMath.ON, START);
        btnHint(g, 60, AppMath.INK);
    }

    // a hint at a button (app.js btnHint), named by the 390's angle (START 60°, BACK 120°, UP 270°, DOWN 240°): round, a
    // short arc on the rim at the class's angle; rectangles, a bar on the right edge at the button's height; no UP / DOWN
    // hint where there are none. Returns where it is ([x, y], units), null when not drawn
    function btnHint(g as AppG, ang as Numeric, col as Number) as Array? {
        var i = [60, 120, 270, 240].indexOf(ang);
        if (i >= 2 && two()) {
            return null;
        }
        var k = AppFontTable.KEYS[i] as Number;
        if (AppFontTable.RECT) {
            var y = k / AppFontTable.S - hh();
            var x = hw() - 3;
            g.l([x, y - 20, x, y + 20], col, 0.9, 5, null);
            return [x, y];
        }
        g.a(0, 0, 190, k - 9, k + 9, col, 0.9, 5, true);
        return AppMath.P(190, k);
    }
}
