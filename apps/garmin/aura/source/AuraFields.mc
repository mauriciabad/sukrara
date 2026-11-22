// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;

// Every field a slot can show (SPEC.md §6.10, aura.js fields()), but the treatment and the automatic ones (AuraModel):
// its icon, its gap, its format and where the watch gets it (a Toybox.Complications type, 0: the face reads it itself,
// AuraWatch). Built per call, not kept (the heap: #217).
module AuraFields {
    function keys() as Array {
        return [
            "tir", "steps", "floors", "calories", "intensity", "pushes", "heart", "bodyBattery", "stress", "spo2",
            "respiration", "sleep", "recovery", "weather", "temperature", "highLow", "forecast1", "forecast2", "forecast3",
            "sunrise", "sunset", "pressure", "altitude", "date", "weekday", "tz2", "events", "battery", "solar",
            "notifications", "training", "vo2Run", "vo2Bike", "runWeek", "bikeWeek", "race5k", "race10k", "raceHalf",
            "raceMarathon", "pace5k", "pace10k", "paceHalf", "paceMarathon", "golf",
        ];
    }

    // [icon, gap, format, complication type] of a field, null for none. Formats (aura.js): n a count with thousands,
    // p a plain number, % a percent, d degrees, w the weather now, h today's high / low, f a forecast, t a time of day,
    // a an altitude, k a distance, P a pressure, u a duration, r a race time, c a pace, s the watch's words, D the date,
    // W the weekday, z the second time zone, B the watch battery
    function spec(key as String) as Array? {
        var i = keys().indexOf(key);
        if (i < 0) {
            return null;
        }
        return [
            [
                "ring", "steps", "stairs", "flame", "stopwatch", "wheelchair", "heart", "bolt", "stress", "drop", "breath",
                "moon", "hourglass", "sun", "thermo", "thermo", "sun", "sun", "sun", "sunrise", "sunset", "gauge",
                "mountain", "calendar", "calendar", "globe", "event", "battery", "solar", "bell", "trend", "lungs", "lungs",
                "run", "bike", "flag", "flag", "flag", "flag", "flag", "flag", "flag", "flag", "golf",
            ][i],
            "55555554555556446666656556535555555555555555".substring(i, i + 1).toNumber(),
            "%npnpnppp%ppuwdhfffttPaDWzsB%psppkkrrrrccccs".substring(i, i + 1),
            [
                0, 2, 4, 3, 5, 40, 18, 23, 22, 35, 36, 42, 21, 0, 38, 0, 0, 0, 0, 13, 14, 16, 15, 0, 0, 0, 12, 0, 37, 17, 26,
                24, 25, 19, 20, 27, 28, 29, 30, 31, 32, 33, 34, 41,
            ][i],
        ];
    }
}
