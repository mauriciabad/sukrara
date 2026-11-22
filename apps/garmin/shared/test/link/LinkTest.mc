// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Application;
import Toybox.Lang;
import Toybox.Test;

// contracts/watchface/payload on the watch, in the shared link (apps/garmin/shared/link): every case's payload,
// received at a watch time, gives the inputs the case expects at each later watch time (Link.inputs); the order rule,
// the hand-over, the push queue. Built into an app's test build (its link-test.jungle): apps/garmin/tools/link-test.sh.
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
        // [a] the expectation: a dictionary checks the keys it names (a `last` item's own t and m: README "Payload")
        if (a instanceof Dictionary) {
            if (!(b instanceof Dictionary)) {
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

// the face's cases: what it makes of each payload at each watch time (the app and the field: linkOwnProfile)
(:test)
function linkEveryCase(logger as Logger) as Boolean {
    if (!Link.PROFILE.equals("face")) {
        return true;
    }
    var ok = true;
    var cases = LinkCases.cases();
    for (var c = 0; c < cases.size(); c++) {
        var k = cases[c] as Dictionary;
        var checks = k.get("watch") as Array;
        for (var w = 0; w < checks.size(); w++) {
            var check = checks[w] as Dictionary;
            var received = check.get("received");
            var at = LinkCases.NOW + (check.get("at") as Number);
            Link.reset();
            Link.accept(k.get("payload"), LinkCases.NOW + (received == null ? 0 : received as Number));
            var x = Link.inputs(at) as Dictionary;
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
        Link.reset();
        var stored = o.get("stored") as Number;
        Link.accept({"v" => Link.VERSION, "p" => Link.PROFILE, "sent" => stored}, stored);
        Link.accept({"v" => Link.VERSION, "p" => Link.PROFILE, "sent" => o.get("incoming")}, o.get("watch") as Number);
        var want = "stored".equals(o.get("keep")) ? stored : o.get("incoming");
        var got = (Link.payload() as Dictionary).get("sent");
        if (got != want) {
            logger.error("order case " + i + ": kept " + got + ", expected " + want);
            ok = false;
        }
    }
    // the list is the face's: another app skips what is its own profile there (it reads it, linkOwnProfile)
    var ignored = LinkCases.ignored();
    for (var i = 0; i < ignored.size(); i++) {
        var x = ignored[i];
        if (x instanceof Dictionary && Link.PROFILE.equals(x.get("p")) && !Link.PROFILE.equals("face")) {
            continue;
        }
        Link.reset();
        if (Link.accept(ignored[i], LinkCases.NOW)) {
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
    Link.reset();
    // the report's runs from an earlier run of the tests (the simulator keeps the app's storage)
    Application.Storage.deleteValue(Link.REPORT + "Runs");
    var a = {"k" => "push", "d" => {"v" => Link.VERSION, "p" => Link.PROFILE, "sent" => now}, "n" => 0, "id" => "a"};
    Application.Storage.setValue(LinkBackground.HANDOFF, a);
    Test.assertMessage(Link.take(a, now, false), "Garmin's hand-over");
    Link.pickUp(now + 20);
    var runs = Link.stored("Runs");
    Test.assertEqualMessage(runs.size(), 1, "the same run from storage doesn't count again");
    // Garmin doesn't hand over the next run: the next draw picks it up from storage, once
    var b = {"k" => "push", "d" => {"v" => Link.VERSION, "p" => Link.PROFILE, "sent" => now + 60}, "n" => 0, "id" => "b"};
    Application.Storage.setValue(LinkBackground.HANDOFF, b);
    Link.pickUp(now + 65);
    Test.assertEqualMessage((Link.payload() as Dictionary).get("sent"), now + 60, "picked up");
    runs = Link.stored("Runs");
    Test.assertMessage((runs[runs.size() - 1] as String).find("ps.") == 0, "a push from storage: ps");
    Test.assertMessage(!Link.take(b, now + 66, false), "Garmin's late hand-over of the same run");
    Link.pickUp(now + 80);
    Test.assertEqualMessage(Link.stored("Runs").size(), 2, "two runs, each once");
    Link.reset();
    return true;
}

// A push run keeps the newest of the waiting messages.
(:test)
function linkNewestOfTheQueue(logger as Logger) as Boolean {
    var a = {"v" => 1, "sent" => 100};
    var b = {"v" => 1, "sent" => 200};
    Test.assert(LinkBackground.newer(a, b) == b);
    Test.assert(LinkBackground.newer(b, a) == b);
    Test.assert(LinkBackground.newer(a, "junk") == a);
    Test.assert(LinkBackground.newer(null, a) == a);
    return true;
}

// Each app keeps the payloads of its own profile and ignores the others' (contracts/watchface/payload "Profiles").
(:test)
function linkOwnProfile(logger as Logger) as Boolean {
    var ok = true;
    var all = {"face" => LinkCases.cases(), "app" => LinkCases.app(), "field" => LinkCases.field()};
    var profiles = all.keys();
    for (var p = 0; p < profiles.size(); p++) {
        var list = all.get(profiles[p]) as Array;
        var own = Link.PROFILE.equals(profiles[p]);
        for (var i = 0; i < list.size(); i++) {
            var k = list[i] as Dictionary;
            Link.reset();
            var payload = k.get("payload") as Dictionary;
            if (Link.accept(payload, payload.get("sent") as Number) != own) {
                logger.error(Link.PROFILE + ": " + (own ? "ignored " : "kept ") + profiles[p] + " case " + k.get("name"));
                ok = false;
            } else if (own && Link.inputs(payload.get("sent") as Number) == null) {
                logger.error(Link.PROFILE + ": no inputs from " + k.get("name"));
                ok = false;
            }
        }
    }
    Link.reset();
    return ok;
}

// the background's health (#217): a start registers the background again when nothing is registered or a one-shot
// Moment should have run and didn't; a standing Duration or a Moment still ahead is left alone
(:test)
function linkBackgroundStale(logger as Logger) as Boolean {
    var now = 100000;
    Test.assertEqualMessage(LinkBackground.stale(null, now), true, "nothing registered");
    Test.assertEqualMessage(LinkBackground.stale(-1, now), false, "the 5-minute Duration");
    Test.assertEqualMessage(LinkBackground.stale(now + 240, now), false, "a Moment ahead");
    Test.assertEqualMessage(LinkBackground.stale(now - 30, now), false, "a Moment running now");
    Test.assertEqualMessage(LinkBackground.stale(now - 600, now), true, "a Moment that never ran");
    return true;
}
