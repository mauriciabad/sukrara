// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// The glance (S.glance): the widget's top row in the glance's area right of Sukrara's icon, on the seam
// AppInput.build → AppParts.glance (the display list) → AppDraw. The area is app.js's x −83 … 165, y −51.5 … 51.5
// (248 × 103 on the Descent Mk3 43), scaled by its height.
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
        AppDraw.release();
    }

    function tick() as Void {
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var now = Time.now().value();
        Link.pickUp(now);
        // its own small copy, never the full payload (GlanceCopy: 64 KB)
        Link.use(GlanceCopy.load());
        AppDraw.glance = true;
        AppDraw.s = dc.getHeight() / 103.0;
        AppDraw.ox = 83 * AppDraw.s - 0.5;
        AppDraw.oy = dc.getHeight() / 2.0 - 0.5;
        var g = new AppG();
        AppParts.glance(g, AppInput.build(now, false));
        // the input went with the layout; the copy goes too, so the paint (its atlases) has the room
        Link.use(null);
        AppDraw.paint(dc, g);
        // and its atlases after it: kept, the next draw (every 30 s, a new reading) ran out of memory
        AppDraw.release();
    }
}
