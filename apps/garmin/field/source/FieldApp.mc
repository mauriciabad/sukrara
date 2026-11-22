// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Activity;
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

// Sukrara data field (docs/47 §8, apps/garmin/README.md): the value and its age in whatever slot of an activity's data
// screen it gets, and its own background on the shared link (Sukrara's push to this app's id, the 5-minute fetch of
// /sukrara/field): a data field can't read another app's storage (docs/47 §2). Loaded by its background too.
(:background)
class FieldApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        LinkBackground.register();
        return [new FieldView()];
    }

    function getServiceDelegate() as [System.ServiceDelegate] {
        return [new LinkService()];
    }

    // a payload from the background, through Garmin; the same run also waits in storage (Link.pickUp at every draw)
    function onBackgroundData(data as Application.PersistableType) as Void {
        if (Link.take(data, Time.now().value(), false)) {
            WatchUi.requestUpdate();
        }
    }
}

// The field: redrawn every second by the activity; on the one seam layout(input) → model, draw(model).
class FieldView extends WatchUi.DataField {
    function initialize() {
        DataField.initialize();
    }

    function compute(info as Activity.Info) as Numeric or Duration or String or Null {
        return null;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var now = Time.now().value();
        Link.pickUp(now);
        PlainDraw.draw(dc, FieldLayout.layout(Readings.input(now), dc.getWidth(), dc.getHeight(),
            getBackgroundColor() == Graphics.COLOR_BLACK));
    }
}

module FieldLayout {
    // The slot [w] × [h] (one of 1–10 fields; [dark]: the activity's dark background): the value centred with the
    // largest number font that fits about half the slot's height, the age under it when there is room.
    function layout(input as ReadingInput, w as Number, h as Number, dark as Boolean) as PlainModel {
        var m = new PlainModel();
        PlainLayout.fill(m, input, WatchUi.loadResource(Rez.Strings.NoReadings) as String,
            WatchUi.loadResource(Rez.Strings.Waiting) as String);
        m.fg = dark ? Graphics.COLOR_WHITE : Graphics.COLOR_BLACK;
        m.bg = dark ? Graphics.COLOR_BLACK : Graphics.COLOR_WHITE;
        var fonts = [Graphics.FONT_NUMBER_THAI_HOT, Graphics.FONT_NUMBER_HOT, Graphics.FONT_NUMBER_MEDIUM,
            Graphics.FONT_NUMBER_MILD, Graphics.FONT_MEDIUM, Graphics.FONT_SMALL] as Array<Graphics.FontDefinition>;
        var ageH = Graphics.getFontHeight(Graphics.FONT_XTINY);
        var room = m.age != null && h >= 3 * ageH ? h - ageH : h;
        var font = Graphics.FONT_XTINY;
        for (var i = 0; i < fonts.size(); i++) {
            if (Graphics.getFontHeight(fonts[i]) <= room * 0.9) {
                font = fonts[i];
                break;
            }
        }
        m.valueFont = font;
        m.valueX = w / 2;
        m.valueY = room / 2;
        m.ageFont = Graphics.FONT_XTINY;
        m.ageX = w / 2;
        m.ageY = room + ageH / 2;
        if (room == h) {
            m.age = null; // no room for it: the value alone (the struck look still says it's old)
        }
        m.arrowSize = Graphics.getFontHeight(font) / 3;
        return m;
    }
}
