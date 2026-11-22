// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.WatchUi;

// THE PLACEHOLDER LOOK of the app's surfaces and the data field (apps/garmin/README.md "One seam"), deliberately plain
// until the design (contracts/watchface/app/SPEC.md) is ported: one model each surface's layout fills
// (layout(input) → PlainModel) and one drawing of it (PlainDraw.draw). The port replaces this folder's drawing and the
// layouts' positions; the input (ReadingInput) and what it means (Glucose: the unit, the freshness steps) stay.
(:glance)
class PlainModel {
    // what is shown
    var value as String? = null; // the reading in the person's unit, or null (then message)
    var message as String? = null; // "No readings" (old or none), "Waiting for Sukrara" (nothing ever came)
    var struck as Boolean = false; // from 5 min: struck through and grey
    var arrow as Boolean = false; // the trend arrow (deg, doubled)
    var deg as Float = 0.0; // its rotation, degrees (negative = up, 0 = flat)
    var doubled as Boolean = false;
    var age as String? = null; // the reading's age, from 90 s
    var line as Array<Number>? = null; // the history: x0, y0, x1, y1, … in pixels; null = none
    // where (each surface's layout decides)
    var fg as Number = Graphics.COLOR_WHITE;
    var bg as Number = Graphics.COLOR_BLACK;
    var valueFont as Graphics.FontType = Graphics.FONT_NUMBER_MEDIUM;
    var valueX as Number = 0; // the value's (or the message's) centre / left edge, see just
    var valueY as Number = 0; // its vertical centre
    var just as Number = Graphics.TEXT_JUSTIFY_CENTER; // the value's and the age's alignment
    var ageFont as Graphics.FontType = Graphics.FONT_XTINY;
    var ageX as Number = 0;
    var ageY as Number = 0; // the age's vertical centre
    var arrowSize as Number = 12; // the arrow's length, drawn right after the value

    function initialize() {}
}

(:glance)
module PlainLayout {
    // What [input] shows, the freshness steps applied (contracts/graph/freshness): the value or a message, struck from
    // 5 min, the age from 90 s, the arrow only on a fresh or late reading with a trend. [noReadings] / [waiting]: the
    // app's own words (its strings).
    function fill(m as PlainModel, input as ReadingInput, noReadings as String, waiting as String) as Void {
        if (!input.payload) {
            m.message = waiting;
            return;
        }
        var mgdl = input.mgdl;
        if (mgdl == null || !Glucose.shown(input.ageS)) {
            m.message = noReadings;
            return;
        }
        m.value = Glucose.value(mgdl, input.unit);
        m.struck = Glucose.struck(input.ageS);
        m.arrow = !m.struck && input.deg != null;
        m.deg = input.deg != null ? input.deg as Float : 0.0;
        m.doubled = input.doubled;
        m.age = Glucose.aged(input.ageS) ? Glucose.age(input.ageS) : null;
    }

    // The history as a line in the box [x, y, w, h]: oldest at the left, the reading at the right edge, 40–300 mg/dL
    // bottom to top (clamped); gaps skipped. None from 3 h old (the freshness steps) or with under two points.
    function line(m as PlainModel, input as ReadingInput, x as Number, y as Number, w as Number, h as Number) as Void {
        var mgdl = input.mgdl;
        if (mgdl == null || !Glucose.chart(input.ageS)) {
            return;
        }
        var hist = input.history;
        var n = hist.size() + 1;
        if (n < 2) {
            return;
        }
        var pts = [] as Array<Number>;
        for (var i = 0; i < n; i++) {
            var v = i < n - 1 ? hist[i] : mgdl;
            if (!(v instanceof Number)) {
                continue;
            }
            var c = v < 40 ? 40 : (v > 300 ? 300 : v);
            pts.add(x + (w * i) / (n - 1));
            pts.add(y + h - (h * (c - 40)) / 260);
        }
        m.line = pts.size() >= 4 ? pts : null;
    }
}

(:glance)
module PlainDraw {
    function draw(dc as Graphics.Dc, m as PlainModel) as Void {
        if (m.bg != Graphics.COLOR_TRANSPARENT) {
            dc.setColor(m.fg, m.bg);
            dc.clear();
        }
        var grey = m.struck ? Graphics.COLOR_LT_GRAY : m.fg;
        var line = m.line;
        if (line != null) {
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            for (var i = 2; i + 1 < line.size(); i += 2) {
                dc.drawLine(line[i - 2], line[i - 1], line[i], line[i + 1]);
            }
        }
        var center = Graphics.TEXT_JUSTIFY_VCENTER;
        var value = m.value;
        if (value == null) {
            var msg = m.message != null ? m.message as String : "";
            var font = dc.getTextWidthInPixels(msg, Graphics.FONT_SMALL) < dc.getWidth() * 0.8
                ? Graphics.FONT_SMALL : Graphics.FONT_XTINY;
            dc.setColor(m.fg, Graphics.COLOR_TRANSPARENT);
            dc.drawText(m.valueX, m.valueY, font, msg, m.just | center);
            return;
        }
        dc.setColor(grey, Graphics.COLOR_TRANSPARENT);
        dc.drawText(m.valueX, m.valueY, m.valueFont, value, m.just | center);
        var tw = dc.getTextWidthInPixels(value, m.valueFont);
        var left = m.just == Graphics.TEXT_JUSTIFY_LEFT
            ? m.valueX
            : (m.just == Graphics.TEXT_JUSTIFY_RIGHT ? m.valueX - tw : m.valueX - tw / 2);
        if (m.struck) {
            dc.setPenWidth(2);
            dc.drawLine(left, m.valueY, left + tw, m.valueY);
            dc.setPenWidth(1);
        }
        if (m.arrow) {
            arrow(dc, left + tw + m.arrowSize / 2 + 4, m.valueY, m.arrowSize, m.deg, m.doubled);
        }
        var age = m.age;
        if (age != null) {
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(m.ageX, m.ageY, m.ageFont, age, m.just | center);
        }
    }

    // A plain arrow centred at [cx, cy], [size] long, pointing right turned by [deg] (negative = up); doubled: two heads.
    function arrow(dc as Graphics.Dc, cx as Number, cy as Number, size as Number, deg as Float, doubled as Boolean) as Void {
        var a = Math.toRadians(deg);
        var c = Math.cos(a);
        var s = Math.sin(a);
        var half = size / 2.0;
        dc.setPenWidth(2);
        dc.drawLine(cx - c * half, cy - s * half, cx + c * half, cy + s * half);
        var heads = doubled ? 2 : 1;
        for (var k = 0; k < heads; k++) {
            var tx = cx + c * (half - k * size / 3.0);
            var ty = cy + s * (half - k * size / 3.0);
            for (var side = -1; side <= 1; side += 2) {
                var b = a + Math.PI + side * 0.6;
                dc.drawLine(tx, ty, tx + Math.cos(b) * size / 3.0, ty + Math.sin(b) * size / 3.0);
            }
        }
        dc.setPenWidth(1);
    }
}
