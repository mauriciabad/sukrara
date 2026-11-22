// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

import Toybox.Application;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Time;

// What the face knows from Sukrara: the newest payload (contracts/watchface/payload, version 4, its profile "aura"), kept in storage so a
// restart shows it at once, and the face's inputs worked out from it and the watch clock at every draw (README "On
// the watch"). Sukrara decides; this only counts time.
module AuraLink {
    const VERSION = 4;
    const PROFILE = "aura";
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

    // A background run's result (AuraLinkService.hand), through Garmin's onBackgroundData or picked up from storage
    // ([stored]); each run counts once, whichever way comes first (its id, kept across restarts). True when the face
    // has something new.
    const TAKEN = "auraTaken";
    // storage is read at most this often (a draw is 1 Hz for ~10 s after a wrist raise)
    const PICKUP_S = 10;
    var _taken = null;
    var _pickedAt as Number = 0;

    function take(data, now as Number, stored as Boolean) as Boolean {
        if (!(data instanceof Dictionary)) {
            return false;
        }
        if (_taken == null) {
            _taken = Application.Storage.getValue(TAKEN);
        }
        var id = data.get("id");
        if (id instanceof String) {
            if (id.equals(_taken)) {
                return false;
            }
            _taken = id;
            Application.Storage.setValue(TAKEN, id);
        }
        return receive(data, now, stored);
    }

    // The face's draw: the background's last result, when Garmin didn't hand it over.
    function pickUp(now as Number) as Void {
        if (now >= _pickedAt && now < _pickedAt + PICKUP_S) {
            return;
        }
        _pickedAt = now;
        take(Application.Storage.getValue(AuraLinkBackground.HANDOFF), now, true);
    }

    // A push or a fetch from AuraLinkService ([stored]: picked up from storage, `s` in the report).
    function receive(data, now as Number, stored as Boolean) as Boolean {
        if (!(data instanceof Dictionary)) {
            return false;
        }
        var fetch = "fetch".equals(data.get("k"));
        if (fetch && data.get("c") == 200) {
            _fetchedAt = now;
        }
        var d = data.get("d");
        var kept = accept(d, now);
        var sent = valid(d) ? (d as Dictionary).get("sent") : 0;
        var g = valid(d) ? (d as Dictionary).get("g") : null;
        // what arrived, when (the report the phone shows): p / f, or the fetch's error code
        // p<n>: a push, n older messages taken with it; s: picked up from storage (Garmin didn't hand it over)
        var n = data.get("n");
        var kind = fetch ? (data.get("c") == 200 ? "f" : "e" + data.get("c")) : "p" + (n instanceof Number && n > 0 ? n : "");
        note(kind + (stored ? "s" : "") + (kept ? "" : "x"), now, sent, g instanceof Dictionary ? g.get("t") : 0);
        return kept;
    }

    // The watch's own account of the link, sent to the phone with every fetch (?r=…&d=…): the last background
    // deliveries (kind, watch time, the payload's sent, its reading's time) and since when the face draws which reading.
    // After the deliveries, r carries the screen's last events in the same shape: <event>.<watch time>.<display mode>.0
    // (docs/43 "The screen"; times and modes only).
    const REPORT = "auraReport";
    const RUNS = 10;
    const SCREEN = 12;
    var _runs as Array<String>? = null;
    var _screen as Array<String>? = null;
    var _drawn as String = "";

    function stored(key as String) as Array<String> {
        var r = Application.Storage.getValue(REPORT + key);
        return r instanceof Array ? r as Array<String> : [] as Array<String>;
    }

    // keeps the last [n] of [list] plus [entry] under REPORT + [key]
    function keep(list as Array<String>, entry as String, n as Number, key as String) as Array<String> {
        list.add(entry);
        if (list.size() > n) {
            list = list.slice(list.size() - n, null);
        }
        Application.Storage.setValue(REPORT + key, list);
        return list;
    }

    function note(kind as String, at as Number, sent, gt) as Void {
        _runs = keep(_runs != null ? _runs : stored("Runs"), kind + "." + at + "." + sent + "." + gt, RUNS, "Runs");
        saveReport();
    }

    // A screen event (the face's foreground): [event] is the callback or the mode drawn (docs/43 "The screen"), [mode]
    // what System.getDisplayMode() said then (0 high power, 1 low power, 2 off, 9 not on this watch).
    function screen(event as String, at as Number, mode as Number) as Void {
        var e = event + "." + at + "." + mode + ".0";
        _screen = keep(_screen != null ? _screen : stored("Screen"), e, SCREEN, "Screen");
        saveReport();
    }

    // The reading the face draws now (its time on the watch clock) and since when: written when it changes.
    function drawn(readAt as Number, now as Number) as Void {
        var key = readAt.toString();
        if (_drawn.find(key + ".") == 0) {
            return;
        }
        _drawn = key + "." + now;
        saveReport();
    }

    function saveReport() as Void {
        if (_runs == null) {
            _runs = stored("Runs");
        }
        if (_screen == null) {
            _screen = stored("Screen");
        }
        var r = "";
        var lists = [_runs, _screen];
        for (var l = 0; l < 2; l++) {
            var list = lists[l] as Array<String>;
            for (var i = 0; i < list.size(); i++) {
                r += (r.length() > 0 ? "_" : "") + list[i];
            }
        }
        Application.Storage.setValue(REPORT, "r=" + r + "&d=" + _drawn);
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
        _runs = null;
        _screen = null;
        _drawn = "";
        _taken = null;
        _pickedAt = 0;
        Application.Storage.deleteValue(STORE);
        Application.Storage.deleteValue(TAKEN);
        Application.Storage.deleteValue(AuraLinkBackground.HANDOFF);
    }

    function valid(p) as Boolean {
        return p instanceof Dictionary && p.get("v") == VERSION && PROFILE.equals(p.get("p")) && p.get("sent") instanceof Number;
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
        var last = treatment(p, now + _skew);
        if (last != null) {
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
        if (s.get("start") instanceof Number) {
            r.sensorStarts = (s.get("start") as Number) - _skew;
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
        if (g instanceof Dictionary) {
            drawn(r.readAt, now);
        }
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
        var last = treatment(p as Dictionary, t);
        if (last != null) {
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

    // The treatment field at the phone's time [t]: the first of last, last.next, … whose until is still ahead (−1 =
    // never); null = none (Sukrara's card shows no last treatment then).
    function treatment(p as Dictionary, t as Number) as Dictionary? {
        var s = p.get("last");
        while (s instanceof Dictionary) {
            var until = s.get("until");
            if (!(until instanceof Number && until != -1 && t >= until)) {
                return s as Dictionary;
            }
            s = s.get("next");
        }
        return null;
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
            var start = s.get("start");
            // the sensor's life (the sensor rail): its days and the hours left, whenever the phone knows the dates
            if (end instanceof Number && start instanceof Number && end > start) {
                out.put("sensorLife", (end - start) / 86400.0);
                out.put("sensorLeft", (end - t > 0 ? end - t : 0) / 3600.0);
            }
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
