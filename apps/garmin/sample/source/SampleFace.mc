// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Background;
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

// A watch face that reads Sukrara's glucose (https://sukrara.mauri.app/developers/garmin-faces/). Its background asks
// Sukrara for Android every 5 minutes; the face draws the time and the value, working out the reading's age again at
// every draw: struck through from 5 minutes, gone from 20. Never an old value as current.

// Loaded by the background too (the fetch), hence (:background).
(:background)
class SampleApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        // every 5 minutes, Connect IQ's shortest; a registration that stands is kept
        if (Background.getTemporalEventRegisteredTime() == null) {
            Background.registerForTemporalEvent(new Time.Duration(5 * 60));
        }
        return [new SampleView()];
    }

    function getServiceDelegate() as [System.ServiceDelegate] {
        return [new SampleFetch()];
    }

    // Sukrara's answer, handed over by the background. Kept only when it's the face profile of the version this face
    // reads: another version may mean something else.
    function onBackgroundData(data as Application.PersistableType) as Void {
        if (!(data instanceof Dictionary)) {
            return;
        }
        var sent = data.get("sent");
        if (data.get("v") != 5 || !"face".equals(data.get("p")) || !(sent instanceof Number)) {
            return;
        }
        // how far the watch clock is behind the phone's: every age adds it
        var now = Time.now().value();
        data.put("skew", sent > now ? sent - now : 0);
        Application.Storage.setValue("sukrara", data);
        WatchUi.requestUpdate();
    }
}

// The fetch: Garmin Connect makes the request on the phone, where Sukrara answers on loopback.
(:background)
class SampleFetch extends System.ServiceDelegate {
    function initialize() {
        ServiceDelegate.initialize();
    }

    function onTemporalEvent() as Void {
        Communications.makeWebRequest(
            "http://127.0.0.1:17582/sukrara/face",
            null,
            {
                :method => Communications.HTTP_REQUEST_METHOD_GET,
                :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_JSON
            },
            method(:onAnswer)
        );
    }

    // 200 with the payload; anything else (Sukrara not running, no reading yet, the phone away) hands over nothing
    function onAnswer(code as Number, data as Dictionary or String or Null) as Void {
        Background.exit(code == 200 ? data : null);
    }
}

class SampleView extends WatchUi.WatchFace {
    function initialize() {
        WatchFace.initialize();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var x = dc.getWidth() / 2;
        var h = dc.getHeight();
        var center = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var clock = System.getClockTime();
        dc.drawText(x, h / 3, Graphics.FONT_NUMBER_MEDIUM, clock.hour.format("%d") + ":" + clock.min.format("%02d"), center);

        var payload = Application.Storage.getValue("sukrara");
        var glucose = payload instanceof Dictionary ? glucoseNow(payload, Time.now().value()) : null;
        if (glucose == null) {
            dc.drawText(x, h * 2 / 3, Graphics.FONT_LARGE, "---", center);
            return;
        }
        var text = glucose[0];
        var age = glucose[1];
        dc.setColor(glucose[2], Graphics.COLOR_TRANSPARENT);
        dc.drawText(x, h * 2 / 3, Graphics.FONT_LARGE, text, center);
        if (age > 5 * 60) {
            // struck through: the value is there, but it isn't current
            var half = dc.getTextWidthInPixels(text, Graphics.FONT_LARGE) / 2;
            dc.setPenWidth(3);
            dc.drawLine(x - half, h * 2 / 3, x + half, h * 2 / 3);
        }
        if (age > 90) {
            // late: say how old it is
            dc.drawText(x, h * 2 / 3 + 40, Graphics.FONT_XTINY, (age / 60).toString() + " min ago", center);
        }
    }

    // What the value says now: [its text, its age in seconds, its colour], or null with no reading or one older than
    // 20 minutes. The payload's keys: contracts/watchface/payload/README.md › The payload.
    function glucoseNow(payload as Dictionary, now as Number) as [String, Number, Number] or Null {
        var g = payload.get("g");
        if (!(g instanceof Dictionary)) {
            return null;
        }
        var v = g.get("v") as Number;
        var age = now + (payload.get("skew") as Number) - (g.get("t") as Number);
        if (age < 0) {
            age = 0;
        }
        if (age > 20 * 60) {
            return null;
        }
        var text;
        if (v < 40) {
            text = "LO";
        } else if (v > 400) {
            text = "HI";
        } else if ("mmol".equals(payload.get("unit"))) {
            text = (v / 18.0182).format("%.1f");
        } else {
            text = v.toString();
        }
        // bands: [veryLow, low, tight, high, veryHigh, extremelyHigh] in mg/dL; grey once it is struck through
        var bands = payload.get("bands") as Array<Number>;
        var colour = Graphics.COLOR_WHITE;
        if (age > 5 * 60) {
            colour = Graphics.COLOR_LT_GRAY;
        } else if (v < bands[1]) {
            colour = Graphics.COLOR_RED;
        } else if (v > bands[3]) {
            colour = Graphics.COLOR_YELLOW;
        }
        return [text, age, colour];
    }
}
