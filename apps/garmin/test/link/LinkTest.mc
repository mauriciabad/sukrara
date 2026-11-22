// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

// contracts/watchface/payload on the watch: every case's payload, received at a watch time, gives the face's inputs
// the case expects at each later watch time (AuraLink.inputs), and THE SEAM's reading (AuraLink.reading) carries them
// onto the watch clock. Run: apps/garmin/tools/link-test.sh.
(:test)
module LinkTest {
    function close(a, b) as Boolean {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (a instanceof String || b instanceof String || a instanceof Boolean || b instanceof Boolean) {
            return a.equals(b);
        }
        if (a instanceof Array) {
            if (!(b instanceof Array) || a.size() != b.size()) {
                return false;
            }
            for (var i = 0; i < a.size(); i++) {
                if (!close(a[i], b[i])) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Dictionary) {
            if (!(b instanceof Dictionary) || a.size() != b.size()) {
                return false;
            }
            var keys = a.keys();
            for (var i = 0; i < keys.size(); i++) {
                if (!close(a.get(keys[i]), b.get(keys[i]))) {
                    return false;
                }
            }
            return true;
        }
        var d = (a as Numeric).toDouble() - (b as Numeric).toDouble();
        return d < 0.01 && d > -0.01;
    }
}

(:test)
function linkEveryCase(logger as Logger) as Boolean {
    var ok = true;
    var cases = LinkCases.cases();
    for (var c = 0; c < cases.size(); c++) {
        var k = cases[c] as Dictionary;
        var checks = k.get("watch") as Array;
        for (var w = 0; w < checks.size(); w++) {
            var check = checks[w] as Dictionary;
            var received = check.get("received");
            var at = LinkCases.NOW + (check.get("at") as Number);
            AuraLink.reset();
            AuraLink.accept(k.get("payload"), LinkCases.NOW + (received == null ? 0 : received as Number));
            var x = AuraLink.inputs(at) as Dictionary;
            var expect = check.get("expect") as Dictionary;
            var keys = expect.keys();
            for (var i = 0; i < keys.size(); i++) {
                var got = x.get(keys[i]);
                if (!LinkTest.close(expect.get(keys[i]), got)) {
                    logger.error(k.get("name") + " @" + check.get("at") + " " + keys[i] + ": expected " + expect.get(keys[i]) + ", got " + got);
                    ok = false;
                }
            }
        }
    }
    return ok;
}

(:test)
function linkKeepsTheNewest(logger as Logger) as Boolean {
    var ok = true;
    var older = LinkCases.older();
    for (var i = 0; i < older.size(); i++) {
        var o = older[i] as Dictionary;
        AuraLink.reset();
        var stored = o.get("stored") as Number;
        AuraLink.accept({"v" => AuraLink.VERSION, "p" => AuraLink.PROFILE, "sent" => stored}, stored);
        AuraLink.accept({"v" => AuraLink.VERSION, "p" => AuraLink.PROFILE, "sent" => o.get("incoming")}, o.get("watch") as Number);
        var want = "stored".equals(o.get("keep")) ? stored : o.get("incoming");
        var got = (AuraLink.payload() as Dictionary).get("sent");
        if (got != want) {
            logger.error("order case " + i + ": kept " + got + ", expected " + want);
            ok = false;
        }
    }
    var ignored = LinkCases.ignored();
    for (var i = 0; i < ignored.size(); i++) {
        AuraLink.reset();
        if (AuraLink.accept(ignored[i], LinkCases.NOW)) {
            logger.error("accepted " + ignored[i]);
            ok = false;
        }
    }
    return ok;
}

// A background run counts once, whichever way comes first (Garmin's onBackgroundData or the pickup from storage at a
// draw), and the pickup brings what Garmin didn't hand over (#244).
(:test)
function linkHandOverCountsOnce(logger as Logger) as Boolean {
    var now = LinkCases.NOW;
    AuraLink.reset();
    var a = {"k" => "push", "d" => {"v" => AuraLink.VERSION, "p" => AuraLink.PROFILE, "sent" => now}, "n" => 0, "id" => "a"};
    Application.Storage.setValue(AuraLinkBackground.HANDOFF, a);
    Test.assertMessage(AuraLink.take(a, now, false), "Garmin's hand-over");
    AuraLink.pickUp(now + 20);
    var runs = AuraLink.stored("Runs");
    Test.assertEqualMessage(runs.size(), 1, "the same run from storage doesn't count again");
    // Garmin doesn't hand over the next run: the next draw picks it up from storage, once
    var b = {"k" => "push", "d" => {"v" => AuraLink.VERSION, "p" => AuraLink.PROFILE, "sent" => now + 60}, "n" => 0, "id" => "b"};
    Application.Storage.setValue(AuraLinkBackground.HANDOFF, b);
    AuraLink.pickUp(now + 65);
    Test.assertEqualMessage((AuraLink.payload() as Dictionary).get("sent"), now + 60, "picked up");
    runs = AuraLink.stored("Runs");
    Test.assertMessage((runs[runs.size() - 1] as String).find("ps.") == 0, "a push from storage: ps");
    Test.assertMessage(!AuraLink.take(b, now + 66, false), "Garmin's late hand-over of the same run");
    AuraLink.pickUp(now + 80);
    Test.assertEqualMessage(AuraLink.stored("Runs").size(), 2, "two runs, each once");
    AuraLink.reset();
    return true;
}

// A push run keeps the newest of the waiting messages.
(:test)
function linkNewestOfTheQueue(logger as Logger) as Boolean {
    var a = {"v" => 1, "sent" => 100};
    var b = {"v" => 1, "sent" => 200};
    Test.assert(AuraLinkBackground.newer(a, b) == b);
    Test.assert(AuraLinkBackground.newer(b, a) == b);
    Test.assert(AuraLinkBackground.newer(a, "junk") == a);
    Test.assert(AuraLinkBackground.newer(null, a) == a);
    return true;
}

// THE SEAM: what AuraData.current() hands the face.
(:test)
function linkSeamReading(logger as Logger) as Boolean {
    var cases = LinkCases.cases();
    var now = LinkCases.NOW;
    AuraLink.reset();
    Test.assert(AuraLink.reading(now) == null);
    // in range: the watch clock 2 min behind the phone moves every time 2 min back (the age is the phone's)
    var first = (cases[0] as Dictionary).get("payload") as Dictionary;
    AuraLink.accept(first, now - 120);
    var r = AuraLink.reading(now - 90) as AuraReading;
    Test.assertEqualMessage(r.readAt, now - 30 - 120, "reading time on the watch clock");
    Test.assertEqualMessage(r.mgdl, 112, "value");
    Test.assertEqualMessage(r.status, "none", "no pill");
    Test.assertEqualMessage((r.history as Array).size(), 36, "history");
    Test.assertEqualMessage(((r.lastItems as Array)[0] as Array)[0], "rapid", "last treatment");
    Test.assertEqualMessage(r.tir, 82, "time in range");
    for (var i = 0; i < cases.size(); i++) {
        var k = cases[i] as Dictionary;
        var name = k.get("name") as String;
        AuraLink.reset();
        AuraLink.accept(k.get("payload"), now);
        r = AuraLink.reading(now) as AuraReading;
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
            Test.assertEqualMessage((AuraLink.reading(now + 2700) as AuraReading).status, "none", name);
        } else if (name.find("a meal") != null) {
            Test.assertEqualMessage((r.lastItems as Array).size(), 2, name);
            Test.assertEqualMessage(r.lastAt, now - 2100, name);
            // the insulin's quiet ends with the phone away: the carbs no longer ride along; 4 h on, nothing
            Test.assertEqualMessage(((AuraLink.reading(now + 1500) as AuraReading).lastItems as Array).size(), 1, name);
            Test.assertMessage((AuraLink.reading(now + 12300) as AuraReading).lastItems == null, name);
        } else if (name.find("warming up") != null) {
            Test.assertEqualMessage(r.sensor, "warmup", name);
            Test.assertEqualMessage(r.warmUntil, now + 2520, name);
        }
    }
    AuraLink.reset();
    logger.debug("seam ok");
    return true;
}
