// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Math;

// The reference's maths and words (tools/watchface/app.js, contracts/watchface/app/SPEC.md): Aura's palette, the
// label widths it lays out with, Kovatchev's scale, the bands, the short durations.
(:glance)
module AppMath {
    // app.js PAL (Aura's)
    const INK = 0xFFFFFF;
    const SOFT = 0xA3A3A8;
    const DIM = 0x6E6E73;
    const FAINT = 0x3A3A3E;
    const GRID = 0x26262A;
    const GREY = 0x8E8E93;
    const ON = 0x1C1C1E;
    const CARD2 = 0x1C1C1F;
    const BLACK = 0x000000;

    // compared, not a dictionary kept in memory (the glance's 64 KB, #217)
    function pal(k as String?) as Number {
        if (k == null) {
            return 0xB4B4B4;
        }
        var ks = ["veryLow", "low", "tight", "target", "high", "veryHigh", "extremelyHigh", "now", "rapid", "long", "carbs"];
        var i = ks.indexOf(k);
        return i < 0 ? 0xB4B4B4 : [0xC2413B, 0xF0524F, 0x3DD47A, 0x8CC63F, 0xF5A524, 0xFF7A45, 0xE35BD0, 0xC6F432, 0x8AB4FF,
            0xC9B0FF, 0xFFB74D][i] as Number;
    }

    function r1(x as Numeric) as Float {
        return Math.floor(x.toFloat() * 10.0 + 0.5).toFloat() / 10.0;
    }

    function clamp(x as Numeric, a as Numeric, b as Numeric) as Float {
        var v = x.toFloat();
        return v < a ? a.toFloat() : v > b ? b.toFloat() : v;
    }

    function abs(a as Numeric) as Float {
        return a < 0 ? -a.toFloat() : a.toFloat();
    }

    // Kovatchev's risk transform (Diabetes Care 1997), g in mg/dL; 0 at 112.5
    function K(g as Numeric) as Float {
        return (1.509 * (Math.pow(Math.ln(g.toFloat()), 1.084) - 5.381)).toFloat();
    }

    // a point on the circle [r] at [a] degrees clockwise from 12
    function P(r as Numeric, a as Numeric) as Array<Float> {
        var t = a.toFloat() * Math.PI / 180.0;
        return [(r * Math.sin(t)).toFloat(), (-r * Math.cos(t)).toFloat()];
    }

    // the big numbers: Atkinson's figures at weight 600 (app.js BIG)
    const BK = "0123456789:.LOHI";
    const BV = "656430569592621602617529621578253253548737699415";

    // the advance [k] of a WV / BV string
    function adv(v as String, k as Number) as Float {
        return ((v.substring(3 * k, 3 * k + 3) as String).toNumber() as Number) / 1000.0;
    }

    // a big number's width at [fs] (app.js bw)
    function bw(s as String, fs as Numeric) as Float {
        var t = 0.0;
        var cs = s.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            var k = BK.find(cs[i].toString());
            t += k != null ? adv(BV, k) : 0.6;
        }
        return (t * AppDraw.fe(fs)).toFloat();
    }

    // the band of [v] mg/dL with the person's limits [b] = [veryLow, low, tight, high, veryHigh, extremelyHigh]
    // (Sukrara's Bands; Aura's band())
    function band(v as Numeric, b as Array) as String {
        if (v <= b[0]) {
            return "veryLow";
        }
        if (v < b[1]) {
            return "low";
        }
        if (v <= b[2]) {
            return "tight";
        }
        if (v < b[3]) {
            return "target";
        }
        if (v < b[4]) {
            return "high";
        }
        if (v < b[5]) {
            return "veryHigh";
        }
        return "extremelyHigh";
    }

    // the value as shown: LO / HI, mmol/L to one decimal
    function fmtG(v as Numeric, unit as String) as String {
        if (v < 40) {
            return "LO";
        }
        if (v > 400) {
            return "HI";
        }
        return unit.equals("mmol") ? (v / 18.0182).format("%.1f") : v.toNumber().toString();
    }

    // the time since [m] minutes, Sukrara's short durations split for a stack (app.js timeSince)
    function timeSince(m as Number) as Array<String> {
        if (m < 1) {
            return [AppWords.now()];
        }
        if (m < 60) {
            return [m + "m"];
        }
        if (m < 360) {
            return m % 60 != 0 ? [(m / 60) + "h", (m % 60) + "m"] : [(m / 60) + "h"];
        }
        if (m < 2880) {
            return [((m + 30) / 60) + "h"];
        }
        return [((m + 720) / 1440) + "d"];
    }

    // an amount with its unit: "4.5u", "30g"
    function amt(k as String, a as Numeric) as String {
        var f = a.toFloat();
        var s = f == f.toNumber() ? f.toNumber().toString() : f.format("%.1f");
        return s + (k.equals("carbs") ? "g" : "u");
    }
}

// The app's own widths, out of the glance's code (its 64 KB)
module AppMathApp {
    // Atkinson's advances per em (app.js WV, aura.js widths()); an unknown glyph counts 0.6
    const WK = " 0123456789.:·ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzáéíóúñÁÉÍÓÚÑ";
    // three digits each, the advance × 1000: a string, not an array of 78 kept in memory (the glance's 64 KB, #217)
    const WV = "280648407548576614588598510615595210210240626613645669569552694694392512631541824693714592724614592558690593856623575607525566485564538314558550252245492262845550548566574349471326540461684481449460525538252548540550626569392714690693";
    // an Atkinson text's width (the amount's unit) at [fs], measured at the size the class draws it (app.js tw)
    function tw(s as String, fs as Numeric) as Float {
        var t = 0.0;
        var cs = s.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            var k = WK.find(cs[i].toString());
            t += k != null ? AppMath.adv(WV, k) : 0.6;
        }
        return (t * AppDraw.fe(fs) * 1.03).toFloat();
    }
}
