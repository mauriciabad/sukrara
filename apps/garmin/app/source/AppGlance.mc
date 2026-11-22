// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// The glance: the value, the arrow and the age from the app's stored payload (ReadingInput), on the one seam
// layout(input) → model, draw(model).
(:glance)
class AppGlance extends WatchUi.GlanceView {
    var _timer as Timer.Timer?;

    function initialize() {
        GlanceView.initialize();
    }

    // the age moves on with nothing new from the phone: a redraw every 30 s while shown (Garmin: under 1 Hz)
    function onShow() as Void {
        _timer = new Timer.Timer();
        (_timer as Timer.Timer).start(method(:tick), 30000, true);
    }

    function onHide() as Void {
        if (_timer != null) {
            (_timer as Timer.Timer).stop();
            _timer = null;
        }
    }

    function tick() as Void {
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var now = Time.now().value();
        Link.pickUp(now);
        PlainDraw.draw(dc, GlanceLayout.layout(Readings.input(now), dc.getWidth(), dc.getHeight()));
    }
}

(:glance)
module GlanceLayout {
    // The glance's slot [w] × [h]: the value (or the message) left on the upper half, the age under it.
    function layout(input as ReadingInput, w as Number, h as Number) as PlainModel {
        var m = new PlainModel();
        PlainLayout.fill(m, input, WatchUi.loadResource(Rez.Strings.NoReadings) as String,
            WatchUi.loadResource(Rez.Strings.Waiting) as String);
        m.bg = Graphics.COLOR_TRANSPARENT;
        m.just = Graphics.TEXT_JUSTIFY_LEFT;
        m.valueFont = Graphics.FONT_GLANCE_NUMBER;
        m.valueX = 0;
        m.valueY = m.age != null ? h * 3 / 8 : h / 2;
        m.ageFont = Graphics.FONT_GLANCE;
        m.ageX = 0;
        m.ageY = h * 13 / 16;
        m.arrowSize = h / 4;
        return m;
    }
}
