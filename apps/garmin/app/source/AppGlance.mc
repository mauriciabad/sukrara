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

    // the glance's area: the 390's glance (248 × 103 units) scaled to the tighter of the content's height and width
    // (app.js glanceBox), its width in units the rest (a wider area's value and places have the room)
    static function glanceArea(w as Number, h as Number) as Void {
        var sh = h / 103.0;
        var sw = w / 248.0;
        AppDraw.scale(sh < sw ? sh : sw);
        AppDraw.gw = w / AppDraw.s;
        AppDraw.ox = 83 * AppDraw.s - 0.5;
    }

    // One thing at a time (the glance has 61 KB, its code and data take 47 before it draws): the copy, then the input
    // built from it, then the display list, then each text's atlas; nothing kept after the draw (#217)
    function onUpdate(dc as Graphics.Dc) as Void {
        var now = Time.now().value();
        Link.pickUp(now);
        // from its own small copy, never the full payload (AppInput.glance)
        var d = AppInput.glance(now);
        AppDraw.glance = true;
        AppDraw.once = true;
        glanceArea(dc.getWidth(), dc.getHeight());
        AppDraw.oy = dc.getHeight() / 2.0 - 0.5;
        var g = new AppG();
        AppParts.glance(g, d);
        d = null;
        AppDraw.paint(dc, g);
        // and its atlases after it: kept, the next draw (every 30 s, a new reading) ran out of memory
        AppDraw.release();
    }
}
