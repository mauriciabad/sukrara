// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.StringUtil;
import Toybox.System;
import Toybox.WatchUi;

// The data field's model: app.js's display list (G) for its screens (FieldLayout). Elements in the reference's layout
// units (origin at the screen's centre, y down, × FieldFontTable.S = px), each a small array, the first item its kind:
//   R [0, x, y, w, h, col, op]                     a filled rectangle
//   C [1, cx, cy, r, col, op, sw]                  a circle (sw 0 = filled)
//   L [2, x1, y1, x2, y2, col, op, sw, on, off, round] a line (on > 0: dashed), round caps or butt
//   P [3, [x, y, x, y, …], col, op]                a filled polygon
//   T [4, x, baseline, s, key, col, anchor, ls]    a text (anchor -1 start, 0 middle, 1 end; key FieldFontTable's)
//   I [5, cx, cy, size, col]                       the bell, tinted
// Colours as the reference gives them; the painter maps them on MIP as app.js's G.col (the 64 colours, no alpha, a
// light tint as a dither: SPEC §14.3); strokes to whole px when added.
class FieldG {
    var els as Array = [];

    function initialize() {}

    function r(x as Numeric, y as Numeric, w as Numeric, h as Numeric, col as Number, op as Float) as Void {
        els.add([0, x, y, w < 0 ? 0 : w, h < 0 ? 0 : h, col, op]);
    }

    function c(cx as Numeric, cy as Numeric, rad as Numeric, col as Number, op as Float, sw as Numeric) as Void {
        els.add([1, cx, cy, rad, col, op, FieldDraw.sw(sw)]);
    }

    function l(x1 as Numeric, y1 as Numeric, x2 as Numeric, y2 as Numeric, col as Number, op as Float, sw as Numeric,
            on as Numeric, off as Numeric, round as Boolean) as Void {
        els.add([2, x1, y1, x2, y2, col, op, FieldDraw.sw(sw), on, off, round]);
    }

    function p(pts as Array, col as Number, op as Float) as Void {
        els.add([3, pts, col, op]);
    }

    // a text at [fs] units, weight [fw] (500 / 600 / 700), [ls] letter spacing (units); the big numbers' atlas for a
    // value (digits, LO / HI, "---") at weight 700
    function t(x as Numeric, y as Numeric, s as String, fs as Numeric, fw as Number, col as Number, anchor as Number,
            ls as Numeric) as Void {
        els.add([4, x, y, s, FieldDraw.font(fs, fw, s), col, anchor, ls]);
    }

    function icon(cx as Numeric, cy as Numeric, size as Numeric, col as Number) as Void {
        els.add([5, cx, cy, size, col]);
    }

    // Sukrara's arrow (aura.js): pointing right, turned by [deg] (negative = up); doubled. Few locals: the data field's
    // stack is small
    function arrow(cx as Numeric, cy as Numeric, box as Numeric, deg as Numeric, doubled as Boolean, col as Number) as Void {
        var t = [box * 1.05, Math.cos(deg * Math.PI / 180.0), Math.sin(deg * Math.PI / 180.0), cx, cy];
        shape(t, [-0.42, -0.07, 0.25, -0.07, 0.25, 0.07, -0.42, 0.07], col);
        shape(t, [0.46, 0, 0.06, 0.3, 0.16, 0, 0.06, -0.3], col);
        if (doubled) {
            shape(t, [0.12, 0, -0.28, 0.3, -0.18, 0, -0.28, -0.3], col);
        }
    }

    // [q] the shape's points in box units; t = [size, cos, sin, cx, cy]
    function shape(t as Array, q as Array, col as Number) as Void {
        var out = [];
        for (var j = 0; j < q.size(); j += 2) {
            var x = Math.round(q[j] * t[0] * 10) / 10.0;
            var y = Math.round(q[j + 1] * t[0] * 10) / 10.0;
            out.add(t[3] + x * t[1] - y * t[2]);
            out.add(t[4] + x * t[2] + y * t[1]);
        }
        p(out, col, 1.0);
    }
}

module FieldMath {
    function r1(x as Numeric) as Float {
        return Math.round(x * 10) / 10.0;
    }

    function clamp(x as Numeric, a as Numeric, b as Numeric) as Numeric {
        return x < a ? a : x > b ? b : x;
    }

    function min(a as Numeric, b as Numeric) as Numeric {
        return a < b ? a : b;
    }

    function max(a as Numeric, b as Numeric) as Numeric {
        return a > b ? a : b;
    }

    function abs(a as Numeric) as Numeric {
        return a < 0 ? -a : a;
    }
}

// Paints a FieldG on a Dc: [ox, oy] the reference's origin in the Dc's pixels (a shape at device x covers x − 0.5 …
// x + 0.5: on a 390 px screen the origin is 194.5; bitmaps sit at + 0.5), [s] the scale. The data field's Dc is its
// slot, so the origin is the screen centre less the slot's place on the screen.
module FieldDraw {
    var ox as Float = 194.5;
    var oy as Float = 194.5;
    var s as Float = 1.0;
    var fonts as Dictionary = {};
    var bell as WatchUi.BitmapResource? = null;
    // the dither's phase: the screen's centre in the Dc's pixels (app.js: the pattern starts at the layout's origin)
    var px0 as Number = 195;
    var py0 as Number = 195;
    // the screen, px
    var W as Number = 390;
    var H as Number = 390;

    // the screen's half width and height in layout units
    function hw() as Float {
        return W / 2.0 / FieldFontTable.S;
    }

    function hh() as Float {
        return H / 2.0 / FieldFontTable.S;
    }

    // --- app.js's class rules (SPEC §14.1) ---

    // a size (units) at the size the class draws it: the nearest of its atlas set (ties: the larger); the 390 keeps
    // the approved sizes, [any] snaps there too (a size the layout computes)
    function fe(fs as Numeric, any as Boolean) as Float {
        if (FieldFontTable.R390 && !any) {
            return fs.toFloat();
        }
        var px = fs * FieldFontTable.S;
        var set = FieldFontTable.ATLAS;
        var best = set[0] as Number;
        for (var i = 0; i < set.size(); i++) {
            var d = FieldMath.abs(set[i] - px);
            var bd = FieldMath.abs(best - px);
            if (d < bd || (d == bd && set[i] > best)) {
                best = set[i] as Number;
            }
        }
        return best / FieldFontTable.S;
    }

    // a stroke width (units) as the class draws it: the 390 as is; AMOLED to the half px, never under 1; MIP whole px,
    // hairlines (≤ 1.6 units) 1 px, else at least 2
    function sw(w as Numeric) as Float {
        if (w == 0 || FieldFontTable.R390) {
            return w.toFloat();
        }
        var px = w * FieldFontTable.S;
        var q;
        if (!FieldFontTable.MIP) {
            q = FieldMath.max(1.0, Math.round(px * 2) / 2.0);
        } else {
            q = w <= 1.6 ? 1.0 : FieldMath.max(2.0, Math.round(px).toFloat());
        }
        return q / FieldFontTable.S;
    }

    // the font key for a text: "n<px>" for a value's characters at weight 700, else "a<px><m|s|b>"
    function font(fs as Numeric, fw as Number, str as String) as String {
        var px = Math.round(fe(fs, false) * FieldFontTable.S).toNumber();
        if (fw >= 700 && big(str)) {
            return "n" + px;
        }
        return "a" + px + (fw >= 700 ? "b" : fw >= 600 ? "s" : "m");
    }

    function big(str as String) as Boolean {
        var cs = str.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            if ("0123456789.:LOHI-".find(cs[i].toString()) == null) {
                return false;
            }
        }
        return true;
    }

    // a colour on this class: [colour, opacity, dither %] (MIP: app.js G.col; AMOLED: as given)
    function col(c as Number, op as Float, fill as Boolean) as Array {
        if (!FieldFontTable.MIP || c == 0) {
            return [c, op, 0];
        }
        var r = (c >> 16) & 255;
        var g = (c >> 8) & 255;
        var b = c & 255;
        if (fill && op < 0.6) {
            var k = FieldMath.min(1.0, op * 3);
            var t = near64(r * k, g * k, b * k);
            if (t == 0) {
                var m = FieldMath.max(r, FieldMath.max(g, b));
                t = near64(r == m ? 85 : 0, g == m ? 85 : 0, b == m ? 85 : 0);
            }
            return [t, 1.0, op >= 0.1 ? 50 : 25];
        }
        if (op >= 0.5) {
            return [near64(r, g, b), 1.0, 0];
        }
        var dk = near64(r * 0.55, g * 0.55, b * 0.55);
        return [dk == 0 ? 0x555555 : dk, 1.0, 0];
    }

    // the nearest of the 64 (each channel to 00 / 55 / AA / FF)
    function near64(r as Numeric, g as Numeric, b as Numeric) as Number {
        r = (Math.round((r > 255 ? 255 : r < 0 ? 0 : r) / 85.0) * 85).toNumber();
        g = (Math.round((g > 255 ? 255 : g < 0 ? 0 : g) / 85.0) * 85).toNumber();
        b = (Math.round((b > 255 ? 255 : b < 0 ? 0 : b) / 85.0) * 85).toNumber();
        return (r << 16) | (g << 8) | b;
    }

    // --- painting ---

    function release() as Void {
        fonts = {};
        bell = null;
        tint = null;
        tintKey = "";
    }

    function loadFont(key as String) as Array {
        var a = fonts[key];
        if (a != null) {
            return a as Array;
        }
        var t = FieldFontTable.atlas(key);
        if (t.size() == 0) {
            missing(key);
            return [];
        }
        // the font table's other kind: a font resource and its baseline (the watches without enhanced graphics)
        if (fonts.size() >= 4) {
            fonts = {};
        }
        if (t[1] instanceof Number) {
            a = [WatchUi.loadResource(t[0]), t[1]];
            fonts[key] = a;
            return a as Array;
        }
        var j = WatchUi.loadResource(t[1]) as Array;
        var m = StringUtil.convertEncodedString(j[1], {
            :fromRepresentation => StringUtil.REPRESENTATION_STRING_BASE64,
            :toRepresentation => StringUtil.REPRESENTATION_BYTE_ARRAY,
        });
        a = [WatchUi.loadResource(t[0]), j[0], m, j[2]];
        fonts[key] = a;
        return a as Array;
    }

    // a font key the class lacks (debug builds say so: field_sizes.js / fonts.py)
    (:debug)
    function missing(key as String) as Void {
        System.println("FONT|missing|" + key);
    }

    (:release)
    function missing(key as String) as Void {}

    function argb(c as Number, alpha as Float) as Number {
        if (alpha >= 1.0 || FieldFontTable.MIP) {
            var v = 0xFF000000 | c;
            return v == -1 ? 0xFFFFFFFE : v;
        }
        var al = (FieldMath.clamp(alpha, 0, 1) * 255 + 0.5).toNumber();
        return Graphics.createColor(al, (c >> 16) & 255, (c >> 8) & 255, c & 255);
    }

    function X(x) as Float {
        return ox + (x as Numeric) * s;
    }

    function Y(y) as Float {
        return oy + (y as Numeric) * s;
    }

    function pw(w as Numeric) as Number {
        var v = Math.round(w.toFloat() * s).toNumber();
        return v < 1 ? 1 : v;
    }

    function paint(dc as Dc, g as FieldG) as Void {
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
                var fill = (e[6] as Numeric) == 0;
                ink(dc, paintCol(e[4] as Number, e[5] as Float, fill ? 1 : 0));
                                if (!fill) {
                    dc.setPenWidth(pw(e[6] as Numeric));
                    dc.drawCircle(X(e[1]), Y(e[2]), (e[3] as Numeric) * s);
                } else {
                    dc.fillCircle(X(e[1]), Y(e[2]), (e[3] as Numeric) * s);
                }
            } else if (k == 2) {
                line(dc, e);
            } else if (k == 3) {
                poly(dc, e);
            } else if (k == 4) {
                text(dc, e);
            } else if (k == 5) {
                icon(dc, e);
            }
        }
    }

    // a colour as this class paints it: ARGB (AMOLED: with its opacity; MIP: one of the 64, opaque); role 0 a stroke,
    // 1 a fill, 2 a text (dim is never text on MIP)
    function paintCol(c as Number, op as Float, role as Number) as Number {
        if (FieldFontTable.MIP && c != 0) {
            c = col(c, op, role == 1)[0] as Number;
            if (role == 2 && c == 0x555555) {
                c = 0xAAAAAA;
            }
            op = 1.0;
        }
        return argb(c, op);
    }

    // the colour for what comes next, fill and stroke: ARGB where the watch blends (setFill / setStroke), else its RGB
    // (the Forerunner 255 family: no alpha, a MIP screen)
    function ink(dc as Dc, argbCol as Number) as Void {
        if (dc has :setFill) {
            dc.setFill(argbCol);
            dc.setStroke(argbCol);
        } else {
            dc.setColor(argbCol & 0xFFFFFF, Graphics.COLOR_TRANSPARENT);
        }
    }

    function rect(dc as Dc, e as Array) as Void {
        if (FieldFontTable.MIP) {
            var m = col(e[5] as Number, e[6] as Float, true);
            if ((m[2] as Number) > 0) {
                dither(dc, e, m[0] as Number, m[2] as Number);
                return;
            }
        }
        ink(dc, paintCol(e[5] as Number, e[6] as Float, 1));
        dc.fillRectangle(X(e[1]), Y(e[2]), (e[3] as Numeric) * s, (e[4] as Numeric) * s);
    }

    function poly(dc as Dc, e as Array) as Void {
        var pts = e[1] as Array;
        var q = [];
        for (var j = 0; j < pts.size(); j += 2) {
            q.add([X(pts[j]), Y(pts[j + 1])]);
        }
        ink(dc, paintCol(e[2] as Number, e[3] as Float, 1));
        dc.fillPolygon(q);
    }

    function line(dc as Dc, e as Array) as Void {
        var col = paintCol(e[5] as Number, e[6] as Float, 0);
        var w = (e[7] as Numeric) * s;
        ink(dc, col);
        dc.setPenWidth(pw(e[7] as Numeric));
        var x1 = X(e[1]);
        var y1 = Y(e[2]);
        var x2 = X(e[3]);
        var y2 = Y(e[4]);
        var round = e[10] as Boolean;
        var on = (e[8] as Numeric) * s;
        if (on <= 0) {
            dc.drawLine(x1, y1, x2, y2);
            if (round && w >= 3) {
                dc.fillCircle(x1, y1, w / 2.0);
                dc.fillCircle(x2, y2, w / 2.0);
            }
            return;
        }
        var len = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        if (len <= 0) {
            return;
        }
        var off = (e[9] as Numeric) * s;
        var ux = (x2 - x1) / len;
        var uy = (y2 - y1) / len;
        for (var d = 0.0; d < len; d += on + off) {
            var d2 = d + on > len ? len : d + on;
            var ax = x1 + ux * d;
            var ay = y1 + uy * d;
            // a round-capped dash reaches w / 2 past both its ends (the limits' dots)
            var c = round ? w / 2.0 : 0.0;
            dc.drawLine(ax - ux * c, ay - uy * c, x1 + ux * (d2 + c), y1 + uy * (d2 + c));
        }
    }

    // a text with its baseline at y, anchored by its drawn width (letter spacing after every glyph, as SVG)
    function text(dc as Dc, e as Array) as Void {
        var s0 = e[3] as String;
        var a = loadFont(e[4] as String);
        if (a.size() == 0) {
            return;
        }
        var col = paintCol(e[5] as Number, 1.0, 2);
        var anchor = e[6] as Number;
        var ls = (e[7] as Numeric) * s;
        var x = X(e[1]) + 0.5;
        var base = Y(e[2]) + 0.5;
        if (a.size() == 2) {
            fontText(dc, e, a, col, x, base - (a[1] as Number) - 1);
            return;
        }
        var chars = a[1] as String;
        var m = a[2] as ByteArray;
        var cs = s0.toCharArray();
        if (anchor >= 0) {
            var w = width(a, s0) + ls * cs.size();
            x -= anchor == 0 ? w / 2.0 : w;
        }
        var top = base - (a[3] as Number);
        var bm = a[0];
        for (var i = 0; i < cs.size(); i++) {
            var k = chars.find(cs[i].toString());
            if (k == null) {
                continue;
            }
            var o = k * 10;
            var gw = m[o + 4];
            var gh = m[o + 5];
            if (gw > 1 || gh > 1) {
                var xo = m[o + 6] > 127 ? m[o + 6] - 256 : m[o + 6];
                var yo = m[o + 7] > 127 ? m[o + 7] - 256 : m[o + 7];
                var gx = (m[o] << 8) | m[o + 1];
                var gy = (m[o + 2] << 8) | m[o + 3];
                dc.drawBitmap2(Math.floor(x + xo + 0.5) - gx, Math.floor(top + yo + 0.5) - gy, bm, {
                    :bitmapX => gx, :bitmapY => gy, :bitmapWidth => gw, :bitmapHeight => gh, :tintColor => col,
                });
            }
            x += ((m[o + 8] << 8) | m[o + 9]) / 16.0 + ls;
        }
    }

    // a text in a font resource (drawText; the system draws bitmap fonts 1 px under the line: AuraDraw.text), [top]
    // its line's top; letter spacing glyph by glyph
    function fontText(dc as Dc, e as Array, a as Array, col as Number, x as Float, top as Float) as Void {
        var s0 = e[3] as String;
        var ls = (e[7] as Numeric) * s;
        var f = a[0];
        var cs = s0.toCharArray();
        dc.setColor(col & 0xFFFFFF, Graphics.COLOR_TRANSPARENT);
        var w = dc.getTextWidthInPixels(s0, f) + (ls != 0 ? ls * cs.size() : 0);
        var anchor = e[6] as Number;
        x -= anchor < 0 ? 0 : anchor == 0 ? w / 2.0 : w;
        if (ls == 0) {
            dc.drawText(Math.floor(x + 0.5), top, f, s0, Graphics.TEXT_JUSTIFY_LEFT);
            return;
        }
        for (var i = 0; i < cs.size(); i++) {
            var c = cs[i].toString();
            dc.drawText(Math.floor(x + 0.5), top, f, c, Graphics.TEXT_JUSTIFY_LEFT);
            x += dc.getTextWidthInPixels(c, f) + ls;
        }
    }

    function width(a as Array, s0 as String) as Float {
        var chars = a[1] as String;
        var m = a[2] as ByteArray;
        var w = 0.0;
        var cs = s0.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            var k = chars.find(cs[i].toString());
            if (k != null) {
                w += ((m[k * 10 + 8] << 8) | m[k * 10 + 9]) / 16.0;
            }
        }
        return w;
    }

    function icon(dc as Dc, e as Array) as Void {
        if (bell == null) {
            bell = WatchUi.loadResource(FieldFontTable.bell()) as WatchUi.BitmapResource;
        }
        var box = FieldFontTable.BELL_BOX;
        var size = (e[3] as Numeric) * s;
        var bs = size / FieldFontTable.BELL;
        var t = new Graphics.AffineTransform();
        t.scale(bs, bs);
        t.translate(-box / 2.0, -box / 2.0);
        dc.drawBitmap2(X(e[1]) + 0.5, Y(e[2]) + 0.5, bell as WatchUi.BitmapResource, {
            :tintColor => paintCol(e[4] as Number, 1.0, 1) & 0xFFFFFF, :filterMode => Graphics.FILTER_MODE_BILINEAR, :transform => t,
        });
    }

    // the tint as every other pixel in both directions (50 %: and the ones between, a checkerboard), in the screen's
    // pixels from its centre, over the black ground (the sparkline's band): drawn once into a bitmap, then copied each
    // second (a point per pixel is slow on the watch)
    var tint as Graphics.BufferedBitmap? = null;
    var tintKey as String = "";

    function dither(dc as Dc, e as Array, c as Number, pct as Number) as Void {
        var x0 = Math.round(X(e[1]) + 0.5).toNumber();
        var y0 = Math.round(Y(e[2]) + 0.5).toNumber();
        var w = Math.round(X((e[1] as Numeric) + (e[3] as Numeric)) + 0.5).toNumber() - x0;
        var h = Math.round(Y((e[2] as Numeric) + (e[4] as Numeric)) + 0.5).toNumber() - y0;
        if (w <= 0 || h <= 0) {
            return;
        }
        // the pattern's phase at the band's corner
        var key = w + "," + h + "," + c + "," + pct + "," + ((x0 - px0) & 1) + ((y0 - py0) & 1);
        if (!key.equals(tintKey)) {
            tint = null;
            var bm = Graphics.createBufferedBitmap({:width => w, :height => h}).get() as Graphics.BufferedBitmap;
            var bd = bm.getDc();
            bd.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
            bd.clear();
            bd.setColor(c, Graphics.COLOR_TRANSPARENT);
            for (var y = 0; y < h; y++) {
                var dy = (y0 + y - py0) & 1;
                if (dy == 1 && pct < 50) {
                    continue;
                }
                for (var x = (x0 - px0 + dy) & 1; x < w; x += 2) {
                    bd.drawPoint(x, y);
                }
            }
            tint = bm;
            tintKey = key;
        }
        dc.drawBitmap(x0, y0, tint as Graphics.BufferedBitmap);
    }
}
