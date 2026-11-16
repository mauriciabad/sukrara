// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Math;
import Toybox.Time;
import Toybox.Time.Gregorian;

// The reference's maths: rounding, the risk scale, points on the circle, label widths and the clock (aura.js).
module AuraMath {
    const NB = " ";

    // round to 0.1 px the way the reference does (JS Math.round: halves up)
    function r1(x as Numeric) as Float {
        return Math.floor(x.toFloat() * 10.0 + 0.5).toFloat() / 10.0;
    }

    function clamp(x as Numeric, a as Numeric, b as Numeric) as Float {
        var v = x.toFloat();
        return v < a ? a.toFloat() : v > b ? b.toFloat() : v;
    }

    function max(a as Numeric, b as Numeric) as Float {
        return a > b ? a.toFloat() : b.toFloat();
    }

    function min(a as Numeric, b as Numeric) as Float {
        return a < b ? a.toFloat() : b.toFloat();
    }

    function abs(a as Numeric) as Float {
        return a < 0 ? -a.toFloat() : a.toFloat();
    }

    // Kovatchev's risk transform (Diabetes Care 1997)
    function K(g as Numeric) as Float {
        return (1.509 * (Math.pow(Math.ln(g.toFloat()), 1.084) - 5.381)).toFloat();
    }

    // the level rail's angle of a glucose value: 40 at 133°, 400 at 47°
    function ang(g as Numeric) as Float {
        var k0 = K(40);
        var k1 = K(400);
        var v = clamp(g, 40, 400);
        return (133.0 - 86.0 * (K(v) - k0) / (k1 - k0)).toFloat();
    }

    // P(r, a) = (r·sin a, −r·cos a), a clockwise from 12 o'clock, to 0.1 px
    function P(r as Numeric, a as Numeric) as Array<Float> {
        var t = a.toFloat() * Math.PI / 180.0;
        return [r1(r * Math.sin(t)), r1(-r * Math.cos(t))];
    }

    // Atkinson's advance widths per em (aura.js widths()); an unknown glyph counts 0.6
    const WK = " 0123456789.:·ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzáéíóúñÁÉÍÓÚÑ";
    const WV = [
        0.28, 0.648, 0.407, 0.548, 0.576, 0.614, 0.588, 0.598, 0.51, 0.615, 0.595, 0.21, 0.21, 0.24, 0.626, 0.613, 0.645,
        0.669, 0.569, 0.552, 0.694, 0.694, 0.392, 0.512, 0.631, 0.541, 0.824, 0.693, 0.714, 0.592, 0.724, 0.614, 0.592,
        0.558, 0.69, 0.593, 0.856, 0.623, 0.575, 0.607, 0.525, 0.566, 0.485, 0.564, 0.538, 0.314, 0.558, 0.55, 0.252,
        0.245, 0.492, 0.262, 0.845, 0.55, 0.548, 0.566, 0.574, 0.349, 0.471, 0.326, 0.54, 0.461, 0.684, 0.481, 0.449,
        0.46, 0.525, 0.538, 0.252, 0.548, 0.54, 0.55, 0.626, 0.569, 0.392, 0.714, 0.69, 0.693,
    ];

    // a label's width at a font size: Atkinson's advances x size x 1.03 (SPEC.md §2)
    function tw(s as String, size as Numeric) as Float {
        var t = 0.0;
        var cs = s.toCharArray();
        for (var i = 0; i < cs.size(); i++) {
            var c = cs[i];
            if (c == ' ') {
                t += 0.28;
                continue;
            }
            var k = WK.find(c.toString());
            t += k != null ? WV[k] : 0.6;
        }
        return (t * size * 1.03).toFloat();
    }

    // the clock: a moment's local fields (the tests' fixed clock is UTC)
    function info(epoch as Number, utc as Boolean) as Gregorian.Info {
        var m = new Time.Moment(epoch);
        return utc ? Gregorian.utcInfo(m, Time.FORMAT_SHORT) : Gregorian.info(m, Time.FORMAT_SHORT);
    }

    // H:MM, no leading zero; 12 h without a mark (SPEC.md §6.3)
    function clock(i as Gregorian.Info, is24 as Boolean) as String {
        return hm(i.hour as Number, i.min as Number, is24);
    }

    function hm(h as Number, m as Number, is24 as Boolean) as String {
        var hh = h;
        if (!is24) {
            hh = h % 12;
            if (hh == 0) {
                hh = 12;
            }
        }
        return hh.toString() + ":" + m.format("%02d");
    }
}
