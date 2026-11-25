// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// The face's settings (SPEC.md §11): Connect IQ properties, keys and options from the generated SettingsSpec
// (contracts/settings/settings.json). The Connect IQ app edits them through settings.xml; the watch through the
// Customize menu below (hold MENU on the face › Watch face › Customize).
module AuraSettings {
    // an option's value (its index is stored); an unknown index falls back to the default
    function option(spec as Array) as String {
        var vals = spec[1] as Array;
        var i = number(spec);
        return (i >= 0 && i < vals.size() ? vals[i] : spec[2]) as String;
    }

    function number(spec as Array) as Number {
        var v = null;
        try {
            v = Properties.getValue(spec[0] as String);
        } catch (e) {
            v = null;
        }
        if (v instanceof Number) {
            return v;
        }
        var vals = spec[1] as Array?;
        return vals != null ? vals.indexOf(spec[2]) : 0;
    }

    function flag(spec as Array) as Boolean {
        var v = null;
        try {
            v = Properties.getValue(spec[0] as String);
        } catch (e) {
            v = null;
        }
        return v instanceof Boolean ? v : spec[2] as Boolean;
    }

    function set(spec as Array, value as Number or Boolean) as Void {
        Properties.setValue(spec[0] as String, value);
        AuraWatch.changed();
    }

    // the label of a setting's current value
    function sublabel(spec as Array) as String {
        if (spec[1] == null) {
            return WatchUi.loadResource(flag(spec) ? Rez.Strings.on : Rez.Strings.off) as String;
        }
        return WatchUi.loadResource(SettingsSpec.label(option(spec))) as String;
    }
}

// The watch's Customize menu: one row per setting with its current value; a row opens its options (a bool toggles).
class AuraSettingsMenu extends WatchUi.Menu2 {
    function initialize() {
        Menu2.initialize({:title => Rez.Strings.AppName});
        var all = SettingsSpec.ALL();
        for (var i = 0; i < all.size(); i++) {
            var spec = all[i] as Array;
            if (spec[1] == null) {
                addItem(new WatchUi.ToggleMenuItem(SettingsSpec.title(spec[0] as String), null, i, AuraSettings.flag(spec), {}));
            } else {
                addItem(new WatchUi.MenuItem(SettingsSpec.title(spec[0] as String), AuraSettings.sublabel(spec), i, {}));
            }
        }
    }
}

class AuraSettingsDelegate extends WatchUi.Menu2InputDelegate {
    var menu as AuraSettingsMenu;

    function initialize(m as AuraSettingsMenu) {
        Menu2InputDelegate.initialize();
        menu = m;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var i = item.getId() as Number;
        var spec = SettingsSpec.ALL()[i] as Array;
        if (spec[1] == null) {
            AuraSettings.set(spec, (item as WatchUi.ToggleMenuItem).isEnabled());
            WatchUi.requestUpdate();
            return;
        }
        var sub = new WatchUi.Menu2({:title => SettingsSpec.title(spec[0] as String)});
        var vals = spec[1] as Array;
        var cur = AuraSettings.number(spec);
        for (var n = 0; n < vals.size(); n++) {
            sub.addItem(new WatchUi.MenuItem(SettingsSpec.label(vals[n] as String), n == cur ? WatchUi.loadResource(Rez.Strings.selected) as String : null, n, {}));
        }
        sub.setFocus(cur);
        WatchUi.pushView(sub, new AuraOptionDelegate(spec, item), WatchUi.SLIDE_LEFT);
    }

    function onBack() as Void {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
    }
}

class AuraOptionDelegate extends WatchUi.Menu2InputDelegate {
    var spec as Array;
    var row as WatchUi.MenuItem;

    function initialize(s as Array, r as WatchUi.MenuItem) {
        Menu2InputDelegate.initialize();
        spec = s;
        row = r;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        AuraSettings.set(spec, item.getId() as Number);
        row.setSubLabel(AuraSettings.sublabel(spec));
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        WatchUi.requestUpdate();
    }

    function onBack() as Void {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
    }
}
