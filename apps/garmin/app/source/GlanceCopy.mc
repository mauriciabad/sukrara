// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Lang;

// The glance's own copy of the payload: only what the glance draws (the reading, the pill, the sensor, its two places),
// written beside the full payload whenever one is kept (the background, the app's own fetch). The glance reads only
// this (Link.use): it has 64 KB, its code takes 40, and the full payload (24 h of history, the treatments, the reports)
// read from storage beside a new one ran it out of memory on a Descent Mk3 43 (#217).
(:background :glance)
module GlanceCopy {
    const STORE = "linkGlance";

    function save(p as Dictionary, skew as Number) as Void {
        var c = {} as Dictionary;
        var keys = ["v", "p", "sent", "unit", "bands", "g", "st", "last", "sensor", "else", "ro"];
        for (var i = 0; i < keys.size(); i++) {
            c.put(keys[i], p.get(keys[i]));
        }
        // the glance's two places: the main metric, then the last treatment or else the second (AppInput.slots)
        var m = p.get("m");
        c.put("m", m instanceof Array && m.size() > 2 ? m.slice(0, 2) : m);
        Application.Storage.setValue(STORE, {"p" => c, "skew" => skew});
    }

    function load() {
        return Application.Storage.getValue(STORE);
    }
}
