// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;

// What Sukrara on the phone last sent: the main reading and what Sukrara decided about it (SPEC.md §3, ground rule 1:
// bands, trend, pill and sensor state are Sukrara's; the face only counts the time). Every time is absolute (epoch s,
// UTC), so the face stays right between messages: it ages the reading, counts the quiet / mute down and the sensor's
// hours by itself, with the phone out of reach too.
class AuraReading {
    var mgdl as Number = 0; // the reading, mg/dL (< 40 = LO, > 400 = HI)
    var readAt as Number = 0; // its time, epoch s
    var rate as Float? = null; // mg/dL/min, Sukrara's trend (docs/34); null = no trend
    var doubled as Boolean = false; // Sukrara's doubled arrow (TrendAngle.isDouble)
    var history as Array? = null; // readings before this one, 5 min apart, oldest first, ending 5 min before readAt;
    // null entries are gaps (at most the last 3 hours: 36)
    var status as String = "auto"; // auto (the pill shows the need), quiet (held after a treatment), muted
    var statusUntil as Number? = null; // when the quiet / mute ends, epoch s; null = no end
    var lastItems as Array? = null; // the last treatment: [[kind (rapid, long, carbs), amount (Float)], ...]
    var lastAt as Number? = null; // its time, epoch s
    var sensor as String = "ok"; // ok, warmup, almost, ending (its last 8 h), endedSending, ended, none
    var sensorStarts as Number? = null; // the sensor's start and end, epoch s (its life: the sensor rail)
    var sensorEnds as Number? = null;
    var warmFrom as Number? = null; // warm-up start and end, epoch s (warmup, almost)
    var warmUntil as Number? = null;
    var unit as String = "mgdl"; // mgdl, mmol (the person's unit in Sukrara)
    var limits as Array = [55, 70, 140, 180, 250, 300] as Array; // the person's band limits, mg/dL: very low, low,
    // very good, high, very high, extremely high
    var tir as Number? = null; // today's time in range, %

    function initialize() {}
}

// THE SEAM: the single place where the face reads its current data. The face asks at every draw; nothing else in the
// face knows where readings come from.
//
// current() is the last reading the phone sent (the shared link's stored payload, apps/garmin/shared/link, turned into
// an AuraReading by reading()), or null when the phone never sent one (the face says "No readings", the rail is
// hidden). Keep it cheap: it runs on every onUpdate (1 Hz while awake). AuraData.changed() redraws at once when a new
// reading arrives.
module AuraData {
    var stub as AuraReading? = null; // the debug case loader and the tests put a reading here

    function current() as AuraReading? {
        return stub != null ? stub : reading(Toybox.Time.now().value());
    }

    function changed() as Void {
        Toybox.WatchUi.requestUpdate();
    }

    var _cache as AuraReading? = null;
    var _cacheAt as Number = -1;
    var _cacheOf as Dictionary? = null;

    // The seam's reading at the watch time [now]: what the phone sent, its times moved onto the watch clock (Link.skew),
    // the time-bound states worked out now (README "On the watch"); null = nothing ever came. Built at most once a
    // second.
    function reading(now as Number) as AuraReading? {
        var p = Link.payload();
        if (p == null) {
            return null;
        }
        if (_cache != null && _cacheAt == now && _cacheOf == p) {
            return _cache;
        }
        var skew = Link.skew;
        var x = Link.inputs(now) as Dictionary;
        var r = new AuraReading();
        var g = p.get("g");
        if (g instanceof Dictionary) {
            r.mgdl = g.get("v") as Number;
            r.readAt = (g.get("t") as Number) - skew;
            var rate = g.get("rate");
            r.rate = rate != null ? (rate as Numeric).toFloat() : null;
            r.doubled = x.get("double") as Boolean;
            var h = p.get("hist");
            r.history = h instanceof Array && h.size() > 0 ? h : null;
        }
        r.status = x.get("status") as String;
        var st = p.get("st");
        var left = x.get("left");
        r.statusUntil = left instanceof Number ? ((st as Dictionary).get("until") as Number) - skew : null;
        var last = Link.treatment(p, now + skew);
        if (last != null) {
            var items = last.get("items") as Array;
            var out = [] as Array;
            for (var i = 0; i < items.size(); i++) {
                var it = items[i] as Dictionary;
                out.add([it.get("k"), (it.get("a") as Numeric).toFloat()]);
            }
            r.lastItems = out;
            r.lastAt = (last.get("t") as Number) - skew;
        }
        var sensor = x.get("sensor") as String;
        var age = x.get("age");
        if (sensor.equals("ended") && age != null && (age as Float) <= 5.0) {
            sensor = "endedSending"; // Sukrara: ended with a fresh or late reading keeps the value, "Ended" beside it
        } else if (sensor.equals("ok") && !(g instanceof Dictionary)) {
            sensor = "none"; // monitoring, but no reading ever: "No readings"
        }
        r.sensor = sensor;
        var s = p.get("sensor") as Dictionary;
        if (s.get("end") instanceof Number) {
            r.sensorEnds = (s.get("end") as Number) - skew;
        }
        if (s.get("start") instanceof Number) {
            r.sensorStarts = (s.get("start") as Number) - skew;
        }
        if (s.get("start") instanceof Number && s.get("ready") instanceof Number) {
            r.warmFrom = (s.get("start") as Number) - skew;
            r.warmUntil = (s.get("ready") as Number) - skew;
        }
        r.unit = p.get("unit") as String;
        var bands = p.get("bands");
        if (bands instanceof Array && bands.size() == 6) {
            r.limits = bands;
        }
        r.tir = x.get("tir") as Number?;
        _cache = r;
        _cacheAt = now;
        _cacheOf = p;
        if (g instanceof Dictionary) {
            Link.drawn(r.readAt, now);
        }
        return r;
    }
}
