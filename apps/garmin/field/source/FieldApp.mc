// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Activity;
import Toybox.Application;
import Toybox.Attention;
import Toybox.FitContributor;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

// Sukrara data field (docs/47 §8, contracts/watchface/app/SPEC.md §9, §14.4): the value with Aura's sparkline or its
// arrow in whatever slot of an activity's data screen it gets, its own alert, glucose in the activity's file; its own
// background on the shared link (Sukrara's push to this app's id, the 5-minute fetch of /sukrara/field): a data field
// can't read another app's storage (docs/47 §2). Loaded by its background too.
(:background :fieldrun)
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

// The field: redrawn every second by the activity; on the one seam input → FieldLayout → FieldG → FieldDraw. Every
// second it also says a new alert from the phone again (its own alert, SPEC §9) and, with Record glucose on, writes the
// fresh value into the activity's file.
(:fieldrun)
class FieldView extends WatchUi.DataField {
    var _seen as Array = [] as Array; // the kinds this field has announced
    var _fit as FitContributor.Field? = null;
    var _fitMmol as Boolean = false;
    var _fitAt as Number = 0; // the reading last written (its time), -1 = the invalid value written (no fresh reading)

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
        if (p == null) {
            return null; // not set up (SPEC §4.3): no alert, nothing in the activity's file
        }
        var items = Ring.now(p, now, Link.skew);
        var fresh = Ring.fresh(items, _seen);
        _seen = Ring.kinds(items);
        if (fresh.size() > 0) {
            alert(fresh[0], now);
        }
        record(p, now);
        return null;
    }

    // a new alert: the field's own full page (on per activity: its Alerts › Connect IQ) and a vibration; it says again
    // what Sukrara rings on the phone
    function alert(it as Dictionary, now as Number) as Void {
        if (WatchUi.DataField has :showAlert) {
            try {
                showAlert(new FieldAlert(it.get("k") as String, now));
            } catch (e) {
                // an alert already shown: the vibration says it
            }
        }
        if (Attention has :vibrate) {
            Attention.vibrate([new Attention.VibeProfile(100, 400), new Attention.VibeProfile(0, 200),
                new Attention.VibeProfile(100, 400)] as Array<Attention.VibeProfile>);
        }
    }

    // the value into the activity's file once per new reading while it is fresh; once it is older (or gone) the field's
    // invalid value, so the file never carries an old value as current (FieldRecord.value)
    function record(p as Dictionary?, now as Number) as Void {
        if (_fit == null) {
            return;
        }
        var v = FieldRecord.value(p, now + Link.skew, _fitMmol);
        var at = v != null ? (p as Dictionary).get("g") as Dictionary : null;
        var t = at != null ? at.get("t") as Number : -1;
        if (t == _fitAt) {
            return;
        }
        _fitAt = t;
        try {
            (_fit as FitContributor.Field).setData(v != null ? v : FieldRecord.invalid(_fitMmol));
        } catch (e) {
            // a value the field's type refuses: the file keeps the last one
        }
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var now = Time.now().value();
        Link.pickUp(now);
        var ds = System.getDeviceSettings();
        var W = ds.screenWidth;
        var H = ds.screenHeight;
        var w = dc.getWidth();
        var h = dc.getHeight();
        var obs = getObscurityFlags();
        var at = FieldPlace.of(w, h, W, H, obs);
        var g = FieldLayout.field(new FieldInput().from(Readings.input(now)), new FieldSlot(at[0], at[1], w, h, W, H, obs));
        FieldPlace.paint(dc, g, at[0], at[1], W, H);
    }
}

// Where the slot is on the screen: Connect IQ gives a field its size and the sides the screen cuts (getObscurityFlags),
// not its place. A slot cut at the top starts at the screen's top, one cut at the bottom ends at its bottom, else it is
// centred; the same left to right.
module FieldPlace {
    function of(w as Number, h as Number, W as Number, H as Number, obs as Number) as Array<Number> {
        // DataField.OBSCURE_LEFT 1, OBSCURE_TOP 2, OBSCURE_RIGHT 4, OBSCURE_BOTTOM 8
        var l = (obs & 1) != 0;
        var r = (obs & 4) != 0;
        var t = (obs & 2) != 0;
        var b = (obs & 8) != 0;
        var x = l ? 0 : r ? W - w : (W - w) / 2;
        var y = t ? 0 : b ? H - h : (H - h) / 2;
        return [x, y];
    }

    // the model onto the slot's Dc at [x, y] on the [W] × [H] screen: black (the design's ground), then the field
    function paint(dc as Graphics.Dc, g as FieldG, x as Number, y as Number, W as Number, H as Number) as Void {
        FieldDraw.W = W;
        FieldDraw.H = H;
        FieldDraw.s = FieldFontTable.S;
        FieldDraw.ox = W / 2.0 - 0.5 - x;
        FieldDraw.oy = H / 2.0 - 0.5 - y;
        FieldDraw.px0 = W / 2 - x;
        FieldDraw.py0 = H / 2 - y;
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        FieldDraw.paint(dc, g);
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

    // what the file gets at the phone's time [t]: the reading in the person's unit while it is fresh (5 min), else
    // null
    function value(p as Dictionary?, t as Number, mmol as Boolean) as Numeric? {
        var g = p != null ? p.get("g") : null;
        if (!(g instanceof Dictionary) || t - (g.get("t") as Number) > SukraraFace.STRUCK_S) {
            return null;
        }
        var v = g.get("v") as Number;
        return mmol ? Glucose.inUnit(v, "mmol") : v;
    }

    // the FIT invalid value of the field's type (no reading): UINT16 0xFFFF, FLOAT a NaN
    function invalid(mmol as Boolean) as Numeric {
        return mmol ? Math.acos(2.0) : 0xFFFF;
    }
}

// The field's own alert (SPEC §9, DataField.showAlert): the whole page in the alert's colour, the bell and the word,
// the value + arrow, "Sukrara · now" (app.js S.dfAlert).
(:fieldrun)
class FieldAlert extends WatchUi.DataFieldAlert {
    var _kind as String;
    var _at as Number;

    function initialize(kind as String, now as Number) {
        DataFieldAlert.initialize();
        _kind = kind;
        _at = now;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var now = Time.now().value();
        var d = new FieldInput().from(Readings.input(now));
        FieldPlace.paint(dc, FieldWords.alert(d, _kind), 0, 0, dc.getWidth(), dc.getHeight());
    }
}

// The alert's words in the person's language (resources: en, es)
module FieldWords {
    function alert(d as FieldInput, kind as String) as FieldG {
        var words = {"very_low" => Rez.Strings.AlertVeryLow, "low" => Rez.Strings.AlertLow, "high" => Rez.Strings.AlertHigh,
            "very_high" => Rez.Strings.AlertVeryHigh, "extremely_high" => Rez.Strings.AlertExtremelyHigh,
            "falling_fast" => Rez.Strings.AlertFallingFast, "rising_fast" => Rez.Strings.AlertRisingFast};
        var w = words.get(kind);
        var word = (WatchUi.loadResource(w != null ? w as ResourceId : Rez.Strings.AlertNoData) as String).toUpper();
        var m = Math.round(d.age).toNumber();
        var line = d.age < 1 ? WatchUi.loadResource(Rez.Strings.Now) as String
            : Lang.format(WatchUi.loadResource(Rez.Strings.MinAgo) as String, [m]);
        return FieldLayout.alert(d, kind, word, line);
    }
}
