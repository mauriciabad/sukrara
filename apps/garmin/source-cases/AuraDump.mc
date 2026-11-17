// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;

// Prints an AuraModel as the values aura.js layout() returns (contracts/watchface/aura/expect.json, the same names and
// the same SVG path shapes), one "D|<case>|<path>|<value>" line each; apps/garmin/tools/garmin_test.py compares them
// with expect.json (SPEC.md §10: positions ±0.5, angles ±0.1, opacities ±0.01, colours and strings exact). Debug only.
(:debug)
module AuraDump {
    var cid as String = "";

    function n(x) as String {
        if (x == null) {
            return "null";
        }
        if (x instanceof Boolean) {
            return x ? "true" : "false";
        }
        if (x instanceof Float || x instanceof Double) {
            return x.format("%.4f");
        }
        return x.toString();
    }

    function col(c as Number?) as String {
        return c == null ? "none" : "#" + c.format("%06X");
    }

    function disp(b as Boolean) as String {
        return b ? "inline" : "none";
    }

    function out(path as String, v) as Void {
        System.println("D|" + cid + "|" + path + "|" + (v instanceof String ? v : n(v)));
    }

    function r1(x) as String {
        return n(AuraMath.r1(x));
    }

    function pt(p as Array) as String {
        return n(p[0]) + " " + n(p[1]);
    }

    // an SVG arc like aura.js arc()
    function arc(r as Numeric, a1 as Numeric, a2 as Numeric, ccw as Boolean) as String {
        var p1 = AuraMath.P(r, a1);
        var p2 = AuraMath.P(r, a2);
        var d = (ccw ? a1 - a2 : a2 - a1).toFloat();
        while (d < 0) {
            d += 360;
        }
        while (d >= 360) {
            d -= 360;
        }
        return "M" + pt(p1) + "A" + r + " " + r + " 0 " + (d > 180 ? 1 : 0) + " " + (ccw ? 0 : 1) + " " + pt(p2);
    }

    function tick(v as Numeric, a as Numeric, b as Numeric) as String {
        var ang = AuraMath.ang(v);
        return "M" + pt(AuraMath.P(a, ang)) + "L" + pt(AuraMath.P(b, ang));
    }

    const STROKED = ["sun", "sunset", "bell", "ring", "globe", "stairs", "phoneOff", "long"];

    function icon(path as String, key as String, x as Numeric, y as Numeric, rot as Numeric, size as Numeric, c as Number) as Void {
        var k = Math.floor(size / 24.0 * 1000 + 0.5) / 1000.0;
        var s = STROKED.indexOf(key) >= 0;
        out(path + ".key", key);
        out(path + ".tf", "translate(" + r1(x) + " " + r1(y) + ") rotate(" + r1(rot) + ") scale(" + n(k) + ") translate(-12 -12)");
        out(path + ".fill", s ? "none" : col(c));
        out(path + ".stroke", s ? col(c) : "none");
        out(path + ".sw", key.equals("long") ? 2.8 : 2);
        out(path + ".disp", "inline");
    }

    // a segmented rail's lit and unlit arcs as aura.js draws them (SPEC.md §6.9): [on, off], each a path or its summary
    function segs(m as AuraModel, n as Number, side as Number, sensor as Boolean) as Array {
        var step = 87.8 / n;
        var len = step - 1.8;
        var on = "";
        var off = "";
        var non = 0;
        var noff = 0;
        for (var i = 0; i < n; i++) {
            var a1 = side < 0 ? 227 + step * i : 133 - step * i - len;
            var f = sensor ? m.sensLit(i) : (i < m.battLit ? 1.0 : 0.0);
            if (f >= 1) {
                on += arc(182, a1, a1 + len, false);
                non++;
                continue;
            }
            off += arc(182, a1, a1 + len, false);
            noff++;
            if (f > 0) {
                on += side < 0 ? arc(182, a1, a1 + len * f, false) : arc(182, a1 + len * (1 - f), a1 + len, false);
                non++;
            }
        }
        return [long(on, non), long(off, noff)];
    }

    // a path, or "M0 0" when empty (the comparison counts a summarised path's subpaths)
    function long(d as String, k as Number) as String {
        return k == 0 ? "M0 0" : d;
    }

    // a side label's marks (left and right), or none
    function marks(m as AuraModel, p as String, key as String, y as Float, fs as Number, c as Number) as Void {
        if (!m.marks) {
            out(p + ".mL.disp", "none");
            out(p + ".mR.disp", "none");
            return;
        }
        icon(p + ".mL", key, -156, y - fs * 0.334, 0, 20, c);
        icon(p + ".mR", key, 156, y - fs * 0.334, 0, 20, c);
    }

    // a long path as the reference's summary ("N subpaths"), the lasers' as the path
    function paths(n as Number) as String {
        return n == 0 ? "M0 0" : n + " subpaths";
    }

    function stroke(kind as String, c as Number) as String {
        return kind.equals("grad") ? "url" : kind.equals("col") ? col(c) : "none";
    }

    // the lasers' path: the bright ones (d1, d3) or the others (d2)
    function laserPath(d as AuraDial, bright as Boolean) as String {
        var p = "";
        for (var i = 0; i < d.lasers.size(); i++) {
            var l = d.lasers[i] as Array;
            if ((l[2] as Boolean) == bright) {
                p += "M-200 " + (l[0] as Float).format("%.1f") + "L200 " + (l[1] as Float).format("%.1f");
            }
        }
        return p.equals("") ? "M0 0" : p;
    }

    // the dial (aura.js "the dial"; AuraDial)
    function dial(m as AuraModel) as Void {
        var d = new AuraDial(m);
        var lasers = d.kind.equals("lasers") && m.awake;
        out("dial.d1", lasers ? laserPath(d, true) : paths(d.n1));
        out("dial.f1", d.f1 == null ? "none" : col(d.f1));
        out("dial.s1", stroke(d.s1, d.stroke1));
        out("dial.w1", d.w1);
        out("dial.o1", d.o1);
        out("dial.d1D", disp(d.d1D));
        out("dial.d2", lasers ? laserPath(d, false) : paths(d.n2));
        out("dial.s2", stroke(d.s2, d.stroke1));
        out("dial.w2", d.w2);
        out("dial.o2", d.o2);
        out("dial.d2D", disp(d.d2D));
        out("dial.d3", lasers ? laserPath(d, true) : "M0 0");
        out("dial.s3", stroke(d.s3, 0));
        out("dial.w3", d.w3);
        out("dial.o3", d.o3);
        out("dial.d3D", disp(d.d3D));
        out("dial.wy1", d.wy1);
        out("dial.wy2", d.wy2);
        out("dial.dash", d.dashed ? "2 6" : "none");
        out("dial.glowD", disp(d.glowD));
        out("dial.gx", d.gx);
        out("dial.gy", d.gy);
        out("dial.gr", d.gr);
        out("dial.col", col(d.col));
        out("dial.go", d.go);
        out("dial.go2", d.go2);
        out("dial.go3", d.go3);
        out("dial.go4", d.go4);
    }

    // the halo (aura.js "the halo"; AuraHalo)
    function halo(m as AuraModel) as Void {
        var h = new AuraHalo(m);
        out("halo.boxD", disp(h.mode.equals("box")));
        out("halo.outD", disp(h.mode.equals("outline")));
        var names = ["t", "v", "c", "f", "s", "m"];
        var boxes = [h.t, h.v, h.c, h.f, h.s, h.mb];
        for (var i = 0; i < 6; i++) {
            var b = boxes[i] as Array;
            out("halo." + names[i] + "x", b[0]);
            out("halo." + names[i] + "y", b[1]);
            out("halo." + names[i] + "w", b[2]);
            out("halo." + names[i] + "h", b[3]);
        }
        out("halo.spD", disp(m.valueOn && m.spark));
        out("halo.ringD", disp(m.valueOn && m.ringD));
        out("halo.ringR", AuraMath.r1(0.295 * m.box + 6.5));
    }

    function dump(id as String, m as AuraModel) as Void {
        cid = id;
        var inp = m.inp;
        var NB = AuraMath.NB;
        // layout
        out("layout.T", m.T);
        out("layout.V", m.V);
        out("layout.TF", m.TF);
        out("layout.roomy", m.roomy);
        out("layout.chipKind", m.chipKind);
        out("layout.msgKind", m.msgKind);
        out("layout.tfBase", AuraMath.r1(m.tfBase));
        out("layout.tTop", AuraMath.r1(m.tTop));
        out("layout.tBase", AuraMath.r1(m.tBase));
        out("layout.vTop", AuraMath.r1(m.vTop));
        out("layout.vBase", AuraMath.r1(m.vBase));
        out("layout.chipC", AuraMath.r1(m.chipC));
        // the dial's inputs
        out("info.ext", m.ext);
        out("info.toward", m.toward);
        out("info.I", m.I);
        out("info.spd", m.spd);
        out("info.levelY", m.levelY);
        out("info.idealY", m.idealY);
        out("info.colour", col(m.dialCol));
        out("radar.disp", disp(m.sweepOn));
        out("radar.col", col(m.sweepCol));
        out("radar.deg", m.sweepDeg);
        dial(m);
        halo(m);
        // the palette
        var ks = m.pal.keys();
        for (var i = 0; i < ks.size(); i++) {
            out("pal." + ks[i], col(m.pal[ks[i]] as Number));
        }
        // date
        out("date.wd", m.dateWd);
        out("date.day", m.dateDay);
        out("date.mo", m.dateMo);
        out("date.fill", col(m.aod ? m.c("dim") : m.c("soft")));
        out("date.dayFill", col(m.aod ? m.c("soft") : m.c("ink")));
        // top field
        out("tf.y", AuraMath.r1(m.tfBase));
        out("tf.fs", m.TF);
        var ni = 0;
        var nt = 0;
        for (var i = 0; i < m.topRuns.size(); i++) {
            var rn = m.topRuns[i] as Array;
            var x = m.topX[i] as Float;
            var w = rn[rn.size() - 1] as Float;
            if (rn[0].equals("i")) {
                icon("tf.i" + ni, rn[1] as String, x + w / 2, m.tfBase - m.TF * 0.334, 0, m.tfIcon, rn[2] as Number);
                ni++;
            } else if (rn[0].equals("s")) {
                var p = "tf.t" + nt;
                out(p + ".x", AuraMath.r1(x));
                out(p + ".s", rn[1]);
                out(p + ".col", col(rn[2] as Number));
                out(p + ".w", rn[3]);
                out(p + ".disp", "inline");
                nt++;
            }
        }
        for (; ni < 2; ni++) {
            out("tf.i" + ni + ".disp", "none");
        }
        for (; nt < 3; nt++) {
            out("tf.t" + nt + ".disp", "none");
        }
        // time
        var t0 = -m.tW / 2;
        var xs = "";
        for (var i = 0; i < m.txs.size(); i++) {
            xs += (i > 0 ? " " : "") + r1(t0 + m.txs[i]);
        }
        out("time.str", m.tStr);
        out("time.xs", xs);
        out("time.y", AuraMath.r1(m.tBase));
        out("time.fs", AuraMath.r1(m.tFs));
        out("time.fill", m.aod ? "none" : col(m.c("ink")));
        out("time.stroke", m.aod ? col(m.c("grey")) : "none");
        out("time.sw", m.aod ? 2.2 : 0);
        // the sides (SPEC.md §6.8-6.9b)
        out("sides.left", m.sideL);
        out("sides.right", m.sideR);
        out("sides.marks", m.marks ? "true" : "false");
        // battery
        var bl = segs(m, 10, -1, false);
        var br = segs(m, 10, 1, false);
        out("batt.disp", disp(!m.aod && m.battOn));
        out("batt.dL", disp(m.sideL.equals("battery")));
        out("batt.dR", disp(m.sideR.equals("battery")));
        out("batt.on", bl[0]);
        out("batt.off", bl[1]);
        out("batt.onR", br[0]);
        out("batt.offR", br[1]);
        out("batt.col", col(m.battCol));
        out("batt.w", m.battBold ? 700 : 600);
        out("batt.l1", m.battL1);
        out("batt.y1", m.by1);
        out("batt.fs", m.battFs);
        out("batt.xL", -166 + m.mkW);
        out("batt.xR", 166 - m.mkW);
        marks(m, "batt", "battery", m.by1, m.battFs, m.battCol);
        // the sensor rail
        var sl = segs(m, m.sensN, -1, true);
        var sr = segs(m, m.sensN, 1, true);
        out("sens.disp", disp(!m.aod && m.sensOn));
        out("sens.dL", disp(m.sideL.equals("sensor")));
        out("sens.dR", disp(m.sideR.equals("sensor")));
        out("sens.n", m.sensN);
        out("sens.on", sl[0]);
        out("sens.off", sl[1]);
        out("sens.onR", sr[0]);
        out("sens.offR", sr[1]);
        out("sens.col", col(m.sensCol));
        out("sens.w", m.sensBold ? 700 : 600);
        out("sens.t", m.sensT);
        out("sens.tD", disp(!m.sensT.equals("")));
        out("sens.y", m.sy1);
        out("sens.fs", m.sensFs);
        out("sens.xL", -166 + m.mkW);
        out("sens.xR", 166 - m.mkW);
        if (m.sensT.equals("")) {
            out("sens.mL.disp", "none");
            out("sens.mR.disp", "none");
        } else {
            marks(m, "sens", "sensor", m.sy1, m.sensFs, m.sensCol);
        }
        // the level rail
        var l = inp.limits;
        out("rail.disp", disp(m.railOn));
        out("rail.dL", disp(m.sideL.equals("level")));
        out("rail.dR", disp(m.sideR.equals("level")));
        out("rail.base", arc(182, 47, 133, false));
        out("rail.band", arc(182, AuraMath.ang(l[3]), AuraMath.ang(l[1]), false));
        out("rail.tight", arc(182, AuraMath.ang(l[2]), AuraMath.ang(l[1]), false));
        out("rail.bandW", m.aod ? 2 : 6);
        out("rail.zone", m.zone != null ? arc(182, m.zone[0], m.zone[1], false) : "M0 0");
        out("rail.zoneCol", col(m.c(m.ck)));
        out("rail.zoneDisp", disp(m.zoneOn));
        var minor = [50, 60, 80, 90, 100, 110, 120, 130, 150, 160, 170, 200, 220, 240, 280, 350];
        var s = "";
        for (var i = 0; i < minor.size(); i++) {
            s += tick(minor[i], 177, 187);
        }
        out("rail.minor", s);
        out("rail.minorDisp", disp(!m.aod));
        var mid = [40, l[2], l[5], 400];
        s = "";
        for (var i = 0; i < mid.size(); i++) {
            s += tick(mid[i], 175, 189);
        }
        out("rail.mid", s);
        var major = [l[0], l[1], l[3], l[4]];
        s = "";
        for (var i = 0; i < major.size(); i++) {
            s += tick(major[i], 171, 192);
        }
        out("rail.major", s);
        // the bead and its trail
        out("bead.disp", disp(m.beadOn));
        out("bead.deg", AuraMath.r1(m.beadA));
        out("bead.col", col(m.struck ? m.c("grey") : m.beadCol));
        out("bead.fill", col(m.struck ? 0x000000 : m.beadCol));
        out("bead.stroke", m.struck ? col(m.c("grey")) : "none");
        out("bead.r", m.aod ? 6 : 7);
        out("bead.glow", disp(!m.struck && m.awake));
        var chev = "M0 0";
        if (m.lo || m.hi) {
            var a = m.lo ? 139.5 : 40.5;
            var b = m.lo ? 135.6 : 44.4;
            chev = "M" + pt(AuraMath.P(182, a)) + "L" + pt(AuraMath.P(176.5, b)) + "L" + pt(AuraMath.P(187.5, b)) + "Z";
        }
        out("bead.chev", chev);
        out("bead.chevDisp", disp(m.beadOn && (m.lo || m.hi)));
        for (var i = 0; i < 6; i++) {
            out("t" + i + ".deg", m.trailDeg[i]);
            out("t" + i + ".r", AuraMath.r1(2.2 + i * 0.45));
            out("t" + i + ".o", AuraMath.r1(0.1 + i * 0.07));
            out("t" + i + ".d", disp(m.trailOk[i] as Boolean));
        }
        // the value
        out("val.disp", disp(m.valueOn));
        out("val.str", m.vStr);
        out("val.fs", AuraMath.r1(m.vFs));
        out("val.y", AuraMath.r1(m.vBase));
        xs = "";
        for (var i = 0; i < m.vxs.size(); i++) {
            xs += (i > 0 ? " " : "") + r1(m.x0 + m.vxs[i]);
        }
        out("val.xs", xs);
        out("val.fill", col(m.vFill));
        out("val.blk", disp(m.inverted));
        out("val.blkCol", col(m.blkCol));
        out("val.bx", m.blk[0]);
        out("val.by", m.blk[1]);
        out("val.bw", m.blk[2]);
        out("val.bh", m.blk[3]);
        out("val.strike", disp(m.struck));
        out("val.sx1", m.strike[0]);
        out("val.sx2", m.strike[1]);
        out("val.sy", m.strike[2]);
        out("val.sw", m.strike[3]);
        var q = m.box * 1.05;
        var arrow = "M" + r1(-0.42 * q) + " " + r1(-0.07 * q) + "H" + r1(0.25 * q) + "V" + r1(0.07 * q) + "H" + r1(-0.42 * q) + "Z" +
            "M" + r1(0.46 * q) + " 0L" + r1(0.06 * q) + " " + r1(0.3 * q) + "L" + r1(0.16 * q) + " 0L" + r1(0.06 * q) + " " + r1(-0.3 * q) + "Z";
        if (m.doubled) {
            arrow += "M" + r1(0.12 * q) + " 0L" + r1(-0.28 * q) + " " + r1(0.3 * q) + "L" + r1(-0.18 * q) + " 0L" + r1(-0.28 * q) + " " + r1(-0.3 * q) + "Z";
        }
        out("val.arrow", arrow);
        out("val.tf", "translate(" + n(m.ax) + "px, " + n(m.ay) + "px) rotate(" + n(m.deg) + "deg)");
        out("val.arrowD", disp(m.arrowD));
        out("val.ax", m.ax);
        out("val.ay", m.ay);
        out("val.ringR", m.ringR);
        out("val.ringW", m.ringW);
        out("val.ringD", disp(m.ringD));
        out("val.revealD", disp(m.revealD));
        out("val.rx1", m.reveal[0]);
        out("val.rx2", m.reveal[1]);
        out("val.ry", m.reveal[2]);
        // the sparkline
        out("sp.disp", disp(m.valueOn && m.spark));
        out("sp.framed", m.spFramed);
        out("sp.mid", AuraMath.r1(m.spMid));
        out("sp.x", AuraMath.r1(m.sx));
        out("sp.w", AuraMath.r1(m.SW));
        out("sp.age", AuraMath.r1(m.spAge));
        out("sp.fy0", AuraMath.r1(m.sy - 12));
        out("sp.fy1", AuraMath.r1(m.sy));
        out("sp.fy2", AuraMath.r1(m.sy + m.SH));
        out("sp.fy3", AuraMath.r1(m.sy + m.SH + 12));
        var fh = m.SH + 24;
        out("sp.o1", 12 / fh);
        out("sp.o2", (12 + m.SH) / fh);
        out("sp.oh1", 6 / fh);
        out("sp.oh2", (18 + m.SH) / fh);
        out("sp.bandY", m.spLimits[0]);
        out("sp.bandH", AuraMath.r1((m.spLimits[1] as Float) - (m.spLimits[0] as Float)));
        out("sp.bandCol", col(m.c("tight")));
        out("sp.limits", "M" + r1(m.sx) + " " + n(m.spLimits[0]) + "H" + r1(m.sx + m.SW) + "M" + r1(m.sx) + " " + n(m.spLimits[1]) + "H" + r1(m.sx + m.SW));
        out("sp.limCol", m.aod ? "#3A3A3E" : "#5C5C62");
        var line = "";
        for (var r = 0; r < m.spPts.size(); r++) {
            var run = m.spPts[r] as Array;
            for (var i = 0; i < run.size(); i++) {
                line += (i == 0 ? "M" : "L") + pt(run[i]);
            }
            if (run.size() == 1) {
                line += "Z"; // a lone point between gaps: a dot (aura.js)
            }
        }
        out("sp.line", line.equals("") ? "M0 0" : line);
        out("sp.lineCol", col(m.spLineCol));
        out("sp.ray", "M" + pt(m.spEnd) + "l" + n(m.spRay[0] - m.spEnd[0]) + " " + n(m.spRay[1] - m.spEnd[1]));
        out("sp.rayD", disp(m.trendOn));
        out("sp.rayCol", col(m.spRayCol));
        out("sp.ex", m.spEnd[0]);
        out("sp.ey", m.spEnd[1]);
        out("sp.endFill", col(m.spEndFill != null ? m.spEndFill : 0x000000));
        out("sp.endStroke", col(m.spEndStroke));
        // the chip row
        out("chip.disp", disp(m.timeOn));
        out("chip.x", AuraMath.r1(m.rx0));
        out("chip.y", AuraMath.r1(m.chipC + 8));
        out("chip.t", m.readTxt);
        out("pill.disp", disp(m.pillOn));
        if (m.pillOn) {
            var px = m.pillX;
            out("pill.x", AuraMath.r1(px));
            out("pill.y", AuraMath.r1(m.chipC - 19));
            out("pill.w", AuraMath.r1(m.pillW));
            out("pill.fill", col(m.pillFill));
            out("pill.fo", m.pillTonal ? 0.24 : 1);
            out("pill.tc", col(m.pillFg));
            out("pill.tx", AuraMath.r1(px + 15.2 + 23.6 + 9.5));
            out("pill.ty", AuraMath.r1(m.chipC + 8));
            out("pill.t", m.pill[1]);
            var ic = m.pill[3] as String;
            var bell = ic.equals("paused") || ic.equals("off");
            if (bell) {
                out("pill.mark.disp", "none");
                out("pill.bell.tf", "translate(" + r1(px + 27 - 11.8) + " " + r1(m.chipC - 11.8) + ") scale(0.0246) translate(0 960)");
                out("pill.bell.disp", "inline");
            } else {
                icon("pill.mark", ic, px + 27, m.chipC, 0, 23.6, m.pillFg);
                out("pill.bell.disp", "none");
            }
        }
        // a message
        var k = m.msgKind;
        out("msg.disp", disp(k != null));
        if (k != null) {
            var ink = col(m.c("ink"));
            var soft = col(m.c("soft"));
            var dx = "0";
            var dy = "0";
            var pie = "M0 0";
            var flag = "M0 0";
            var pole = "M0 0";
            var x1 = "0";
            var y1 = "0";
            var a1 = "middle";
            var fs1 = "25";
            var c1 = ink;
            var y2 = "0";
            var fs2 = "24";
            var w2 = "500";
            var c2 = soft;
            var t2 = "";
            if (k.equals("warm")) {
                var cx = AuraMath.r1(m.msgX + 17);
                var cy = AuraMath.r1(m.vTop + 17);
                var ea = m.warmFrac * 360;
                var e = [AuraMath.r1(cx + 12.5 * Math.sin(ea * Math.PI / 180)), AuraMath.r1(cy - 12.5 * Math.cos(ea * Math.PI / 180))];
                dx = n(cx);
                dy = n(cy);
                pie = "M" + dx + " " + dy + "L" + dx + " " + r1(cy - 12.5) + "A12.5 12.5 0 " + (ea > 180 ? 1 : 0) + " 1 " + pt(e) + "Z";
                x1 = r1(m.msgX + 46);
                y1 = r1(cy + 32 * 0.668 / 2);
                a1 = "start";
                fs1 = "32";
                y2 = r1(m.chipC + 8);
                t2 = m.msgT2;
            } else if (k.equals("ended")) {
                var fy = AuraMath.r1(m.vTop + 1);
                var cells = [[0, 0], [2, 0], [1, 1]];
                flag = "";
                for (var i = 0; i < 3; i++) {
                    flag += "M" + r1(m.msgX + 3 + cells[i][0] * 7.5) + " " + r1(fy + cells[i][1] * 7.5) + "h7.5v7.5h-7.5Z";
                }
                pole = "M" + r1(m.msgX + 2) + " " + r1(fy - 2) + "V" + r1(fy + 30) + "M" + r1(m.msgX + 3) + " " + n(fy) + "h22.5v15h-22.5";
                x1 = r1(m.msgX + 38);
                y1 = r1(m.vTop + 16 + 32 * 0.668 / 2);
                a1 = "start";
                fs1 = "32";
            } else if (k.equals("none")) {
                y1 = r1(m.vTop + 20);
                fs1 = "30";
                c1 = soft;
            } else if (k.equals("gap")) {
                y1 = r1(m.vTop + 16.7);
                c1 = soft;
                y2 = r1(m.vTop + 16.7 + 12 + m.cap2);
                fs2 = r1(m.cap2 / 0.668);
                w2 = "600";
                c2 = ink;
                t2 = m.msgT2;
            }
            out("msg.dx", dx);
            out("msg.dy", dy);
            out("msg.discD", disp(k.equals("warm")));
            out("msg.pie", pie);
            out("msg.flag", flag);
            out("msg.pole", pole);
            out("msg.flagD", disp(k.equals("ended")));
            out("msg.x1", x1);
            out("msg.y1", y1);
            out("msg.a1", a1);
            out("msg.fs1", fs1);
            out("msg.c1", c1);
            out("msg.t1", m.msgT1);
            out("msg.y2", y2);
            out("msg.fs2", fs2);
            out("msg.w2", w2);
            out("msg.c2", c2);
            out("msg.t2", t2);
        }
        // the bottom fields
        out("b.disp", disp(!m.aod));
        ni = 0;
        nt = 0;
        var runs = m.aod ? [] : m.bottomRuns;
        for (var i = 0; i < runs.size(); i++) {
            var rn = runs[i] as Array;
            var so = m.bottomOff[i] as Float;
            var w = rn[rn.size() - 1] as Float;
            if (rn[0].equals("i")) {
                var an = 232 - (so + w / 2) / 172 * 180 / Math.PI;
                var p = AuraMath.P(164, an);
                icon("b.i" + ni, rn[1] as String, p[0], p[1], an - 180, 20, rn[2] as Number);
                ni++;
            } else if (rn[0].equals("s")) {
                var p = "b.t" + nt;
                out(p + ".off", AuraMath.r1(so));
                out(p + ".s", rn[1]);
                out(p + ".col", col(rn[2] as Number));
                out(p + ".w", rn[3]);
                out(p + ".disp", "inline");
                nt++;
            }
        }
        for (; ni < 5; ni++) {
            out("b.i" + ni + ".disp", "none");
        }
        for (; nt < 6; nt++) {
            out("b.t" + nt + ".disp", "none");
        }
        System.println("END|" + id);
    }
}
