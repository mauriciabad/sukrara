// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Activity;
import Toybox.Application;
import Toybox.Attention;
import Toybox.FitContributor;
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

// The field: redrawn every second by the activity; on the one seam layout(input) → model, draw(model). Every second it
// also says a new alert from the phone again (its own alert, SPEC §9) and, with Record glucose on, writes the fresh
// value into the activity's file.
class FieldView extends WatchUi.DataField {
    var _seen as Array = [] as Array; // the kinds this field has announced
    var _fit as FitContributor.Field? = null;
    var _fitMmol as Boolean = false;
    var _fitAt as Number = 0;

    function initialize() {
        DataField.initialize();
        if (FieldRecord.on()) {
            var p = Link.payload();
            _fitMmol = p != null && "mmol".equals(p.get("unit"));
            // one field in the person's unit at the activity's start (ids stable: resources fitContributions)
            _fit = createField(_fitMmol ? "glucose_mmol" : "glucose", _fitMmol ? 1 : 0,
                _fitMmol ? FitContributor.DATA_TYPE_FLOAT : FitContributor.DATA_TYPE_UINT16,
                {:mesgType => FitContributor.MESG_TYPE_RECORD, :units => _fitMmol ? "mmol/L" : "mg/dL"});
        }
    }

    function compute(info as Activity.Info) as Numeric or Duration or String or Null {
        var now = Time.now().value();
        Link.pickUp(now);
        var p = Link.payload();
        var items = Ring.now(p, now, Link.skew);
        var fresh = Ring.fresh(items, _seen);
        _seen = Ring.kinds(items);
        if (fresh.size() > 0) {
            alert(fresh[0], p as Dictionary, now);
        }
        record(p, now);
        return null;
    }

    // a new alert: the field's own full page (on per activity: its Alerts › Connect IQ) and a vibration; it says again
    // what Sukrara rings on the phone
    function alert(it as Dictionary, p as Dictionary, now as Number) as Void {
        if (WatchUi.DataField has :showAlert) {
            try {
                showAlert(new FieldAlert(it, p, now));
            } catch (e) {
                // an alert already shown: the vibration says it
            }
        }
        if (Attention has :vibrate) {
            Attention.vibrate([new Attention.VibeProfile(100, 400), new Attention.VibeProfile(0, 200),
                new Attention.VibeProfile(100, 400)] as Array<Attention.VibeProfile>);
        }
    }

    // the value into the activity's file once per new reading while it is fresh (never an old value as current)
    function record(p as Dictionary?, now as Number) as Void {
        var g = p != null ? p.get("g") : null;
        if (_fit == null || !(g instanceof Dictionary)) {
            return;
        }
        var t = g.get("t") as Number;
        if (t == _fitAt || now + Link.skew - t > Glucose.STRUCK_S) {
            return;
        }
        _fitAt = t;
        var v = g.get("v") as Number;
        (_fit as FitContributor.Field).setData(_fitMmol ? Glucose.inUnit(v, "mmol") : v);
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

// Record glucose (the field's setting, SettingsSpec): on by default
module FieldRecord {
    function on() as Boolean {
        var v = null;
        try {
            v = Application.Properties.getValue(SettingsSpec.RECORD[0] as String);
        } catch (e) {
            v = null;
        }
        return v instanceof Boolean ? v : SettingsSpec.RECORD[2] as Boolean;
    }
}

// The field's own alert (SPEC §9, DataField.showAlert): the whole page in the band's colour, the alert's word, the value
// white, "Sukrara · now". Placeholder look until the design's port.
class FieldAlert extends WatchUi.DataFieldAlert {
    var _word as String;
    var _value as String;
    var _colour as Number;

    function initialize(it as Dictionary, p as Dictionary, now as Number) {
        DataFieldAlert.initialize();
        var words = {"very_low" => Rez.Strings.AlertVeryLow, "low" => Rez.Strings.AlertLow, "high" => Rez.Strings.AlertHigh,
            "very_high" => Rez.Strings.AlertVeryHigh, "extremely_high" => Rez.Strings.AlertExtremelyHigh,
            "falling_fast" => Rez.Strings.AlertFallingFast, "rising_fast" => Rez.Strings.AlertRisingFast};
        var w = words.get(it.get("k"));
        _word = WatchUi.loadResource(w != null ? w as ResourceId : Rez.Strings.AlertNoData) as String;
        var g = p.get("g");
        var unit = p.get("unit");
        _value = g instanceof Dictionary ? Glucose.value(g.get("v") as Number, unit instanceof String ? unit : "mgdl") : "";
        var side = it.get("side");
        _colour = "low".equals(side) ? Graphics.COLOR_DK_RED : ("high".equals(side) ? Graphics.COLOR_ORANGE
            : Graphics.COLOR_DK_GRAY);
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();
        dc.setColor(Graphics.COLOR_WHITE, _colour);
        dc.clear();
        dc.drawText(w / 2, h / 4, Graphics.FONT_MEDIUM, _word, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(w / 2, h / 2, Graphics.FONT_NUMBER_HOT, _value, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(w / 2, h * 3 / 4, Graphics.FONT_XTINY, WatchUi.loadResource(Rez.Strings.Now) as String,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}
