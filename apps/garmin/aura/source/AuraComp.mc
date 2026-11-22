// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Complications;
import Toybox.Lang;

// The watch's complications the shown fields need (Toybox.Complications, SPEC.md §6.10): subscribed to those only,
// their newest values kept by type. A complication this watch hasn't stays null (the field says --). The face's only
// ((:complications)): a watch-app may not subscribe, so the debug case loader builds without it (cases.jungle) and has its stub.
(:complications)
module AuraComp {
    var vals as Dictionary = {} as Dictionary;
    var _subs as String = "";

    // subscribe to these complication types and no others (a change of the fields changes them)
    function sync(types as Array) as Void {
        var key = types.toString();
        if (key.equals(_subs)) {
            return;
        }
        _subs = key;
        Complications.unsubscribeFromAllUpdates();
        Complications.registerComplicationChangeCallback(new Lang.Method(AuraComp, :changed));
        vals = {} as Dictionary;
        for (var n = 0; n < types.size(); n++) {
            try {
                var id = new Complications.Id(types[n] as Complications.Type);
                Complications.subscribeToUpdates(id);
                vals[types[n]] = Complications.getComplication(id).value;
            } catch (e) {
                // not on this watch
            }
        }
    }

    function changed(id as Complications.Id) as Void {
        try {
            var c = Complications.getComplication(id);
            vals[c.getType()] = c.value;
        } catch (e) {
            // gone
        }
    }
}
