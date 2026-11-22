// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Lang;

// The glance's own copy of the payload: only what the glance draws (the reading, the pill, the sensor, its two places),
// written beside the full payload whenever one is kept (the background, the app's own fetch). The glance reads only
// this (Link.use): it has 61 KB, its code and data take 47, and the full payload (24 h of history, the treatments, the
// reports) read from storage beside a new one ran it out of memory on a Descent Mk3 43 (#217). Every dictionary read
// back from storage costs ~200 B, so the places' metrics keep only what the ring draws, the second metric only when it
// can show, the last treatment's steps only their first item's kind and time, and nothing missing gets an entry.
(:background :glance)
module GlanceCopy {
    const STORE = "linkGlance";

    function save(p as Dictionary, skew as Number) as Void {
        var c = {} as Dictionary;
        // what's missing reads as null: no entry for it
        var keys = ["v", "p", "sent", "unit", "bands", "g", "st", "sensor", "ro"];
        for (var i = 0; i < keys.size(); i++) {
            if (p.get(keys[i]) != null) {
                c.put(keys[i], p.get(keys[i]));
            }
        }
        // the glance's two places (AppInput.slots): the main metric, then the last treatment, else `else`, else the
        // second metric (only when there's no `else`)
        var e = p.get("else");
        var withElse = e instanceof Dictionary && e.get("v") != null;
        var m = p.get("m");
        if (m instanceof Array) {
            var two = [];
            for (var i = 0; i < m.size() && i < (withElse ? 1 : 2); i++) {
                two.add(only(m[i], ["k", "v", "dim", "b"]));
            }
            c.put("m", two);
        }
        if (withElse) {
            c.put("else", only(e, ["k", "v", "dim", "b"]));
        }
        if (p.get("last") != null) {
            c.put("last", steps(p.get("last")));
        }
        // [copy, skew]: an array, not a dictionary, around it
        Application.Storage.setValue(STORE, [c, skew]);
    }

    // [d] with only [keys] that aren't null (anything else as it is)
    function only(d, keys as Array<String>) {
        if (!(d instanceof Dictionary)) {
            return d;
        }
        var out = {} as Dictionary;
        for (var i = 0; i < keys.size(); i++) {
            if (d.get(keys[i]) != null) {
                out.put(keys[i], d.get(keys[i]));
            }
        }
        return out;
    }

    // the last treatment's steps (Link.treatment): each step's time and end, and its first item's kind and time (the
    // glance's place shows that one)
    function steps(s) {
        if (!(s instanceof Dictionary)) {
            return s;
        }
        var out = only(s, ["t", "until"]) as Dictionary;
        var items = s.get("items");
        if (items instanceof Array) {
            out.put("items", items.size() > 0 ? [only(items[0], ["k", "t"])] : []);
        }
        if (s.get("next") != null) {
            out.put("next", steps(s.get("next")));
        }
        return out;
    }

    function load() {
        var s = Application.Storage.getValue(STORE);
        return s instanceof Array && s.size() == 2 ? {"p" => s[0], "skew" => s[1]} : null;
    }
}
