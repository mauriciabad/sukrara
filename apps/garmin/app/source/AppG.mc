// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.WatchUi;

// The model: app.js's display list (G). Every screen function (AppParts, AppScreens, GlanceScreen) pushes elements
// here, in the reference's units (origin at the screen's centre, y down, 1 unit = 1 px of the 390 px Descent); AppDraw
// paints them. Elements are small arrays, the first item their kind:
//   R [0, x, y, w, h, rx, col, op, sw]          a rectangle (sw 0 = filled)
//   C [1, cx, cy, r, col, op, sw]               a circle (sw 0 = filled)
//   L [2, [x, y, x, y, …], col, op, sw, dash]   a polyline, round caps and joins; dash [on, off] or null
//   P [3, [x, y, x, y, …], col, op]             a filled polygon
//   A [4, cx, cy, r, a0, a1, col, op, sw, round] an arc, degrees clockwise from 12
//   T [5, x, baseline, s, font, col, anchor, ls?] a text (anchor -1 start, 0 middle, 1 end; font AppDraw.font; ls
//                                               the letter spacing)
//   I [6, name, cx, cy, size, col]              an icon of the icon sheet, tinted
//   B [7, resource, cx, cy, size]               a bitmap resource (Sukrara's mark), [size] wide
(:glance)
class AppG {
    var els as Array = [];

    function initialize() {}

    function r(x as Numeric, y as Numeric, w as Numeric, h as Numeric, rx as Numeric, col as Number, op as Float, sw as Numeric) as Void {
        els.add([0, x, y, w < 0 ? 0 : w, h < 0 ? 0 : h, rx, col, op, sw]);
    }

    function c(cx as Numeric, cy as Numeric, rad as Numeric, col as Number, op as Float, sw as Numeric) as Void {
        els.add([1, cx, cy, rad, col, op, sw]);
    }

    function l(pts as Array, col as Number, op as Float, sw as Numeric, dash as Array?) as Void {
        if (pts.size() >= 4) {
            els.add([2, pts, col, op, sw, dash]);
        }
    }

    function line(x1 as Numeric, y1 as Numeric, x2 as Numeric, y2 as Numeric, col as Number, sw as Numeric) as Void {
        l([x1, y1, x2, y2], col, 1.0, sw, null);
    }

    function p(pts as Array, col as Number, op as Float) as Void {
        els.add([3, pts, col, op]);
    }

    function a(cx as Numeric, cy as Numeric, rad as Numeric, a0 as Numeric, a1 as Numeric, col as Number, op as Float, sw as Numeric, round as Boolean) as Void {
        els.add([4, cx, cy, rad, a0, a1, col, op, sw, round]);
    }

    // a text at [fs] px, weight [fw] (500 / 600 / 700); [big] = the big numbers' atlas
    function t(x as Numeric, y as Numeric, s as String, fs as Numeric, fw as Number, col as Number, anchor as Number) as Void {
        els.add([5, x, y, s, AppDraw.font(fs, fw, false), col, anchor]);
    }

    // a text with letter spacing [ls] (app.js's ls: px after every glyph)
    function ts(x as Numeric, y as Numeric, s as String, fs as Numeric, fw as Number, col as Number, anchor as Number, ls as Numeric) as Void {
        els.add([5, x, y, s, AppDraw.font(fs, fw, false), col, anchor, ls]);
    }

    function n(x as Numeric, y as Numeric, s as String, fs as Numeric, col as Number, anchor as Number) as Void {
        els.add([5, x, y, s, AppDraw.font(fs, 700, true), col, anchor]);
    }

    function bmp(id as ResourceId, cx as Numeric, cy as Numeric, size as Numeric) as Void {
        els.add([7, id, cx, cy, size]);
    }

    function icon(name as String, cx as Numeric, cy as Numeric, size as Numeric, col as Number) as Void {
        els.add([6, name, cx, cy, size, col]);
    }

    // Sukrara's arrow (aura.js): pointing right, turned by [deg] (negative = up); doubled
    function arrow(cx as Numeric, cy as Numeric, box as Numeric, deg as Numeric, doubled as Boolean, col as Number) as Void {
        var s = box * 1.05;
        var t = deg.toFloat() * Math.PI / 180.0;
        var co = Math.cos(t);
        var si = Math.sin(t);
        var shapes = [[-0.42, -0.07, 0.25, -0.07, 0.25, 0.07, -0.42, 0.07], [0.46, 0, 0.06, 0.3, 0.16, 0, 0.06, -0.3]];
        if (doubled) {
            shapes.add([0.12, 0, -0.28, 0.3, -0.18, 0, -0.28, -0.3]);
        }
        for (var i = 0; i < shapes.size(); i++) {
            var q = shapes[i];
            var out = [];
            for (var j = 0; j < q.size(); j += 2) {
                var x = q[j] * s;
                var y = q[j + 1] * s;
                out.add(cx + x * co - y * si);
                out.add(cy + x * si + y * co);
            }
            p(out, col, 1.0);
        }
    }
}

// Paints an AppG on a Dc: [ox, oy] the reference's origin in the Dc's pixels (a shape at device x covers x − 0.5 …
// x + 0.5: on the 390 px screen the origin is 194.5, the edge between pixels 194 and 195; bitmaps sit at + 0.5), [s]
// the layout units → px (the class's, AppFontTable.S; the glance's own). The class's rules (SPEC §14.1, app.js
// snapFont, snapStroke, G.col): every text at the nearest size of the class's atlas set (the 390 keeps its approved
// sizes), strokes to whole px, and on the 64-colour MIP screens every colour one of the 64, no alpha: a tint becomes a
// solid, a dither or (a selected row, a chip: card2) black with a 2 px outline.
(:glance)
module AppDraw {
    var ox as Float = 194.5;
    var oy as Float = 194.5;
    var s as Float = 1.0;
    (:snap)
    var snapOn as Boolean = false; // sizes snap to the atlas set (all but the 390 at 1:1)
    var gw as Float = 248.0; // the glance's width, units
    var fonts as Dictionary = {};
    var sheets as Dictionary = {}; // the icon sheets loaded, by index
    var glance as Boolean = false;
    // the glance paints its display list once: each element goes as it's painted, room for the atlases (#217)
    var once as Boolean = false;
    // the foreground's tables (AppFontTable): set by the app's view, so the glance's code never names them
    var table as Method? = null;
    var iconAt as Method? = null;
    var sheetAt as Method? = null;
    var bitmapAt as Method? = null; // AppDrawApp.bitmap

    // the scale: the class's (the app's pages) or the glance's. A watch whose app and glance both draw the 390's own
    // sizes (the 390s with the 390's glance: monkey.jungle's screen classes) builds no snapping (:nosnap)
    (:nosnap)
    function scale(sc as Float) as Void {
        s = sc;
    }

    (:nosnap)
    function fe(fs as Numeric) as Float {
        return fs.toFloat();
    }

    (:snap)
    function scale(sc as Float) as Void {
        s = sc;
        snapOn = !GlanceFontTable.R390 || AppMath.abs(sc - 1.0) > 0.001;
    }

    // a size (units) as the class draws it: the nearest of its atlas set (ties: the larger)
    (:snap)
    function fe(fs as Numeric) as Float {
        if (!snapOn) {
            return fs.toFloat();
        }
        var px = fs * s;
        var set = GlanceFontTable.ATLAS;
        var best = set[0] as Number;
        for (var i = 1; i < set.size(); i++) {
            var d = AppMath.abs(set[i] - px);
            var bd = AppMath.abs(best - px);
            if (d < bd || (d == bd && set[i] > best)) {
                best = set[i] as Number;
            }
        }
        return best / s;
    }

    // a font key: a text "a<px><m|s|b>", a big number "n<px>"
    function font(fs as Numeric, fw as Number, big as Boolean) as String {
        var px = Math.round(fe(fs) * s).toNumber();
        if (big) {
            return "n" + px;
        }
        return "a" + px + (fw >= 700 ? "b" : fw >= 600 ? "s" : "m");
    }

    // a font key the tables lack (debug builds say so: add the size to tools/fonts.py)
    (:debug)
    function missing(key as String) as Void {
        System.println("FONT|missing|" + key);
    }

    (:release)
    function missing(key as String) as Void {}

    function release() as Void {
        fonts = {};
        sheets = {};
    }

    // a font's atlas and glyph table, loaded once a page (AppDraw.release); the glance holds one at a time (its 64 KB:
    // three glyph tables held together ran it out of memory on a Descent Mk3 43, #217)
    function loadFont(key as String) as Array {
        var a = fonts[key];
        if (a != null) {
            return a as Array;
        }
        if (glance) {
            fonts = {};
        }
        var t = GlanceFontTable.atlas(key);
        if (t.size() == 0 && table != null) {
            t = (table as Method).invoke(key) as Array;
        }
        if (t.size() == 0) {
            return [];
        }
        if (t[1] instanceof Number) {
            a = [WatchUi.loadResource(t[0]), t[1]];
        } else {
            // [glyphs, two Numbers a glyph (tools/fonts.py), baseline]: used as it loads, nothing decoded
            var j = WatchUi.loadResource(t[1]) as Array;
            a = [WatchUi.loadResource(t[0]), j[0], j[1], j[2]];
        }
        fonts[key] = a;
        return a as Array;
    }

    // ---- colour (SPEC §14.3) -------------------------------------------------------------------------------------
    // the MIP code is built only for the MIP watches (:mip; the AMOLEDs build the :amoled stubs: monkey.jungle's
    // screen classes), so it costs the AMOLED glances nothing
    (:mip)
    const CARD = -2; // card2 on MIP: black, outlined AAAAAA
    (:mip)
    const GRID_MIP = -3; // grid on MIP: 555555 dithered 50 %

    // an AMOLED token as its MIP token (app.js PAL_MIP); anything else: the nearest of the 64. Compared, not a
    // dictionary kept in memory (the glance's 64 KB, #217)
    (:mip)
    function mipToken(c as Number) as Number {
        var i = [0xA3A3A8, 0x6E6E73, 0x3A3A3E, 0x26262A, 0x38383E, 0x4E4E55, 0x8E8E93, 0xC2413B, 0xF0524F, 0x3DD47A, 0x8CC63F,
            0xF5A524, 0xFF7A45, 0xE35BD0, 0xC6F432, 0x8AB4FF, 0xC9B0FF, 0xFFB74D, 0xB4B4B4, 0x1C1C1E, 0x141416, 0x1C1C1F].indexOf(c);
        return i < 0 ? c : [0xAAAAAA, 0x555555, 0x555555, GRID_MIP, 0x555555, 0x555555, 0xAAAAAA, 0xFF0000, 0xFF5555, 0x00FF55,
            0xAAFF00, 0xFFAA00, 0xFF5500, 0xFF55FF, 0xAAFF00, 0x55AAFF, 0xAAAAFF, 0xFFAA55, 0xAAAAAA, 0x000000, 0x000000, CARD][i]
            as Number;
    }

    // [colour, dither %] as the class paints [c] at opacity [op]; [role] 0 a stroke, 1 a fill, 2 a text (app.js G.col)
    (:amoled)
    function col(c as Number, op as Float, role as Number) as Array {
        return [c, 0];
    }

    (:mip)
    function col(c as Number, op as Float, role as Number) as Array {
        c = mipToken(c);
        if (c < 0 || c == 0) {
            return c == GRID_MIP ? [0x555555, 50] : [c, 0];
        }
        var r = (c >> 16) & 255;
        var g = (c >> 8) & 255;
        var b = c & 255;
        // a tint (a fill under 60 %): a dither of the tint's colour at 3× its opacity; a checkerboard from 10 %, else one
        // pixel in four
        if (role == 1 && op < 0.6) {
            var k = op * 3 > 1 ? 1.0 : op * 3;
            var t = near64(r * k, g * k, b * k);
            if (t == 0) {
                var mx = r > g ? (r > b ? r : b) : (g > b ? g : b);
                t = near64(r == mx ? 85 : 0, g == mx ? 85 : 0, b == mx ? 85 : 0);
            }
            return [t, op >= 0.1 ? 50 : 25];
        }
        if (op >= 0.5) {
            var f = near64(r, g, b);
            return [role == 2 && f == 0x555555 ? 0xAAAAAA : f, 0];
        }
        var dk = near64(r * 0.55, g * 0.55, b * 0.55);
        return [dk == 0 ? 0x555555 : dk, 0];
    }

    // the nearest of the 64 (each channel to 00 / 55 / AA / FF)
    (:mip)
    function near64(r as Numeric, g as Numeric, b as Numeric) as Number {
        var q = [r, g, b];
        var v = 0;
        for (var i = 0; i < 3; i++) {
            var x = q[i] > 255 ? 255 : q[i] < 0 ? 0 : q[i];
            v = (v << 8) | (Math.round(x / 85.0) * 85).toNumber();
        }
        return v;
    }

    // fill and stroke for what comes next: ARGB on the AMOLEDs (they blend)
    (:amoled)
    function ink(dc as Dc, c as Number, op as Float) as Void {
        var v = 0;
        if (op >= 1.0) {
            v = 0xFF000000 | c;
            v = v == -1 ? 0xFFFFFFFE : v;
        } else {
            v = Graphics.createColor((AppMath.clamp(op, 0, 1) * 255 + 0.5).toNumber(), (c >> 16) & 255, (c >> 8) & 255, c & 255);
        }
        dc.setFill(v);
        dc.setStroke(v);
    }

    // the MIP screens: opaque, the 64 colours (no alpha)
    (:mip)
    function ink(dc as Dc, c as Number, op as Float) as Void {
        if (dc has :setFill) {
            var v = 0xFF000000 | c;
            v = v == -1 ? 0xFFFFFFFE : v;
            dc.setFill(v);
            dc.setStroke(v);
        } else {
            dc.setColor(c, Graphics.COLOR_TRANSPARENT);
        }
    }

    function paint(dc as Dc, g as AppG) as Void {
        if (dc has :setAntiAlias) {
            dc.setAntiAlias(true);
        }
        var els = g.els;
        for (var i = 0; i < els.size(); i++) {
            var e = els[i] as Array;
            var k = e[0] as Number;
            if (k == 0) {
                rect(dc, e);
            } else if (k == 1) {
                circle(dc, e);
            } else if (k == 2) {
                poly(dc, e);
            } else if (k == 3) {
                var pts = e[1] as Array;
                var q = [];
                for (var j = 0; j < pts.size(); j += 2) {
                    q.add([X(pts[j]), Y(pts[j + 1])]);
                }
                var m = col(e[2] as Number, e[3] as Float, 1);
                ink(dc, m[0] as Number, e[3] as Float);
                dc.fillPolygon(q);
            } else if (k == 4) {
                arc(dc, e);
            } else if (k == 5) {
                text(dc, e);
            } else if (k == 6) {
                icon(dc, e);
            } else if (k == 7 && bitmapAt != null) {
                (bitmapAt as Method).invoke(dc, e);
            }
            if (once) {
                els[i] = null;
            }
        }
    }

    function X(x) as Float {
        return ox + (x as Numeric) * s;
    }

    function Y(y) as Float {
        return oy + (y as Numeric) * s;
    }

    // a stroke's pen (app.js snapStroke): the 390 as is; AMOLED never under 1 px; MIP hairlines (≤ 1.6 units) 1 px,
    // everything else at least 2
    (:amoled)
    function px(w as Numeric) as Number {
        var v = Math.round(w.toFloat() * s).toNumber();
        return v < 1 ? 1 : v;
    }

    (:mip)
    function px(w as Numeric) as Number {
        var v = Math.round(w.toFloat() * s).toNumber();
        return w <= 1.6 ? 1 : v < 2 ? 2 : v;
    }

    // the AMOLEDs' rectangle and circle: the colour as it is, blended
    (:amoled)
    function rect(dc as Dc, e as Array) as Void {
        var x = X(e[1]);
        var y = Y(e[2]);
        var w = (e[3] as Numeric) * s;
        var h = (e[4] as Numeric) * s;
        var rx = (e[5] as Numeric) * s;
        if (rx > h / 2) {
            rx = h / 2;
        }
        ink(dc, e[6] as Number, e[7] as Float);
        if ((e[8] as Numeric) > 0) {
            dc.setPenWidth(px(e[8] as Numeric));
            if (rx > 0) {
                dc.drawRoundedRectangle(x, y, w, h, rx);
            } else {
                dc.drawRectangle(x, y, w, h);
            }
        } else if (rx > 0) {
            dc.fillRoundedRectangle(x, y, w, h, rx);
        } else {
            dc.fillRectangle(x, y, w, h);
        }
    }

    (:amoled)
    function circle(dc as Dc, e as Array) as Void {
        ink(dc, e[4] as Number, e[5] as Float);
        if ((e[6] as Numeric) > 0) {
            dc.setPenWidth(px(e[6] as Numeric));
            dc.drawCircle(X(e[1]), Y(e[2]), (e[3] as Numeric) * s);
        } else {
            dc.fillCircle(X(e[1]), Y(e[2]), (e[3] as Numeric) * s);
        }
    }

    // the MIP screens' (G.col): card2 outlined, a tint dithered
    (:mip)
    function rect(dc as Dc, e as Array) as Void {
        var x = X(e[1]);
        var y = Y(e[2]);
        var w = (e[3] as Numeric) * s;
        var h = (e[4] as Numeric) * s;
        var rx = (e[5] as Numeric) * s;
        if (rx > h / 2) {
            rx = h / 2;
        }
        var stroke = (e[8] as Numeric) > 0;
        var m = col(e[6] as Number, e[7] as Float, stroke ? 0 : 1);
        if (mipFill(dc, m, x, y, w, h, rx, -1.0)) {
            return;
        }
        ink(dc, m[0] as Number, e[7] as Float);
        if (stroke) {
            dc.setPenWidth(px(e[8] as Numeric));
        }
        box(dc, x, y, w, h, rx, stroke);
    }

    (:mip)
    function box(dc as Dc, x as Float, y as Float, w as Float, h as Float, rx as Float, stroke as Boolean) as Void {
        if (stroke) {
            if (rx > 0) {
                dc.drawRoundedRectangle(x, y, w, h, rx);
            } else {
                dc.drawRectangle(x, y, w, h);
            }
        } else if (rx > 0) {
            dc.fillRoundedRectangle(x, y, w, h, rx);
        } else {
            dc.fillRectangle(x, y, w, h);
        }
    }

    (:mip)
    function circle(dc as Dc, e as Array) as Void {
        var cx = X(e[1]);
        var cy = Y(e[2]);
        var r = (e[3] as Numeric) * s;
        var stroke = (e[6] as Numeric) > 0;
        var m = col(e[4] as Number, e[5] as Float, stroke ? 0 : 1);
        if (mipFill(dc, m, cx - r, cy - r, 2 * r, 2 * r, 0.0, r)) {
            return;
        }
        ink(dc, m[0] as Number, e[5] as Float);
        if (stroke) {
            dc.setPenWidth(px(e[6] as Numeric));
            dc.drawCircle(cx, cy, r);
        } else {
            dc.fillCircle(cx, cy, r);
        }
    }

    // [m] (col()'s) as the MIP screens fill it, in the box [x, y, w, h] (corners [rx]) or the circle of radius [r] ≥ 0
    // filling it: card2 black with a 2 px AAAAAA outline, a tint a dither; false when [m] is a plain colour
    (:mip)
    function mipFill(dc as Dc, m as Array, x as Float, y as Float, w as Float, h as Float, rx as Float, r as Float) as Boolean {
        var c = m[0] as Number;
        if (c == CARD) {
            ink(dc, 0, 1.0);
            if (r >= 0) {
                dc.fillCircle(x + r, y + r, r);
            } else {
                box(dc, x, y, w, h, rx, false);
            }
            ink(dc, 0xAAAAAA, 1.0);
            dc.setPenWidth(2);
            if (r >= 0) {
                dc.drawCircle(x + r, y + r, r - 1);
            } else {
                box(dc, x + 1, y + 1, w - 2, h - 2, rx > 1 ? rx - 1 : 0, true);
            }
            return true;
        }
        if ((m[1] as Number) > 0) {
            dither(dc, x, y, w, h, r, c, m[1] as Number);
            return true;
        }
        return false;
    }

    // an ordered dither in the screen's pixels (app.js G.dither): 50 % a checkerboard (its pixels on every other
    // anti-diagonal, drawn as lines), 25 % one pixel in four; in the box [x, y, w, h], or the circle of radius [r] ≥ 0
    // filling it
    (:mip)
    function dither(dc as Dc, x as Float, y as Float, w as Float, h as Float, r as Float, c as Number, pct as Number) as Void {
        var x0 = Math.round(x + 0.5).toNumber();
        var y0 = Math.round(y + 0.5).toNumber();
        var x1 = Math.round(x + w + 0.5).toNumber();
        var y1 = Math.round(y + h + 0.5).toNumber();
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        if (dc has :setAntiAlias) {
            dc.setAntiAlias(false);
        }
        dc.setColor(c, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(1);
        dc.setClip(x0, y0, x1 - x0, y1 - y0);
        var cx = x + w / 2.0;
        var cy = y + h / 2.0;
        if (pct >= 50) {
            for (var k = ((x0 + y0) / 2) * 2; k <= x1 + y1; k += 2) {
                var ya = y0.toFloat();
                var yb = y1.toFloat();
                if (r >= 0) {
                    // the anti-diagonal x + y = k inside the circle: u = y − cy solves 2u² − 2au + a² − r² ≤ 0
                    var a = k - cx - cy;
                    var q = 2 * r * r - a * a;
                    if (q < 0) {
                        continue;
                    }
                    var sq = Math.sqrt(q);
                    ya = cy + (a - sq) / 2.0;
                    yb = cy + (a + sq) / 2.0;
                }
                var ys = Math.ceil(ya).toNumber();
                var ye = Math.floor(yb).toNumber();
                if (ye >= ys) {
                    dc.drawLine(k - ys, ys, k - ye - 1, ye + 1);
                }
            }
        } else {
            for (var yy = y0 + (y0 & 1); yy < y1; yy += 2) {
                var xa = x0;
                var xb = x1;
                if (r >= 0) {
                    var dy = yy - cy;
                    if (dy * dy > r * r) {
                        continue;
                    }
                    var hw = Math.sqrt(r * r - dy * dy);
                    xa = Math.ceil(cx - hw).toNumber();
                    xb = Math.floor(cx + hw).toNumber() + 1;
                }
                for (var xx = xa + (xa & 1); xx < xb; xx += 2) {
                    dc.drawPoint(xx, yy);
                }
            }
        }
        dc.clearClip();
        if (dc has :setAntiAlias) {
            dc.setAntiAlias(true);
        }
    }

    function poly(dc as Dc, e as Array) as Void {
        var pts = e[1] as Array;
        var m = col(e[2] as Number, e[3] as Float, 0);
        var w = px(e[4] as Numeric);
        var dash = e[5];
        ink(dc, m[0] as Number, e[3] as Float);
        dc.setPenWidth(w);
        if (dash != null) {
            // a dashed straight line (the limits, the trend ray)
            var x1 = X(pts[0]);
            var y1 = Y(pts[1]);
            var x2 = X(pts[2]);
            var y2 = Y(pts[3]);
            var len = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
            if (len <= 0) {
                return;
            }
            var on = (dash[0] as Numeric) * s;
            var off = (dash[1] as Numeric) * s;
            var ux = (x2 - x1) / len;
            var uy = (y2 - y1) / len;
            for (var d = 0.0; d < len; d += on + off) {
                var d2 = d + on > len ? len : d + on;
                dc.drawLine(x1 + ux * d, y1 + uy * d, x1 + ux * d2, y1 + uy * d2);
            }
            return;
        }
        for (var j = 2; j < pts.size(); j += 2) {
            dc.drawLine(X(pts[j - 2]), Y(pts[j - 1]), X(pts[j]), Y(pts[j + 1]));
        }
        if (w >= 3) {
            for (var j = 0; j < pts.size(); j += 2) {
                dc.fillCircle(X(pts[j]), Y(pts[j + 1]), w / 2.0);
            }
        }
    }

    function arc(dc as Dc, e as Array) as Void {
        var m = col(e[6] as Number, e[7] as Float, 0);
        var cx = X(e[1]);
        var cy = Y(e[2]);
        var r = (e[3] as Numeric) * s;
        var a0 = e[4] as Numeric;
        var a1 = e[5] as Numeric;
        var w = px(e[8] as Numeric);
        ink(dc, m[0] as Number, e[7] as Float);
        dc.setPenWidth(w);
        if (a1 - a0 >= 359.5) {
            dc.drawCircle(cx, cy, r);
            return;
        }
        if (a1 - a0 <= 0) {
            return;
        }
        dc.drawArc(cx, cy, r, Graphics.ARC_CLOCKWISE, 90 - a0, 90 - a1);
        if (e[9]) {
            var p0 = AppMath.P(r, a0);
            var p1 = AppMath.P(r, a1);
            dc.fillCircle(cx + p0[0], cy + p0[1], w / 2.0);
            dc.fillCircle(cx + p1[0], cy + p1[1], w / 2.0);
        }
    }

    // a text with its baseline at y, anchored by its drawn width: atlas glyphs, or a font resource on the watches
    // without tinted bitmaps (:plain; each watch builds one of the two, monkey.jungle's screen classes)
    function text(dc as Dc, e as Array) as Void {
        var a = loadFont(e[4] as String);
        if (a.size() == 0) {
            missing(e[4] as String);
            return;
        }
        var tc = (col(e[5] as Number, 1.0, 2)[0] as Number) & 0xFFFFFF;
        var ls = e.size() > 7 ? (e[7] as Numeric) * s : 0;
        glyphs(dc, e[3] as String, a, tc, X(e[1]) + 0.5, Y(e[2]) + 0.5, e[6] as Number, ls);
    }

    // a font resource; the system draws bitmap fonts 1 px under the line
    (:plain)
    function glyphs(dc as Dc, s0 as String, a as Array, tc as Number, x as Float, base as Float, anchor as Number, ls as Numeric) as Void {
        var fr = a[0];
        dc.setColor(tc, Graphics.COLOR_TRANSPARENT);
        var cs = s0.toCharArray();
        if (anchor >= 0) {
            var w = dc.getTextWidthInPixels(s0, fr) + ls * cs.size();
            x -= anchor == 0 ? w / 2.0 : w;
        }
        var top = base - (a[1] as Number) - 1;
        if (ls == 0) {
            dc.drawText(Math.floor(x + 0.5), top, fr, s0, Graphics.TEXT_JUSTIFY_LEFT);
            return;
        }
        for (var i = 0; i < cs.size(); i++) {
            var ch = cs[i].toString();
            dc.drawText(Math.floor(x + 0.5), top, fr, ch, Graphics.TEXT_JUSTIFY_LEFT);
            x += dc.getTextWidthInPixels(ch, fr) + ls;
        }
    }

    // atlas glyphs, tinted
    (:atlas)
    function glyphs(dc as Dc, s0 as String, a as Array, tc as Number, x as Float, base as Float, anchor as Number, ls as Numeric) as Void {
        var chars = a[1] as String;
        var m = a[2] as Array<Number>;
        if (anchor >= 0) {
            var w = width(a, s0) + ls * s0.length();
            x -= anchor == 0 ? w / 2.0 : w;
        }
        var top = base - (a[3] as Number);
        var cs = s0.toCharArray();
        var bm = a[0];
        for (var i = 0; i < cs.size(); i++) {
            var k = chars.find(cs[i].toString());
            if (k == null) {
                continue;
            }
            // x | y << 9 | w << 17 | h << 24, then xo + 128 | (yo + 128) << 8 | advance·16 << 16
            var p = m[k * 2];
            var q = m[k * 2 + 1];
            var gw0 = (p >> 17) & 127;
            var gh = (p >> 24) & 127;
            if (gw0 > 1 || gh > 1) {
                var gx = p & 511;
                var gy = (p >> 9) & 255;
                var xo = (q & 255) - 128;
                var yo = ((q >> 8) & 255) - 128;
                dc.drawBitmap2(Math.floor(x + xo + 0.5) - gx, Math.floor(top + yo + 0.5) - gy, bm, {
                    :bitmapX => gx, :bitmapY => gy, :bitmapWidth => gw0, :bitmapHeight => gh, :tintColor => tc,
                });
            }
            x += (q >> 16) / 16.0 + ls;
        }
    }

    (:atlas)
    function width(a as Array, s0 as String) as Float {
        var chars = a[1] as String;
        var m = a[2] as Array<Number>;
        var w = 0.0;
        var cs = s0.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            var k = chars.find(cs[i].toString());
            if (k != null) {
                w += (m[k * 2 + 1] >> 16) / 16.0;
            }
        }
        return w;
    }

    // an icon: an icon font's glyph "A" + index, its tile drawn from its top left (1 px under, as every bitmap font)
    (:plain)
    function icon(dc as Dc, e as Array) as Void {
        var size = Math.round((e[4] as Numeric) * s).toNumber();
        var b = glance ? GlanceFontTable.icon(e[1] as String, size) : (iconAt as Method).invoke(e[1], size) as Array?;
        if (b == null) {
            return;
        }
        var si = b[3] as Number;
        var sheet = sheets[si];
        if (sheet == null) {
            sheet = WatchUi.loadResource(glance ? GlanceFontTable.sheet(si) : (sheetAt as Method).invoke(si) as ResourceId);
            sheets[si] = sheet;
        }
        var c = (col(e[5] as Number, 1.0, 1)[0] as Number) & 0xFFFFFF;
        var box = b[2] as Number;
        dc.setColor(c, Graphics.COLOR_TRANSPARENT);
        dc.drawText(Math.round(X(e[2]) + 0.5 - box / 2.0), Math.round(Y(e[3]) + 0.5 - box / 2.0) - 1, sheet,
            ((65 + (b[0] as Number)) as Number).toChar().toString(), Graphics.TEXT_JUSTIFY_LEFT);
    }

    // an icon: its sheet's tile, tinted and scaled to [size]
    (:atlas)
    function icon(dc as Dc, e as Array) as Void {
        var size = Math.round((e[4] as Numeric) * s).toNumber();
        var b = glance ? GlanceFontTable.icon(e[1] as String, size) : (iconAt as Method).invoke(e[1], size) as Array?;
        if (b == null) {
            return;
        }
        var si = b[3] as Number;
        var sheet = sheets[si];
        if (sheet == null) {
            sheet = WatchUi.loadResource(glance ? GlanceFontTable.sheet(si) : (sheetAt as Method).invoke(si) as ResourceId);
            sheets[si] = sheet;
        }
        var c = (col(e[5] as Number, 1.0, 1)[0] as Number) & 0xFFFFFF;
        var box = b[2] as Number;
        var bs = size.toFloat() / (box - 4);
        var t = new Graphics.AffineTransform();
        t.scale(bs, bs);
        t.translate(-box / 2.0 - (b[0] as Number), -box / 2.0 - (b[1] as Number));
        dc.drawBitmap2(X(e[2]) + 0.5, Y(e[3]) + 0.5, sheet, {
            :bitmapX => b[0], :bitmapY => b[1], :bitmapWidth => box, :bitmapHeight => box,
            :tintColor => c, :filterMode => Graphics.FILTER_MODE_BILINEAR, :transform => t,
        });
    }
}

// The app's own drawing, out of the glance's code (its 64 KB): a bitmap resource [size] units wide at its centre, drawn
// at its own size where the class's resource is made at it (AppDraw.bitmapAt)
module AppDrawApp {
    function bitmap(dc as Dc, e as Array) as Void {
        var b = WatchUi.loadResource(e[1] as ResourceId) as BitmapResource;
        var bw = b.getWidth();
        var size = (e[4] as Numeric) * AppDraw.s;
        if (AppMath.abs(size - bw) < 1.5 || !(Graphics has :AffineTransform)) {
            // drawn at its own size (the class's resource is made at the size it is drawn)
            dc.drawBitmap(Math.round(AppDraw.X(e[2]) + 0.5 - bw / 2.0), Math.round(AppDraw.Y(e[3]) + 0.5 - b.getHeight() / 2.0), b);
            return;
        }
        var sc = size / bw;
        var t = new Graphics.AffineTransform();
        t.scale(sc, sc);
        dc.drawBitmap2(AppDraw.X(e[2]) - bw * sc / 2, AppDraw.Y(e[3]) - b.getHeight() * sc / 2, b, {
            :transform => t, :filterMode => Graphics.FILTER_MODE_BILINEAR,
        });
    }
}
