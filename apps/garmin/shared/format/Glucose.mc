// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;

// A reading the way Sukrara writes it, for the app, its glance and the data field (the face writes its value in its
// own layout, AuraModel, so this folder isn't in its build):
// - value: in the person's unit (contracts/watchface/payload "unit": mg/dL ÷ 18.0182, one decimal; < 40 = LO, > 400 = HI)
// - the freshness steps (contracts/graph/freshness): until 90 s the value alone; then its age; from 5 min struck
//   through and grey without the trend; from 20 min "No readings"; from 3 h no chart.
(:glance)
module Glucose {
    const LATE_S = 90;
    const STRUCK_S = 300;
    const GONE_S = 1200;
    const NO_CHART_S = 10800;

    function value(mgdl as Number, unit as String) as String {
        if (mgdl < 40) {
            return "LO";
        }
        if (mgdl > 400) {
            return "HI";
        }
        return unit.equals("mmol") ? (mgdl / 18.0182).format("%.1f") : mgdl.toString();
    }

    // the value is drawn at [ageS] (s since the reading); false = "No readings"
    function shown(ageS as Number) as Boolean {
        return ageS <= GONE_S;
    }

    // struck through and grey, no trend
    function struck(ageS as Number) as Boolean {
        return ageS > STRUCK_S;
    }

    // the reading's age is shown
    function aged(ageS as Number) as Boolean {
        return ageS > LATE_S;
    }

    function chart(ageS as Number) as Boolean {
        return ageS <= NO_CHART_S;
    }

    // the age as the person reads it: whole minutes, "<1 min" under one
    function age(ageS as Number) as String {
        var m = ageS / 60;
        return m < 1 ? "<1 min" : m < 60 ? m + " min" : (m / 60) + " h";
    }
}
