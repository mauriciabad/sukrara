// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.StringUtil;
import Toybox.WatchUi;

// Draws an AuraModel (SPEC.md §4 z-order). Every position comes from the model; this file only paints.
module AuraDraw {
    // the screen's centre for shapes (a shape at device x covers x - 0.5 .. x + 0.5: the reference's 0 is the edge between
    // pixels 194 and 195), plus the always-on shift; bitmaps (text, glyphs, icons) are placed by their pixels' corners,
    // at cx + 0.5
    var cx as Float = 194.5;
    var cy as Float = 194.5;
    var atlases as Dictionary = {};
    var used as Dictionary = {}; // the fonts the frame being drawn uses
    var icons as Graphics.BitmapReference or Graphics.BitmapType or Null = null;

    // a font: its atlas (8-bit alpha, white), glyphs, boxes and baseline (AuraFontTable, tools/fonts.py); or, for the
    // small straight texts, [the Connect IQ font, baseline]
    function atlas(key as String) as Array {
        used[key] = true;
        var a = atlases[key];
        if (a == null) {
            var t = AuraFontTable.atlas(key);
            if (t[1] instanceof Number) {
                a = [WatchUi.loadResource(t[0]), t[1]];
                atlases[key] = a;
                return a;
            }
            var j = WatchUi.loadResource(t[1]) as Array;
            var m = StringUtil.convertEncodedString(j[1], {
                :fromRepresentation => StringUtil.REPRESENTATION_STRING_BASE64,
                :toRepresentation => StringUtil.REPRESENTATION_BYTE_ARRAY,
            });
            a = [WatchUi.loadResource(t[0]), j[0], m, j[2]];
            atlases[key] = a;
        }
        return a as Array;
    }

    function iconSheet() {
        if (icons == null) {
            icons = WatchUi.loadResource(Rez.Drawables.icons);
        }
        return icons;
    }

    // drop the loaded fonts (the graphics pool may purge them; they reload on the next draw)
    function release() as Void {
        releaseFonts();
        AuraDials.release();
    }

    function releaseFonts() as Void {
        atlases = {};
        icons = null;
    }

    function argb(col as Number, alpha as Float) as Number {
        var a = (AuraMath.clamp(alpha, 0, 1) * 255 + 0.5).toNumber();
        return Graphics.createColor(a, (col >> 16) & 255, (col >> 8) & 255, col & 255);
    }

    // setFill / setStroke read ARGB (a 24-bit colour is transparent); opaque white, 0xFFFFFFFF, is -1, which is
    // COLOR_TRANSPARENT, so it is drawn one blue step under (0xFFFFFE, the same on the 16-bit screen)
    function opaque(col as Number) as Number {
        var c = 0xFF000000 | col;
        return c == -1 ? 0xFFFFFFFE : c;
    }

    function fill(dc as Dc, col as Number, alpha as Float) as Void {
        dc.setFill(alpha >= 1 ? opaque(col) : argb(col, alpha));
    }

    function stroke(dc as Dc, col as Number, alpha as Float, w as Numeric) as Void {
        dc.setStroke(alpha >= 1 ? opaque(col) : argb(col, alpha));
        dc.setPenWidth(w);
    }

    // a text with its baseline at y (screen-centred coordinates), left-aligned at x or centred on it by its advance:
    // each glyph of the atlas drawn tinted at its pen position, on whole pixels (crisp), the pen advancing by the
    // font's own fractional advances (as the reference's browser does)
    function text(dc as Dc, key as String, x as Numeric, baseline as Numeric, s as String, col as Number, just as Number) as Void {
        var a = atlas(key);
        if (a.size() == 2) {
            // a Connect IQ font: centred by its own advance, rounded to the nearest pixel (the system's centring
            // truncates); the system draws bitmap fonts 1 px under the line
            var fr = a[0];
            if (fr has :get) {
                fr = fr.get();
            }
            dc.setColor(col, Graphics.COLOR_TRANSPARENT);
            var left = cx + 0.5 + x;
            if (just == Graphics.TEXT_JUSTIFY_CENTER) {
                left -= dc.getTextWidthInPixels(s, fr) / 2.0;
            }
            dc.drawText(Math.floor(left + 0.5), cy + 0.5 + baseline - (a[1] as Number) - 1, fr, s, Graphics.TEXT_JUSTIFY_LEFT);
            return;
        }
        var chars = a[1] as String;
        var m = a[2] as ByteArray;
        var pen = cx + 0.5 + x;
        if (just == Graphics.TEXT_JUSTIFY_CENTER) {
            pen -= arcWidth(key, s, 0) / 2.0;
        }
        var top = cy + 0.5 + baseline - (a[3] as Number);
        var cs = s.toCharArray();
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
                // drawBitmap2 keeps the sub-rectangle where it sits in the atlas: move the atlas by its corner
                var gx = (m[o] << 8) | m[o + 1];
                var gy = (m[o + 2] << 8) | m[o + 3];
                dc.drawBitmap2(Math.floor(pen + xo + 0.5) - gx, Math.floor(top + yo + 0.5) - gy, a[0], {
                    :bitmapX => gx, :bitmapY => gy, :bitmapWidth => gw, :bitmapHeight => gh, :tintColor => col,
                });
            }
            pen += adv(a, k);
        }
    }

    // a line with round caps
    function line(dc as Dc, x1 as Numeric, y1 as Numeric, x2 as Numeric, y2 as Numeric, col as Number, alpha as Float, w as Numeric) as Void {
        stroke(dc, col, alpha, w);
        dc.drawLine(cx + x1, cy + y1, cx + x2, cy + y2);
        fill(dc, col, alpha);
        dc.fillCircle(cx + x1, cy + y1, w / 2.0);
        dc.fillCircle(cx + x2, cy + y2, w / 2.0);
    }

    // an arc from a1 to a2 clockwise (degrees clockwise from 12), butt caps
    function arc(dc as Dc, r as Numeric, a1 as Numeric, a2 as Numeric, col as Number, alpha as Float, w as Numeric) as Void {
        stroke(dc, col, alpha, w);
        dc.drawArc(cx, cy, r, Graphics.ARC_CLOCKWISE, 90 - a1, 90 - a2);
    }

    function poly(dc as Dc, pts as Array, col as Number, alpha as Float) as Void {
        var q = [];
        for (var i = 0; i < pts.size(); i++) {
            q.add([cx + pts[i][0], cy + pts[i][1]]);
        }
        fill(dc, col, alpha);
        dc.fillPolygon(q);
    }

    // one icon of icons.png centred at (x, y), rotated by rot degrees, tinted
    function icon(dc as Dc, name as String, size as Number, x as Numeric, y as Numeric, rot as Numeric, col as Number) as Void {
        var b = AuraFontTable.icon(name, size);
        if (b == null) {
            return;
        }
        var box = b[2] as Number;
        var t = new Graphics.AffineTransform();
        t.rotate(rot * Math.PI / 180);
        t.translate(-box / 2.0 - (b[0] as Number), -box / 2.0 - (b[1] as Number));
        dc.drawBitmap2(cx + 0.5 + x, cy + 0.5 + y, iconSheet(), {
            :bitmapX => b[0], :bitmapY => b[1], :bitmapWidth => box, :bitmapHeight => box,
            :tintColor => col, :filterMode => Graphics.FILTER_MODE_BILINEAR, :transform => t,
        });
    }

    // the advance of a glyph of an arc font, px
    function adv(a as Array, i as Number) as Float {
        var m = a[2] as ByteArray;
        var o = i * 10;
        return ((m[o + 8] << 8) | m[o + 9]) / 16.0;
    }

    function arcWidth(key as String, s as String, spacing as Numeric) as Float {
        var a = atlas(key);
        var w = 0.0;
        var cs = s.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            var k = (a[1] as String).find(cs[i].toString());
            w += (k != null ? adv(a, k) : 0.0) + spacing;
        }
        return w;
    }

    // text along a circle of radius r, starting at arc length s0 from the angle a0 (degrees); top = clockwise over the
    // top (glyph tops outward), else counter-clockwise along the bottom (tops inward); returns the arc length after it
    function arcText(dc as Dc, key as String, s as String, r as Numeric, a0 as Numeric, s0 as Numeric, top as Boolean, spacing as Numeric, col as Number) as Float {
        var a = atlas(key);
        var chars = a[1] as String;
        var m = a[2] as ByteArray;
        var base = a[3] as Number;
        var pos = s0.toFloat();
        var cs = s.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            var k = chars.find(cs[i].toString());
            if (k == null) {
                pos += spacing;
                continue;
            }
            var w = adv(a, k);
            var o = k * 10;
            var gw = m[o + 4];
            var gh = m[o + 5];
            if (gw > 1 || gh > 1) {
                var gx = (m[o] << 8) | m[o + 1];
                var gy = (m[o + 2] << 8) | m[o + 3];
                var xo = m[o + 6] > 127 ? m[o + 6] - 256 : m[o + 6];
                var yo = m[o + 7] > 127 ? m[o + 7] - 256 : m[o + 7];
                var mid = pos + w / 2;
                var ang = top ? a0 + mid / r * 180 / Math.PI : a0 - mid / r * 180 / Math.PI;
                var t = ang * Math.PI / 180;
                var px = r * Math.sin(t);
                var py = -r * Math.cos(t);
                var phi = top ? t : t + Math.PI;
                // the glyph's pen origin: half its advance back along the tangent
                var ox = px - w / 2 * Math.cos(phi);
                var oy = py - w / 2 * Math.sin(phi);
                var tr = new Graphics.AffineTransform();
                tr.rotate(phi);
                tr.translate((xo - gx).toFloat(), (yo - base - gy).toFloat());
                dc.drawBitmap2(cx + 0.5 + ox, cy + 0.5 + oy, a[0], {
                    :bitmapX => gx, :bitmapY => gy, :bitmapWidth => gw, :bitmapHeight => gh,
                    :tintColor => col, :filterMode => Graphics.FILTER_MODE_BILINEAR, :transform => tr,
                });
            }
            pos += w + spacing;
        }
        return pos;
    }

    // ---------------------------------------------------------------------------------------------------------------

    function draw(dc as Dc, m as AuraModel, shiftX as Numeric, shiftY as Numeric) as Void {
        cx = 194.5 + shiftX;
        cy = 194.5 + shiftY;
        dc.setAntiAlias(true);
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        // the dial (cached), cut by the halo, then the sweep (not cut), then the content (SPEC.md §4 z-order)
        if (AuraDials.draw(dc, m)) {
            new AuraHalo(m).paint(dc, m);
        }
        AuraDials.sweep(dc, m);
        date(dc, m);
        topField(dc, m);
        time(dc, m);
        batteryRail(dc, m);
        levelRail(dc, m);
        value(dc, m);
        sparkline(dc, m);
        chipRow(dc, m);
        message(dc, m);
        bottomFields(dc, m);
        stroke(dc, 0x121214, 1.0, 1);
        dc.drawCircle(cx, cy, 194.5);
        // keep only the fonts this frame used (the sizes change with the rows: the others would pile up in the heap)
        var ks = atlases.keys();
        for (var i = 0; i < ks.size(); i++) {
            if (used[ks[i]] == null) {
                atlases.remove(ks[i]);
            }
        }
        used = {};
    }

    function date(dc as Dc, m as AuraModel) as Void {
        var fill1 = m.aod ? m.c("dim") : m.c("soft");
        var fill2 = m.aod ? m.c("soft") : m.c("ink");
        var w = arcWidth("d26", m.dateWd, 2) + arcWidth("d26b", m.dateDay, 2) + arcWidth("d26", m.dateMo, 2);
        var L = 160 * 120 * Math.PI / 180;
        var s = L / 2 - w / 2;
        s = arcText(dc, "d26", m.dateWd, 160, 300, s, true, 2, fill1);
        s = arcText(dc, "d26b", m.dateDay, 160, 300, s, true, 2, fill2);
        arcText(dc, "d26", m.dateMo, 160, 300, s, true, 2, fill1);
    }

    function topField(dc as Dc, m as AuraModel) as Void {
        var key = m.TF == 26 ? "l26m" : m.TF == 25 ? "l25m" : "l24m";
        for (var i = 0; i < m.topRuns.size(); i++) {
            var rn = m.topRuns[i] as Array;
            var x = m.topX[i] as Float;
            var w = rn[rn.size() - 1] as Float;
            if (rn[0].equals("i")) {
                icon(dc, rn[1] as String, m.tfIcon, x + w / 2, m.tfBase - m.TF * 0.334, 0, rn[2] as Number);
            } else if (rn[0].equals("s")) {
                text(dc, key, AuraMath.r1(x), m.tfBase, rn[1] as String, rn[2] as Number, Graphics.TEXT_JUSTIFY_LEFT);
            }
        }
    }

    function time(dc as Dc, m as AuraModel) as Void {
        var key = "t" + m.T.toString() + (m.aod ? "o" : "");
        var col = m.aod ? m.c("grey") : m.c("ink");
        var t0 = -m.tW / 2;
        var cs = m.tStr.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            text(dc, key, AuraMath.r1(t0 + m.txs[i]), AuraMath.r1(m.tBase), cs[i].toString(), col, Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

    function batteryRail(dc as Dc, m as AuraModel) as Void {
        if (m.aod) {
            return;
        }
        for (var i = 0; i < 10; i++) {
            var a1 = 227 + i * 8.78;
            arc(dc, 182, a1, a1 + 6.98, i < m.battLit ? m.battCol : m.c("grid"), 1.0, 6);
        }
        var key = "l" + m.battFs.toString() + (m.battBold ? "b" : "s");
        text(dc, key, -166, m.by1, m.battL1, m.battCol, Graphics.TEXT_JUSTIFY_LEFT);
        if (!m.battL2.equals("")) {
            text(dc, key, -166, AuraMath.r1(m.by1 + m.battFs * 0.668 + 9), m.battL2, m.battCol, Graphics.TEXT_JUSTIFY_LEFT);
        }
    }

    function tick(dc as Dc, v as Numeric, r1 as Numeric, r2 as Numeric, col as Number, w as Numeric) as Void {
        var a = AuraMath.ang(v);
        var p1 = AuraMath.P(r1, a);
        var p2 = AuraMath.P(r2, a);
        stroke(dc, col, 1.0, w);
        dc.drawLine(cx + p1[0], cy + p1[1], cx + p2[0], cy + p2[1]);
    }

    function levelRail(dc as Dc, m as AuraModel) as Void {
        if (!m.railOn) {
            return;
        }
        var l = m.inp.limits;
        arc(dc, 182, 47, 133, m.c("faint"), 1.0, 1.5);
        var bw = m.aod ? 2 : 6;
        arc(dc, 182, AuraMath.ang(l[3]), AuraMath.ang(l[1]), m.c("band"), 1.0, bw);
        arc(dc, 182, AuraMath.ang(l[2]), AuraMath.ang(l[1]), m.c("band2"), 1.0, bw);
        if (m.zoneOn && m.zone != null) {
            arc(dc, 182, m.zone[0], m.zone[1], m.c(m.ck), 0.85, 3);
        }
        if (!m.aod) {
            var minor = [50, 60, 80, 90, 100, 110, 120, 130, 150, 160, 170, 200, 220, 240, 280, 350];
            for (var i = 0; i < minor.size(); i++) {
                tick(dc, minor[i], 177, 187, m.c("faint"), 1.4);
            }
        }
        var mid = [40, l[2], l[5], 400];
        for (var i = 0; i < mid.size(); i++) {
            tick(dc, mid[i], 175, 189, m.c("dim"), 1.6);
        }
        var major = [l[0], l[1], l[3], l[4]];
        for (var i = 0; i < major.size(); i++) {
            tick(dc, major[i], 171, 192, m.c("soft"), 2);
        }
        if (!m.beadOn) {
            return;
        }
        var col = m.struck ? m.c("grey") : m.beadCol;
        for (var i = 0; i < m.trail.size(); i++) {
            var t = m.trail[i] as Array;
            var p = AuraMath.P(182, t[0]);
            fill(dc, col, t[2] as Float);
            dc.fillCircle(cx + p[0], cy + p[1], t[1]);
        }
        var b = AuraMath.P(182, m.beadDrawA);
        if (!m.struck && m.awake) {
            fill(dc, col, 0.2);
            dc.fillCircle(cx + b[0], cy + b[1], 13);
        }
        var r = m.aod ? 6 : 7;
        if (m.struck) {
            fill(dc, 0x000000, 1.0);
            dc.fillCircle(cx + b[0], cy + b[1], r);
            stroke(dc, m.c("grey"), 1.0, 2.4);
            dc.drawCircle(cx + b[0], cy + b[1], r);
        } else {
            fill(dc, m.beadCol, 1.0);
            dc.fillCircle(cx + b[0], cy + b[1], r);
        }
        if (m.lo || m.hi) {
            var t = AuraMath.P(182, m.lo ? 139.5 : 40.5);
            var b1 = AuraMath.P(176.5, m.lo ? 135.6 : 44.4);
            var b2 = AuraMath.P(187.5, m.lo ? 135.6 : 44.4);
            poly(dc, [t, b1, b2], col, 1.0);
        }
    }

    function value(dc as Dc, m as AuraModel) as Void {
        if (!m.valueOn) {
            return;
        }
        if (m.inverted) {
            fill(dc, m.c("veryLow"), 1.0);
            dc.fillRoundedRectangle(cx + m.blk[0], cy + m.blk[1], m.blk[2], m.blk[3], 12);
        }
        var key = "v" + m.V.toString();
        var cs = m.vStr.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            text(dc, key, AuraMath.r1(m.x0 + m.vxs[i]), AuraMath.r1(m.vBase), cs[i].toString(), m.vFill, Graphics.TEXT_JUSTIFY_CENTER);
        }
        if (m.struck) {
            line(dc, m.strike[0], m.strike[2], m.strike[1], m.strike[2], m.vFill, 1.0, m.strike[3]);
        }
        if (m.arrowD) {
            for (var i = 0; i < m.arrowPts.size(); i++) {
                poly(dc, m.arrowPts[i], m.vFill, 1.0);
            }
        }
        if (m.ringD) {
            stroke(dc, m.vFill, 1.0, m.ringW);
            dc.drawCircle(cx + m.ax, cy + m.ay, m.ringR);
        }
        if (m.revealD && m.reveal[1] > m.reveal[0]) {
            line(dc, m.reveal[0], m.reveal[2], m.reveal[1], m.reveal[2], m.c("now"), 1.0, 3);
        }
    }

    // a dashed / dotted straight line: dash and gap along it, round caps
    function dashed(dc as Dc, x1 as Numeric, y1 as Numeric, x2 as Numeric, y2 as Numeric, dash as Numeric, gap as Numeric, col as Number, w as Numeric) as Void {
        var dx = x2 - x1;
        var dy = y2 - y1;
        var len = Math.sqrt(dx * dx + dy * dy);
        if (len <= 0) {
            return;
        }
        var ux = dx / len;
        var uy = dy / len;
        var s = 0.0;
        while (s < len) {
            var e = AuraMath.min(len, s + dash);
            line(dc, x1 + ux * s, y1 + uy * s, x1 + ux * e, y1 + uy * e, col, 1.0, w);
            s += dash + gap;
        }
    }

    function sparkline(dc as Dc, m as AuraModel) as Void {
        if (!m.valueOn || !m.spark) {
            return;
        }
        var lim = m.aod ? 0x3A3A3E : 0x5C5C62;
        for (var i = 0; i < 2; i++) {
            dashed(dc, m.sx, m.spLimits[i], m.sx + 100, m.spLimits[i], 0.01, 6, lim, 1.4);
        }
        for (var r = 0; r < m.spPts.size(); r++) {
            var run = m.spPts[r] as Array;
            for (var i = 0; i < run.size(); i++) {
                if (i > 0) {
                    line(dc, run[i - 1][0], run[i - 1][1], run[i][0], run[i][1], m.spLineCol, 1.0, 5.4);
                } else {
                    fill(dc, m.spLineCol, 1.0);
                    dc.fillCircle(cx + run[i][0], cy + run[i][1], 2.7);
                }
            }
        }
        if (m.trendOn) {
            dashed(dc, m.spEnd[0], m.spEnd[1], m.spRay[0], m.spRay[1], 4.5, 4.5, m.spRayCol, 3.4);
        }
        if (m.spEndFill != null) {
            fill(dc, m.spEndFill, 1.0);
            dc.fillCircle(cx + m.spEnd[0], cy + m.spEnd[1], 7.5);
        } else {
            fill(dc, 0x000000, 1.0);
            dc.fillCircle(cx + m.spEnd[0], cy + m.spEnd[1], 7.5);
            stroke(dc, m.spEndStroke, 1.0, 3);
            dc.drawCircle(cx + m.spEnd[0], cy + m.spEnd[1], 7.5);
        }
    }

    function chipRow(dc as Dc, m as AuraModel) as Void {
        if (m.timeOn) {
            text(dc, "l24m", AuraMath.r1(m.rx0), AuraMath.r1(m.chipC + 8), m.readTxt, m.c("soft"), Graphics.TEXT_JUSTIFY_LEFT);
        }
        if (!m.pillOn) {
            return;
        }
        var px = AuraMath.r1(m.pillX);
        fill(dc, m.pillFill, m.pillTonal ? 0.24 : 1.0);
        dc.fillRoundedRectangle(cx + px, cy + AuraMath.r1(m.chipC - 19), AuraMath.r1(m.pillW), 38, 19);
        var ic = m.pill[3] as String;
        icon(dc, ic.equals("paused") || ic.equals("off") ? "bell_" + ic : ic, 24, px + 27, m.chipC, 0, m.pillFg);
        text(dc, "l24s", AuraMath.r1(px + 48.3), AuraMath.r1(m.chipC + 8), m.pill[1] as String, m.pillFg, Graphics.TEXT_JUSTIFY_LEFT);
    }

    function message(dc as Dc, m as AuraModel) as Void {
        var k = m.msgKind;
        if (k == null) {
            return;
        }
        if (k.equals("warm")) {
            var dx = AuraMath.r1(m.msgX + 17);
            var dy = AuraMath.r1(m.vTop + 17);
            stroke(dc, m.c("soft"), 1.0, 2.5);
            dc.drawCircle(cx + dx, cy + dy, 17);
            // the pie, clockwise from 12 by the warm-up done
            var pts = [[dx, dy]];
            var n = (m.warmFrac * 48).toNumber() + 1;
            for (var i = 0; i <= n; i++) {
                var a = m.warmFrac * 2 * Math.PI * i / n;
                pts.add([dx + 12.5 * Math.sin(a), dy - 12.5 * Math.cos(a)]);
            }
            poly(dc, pts, m.c("soft"), 1.0);
            text(dc, "l32s", AuraMath.r1(m.msgX + 46), AuraMath.r1(dy + 32 * 0.668 / 2), m.msgT1, m.c("ink"), Graphics.TEXT_JUSTIFY_LEFT);
            text(dc, "l24m", 0, AuraMath.r1(m.chipC + 8), m.msgT2, m.c("soft"), Graphics.TEXT_JUSTIFY_CENTER);
        } else if (k.equals("ended")) {
            var mx = m.msgX;
            var fy = AuraMath.r1(m.vTop + 1);
            var ink = m.c("ink");
            fill(dc, ink, 1.0);
            var cells = [[0, 0], [2, 0], [1, 1]];
            for (var i = 0; i < 3; i++) {
                dc.fillRectangle(cx + AuraMath.r1(mx + 3 + cells[i][0] * 7.5), cy + fy + cells[i][1] * 7.5, 7.5, 7.5);
            }
            line(dc, AuraMath.r1(mx + 2), fy - 2, AuraMath.r1(mx + 2), fy + 30, ink, 1.0, 2.4);
            line(dc, AuraMath.r1(mx + 3), fy, AuraMath.r1(mx + 25.5), fy, ink, 1.0, 2.4);
            line(dc, AuraMath.r1(mx + 25.5), fy, AuraMath.r1(mx + 25.5), fy + 15, ink, 1.0, 2.4);
            line(dc, AuraMath.r1(mx + 25.5), fy + 15, AuraMath.r1(mx + 3), fy + 15, ink, 1.0, 2.4);
            text(dc, "l32s", AuraMath.r1(mx + 38), AuraMath.r1(m.vTop + 16 + 32 * 0.668 / 2), m.msgT1, ink, Graphics.TEXT_JUSTIFY_LEFT);
        } else if (k.equals("none")) {
            text(dc, "l30s", 0, AuraMath.r1(m.vTop + 20), m.msgT1, m.c("soft"), Graphics.TEXT_JUSTIFY_CENTER);
        } else if (k.equals("gap")) {
            text(dc, "l25s", 0, AuraMath.r1(m.vTop + 16.7), m.msgT1, m.c("soft"), Graphics.TEXT_JUSTIFY_CENTER);
            text(dc, m.cap2 == 40 ? "g40" : "g36", 0, AuraMath.r1(m.vTop + 16.7 + 12 + m.cap2), m.msgT2, m.c("ink"), Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

    function bottomFields(dc as Dc, m as AuraModel) as Void {
        for (var i = 0; i < m.bottomRuns.size(); i++) {
            var rn = m.bottomRuns[i] as Array;
            var s = m.bottomOff[i] as Float;
            var w = rn[rn.size() - 1] as Float;
            if (rn[0].equals("i")) {
                var an = 232 - (s + w / 2) / 172 * 180 / Math.PI;
                var p = AuraMath.P(164, an);
                icon(dc, rn[1] as String, 20, p[0], p[1], an - 180, rn[2] as Number);
            } else if (rn[0].equals("s")) {
                arcText(dc, (rn[3] as Number) >= 600 ? "l24s" : "l24m", rn[1] as String, 172, 232, AuraMath.r1(s), false, 0, rn[2] as Number);
            }
        }
    }
}
