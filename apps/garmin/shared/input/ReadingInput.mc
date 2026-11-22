// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;

// THE INPUT MODEL of the app's surfaces and the data field (apps/garmin/README.md "One seam"): everything they draw from,
// filled from the stored payload (Link) and the watch clock, nothing else. Each surface then runs layout(input) → model
// and draw(model); the design's port replaces only the drawing. Every time is the watch clock's (Link.skew applied).
(:glance)
class ReadingInput {
    var now as Number = 0; // the watch clock, epoch s
    var payload as Boolean = false; // the phone ever sent this app anything
    var mgdl as Number? = null; // the newest reading, mg/dL (< 40 LO, > 400 HI); null = none
    var unit as String = "mgdl"; // mgdl, mmol
    var readAt as Number = 0; // its time, epoch s
    var ageS as Number = 0; // its age at now, s
    var deg as Float? = null; // the trend arrow's rotation (negative = up); null = no trend
    var doubled as Boolean = false; // the doubled arrow
    var history as Array = [] as Array; // the readings before it, 5 min apart, oldest first (null = a gap)
    var bands as Array = [55, 70, 140, 180, 250, 300] as Array; // the person's limits, mg/dL
    var status as String = "none"; // the pill: none, auto, quiet, muted
    var ring as Array = [] as Array; // what rings now on the phone: [{k, side}] (app and field profiles)

    function initialize() {}
}

(:glance)
module Readings {
    // The input at the watch time [now] (epoch s).
    function input(now as Number) as ReadingInput {
        var r = new ReadingInput();
        r.now = now;
        var p = Link.payload();
        var x = Link.inputs(now);
        if (p == null || x == null) {
            return r;
        }
        r.payload = true;
        var u = p.get("unit");
        r.unit = u instanceof String ? u : "mgdl";
        var b = p.get("bands");
        if (b instanceof Array && b.size() == 6) {
            r.bands = b;
        }
        r.status = x.status;
        var ring = p.get("ring");
        if (ring instanceof Array) {
            r.ring = ring;
        }
        var g = p.get("g");
        if (g instanceof Dictionary) {
            r.mgdl = g.get("v") as Number;
            r.readAt = (g.get("t") as Number) - Link.skew;
            r.ageS = now > r.readAt ? now - r.readAt : 0;
            var deg = g.get("deg");
            r.deg = deg != null ? (deg as Numeric).toFloat() : null;
            r.doubled = x.doubled;
            var h = p.get("hist");
            if (h instanceof Array) {
                r.history = h;
            }
        }
        return r;
    }
}
