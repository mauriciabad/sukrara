// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;

// THE HALO (SPEC.md §6.13): where the dial stops short of every text, so no dial line reads as a strike-through (which
// means "old"). Its numbers are the reference's (aura.js "the halo", expect.json "halo"); paint() draws it in black over
// the dial, before the sweep and the content (only the dial is cut).
//
// Outline: the time and the value are cut by their own glyphs grown 6.5 px (the reference's 13 px round stroke; the
// halo atlases t*h / v*h of tools/fonts.py), the arrow by its polygons grown 6.5 px; the smaller texts (date, top field,
// bottom fields, battery label) by their line box grown 5.5 px, along the arc for the date and the bottom fields — at
// 24 px with 5.5 px around each glyph the grown glyphs already merge into that box.
class AuraHalo {
    var mode as String;
    var t as Array; // the time's box: x, y, w, h
    var v as Array; // the value's box, or the message's
    var c as Array; // the chip row's
    var f as Array; // the top field's
    var s as Array; // the sparkline's
    var mb as Array; // the message's

    function initialize(m as AuraModel) {
        mode = m.inp.halo;
        var t0 = -m.tW / 2;
        t = [AuraMath.r1(t0 - 10), AuraMath.r1(m.tBase - m.T - 10), AuraMath.r1(m.tW + 20), m.T + 20];
        mb = msgBox(m);
        if (m.valueOn) {
            v = [AuraMath.r1(m.x0 - 12), AuraMath.r1(m.vBase - m.V - 12), AuraMath.r1(m.total + 24), m.V + 24];
        } else if (m.msgKind != null) {
            v = [AuraMath.r1(mb[0]), AuraMath.r1(mb[1]), AuraMath.r1(mb[2]), AuraMath.r1(mb[3])];
        } else {
            v = [0, 0, 0, 0];
        }
        var chip = m.timeOn || m.pillOn;
        var gapA = m.timeOn && m.pillOn ? 14 : 0;
        if (m.upd != null) {
            // the update line's two lines: one box from the first's ascender to the second's descender
            var w = AuraMath.max(AuraMath.tw(m.updT1, 24), AuraMath.tw(m.updT2, 24));
            c = [AuraMath.r1(-w / 2 - 8), AuraMath.r1(m.chipC - 27), AuraMath.r1(w + 16), 54];
        } else {
            c = [AuraMath.r1(m.rx0 - 8), AuraMath.r1(m.chipC - 23), chip ? AuraMath.r1(m.ageW + gapA + m.pillW + 16) : 0, chip ? 46 : 0];
        }
        f = [AuraMath.r1(-m.topWidth() / 2 - 8), AuraMath.r1(m.tfBase - m.TF * 0.668 - 8), m.topOn ? AuraMath.r1(m.topW + 16) : 0,
            m.topOn ? AuraMath.r1(m.TF * 0.668 + 16) : 0];
        // the sparkline's box grown 8 px, not its fade bands: what runs into them is cut by its own shape (sparkCut)
        s = [AuraMath.r1(m.sx - 8), AuraMath.r1(m.sy - 8), m.valueOn && m.spark ? AuraMath.r1(m.SW + 16) : 0, AuraMath.r1(m.SH + 16)];
        mb = [AuraMath.r1(mb[0]), AuraMath.r1(mb[1]), m.msgKind != null ? AuraMath.r1(mb[2]) : 0, AuraMath.r1(mb[3])];
    }

    // the sparkline's line, ray and end dot grown 6.5 px (they run into the fade bands outside the box's cut)
    function sparkCut(dc as Dc, m as AuraModel) as Void {
        if (!m.valueOn || !m.spark) {
            return;
        }
        var ox = AuraDraw.cx;
        var oy = AuraDraw.cy;
        dc.setStroke(0xFF000000);
        dc.setPenWidth(18);
        for (var r = 0; r < m.spPts.size(); r++) {
            var run = m.spPts[r] as Array;
            for (var i = 0; i < run.size(); i++) {
                var a = run[i] as Array;
                dc.fillCircle(ox + a[0], oy + a[1], 9.2);
                if (i > 0) {
                    var b = run[i - 1] as Array;
                    dc.drawLine(ox + b[0], oy + b[1], ox + a[0], oy + a[1]);
                }
            }
        }
        if (m.trendOn) {
            dc.setPenWidth(16);
            dc.drawLine(ox + m.spEnd[0], oy + m.spEnd[1], ox + m.spRay[0], oy + m.spRay[1]);
            dc.fillCircle(ox + m.spRay[0], oy + m.spRay[1], 8.2);
        }
        dc.fillCircle(ox + m.spEnd[0], oy + m.spEnd[1], 14);
    }

    // a message's box (aura.js msgBox)
    function msgBox(m as AuraModel) as Array {
        var k = m.msgKind;
        if (k == null) {
            return [0, 0, 0, 0];
        }
        if (k.equals("warm")) {
            return [m.msgX - 10, m.vTop - 6, -2 * m.msgX + 20, m.chipC + 14 - m.vTop];
        } else if (k.equals("ended")) {
            return [m.msgX - 10, m.vTop - 6, -2 * m.msgX + 20, 44];
        } else if (k.equals("none")) {
            var w = AuraMath.tw(m.msgT1, 30);
            return [-w / 2 - 10, m.vTop - 8, w + 20, 36];
        }
        return [-120, m.vTop - 8, 240, 16.7 + 12 + m.cap2 + 16];
    }

    // ------------------------------------------------------------------------------------------------------ painting

    // a side label's line box grown 5.5 px (w its width, x0 how far it starts inwards of x -166 / 166)
    hidden function label(dc as Dc, side as Number, y as Float, fs as Number, w as Float, x0 as Number) as Void {
        rect(dc, [side < 0 ? -171.5 + x0 : 171.5 - x0 - w - 11, y - fs * 0.73 - 5.5, w + 11, fs * 0.93 + 11], 5.5);
    }

    function rect(dc as Dc, b as Array, r as Numeric) as Void {
        if ((b[2] as Numeric) > 0 && (b[3] as Numeric) > 0) {
            dc.fillRoundedRectangle(AuraDraw.cx + b[0], AuraDraw.cy + b[1], b[2], b[3], r);
        }
    }

    // black over the dial (called after the dial, before the sweep and the content)
    function paint(dc as Dc, m as AuraModel) as Void {
        if (mode.equals("none")) {
            return;
        }
        dc.setAntiAlias(true);
        dc.setFill(0xFF000000);
        if (mode.equals("box")) {
            rect(dc, t, 16);
            rect(dc, v, 16);
            rect(dc, c, 22);
            rect(dc, f, 12);
            rect(dc, s, 8);
            sparkCut(dc, m);
            return;
        }
        var black = 0x000000;
        // the time and the value: their glyphs grown 6.5 px
        var tk = "t" + m.T.toString() + "h";
        var t0 = -m.tW / 2;
        var cs = m.tStr.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            AuraDraw.text(dc, tk, AuraMath.r1(t0 + m.txs[i]), AuraMath.r1(m.tBase), cs[i].toString(), black, Graphics.TEXT_JUSTIFY_CENTER);
        }
        if (m.valueOn) {
            var vk = "v" + m.V.toString() + "h";
            cs = m.vStr.toCharArray();
            for (var i = 0; i < cs.size(); i++) {
                AuraDraw.text(dc, vk, AuraMath.r1(m.x0 + m.vxs[i]), AuraMath.r1(m.vBase), cs[i].toString(), black, Graphics.TEXT_JUSTIFY_CENTER);
            }
        }
        if (m.arrowD) {
            // the arrow's polygons grown 6.5 px: filled, every edge a 13 px line with round ends
            dc.setFill(0xFF000000);
            dc.setStroke(0xFF000000);
            dc.setPenWidth(13);
            for (var i = 0; i < m.arrowPts.size(); i++) {
                var p = m.arrowPts[i] as Array;
                var q = [];
                for (var j = 0; j < p.size(); j++) {
                    var a = p[j] as Array;
                    var b = p[(j + 1) % p.size()] as Array;
                    q.add([AuraDraw.cx + a[0], AuraDraw.cy + a[1]]);
                    dc.drawLine(AuraDraw.cx + a[0], AuraDraw.cy + a[1], AuraDraw.cx + b[0], AuraDraw.cy + b[1]);
                    dc.fillCircle(AuraDraw.cx + a[0], AuraDraw.cy + a[1], 6.5);
                }
                dc.fillPolygon(q);
            }
        }
        dc.setFill(0xFF000000);
        rect(dc, s, 8);
        sparkCut(dc, m);
        if (m.valueOn && m.ringD) {
            // the no-trend ring: a circle 6.5 px past its stroke
            dc.fillCircle(AuraDraw.cx + m.ax, AuraDraw.cy + m.ay, 0.295 * m.box + 6.5);
        }
        rect(dc, c, 22);
        rect(dc, mb, 14);
        // the smaller texts: their line (Atkinson's ascender 0.73 em above the baseline, descender 0.2 em under it)
        // grown 5.5 px
        if (m.topOn && m.topRuns.size() > 0) {
            var up = AuraMath.max(m.TF * 0.73, m.tfIcon / 2.0 + m.TF * 0.334);
            rect(dc, [-m.topW / 2 - 5.5, m.tfBase - up - 5.5, m.topW + 11, up + m.TF * 0.2 + 11], 5.5);
        }
        // the side labels, each line with its mark: from x -166 on the left, to x 166 on the right
        var ks = [m.sideL, m.sideR];
        for (var i = 0; i < 2; i++) {
            var k = ks[i] as String;
            var side = i == 0 ? -1 : 1;
            if (k.equals("battery")) {
                var bf = m.battFs;
                label(dc, side, m.by1, bf, AuraMath.tw(m.battL1, bf) + m.mkW, 0);
            } else if (k.equals("sensor") && m.sensOn && !m.sensT.equals("")) {
                label(dc, side, m.sy1, m.sensFs, AuraMath.tw(m.sensT, m.sensFs) + m.mkW, 0);
            }
        }
        // the date along its arc (r 160, over the top, glyph tops outward: capitals, accents above)
        var da = (AuraDraw.arcWidth("d26", m.dateWd, 2) + AuraDraw.arcWidth("d26b", m.dateDay, 2) +
            AuraDraw.arcWidth("d26", m.dateMo, 2)) / 2 + 5.5;
        da = da / 160 * 180 / Math.PI;
        band(dc, 160 + 9.5, 19 + 11, -da, da);
        // the bottom fields along theirs (r 172, counter-clockwise from 232°, tops inward); icons centred on r 164
        for (var i = 0; i < m.bottomRuns.size(); i++) {
            var rn = m.bottomRuns[i] as Array;
            var s0 = m.bottomOff[i] as Float;
            var w = rn[rn.size() - 1] as Float;
            var a1 = 232 - (s0 + w + 5.5) / 172 * 180 / Math.PI;
            var a2 = 232 - (s0 - 5.5) / 172 * 180 / Math.PI;
            if (rn[0].equals("i")) {
                band(dc, 164, 29.2, a1, a2);
            } else if (rn[0].equals("s")) {
                band(dc, 172 - 24 * 0.265, 24 * 0.93 + 11, a1, a2);
            }
        }
    }

    // a band along a circle: centre radius r, width w, from a1 to a2 (degrees clockwise from 12)
    function band(dc as Dc, r as Numeric, w as Numeric, a1 as Numeric, a2 as Numeric) as Void {
        dc.setStroke(0xFF000000);
        dc.setPenWidth(w);
        dc.drawArc(AuraDraw.cx, AuraDraw.cy, r, Graphics.ARC_CLOCKWISE, 90 - a1, 90 - a2);
    }
}
