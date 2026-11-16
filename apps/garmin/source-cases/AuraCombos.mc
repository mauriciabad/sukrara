// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.System;

// SPEC.md §9: "the battery label never meets the value or a message, whatever the width (sparkline, mmol/L 22.2, LO /
// HI), language, battery text (9d, 14h, 86%, both) or top field". Every combination of those, laid out by the port,
// checked: the label (left of the screen, under the time) and the value's row (the value with its arrow or sparkline,
// or a message's first line) never overlap (8 px apart, or the label wholly above the row). The case loader runs them after
// the cases, a few per timer tick; garmin_test.py reads the "COMBOS" line. Debug only.
(:debug)
module AuraCombos {
    // the row: [glucose, unit, trend style, sensor, age (min)] (the widest values, LO / HI, every message)
    const ROWS = [
        [112, "mgdl", "arrow", "ok", 0.5], [112, "mgdl", "spark", "ok", 0.5], [400, "mgdl", "arrow", "ok", 0.5],
        [400, "mgdl", "spark", "ok", 0.5], [399.7, "mmol", "arrow", "ok", 0.5], [399.7, "mmol", "spark", "ok", 0.5],
        [180.2, "mmol", "spark", "ok", 0.5], [30, "mgdl", "arrow", "ok", 0.5], [450, "mgdl", "spark", "ok", 0.5],
        [104, "mgdl", "spark", "ok", 3.2], [104, "mgdl", "arrow", "ok", 9], [250, "mgdl", "spark", "ok", 0.5],
        [112, "mgdl", "arrow", "warmup", 0.5], [112, "mgdl", "arrow", "ended", 0.5], [112, "mgdl", "arrow", "none", 0.5],
        [112, "mgdl", "arrow", "ok", 34], [112, "mgdl", "arrow", "ok", 900], [112, "mgdl", "arrow", "endedSending", 0.5],
    ];
    const LANGS = ["en", "es"];
    // battery: [label setting, days]
    const BATT = [["days", 8.6], ["days", 0.6], ["percent", 8.6], ["both", 8.6], ["both", 0.6], ["percent", 1.6]];
    const TOP = ["weather", "none", "tz2", "altitude"];
    const STATUS = ["auto", "muted"];
    const PRIV = ["open", "private"];

    function count() as Number {
        return ROWS.size() * LANGS.size() * BATT.size() * TOP.size() * STATUS.size() * PRIV.size();
    }

    // combination n's input
    function input(n as Number) as AuraInput {
        var i = new AuraInput();
        i.utc = true;
        i.now = 1794823776; // Mon 16 Nov 2026 10:09:36 UTC
        var r = ROWS[n % ROWS.size()] as Array;
        n /= ROWS.size();
        i.glucose = (r[0] as Numeric).toFloat();
        i.unit = r[1] as String;
        i.trendStyle = r[2] as String;
        i.sensor = r[3] as String;
        i.age = (r[4] as Numeric).toFloat();
        i.lang = LANGS[n % LANGS.size()] as String;
        n /= LANGS.size();
        var b = BATT[n % BATT.size()] as Array;
        n /= BATT.size();
        i.battLabel = b[0] as String;
        i.battery = (b[1] as Numeric).toFloat();
        i.topField = TOP[n % TOP.size()] as String;
        n /= TOP.size();
        i.status = STATUS[n % STATUS.size()] as String;
        n /= STATUS.size();
        i.privacy = PRIV[n % PRIV.size()] as String;
        return i;
    }

    // null when clear, else what's wrong
    // (the time is not checked here: with no top field and a pill its last size set is wider than its own fit margin,
    // halfAt(tTop), in the reference too; it stays inside the screen)
    function check(m as AuraModel) as String? {
        if (m.aod) {
            return null;
        }
        // the label's box
        var lines = m.battL2.equals("") ? 1 : 2;
        var top = m.by1 - m.battFs * 0.668;
        var bottom = m.by1 + (lines == 2 ? m.battFs * 0.668 + 9 : 0);
        var w = AuraMath.tw(m.battL1, m.battFs);
        if (lines == 2) {
            w = AuraMath.max(w, AuraMath.tw(m.battL2, m.battFs));
        }
        var right = -166 + w;
        // the row's left edge and top
        var left;
        if (m.hasValue) {
            left = -m.total / 2;
        } else if ("warm".equals(m.msgKind)) {
            left = -(46 + AuraMath.tw(m.msgT1, 32)) / 2;
        } else if ("ended".equals(m.msgKind)) {
            left = -(38 + AuraMath.tw(m.msgT1, 32)) / 2;
        } else if ("none".equals(m.msgKind)) {
            left = -AuraMath.tw(m.msgT1, 30) / 2;
        } else {
            left = -AuraMath.tw(m.msgT1, 25) / 2;
        }
        var clearBelow = bottom <= m.vTop + 0.05;
        var clearBeside = left >= right + 8 - 0.05;
        if (!clearBelow && !clearBeside) {
            return "label " + m.battL1 + "/" + m.battL2 + " right " + right.format("%.1f") + " bottom " + bottom.format("%.1f")
                + ", row left " + left.format("%.1f") + " top " + m.vTop.format("%.1f");
        }
        if (top < m.tBase - 0.05) {
            return "label above the time's baseline";
        }
        return null;
    }

    // runs combinations from..to-1; prints each failure; returns the failures
    function run(from as Number, to as Number) as Number {
        var bad = 0;
        for (var n = from; n < to && n < count(); n++) {
            var why = check(new AuraModel(input(n)));
            if (why != null) {
                bad++;
                System.println("COMBO_FAIL|" + n + "|" + why);
            }
        }
        return bad;
    }
}
