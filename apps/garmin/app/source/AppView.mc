// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// The app's view: the value, the arrow, the age and a plain line of the history, from the app's stored payload
// (ReadingInput), on the one seam layout(input) → model, draw(model).
class AppView extends WatchUi.View {
    var _timer as Timer.Timer?;

    function initialize() {
        View.initialize();
    }

    // the age moves on with nothing new from the phone: a redraw every 10 s while shown
    function onShow() as Void {
        _timer = new Timer.Timer();
        (_timer as Timer.Timer).start(method(:tick), 10000, true);
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
        PlainDraw.draw(dc, AppLayout.layout(Readings.input(now), dc.getWidth(), dc.getHeight()));
    }
}

module AppLayout {
    // The whole screen [w] × [h]: the value centred in the upper third, the age under it, the history below.
    function layout(input as ReadingInput, w as Number, h as Number) as PlainModel {
        var m = new PlainModel();
        PlainLayout.fill(m, input, WatchUi.loadResource(Rez.Strings.NoReadings) as String,
            WatchUi.loadResource(Rez.Strings.Waiting) as String);
        m.valueFont = Graphics.FONT_NUMBER_MEDIUM;
        m.valueX = w / 2;
        m.valueY = h * 3 / 10;
        m.ageFont = Graphics.FONT_SMALL;
        m.ageX = w / 2;
        m.ageY = h * 9 / 20;
        m.arrowSize = h / 10;
        PlainLayout.line(m, input, w / 6, h * 11 / 20, w * 2 / 3, h / 4);
        return m;
    }
}
