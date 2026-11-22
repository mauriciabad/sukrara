// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;

// What Sukrara's alert runtime rings on the phone, as the watch apps take it (contracts/watchface/payload `ring`, README
// "On the watch"): the payload's ring items while its reading is fresh (or the item is no_data), and which of them are
// new since the kinds an app last announced. Sukrara decides what rings; the watch only says it again, on the wrist.
(:background)
module Ring {
    // the ring items at the watch time [now] ([skew]: the phone's clock ahead of the watch's): none once the reading has
    // aged past fresh, except no_data (README: the phone's next message says what rings)
    function now(p as Dictionary?, now as Number, skew as Number) as Array<Dictionary> {
        var out = [] as Array<Dictionary>;
        if (p == null || !(p.get("ring") instanceof Array)) {
            return out;
        }
        var g = p.get("g");
        var fresh = g instanceof Dictionary && now + skew - (g.get("t") as Number) <= Glucose.STRUCK_S;
        var items = p.get("ring") as Array;
        for (var i = 0; i < items.size(); i++) {
            var it = items[i];
            if (it instanceof Dictionary && (fresh || "no_data".equals(it.get("k")))) {
                out.add(it);
            }
        }
        return out;
    }

    // the kinds of [items]
    function kinds(items as Array<Dictionary>) as Array<String> {
        var out = [] as Array<String>;
        for (var i = 0; i < items.size(); i++) {
            out.add(items[i].get("k") as String);
        }
        return out;
    }

    // the items whose kind isn't in [seen]: a kind that appears is a new alert (one that goes was answered, muted or
    // ended, so it rings again when it comes back)
    function fresh(items as Array<Dictionary>, seen as Array) as Array<Dictionary> {
        var out = [] as Array<Dictionary>;
        for (var i = 0; i < items.size(); i++) {
            if (seen.indexOf(items[i].get("k")) < 0) {
                out.add(items[i]);
            }
        }
        return out;
    }

    // what logging answers this side's alert: carbs for a low, rapid insulin for a high, nothing for no data
    function treatment(side) as String? {
        return "low".equals(side) ? "carbs" : ("high".equals(side) ? "rapid" : null);
    }
}
