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
    // null entries are gaps (at most the last hour: 12)
    var status as String = "auto"; // auto (the pill shows the need), quiet (held after a treatment), muted
    var statusUntil as Number? = null; // when the quiet / mute ends, epoch s; null = no end
    var lastItems as Array? = null; // the last treatment: [[kind (rapid, long, carbs), amount (Float)], ...]
    var lastAt as Number? = null; // its time, epoch s
    var sensor as String = "ok"; // ok, warmup, almost, ending (its last 8 h), endedSending, ended, none
    var sensorEnds as Number? = null; // the sensor's end, epoch s (ending)
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
// For the link (the phone → watch data link, apps/garmin/README.md "The seam"): replace the body of current() with the
// last reading the phone sent (decoded from the payload, contracts/watchface/), or null when the phone never sent one
// (the face says "No readings", the rail is hidden). Keep it cheap: it runs on every onUpdate (1 Hz while awake).
// Call AuraData.changed() when a new reading arrives so the face redraws at once.
module AuraData {
    var stub as AuraReading? = null; // the debug case loader and the tests put a reading here

    function current() as AuraReading? {
        return stub != null ? stub : AuraLink.reading(Toybox.Time.now().value());
    }

    function changed() as Void {
        Toybox.WatchUi.requestUpdate();
    }
}
