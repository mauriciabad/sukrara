// SukraraFace — https://sukrara.mauri.app/developers/garmin-faces/
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: 0BSD
import Toybox.Lang;

// contracts/watchface/payload/cases.json's `watch` checks against a Reading: the case's names for what a face makes of
// a payload (README "On the watch"), and the comparison. The barrel's tests and Sukrara's own link tests use it.
(:test)
module SukraraFaceExpect {
    // [r] in the names the cases expect; [skew] the phone's clock lead it was read with
    function inputs(r as SukraraFace.Reading, skew as Number) as Dictionary {
        var v = r.mgdl;
        var hold = r.status.equals("quiet") || r.status.equals("muted");
        return {
            "glucose" => v == null ? null : v < 40 ? "LO" : v > 400 ? "HI" : v,
            "age" => r.ageS / 60.0,
            "rate" => r.rate,
            "trend" => r.rate != null,
            "double" => r.doubled,
            "deg" => r.deg,
            "history" => r.history,
            "unit" => r.unit,
            "bands" => r.bands,
            "status" => r.status,
            "side" => r.side,
            "left" => hold && r.left == null ? "never" : r.left,
            "last" => r.last,
            "lastAgo" => r.lastAt != null ? (r.now - (r.lastAt as Number)) / 60 : null,
            "sensor" => r.sensor,
            "warm" => r.warm,
            "ready" => r.readyAt != null ? (r.readyAt as Number) + skew : null,
            "sensorLife" => r.sensorLife,
            "sensorLeft" => r.sensorLeft,
            "tir" => r.tir
        };
    }

    // [a] the expectation, [b] what came: numbers within 0.01; a dictionary checks only the keys [a] names (a `last`
    // item's own t and m: README "The payload")
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

    // [a] and [b] the same, every key of both (numbers within 0.01)
    function same(a, b) as Boolean {
        return close(a, b) && close(b, a);
    }

    // Every key [expect] names, [got] has the same; the differences logged as "<name> <key>: expected …, got …".
    function check(name as String, expect as Dictionary, got as Dictionary, logger) as Boolean {
        var ok = true;
        var keys = expect.keys();
        for (var i = 0; i < keys.size(); i++) {
            if (!close(expect.get(keys[i]), got.get(keys[i]))) {
                logger.error(name + " " + keys[i] + ": expected " + expect.get(keys[i]) + ", got " + got.get(keys[i]));
                ok = false;
            }
        }
        return ok;
    }
}
