// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;

// THE AURA (SPEC.md §6.12): the dial as the reference's numbers (aura.js "the dial", expect.json "dial") and its
// painting. A pure function of the model's dial inputs (ext, toward, I, spd, levelY, colour); AuraDraw paints it once
// into a cached bitmap (AuraDraw.dialCache) and redraws it only when these numbers change (a new reading, the mode, a
// setting), so a 1 Hz awake second costs one bitmap copy.
//
// Alpha: the cache holds only the dial over black, and no two marks of one dial overlap (dots, rings and waves never
// touch), so each mark is drawn opaque in its colour pre-multiplied by its opacity over black: the same pixels as the
// reference's alpha, without blending. The reference's gradients become one opacity per mark (a ring: the fade at its
// radius; a wave: at its depth; a laser: per 10 px piece; the glow: 3 px rings) — SPEC.md §13.
class AuraDial {
    var kind as String = "black"; // grain, contours, waterline, lasers, glow, map, black
    var drawn as Boolean = false; // something to paint (awake, and a dial that draws)
    var current as Boolean = false;
    var levelY as Float = 60.0;
    var ext as Float = 0.0;
    var I as Float = 1.0;
    var spd as Float = 0.0;

    // the reference's numbers: paths as subpath counts (lasers: the path itself), strokes, opacities, the gradient
    var n1 as Number = 0;
    var n2 as Number = 0;
    var f1 as Number? = null; // fill colour (grain), null = none
    var s1 as String = "none"; // stroke: "none", "grad" (the gradient below) or "col" (col / texture)
    var s2 as String = "none";
    var s3 as String = "none";
    var w1 as Float = 1.0;
    var w2 as Float = 1.0;
    var w3 as Float = 1.0;
    var o1 as Float = 1.0;
    var o2 as Float = 1.0;
    var o3 as Float = 1.0;
    var d1D as Boolean = false;
    var d2D as Boolean = false;
    var d3D as Boolean = false;
    var glowD as Boolean = false;
    var wy1 as Float = 0.0;
    var wy2 as Float = 1.0;
    var dashed as Boolean = false; // "2 6"
    var gx as Float = 0.0;
    var gy as Float = 0.0;
    var gr as Float = 100.0;
    var col as Number = 0x000000;
    var go as Float = 0.0;
    var go2 as Float = 0.0;
    var go3 as Float = 0.0;
    var go4 as Float = 0.0;
    var stroke1 as Number = 0; // the colour of a plain stroke (map texture, the waterline)

    // the lasers: [y at x = -200, y at x = 200, bright] each
    var lasers as Array = [] as Array;

    const TEXTURE = 0x19191C;

    function initialize(m as AuraModel) {
        kind = m.inp.dial;
        current = m.current;
        levelY = m.levelY;
        ext = m.ext;
        I = m.I;
        spd = m.spd;
        dashed = !current;
        if (!m.awake) {
            return;
        }
        var dcol = m.dialCol;
        if (kind.equals("map")) {
            n1 = 12;
            s1 = "col";
            stroke1 = TEXTURE;
            w1 = 1.2;
            d1D = true;
            dashed = false;
        } else if (kind.equals("contours")) {
            var spc = 22 - 7 * ext;
            var Rf = current ? 85 + 270 * ext : 240.0;
            n1 = 0;
            for (var k = 1; k * spc < Rf; k++) {
                n1++;
            }
            var peak = current ? AuraMath.clamp((0.36 + 0.4 * ext) * I, 0.1, 0.95) : 0.22;
            s1 = "grad";
            w1 = Math.round(1.3 * (0.8 + 0.3 * I) * 100) / 100.0;
            d1D = true;
            gx = 30.0;
            gy = levelY;
            gr = AuraMath.r1(Rf);
            col = dcol;
            go = peak;
            go2 = peak * 0.72;
            go3 = peak * 0.36;
            go4 = peak * 0.11;
        } else if (kind.equals("waterline")) {
            var yL = m.hasValue ? levelY : 150.0;
            var depth = current ? 45 + 150 * ext : 0.0;
            var step = AuraMath.clamp(9 - 3.4 * (I - 0.45), 5, 9);
            n1 = 0;
            if (current) {
                for (var k = 1; k * step < depth && yL + k * step < 196; k++) {
                    n1++;
                }
            }
            var peak = current ? AuraMath.clamp((0.36 + 0.32 * ext) * I, 0.12, 0.9) : 0.0;
            s1 = "grad";
            w1 = 1.3;
            d1D = n1 > 0;
            wy1 = AuraMath.r1(yL);
            wy2 = AuraMath.r1(yL + AuraMath.max(20, depth));
            col = dcol;
            go = peak;
            n2 = 1;
            s2 = "col";
            stroke1 = dcol;
            w2 = 2.0;
            o2 = current ? 0.75 : 0.32;
            d2D = true;
        } else if (kind.equals("lasers")) {
            var n = current ? Math.round(5 + 9 * ext + 14 * ext * ext).toNumber() : 4;
            var sig = current ? 12 + 60 * ext : 90.0;
            lasers = [];
            // the seeded LCG (seed 1337, 64-bit integers): tuple i is draws 5i … 5i + 4 (u1, u2, x, unused, tilt)
            var s = 1337l;
            for (var i = 0; i < n; i++) {
                var q = new [5];
                for (var j = 0; j < 5; j++) {
                    s = (s * 1664525l + 1013904223l) % 4294967296l;
                    q[j] = s.toDouble() / 4294967296.0d;
                }
                var u1 = (q[0] as Double) < 0.000001d ? 0.000001d : q[0] as Double;
                var gz = Math.sqrt(-2 * Math.ln(u1)) * Math.cos(2 * Math.PI * (q[1] as Double));
                var y = levelY + gz * sig;
                var xc = -100 + 200 * (q[2] as Double);
                var tn = Math.tan(((q[4] as Double) * 2 - 1) * (6 + 8 * spd) * Math.PI / 180);
                var ya = AuraMath.r1(y + (-200 - xc) * tn);
                var yb = AuraMath.r1(y + (200 - xc) * tn);
                var bright = current && AuraMath.abs(gz) < 0.9;
                lasers.add([ya, yb, bright]);
                if (bright) {
                    n1++;
                } else {
                    n2++;
                }
            }
            var pk = current ? AuraMath.clamp((0.55 + 0.3 * ext) * I, 0.2, 1) : 0.3;
            gx = 30.0;
            gy = levelY;
            gr = AuraMath.r1(200 + 140 * ext);
            col = dcol;
            go = 1.0;
            go2 = 0.9;
            go3 = 0.62;
            go4 = 0.3;
            s1 = "grad";
            s2 = "grad";
            s3 = "grad";
            w1 = 1.7;
            w2 = 1.1;
            w3 = 6.0;
            o1 = Math.round(pk * 100) / 100.0;
            o2 = Math.round(pk * 0.38 * 100) / 100.0;
            o3 = Math.round(pk * 0.16 * 100) / 100.0;
            d1D = n1 > 0;
            d2D = n2 > 0;
            d3D = n1 > 0;
        } else if (kind.equals("glow") && current) {
            var bp = AuraMath.P(182, m.beadA);
            var g = AuraMath.clamp((0.06 + 0.34 * ext) * I, 0.03, 0.55);
            glowD = true;
            gx = bp[0];
            gy = bp[1];
            gr = AuraMath.r1(90 + 320 * ext + 40 * spd);
            col = dcol;
            go = Math.round(g * 1000) / 1000.0;
            go2 = Math.round(g * 0.4 * 1000) / 1000.0;
        } else if (kind.equals("grain")) {
            var sig = 16 + 20 * ext + 175 * ext * ext;
            var rmax = (0.75 + 1.5 * ext) * Math.pow(I, 0.9);
            n1 = 0;
            for (var y = -192; y <= 192; y += 12) {
                var row = grainRow(y, rmax, sig);
                for (var x = -192; x <= 192; x += 12) {
                    var xx = x + ((y / 12) % 2 != 0 ? 6 : 0);
                    if (xx * xx + y * y <= 190 * 190 && row >= 0.3) {
                        n1++;
                    }
                }
            }
            f1 = dcol;
            o1 = current ? Math.round(AuraMath.clamp((0.42 + 0.3 * ext) * AuraMath.min(1.25, I), 0.2, 0.9) * 100) / 100.0 : 0.3;
            d1D = true;
            dashed = false;
        }
        drawn = d1D || d2D || glowD;
    }

    // a grain row's dot radius (every dot of a row is the same: the bell is in y only)
    function grainRow(y as Number, rmax as Float, sig as Float) as Float {
        if (!current) {
            return 0.6;
        }
        var t = (y - levelY) / sig;
        return (rmax * Math.pow(Math.E, -t * t)).toFloat();
    }

    // ---------------------------------------------------------------------------------------------------- painting

    // the colour at an opacity, pre-multiplied over black (opaque): the cache holds the dial alone, over black
    function pm(c as Number, a as Numeric) as Number {
        return AuraDials.pm(c, a);
    }

    // the radial fade of contours and lasers: stops at 0, 0.22, 0.48, 0.76, 1 of gr
    function fade(d as Float) as Float {
        var t = d / gr;
        var st = [0.0, 0.22, 0.48, 0.76, 1.0];
        var ov = [go, go2, go3, go4, 0.0];
        if (t <= 0) {
            return go;
        }
        for (var i = 1; i < 5; i++) {
            if (t <= st[i]) {
                var u = (t - st[i - 1]) / (st[i] - st[i - 1]);
                return (ov[i - 1] + (ov[i] - ov[i - 1]) * u) as Float;
            }
        }
        return 0.0;
    }

    // Painting is in units (a grain row, a ring, a wave, a laser, the glow), a few per frame: a whole dial in one call
    // would trip the watchdog (contours: up to ~24 rings of 120 pieces). `next` is the next unit to paint.
    var next as Number = 0;

    function units() as Number {
        if (kind.equals("grain")) {
            return 33;
        } else if (kind.equals("contours") || kind.equals("map")) {
            return n1;
        } else if (kind.equals("waterline")) {
            return n1 + 1;
        } else if (kind.equals("lasers")) {
            return 3 * lasers.size();
        }
        return 1;
    }

    // paints units with the screen's centre at (ox, oy) until the budget (in line pieces) is spent; true when the dial
    // is complete (the halo is AuraHalo's, drawn over it)
    function paintSome(dc as Dc, ox as Float, oy as Float, budget as Number) as Boolean {
        dc.setAntiAlias(true);
        var n = drawn ? units() : 0;
        while (next < n && budget > 0) {
            if (kind.equals("grain")) {
                grainRow2(dc, ox, oy, -192 + 12 * next);
                budget -= 33;
            } else if (kind.equals("contours")) {
                var spc = 22 - 7 * ext;
                ring(dc, ox, oy, gx, gy, next + 1, spc, spc * 0.14 * spd, 0.5, false);
                budget -= dashed ? 300 : 121;
            } else if (kind.equals("map")) {
                ring(dc, ox, oy, -38.0, 52.0, next + 1, 24.0, 5.0, 0.9, true);
                budget -= 121;
            } else if (kind.equals("waterline")) {
                wave(dc, ox, oy, n1 - next);
                budget -= dashed ? 160 : 66;
            } else if (kind.equals("lasers")) {
                laser(dc, ox, oy, next / lasers.size(), next % lasers.size());
                budget -= dashed ? 100 : 40;
            } else {
                glow(dc, ox, oy);
                budget -= 130;
            }
            next++;
        }
        return next >= n;
    }

    // a grain row: its dots, all of one size (the bell is in y only)
    hidden function grainRow2(dc as Dc, ox as Float, oy as Float, y as Number) as Void {
        var rr = grainRow(y, (0.75 + 1.5 * ext) * Math.pow(I, 0.9), 16 + 20 * ext + 175 * ext * ext);
        if (rr < 0.3) {
            return;
        }
        var rq = AuraMath.r1(rr < 5.4 ? rr : 5.4);
        // a dot under 1 px is one pixel at the dot's area (an anti-aliased circle that small crashes the simulator's
        // firmware: the blue triangle); Connect IQ's filled circle comes out ~0.75 px wider than its radius, so the
        // others are drawn that much smaller (measured against the reference's dots), 1 px at least
        var tiny = rq < 1;
        if (!tiny) {
            rq = AuraMath.max(1, rq - 0.75);
        }
        dc.setFill(pm(f1 as Number, tiny ? o1 * Math.PI * rq * rq : o1));
        for (var x = -192; x <= 192; x += 12) {
            var xx = x + ((y / 12) % 2 != 0 ? 6 : 0);
            if (xx * xx + y * y <= 190 * 190) {
                if (tiny) {
                    dc.fillRectangle(Math.floor(ox + xx), Math.floor(oy + y), 1, 1);
                } else {
                    dc.fillCircle(ox + xx, oy + y, rq);
                }
            }
        }
    }

    // the pen of the line being drawn: its last point and the dash pattern's place along it (not current: 2 on, 6
    // off, round caps). Lines are drawn point by point, without arrays (the face's heap is small)
    var phase as Float = 0.0;
    var px as Float = 0.0;
    var py as Float = 0.0;

    function moveTo(x as Float, y as Float) as Void {
        px = x;
        py = y;
        phase = 0.0;
    }

    // a line from the pen to (x, y) (screen-centred), only where it is on the screen
    function lineTo(dc as Dc, ox as Float, oy as Float, x as Float, y as Float, w as Float) as Void {
        if (px * px + py * py < 40000 || x * x + y * y < 40000) {
            seg(dc, ox + px, oy + py, ox + x, oy + y, w);
        } else {
            phase += 8.0;
        }
        px = x;
        py = y;
    }

    // a piece of a line: whole, or its share of the dashes
    hidden function seg(dc as Dc, x1 as Float, y1 as Float, x2 as Float, y2 as Float, w as Float) as Void {
        if (!dashed) {
            dc.drawLine(x1, y1, x2, y2);
            return;
        }
        var dx = x2 - x1;
        var dy = y2 - y1;
        var len = Math.sqrt(dx * dx + dy * dy).toFloat();
        var t = 0.0;
        while (t < len) {
            var ph = (phase + t) - Math.floor((phase + t) / 8.0) * 8;
            if (ph < 2) {
                var e = AuraMath.min(len, t + 2 - ph);
                var ax = x1 + dx * t / len;
                var ay = y1 + dy * t / len;
                var bx = x1 + dx * e / len;
                var by = y1 + dy * e / len;
                dc.drawLine(ax, ay, bx, by);
                if (w >= 2) {
                    // round caps (a circle under 1 px would crash the simulator's firmware; thinner lines go without)
                    dc.fillCircle(ax, ay, w / 2);
                    dc.fillCircle(bx, by, w / 2);
                }
                t = e;
            } else {
                t += 8 - ph;
            }
        }
        phase += len;
    }

    // ring k around (cx0, cy0): its radius at angle t is k·spc·F(t) + A·sin(4t + phi·k), 120 pieces; a contour fades
    // by its radius, the map's are one colour
    hidden function ring(dc as Dc, ox as Float, oy as Float, cx0 as Float, cy0 as Float, k as Number, spc as Float, A as Float, phi as Float, isMap as Boolean) as Void {
        var base = k * spc;
        var a = isMap ? 1.0 : fade(base);
        if (a <= 0.004) {
            return;
        }
        var c = isMap ? 0xFF000000 | stroke1 : pm(col, a);
        dc.setStroke(c);
        dc.setFill(c);
        dc.setPenWidth(w1);
        for (var i = 0; i <= 120; i++) {
            var t = (i % 120) / 120.0 * Math.PI * 2;
            var f = isMap ? 1 + 0.12 * Math.sin(3 * t + 1.3) + 0.07 * Math.sin(5 * t + 0.4) + 0.05 * Math.sin(2 * t + 2.2)
                : 1 + 0.05 * Math.sin(2 * t + 0.9) + 0.035 * Math.sin(3 * t + 2.1);
            var rr = base * f + A * Math.sin(4 * t + phi * k);
            var x = (cx0 + rr * Math.cos(t)).toFloat();
            var y = (cy0 + rr * Math.sin(t)).toFloat();
            if (i == 0) {
                moveTo(x, y);
            } else {
                lineTo(dc, ox, oy, x, y, w1);
            }
        }
    }

    // wave k under the waterline at the fade of its depth; k 0 is the waterline itself
    hidden function wave(dc as Dc, ox as Float, oy as Float, k as Number) as Void {
        var amp = 2 + 7 * spd;
        var y0 = wy1 + k * AuraMath.clamp(9 - 3.4 * (I - 0.45), 5, 9);
        var a = k == 0 ? o2 : go * (1 - (y0 - wy1) / (wy2 - wy1));
        if (a <= 0.004) {
            return;
        }
        var a2 = k == 0 ? amp : amp * AuraMath.max(0.35, 1 - k * 0.04);
        var w = k == 0 ? 2.0 : 1.3;
        var c = pm(k == 0 ? stroke1 : col, a);
        dc.setStroke(c);
        dc.setFill(c);
        dc.setPenWidth(w);
        // x from -196 to 194 every 6 (the reference's points)
        for (var x = -196; x <= 196; x += 6) {
            var y = AuraMath.r1(y0 + a2 * Math.sin(x / 14.0 + 0.8));
            if (x == -196) {
                moveTo(x.toFloat(), y);
            } else {
                lineTo(dc, ox, oy, x.toFloat(), y, w);
            }
        }
    }

    // laser i in pass p (0 the bright ones' underlays, 1 the dim ones, 2 the bright ones on top), in 10 px pieces, each
    // at the fade of its middle (its distance from (30, levelY))
    hidden function laser(dc as Dc, ox as Float, oy as Float, p as Number, i as Number) as Void {
        var l = lasers[i] as Array;
        var bright = l[2] as Boolean;
        if ((p != 1) != bright) {
            return;
        }
        var w = p == 0 ? w3 : bright ? w1 : w2;
        var o = p == 0 ? o3 : bright ? o1 : o2;
        var slope = ((l[1] as Float) - (l[0] as Float)) / 400.0;
        dc.setPenWidth(w);
        phase = 0.0;
        for (var x = -200.0; x < 200; x += 10) {
            var y1 = (l[0] as Float) + (x + 200) * slope;
            var mx = x + 5;
            var my = y1 + 5 * slope;
            var dx = mx - gx;
            var dy = my - gy;
            var a = o * fade(Math.sqrt(dx * dx + dy * dy).toFloat());
            if (mx * mx + my * my < 42025 && a > 0.004) {
                var c = pm(col, a);
                dc.setStroke(c);
                dc.setFill(c);
                seg(dc, ox + x, oy + y1, ox + x + 10, oy + y1 + 10 * slope, w);
            } else {
                phase += 10;
            }
        }
    }

    // the glow: 3 px rings around the bead, from the outside in, each at the radial fade of its radius (0 at gr, 0.4
    // of go at half, go at the centre); only the rings that reach the screen
    hidden function glow(dc as Dc, ox as Float, oy as Float) as Void {
        var d = Math.sqrt(gx * gx + gy * gy);
        dc.setAntiAlias(false);
        for (var r = AuraMath.min(gr, d + 196); r > 0 && r + 2 > d - 197; r -= 3) {
            var t = r / gr;
            var a = t < 0.5 ? go + (go2 - go) * t / 0.5 : go2 * (1 - t) / 0.5;
            if (a > 0.004) {
                dc.setFill(pm(col, a));
                dc.fillCircle(ox + gx, oy + gy, r);
            }
        }
        dc.setAntiAlias(true);
    }
}

// The dial's cache and the seconds sweep (SPEC.md §6.11).
module AuraDials {
    var cache as Graphics.BufferedBitmapReference? = null;
    var cacheKey as String = "";
    var pending as AuraDial? = null; // a dial still being painted into the cache
    const BUDGET = 400; // line pieces a frame

    // the colour at an opacity, pre-multiplied over black (opaque): the cache holds the dial alone, over black
    function pm(c as Number, a as Numeric) as Number {
        var k = a < 0 ? 0.0 : a > 1 ? 1.0 : a.toFloat();
        var r = (((c >> 16) & 255) * k + 0.5).toNumber();
        var g = (((c >> 8) & 255) * k + 0.5).toNumber();
        var b = ((c & 255) * k + 0.5).toNumber();
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // what the dial's picture depends on: when it changes the cache is painted again (a new reading, the mode, a
    // setting); otherwise a frame copies the cache
    function key(m as AuraModel) as String {
        return m.inp.dial + "|" + m.awake + "|" + m.current + "|" + m.hasValue + "|" + m.dialCol + "|" + m.levelY + "|" +
            m.ext + "|" + m.I + "|" + m.spd + "|" + m.inp.glucose;
    }

    // the dial, awake only: from the cache (painted again when its key changes); true when something was drawn
    function draw(dc as Dc, m as AuraModel) as Boolean {
        if (!m.awake || m.inp.dial.equals("black")) {
            return false;
        }
        var k = key(m);
        if (!k.equals(cacheKey)) {
            var d = new AuraDial(m);
            cacheKey = k;
            pending = null;
            if (!d.drawn) {
                cache = null;
                return false;
            }
            if (cache == null) {
                cache = Graphics.createBufferedBitmap({:width => 390, :height => 390});
            }
            var bdc = (cache as Graphics.BufferedBitmapReference).get().getDc();
            bdc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
            bdc.clear();
            pending = d;
        }
        if (cache == null) {
            return false;
        }
        if (pending != null) {
            // a few units a frame (the watchdog); the next frame (1 Hz while awake) goes on
            var bdc = (cache as Graphics.BufferedBitmapReference).get().getDc();
            if ((pending as AuraDial).paintSome(bdc, 194.5, 194.5, BUDGET)) {
                pending = null;
            }
        }
        dc.drawBitmap(AuraDraw.cx - 194.5, AuraDraw.cy - 194.5, cache);
        return true;
    }

    // whether the dial's cache is still being painted
    function painting() as Boolean {
        return pending != null;
    }

    function release() as Void {
        cache = null;
        cacheKey = "";
        pending = null;
    }

    // the radial ramp of the sweep: 0 inside r 137 (0.7 of 196), 1 from r 193
    function ramp(r as Float) as Float {
        return AuraMath.clamp((r - 137.2) / (193.06 - 137.2), 0, 1);
    }

    // the seconds sweep: a faint tail behind the leading line, near the rim, in the bead's colour. The tail is the
    // reference's 25 nested 3° wedges (cumulative 0.17 at the edge, falling as ((k + 1) / 25)^2.2) as 6° pieces in
    // 10 px rings (the radial ramp), each one arc at its own opacity
    function sweep(dc as Dc, m as AuraModel) as Void {
        if (!m.sweepOn) {
            return;
        }
        var deg = m.sweepDeg;
        var col = m.sweepCol;
        dc.setAntiAlias(false);
        for (var j = 0; j < 25; j += 2) {
            // the piece from 3j to 3j + 6 behind the edge: the cumulative opacity of the wedges that cover it
            var t = 0.17 * Math.pow((24.5 - j) / 25.0, 2.2);
            if (t < 0.012) {
                break;
            }
            var a2 = deg - 3 * j;
            var a1 = a2 - (j + 2 > 25 ? 3 : 6);
            for (var r = 142; r < 196; r += 10) {
                var a = t * ramp(r.toFloat());
                if (a >= 0.006) {
                    AuraDraw.arc(dc, r, a1, a2, col, a, 11);
                }
            }
        }
        dc.setAntiAlias(true);
        // the leading line, r 140 to 196 at 0.5, through the ramp
        var t = deg * Math.PI / 180;
        var sx = Math.sin(t);
        var sy = -Math.cos(t);
        for (var r = 140; r < 196; r += 8) {
            var a = 0.5 * ramp(r + 4.0);
            if (a >= 0.01) {
                AuraDraw.stroke(dc, col, a, 1.6);
                dc.drawLine(AuraDraw.cx + r * sx, AuraDraw.cy + r * sy, AuraDraw.cx + (r + 8) * sx, AuraDraw.cy + (r + 8) * sy);
            }
        }
    }
}
