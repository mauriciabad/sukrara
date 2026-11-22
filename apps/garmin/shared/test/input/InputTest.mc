// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;
import Toybox.Test;

// The input of the app's surfaces and the data field: the value in the person's unit, the freshness steps
// (contracts/graph/freshness: 90 s, 5 min, 20 min, 3 h), and ReadingInput filled from the app's own profile's cases.
// Built into the app and the field by their link-test.jungle: apps/garmin/tools/link-test.sh app|field.
(:test)
function inputValueAndFreshness(logger as Logger) as Boolean {
    Test.assertEqual(Glucose.value(39, "mgdl"), "LO");
    Test.assertEqual(Glucose.value(401, "mmol"), "HI");
    Test.assertEqual(Glucose.value(112, "mgdl"), "112");
    Test.assertEqual(Glucose.value(112, "mmol"), "6.2");
    // freshness.json's boundaries
    Test.assert(!Glucose.aged(90) && Glucose.aged(91));
    Test.assert(!Glucose.struck(300) && Glucose.struck(301));
    Test.assert(Glucose.shown(1200) && !Glucose.shown(1201));
    Test.assert(Glucose.chart(10800) && !Glucose.chart(10801));
    Test.assertEqual(Glucose.age(59), "<1 min");
    Test.assertEqual(Glucose.age(600), "10 min");
    return true;
}

(:test)
function inputFromOwnProfile(logger as Logger) as Boolean {
    var list = (Link.PROFILE.equals("app") ? LinkCases.app() : LinkCases.field()) as Array;
    for (var i = 0; i < list.size(); i++) {
        var p = (list[i] as Dictionary).get("payload") as Dictionary;
        var sent = p.get("sent") as Number;
        Link.reset();
        Link.accept(p, sent);
        var r = Readings.input(sent);
        Test.assert(r.payload);
        var g = p.get("g");
        if (g instanceof Dictionary) {
            Test.assertEqual(r.mgdl, g.get("v"));
            Test.assertEqual(r.ageS, sent - (g.get("t") as Number));
            Test.assertEqual(r.history.size(), (p.get("hist") as Array).size());
            var m = new PlainModel();
            PlainLayout.fill(m, r, "none", "wait");
            Test.assertEqual(m.value != null, Glucose.shown(r.ageS));
            Test.assertEqual(m.message != null, !Glucose.shown(r.ageS));
        } else {
            Test.assert(r.mgdl == null);
        }
    }
    Link.reset();
    var none = Readings.input(LinkCases.NOW);
    Test.assert(!none.payload);
    var m = new PlainModel();
    PlainLayout.fill(m, none, "none", "wait");
    Test.assertEqual(m.message, "wait");
    return true;
}
