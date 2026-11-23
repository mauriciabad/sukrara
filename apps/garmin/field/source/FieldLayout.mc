// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Time;
import Toybox.Time.Gregorian;
import Toybox.WatchUi;

// The data field's input: the reading as the field draws it (ReadingInput, the payload's field profile) plus what the
// layout needs from it, in app.js's terms (contracts/watchface/app/SPEC.md §2: `d`).
class FieldInput {
    var g as Number? = null; // mg/dL; null = no reading
    var unit as String = "mgdl";
    var age as Float = 0.0; // minutes since the reading
    var readAt as String = ""; // its time, "20:38"
    var deg as Float? = null; // the arrow's angle (negative = up; the doubled arrow ±90); null = no trend
    var doubled as Boolean = false;
    var hm as Array = []; // the sparkline's readings: minutes before the reading, oldest first (the reading last, 0)
    var hv as Array = []; // their values
    var bands as Array = [55, 70, 140, 180, 250, 300];
    var noPhone as Boolean = true; // the phone never sent this field anything

    function initialize() {}

    function from(r as ReadingInput) as FieldInput {
        noPhone = !r.payload;
        unit = r.unit;
        bands = r.bands;
        g = r.mgdl;
        if (g == null) {
            return self;
        }
        age = r.ageS / 60.0;
        var c = Gregorian.info(new Time.Moment(r.readAt), Time.FORMAT_SHORT);
        readAt = (c.hour as Number) + ":" + (c.min as Number).format("%02d");
        deg = r.deg;
        doubled = r.doubled;
        if (deg != null && doubled) {
            deg = (deg as Float) < 0 ? -90.0 : 90.0;
        }
        var h = r.history;
        var n = h.size();
        for (var i = 0; i < n; i++) {
            if (h[i] instanceof Number || h[i] instanceof Float) {
                hm.add(5 * (n - i));
                hv.add(h[i]);
            }
        }
        hm.add(0);
        hv.add(g);
        return self;
    }

    // the band of [v] by the person's limits (app.js bandOf)
    function band(v as Numeric) as String {
        if (v < bands[0]) {
            return "veryLow";
        }
        if (v < bands[1]) {
            return "low";
        }
        if (v <= bands[2]) {
            return "tight";
        }
        if (v < bands[3]) {
            return "target";
        }
        if (v < bands[4]) {
            return "high";
        }
        if (v < bands[5]) {
            return "veryHigh";
        }
        return "extremelyHigh";
    }
}

// A slot in layout units (app.js slotBox): its edges from the screen's centre and the sides the screen cuts.
class FieldSlot {
    var x0 as Float = 0.0;
    var y0 as Float = 0.0;
    var x1 as Float = 0.0;
    var y1 as Float = 0.0;
    var top as Boolean = false; // obscured at the top (the slot touches the screen's top edge)
    var bot as Boolean = false;
    var whole as Boolean = false; // the slot is the screen

    // the slot [x, y, w, h] px on a [W] × [H] screen, obscured on [obs] (Graphics.OBSCURE_*)
    function initialize(x as Numeric, y as Numeric, w as Numeric, h as Numeric, W as Number, H as Number, obs as Number) {
        var s = FieldFontTable.S;
        x0 = (x - W / 2.0) / s;
        y0 = (y - H / 2.0) / s;
        x1 = (x + w - W / 2.0) / s;
        y1 = (y + h - H / 2.0) / s;
        top = (obs & 2) != 0; // DataField.OBSCURE_TOP; OBSCURE_BOTTOM 8
        bot = (obs & 8) != 0;
        whole = w >= W - 2 && h >= H - 2;
    }
}

// The data field's screens: app.js S.dfSlot's field (dfField) and S.dfAlert, on the one seam (input, slot) → model
// (FieldG). Every number is the reference's (contracts/watchface/app/SPEC.md §9, §14.4).
module FieldLayout {
    // Atkinson's big-number widths per em (app.js BIG)
    const BIGK = "0123456789:.LOHI";
    const BIGV = [0.656, 0.43, 0.569, 0.592, 0.621, 0.602, 0.617, 0.529, 0.621, 0.578, 0.253, 0.253, 0.548, 0.737, 0.699, 0.415];
    const GREY_LIMIT = 0x5C5C62;

    // the class's palette (app.js PAL / PAL_MIP)
    function pal(k as String) as Number {
        var mip = FieldFontTable.MIP;
        var keys = ["ink", "soft", "dim", "faint", "grey", "veryLow", "low", "tight", "target", "high", "veryHigh", "extremelyHigh"];
        var i = keys.indexOf(k);
        var a = mip
            ? [0xFFFFFF, 0xAAAAAA, 0x555555, 0x555555, 0xAAAAAA, 0xFF0000, 0xFF5555, 0x00FF55, 0xAAFF00, 0xFFAA00, 0xFF5500, 0xFF55FF]
            : [0xFFFFFF, 0xA3A3A8, 0x6E6E73, 0x3A3A3E, 0x8E8E93, 0xC2413B, 0xF0524F, 0x3DD47A, 0x8CC63F, 0xF5A524, 0xFF7A45, 0xE35BD0];
        return a[i < 0 ? 0 : i] as Number;
    }

    function bw(s as String, fs as Numeric) as Float {
        return em(s) * FieldDraw.fe(fs, false);
    }

    function em(s as String) as Float {
        var t = 0.0;
        var cs = s.toCharArray();
        for (var k = 0; k < cs.size(); k++) {
            var i = BIGK.find(cs[k].toString());
            t += i != null ? BIGV[i] as Float : 0.6;
        }
        return t;
    }

    function fmtG(d as FieldInput) as String {
        return Glucose.value(d.g as Number, d.unit);
    }

    // WCAG 2 contrast of two colours
    function lum(c as Number) as Float {
        var v = [(c >> 16) & 255, (c >> 8) & 255, c & 255];
        var o = [0.0, 0.0, 0.0];
        for (var i = 0; i < 3; i++) {
            var x = v[i] / 255.0;
            o[i] = x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * o[0] + 0.7152 * o[1] + 0.0722 * o[2];
    }

    function contrast(a as Number, b as Number) as Float {
        var x = lum(a);
        var y = lum(b);
        return (FieldMath.max(x, y) + 0.05) / (FieldMath.min(x, y) + 0.05);
    }

    // white or black, whichever reads better on [col]
    function inkBy(col as Number) as Number {
        return contrast(0xFFFFFF, col) >= contrast(0x000000, col) ? 0xFFFFFF : 0x000000;
    }

    // the ink on a block: white on AMOLED (Aura's very-low block), on MIP whichever reads better (app.js inkOn)
    function inkOn(col as Number) as Number {
        return FieldFontTable.MIP ? inkBy(col) : 0xFFFFFF;
    }

    // GLUCOSE, the field's label: the watch's xtiny (app.js dfLabel)
    function label(g as FieldG, x as Numeric, y as Numeric, fill as Number, anchor as Number) as Void {
        g.w(x, y, WatchUi.loadResource(Rez.Strings.Glucose) as String, FieldDraw.XT, fill, anchor);
    }

    function valColour(d as FieldInput, inverted as Boolean) as Number {
        if (d.age >= 5) {
            return pal("grey");
        }
        if (inverted) {
            return 0xFFFFFF;
        }
        return pal(d.band(d.g as Number));
    }

    // the value and its arrow as one group (app.js value()); [anchor] -1 start, 0 middle; [col] null = its colour
    function value(g as FieldG, d as FieldInput, x as Numeric, y as Numeric, fs as Numeric, anchor as Number, arrow as Boolean,
            inverted as Boolean, col as Number?) as Void {
        var str = fmtG(d);
        var vw = bw(str, fs);
        var cap = fs * 0.668;
        var box = cap * 0.78;
        var gap = fs * 0.1;
        var showArrow = arrow && d.deg != null && !str.equals("LO") && !str.equals("HI");
        var w = vw + (showArrow ? gap + box : 0);
        var x0 = anchor < 0 ? x : x - w / 2.0;
        var c = col != null ? col : valColour(d, inverted);
        g.t(x0, y, str, fs, c, -1, 0);
        if (d.age >= 5) {
            g.l(FieldMath.r1(x0 - 4), FieldMath.r1(y - cap / 2), FieldMath.r1(x0 + vw + 4), FieldMath.r1(y - cap / 2), c, 1.0,
                FieldMath.max(3, fs * 0.05), 0, 0, true);
        }
        if (showArrow) {
            var ax = x0 + vw + gap + box / 2;
            var ay = y - cap / 2;
            if (d.age >= 5) {
                g.c(FieldMath.r1(ax), FieldMath.r1(ay), FieldMath.r1(box * 0.28), c, 1.0, box * 0.1);
            } else {
                g.arrow(FieldMath.r1(ax), FieldMath.r1(ay), box, FieldMath.r1(d.deg as Float), d.doubled, c);
            }
        }
    }

    // Sukrara Aura's sparkline (app.js auraSpark, Aura's SPEC §6.5), scaled by [sc], in the box x0 … x1, y0 … y1
    function spark(g as FieldG, d as FieldInput, x0 as Float, x1 as Float, y0 as Float, y1 as Float, sc as Float) as Void {
        new FieldSpark(d, x0, x1, y0, y1, sc).draw(g, d);
    }

    // a box [h] high inside the slot and the screen, at the slot's side toward the centre, as wide as the circle allows
    // there: [x0, x1, y0, y1, w], or null (app.js fitBox)
    function fitBox(b as FieldSlot, h as Numeric, pad as Numeric) as Array? {
        var y0;
        var y1;
        if (b.top && !b.bot) {
            y1 = b.y1 - pad;
            y0 = y1 - h;
        } else if (b.bot && !b.top) {
            y0 = b.y0 + pad;
            y1 = y0 + h;
        } else {
            var mid = (b.y0 + b.y1) / 2;
            y0 = mid - h / 2.0;
            y1 = mid + h / 2.0;
        }
        if (y0 < b.y0 + pad - 0.01 || y1 > b.y1 - pad + 0.01) {
            return null;
        }
        var x0 = b.x0 + pad;
        var x1 = b.x1 - pad;
        if (!FieldFontTable.RECT) {
            var R = 195 - pad;
            var ym = FieldMath.max(FieldMath.abs(y0), FieldMath.abs(y1));
            if (ym >= R) {
                return null;
            }
            var hc = Math.sqrt(R * R - ym * ym);
            x0 = FieldMath.max(x0, -hc);
            x1 = FieldMath.min(x1, hc);
        }
        return x1 - x0 > 0 ? [x0, x1, y0, y1, x1 - x0] : null;
    }

    // the largest value (em width [e]) that fits in the slot, with its label row when there is room (app.js fitValue):
    // [box x0, x1, y0, y1, fs, label]
    function fitValue(b as FieldSlot, e as Float, max as Numeric) as Array? {
        var best = null;
        for (var h = b.y1 - b.y0 - 8; h >= 24; h -= 2) {
            var bx = fitBox(b, h, 4);
            if (bx == null) {
                continue;
            }
            var lab = h >= 64;
            var fsH = (h - (lab ? FieldDraw.capOf(FieldDraw.XT) + 8 : 0) - 6) / 0.668;
            var fsW = (bx[4] as Float) * 0.9 / e;
            var fs = FieldMath.min(FieldMath.min(fsH, fsW), max);
            if (best == null || fs > (best[1] as Float) + 0.5) {
                best = [bx, fs, lab];
            }
        }
        if (best == null) {
            return null;
        }
        var fs = FieldDraw.fe(best[1] as Float, true);
        var lab = best[2] as Boolean;
        var bb = best[0] as Array;
        var ch = (lab ? FieldDraw.capOf(FieldDraw.XT) + 8 : 0) + FieldDraw.fe(fs, false) * 0.668 + 6;
        var y0 = b.top && !b.bot ? (bb[3] as Float) - ch : b.bot && !b.top ? bb[2] as Float : ((bb[2] as Float) + (bb[3] as Float)) / 2 - ch / 2;
        return [bb[0], bb[1], y0, y0 + ch, fs, lab];
    }

    // --- the field in a slot (app.js dfField) ---
    function field(d as FieldInput, b as FieldSlot) as FieldG {
        var g = new FieldG();
        if (d.noPhone) {
            setup(g, b);
            return g;
        }
        if (d.g == null || d.age >= 20) {
            none(g, d, b);
            return g;
        }
        var band = d.band(d.g as Number);
        var inv = d.age < 5 && (band.equals("veryLow") || band.equals("low"));
        if (inv) {
            g.r(b.x0, b.y0, b.x1 - b.x0, b.y1 - b.y0, pal(band), band.equals("veryLow") ? 1.0 : 0.85);
        }
        if (b.whole) {
            whole(g, d, inv ? inkOn(pal(band)) : null);
        } else if (inv || !side(g, d, b)) {
            valueOnly(g, d, b, inv ? inkOn(pal(band)) : null);
        }
        return g;
    }

    // the whole page: the label, the value (116) over the sparkline (sc 1.4), the reading's time when late; [ink] the
    // low block's ink (null: no block)
    function whole(g as FieldG, d as FieldInput, ink as Number?) as Void {
        label(g, 0, FieldFontTable.RECT ? -FieldDraw.hh() + 40 : -128, ink != null ? ink : pal("soft"), 0);
        value(g, d, 0, -8, 116, 0, true, ink != null, ink);
        if (ink == null) {
            var Wd = 100 / Math.ln(7) * 1.4 * Math.ln(19) + 24 * 1.4;
            spark(g, d, -Wd / 2, Wd / 2, 26.0, 110.0, 1.4);
        }
        if (d.age >= 1.5) {
            g.w(0, 150, d.readAt, FieldDraw.XT, ink != null ? ink : pal("soft"), 0);
        }
    }

    // value + sparkline side by side, when a 260 × 100 box fits in the slot inside the screen; false: it doesn't
    function side(g as FieldG, d as FieldInput, b as FieldSlot) as Boolean {
        for (var h = FieldMath.min(170, b.y1 - b.y0 - 12); h >= 100; h -= 2) {
            var sb = fitBox(b, h, 6);
            if (sb == null || (sb[4] as Float) < 260) {
                continue;
            }
            var fs = FieldDraw.fe(FieldMath.min(92, (h - 30) * 0.8), true);
            var vw = bw(fmtG(d), fs);
            var gw = FieldMath.min(170, (sb[4] as Float) - vw - 14 - 16);
            if (gw < 90) {
                return false;
            }
            gw += vw + 14;
            var x0 = ((sb[0] as Float) + (sb[1] as Float)) / 2 - gw / 2;
            label(g, x0, (sb[2] as Float) + 20, pal("soft"), -1);
            if (d.age >= 1.5) {
                g.w(x0 + gw, (sb[2] as Float) + 20, d.readAt, FieldDraw.XT, pal("soft"), 1);
            }
            h = (sb[3] as Float) - 16; // the value's baseline
            value(g, d, x0, h, fs, -1, false, false, null);
            spark(g, d, x0 + vw + 14, x0 + gw, h - fs * 0.668 - 8, h, FieldMath.min(1.0, fs / 88.0));
            return true;
        }
        return false;
    }

    // the value and its arrow, as big as the slot allows; [ink] the low block's ink (null: no block)
    function valueOnly(g as FieldG, d as FieldInput, b as FieldSlot, ink as Number?) as Void {
        var fv = fitValue(b, em(fmtG(d)) + (d.deg != null ? 0.1 + 0.668 * 0.78 : 0), 116);
        if (fv == null) {
            return;
        }
        var cx = ((fv[0] as Float) + (fv[1] as Float)) / 2;
        if (fv[5]) {
            label(g, cx, (fv[2] as Float) + FieldDraw.capOf(FieldDraw.XT) + 3, ink != null ? ink : pal("soft"), 0);
        }
        value(g, d, cx, (fv[3] as Float) - 4, fv[4] as Float, 0, true, ink != null, ink);
    }

    // not set up (SPEC §4.3: no message from the phone yet): GLUCOSE and "Waiting for phone" (soft), on two lines where
    // one doesn't fit, in tiny, else xtiny (never smaller); the label where the slot has room (app.js dfSetup)
    function setup(g as FieldG, b as FieldSlot) as Void {
        var w1 = WatchUi.loadResource(Rez.Strings.Setup1) as String;
        var w2 = WatchUi.loadResource(Rez.Strings.Setup2) as String;
        var one = w1 + " " + w2;
        if (b.whole) {
            label(g, 0, FieldFontTable.RECT ? -FieldDraw.hh() + 40 : -128, pal("soft"), 0);
            g.w(0, 8, one, FieldDraw.fitRole(one, FieldDraw.SM, 2 * FieldDraw.hw() - 60), pal("soft"), 0);
            return;
        }
        // the label and one line, the label and two (tiny, then xtiny), then without the label (xtiny)
        for (var k = 0; k < 6; k++) {
            var lab = k < 4;
            var two = k % 2 == 1;
            var f = k < 2 ? FieldDraw.TI : FieldDraw.XT;
            var lf = FieldDraw.lh(f);
            var lr = lab ? FieldDraw.lh(FieldDraw.XT) : 0;
            var bx = fitBox(b, lr + (two ? 2 : 1) * lf, 4);
            if (bx == null || (two ? FieldMath.max(FieldDraw.ww(w1, f), FieldDraw.ww(w2, f)) : FieldDraw.ww(one, f)) > (bx[4] as Float) * 0.92) {
                continue;
            }
            var cx = ((bx[0] as Float) + (bx[1] as Float)) / 2;
            var y = (bx[2] as Float) + lr + (lf + FieldDraw.capOf(f)) / 2;
            if (lab) {
                label(g, cx, (bx[2] as Float) + FieldDraw.capOf(FieldDraw.XT) + 3, pal("soft"), 0);
            }
            g.w(cx, y, two ? w1 : one, f, pal("soft"), 0);
            if (two) {
                g.w(cx, y + lf, w2, f, pal("soft"), 0);
            }
            return;
        }
    }

    // no value to show: "---" in grey where the value goes; the whole page says since when (or why)
    function none(g as FieldG, d as FieldInput, b as FieldSlot) as Void {
        if (b.whole) {
            var why = d.g == null ? WatchUi.loadResource(Rez.Strings.NoReadings) as String
                : Lang.format(WatchUi.loadResource(Rez.Strings.NoReadingSince) as String, [d.readAt]);
            label(g, 0, FieldFontTable.RECT ? -FieldDraw.hh() + 40 : -128, pal("soft"), 0);
            g.t(0, -8, "---", 116, pal("grey"), 0, 0);
            g.w(0, 60, why, FieldDraw.fitRole(why, FieldDraw.TI, 2 * FieldDraw.hw() - 60), pal("soft"), 0);
            return;
        }
        var fv = fitValue(b, em("---"), 116);
        if (fv == null) {
            return;
        }
        var cx = ((fv[0] as Float) + (fv[1] as Float)) / 2;
        if (fv[5]) {
            label(g, cx, (fv[2] as Float) + FieldDraw.capOf(FieldDraw.XT) + 3, pal("soft"), 0);
        }
        g.t(cx, (fv[3] as Float) - 4, "---", fv[4] as Float, pal("grey"), 0, 0);
    }

    // --- the field's own alert, the whole page (app.js S.dfAlert) ---
    // [kind] the phone's (very_low … no_data), [word] its word, [line] "Sukrara · now"
    function alert(d as FieldInput, kind as String, word as String, line as String) as FieldG {
        var g = new FieldG();
        var bands = {"very_low" => "veryLow", "low" => "low", "high" => "high", "very_high" => "veryHigh",
            "extremely_high" => "extremelyHigh"};
        var b = kind.equals("no_data") ? "grey" : bands[kind] != null ? bands[kind] as String
            : d.g != null ? d.band(d.g as Number) : "grey";
        var col = pal(b);
        var hw = FieldDraw.hw();
        var hh = FieldDraw.hh();
        g.r(-hw - 2, -hh - 2, 2 * hw + 4, 2 * hh + 4, col, 1.0);
        var W0 = b.equals("veryLow") || b.equals("low") ? inkOn(col) : inkBy(col);
        // the word in medium, else smaller (never under xtiny), the bell before it
        var wf = FieldDraw.fitRole(word, FieldDraw.ME, 2 * hw - 90);
        var wd = FieldDraw.ww(word, wf) + 32;
        g.icon(-wd / 2 + 12, -88 - FieldDraw.capOf(wf) / 2, 24, W0);
        g.w(-wd / 2 + 32, -88, word, wf, W0, -1);
        if (d.g != null) {
            value(g, d, 0, 30, 116, 0, true, true, W0);
        }
        g.w(0, 92, line, FieldDraw.fitRole(line, FieldDraw.TI, 2 * hw - 80), W0, 0);
        return g;
    }
}

// Aura's sparkline's scale (app.js auraSpark): a log time axis, 0.6·sc px per mg/dL around the window's middle, what
// runs past the box squeezed into the fade band and fainter
class FieldSpark {
    var sc as Float;
    var K as Float;
    var xNow as Float;
    var y0 as Float;
    var SH as Float;
    var mid as Float = 0.0;
    var ppm as Float;
    var x0 as Float;
    var spanMin as Float;

    function initialize(d as FieldInput, bx0 as Float, bx1 as Float, by0 as Float, by1 as Float, s as Float) {
        sc = s;
        K = 100 / Math.ln(7) * sc;
        y0 = by0;
        SH = by1 - by0;
        ppm = 0.6 * sc;
        spanMin = FieldMath.min(180.0, 10 * (Math.pow(Math.E, (bx1 - bx0 - 24 * sc) / K) - 1)).toFloat();
        xNow = bx1 - 24 * sc;
        x0 = X(spanMin);
        // the window: frame the person's range when the last hour fits with it, else the hour's middle, the newest
        // inside
        var lo = 999;
        var hi = 0;
        for (var i = 0; i < d.hm.size(); i++) {
            if ((d.hm[i] as Number) <= 60) {
                lo = FieldMath.min(lo, d.hv[i]);
                hi = FieldMath.max(hi, d.hv[i]);
            }
        }
        var span = SH / ppm;
        var L = d.bands[1] as Number;
        var H = d.bands[3] as Number;
        if (FieldMath.max(hi, H) - FieldMath.min(lo, L) <= span) {
            mid = (FieldMath.max(hi, H) + FieldMath.min(lo, L)) / 2.0;
        } else {
            var last = d.hv[d.hv.size() - 1] as Numeric;
            mid = FieldMath.clamp((lo + hi) / 2.0, last - 0.4 * span, last + 0.4 * span).toFloat();
        }
    }

    function X(age as Numeric) as Float {
        return xNow - K * Math.ln(1 + age / 10.0);
    }

    function raw(v as Numeric) as Float {
        return y0 + SH / 2 - (v - mid) * ppm;
    }

    function Y(v as Numeric) as Float {
        var y = raw(v);
        var B = 12 * sc;
        if (y < y0) {
            return y0 - B * (1 - Math.pow(Math.E, (y - y0) / B));
        }
        if (y > y0 + SH) {
            return y0 + SH + B * (1 - Math.pow(Math.E, (y0 + SH - y) / B));
        }
        return y;
    }

    function OP(v as Numeric) as Float {
        var y = raw(v);
        var out = y < y0 ? y0 - y : y > y0 + SH ? y - y0 - SH : 0.0;
        if (out <= 0) {
            return 1.0;
        }
        if (out <= 6 * sc) {
            return 1 - 0.55 * out / (6 * sc);
        }
        return FieldMath.max(0.15, 0.45 - 0.3 * (out - 6 * sc) / (6 * sc));
    }

    // the band between the limits, the limits dotted, the line (a piece per pair, each faded by its height), the trend
    // ray, the end dot
    function draw(g as FieldG, d as FieldInput) as Void {
        var L = d.bands[1] as Number;
        var H = d.bands[3] as Number;
        var yH = Y(H);
        var yL = Y(L);
        g.r(x0, FieldMath.min(yH, yL), xNow - x0, FieldMath.abs(yL - yH), FieldLayout.pal("tight"), 0.09);
        limit(g, yH, OP(H));
        limit(g, yL, OP(L));
        var col = d.age >= 5 ? FieldLayout.pal("dim") : FieldLayout.pal("soft");
        var prev = -1;
        for (var k = 0; k < d.hm.size(); k++) {
            if ((d.hm[k] as Number) > spanMin) {
                continue;
            }
            if (prev >= 0) {
                g.l(FieldMath.r1(X((d.hm[prev] as Number) + d.age)), FieldMath.r1(Y(d.hv[prev])),
                    FieldMath.r1(X((d.hm[k] as Number) + d.age)), FieldMath.r1(Y(d.hv[k])), col,
                    FieldMath.min(OP(d.hv[prev]), OP(d.hv[k])), 5.4 * sc, 0, 0, true);
            }
            prev = k;
        }
        end(g, d);
    }

    function limit(g as FieldG, y as Float, op as Float) as Void {
        g.l(FieldMath.r1(x0), FieldMath.r1(y), FieldMath.r1(xNow), FieldMath.r1(y), FieldLayout.GREY_LIMIT, op, 1.4 * sc,
            FieldMath.r1(1.5 * sc), FieldMath.r1(4.5 * sc), true);
    }

    function end(g as FieldG, d as FieldInput) as Void {
        var ex = FieldMath.r1(X(d.age));
        var ey = FieldMath.r1(Y(d.hv[d.hv.size() - 1]));
        var b = d.band(d.g as Number);
        if (d.age >= 5) {
            g.c(ex, ey, FieldMath.r1(7.5 * sc), 0x000000, 1.0, 0);
            g.c(ex, ey, FieldMath.r1(7.5 * sc), FieldLayout.pal("grey"), 1.0, 3 * sc);
            return;
        }
        if (d.deg != null) {
            var a = (d.deg as Float) * Math.PI / 180;
            g.l(ex, ey, FieldMath.r1(ex + 22 * sc * Math.cos(a)), FieldMath.r1(ey + 22 * sc * Math.sin(a)),
                b.equals("veryLow") ? FieldLayout.pal("veryLow") : FieldLayout.valColour(d, false), 1.0, 3.4 * sc,
                FieldMath.r1(4.5 * sc), FieldMath.r1(4.5 * sc), false);
        }
        g.c(ex, ey, FieldMath.r1(7.5 * sc), FieldLayout.pal(b), 1.0, 0);
    }
}
