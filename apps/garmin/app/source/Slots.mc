// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Background;
import Toybox.Complications;
import Toybox.Lang;
import Toybox.Time;

// THE COMPLICATIONS the app publishes for any face (contracts/watchface/app/SPEC.md §10, resources/complications):
// index = the complication id in resources/complications/complications.xml, stable across versions. Values are Strings
// of Latin letters and digits only (Garmin's advice; the system fonts draw no arrows: the trend is in CGM-GB-data's
// ranges). Published from the background on every run (faces update with the app closed) and, guarded, from the app.
(:background)
module Slots {
    // the slot table: id → what it publishes (the long labels are CGM+ GB's for the glucose ones, so its faces find them)
    const VALUE = 0; // CGM-GB-value: the value
    const DATA = 1; // CGM-GB-data: the value; ranges [unit, trend, low, high, very low, very high, time, old, too old]
    const GRAPH = 2; // CGM-GB-graph: the value; ranges 288 values, 5 min apart, newest first, 0 = none
    const LOG = 3; // Sukrara log: the watch's last treatment (SPEC §8), or "Log"
    const OLD_S = 300;
    const TOO_OLD_S = 1200;
    const GRAPH_N = 288;

    function publish(now as Number) as Void {
        if (!(Toybox has :Complications) || !(Complications has :updateComplication)) {
            return;
        }
        var p = Link.payload();
        try {
            var v = value(p, now);
            Complications.updateComplication(VALUE, {:value => v});
            Complications.updateComplication(DATA, {:value => v, :ranges => data(p, now)});
            Complications.updateComplication(GRAPH, {:value => v, :ranges => graph(p)});
            Complications.updateComplication(LOG, {:value => log(p, now, logShows())});
        } catch (e) {
            // a watch that refuses a publish (docs/47 §3: reported once in the simulator): the next run tries again
        }
    }

    // the reading's time on the watch clock, null = none
    function readAt(p as Dictionary?) as Number? {
        var g = p != null ? p.get("g") : null;
        return g instanceof Dictionary ? (g.get("t") as Number) - Link.skew : null;
    }

    // "175", "9.7", "LO", "HI"; "---" when old (5 min), with no reading, or while the sensor warms up
    function value(p as Dictionary?, now as Number) as String {
        var at = readAt(p);
        if (at == null || now - at >= OLD_S) {
            return "---";
        }
        var x = Link.inputs(now);
        var s = x != null ? x.sensor : null;
        if ("warmup".equals(s) || "almost".equals(s)) {
            return "---";
        }
        return Glucose.value((p as Dictionary).get("g").get("v") as Number, unitOf(p));
    }

    function unitOf(p as Dictionary?) as String {
        var u = p != null ? p.get("unit") : null;
        return u instanceof String ? u : "mgdl";
    }

    // CGM+ GB's trend code from THE trend's band (−3 … 3): 0 none, 1 doubled up … 4 flat … 7 doubled down, 9 LO / HI
    function trend(g as Dictionary?) as Number {
        if (g == null) {
            return 0;
        }
        var v = g.get("v") as Number;
        if (v < 40 || v > 400) {
            return 9;
        }
        var b = g.get("band");
        return b instanceof Number ? 4 - b : 0;
    }

    function data(p as Dictionary?, now as Number) as Array<Float> {
        var unit = unitOf(p);
        var b = p != null && p.get("bands") instanceof Array ? p.get("bands") as Array : [55, 70, 140, 180, 250, 300];
        var at = readAt(p);
        return [unit.equals("mmol") ? 1.0 : 0.0, trend(p != null ? p.get("g") as Dictionary? : null).toFloat(),
            Glucose.inUnit(b[1] as Number, unit), Glucose.inUnit(b[3] as Number, unit), Glucose.inUnit(b[0] as Number, unit),
            Glucose.inUnit(b[4] as Number, unit), at != null ? earlier(at) : 0.0, at != null ? earlier(at + OLD_S) : 0.0,
            at != null ? earlier(at + TOO_OLD_S) : 0.0] as Array<Float>;
    }

    // An epoch second as CGM+ GB's Float: the largest 32-bit Float not after [t] (exact only to 128 s there), so a
    // face marks a value old early, never late, and its age is never younger than the truth (never old as current).
    function earlier(t as Number) as Float {
        var e = 0;
        var v = t;
        while (v >= 16777216) {
            v = v >> 1;
            e += 1;
        }
        return ((t >> e) << e).toFloat();
    }

    // the last 24 h every 5 min, newest first: the reading, then the history from its end; 0 = no reading there
    function graph(p as Dictionary?) as Array<Float> {
        var out = new [GRAPH_N] as Array<Float>;
        var unit = unitOf(p);
        var g = p != null ? p.get("g") : null;
        var h = p != null && p.get("hist") instanceof Array ? p.get("hist") as Array : [] as Array;
        for (var i = 0; i < GRAPH_N; i++) {
            var v = null;
            if (g instanceof Dictionary) {
                v = i == 0 ? g.get("v") : (h.size() - i >= 0 ? h[h.size() - i] : null);
            }
            out[i] = v instanceof Number ? Glucose.inUnit(v, unit) : 0.0;
        }
        return out;
    }

    // what the Log complication shows (the watch setting Log complication shows: since / amount / both)
    function logShows() as String {
        var spec = SettingsSpec.LOG_SLOT();
        var i = null;
        try {
            i = Application.Properties.getValue(spec[0] as String);
        } catch (e) {
            i = null;
        }
        var values = spec[1] as Array<String>;
        return i instanceof Number && i >= 0 && i < values.size() ? values[i] : spec[2] as String;
    }

    // THE WATCH'S LAST TREATMENT (SPEC §8, the card's own rule: payload `last`): the first step of the chain still
    // ahead at the phone's time, its first item {k, a, t, m}; null = none
    function last(p as Dictionary?, now as Number) as Dictionary? {
        if (p == null) {
            return null;
        }
        var step = SukraraFace.treatment(p, now + Link.skew);
        var items = step != null ? step.get("items") : null;
        return items instanceof Array && items.size() > 0 ? items[0] as Dictionary : null;
    }

    // the Log complication: [shows] since "1h 5m", amount "4.5u", both "4.5u 1h5m"; "Log" without a last treatment
    function log(p as Dictionary?, now as Number, shows as String) as String {
        var it = last(p, now);
        if (it == null) {
            return "Log";
        }
        var a = Outbox.amount(it.get("k") as String, (it.get("a") as Numeric).toFloat())
            + ("carbs".equals(it.get("k")) ? "g" : "u");
        var since = now + Link.skew - (it.get("t") as Number);
        if (shows.equals("amount")) {
            return a;
        }
        if (shows.equals("both")) {
            return a + " " + Durations.short(since, "");
        }
        return Durations.short(since, " ");
    }
}

// Sukrara's short durations: "12m", "1h 5m", "7h" (minutes, then hours and minutes; [gap] between them)
(:background :glance)
module Durations {
    function short(s as Number, gap as String) as String {
        var m = (s < 0 ? 0 : s) / 60;
        if (m < 60) {
            return m + "m";
        }
        return (m / 60) + "h" + (m % 60 == 0 ? "" : gap + (m % 60) + "m");
    }
}

// THE EXPIRY (SPEC §10: never old as current): the next background run is at the reading's time + 5 min, when the
// complications must turn "---", phone or not; never sooner than 5 min after the last temporal run (Garmin's floor), and
// never more than 5 min after this run (so the fetch keeps its rhythm while the pushes don't come).
(:background)
module Expiry {
    function next(lastRun as Number?, readAt as Number?, now as Number) as Number {
        var at = readAt != null && readAt + Slots.OLD_S > now ? readAt + Slots.OLD_S : now + Slots.OLD_S;
        var floor = lastRun != null ? lastRun + Slots.OLD_S : 0;
        return at > floor ? at : floor;
    }

    // THE REGISTRATION: what a background run or the app's start registers, given what's registered [reg]
    // (LinkBackground.registered: null, -1 the Duration, a Moment's epoch), the last temporal run, the reading's time
    // and now. The tries in order, each a Moment's epoch or -1 for the 5-minute Duration; none = the right Moment
    // stands. A Moment runs once, so the background is never left without one: the expiry; when the watch refuses it
    // (within 5 min of its own last run, which it doesn't always tell: null once the app started) 5 min from now, which
    // no past run is within; last the Duration, which repeats on its own (#217).
    function plan(reg as Number?, lastRun as Number?, readAt as Number?, now as Number) as Array<Number> {
        var at = next(lastRun, readAt, now);
        if (reg != null && reg == at) {
            return [] as Array<Number>;
        }
        return (at == now + Slots.OLD_S ? [at, -1] : [at, now + Slots.OLD_S, -1]) as Array<Number>;
    }

    // [readAt]: the kept reading's time on the watch clock (Slots.readAt)
    function schedule(now as Number, readAt as Number?) as Void {
        var last = Background.getLastTemporalEventTime();
        var tries = plan(LinkBackground.registered(), last != null ? last.value() : null, readAt, now);
        Trace.log("expiry: last run " + (last != null ? last.value() : "none") + ", registers " + tries);
        for (var i = 0; i < tries.size(); i++) {
            var t = tries[i];
            try {
                Background.registerForTemporalEvent(t == -1 ? new Time.Duration(Slots.OLD_S) : new Time.Moment(t));
            } catch (e) {
                // refused: the next try
                Trace.log("expiry: " + t + " refused, " + e.getErrorMessage());
            }
            if (!LinkBackground.stale(LinkBackground.registered(), now)) {
                return;
            }
        }
    }
}
