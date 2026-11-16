// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.System;

// SPEC.md §9: "the side labels never meet the value or a message, whatever the width (sparkline, mmol/L 22.2, LO /
// HI), language, battery text (9d, 14h, 86%, both), sensor text, marks, the side they're on or the top field". Every
// combination of those, laid out by the port, checked: each label (under the time, on its side) and the value's row
// (the value with its arrow or sparkline, or a message's first line; centred, so the right side mirrors the left) never
// overlap (8 px apart, or the label wholly above the row). The case loader runs them after
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
        [118, "mgdl", "spark", "ending", 0.5],
    ];
    const LANGS = ["en", "es"];
    // battery and sensor: [the battery label setting, its days, the sensor's hours left]
    const BATT = [["days", 8.6, 225.6], ["days", 0.6, 14], ["percent", 8.6, 225.6], ["both", 8.6, 14], ["both", 0.6, 225.6], ["percent", 1.6, 5]];
    // the sides: each label on either side, and both together (their marks)
    const SIDES = [["battery", "level"], ["level", "battery"], ["battery", "sensor"], ["sensor", "battery"]];
    const TOP = ["weather", "none", "tz2", "altitude"];
    const STATUS = ["auto", "muted"];
    const PRIV = ["open", "private"];

    function count() as Number {
        return ROWS.size() * LANGS.size() * BATT.size() * TOP.size() * STATUS.size() * PRIV.size() * SIDES.size();
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
        i.sensorLeft = (b[2] as Numeric).toFloat();
        i.topField = TOP[n % TOP.size()] as String;
        n /= TOP.size();
        i.status = STATUS[n % STATUS.size()] as String;
        n /= STATUS.size();
        i.privacy = PRIV[n % PRIV.size()] as String;
        n /= PRIV.size();
        var sd = SIDES[n % SIDES.size()] as Array;
        i.sideL = sd[0] as String;
        i.sideR = sd[1] as String;
        return i;
    }

    // null when clear, else what's wrong
    // (the time is not checked here: with no top field and a pill its last size set is wider than its own fit margin,
    // halfAt(tTop), in the reference too; it stays inside the screen)
    function check(m as AuraModel) as String? {
        if (m.aod) {
            return null;
        }
        // the row's edges: a message is centred; the value's row runs from x0 to the sparkline's now (the arrow: centred)
        var left;
        if (m.hasValue) {
            left = m.x0; // the value's left edge (the sparkline's row spans the fits' width, SPEC.md §6.5)
        } else if ("warm".equals(m.msgKind)) {
            left = -(46 + AuraMath.tw(m.msgT1, 32)) / 2;
        } else if ("ended".equals(m.msgKind)) {
            left = -(38 + AuraMath.tw(m.msgT1, 32)) / 2;
        } else if ("none".equals(m.msgKind)) {
            left = -AuraMath.tw(m.msgT1, 30) / 2;
        } else {
            left = -AuraMath.tw(m.msgT1, 25) / 2;
        }
        var right = m.hasValue && m.spark ? m.sx + m.SW : -left;
        var ks = [m.sideL, m.sideR];
        for (var i = 0; i < 2; i++) {
            var k = ks[i] as String;
            var l1 = "";
            var l2 = "";
            var fs = 22;
            var y = 0.0;
            if (k.equals("battery")) {
                l1 = m.battL1;
                l2 = m.battL2;
                fs = m.battFs;
                y = m.by1;
            } else if (k.equals("sensor") && m.sensOn) {
                l1 = m.sensT;
                fs = m.sensFs;
                y = m.sy1;
            }
            if (l1.equals("")) {
                continue;
            }
            // the label's box: its widest line and its mark
            var w = AuraMath.tw(l1, fs);
            if (!l2.equals("")) {
                w = AuraMath.max(w, AuraMath.tw(l2, fs));
            }
            w += m.mkW;
            var top = y - fs * 0.668;
            var bottom = y + (l2.equals("") ? 0 : fs * 0.668 + 9);
            var clearBelow = bottom <= m.vTop + 0.05;
            var clearBeside = i == 0 ? left >= -166 + w + 8 - 0.05 : right <= 166 - w - 8 + 0.05;
            if (!clearBelow && !clearBeside) {
                return (i == 0 ? "left " : "right ") + k + " label " + l1 + "/" + l2 + " width " + w.format("%.1f") + " bottom " + bottom.format("%.1f")
                    + ", row left " + left.format("%.1f") + " top " + m.vTop.format("%.1f");
            }
            if (top < m.tBase - 0.05) {
                return "label above the time's baseline";
            }
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
