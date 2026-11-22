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

    var _pal as Dictionary? = null;

    function pal(k as String?) as Number {
        if (_pal == null) {
            _pal = {
                "veryLow" => 0xC2413B, "low" => 0xF0524F, "tight" => 0x3DD47A, "target" => 0x8CC63F, "high" => 0xF5A524,
                "veryHigh" => 0xFF7A45, "extremelyHigh" => 0xE35BD0, "now" => 0xC6F432, "rapid" => 0x8AB4FF,
                "long" => 0xC9B0FF, "carbs" => 0xFFB74D, "note" => 0xB4B4B4
            };
        }
        var c = k != null ? (_pal as Dictionary).get(k) : null;
        return c != null ? c as Number : 0xB4B4B4;
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

    // Atkinson's advances per em (app.js WV, aura.js widths()); an unknown glyph counts 0.6
    const WK = " 0123456789.:·ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzáéíóúñÁÉÍÓÚÑ";
    const WV = [
        0.28, 0.648, 0.407, 0.548, 0.576, 0.614, 0.588, 0.598, 0.51, 0.615, 0.595, 0.21, 0.21, 0.24, 0.626, 0.613, 0.645,
        0.669, 0.569, 0.552, 0.694, 0.694, 0.392, 0.512, 0.631, 0.541, 0.824, 0.693, 0.714, 0.592, 0.724, 0.614, 0.592,
        0.558, 0.69, 0.593, 0.856, 0.623, 0.575, 0.607, 0.525, 0.566, 0.485, 0.564, 0.538, 0.314, 0.558, 0.55, 0.252,
        0.245, 0.492, 0.262, 0.845, 0.55, 0.548, 0.566, 0.574, 0.349, 0.471, 0.326, 0.54, 0.461, 0.684, 0.481, 0.449,
        0.46, 0.525, 0.538, 0.252, 0.548, 0.54, 0.55, 0.626, 0.569, 0.392, 0.714, 0.69, 0.693,
    ];
    // the big numbers: Atkinson's figures at weight 600 (app.js BIG)
    const BK = "0123456789:.LOHI";
    const BV = [0.656, 0.43, 0.569, 0.592, 0.621, 0.602, 0.617, 0.529, 0.621, 0.578, 0.253, 0.253, 0.548, 0.737, 0.699, 0.415];

    // a label's width at [fs] (app.js tw)
    function tw(s as String, fs as Numeric) as Float {
        var t = 0.0;
        var cs = s.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            var k = WK.find(cs[i].toString());
            t += k != null ? WV[k] : 0.6;
        }
        return (t * fs * 1.03).toFloat();
    }

    // a big number's width at [fs] (app.js bw)
    function bw(s as String, fs as Numeric) as Float {
        var t = 0.0;
        var cs = s.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            var k = BK.find(cs[i].toString());
            t += k != null ? BV[k] : 0.6;
        }
        return (t * fs).toFloat();
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
