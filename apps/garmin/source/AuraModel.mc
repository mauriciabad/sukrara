// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Math;
import Toybox.Time.Gregorian;

// THE LAYOUT: every value of the face as a pure function of the input model (AuraInput), the port of aura.js
// renderVals() (SPEC.md §3–§6). Coordinates: the screen's centre is (0, 0), y down; angles clockwise from 12 o'clock.
// AuraDraw draws it; the tests compare it with contracts/watchface/aura/expect.json (SPEC.md §10). The dials' drawing,
// the halo and the seconds sweep are face-aura's: only their inputs (info) are here.
class AuraModel {
    var inp as AuraInput;
    var pal as Dictionary;
    var lang as String;
    var aod as Boolean;
    var night as Boolean;
    var awake as Boolean;

    // the reading: Sukrara's decisions
    var fr as String = "fresh";
    var hasValue as Boolean = false;
    var struck as Boolean = false;
    var current as Boolean = false;
    var lo as Boolean = false;
    var hi as Boolean = false;
    var bk as String = "tight";
    var ck as String = "tight";
    var out as Boolean = false;
    var lowSide as Boolean = false;
    var trendOn as Boolean = false;
    var doubled as Boolean = false;
    var readTxt as String = "";
    var pill as Array? = null; // [kind, text, tonal, icon]
    var needed as Boolean = false;
    var valueOn as Boolean = false;
    var timeOn as Boolean = false;
    var pillOn as Boolean = false;
    var topRow as Boolean = true;
    var topOn as Boolean = true;
    var msgKind as String? = null; // warm, ended, none, gap
    var chipKind as String? = null; // pill, age
    var spark as Boolean = false;

    // the rows (SPEC.md §4)
    var T as Number = 68;
    var V as Number = 60;
    var TF as Number = 26;
    var roomy as Boolean = true;
    var cap2 as Number = 40;
    var chipH as Number = 0;
    var tfBase as Float = 0.0;
    var tTop as Float = 0.0;
    var tBase as Float = 0.0;
    var vTop as Float = 0.0;
    var vBase as Float = 0.0;
    var chipC as Float = 0.0;

    // the time
    var tStr as String = "";
    var tFs as Float = 0.0;
    var tW as Float = 0.0;
    var txs as Array = [] as Array; // each glyph's centre x

    // the value
    var vStr as String = "";
    var vFs as Float = 0.0;
    var vW as Float = 0.0;
    var vxs as Array = [] as Array; // each glyph's centre x
    var gap as Float = 0.0;
    var box as Float = 0.0;
    var total as Float = 0.0;
    var x0 as Float = 0.0;
    var vFill as Number = 0;
    var inverted as Boolean = false;
    var blkCol as Number = 0; // the very-low block: very low; always-on at 35 % (SPEC.md §8)
    var ax as Float = 0.0;
    var ay as Float = 0.0;
    var deg as Float = 0.0;
    var drawDeg as Float = 0.0; // the angle the arrow is drawn at (deg, or on its way there: the glide)
    var arrowPts as Array = [] as Array; // polygons (each [[x, y], ...]) already rotated and placed
    var arrowD as Boolean = false;
    var ringD as Boolean = false;
    var ringR as Float = 0.0;
    var ringW as Float = 0.0;
    var blk as Array = [] as Array; // x, y, w, h
    var strike as Array = [] as Array; // x1, x2, y, width
    var revealD as Boolean = false;
    var reveal as Array = [] as Array; // x1, x2, y

    // the sparkline
    var sx as Float = 0.0;
    var sy as Float = 0.0;
    var SH as Float = 0.0;
    var spPts as Array = [] as Array; // runs of points (a gap breaks the line)
    var spLimits as Array = [] as Array; // y of the high and low limits
    var spMid as Float = 0.0; // the sparkline window's middle, mg/dL
    var spFramed as Boolean = false; // the window frames the person's range
    var spEnd as Array = [0.0, 0.0] as Array;
    var spRay as Array = [0.0, 0.0] as Array; // the ray's end
    var spLineCol as Number = 0;
    var spRayCol as Number = 0;
    var spEndFill as Number? = null; // null = hollow
    var spEndStroke as Number? = null;
    var beadCol as Number = 0;

    // the chip row
    var rx0 as Float = 0.0;
    var ageW as Float = 0.0;
    var pillX as Float = 0.0;
    var pillW as Float = 0.0;
    var pillFill as Number = 0;
    var pillTonal as Boolean = false;
    var pillFg as Number = 0;

    // a message
    var msgX as Float = 0.0; // the group's left (warm, ended)
    var msgT1 as String = "";
    var msgT2 as String = "";
    var warmFrac as Float = 0.0;

    // the rails
    var railOn as Boolean = false;
    var beadOn as Boolean = false;
    var beadA as Float = 0.0;
    var beadDrawA as Float = 0.0; // the angle the bead is drawn at (beadA, or on its way there: the glide)
    var zone as Array? = null; // [from, to] degrees
    var zoneOn as Boolean = false;
    var trail as Array = [] as Array; // [deg, r, opacity] per drawn point
    var trailDeg as Array = [] as Array; // the 6 places (degrees), drawn or not (the tests)
    var trailOk as Array = [] as Array;

    // the battery
    var battery as Float = 0.0;
    var battCol as Number = 0;
    var battFs as Number = 22;
    var battBold as Boolean = false;
    var battL1 as String = "";
    var battL2 as String = "";
    var battOne as Boolean = false;
    var battLit as Number = 0;
    var by1 as Float = 0.0;

    // the date
    var dateWd as String = "";
    var dateDay as String = "";
    var dateMo as String = "";

    // the fields: runs [type, ...] measured; top: x of each run; bottom: arc offsets
    var topRuns as Array = [] as Array;
    var topX as Array = [] as Array;
    var topW as Float = 0.0;
    var tfIcon as Number = 22;
    var bottomRuns as Array = [] as Array;
    var bottomOff as Array = [] as Array;

    // the dial's inputs (SPEC.md §6.12; drawn by face-aura) and the sweep's
    var ext as Float = 0.0;
    var toward as Float = 0.0;
    var I as Float = 1.0;
    var spd as Float = 0.0;
    var levelY as Float = 60.0;
    var idealY as Float = 15.3;
    var dialCol as Number = 0;
    var sweepOn as Boolean = false;
    var sweepCol as Number = 0;
    var sweepDeg as Number = 0;

    // the clock
    var nowInfo as Gregorian.Info;

    function initialize(input as AuraInput) {
        inp = input;
        lang = input.lang;
        aod = input.mode.equals("aod");
        night = input.mode.equals("night");
        awake = !aod && !night;
        pal = AuraPalette.of(input.mode);
        nowInfo = AuraMath.info(input.now, input.utc);
        reading();
        rows();
        value();
        sparkline();
        chipRow();
        message();
        rails();
        dialInfo();
        batteryLabel();
        timeAndDate();
        topField();
        bottomFields();
    }

    // --- the reading: Sukrara's decisions (aura.js "the reading") ---
    function band(g as Float) as String {
        var l = inp.limits;
        if (g <= l[0]) {
            return "veryLow";
        }
        if (g < l[1]) {
            return "low";
        }
        if (g <= l[2]) {
            return "tight";
        }
        if (g < l[3]) {
            return "target";
        }
        if (g < l[4]) {
            return "high";
        }
        return "veryHigh";
    }

    function c(key as String) as Number {
        return pal[key] as Number;
    }

    hidden function reading() as Void {
        var s = inp.sensor;
        var reads = s.equals("ok") || s.equals("ending") || s.equals("endedSending");
        var a = inp.age;
        fr = a <= 1.5 ? "fresh" : a <= 5 ? "late" : a <= 20 ? "stale" : a < 180 ? "gap" : "long";
        hasValue = reads && (fr.equals("fresh") || fr.equals("late") || fr.equals("stale"));
        struck = hasValue && fr.equals("stale");
        current = hasValue && !struck;
        var g = inp.glucose;
        lo = g < 40;
        hi = g > 400;
        bk = lo ? "veryLow" : hi ? "veryHigh" : band(g);
        ck = hi || g >= inp.limits[5] ? "extremelyHigh" : bk;
        out = !bk.equals("tight") && !bk.equals("target");
        lowSide = bk.equals("veryLow") || bk.equals("low");
        trendOn = inp.trend && inp.rate != null && !lo && !hi && !struck;
        var r = inp.rate != null ? inp.rate : 0.0;
        doubled = inp.doubled != null ? inp.doubled : AuraMath.abs(r) >= 3;
        readTxt = timeText(inp.now - (a * 60).toNumber());

        // the pill: Sukrara's need or what holds it, short, with its icon
        var left = inp.left != null ? AuraWords.dur(lang, inp.left) : AuraWords.muted(lang);
        pill = null;
        if (current) {
            if (inp.status.equals("auto") && out) {
                pill = lowSide ? ["carbs", AuraWords.carbs(lang), false, "carbs"] : ["rapid", AuraWords.insulin(lang), false, "rapid"];
            } else if (inp.status.equals("quiet") && out) {
                pill = [lowSide ? "carbs" : "rapid", left, true, "paused"];
            } else if (inp.status.equals("muted")) {
                pill = out ? [lowSide ? "carbs" : "rapid", left, true, "paused"] : ["note", left, true, "off"];
            }
        }
        if (pill == null && s.equals("endedSending") && hasValue) {
            pill = ["note", AuraWords.ended(lang), false, "sensor"];
        }
        if (s.equals("ended")) {
            pill = ["note", AuraWords.replace(lang), false, "sensor"];
        }
        needed = pill != null && !(pill[2] as Boolean) && (pill[0].equals("carbs") || pill[0].equals("rapid"));
        var priv = inp.privacy.equals("private");
        var revealed = inp.privacy.equals("revealed");
        valueOn = hasValue && (!priv || needed || revealed);
        // the reading's time isn't the number: it shows in private too
        timeOn = hasValue && (fr.equals("late") || fr.equals("stale"));
        pillOn = pill != null && (hasValue || s.equals("ended"));
        topRow = !inp.topField.equals("none");
        topOn = !aod && topRow;
        msgKind = s.equals("warmup") || s.equals("almost") ? "warm" : s.equals("ended") ? "ended" : s.equals("none") ? "none" : !hasValue ? "gap" : null;
        chipKind = pillOn ? "pill" : timeOn || "warm".equals(msgKind) ? "age" : null;
        spark = inp.trendStyle.equals("spark");
        revealD = revealed && hasValue;
    }

    // Sukrara's Freshness.timeText: the clock while it's today's and at most 12 h old, the weekday with it from 12 h or
    // another day, the date after 6 days (contracts/graph/freshness/freshness.json "times")
    function timeText(at as Number) as String {
        var i = AuraMath.info(at, inp.utc);
        var t = AuraMath.clock(i, inp.is24);
        var ageS = inp.now - at;
        if (ageS > 6 * 86400) {
            return (i.day as Number).toString() + AuraMath.NB + AuraWords.monthsShort(lang)[(i.month as Number) - 1];
        }
        var sameDay = i.day == nowInfo.day && i.month == nowInfo.month && i.year == nowInfo.year;
        if (ageS > 12 * 3600 || !sameDay) {
            return AuraWords.days(lang)[(i.day_of_week as Number) - 1] + AuraMath.NB + t;
        }
        return t;
    }

    // --- the big numbers' type (SPEC.md §2): Atkinson Hyperlegible Next at weight 600, the font's own (proportional)
    // figures' advances and its kern pairs, per em; the tabular figures' width ---
    // (per mille of the em; one string of the kern pairs, each " ab" at 3 x its index: cheaper on the heap than arrays)
    const NUM_GLYPHS = "0123456789:.LOHI";
    const NUM_ADV = [656, 430, 569, 592, 621, 602, 617, 529, 621, 578, 253, 253, 548, 737, 699, 415];
    const KERN_PAIRS = " 23 24 28 41 42 44 49 51 53 55 56 59 61 67 71 73 74 77 78 7. 81 82 87 89 92 94 .1 .O LO O. OI IO ";
    const KERN = [-4, -13, -10, -6, -9, 10, -24, -20, -20, -6, -7, -4, -26, -6, 20, 10,
        -44, 30, -20, -30, -20, -10, -10, -19, -20, -4, -30, -30, -30, -30, -23, -23];
    const TNUM = 0.632;

    // a kerned run of the font's figures at fs px: each glyph's centre (its advance centred) into xs; the run's width
    hidden function glyphRun(str as String, fs as Float, xs as Array?) as Float {
        var w = 0.0;
        var cs = str.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            var k = NUM_GLYPHS.find(cs[i].toString());
            var a = k != null ? NUM_ADV[k] / 1000.0 : 0.6;
            if (i > 0) {
                var p = KERN_PAIRS.find(" " + cs[i - 1].toString() + cs[i].toString() + " ");
                if (p != null) {
                    w += KERN[p / 3] / 1000.0;
                }
            }
            if (xs != null) {
                xs.add((w + a / 2) * fs);
            }
            w += a;
        }
        return w * fs;
    }

    // the time: the hour, the colon and the tens of minutes as one kerned run, the last digit tabular (a centred cell,
    // no kerning into it); tFit: the width with the widest tens digit (0-5) at this hour, so the size changes only with it
    var tFit as Float = 0.0;

    hidden function timeGeom(TT as Number) as Void {
        tFs = TT / 0.668;
        txs = [];
        var n = tStr.length();
        var head = glyphRun(tStr.substring(0, n - 1) as String, tFs, txs);
        var cell = TNUM * tFs;
        txs.add(head + cell / 2);
        tW = head + cell;
        tFit = 0.0;
        var pre = tStr.substring(0, n - 2) as String;
        for (var d = 0; d <= 5; d++) {
            tFit = AuraMath.max(tFit, glyphRun(pre + d.toString(), tFs, null));
        }
        tFit += cell;
    }

    // the value: one kerned run (it changes only with a reading)
    hidden function valueGeom(VV as Number) as Void {
        vFs = VV / 0.668;
        vxs = [];
        vW = glyphRun(vStr, vFs, vxs);
        gap = 0.08 * vFs;
        box = 0.62 * vFs;
        total = spark ? vW + 14 + 100 : vW + gap + box;
    }

    var battDaysT as String = "";
    var battPctT as String = "";
    var battOneT as String = "";
    var battBoth as Boolean = false;

    hidden function battW(one as Boolean) as Float {
        if (one) {
            return AuraMath.tw(battOneT, battFs);
        }
        return AuraMath.max(AuraMath.tw(battL1, battFs), battBoth ? AuraMath.tw(battPctT, battFs) : 0);
    }

    hidden function battH(one as Boolean) as Float {
        return battBoth && !one ? battFs * 0.668 * 2 + 9 : battFs * 0.668;
    }

    function halfAt(y as Float) as Float {
        return Math.sqrt(AuraMath.max(0, 171 * 171 - y * y)).toFloat() - 6;
    }

    hidden function msgLeft() as Float {
        if ("warm".equals(msgKind)) {
            return -(46 + AuraMath.tw(AuraWords.warming(lang), 32)) / 2;
        }
        if ("ended".equals(msgKind)) {
            return -(38 + AuraMath.tw(AuraWords.ended(lang), 32)) / 2;
        }
        if ("none".equals(msgKind)) {
            return -AuraMath.tw(AuraWords.noYet(lang), 30) / 2;
        }
        if ("gap".equals(msgKind)) {
            return -AuraMath.tw(AuraWords.noSince(lang), 25) / 2;
        }
        return 0.0;
    }

    // lay the rows out with the first size set that fits; true when one did
    hidden function fitRows(one as Boolean) as Boolean {
        // the time grows by its string: the larger time sizes first, up to ~1.27 x the value's cap (SPEC.md §4)
        var sets = "pill".equals(chipKind)
            ? [[68, 54, 24], [66, 54, 24], [64, 54, 24], [62, 54, 24]]
            : "age".equals(chipKind)
                ? [[70, 56, 24], [68, 56, 24], [66, 56, 24], [64, 56, 24], [62, 54, 24]]
                : [[76, 60, 26], [74, 60, 26], [72, 60, 26], [70, 60, 26], [68, 60, 26], [66, 58, 26], [64, 56, 25], [62, 54, 24]];
        var left0 = msgLeft();
        for (var n = 0; n < sets.size(); n++) {
            var set = sets[n] as Array<Number>;
            T = set[0];
            V = set[1];
            TF = set[2];
            roomy = chipKind == null;
            cap2 = roomy ? 40 : 36;
            var vb = "gap".equals(msgKind)
                ? 16.7 + 12 + cap2
                : "warm".equals(msgKind) ? 34 : "ended".equals(msgKind) ? 32 : "none".equals(msgKind) ? 20 : V;
            chipH = "pill".equals(chipKind) ? 38 : "age".equals(chipKind) ? 17 : 0;
            var tfc = topRow ? TF * 0.668 : 0.0;
            var H = (topRow ? tfc + 20 : 0) + T + 22 + vb + (chipH > 0 ? 18 + chipH : 0);
            var y = -7 - H / 2.0;
            if (topRow) {
                tfBase = y + tfc;
                y += tfc + 20;
            } else {
                tfBase = y;
            }
            tTop = y;
            tBase = y + T;
            y += T + 22;
            vTop = y;
            vBase = y + vb;
            y += vb;
            chipC = chipH > 0 ? y + 18 + chipH / 2.0 : y;
            timeGeom(T);
            valueGeom(V);
            var okT = tFit / 2 <= halfAt(tTop);
            var okV = !hasValue || (total / 2 <= halfAt(vTop) && total / 2 <= halfAt(vBase));
            var lBot = tBase + 6 + battH(one);
            var left = hasValue ? -total / 2 : left0;
            var okL = aod || lBot <= vTop || left >= -166 + battW(one) + 8;
            if (okT && okV && okL) {
                return true;
            }
        }
        return false;
    }

    hidden function rows() as Void {
        var g = inp.glucose;
        vStr = lo
            ? "LO"
            : hi ? "HI" : inp.unit.equals("mmol") ? (g / 18.0182).format("%.1f") : Math.floor(g + 0.5).toNumber().toString();
        tStr = AuraMath.clock(nowInfo, inp.is24);
        // the battery label: days, percent or both (stacked where it fits beside the row, else one line)
        battery = inp.battery;
        battDaysT = battery < 1
            ? AuraWords.h(lang, AuraMath.max(1, Math.floor(battery * 24 + 0.5)).toNumber())
            : AuraWords.d(lang, Math.floor(battery + 0.5).toNumber());
        var pct = inp.batteryPct != null ? inp.batteryPct.toFloat() : Math.floor(battery * 10 + 0.5).toFloat();
        battPctT = AuraMath.clamp(Math.floor(pct + 0.5), 0, 100).toNumber().toString() + "%";
        battBoth = inp.battLabel.equals("both");
        battL1 = inp.battLabel.equals("percent") ? battPctT : battDaysT;
        battFs = battBoth ? 20 : 22;
        battOneT = battDaysT + AuraMath.NB + "·" + AuraMath.NB + battPctT;
        battOne = battBoth && !fitRows(false);
        fitRows(battOne);
    }

    // --- the value with Sukrara's arrow (SPEC.md §6.4) ---
    hidden function value() as Void {
        x0 = -total / 2;
        ax = AuraMath.r1(spark ? 0 : x0 + vW + gap + box / 2);
        ay = AuraMath.r1(vBase - V / 2.0);
        inverted = !struck && bk.equals("veryLow");
        blkCol = aod ? 0x441715 : c("veryLow");
        vFill = c("ink");
        if (struck) {
            vFill = c("grey");
        } else if (inverted) {
            vFill = aod ? c("ink") : 0xFFFFFF; // always-on: the dimmer ink on the dimmed block (SPEC.md §8)
        } else if (out) {
            vFill = c(ck);
        } else if (inp.rangeColour) {
            vFill = c(bk);
        }
        var r = inp.rate != null ? inp.rate : 0.0;
        deg = AuraMath.r1(-AuraMath.clamp(r / 3, -1, 1) * 90);
        arrow(deg);
        arrowD = valueOn && !spark && trendOn;
        ringD = !spark && !trendOn;
        ringR = AuraMath.r1(0.24 * box);
        ringW = AuraMath.r1(0.11 * box);
        var blkR = spark ? x0 + vW : x0 + total;
        blk = [AuraMath.r1(x0 - 12), AuraMath.r1(vBase - V - 12), AuraMath.r1(blkR - x0 + 24), V + 24];
        strike = [AuraMath.r1(x0 - 6), AuraMath.r1(x0 + vW + 6), AuraMath.r1(vBase - V * 0.48), AuraMath.r1(0.075 * vFs)];
        var len = AuraMath.clamp(inp.reveal, 0, 1) * vW;
        reveal = [AuraMath.r1(x0 + vW / 2 - len / 2), AuraMath.r1(x0 + vW / 2 + len / 2), AuraMath.r1(vBase + 13)];
        beadCol = out ? c(ck) : inp.rangeColour ? c(bk) : c("now");
    }

    // the arrow's polygons turned to d degrees and placed (deg, or an angle of the glide on a new reading, SPEC.md §7)
    function arrow(d as Numeric) as Void {
        drawDeg = d.toFloat();
        var s = box * 1.05;
        var polys = [
            [[-0.42, -0.07], [0.25, -0.07], [0.25, 0.07], [-0.42, 0.07]],
            [[0.46, 0.0], [0.06, 0.3], [0.16, 0.0], [0.06, -0.3]],
        ];
        if (doubled) {
            polys.add([[0.12, 0.0], [-0.28, 0.3], [-0.18, 0.0], [-0.28, -0.3]]);
        }
        var t = d * Math.PI / 180;
        var co = Math.cos(t);
        var si = Math.sin(t);
        arrowPts = [];
        for (var i = 0; i < polys.size(); i++) {
            var p = polys[i] as Array;
            var q = [];
            for (var j = 0; j < p.size(); j++) {
                var px = AuraMath.r1(p[j][0] * s);
                var py = AuraMath.r1(p[j][1] * s);
                q.add([ax + px * co - py * si, ay + px * si + py * co]);
            }
            arrowPts.add(q);
        }
    }

    // --- the sparkline: the last hour, log time, a fixed scale (0.6 px per mg/dL) whose window pans (SPEC.md §6.5) ---
    const SPARK_PX = 0.6;
    const SPARK_FADE = 12.0;

    // a value's height: straight on the fixed scale around spMid, squeezed into the fade band beyond the box
    function sparkY(v as Numeric) as Float {
        var y = sy + SH / 2 - (v - spMid) * SPARK_PX;
        if (y < sy) {
            y = sy - SPARK_FADE * (1 - Math.pow(Math.E, (y - sy) / SPARK_FADE));
        } else if (y > sy + SH) {
            y = sy + SH + SPARK_FADE * (1 - Math.pow(Math.E, (sy + SH - y) / SPARK_FADE));
        }
        return AuraMath.r1(y);
    }

    // the opacity at a height: 1 in the box, 0.45 at 6 px out, 0.15 at the fade band's edge
    function sparkFade(y as Numeric) as Float {
        var d = y < sy ? sy - y : y > sy + SH ? y - sy - SH : 0.0;
        if (d <= 0) {
            return 1.0;
        }
        return d <= SPARK_FADE / 2 ? 1 - 0.55 * d / (SPARK_FADE / 2) : AuraMath.max(0.15, 0.45 - 0.3 * (d - SPARK_FADE / 2) / (SPARK_FADE / 2));
    }

    hidden function sparkline() as Void {
        var SW = 100.0;
        sx = x0 + vW + 14;
        SH = V + 14.0;
        sy = vBase - V - 6;
        var g = inp.glucose;
        var series;
        if (inp.spark != null) {
            series = inp.spark;
        } else if (inp.history != null) {
            series = (inp.history as Array).slice(0, null);
            series.add(g);
        } else if (inp.rate != null) {
            series = [];
            for (var k = 12; k >= 0; k--) {
                series.add(g - inp.rate * 5 * k);
            }
        } else {
            series = [g];
        }
        // the window: frame the person's range when the hour fits with it, else the hour's middle, the newest inside
        var n = series.size();
        var hLo = null;
        var hHi = null;
        var newest = g;
        for (var j = 0; j < n; j++) {
            var v = series[j];
            if (v == null || inp.age + 5 * (n - 1 - j) > 60) {
                continue;
            }
            hLo = hLo == null || v < hLo ? v : hLo;
            hHi = hHi == null || v > hHi ? v : hHi;
            newest = v;
        }
        if (hLo == null) {
            hLo = g;
            hHi = g;
        }
        var span = SH / SPARK_PX;
        var rLo = AuraMath.min(hLo, inp.limits[1]);
        var rHi = AuraMath.max(hHi, inp.limits[3]);
        spFramed = rHi - rLo <= span;
        spMid = spFramed ? (rLo + rHi) / 2.0 : AuraMath.clamp((hLo + hHi) / 2.0, newest - 0.4 * span, newest + 0.4 * span);
        spPts = [];
        var run = [];
        var ln7 = Math.ln(7);
        var last = null;
        for (var j = 0; j < n; j++) {
            var a = inp.age + 5 * (n - 1 - j);
            if (a > 60) {
                continue;
            }
            var v = series[j];
            if (v == null) {
                if (run.size() > 0) {
                    spPts.add(run);
                    run = [];
                }
                continue;
            }
            last = [AuraMath.r1(sx + SW * (1 - Math.ln(1 + a / 10.0) / ln7)), sparkY(v)];
            run.add(last);
        }
        if (run.size() > 0) {
            spPts.add(run);
        }
        spEnd = last != null ? last : [AuraMath.r1(sx + SW), sparkY(g)];
        var rad = deg * Math.PI / 180;
        spRay = [spEnd[0] + AuraMath.r1(22 * Math.cos(rad)), spEnd[1] + AuraMath.r1(22 * Math.sin(rad))];
        spLimits = [sparkY(inp.limits[3]), sparkY(inp.limits[1])];
        spLineCol = struck ? c("dim") : c("soft");
        spRayCol = inverted ? c("veryLow") : vFill;
        spEndFill = current && trendOn ? beadCol : null;
        spEndStroke = current && trendOn ? null : struck ? c("grey") : vFill;
    }

    // --- the chip row: the reading's time once it's late, then the pill (SPEC.md §6.6) ---
    hidden function chipRow() as Void {
        ageW = timeOn ? AuraMath.tw(readTxt, 24) : 0.0;
        pillW = pillOn ? 15.2 + 23.6 + 9.5 + AuraMath.tw(pill[1] as String, 24) + 17.1 : 0.0;
        var gapA = timeOn && pillOn ? 14 : 0;
        rx0 = -(ageW + gapA + pillW) / 2;
        pillX = rx0 + ageW + gapA;
        if (pillOn) {
            pillTonal = pill[2] as Boolean;
            pillFill = pal[pill[0]] != null ? c(pill[0] as String) : c("note");
            pillFg = pillTonal ? pillFill : 0x1C1C1E;
        }
    }

    // --- a message instead of a value: never a figure that could pass for glucose (SPEC.md §6.7) ---
    hidden function message() as Void {
        if ("warm".equals(msgKind)) {
            var almost = inp.sensor.equals("almost");
            warmFrac = almost ? 0.96 : AuraMath.clamp(inp.warm, 0.02, 0.98);
            msgT1 = AuraWords.warming(lang);
            msgX = -(34 + 12 + AuraMath.tw(msgT1, 32)) / 2;
            var readyMin = inp.readyMin != null ? inp.readyMin : (1 - warmFrac) * 60;
            var ready = AuraMath.info(inp.now + (readyMin * 60).toNumber(), inp.utc);
            msgT2 = almost ? AuraWords.almost(lang) : AuraWords.readyAt(lang, AuraMath.clock(ready, inp.is24));
        } else if ("ended".equals(msgKind)) {
            msgT1 = AuraWords.ended(lang);
            msgX = -(26 + 12 + AuraMath.tw(msgT1, 32)) / 2;
        } else if ("none".equals(msgKind)) {
            msgT1 = AuraWords.noYet(lang);
        } else if ("gap".equals(msgKind)) {
            msgT1 = AuraWords.noSince(lang);
            msgT2 = readTxt;
        }
    }

    // --- the level rail: Kovatchev's scale, the person's limits as notches (SPEC.md §6.8) ---
    hidden function rails() as Void {
        var l = inp.limits;
        railOn = !inp.sensor.equals("none");
        beadOn = hasValue && railOn;
        var g = inp.glucose;
        beadA = lo ? 133.0 : hi ? 47.0 : AuraMath.ang(g);
        beadDrawA = beadA;
        zone = null;
        if (bk.equals("veryLow")) {
            zone = [AuraMath.ang(l[0]), 133.0];
        } else if (bk.equals("low")) {
            zone = [AuraMath.ang(l[1]), AuraMath.ang(l[0])];
        } else if (bk.equals("high")) {
            zone = [AuraMath.ang(l[4]), AuraMath.ang(l[3])];
        } else if (bk.equals("veryHigh")) {
            zone = [47.0, AuraMath.ang(l[4])];
        }
        zoneOn = beadOn && out && !struck && !aod;
        var hist = [];
        var r = inp.rate != null ? inp.rate : 0.0;
        if (inp.history != null) {
            hist = inp.history as Array;
        } else if (inp.spark != null) {
            var sp = inp.spark as Array;
            hist = sp.slice(sp.size() >= 7 ? sp.size() - 7 : 0, sp.size() - 1);
        } else if (trendOn) {
            for (var k = 6; k >= 1; k--) {
                hist.add(g - r * 5 * k);
            }
        }
        var trailOn = beadOn && !struck && !aod && !lo && !hi;
        trail = [];
        trailDeg = [];
        trailOk = [];
        for (var i = 0; i < 6; i++) {
            var idx = hist.size() - 6 + i;
            var v = idx >= 0 ? hist[idx] : null;
            var ok = trailOn && v != null;
            var d = AuraMath.r1(ok ? AuraMath.ang(v) : beadA);
            trailDeg.add(d);
            trailOk.add(ok);
            if (ok) {
                trail.add([d, AuraMath.r1(2.2 + i * 0.45), AuraMath.r1(0.1 + i * 0.07)]);
            }
        }
    }

    // --- the dial's inputs and the sweep's colour (SPEC.md §6.11, §6.12) ---
    hidden function dialInfo() as Void {
        var g = inp.glucose;
        var kv = AuraMath.K(AuraMath.clamp(g, 40, 400));
        ext = hasValue ? AuraMath.clamp(AuraMath.abs(kv) / 1.9, 0, 1) : 0.0;
        var side = kv >= 0 ? 1 : -1;
        var r = inp.rate != null ? inp.rate : 0.0;
        toward = current && trendOn ? AuraMath.clamp(-side * r / 2, -1, 1) * AuraMath.min(1, AuraMath.abs(kv) / 0.3) : 0.0;
        I = 1 + 0.65 * AuraMath.max(0, -toward) - 0.55 * AuraMath.max(0, toward);
        spd = trendOn ? AuraMath.clamp(AuraMath.abs(r) / 3, 0, 1) : 0.0;
        levelY = hasValue ? AuraMath.r1(-182 * Math.cos(beadA * Math.PI / 180)) : 60.0;
        idealY = AuraMath.r1(-182 * Math.cos(AuraMath.ang(112.5) * Math.PI / 180));
        dialCol = !current ? c("grey") : out ? c(ck) : inp.rangeColour ? c(bk) : c("neutral");
        sweepOn = awake && inp.seconds;
        sweepCol = current ? beadCol : c("grey");
        sweepDeg = (nowInfo.sec as Number) * 6;
    }

    // --- the battery: rail and label (SPEC.md §6.9) ---
    hidden function batteryLabel() as Void {
        battLit = AuraMath.clamp(Math.ceil(battery), 0, 10).toNumber();
        battCol = battery < 1 ? c("low") : battery < 2 ? c("high") : c("soft");
        battBold = battery < 2;
        by1 = AuraMath.r1(tBase + 6 + battFs * 0.668);
        battL2 = battBoth && !battOne ? battPctT : "";
        battL1 = battOne ? battOneT : battL1;
    }

    // --- the time and the date (SPEC.md §6.1, §6.3) ---
    hidden function timeAndDate() as Void {
        dateWd = AuraWords.weekdays(lang)[(nowInfo.day_of_week as Number) - 1] + AuraMath.NB;
        dateDay = (nowInfo.day as Number).toString();
        dateMo = AuraMath.NB + AuraWords.months(lang)[(nowInfo.month as Number) - 1];
    }

    // --- the fields (SPEC.md §6.2, §6.10) ---
    // a run: ["i", icon, colour] | ["s", text, colour, weight] | ["g", width]; measure() appends the width
    function fieldRuns(key as String) as Array {
        var ink = c("ink");
        var soft = c("soft");
        var NB = AuraMath.NB;
        if (key.equals("steps")) {
            return [["i", "steps", soft], ["g", 5], ["s", num(inp.steps, true), ink, 500]];
        } else if (key.equals("weather")) {
            return [["i", "sun", soft], ["g", 6], ["s", inp.temp != null ? inp.temp.toString() + "°" : "--", ink, 500]];
        } else if (key.equals("sunset")) {
            return [["i", "sunset", soft], ["g", 6], ["s", minutes(inp.sunset), ink, 500]];
        } else if (key.equals("bodyBattery")) {
            return [["i", "bolt", soft], ["g", 4], ["s", num(inp.bodyBattery, false), ink, 500]];
        } else if (key.equals("altitude")) {
            return [["i", "mountain", soft], ["g", 6], ["s", inp.altitude != null ? num(inp.altitude, true) + NB + inp.altUnit : "--", ink, 500]];
        } else if (key.equals("heart")) {
            return [["i", "heart", soft], ["g", 5], ["s", num(inp.heart, false), ink, 500]];
        } else if (key.equals("notifications")) {
            return [["i", "bell", soft], ["g", 5], ["s", num(inp.notifications, false), ink, 500]];
        } else if (key.equals("tir")) {
            return [["i", "ring", soft], ["g", 5], ["s", inp.tir != null ? inp.tir.toString() + "%" : "--", ink, 500]];
        } else if (key.equals("tz2")) {
            return [["i", "globe", soft], ["g", 6], ["s", inp.tz2Label + NB + minutes(inp.tz2Min), ink, 500]];
        } else if (key.equals("floors")) {
            return [["i", "stairs", soft], ["g", 5], ["s", num(inp.floors, false), ink, 500]];
        } else if (key.equals("sensor")) {
            return [["i", "sensor", soft], ["g", 5], ["s", AuraWords.dur(lang, inp.sensorLeft * 60), ink, 500]];
        } else if (key.equals("noPhone")) {
            return [["i", "phoneOff", soft], ["g", 5], ["s", AuraWords.noPhone(lang), ink, 500]];
        } else if (key.equals("treatment")) {
            var r = [];
            var items = inp.lastItems;
            if (items.size() == 0) {
                return r;
            }
            for (var n = 0; n < items.size(); n++) {
                var it = items[n] as Array;
                var k = it[0] as String;
                var kc = pal[k] != null ? c(k) : c("rapid");
                if (n > 0) {
                    r.add(["g", 8]);
                }
                r.add(["i", k, kc]);
                r.add(["g", 4]);
                r.add(["s", AuraWords.amount(lang, k, it[1] as Numeric or String), kc, 600]);
            }
            r.add(["g", 9]);
            r.add(["s", AuraWords.dur(lang, inp.lastAgo), soft, 500]);
            return r;
        }
        return [];
    }

    function num(n as Number?, sep as Boolean) as String {
        if (n == null) {
            return "--";
        }
        return sep ? AuraWords.thousands(lang, n) : n.toString();
    }

    function minutes(m as Number?) as String {
        if (m == null) {
            return "--";
        }
        return AuraMath.hm((m / 60) % 24, m % 60, inp.is24);
    }

    hidden function measure(runs as Array, fs as Number, iw as Number) as Float {
        var w = 0.0;
        for (var i = 0; i < runs.size(); i++) {
            var rn = runs[i] as Array;
            var t = rn[0] as String;
            var x = t.equals("i") ? iw.toFloat() : t.equals("s") ? AuraMath.tw(rn[1] as String, fs) : (rn[1] as Number).toFloat();
            rn.add(x);
            w += x;
        }
        return w;
    }

    // the top field's width even where it is hidden (always-on keeps its row; the halo's numbers use it)
    function topWidth() as Float {
        return topOn || !topRow ? topW : measure(fieldRuns(inp.topField), TF, tfIcon);
    }

    hidden function topField() as Void {
        tfIcon = Math.floor(TF * 0.83 + 0.5).toNumber();
        topRuns = topOn ? fieldRuns(inp.topField) : [];
        topW = measure(topRuns, TF, tfIcon);
        topX = [];
        var xx = -topW / 2;
        for (var i = 0; i < topRuns.size(); i++) {
            topX.add(xx);
            var rn = topRuns[i] as Array;
            xx += rn[rn.size() - 1] as Float;
        }
    }

    const BL = 172 * (232 - 128) * Math.PI / 180; // the bottom arc's length

    function prio(k as String) as Number {
        return k.equals("sensor") || k.equals("noPhone") ? 3 : k.equals("treatment") ? 2 : 1;
    }

    hidden function buildBottom(keys as Array) as Array {
        var r = [];
        for (var i = 0; i < keys.size(); i++) {
            var f = fieldRuns(keys[i] as String);
            if (f.size() == 0) {
                continue;
            }
            if (r.size() > 0) {
                r.add(["g", 26]);
            }
            r.addAll(f);
        }
        return r;
    }

    hidden function bottomFields() as Void {
        var keys = inp.fields.slice(0, 2);
        var second = keys.size() > 1 ? keys[1] : "treatment";
        if (inp.sensor.equals("ending")) {
            keys = ["sensor", second];
        }
        if (!inp.phone) {
            keys = ["noPhone", second];
        }
        var runs = buildBottom(keys);
        var w = measure(runs, 24, 20);
        while (keys.size() > 1 && w > BL - 8) {
            var drop = 0;
            for (var n = 1; n < keys.size(); n++) {
                if (prio(keys[n] as String) < prio(keys[drop] as String)) {
                    drop = n;
                }
            }
            var kept = [];
            for (var n = 0; n < keys.size(); n++) {
                if (n != drop) {
                    kept.add(keys[n]);
                }
            }
            keys = kept;
            runs = buildBottom(keys);
            w = measure(runs, 24, 20);
        }
        bottomRuns = aod ? [] : runs;
        bottomOff = [];
        var bs = AuraMath.max(0, (BL - w) / 2);
        for (var i = 0; i < runs.size(); i++) {
            bottomOff.add(bs);
            var rn = runs[i] as Array;
            bs += rn[rn.size() - 1] as Float;
        }
    }
}

// The three palettes (aura.js pal(); SPEC.md §2)
module AuraPalette {
    function of(mode as String) as Dictionary {
        var p = {
            "ink" => 0xFFFFFF, "soft" => 0xA3A3A8, "dim" => 0x6E6E73, "faint" => 0x3A3A3E, "grid" => 0x26262A,
            "band" => 0x38383E, "band2" => 0x4E4E55, "grey" => 0x8E8E93, "texture" => 0x19191C,
            "veryLow" => 0xC2413B, "low" => 0xF0524F, "tight" => 0x3DD47A, "target" => 0x8CC63F, "high" => 0xF5A524,
            "veryHigh" => 0xFF7A45, "extremelyHigh" => 0xE35BD0, "now" => 0xC6F432, "rapid" => 0x8AB4FF,
            "long" => 0xC9B0FF, "carbs" => 0xFFB74D, "note" => 0xB4B4B4, "neutral" => 0xA3A3A8,
        };
        if (mode.equals("night")) {
            var n = {
                "ink" => 0x8C8C92, "soft" => 0x5E5E64, "dim" => 0x46464B, "faint" => 0x2A2A2E, "grid" => 0x1A1A1D,
                "band" => 0x242428, "band2" => 0x303035, "grey" => 0x56565B, "veryLow" => 0xA63A35, "low" => 0xB8433F,
                "tight" => 0x2E9C5C, "target" => 0x6B9631, "high" => 0xB57C1E, "veryHigh" => 0xBC5C36,
                "extremelyHigh" => 0xA84A9C, "now" => 0x7E9A26, "rapid" => 0x5F7EB3, "long" => 0x8C7AB3,
                "carbs" => 0xB3833A, "note" => 0x7A7A7E, "neutral" => 0x5E5E64,
            };
            var ks = n.keys();
            for (var i = 0; i < ks.size(); i++) {
                p[ks[i]] = n[ks[i]];
            }
        } else if (mode.equals("aod")) {
            p["ink"] = 0xC9C9CE;
            p["soft"] = 0x7A7A80;
            p["dim"] = 0x55555A;
            p["faint"] = 0x323236;
            p["grid"] = 0x1C1C1F;
            p["band"] = 0x2A2A2E;
            p["band2"] = 0x2A2A2E;
            p["grey"] = 0x6E6E73;
            p["now"] = 0xA9CF2A;
        }
        return p;
    }
}
