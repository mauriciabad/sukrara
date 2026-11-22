// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Test;

// THE SEAM: what AuraData.current() hands the face, from the shared link's payload (SukraraFaceCases).
(:test)
function linkSeamReading(logger as Logger) as Boolean {
    var cases = SukraraFaceCases.cases();
    var now = SukraraFaceCases.NOW;
    Link.reset();
    AuraData._cache = null;
    Test.assert(AuraData.reading(now) == null);
    // in range: the watch clock 2 min behind the phone moves every time 2 min back (the age is the phone's)
    var first = (cases[0] as Dictionary).get("payload") as Dictionary;
    Link.accept(first, now - 120);
    var r = AuraData.reading(now - 90) as AuraReading;
    Test.assertEqualMessage(r.readAt, now - 30 - 120, "reading time on the watch clock");
    Test.assertEqualMessage(r.mgdl, 112, "value");
    Test.assertEqualMessage(r.status, "none", "no pill");
    Test.assertEqualMessage((r.history as Array).size(), 36, "history");
    Test.assertEqualMessage(((r.lastItems as Array)[0] as Array)[0], "rapid", "last treatment");
    Test.assertEqualMessage(r.tir, 82, "time in range");
    for (var i = 0; i < cases.size(); i++) {
        var k = cases[i] as Dictionary;
        var name = k.get("name") as String;
        Link.reset();
        AuraData._cache = null;
        Link.accept(k.get("payload"), now);
        r = AuraData.reading(now) as AuraReading;
        if (name.find("still sending") != null) {
            Test.assertEqualMessage(r.sensor, "endedSending", name);
        } else if (name.find("nothing paired") != null) {
            Test.assertEqualMessage(r.sensor, "none", name);
        } else if (name.find("Mute all") != null) {
            Test.assertEqualMessage(r.status, "muted", name);
            Test.assertMessage(r.statusUntil == null, name);
        } else if (name.find("high, insulin") != null) {
            Test.assertEqualMessage(r.status, "quiet", name);
            Test.assertEqualMessage(r.statusUntil, now + 2700, name);
            // the quiet ran out: no pill until the phone says what follows
            Test.assertEqualMessage((AuraData.reading(now + 2700) as AuraReading).status, "none", name);
        } else if (name.find("a meal") != null) {
            Test.assertEqualMessage((r.lastItems as Array).size(), 2, name);
            Test.assertEqualMessage(r.lastAt, now - 2100, name);
            // the insulin's quiet ends with the phone away: the carbs no longer ride along; 4 h on, nothing
            Test.assertEqualMessage(((AuraData.reading(now + 1500) as AuraReading).lastItems as Array).size(), 1, name);
            Test.assertMessage((AuraData.reading(now + 12300) as AuraReading).lastItems == null, name);
        } else if (name.find("warming up") != null) {
            Test.assertEqualMessage(r.sensor, "warmup", name);
            Test.assertEqualMessage(r.warmUntil, now + 2520, name);
        }
    }
    Link.reset();
    AuraData._cache = null;
    logger.debug("seam ok");
    return true;
}
