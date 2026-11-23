// SukraraFace — https://sukrara.mauri.app/developers/garmin-faces/
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: 0BSD
import Toybox.Lang;

// What a watch face makes of Sukrara's payload (https://sukrara.mauri.app/schemas/watch-payload.json, version 5) at
// the watch time it draws: the value in the person's unit, LO / HI, its age and freshness, the trend, what Sukrara
// asks, the last treatment, the sensor, today's time in range. Every age is worked out again at every draw from the
// payload's absolute times, so an old value is never shown as current. Sukrara on the phone decides everything; this
// only counts time.
//
// Sukrara's own watch apps read their payloads with this same code.
(:background :glance)
module SukraraFace {
    // the payload version this barrel reads; the barrel's major version is the same number
    const VERSION = 5;
    // the freshness steps, seconds since the reading: from LATE_S its age is shown, from STRUCK_S it is struck through
    // and grey without the trend, from GONE_S there is no value ("No reading since"), from NO_CHART_S no chart
    const LATE_S = 90;
    const STRUCK_S = 300;
    const GONE_S = 1200;
    const NO_CHART_S = 10800;
    // a stored payload this far ahead of the watch clock gives way to any new one (the phone's clock was wrong)
    const AHEAD_S = 300;

    // Reading.freshness
    enum {
        CURRENT, // up to LATE_S: the value alone
        LATE, // its age beside it
        STRUCK, // struck through, grey, no trend
        GONE // no value
    }

    // True when [p] is a payload of this version and [profile] ("face" for every watch face).
    function valid(p as Object?, profile as String) as Boolean {
        if (!(p instanceof Dictionary)) {
            return false;
        }
        var d = p as Dictionary<String, Object?>;
        return d.get("v") == VERSION && profile.equals(d.get("p")) && d.get("sent") instanceof Number;
    }

    // THE VERSION RULE (contracts/apps/formats.json, the payload's "versions"): the version of [p] when it is a payload
    // of [profile] of another version (a whole-number v, the profile, a whole-number sent: parts that never move),
    // else null. Its glucose still reads (glucoseOnly); which app to update is toUpdate's.
    function otherVersion(p as Object?, profile as String) as Number? {
        if (!(p instanceof Dictionary)) {
            return null;
        }
        var d = p as Dictionary<String, Object?>;
        var v = d.get("v");
        return v instanceof Number && v != VERSION && profile.equals(d.get("p")) && d.get("sent") instanceof Number ? v : null;
    }

    // What this version reads of [p], a payload of another version (otherVersion): the parts that never move (v, p,
    // sent, unit, bands and the glucose's t and v) as a payload of this version, so the current value stays on the
    // watch, ageing; nothing else of it (no trend, history, status or anything newer).
    function glucoseOnly(p as Dictionary) as Dictionary {
        var out = {"v" => VERSION, "p" => p.get("p"), "sent" => p.get("sent")} as Dictionary;
        var u = p.get("unit");
        if (u instanceof String) {
            out.put("unit", u);
        }
        var b = p.get("bands");
        if (b instanceof Array) {
            out.put("bands", b);
        }
        var g = p.get("g");
        out.put("g", g instanceof Dictionary && g.get("t") instanceof Number && g.get("v") instanceof Number
            ? {"t" => g.get("t"), "v" => g.get("v"), "rate" => null, "band" => null, "deg" => null}
            : null);
        out.put("hist", []);
        return out;
    }

    // toUpdate's answers: this app (the phone is newer), or Sukrara on the phone (it is older)
    const UPDATE_APP = "app";
    const UPDATE_PHONE = "phone";

    // Which app to update after a payload of version [other] (otherVersion; null = none): the smaller version's.
    function toUpdate(other as Number?) as String? {
        return other == null || other == VERSION ? null : other > VERSION ? UPDATE_APP : UPDATE_PHONE;
    }

    // The order rule: whether [incoming] (valid) replaces the payload kept, sent at [storedSent] (null: none), at the
    // watch time [now]. The newest by `sent` wins, unless the kept one is more than AHEAD_S ahead of the watch clock.
    // It takes the kept one's `sent` only, so a background or a glance never needs to hold two payloads at once.
    function replaces(storedSent as Number?, incoming as Dictionary, now as Number) as Boolean {
        if (storedSent == null) {
            return true;
        }
        var s = storedSent as Number;
        return (incoming.get("sent") as Number) >= s || s - now > AHEAD_S;
    }

    // How far the phone's clock is ahead of the watch's when a payload sent at [sent] arrives at [now] (never below 0:
    // a watch clock ahead only makes readings older).
    function skewOf(sent as Number, now as Number) as Number {
        return sent > now ? sent - now : 0;
    }

    // mg/dL [mgdl] as the person reads it: "112", "6.2" (mmol/L, one decimal), "LO" under 40, "HI" over 400.
    function text(mgdl as Number, unit as String) as String {
        if (mgdl < 40) {
            return "LO";
        }
        if (mgdl > 400) {
            return "HI";
        }
        return unit.equals("mmol") ? (mgdl / 18.0182).format("%.1f") : mgdl.toString();
    }

    // The freshness step at [ageS] seconds: CURRENT, LATE, STRUCK or GONE.
    function freshness(ageS as Number) as Number {
        return ageS > GONE_S ? GONE : ageS > STRUCK_S ? STRUCK : ageS > LATE_S ? LATE : CURRENT;
    }

    // The last treatment at the phone's time [t]: the first of `last`, `last.next`, … whose `until` is still ahead
    // (−1 = never); null = none.
    function treatment(p as Dictionary, t as Number) as Dictionary? {
        var s = p.get("last");
        while (s instanceof Dictionary) {
            var until = s.get("until");
            if (!(until instanceof Number && until != -1 && t >= until)) {
                return s;
            }
            s = s.get("next");
        }
        return null;
    }

    // The payload at one watch time. Times are the watch clock's (epoch s), the phone's clock lead already applied.
    // (:sukraraFace): what a face draws with (text, trend, level); Sukrara's watch app and data field leave them out
    // (their jungles exclude sukraraFace: the app's glance has 64 KB). Named for the barrel, so it never clashes with an
    // annotation of the face that uses it.
    class Reading {
        var now as Number; // the watch time it was worked out for
        var unit as String = "mgdl"; // mgdl, mmol
        var bands as Array<Number> = [55, 70, 140, 180, 250, 300] as Array<Number>; // the person's limits, mg/dL:
        // [veryLow, low, tight, high, veryHigh, extremelyHigh]; in range is low…high

        // --- the newest reading (mgdl null: none, or not monitoring) ---
        var mgdl as Number? = null; // < 40 = LO, > 400 = HI
        var readAt as Number = 0; // its time
        var ageS as Number = 0; // its age at now, never negative
        var freshness as Number = GONE; // CURRENT, LATE, STRUCK, GONE
        var rate as Float? = null; // mg/dL per minute; null = no trend (always for LO / HI)
        var band as Number? = null; // the trend's band −3…3 (±3 = the doubled arrow); null = no trend
        var deg as Float? = null; // the arrow's rotation, degrees (negative = up); null = no trend
        var doubled as Boolean = false; // the doubled arrow
        var history as Array = [] as Array; // the readings before it, 5 min apart, oldest first (null = a gap)

        // --- what Sukrara asks (the pill) ---
        var status as String = "none"; // none, auto (treat the side), quiet (treated), muted (a side or all)
        var side as String? = null; // low, high; null = all (muted) or none
        var left as Number? = null; // seconds left of a quiet / mute; null = no end (or none)

        // --- the last treatment (null = none) ---
        var last as Array<Dictionary>? = null; // [{k: rapid / carbs, a: amount, t, m}], as Sukrara's card
        var lastAt as Number? = null; // its time

        // --- the sensor ---
        var sensor as String = "ok"; // none (nothing paired), warmup, almost (ready, no reading yet), ok, ending, ended
        var warm as Float? = null; // warm-up done 0…1 (warmup, almost)
        var readyAt as Number? = null; // when the warm-up ends (warmup, almost)
        var sensorLife as Float? = null; // the sensor's whole life, days (when the phone knows its dates)
        var sensorLeft as Float? = null; // hours left of it

        var tir as Number? = null; // today's time in range on the phone, whole %; null = not enough data

        // [payload] a valid payload, [skew] SukraraFace.skewOf at its arrival, [now] the watch time.
        function initialize(payload as Dictionary, skew as Number, now as Number) {
            self.now = now;
            var p = payload as Dictionary<String, Object?>;
            var t = now + skew; // the phone's clock now
            var u = p.get("unit");
            unit = u instanceof String ? u : "mgdl";
            var b = p.get("bands");
            if (b instanceof Array && b.size() == 6) {
                bands = b as Array<Number>;
            }
            var g = dict(p.get("g"));
            if (g != null) {
                mgdl = g.get("v") as Number;
                readAt = (g.get("t") as Number) - skew;
                ageS = now > readAt ? now - readAt : 0;
                freshness = SukraraFace.freshness(ageS);
                var r = g.get("rate");
                rate = r != null ? (r as Numeric).toFloat() : null;
                var k = g.get("band");
                band = k instanceof Number ? k : null;
                doubled = k == 3 || k == -3;
                var d = g.get("deg");
                deg = d != null ? (d as Numeric).toFloat() : null;
                var h = p.get("hist");
                if (h instanceof Array) {
                    history = h;
                }
            }
            pill(dict(p.get("st")), t);
            var l = treatment(payload, t);
            if (l != null) {
                last = l.get("items") as Array<Dictionary>;
                lastAt = (l.get("t") as Number) - skew;
            }
            sensorAt(dict(p.get("sensor")), g, t, skew);
            var x = dict(p.get("tir"));
            tir = x != null && t < (x.get("until") as Number) ? x.get("pct") as Number : null;
        }

        // [x] as a payload's dictionary, null when it isn't one
        private function dict(x as Object?) as Dictionary<String, Object?>? {
            return x instanceof Dictionary ? x as Dictionary<String, Object?> : null;
        }

        // needed → auto, taken → quiet, paused / muted → muted; a countdown that ran out shows nothing
        private function pill(st as Dictionary<String, Object?>?, t as Number) as Void {
            if (st == null) {
                return;
            }
            var until = st.get("until");
            if (until instanceof Number && until != -1) {
                if (until <= t) {
                    return;
                }
                left = until - t;
            }
            var k = st.get("k");
            status = "needed".equals(k) ? "auto" : "taken".equals(k) ? "quiet" : "paused".equals(k) || "muted".equals(k) ? "muted" : "none";
            side = st.get("side") as String?;
        }

        private function sensorAt(s as Dictionary<String, Object?>?, g as Dictionary<String, Object?>?, t as Number, skew as Number) as Void {
            if (s == null) {
                return;
            }
            var state = s.get("s");
            if ("none".equals(state) || "ended".equals(state)) {
                sensor = state as String;
            } else if ("warmup".equals(state)) {
                var start = s.get("start") as Number;
                var ready = s.get("ready") as Number;
                // a reading since the start ends the warm-up early
                if (t < (s.get("hold") as Number) && !(g != null && (g.get("t") as Number) >= start)) {
                    sensor = t >= ready ? "almost" : "warmup";
                    var w = ready > start ? (t - start).toFloat() / (ready - start) : 1.0;
                    warm = w < 0.0 ? 0.0 : w > 1.0 ? 1.0 : w;
                    readyAt = ready - skew;
                }
            } else if ("active".equals(state)) {
                var end = s.get("end");
                var start = s.get("start");
                var warn = s.get("warn");
                if (end instanceof Number && start instanceof Number && end > start) {
                    sensorLife = (end - start) / 86400.0;
                    sensorLeft = (end > t ? end - t : 0) / 3600.0;
                }
                if (end instanceof Number && t >= end) {
                    sensor = "ended";
                } else if (end instanceof Number && warn instanceof Number && t >= warn) {
                    sensor = "ending";
                    sensorLeft = (end - t) / 3600.0;
                }
            }
        }

        // The value as the person reads it ("112", "6.2", "LO", "HI"); null with no value to show (none, or GONE).
        (:sukraraFace)
        function text() as String? {
            return mgdl == null || freshness == GONE ? null : SukraraFace.text(mgdl as Number, unit);
        }

        // Whether to draw the trend arrow now: a trend, a value that isn't struck through, not LO / HI.
        (:sukraraFace)
        function trend() as Boolean {
            return deg != null && freshness <= LATE && (mgdl as Number) >= 40 && (mgdl as Number) <= 400;
        }

        // The value's place among the person's limits, as Sukrara colours it: −2 very low (≤ veryLow), −1 low
        // (< low), 0 in range, 1 high (≥ high), 2 very high (≥ veryHigh), 3 extremely high (≥ extremelyHigh); null
        // without a value.
        (:sukraraFace)
        function level() as Number? {
            if (mgdl == null) {
                return null;
            }
            var v = mgdl as Number;
            return v <= bands[0] ? -2 : v < bands[1] ? -1 : v < bands[3] ? 0 : v < bands[4] ? 1 : v < bands[5] ? 2 : 3;
        }
    }
}
