// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later

import Toybox.Application;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Time;

// What an app knows from Sukrara (every Sukrara Garmin app, apps/garmin/README.md): the newest payload
// (contracts/watchface/payload, version 5, the app's own profile), kept in storage so a restart shows it at once, the
// phone's clock lead, the run report the fetch carries back, and the payload at every draw (README "On the watch").
// The rules (the version, the order, the clock lead, what the payload says at a watch time) are the SukraraFace
// barrel's own code (apps/garmin/sdk/source/read), the one implementation every face shares. Sukrara decides; this
// only counts time.
// (:glance): the app's glance reads it too; (:background): the app's background keeps the newest payload itself (its
// complications and alerts, AppService).
(:background :glance)
module Link {
    // the app's profile (contracts/watchface/payload/apps.json): its jungle excludes the other profiles' annotations;
    // every watch face is (:face)
    (:face)
    const PROFILE = "face";
    (:app)
    const PROFILE = "app";
    (:field)
    const PROFILE = "field";
    const STORE = "link";
    const SENT = "linkSent"; // the stored payload's sent (the order rule reads only this)
    // the watch's id on the phone (the app profile's push only, README `w`): its fetch says it (&w=)
    const WATCH = "linkW";
    // the version of the last payload (or write answer) of another version, until one of this version comes: which app
    // to update (update())
    const OTHER = "linkOther";
    var _payload as Dictionary? = null;
    var skew as Number = 0; // how far the phone's clock is ahead of the watch's, s
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
            skew = k instanceof Number ? k : 0;
        }
    }

    // A background run's result (LinkService.hand), through Garmin's onBackgroundData or picked up from storage
    // ([stored]); each run counts once, whichever way comes first (its id, kept across restarts). True when the app
    // has something new.
    const TAKEN = "linkTaken";
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

    // The app's draw: the background's last result, when Garmin didn't hand it over.
    function pickUp(now as Number) as Void {
        if (now >= _pickedAt && now < _pickedAt + PICKUP_S) {
            return;
        }
        _pickedAt = now;
        take(Application.Storage.getValue(LinkBackground.HANDOFF), now, true);
    }

    // A push or a fetch from LinkService ([stored]: picked up from storage, `s` in the report).
    function receive(data, now as Number, stored as Boolean) as Boolean {
        if (!(data instanceof Dictionary)) {
            return false;
        }
        var fetch = "fetch".equals(data.get("k"));
        if (fetch && data.get("c") == 200) {
            _fetchedAt = now;
        }
        var d = data.get("d");
        // the app's background keeps the payload itself and hands over only what came (AppService: its glance has
        // 64 KB): the stored one is read again when next needed
        var kept = data.get("kept");
        var sent = data.get("sent");
        var gt = data.get("gt");
        if (kept == null) {
            d = readable(d);
            kept = accept(d, now);
            var g = valid(d) ? (d as Dictionary).get("g") : null;
            sent = valid(d) ? (d as Dictionary).get("sent") : 0;
            gt = g instanceof Dictionary ? g.get("t") : 0;
        } else if (kept) {
            _loaded = false;
        }
        // what arrived, when (the report the phone shows): p / f, or the fetch's error code
        // p<n>: a push, n older messages taken with it; s: picked up from storage (Garmin didn't hand it over)
        var n = data.get("n");
        var kind = fetch ? (data.get("c") == 200 ? "f" : "e" + data.get("c")) : "p" + (n instanceof Number && n > 0 ? n : "");
        note(kind + (stored ? "s" : "") + (kept ? "" : "x"), now, sent, gt);
        return kept as Boolean;
    }

    // The watch's own account of the link, sent to the phone with every fetch (?r=…&d=…): the last background
    // deliveries (kind, watch time, the payload's sent, its reading's time) and since when the app draws which reading.
    // After the deliveries, r carries the screen's last events in the same shape: <event>.<watch time>.<display mode>.0
    // (docs/43 "The screen"; times and modes only).
    const REPORT = "linkReport";
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

    // A screen event (the app's foreground): [event] is the callback or the mode drawn (docs/43 "The screen"), [mode]
    // what System.getDisplayMode() said then (0 high power, 1 low power, 2 off, 9 not on this watch).
    function screen(event as String, at as Number, mode as Number) as Void {
        var e = event + "." + at + "." + mode + ".0";
        _screen = keep(_screen != null ? _screen : stored("Screen"), e, SCREEN, "Screen");
        saveReport();
    }

    // The reading the app draws now (its time on the watch clock) and since when: written when it changes.
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

    // THE VERSION RULE (SukraraFace.otherVersion): [p] as this version reads it. A payload of another version is its
    // glucose alone (SukraraFace.glucoseOnly), its version kept for the update line; one of this version clears that.
    function readable(p) {
        var other = SukraraFace.otherVersion(p, PROFILE);
        if (other != null) {
            Application.Storage.setValue(OTHER, other);
            return SukraraFace.glucoseOnly(p as Dictionary);
        }
        if (valid(p)) {
            Application.Storage.deleteValue(OTHER);
        }
        return p;
    }

    // Another version than this app's, from a write's answer (r: v, Outbox) or a payload (readable).
    function other(v) as Void {
        if (v instanceof Number && v != SukraraFace.VERSION) {
            Application.Storage.setValue(OTHER, v);
        }
    }

    // Which app to update (SukraraFace.toUpdate): UPDATE_APP (this one), UPDATE_PHONE (Sukrara on the phone) or null.
    function update() as String? {
        var o = Application.Storage.getValue(OTHER);
        return SukraraFace.toUpdate(o instanceof Number ? o : null);
    }

    // Keeps [p] when it is a payload of this version and the app's profile, no older than the stored one (README "Order").
    function accept(p, now as Number) as Boolean {
        if (!valid(p)) {
            return false;
        }
        // the stored one's time alone ([SENT]), never the stored payload itself: a background run that read it beside
        // the new one held two (64 KB, #217)
        var sent = (p as Dictionary).get("sent") as Number;
        var stored = _loaded && _payload != null ? _payload.get("sent") : Application.Storage.getValue(SENT);
        if (!SukraraFace.replaces(stored instanceof Number ? stored : null, p as Dictionary, now)) {
            return false;
        }
        _payload = p as Dictionary;
        _loaded = true;
        skew = SukraraFace.skewOf(sent, now);
        var w = (p as Dictionary).get("w");
        if (w instanceof String) {
            Application.Storage.setValue(WATCH, w);
        }
        Application.Storage.setValue(STORE, {"p" => _payload, "skew" => skew});
        Application.Storage.setValue(SENT, sent);
        return true;
    }

    // Forgets everything (the link's tests).
    function reset() as Void {
        _payload = null;
        skew = 0;
        _fetchedAt = null;
        _loaded = true;
        _runs = null;
        _screen = null;
        _drawn = "";
        _taken = null;
        _pickedAt = 0;
        Application.Storage.deleteValue(STORE);
        Application.Storage.deleteValue(SENT);
        Application.Storage.deleteValue(TAKEN);
        Application.Storage.deleteValue(LinkBackground.HANDOFF);
        Application.Storage.deleteValue(WATCH);
        Application.Storage.deleteValue(OTHER);
    }

    function valid(p) as Boolean {
        return SukraraFace.valid(p, PROFILE);
    }

    // When the app last fetched from the phone (watch clock), null = not since it started.
    function fetchedAt() as Number? {
        return _fetchedAt;
    }

    function payload() as Dictionary? {
        load();
        return _payload;
    }

    // Lets the kept payload go (read again from storage when next needed): a background run before its fetch holds
    // one payload at a time
    function unload() as Void {
        _payload = null;
        _loaded = false;
    }

    // The app's glance: its small copy of the payload (GlanceCopy) in place of the stored one, never loaded there
    (:app)
    function use(c) as Void {
        _loaded = true;
        _payload = null;
        if (c instanceof Dictionary && valid(c.get("p"))) {
            _payload = c.get("p") as Dictionary;
            var k = c.get("skew");
            skew = k instanceof Number ? k : 0;
        }
    }

    // What the payload says at the watch time [now] (epoch s); null without any payload.
    function inputs(now as Number) as SukraraFace.Reading? {
        load();
        return _payload != null ? new SukraraFace.Reading(_payload as Dictionary, skew, now) : null;
    }
}
