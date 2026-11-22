// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Math;
import Toybox.WatchUi;

// The app's screens, ported from app.js function by function (S.appA, chart(), pctColumn(), chipRow() …): each one
// pushes its display list from the input `d` (AppData).
module AppScreens {
    const START = -1;
    const MIDDLE = 0;
    const END = 1;
    const LO = 40;
    const HI = 300;

    // ---- page 1: the widget (S.appA) ---------------------------------------------------------------------------
    function appA(g as AppG, d as AppData) as Void {
        g.t(0, -150, d.clock, 22, 600, AppMath.SOFT, MIDDLE);
        if (!d.sent || d.g == null && !d.sensorState.equals("warmup")) {
            g.t(0, -60, AppWords.s(d.sent ? Rez.Strings.NoReadings : Rez.Strings.Waiting), 22, 600, AppMath.SOFT, MIDDLE);
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
        var bx = [-134, 146, -22, 88];
        chart(g, d, bx, d.hours, 14, 10);
        if (d.side) {
            pctColumn(g, d, bx, d.hours, 14);
        }
        if (!chipRow(g, d, 146, 19)) {
            sensorTag(g, d, 0, 152, 17);
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

    // the chart's scale: Kovatchev's symmetrised risk over 40–300 (app.js chart(): above 300 pins to the top)
    function Y(bx as Array, v as Numeric) as Float {
        var k0 = AppMath.K(LO);
        return (bx[3] - (AppMath.K(AppMath.clamp(v, LO, HI)) - k0) / (AppMath.K(HI) - k0) * (bx[3] - bx[2])).toFloat();
    }

    // its inverse: the mg/dL at a height
    function G(bx as Array, y as Numeric) as Float {
        var k0 = AppMath.K(LO);
        var k = k0 + (bx[3] - y) / (bx[3] - bx[2]).toFloat() * (AppMath.K(HI) - k0);
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
    function chart(g as AppG, d as AppData, bx as Array, hours as Number, lfs as Number, ms as Number) as Void {
        var b = d.bands;
        var w = bx[1] - bx[0];
        g.r(bx[0], Y(bx, b[2]), w, Y(bx, b[1]) - Y(bx, b[2]), 0, AppMath.pal("tight"), 0.13, 0);
        g.r(bx[0], Y(bx, b[3]), w, Y(bx, b[2]) - Y(bx, b[3]), 0, AppMath.pal("target"), 0.075, 0);
        var lims = [b[1], b[3]];
        for (var i = 0; i < 2; i++) {
            var y = Y(bx, lims[i]);
            g.l([bx[0], y, bx[1], y], AppMath.FAINT, 1.0, 1, [3, 4]);
            var lab = d.unit.equals("mmol") ? ((lims[i] as Number) / 18.0182).format("%.1f") : lims[i].toString();
            g.t(bx[1] + 5, y + 5, lab, lfs, 500, AppMath.DIM, START);
        }
        // the line: one polyline per run of one colour; a gap breaks it
        var old = d.age >= 5;
        var lw = 3.4;
        var run = [] as Array;
        var col = -1;
        var lastX = null;
        var lastY = null;
        var lastV = null;
        for (var k = 1; k < d.hm.size(); k++) {
            var va = d.hv[k - 1];
            var vb = d.hv[k];
            var ma = d.hm[k - 1] as Float;
            if (va == null || vb == null || ma > hours * 60) {
                if (run.size() > 0) {
                    g.l(run, old ? AppMath.GREY : col, 1.0, lw, null);
                }
                run = [];
                continue;
            }
            var c = AppMath.pal(d.band(((va as Number) + (vb as Number)) / 2.0));
            var xa = X(bx, hours, ma);
            var ya = Y(bx, va as Number);
            if (c != col && run.size() > 0) {
                g.l(run, old ? AppMath.GREY : col, 1.0, lw, null);
                run = [];
            }
            if (run.size() == 0) {
                run.add(xa);
                run.add(ya);
            }
            col = c;
            lastX = X(bx, hours, d.hm[k] as Float);
            lastY = Y(bx, vb as Number);
            lastV = vb;
            run.add(lastX);
            run.add(lastY);
        }
        if (run.size() > 0) {
            g.l(run, old ? AppMath.GREY : col, 1.0, lw, null);
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
        for (var i = d.trm.size() - 1; i >= 0; i--) {
            var m = d.trm[i] as Float;
            var k = d.trk[i] as String;
            if (m > hours * 60 || m < 0 || !marks(d, k, "point")) {
                continue;
            }
            var x = X(bx, hours, m);
            var y = bx[3] - 7;
            var c = AppMath.pal(k);
            if (k.equals("carbs")) {
                var with = false;
                for (var j = 0; j < d.trm.size(); j++) {
                    if (!"carbs".equals(d.trk[j]) && AppMath.abs((d.trm[j] as Float) - m) < 0.5) {
                        with = true;
                    }
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
            if (marks(d, k, "amount")) {
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
    const MIN_MG = 20;
    const MAX_MG = 450;
    var _prof as Array? = null;
    var _profKey as String = "";

    // the time at each level (index mg/dL − MIN_MG): followed level by level between readings ≤ 15 min apart,
    // smoothed by 4 mg/dL, each band rescaled to its share; [3] = the shares below / in / above (whole %, largest
    // remainder)
    function profile(d as AppData, hours as Number) as Array {
        var key = d.hm.size() + ":" + hours + ":" + (d.hm.size() > 0 ? d.hm[d.hm.size() - 1] : 0) + ":" + d.hv.size();
        if (_prof != null && key.equals(_profKey)) {
            return _prof as Array;
        }
        var n = MAX_MG - MIN_MG + 1;
        var occ = new [n];
        for (var i = 0; i < n; i++) {
            occ[i] = 0.0;
        }
        var prev = null;
        var share = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        var bandIx = {"veryLow" => 0, "low" => 1, "tight" => 2, "target" => 3, "high" => 4, "veryHigh" => 5, "extremelyHigh" => 6};
        for (var k = 0; k < d.hm.size(); k++) {
            var v = d.hv[k];
            if (v == null || (d.hm[k] as Float) > hours * 60) {
                prev = null;
                continue;
            }
            var vv = (v as Number) < MIN_MG ? MIN_MG : (v as Number) > MAX_MG ? MAX_MG : v as Number;
            share[bandIx.get(d.band(vv)) as Number] += 1.0;
            if (prev != null) {
                var pv = prev as Number;
                var lo = pv < vv ? pv : vv;
                var hi = pv < vv ? vv : pv;
                var lv = hi - lo + 1;
                for (var gg = lo; gg <= hi; gg++) {
                    occ[gg - MIN_MG] += 300.0 / lv;
                }
            }
            prev = vv;
        }
        if (prev != null) {
            occ[(prev as Number) - MIN_MG] += 60.0;
        }
        // Gaussian, sigma 4 mg/dL
        var rr = 12;
        var ker = new [2 * rr + 1];
        for (var j = 0; j <= 2 * rr; j++) {
            var z = (j - rr) / 4.0;
            ker[j] = Math.pow(Math.E, -0.5 * z * z);
        }
        var sm = new [n];
        for (var i = 0; i < n; i++) {
            var acc = 0.0;
            var ws = 0.0;
            for (var j = -rr; j <= rr; j++) {
                var ii = i + j;
                if (ii >= 0 && ii < n) {
                    acc += occ[ii] * ker[j + rr];
                    ws += ker[j + rr];
                }
            }
            sm[i] = acc / ws;
        }
        var tot = 0.0;
        for (var i = 0; i < 7; i++) {
            tot += share[i];
        }
        var area = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
        for (var i = 0; i < n; i++) {
            area[bandIx.get(d.band(MIN_MG + i)) as Number] += sm[i];
        }
        for (var i = 0; i < n && tot > 0; i++) {
            var bi = bandIx.get(d.band(MIN_MG + i)) as Number;
            if (area[bi] > 0) {
                sm[i] = sm[i] * (share[bi] / tot) / area[bi];
            }
        }
        var three = tot > 0 ? pct3((share[0] + share[1]) / tot, (share[2] + share[3]) / tot, (share[4] + share[5] + share[6]) / tot) : [0, 0, 0];
        _prof = [sm, three];
        _profKey = key;
        return _prof as Array;
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

    function pctColumn(g as AppG, d as AppData, bx as Array, hours as Number, lfs as Number) as Void {
        var pr = profile(d, hours);
        var prof = pr[0] as Array;
        var three = pr[1] as Array;
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
                if (k >= 0 && k < prof.size() && ov > 0) {
                    s += (prof[k] as Float) * ov;
                }
            }
            mass[i] = s;
            if (s > peak) {
                peak = s;
            }
        }
        if (peak > 0) {
            var tint = [[d.bands[1], d.bands[2], AppMath.pal("tight"), 0.13], [d.bands[2], d.bands[3], AppMath.pal("target"), 0.075]];
            var h = R + CUT;
            for (var i = 0; i < rows; i++) {
                var nd = Math.round(mass[i] / peak * maxDots).toNumber();
                if (nd == 0 && mass[i] > peak * 0.004) {
                    nd = 1;
                }
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
                    var t0 = y0 > Y(bx, tint[t][1]) ? y0 : Y(bx, tint[t][1]);
                    var t1 = y1 < Y(bx, tint[t][0]) ? y1 : Y(bx, tint[t][0]);
                    if (t1 > t0) {
                        g.r(bx[0], t0, endX + CUT - bx[0], t1 - t0, 0, tint[t][2], tint[t][3], 0);
                    }
                }
            }
            for (var i = 0; i < rows; i++) {
                var nd = Math.round(mass[i] / peak * maxDots).toNumber();
                if (nd == 0 && mass[i] > peak * 0.004) {
                    nd = 1;
                }
                var cy = bx[3] - i * PITCH - PITCH / 2;
                var c = AppMath.pal(d.band(G(bx, cy)));
                for (var k = 0; k < nd; k++) {
                    g.c(bx[0] + PITCH * (k + 0.5), cy, R, c, 1.0, 0);
                }
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

    function pageDots(g as AppG, n as Number, i as Number) as Void {
        for (var k = 0; k < n; k++) {
            g.c((k - (n - 1) / 2.0) * 13, 180, k == i ? 4 : 3, k == i ? AppMath.INK : AppMath.DIM, 1.0, 0);
        }
    }

    // ---- page 2: the numbers (S.appNums), the phone's Glucose report simplified; [days] the period shown -------
    function pctText(pm as Number) as String {
        return (pm > 0 && pm < 10 ? (pm / 10.0).format("%.1f") : ((pm + 5) / 10).toString()) + " %";
    }

    function appNums(g as AppG, d as AppData, days as Number) as Void {
        var pers = [1, 7, 14, 30];
        var cw = 50;
        var gp = 6;
        var x0 = -(pers.size() * cw + (pers.size() - 1) * gp) / 2;
        for (var i = 0; i < pers.size(); i++) {
            var on = pers[i] == days;
            var x = x0 + i * (cw + gp);
            if (on) {
                g.r(x, -152, cw, 30, 15, AppMath.CARD2, 1.0, 0);
            }
            g.r(x, -152, cw, 30, 15, on ? AppMath.SOFT : AppMath.FAINT, 1.0, 1.5);
            g.t(x + cw / 2, -131, pers[i] + "d", 17, on ? 700 : 500, on ? AppMath.INK : AppMath.SOFT, MIDDLE);
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
        var y = 64.0;
        for (var i = 0; tot > 0 && i < 6; i++) {
            var v = b[i] as Number;
            if (v == 0) {
                continue;
            }
            var h = v * 176.0 / tot;
            var hh = h - (h > 3 ? 2 : 0);
            g.r(-142, y - h + (h > 3 ? 1 : 0), 12, hh > 1.5 ? hh : 1.5, 2, AppMath.pal(AppParts.BANDS[i]), 1.0, 0);
            y -= h;
        }
        var above = (b[4] as Number) + (b[5] as Number);
        var inR = (b[2] as Number) + (b[3] as Number);
        var below = (b[0] as Number) + (b[1] as Number);
        row(g, -96, AppWords.s(Rez.Strings.RepAbove), pctText(above), "< 25%", above < 250, false, null);
        row(g, -30, AppWords.s(Rez.Strings.RepIn), pctText(inR), "> 70%", inR > 700, true,
            Lang.format(AppWords.s(Rez.Strings.RepVeryGood), [pctText(b[2] as Number)]));
        row(g, 38, AppWords.s(Rez.Strings.RepBelow), pctText(below), "< 4%", below < 40, false, null);
        g.line(-140, 80, 140, 80, AppMath.GRID, 1.5);
        var avg = rep.get("avg");
        var gmi = rep.get("gmi");
        var cv = rep.get("cv");
        var ms = [
            [avg != null ? AppMath.fmtG(avg as Numeric, d.unit) : "--", AppWords.s(Rez.Strings.RepAverage), avg != null && (avg as Numeric) < 154],
            [gmi != null ? (gmi as Numeric).toFloat().format("%.1f") + " %" : "--", AppWords.s(Rez.Strings.RepGmi), gmi != null && (gmi as Numeric) < 7],
            [cv != null ? (cv as Numeric).toFloat().format("%.0f") + " %" : "--", AppWords.s(Rez.Strings.RepCv), cv != null && (cv as Numeric) <= 36]
        ];
        for (var i = 0; i < 3; i++) {
            var x = -92 + i * 92;
            g.t(x, 114, ms[i][0] as String, 24, 700, AppMath.INK, MIDDLE);
            var lw = AppMath.tw(ms[i][1] as String, 15) + 18;
            mark(g, x - lw / 2, 131, ms[i][2] as Boolean, 0.85);
            g.t(x - lw / 2 + 14, 137, ms[i][1] as String, 15, 600, AppMath.SOFT, START);
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

    function row(g as AppG, yy as Numeric, label as String, pct as String, gtext as String, met as Boolean, big as Boolean, sub as String?) as Void {
        g.t(-116, yy, label, big ? 22 : 19, big ? 700 : 600, AppMath.INK, START);
        g.t(152, yy + (big ? 4 : 0), pct, big ? 32 : 22, 700, AppMath.INK, END);
        goal(g, -116, yy + 22, gtext, met);
        if (sub != null) {
            g.t(-116 + 26 + AppMath.tw(gtext, 14) + 10, yy + 23, sub, 15, 500, AppMath.SOFT, START);
        }
    }

    // a hint at a button: a short arc on the rim at its angle (START 60°, BACK 120°, DOWN 240°, UP 270°)
    function btnHint(g as AppG, ang as Numeric, col as Number) as Void {
        g.a(0, 0, 190, ang - 9, ang + 9, col, 0.9, 5, true);
    }
}
