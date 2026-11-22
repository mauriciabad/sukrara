// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: 0BSD
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.WatchUi;

// A watch face that reads Sukrara's glucose (https://sukrara.mauri.app/developers/garmin-faces/) with the SukraraFace
// barrel: its background asks Sukrara for Android every 5 minutes; the face draws the time and the value, and the
// barrel works out the reading's age again at every draw. Late from 90 s, struck through from 5 minutes, gone from 20:
// never an old value as current.

// Loaded by the background too (the fetch), hence (:background).
(:background)
class SampleApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        SukraraFace.register();
        return [new SampleView()];
    }

    function getServiceDelegate() as [System.ServiceDelegate] {
        return [new SukraraFace.Service()];
    }

    // Sukrara's answer, handed over by the background: kept when it's the payload this barrel reads and the newest
    function onBackgroundData(data as Application.PersistableType) as Void {
        if (SukraraFace.receive(data)) {
            WatchUi.requestUpdate();
        }
    }
}

class SampleView extends WatchUi.WatchFace {
    function initialize() {
        WatchFace.initialize();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var x = dc.getWidth() / 2;
        var h = dc.getHeight();
        var y = h * 2 / 3;
        var center = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var clock = System.getClockTime();
        dc.drawText(x, h / 3, Graphics.FONT_NUMBER_MEDIUM, clock.hour.format("%d") + ":" + clock.min.format("%02d"), center);

        // what Sukrara's payload says now; null until Sukrara first answers
        var r = SukraraFace.reading();
        var text = r != null ? r.text() : null;
        if (r == null || text == null) {
            // no reading, or none for 20 minutes
            dc.drawText(x, y, Graphics.FONT_LARGE, "---", center);
            return;
        }
        var struck = r.freshness == SukraraFace.STRUCK;
        // the person's own limits, never fixed numbers; grey once it is struck through
        var level = r.level() as Number;
        var colour = struck ? Graphics.COLOR_LT_GRAY : level < 0 ? Graphics.COLOR_RED : level > 0 ? Graphics.COLOR_YELLOW : Graphics.COLOR_WHITE;
        dc.setColor(colour, Graphics.COLOR_TRANSPARENT);
        dc.drawText(x, y, Graphics.FONT_LARGE, text, center);
        var half = dc.getTextWidthInPixels(text, Graphics.FONT_LARGE) / 2;
        if (struck) {
            // the value is there, but it isn't current
            dc.setPenWidth(3);
            dc.drawLine(x - half, y, x + half, y);
        } else if (r.trend()) {
            // the trend arrow, rotated by Sukrara's degrees (negative = up)
            var a = Math.toRadians(r.deg as Float);
            var ax = x + half + 14;
            dc.setPenWidth(r.doubled ? 5 : 3);
            dc.drawLine(ax - 10 * Math.cos(a), y - 10 * Math.sin(a), ax + 10 * Math.cos(a), y + 10 * Math.sin(a));
            dc.fillCircle(ax + 10 * Math.cos(a), y + 10 * Math.sin(a), 4);
        }
        if (r.freshness != SukraraFace.CURRENT) {
            // late: say how old it is
            dc.drawText(x, y + 40, Graphics.FONT_XTINY, (r.ageS / 60).toString() + " min ago", center);
        }
    }
}
