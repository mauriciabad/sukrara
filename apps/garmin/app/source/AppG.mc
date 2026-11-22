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
// the scale (the screen's size / 390).
(:glance)
module AppDraw {
    var ox as Float = 194.5;
    var oy as Float = 194.5;
    var s as Float = 1.0;
    var fonts as Dictionary = {};
    var sheets as Dictionary = {}; // the icon sheets loaded, by index
    var glance as Boolean = false;
    // the glance paints its display list once: each element goes as it's painted, room for the atlases (#217)
    var once as Boolean = false;
    // the foreground's tables (AppFontTable): set by the app's view, so the glance's code never names them
    var table as Method? = null;
    var iconAt as Method? = null;
    var sheetAt as Method? = null;

    // a font key: a text "a<px><m|s|b>", a big number "n<px>"
    function font(fs as Numeric, fw as Number, big as Boolean) as String {
        var px = Math.round(fs.toFloat() * s).toNumber();
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

    function argb(col as Number, alpha as Float) as Number {
        if (alpha >= 1.0) {
            var c = 0xFF000000 | col;
            return c == -1 ? 0xFFFFFFFE : c;
        }
        var al = (AppMath.clamp(alpha, 0, 1) * 255 + 0.5).toNumber();
        return Graphics.createColor(al, (col >> 16) & 255, (col >> 8) & 255, col & 255);
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
                var col = argb(e[4] as Number, e[5] as Float);
                if ((e[6] as Numeric) > 0) {
                    dc.setStroke(col);
                    dc.setPenWidth(px(e[6] as Numeric));
                    dc.drawCircle(X(e[1]), Y(e[2]), (e[3] as Numeric) * s);
                } else {
                    dc.setFill(col);
                    dc.fillCircle(X(e[1]), Y(e[2]), (e[3] as Numeric) * s);
                }
            } else if (k == 2) {
                poly(dc, e);
            } else if (k == 3) {
                var pts = e[1] as Array;
                var q = [];
                for (var j = 0; j < pts.size(); j += 2) {
                    q.add([X(pts[j]), Y(pts[j + 1])]);
                }
                dc.setFill(argb(e[2] as Number, e[3] as Float));
                dc.fillPolygon(q);
            } else if (k == 4) {
                arc(dc, e);
            } else if (k == 5) {
                text(dc, e);
            } else if (k == 6) {
                icon(dc, e);
            } else if (k == 7) {
                bitmap(dc, e);
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

    function px(w as Numeric) as Number {
        var v = Math.round(w.toFloat() * s).toNumber();
        return v < 1 ? 1 : v;
    }

    function rect(dc as Dc, e as Array) as Void {
        var col = argb(e[6] as Number, e[7] as Float);
        var x = X(e[1]);
        var y = Y(e[2]);
        var w = (e[3] as Numeric) * s;
        var h = (e[4] as Numeric) * s;
        var rx = (e[5] as Numeric) * s;
        if (rx > h / 2) {
            rx = h / 2;
        }
        if ((e[8] as Numeric) > 0) {
            dc.setStroke(col);
            dc.setPenWidth(px(e[8] as Numeric));
            if (rx > 0) {
                dc.drawRoundedRectangle(x, y, w, h, rx);
            } else {
                dc.drawRectangle(x, y, w, h);
            }
        } else {
            dc.setFill(col);
            if (rx > 0) {
                dc.fillRoundedRectangle(x, y, w, h, rx);
            } else {
                dc.fillRectangle(x, y, w, h);
            }
        }
    }

    function poly(dc as Dc, e as Array) as Void {
        var pts = e[1] as Array;
        var col = argb(e[2] as Number, e[3] as Float);
        var w = (e[4] as Numeric) * s;
        var dash = e[5];
        dc.setStroke(col);
        dc.setFill(col);
        dc.setPenWidth(px(e[4] as Numeric));
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
        var col = argb(e[6] as Number, e[7] as Float);
        var cx = X(e[1]);
        var cy = Y(e[2]);
        var r = (e[3] as Numeric) * s;
        var a0 = e[4] as Numeric;
        var a1 = e[5] as Numeric;
        dc.setStroke(col);
        dc.setPenWidth(px(e[8] as Numeric));
        if (a1 - a0 >= 359.5) {
            dc.drawCircle(cx, cy, r);
            return;
        }
        if (a1 - a0 <= 0) {
            return;
        }
        dc.drawArc(cx, cy, r, Graphics.ARC_CLOCKWISE, 90 - a0, 90 - a1);
        if (e[9]) {
            dc.setFill(col);
            var w = (e[8] as Numeric) * s / 2.0;
            var p0 = AppMath.P(r, a0);
            var p1 = AppMath.P(r, a1);
            dc.fillCircle(cx + p0[0], cy + p0[1], w);
            dc.fillCircle(cx + p1[0], cy + p1[1], w);
        }
    }

    // a text with its baseline at y, anchored by its drawn width
    function text(dc as Dc, e as Array) as Void {
        var s0 = e[3] as String;
        var a = loadFont(e[4] as String);
        if (a.size() == 0) {
            missing(e[4] as String);
            return;
        }
        var col = e[5] as Number;
        var anchor = e[6] as Number;
        var x = X(e[1]) + 0.5;
        var base = Y(e[2]) + 0.5;
        if (a.size() == 2) {
            var fr = a[0];
            if (fr has :get) {
                fr = fr.get();
            }
            dc.setColor(col, Graphics.COLOR_TRANSPARENT);
            if (anchor >= 0) {
                var w = dc.getTextWidthInPixels(s0, fr);
                x -= anchor == 0 ? w / 2.0 : w;
            }
            // the system draws bitmap fonts 1 px under the line (AuraDraw.text)
            dc.drawText(Math.floor(x + 0.5), base - (a[1] as Number) - 1, fr, s0, Graphics.TEXT_JUSTIFY_LEFT);
            return;
        }
        var chars = a[1] as String;
        var m = a[2] as Array<Number>;
        var ls = e.size() > 7 ? (e[7] as Numeric) * s : 0;
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
            var gw = (p >> 17) & 127;
            var gh = (p >> 24) & 127;
            if (gw > 1 || gh > 1) {
                var gx = p & 511;
                var gy = (p >> 9) & 255;
                var xo = (q & 255) - 128;
                var yo = ((q >> 8) & 255) - 128;
                dc.drawBitmap2(Math.floor(x + xo + 0.5) - gx, Math.floor(top + yo + 0.5) - gy, bm, {
                    :bitmapX => gx, :bitmapY => gy, :bitmapWidth => gw, :bitmapHeight => gh, :tintColor => col,
                });
            }
            x += (q >> 16) / 16.0 + ls;
        }
    }

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

    function bitmap(dc as Dc, e as Array) as Void {
        var b = WatchUi.loadResource(e[1] as ResourceId) as BitmapResource;
        var sc = (e[4] as Numeric) * s / b.getWidth();
        var t = new Graphics.AffineTransform();
        t.scale(sc, sc);
        dc.drawBitmap2(X(e[2]) - b.getWidth() * sc / 2, Y(e[3]) - b.getHeight() * sc / 2, b, {
            :transform => t, :filterMode => Graphics.FILTER_MODE_BILINEAR,
        });
    }

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
        var box = b[2] as Number;
        var bs = size.toFloat() / (box - 4);
        var t = new Graphics.AffineTransform();
        t.scale(bs, bs);
        t.translate(-box / 2.0 - (b[0] as Number), -box / 2.0 - (b[1] as Number));
        dc.drawBitmap2(X(e[2]) + 0.5, Y(e[3]) + 0.5, sheet, {
            :bitmapX => b[0], :bitmapY => b[1], :bitmapWidth => box, :bitmapHeight => box,
            :tintColor => e[5], :filterMode => Graphics.FILTER_MODE_BILINEAR, :transform => t,
        });
    }
}
