// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

import Toybox.Application;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Time;

// What the face knows from Sukrara: the newest payload (contracts/watchface/payload, version 1), kept in storage so a
// restart shows it at once, and the face's inputs worked out from it and the watch clock at every draw (README "On
// the watch"). Sukrara decides; this only counts time.
module AuraLink {
    const VERSION = 1;
    const STORE = "auraLink";
    // a stored payload this far ahead of the watch clock gives way to any new one (the phone's clock was wrong)
    const AHEAD_S = 300;

    var _payload as Dictionary? = null;
    var _skew as Number = 0;
    var _fetchedAt as Number? = null;
    var _loaded as Boolean = false;

    function load() as Void {
        if (_loaded) {
            return;
        }
        _loaded = true;
        var s = Application.Storage.getValue(STORE);
        if (s instanceof Dictionary && valid(s.get("p"))) {
            _payload = s.get("p") as Dictionary;
            var k = s.get("skew");
            _skew = k instanceof Number ? k : 0;
        }
    }

    // The face's onBackgroundData: a push or a fetch from AuraLinkService. True when the face has something new.
    function receive(data, now as Number) as Boolean {
        if (!(data instanceof Dictionary)) {
            return false;
        }
        if ("fetch".equals(data.get("k")) && data.get("c") == 200) {
            _fetchedAt = now;
        }
        return accept(data.get("d"), now);
    }

    // Keeps [p] when it is a version-1 payload no older than the stored one (README "Order").
    function accept(p, now as Number) as Boolean {
        load();
        if (!valid(p)) {
            return false;
        }
        var sent = (p as Dictionary).get("sent") as Number;
        if (_payload != null) {
            var stored = _payload.get("sent") as Number;
            if (sent < stored && stored - now <= AHEAD_S) {
                return false;
            }
        }
        _payload = p as Dictionary;
        _skew = sent > now ? sent - now : 0;
        Application.Storage.setValue(STORE, {"p" => _payload, "skew" => _skew});
        return true;
    }

    // Forgets everything (the link's tests).
    function reset() as Void {
        _payload = null;
        _skew = 0;
        _fetchedAt = null;
        _cache = null;
        _loaded = true;
        Application.Storage.deleteValue(STORE);
    }

    function valid(p) as Boolean {
        return p instanceof Dictionary && p.get("v") == VERSION && p.get("sent") instanceof Number;
    }

    // When the face last fetched from the phone (watch clock), null = not since it started.
    function fetchedAt() as Number? {
        return _fetchedAt;
    }

    function payload() as Dictionary? {
        load();
        return _payload;
    }

    var _cache as AuraReading? = null;
    var _cacheAt as Number = -1;
    var _cacheOf as Dictionary? = null;

    // THE SEAM's reading (AuraData.current) at the watch time [now]: what the phone sent, its times moved onto the
    // watch clock (skew), the time-bound states worked out now (README "On the watch"); null = nothing ever came.
    // Built at most once a second.
    function reading(now as Number) as AuraReading? {
        load();
        if (_payload == null) {
            return null;
        }
        if (_cache != null && _cacheAt == now && _cacheOf == _payload) {
            return _cache;
        }
        var x = inputs(now) as Dictionary;
        var p = _payload as Dictionary;
        var r = new AuraReading();
        var g = p.get("g");
        if (g instanceof Dictionary) {
            r.mgdl = g.get("v") as Number;
            r.readAt = (g.get("t") as Number) - _skew;
            var rate = g.get("rate");
            r.rate = rate != null ? (rate as Numeric).toFloat() : null;
            r.doubled = x.get("double") as Boolean;
            var h = p.get("hist");
            r.history = h instanceof Array && h.size() > 0 ? h : null;
        }
        r.status = x.get("status") as String;
        var st = p.get("st");
        var left = x.get("left");
        r.statusUntil = left instanceof Number ? ((st as Dictionary).get("until") as Number) - _skew : null;
        var last = p.get("last");
        if (last instanceof Dictionary) {
            var items = last.get("items") as Array;
            var out = [] as Array;
            for (var i = 0; i < items.size(); i++) {
                var it = items[i] as Dictionary;
                out.add([it.get("k"), (it.get("a") as Numeric).toFloat()]);
            }
            r.lastItems = out;
            r.lastAt = (last.get("t") as Number) - _skew;
        }
        var sensor = x.get("sensor") as String;
        var age = x.get("age");
        if (sensor.equals("ended") && age != null && (age as Float) <= 5.0) {
            sensor = "endedSending"; // Sukrara: ended with a fresh or late reading keeps the value, "Ended" beside it
        } else if (sensor.equals("ok") && !(g instanceof Dictionary)) {
            sensor = "none"; // monitoring, but no reading ever: "No readings"
        }
        r.sensor = sensor;
        var s = p.get("sensor") as Dictionary;
        if (s.get("end") instanceof Number) {
            r.sensorEnds = (s.get("end") as Number) - _skew;
        }
        if (s.get("start") instanceof Number && s.get("ready") instanceof Number) {
            r.warmFrom = (s.get("start") as Number) - _skew;
            r.warmUntil = (s.get("ready") as Number) - _skew;
        }
        r.unit = p.get("unit") as String;
        var bands = p.get("bands");
        if (bands instanceof Array && bands.size() == 6) {
            r.limits = bands;
        }
        r.tir = x.get("tir") as Number?;
        _cache = r;
        _cacheAt = now;
        _cacheOf = _payload;
        return r;
    }

    // The face's inputs (SPEC §3 names) at the watch time [now] (epoch s); null without any payload.
    function inputs(now as Number) as Dictionary? {
        load();
        var p = _payload;
        if (p == null) {
            return null;
        }
        var t = now + _skew;
        var out = {
            "unit" => p.get("unit"),
            "bands" => p.get("bands"),
            "sent" => p.get("sent")
        } as Dictionary;
        var g = p.get("g");
        if (g instanceof Dictionary) {
            var v = g.get("v") as Number;
            var age = t - (g.get("t") as Number);
            out.put("glucose", v < 40 ? "LO" : (v > 400 ? "HI" : v));
            out.put("readingTime", g.get("t"));
            out.put("age", (age < 0 ? 0 : age) / 60.0);
            var rate = g.get("rate");
            out.put("rate", rate);
            out.put("trend", rate != null);
            var band = g.get("band");
            out.put("double", band instanceof Number && (band == 3 || band == -3));
            out.put("deg", g.get("deg"));
            out.put("history", p.get("hist"));
        } else {
            out.put("glucose", null);
            out.put("trend", false);
            out.put("history", []);
        }
        status(p.get("st"), t, out);
        var last = p.get("last");
        if (last instanceof Dictionary) {
            out.put("last", last.get("items"));
            out.put("lastAgo", (t - (last.get("t") as Number)) / 60);
        } else {
            out.put("last", null);
        }
        sensor(p.get("sensor"), g, t, out);
        var tir = p.get("tir");
        out.put("tir", tir instanceof Dictionary && t < (tir.get("until") as Number) ? tir.get("pct") : null);
        return out;
    }

    // needed → auto, taken → quiet, paused / muted → muted; a countdown that ran out shows nothing.
    function status(st, t as Number, out as Dictionary) as Void {
        out.put("status", "none");
        out.put("side", null);
        out.put("left", null);
        if (!(st instanceof Dictionary)) {
            return;
        }
        var k = st.get("k");
        var until = st.get("until");
        var left = null;
        if (until instanceof Number) {
            if (until == -1) {
                left = "never";
            } else if (until - t > 0) {
                left = until - t;
            } else {
                return;
            }
        }
        out.put("side", st.get("side"));
        out.put("left", left);
        if ("needed".equals(k)) {
            out.put("status", "auto");
        } else if ("taken".equals(k)) {
            out.put("status", "quiet");
        } else if ("paused".equals(k) || "muted".equals(k)) {
            out.put("status", "muted");
        }
    }

    function sensor(s, g, t as Number, out as Dictionary) as Void {
        var state = s instanceof Dictionary ? s.get("s") : null;
        var name = "ok";
        if ("none".equals(state)) {
            name = "none";
        } else if ("ended".equals(state)) {
            name = "ended";
        } else if ("warmup".equals(state)) {
            var start = s.get("start") as Number;
            var ready = s.get("ready") as Number;
            var since = g instanceof Dictionary && (g.get("t") as Number) >= start;
            if (t < (s.get("hold") as Number) && !since) {
                name = t >= ready ? "almost" : "warmup";
                var span = ready - start;
                var warm = span > 0 ? (t - start).toFloat() / span : 1.0;
                out.put("warm", warm < 0.0 ? 0.0 : (warm > 1.0 ? 1.0 : warm));
                out.put("ready", ready);
            }
        } else if ("active".equals(state)) {
            var end = s.get("end");
            var warn = s.get("warn");
            if (end instanceof Number && t >= end) {
                name = "ended";
            } else if (end instanceof Number && warn instanceof Number && t >= warn) {
                name = "ending";
                out.put("sensorLeft", (end - t) / 3600.0);
            }
        }
        out.put("sensor", name);
    }
}
