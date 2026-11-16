// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import Toybox.Lang;

// THE INPUT MODEL: everything the face draws from, one object, the port of aura.js opt(cfg) (SPEC.md §3). The layout
// (AuraModel) is a pure function of it, so the unit tests and the debug case loader fill it from cases.json and the
// face fills it from the watch, the settings and THE SEAM (AuraData.current(), what Sukrara on the phone sent).
// Defaults are the reference's (every case starts from them). Times the face counts itself are absolute (epoch s):
// the face computes the age, the time left and the time since at every draw (ground rule 1, SPEC.md §3).
class AuraInput {
    // --- the reading (Sukrara decides; AuraData) ---
    var glucose as Float = 112.0; // mg/dL; < 40 = LO, > 400 = HI
    var rate as Float? = 0.4; // mg/dL/min, Sukrara's trend; null = no trend
    var trend as Boolean = true; // false = no trend even with a rate
    var doubled as Boolean? = null; // Sukrara's doubled arrow (TrendAngle.isDouble); null = |rate| >= 3 (the reference)
    var age as Float = 0.5; // minutes since the reading: now - its time, never negative (a reading ahead is fresh)
    var history as Array? = null; // the readings before this one, 5 min apart, oldest first (null = a gap)
    var spark as Array? = null; // the sparkline's series, current last (null: history + the reading)
    var sensor as String = "ok"; // ok, warmup, almost, ending, endedSending, ended, none (nothing paired)
    var status as String = "auto"; // auto, quiet (held after a treatment), muted
    var left as Float? = 25.0; // minutes left of a quiet / mute; null = no end
    var lastItems as Array = [["rapid", 2]] as Array; // the last treatment: [kind (rapid, long, carbs), amount]
    var lastAgo as Float = 80.0; // minutes since it
    var warm as Float = 0.45; // warm-up done, 0..1
    var readyMin as Float? = null; // minutes until the sensor is ready (null: (1 - warm) h, the reference's fake)
    var sensorLeft as Float = 5.0; // hours left of the sensor (shown in its last 8 h)
    var unit as String = "mgdl"; // mgdl, mmol
    var limits as Array = [55, 70, 140, 180, 250, 300] as Array; // very low, low, very good, high, very high, extreme
    var tir as Number? = 82; // today's time in range, %

    // --- the watch ---
    var now as Number = 0; // epoch s (the clock)
    var utc as Boolean = false; // true: show now in UTC (the tests' fixed clock), false: the watch's zone
    var is24 as Boolean = true;
    var lang as String = "en"; // en, es
    var battery as Float = 8.6; // days left
    var batteryPct as Number? = null; // null = days x 10 (the reference)
    var mode as String = "awake"; // awake, aod, night
    var privacy as String = "open"; // open, private, revealed (held)
    var reveal as Float = 0.6; // revealed: the fraction of the 5 s left
    var phone as Boolean = true; // the phone in reach
    var steps as Number? = 6420;
    var temp as Number? = 14; // weather, degrees in the watch's unit
    var sunset as Number? = 17 * 60 + 42; // minutes after midnight
    var bodyBattery as Number? = 62;
    var altitude as Number? = 1240; // in the watch's unit
    var altUnit as String = "m"; // m, ft
    var heart as Number? = 64;
    var notifications as Number? = 3;
    var tz2Label as String = "NYC";
    var tz2Min as Number? = 4 * 60 + 9; // the second zone's time, minutes after midnight
    var floors as Number? = 8;

    // --- the face's settings (SPEC.md §11, SettingsSpec) ---
    var dial as String = "grain"; // grain, black, map, contours, waterline, lasers, glow
    var halo as String = "outline"; // outline, box, none
    var trendStyle as String = "spark"; // arrow, spark
    var seconds as Boolean = true;
    var rangeColour as Boolean = true;
    var topField as String = "steps";
    var fields as Array = ["tir", "treatment"] as Array;
    var battLabel as String = "days"; // days, percent, both

    function initialize() {}
}
